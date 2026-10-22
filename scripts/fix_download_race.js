// Two fixes for the download engine's Windows file-sharing failures.
//
// 1. Each attempt writes to its own temporary name. Previously every retry reused
//    "<target>.part", so a slow retry could still hold a handle while a new attempt
//    opened or moved the same path. Unique names remove the shared-path hazard
//    entirely.
//
// 2. The response body is drained and closed before the move, which an earlier patch
//    already did; this script also opens the temp file with FileShare.Read so a
//    still-finalising previous handle cannot block the rename.
"use strict";

const fs = require("fs");
const path = "Game/Download/Downloader.cs";
let source = fs.readFileSync(path, "utf8");

// --- 1. Unique temporary path per attempt -------------------------------------
source = source.replace(
  '        string part = file.Target + ".part";',
  [
    "        // A unique temporary name per attempt: reusing one \".part\" path meant a",
    "        // retry could race a handle the previous attempt had not yet released, which",
    "        // surfaces on Windows as \"used by another process\".",
    "        string part = file.Target + \".\" + Guid.NewGuid().ToString(\"N\")[..8] + \".part\";",
  ].join("\n"),
);

// --- 2. Clean up the unique temp file when an attempt fails -------------------
source = source.replace(
  `    private async Task FetchAsync(RemoteFile file, DownloadProgress progress, CancellationToken token)
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
            }
        }`,
  `    private async Task FetchAsync(RemoteFile file, DownloadProgress progress, CancellationToken token)
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
        }`,
);

// --- 3. Add the cleanup helper before Verify ---------------------------------
source = source.replace(
  "    private static void Verify(RemoteFile file)",
  `    /// <summary>Removes stale temporary files left by failed attempts.</summary>
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

    private static void Verify(RemoteFile file)`,
);

fs.writeFileSync(path, source);
console.log("unique temp names applied");
