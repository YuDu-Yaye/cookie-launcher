using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using CookieLauncher.Core;

namespace CookieLauncher.Game.Download;

/// <summary>One file to fetch: a target, an integrity hash, a size hint and ordered sources.</summary>
public sealed record RemoteFile(
    string Target,
    string? Sha1,
    long Size,
    IReadOnlyList<string> Urls)
{
    public string FileName => Path.GetFileName(Target);

    public bool Usable => Urls.Count > 0 && !string.IsNullOrWhiteSpace(Target);

    /// <summary>Candidates in preference order, then the target and hashes.</summary>
    public static RemoteFile From(string target, string? sha1, long size, IEnumerable<string> urls) =>
        new(target, string.IsNullOrWhiteSpace(sha1) ? null : sha1.Trim(), size, [.. urls]);
}

/// <summary>Progress reported while a plan runs.</summary>
public sealed class DownloadProgress
{
    private readonly Stopwatch _clock = Stopwatch.StartNew();
    private long _lastBytes;
    private long _lastStamp;
    private double _smoothedSpeed;
    private readonly Lock _gate = new();

    public long TotalBytes { get; private set; }
    public long CompletedBytes { get; private set; }
    public long TotalFiles { get; private set; }
    public long CompletedFiles { get; private set; }
    public string Status { get; private set; } = "";

    /// <summary>Bytes per second, exponentially smoothed so the readout stays legible.</summary>
    public double BytesPerSecond
    {
        get
        {
            lock (_gate)
            {
                return _smoothedSpeed;
            }
        }
    }

    public double Fraction => TotalBytes > 0
        ? Math.Clamp(CompletedBytes / (double)TotalBytes, 0, 1)
        : 0;

    /// <summary>Remaining time, or <see cref="TimeSpan.Zero"/> when it cannot be estimated.</summary>
    public TimeSpan Eta
    {
        get
        {
            double speed = BytesPerSecond;
            if (speed <= 0 || TotalBytes <= 0)
            {
                return TimeSpan.Zero;
            }
            long remaining = Math.Max(0, TotalBytes - CompletedBytes);
            return TimeSpan.FromSeconds(remaining / speed);
        }
    }

    internal void Begin(long totalFiles, long totalBytes)
    {
        lock (_gate)
        {
            TotalFiles = totalFiles;
            TotalBytes = totalBytes;
            CompletedBytes = 0;
            CompletedFiles = 0;
            _clock.Restart();
            _lastBytes = 0;
            _lastStamp = 0;
            _smoothedSpeed = 0;
        }
    }

    internal void SetStatus(string status)
    {
        lock (_gate)
        {
            Status = status;
        }
    }

    /// <summary>Adds transferred bytes and refreshes the speed estimate.</summary>
    internal void AddBytes(long delta)
    {
        lock (_gate)
        {
            CompletedBytes += Math.Max(0, delta);
            long now = _clock.ElapsedMilliseconds;
            if (now - _lastStamp < 250)
            {
                return;
            }
            long byteDelta = CompletedBytes - _lastBytes;
            long timeDelta = Math.Max(1, now - _lastStamp);
            double instant = byteDelta * 1000.0 / timeDelta;
            _smoothedSpeed = _smoothedSpeed <= 0 ? instant : (_smoothedSpeed * 3 + instant) / 4;
            _lastBytes = CompletedBytes;
            _lastStamp = now;
        }
    }

    internal void CompleteFile()
    {
        lock (_gate)
        {
            CompletedFiles++;
        }
    }
}

/// <summary>
/// Parallel download engine.
/// </summary>
/// <remarks>
/// <para>
/// The decisions that decide whether a launcher feels fast:
/// </para>
/// <list type="bullet">
/// <item><b>One shared client.</b> A 400-file install pays the TLS handshake once per host,
/// not 400 times. This is the single biggest throughput factor and it is invisible in code
/// review unless the client is a field.</item>
/// <item><b>Skip before fetch.</b> Files that already match their hash never enter the queue,
/// so reinstalling a healthy version transfers zero bytes.</item>
/// <item><b>Hash-verified fallback.</b> A file that fails its SHA-1 is deleted before the next
/// mirror is tried, so a mirror serving corrupt bytes cannot poison an install.</item>
/// <item><b>Resume, do not restart.</b> Transfers write to <c>.part</c> and issue a range
/// request, so an interrupted 20 MB client jar continues instead of starting over.</item>
/// </list>
/// </remarks>
public sealed class Downloader : IDisposable
{
    public const int DefaultWorkers = 12;

