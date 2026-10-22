using System.Diagnostics;
using System.IO;
using CookieLauncher.Core;

namespace CookieLauncher.Shell;

/// <summary>Shell integration: opening folders and files without crashing on failure.</summary>
internal static class ShellOperations
{
    /// <summary>Opens a folder or file in the platform's file manager.</summary>
    public static void Open(string path)
    {
        try
        {
            Process.Start(new ProcessStartInfo(path) { UseShellExecute = true });
        }
        catch (Exception error) when (error is IOException or System.ComponentModel.Win32Exception)
        {
            Log.Warn($"Cannot open {path}: {error.Message}");
        }
    }

    /// <summary>Opens Explorer at the given directory, creating it when missing.</summary>
    public static void OpenDirectory(string path)
    {
        try
        {
            Directory.CreateDirectory(path);
        }
        catch (IOException error)
        {
            Log.Warn($"Cannot create {path}: {error.Message}");
            return;
        }
        Open(path);
    }

    /// <summary>Reveals a file by selecting it in Explorer.</summary>
    public static void RevealFile(string path)
    {
        if (!File.Exists(path))
        {
            return;
        }
        try
        {
            Process.Start(new ProcessStartInfo("explorer.exe", $"/select,\"{path}\"")
            {
                UseShellExecute = true,
            });
        }
        catch (Exception error) when (error is IOException or System.ComponentModel.Win32Exception)
        {
            Log.Warn($"Cannot reveal {path}: {error.Message}");
        }
    }

    /// <summary>Copies text to the clipboard, ignoring failures on locked-down systems.</summary>
    public static void CopyText(string text)
    {
        try
        {
            System.Windows.Clipboard.SetText(text);
        }
        catch (Exception error)
        {
            Log.Warn("Cannot access the clipboard: " + error.Message);
        }
    }
}
