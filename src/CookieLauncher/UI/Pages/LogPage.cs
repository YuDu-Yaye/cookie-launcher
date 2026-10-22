using System.IO;
using System.Text;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Threading;
using CookieLauncher.Core;

namespace CookieLauncher.UI.Pages;

/// <summary>
/// Logs page: live launcher output, a filter, export and copy.
/// </summary>
/// <remarks>
/// <para>
/// The page subscribes to <see cref="Log"/> rather than polling a file, because the log is
/// written from worker threads and a file reader would either miss lines or need a timer.
/// The subscription is dropped when the page leaves the visual tree, so a long session
/// cannot accumulate listeners.
/// </para>
/// <para>
/// Rendering is throttled: appending every line straight to the control makes the UI stutter
/// when the game floods output, so lines are buffered and flushed a few times per second.
/// </para>
/// </remarks>
public sealed class LogPage : PageBase
{
    private const int MaxRenderedLines = 3000;

    private readonly TextBox _output;
    private readonly TextBox _filter;
    private readonly TextBlock _summary;
    private readonly DispatcherTimer _flushTimer;
    private readonly Queue<string> _pending = new();
    private readonly Lock _gate = new();
    private readonly Action<string> _listener;
    private bool _subscribed;

    public LogPage()
    {
        Margin = new Thickness(28, 22, 28, 18);

        var root = new Grid();
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

        // ---- Headline ------------------------------------------------------
        var headline = new StackPanel { Margin = new Thickness(0, 0, 0, 14) };
        headline.Children.Add(new TextBlock
        {
            Text = "Logs",
            Style = (Style)FindResource("MdHeadlineSmall"),
        });
        headline.Children.Add(new TextBlock
        {
            Text = "Live launcher output. When a launch fails, the reason appears here.",
            Style = (Style)FindResource("MdBodyMedium"),
            Margin = new Thickness(0, 3, 0, 0),
        });
        Grid.SetRow(headline, 0);
        root.Children.Add(headline);

        // ---- Toolbar -------------------------------------------------------
        _filter = new TextBox
        {
            Style = (Style)FindResource("MdTextField"),
            Tag = "Filter lines, for example error or mixin",
            Margin = new Thickness(0, 0, 8, 0),
        };
        Grid.SetColumn(_filter, 0);

        var copy = new Button
        {
            Content = "Copy",
            Style = (Style)FindResource("MdOutlinedButton"),
            MinWidth = 92,
        };
        copy.Click += (_, _) => CopyAll();

        var export = new Button
        {
            Content = "Export",
            Style = (Style)FindResource("MdOutlinedButton"),
            MinWidth = 92,
            Margin = new Thickness(8, 0, 0, 0),
        };
        export.Click += (_, _) => Export();

        var clear = new Button
        {
            Content = "Clear",
            Style = (Style)FindResource("MdTextButton"),
            MinWidth = 84,
            Margin = new Thickness(8, 0, 0, 0),
        };
        clear.Click += (_, _) => ClearAll();

        var toolbar = new Grid { Margin = new Thickness(0, 0, 0, 10) };
        toolbar.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        toolbar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        toolbar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        toolbar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        toolbar.Children.Add(_filter);
        Grid.SetColumn(copy, 1);
        toolbar.Children.Add(copy);
        Grid.SetColumn(export, 2);
        toolbar.Children.Add(export);
        Grid.SetColumn(clear, 3);
        toolbar.Children.Add(clear);
        Grid.SetRow(toolbar, 1);
        root.Children.Add(toolbar);

        // ---- Output surface ------------------------------------------------
        var surface = new Border
        {
            CornerRadius = new CornerRadius(12),
            Background = (Brush)FindResource("SurfaceContainerBrush"),
            BorderBrush = (Brush)FindResource("OutlineVariantBrush"),
            BorderThickness = new Thickness(1),
            Padding = new Thickness(2),
        };

        _output = new TextBox
        {
            IsReadOnly = true,
            AcceptsReturn = true,
            TextWrapping = TextWrapping.NoWrap,
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
            HorizontalScrollBarVisibility = ScrollBarVisibility.Auto,
            Background = Brushes.Transparent,
            BorderThickness = new Thickness(0),
            Padding = new Thickness(14, 12, 14, 12),
            FontFamily = new FontFamily("Cascadia Mono, Consolas, Courier New"),
            FontSize = 11.5,
            Foreground = (Brush)FindResource("OnSurfaceBrush"),
            CaretBrush = (Brush)FindResource("PrimaryBrush"),
        };
        surface.Child = _output;
        Grid.SetRow(surface, 2);
        root.Children.Add(surface);

        // ---- Footer --------------------------------------------------------
        _summary = new TextBlock
        {
            Style = (Style)FindResource("MdLabelSmall"),
            Margin = new Thickness(0, 10, 0, 0),
        };
        Grid.SetRow(_summary, 3);
        root.Children.Add(_summary);

        Content = root;

        _flushTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(180) };
        _flushTimer.Tick += (_, _) => Flush();
        _flushTimer.Start();