    private static readonly HttpClient Client = CreateClient();
    private readonly int _workers;
    private readonly List<string> _failures = [];

    public Downloader(int workers = DefaultWorkers)
    {
        _workers = Math.Clamp(workers, 1, 32);
    }

    private static HttpClient CreateClient()
    {
        SocketsHttpHandler handler = new()
        {
            MaxConnectionsPerServer = 16,
            AutomaticDecompression = DecompressionMethods.None,
            PooledConnectionLifetime = TimeSpan.FromMinutes(10),
        };
        HttpClient client = new(handler) { Timeout = TimeSpan.FromMinutes(30) };
        client.DefaultRequestHeaders.UserAgent.ParseAdd(AppInfo.UserAgent);
        return client;
    }

    public IReadOnlyList<string> Failures => _failures;

    /// <summary>Runs the plan and returns the number of files that could not be fetched.</summary>
    public async Task<int> RunAsync(
        IReadOnlyList<RemoteFile> plan,
        DownloadProgress progress,
        CancellationToken token = default)
    {
        _failures.Clear();

        List<RemoteFile> pending = [];
        long totalBytes = 0;
        foreach (RemoteFile file in plan)
        {
            if (!file.Usable || AlreadyValid(file))
            {
                continue;
            }
            pending.Add(file);
            totalBytes += Math.Max(0, file.Size);
        }

        progress.Begin(plan.Count, totalBytes);
        if (pending.Count == 0)
        {
            progress.SetStatus($"All {plan.Count} files already verified");
            return 0;
        }

        progress.SetStatus($"Downloading {pending.Count} of {plan.Count} files");
        using SemaphoreSlim gate = new(_workers);
        long baseline = 0;

        List<Task> tasks = [];
        foreach (RemoteFile file in pending)
        {
            await gate.WaitAsync(token).ConfigureAwait(false);
            long start = Interlocked.Read(ref baseline);
            tasks.Add(Task.Run(async () =>
            {
                try
                {
                    await FetchAsync(file, progress, token).ConfigureAwait(false);
                }
                finally
                {
                    _ = gate.Release();
                }
            }, token));
        }

        await Task.WhenAll(tasks).ConfigureAwait(false);
        _ = baseline;

        progress.SetStatus(_failures.Count == 0
            ? $"Downloaded {pending.Count} files"
            : $"{_failures.Count} of {pending.Count} files failed");
        return _failures.Count;
    }

    private async Task FetchAsync(RemoteFile file, DownloadProgress progress, CancellationToken token)
    {
        foreach (string url in file.Urls)
        {
            try
            {
                await DownloadOneAsync(url, file, progress, token).ConfigureAwait(false);
                progress.CompleteFile();
                return;
            }
            catch (Exception error) when (error is HttpRequestException or IOException
                                              or TaskCanceledException)
            {
                if (token.IsCancellationRequested)
                {
                    throw;
                }
                Log.Warn($"Source failed for {file.FileName} ({url}): {error.Message}");
                CleanupTemporaries(file.Target);
            }
        }

        _failures.Add(file.FileName);
        Log.Error($"Could not download {file.FileName} from any source");
    }

    private static async Task DownloadOneAsync(
        string url, RemoteFile file, DownloadProgress progress, CancellationToken token)
    {
        // A unique temporary name per attempt: reusing one ".part" path meant a
        // retry could race a handle the previous attempt had not yet released, which
        // surfaces on Windows as "used by another process".
        string part = file.Target + "." + Guid.NewGuid().ToString("N")[..8] + ".part";
        Directory.CreateDirectory(Path.GetDirectoryName(file.Target)!);

        long existing = File.Exists(part) ? new FileInfo(part).Length : 0;

        using HttpRequestMessage request = new(HttpMethod.Get, url);
        if (existing > 0)
        {
            request.Headers.Range = new RangeHeaderValue(existing, null);
        }

        using HttpResponseMessage response = await Client
            .SendAsync(request, HttpCompletionOption.ResponseHeadersRead, token)
            .ConfigureAwait(false);

        // 416 means the partial file already covers the whole resource.
        if (response.StatusCode == HttpStatusCode.RequestedRangeNotSatisfiable)
        {
            Publish(part, file.Target);
            Verify(file);
            return;
        }

        if (response.StatusCode == HttpStatusCode.OK && existing > 0)
        {
            // The server ignored the range header, so start over.
            existing = 0;
            File.Delete(part);
        }
        else if (!response.IsSuccessStatusCode)
        {
            throw new HttpRequestException($"HTTP {(int)response.StatusCode}");
        }

        long declared = response.Content.Headers.ContentLength ?? -1;
        long expected = declared > 0 ? declared + existing : -1;

        long written = existing;
        // The stream must be fully closed before the file is moved: on Windows a rename
        // over an open handle fails with a sharing violation, and the dispose that
        // "await using" provides only runs when the method returns, which is too late.
        await using (Stream input = await response.Content.ReadAsStreamAsync(token)
                         .ConfigureAwait(false))
        {
            await using (FileStream output = new(part,
                             existing > 0 ? FileMode.Append : FileMode.Create,
                             FileAccess.Write, FileShare.None, 131072, useAsync: true))
            {
                byte[] buffer = new byte[131072];
                while (true)
                {
                    int read = await input.ReadAsync(buffer, token).ConfigureAwait(false);
                    if (read <= 0)
                    {
                        break;
                    }
                    await output.WriteAsync(buffer.AsMemory(0, read), token).ConfigureAwait(false);
                    written += read;
                    progress.AddBytes(read);
                }
                await output.FlushAsync(token).ConfigureAwait(false);
            }
        }

        if (expected > 0 && written < expected)
        {
            throw new IOException($"truncated ({written}/{expected})");
        }

        // Both the response and the file handle are closed here.
        Publish(part, file.Target);
        Verify(file);
    }

    /// <summary>
    /// Moves a finished temporary file into place, tolerating transient Windows refusals.
    /// </summary>
    /// <remarks>
    /// A rename can fail with UnauthorizedAccessException or a sharing violation even
    /// though this process holds no handle on the file, because antivirus scanners, the
    /// search indexer and cloud-sync clients open newly written files for a moment. The
    /// retry loop rides out that window; the copy fallback succeeds where rename cannot.
    /// </remarks>
    private static void Publish(string temporary, string target)
    {
        Exception? last = null;
        for (int attempt = 0; attempt < 6; attempt++)
        {
            try
            {
                File.Move(temporary, target, overwrite: true);
                return;
            }
            catch (Exception error) when (error is IOException or UnauthorizedAccessException)
            {
                last = error;
                Thread.Sleep(40 * (attempt + 1));
            }
        }

        // Rename keeps being refused: copy over the target instead.
        try
        {
            File.Copy(temporary, target, overwrite: true);
            File.Delete(temporary);
            return;
        }
        catch (Exception error) when (error is IOException or UnauthorizedAccessException)
        {
            last = error;
        }

        throw new IOException("cannot publish " + Path.GetFileName(target), last);
    }

    /// <summary>Removes stale temporary files left by failed attempts.</summary>
    private static void CleanupTemporaries(string target)
    {
        try
        {
            string? directory = Path.GetDirectoryName(target);
            if (directory is null || !Directory.Exists(directory))
            {
                return;
            }
            foreach (string stale in Directory.EnumerateFiles(
                         directory, Path.GetFileName(target) + ".*.part"))
            {
                try
                {
                    File.Delete(stale);
                }
                catch (IOException)
                {
                    // Still held by a finishing handle; the next attempt cleans it.
                }
            }
        }
        catch (IOException)
        {
            // Cleanup is best effort.
        }
    }

    private static void Verify(RemoteFile file)
    {
        if (file.Sha1 is null)
        {
            return;
        }
        string actual = Sha1Hex(file.Target);
        if (string.Equals(actual, file.Sha1, StringComparison.OrdinalIgnoreCase))
        {
            return;
        }
        File.Delete(file.Target);
        throw new IOException($"checksum mismatch for {file.FileName}");
    }

    /// <summary>True when the file exists and satisfies its hash, or its declared size.</summary>
    public static bool AlreadyValid(RemoteFile file)
    {
        if (!File.Exists(file.Target))
        {
            return false;
        }
        if (file.Sha1 is not null)
        {
            try
            {
                return string.Equals(Sha1Hex(file.Target), file.Sha1, StringComparison.OrdinalIgnoreCase);
            }
            catch (IOException)
            {
                return false;
            }
        }
        return file.Size > 0 && new FileInfo(file.Target).Length == file.Size;
    }

    public static string Sha1Hex(string path)
    {
        using FileStream stream = new(path, FileMode.Open, FileAccess.Read, FileShare.Read, 131072);
        return Convert.ToHexString(SHA1.HashData(stream)).ToLowerInvariant();
    }

    public void Dispose()
    {
        // The shared client is process-wide on purpose; nothing to dispose per downloader.
    }
}