        _listener = line =>
        {
            lock (_gate)
            {
                _pending.Enqueue(line);
            }
        };
    }

    public override void OnShown()
    {
        if (!_subscribed)
        {
            Log.AddListener(_listener);
            _subscribed = true;
        }
        SeedFromHistory();
        SetStatus("Live");
    }

    /// <summary>Shows what the launcher has already logged this session.</summary>
    private void SeedFromHistory()
    {
        if (_output.Text.Length > 0)
        {
            UpdateSummary();
            return;
        }

        IReadOnlyList<string> history = Log.Snapshot();
        if (history.Count == 0)
        {
            UpdateSummary();
            return;
        }

        StringBuilder builder = new();
        int from = Math.Max(0, history.Count - MaxRenderedLines);
        for (int i = from; i < history.Count; i++)
        {
            builder.AppendLine(history[i]);
        }
        _output.Text = builder.ToString();
        _output.ScrollToEnd();
        UpdateSummary();
    }

    private void Flush()
    {
        List<string> lines;
        lock (_gate)
        {
            if (_pending.Count == 0)
            {
                return;
            }
            lines = [.. _pending];
            _pending.Clear();
        }

        string filter = _filter.Text.Trim();
        StringBuilder builder = new(lines.Count * 90);
        int shown = 0;
        foreach (string line in lines)
        {
            if (filter.Length > 0 && !line.Contains(filter, StringComparison.OrdinalIgnoreCase))
            {
                continue;
            }
            builder.AppendLine(line);
            shown++;
        }
        if (shown == 0)
        {
            return;
        }

        _output.AppendText(builder.ToString());
        _output.ScrollToEnd();
        TrimRendered();
        UpdateSummary();
    }

    /// <summary>Counts rendered lines without relying on TextBox.LineCount.</summary>
    private static int CountLines(string text)
    {
        if (text.Length == 0)
        {
            return 0;
        }
        int lines = 0;
        foreach (char character in text)
        {
            if (character == '\n')
            {
                lines++;
            }
        }
        return text.EndsWith('\n') ? lines : lines + 1;
    }

    /// <summary>Drops the oldest lines so the control never grows without bound.</summary>
    private void TrimRendered()
    {
        string text = _output.Text;
        int count = CountLines(text);
        if (count <= MaxRenderedLines)
        {
            return;
        }

        int drop = count - MaxRenderedLines;
        int index = -1;
        for (int i = 0; i < drop; i++)
        {
            index = text.IndexOf('\n', index + 1);
            if (index < 0)
            {
                break;
            }
        }
        if (index >= 0 && index < text.Length - 1)
        {
            _output.Text = text[(index + 1)..];
        }
    }

    private void UpdateSummary()
    {
        int lines = CountLines(_output.Text);
        string file = Log.FilePath ?? "(not configured)";
        _summary.Text = lines <= 0
            ? $"No output yet  \u00b7  log file: {file}"
            : $"{lines} lines  \u00b7  log file: {file}";
    }

    private void CopyAll()
    {
        Shell.ShellOperations.CopyText(_output.Text);
        SetStatus("Log copied to the clipboard");
    }

    private void ClearAll()
    {
        _output.Clear();
        lock (_gate)
        {
            _pending.Clear();
        }
        UpdateSummary();
    }

    private void Export()
    {
        try
        {
            string directory = AppPaths.Logs;
            Directory.CreateDirectory(directory);
            string target = Path.Combine(directory,
                "export-" + DateTime.Now.ToString("yyyyMMdd-HHmmss") + ".log");
            File.WriteAllText(target, _output.Text);
            SetStatus("Exported to " + target);
            Shell.ShellOperations.RevealFile(target);
        }
        catch (IOException error)
        {
            SetStatus("Export failed: " + error.Message);
        }
    }

    protected override void OnVisualParentChanged(DependencyObject oldParent)
    {
        base.OnVisualParentChanged(oldParent);
        if (VisualParent is null && _subscribed)
        {
            Log.RemoveListener(_listener);
            _subscribed = false;
        }
    }
}
