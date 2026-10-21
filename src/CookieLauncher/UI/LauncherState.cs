using System.IO;
using CookieLauncher.Core;

namespace CookieLauncher.UI;

/// <summary>
/// Shared application state: the stores plus the small derived queries pages need.
/// </summary>
/// <remarks>
/// Passing this around instead of reaching for statics keeps pages testable and makes the
/// dependency obvious. There is exactly one instance for the lifetime of the process.
/// </remarks>
public sealed class LauncherState
{
    public static LauncherState Current { get; private set; } = null!;

    public InstanceStore Instances { get; }
    public AccountStore Accounts { get; }

    private LauncherState(InstanceStore instances, AccountStore accounts)
    {
        Instances = instances;
        Accounts = accounts;
    }

    /// <summary>Loads both stores and publishes the singleton.</summary>
    public static LauncherState Bootstrap()
    {
        InstanceStore instances = InstanceStore.Load(AppPaths.InstancesFile);
        AccountStore accounts = AccountStore.Load(AppPaths.AccountsFile);
        Current = new LauncherState(instances, accounts);
        return Current;
    }

    /// <summary>The selected instance, or the first one available.</summary>
    public Instance? Instance => Instances.Selected;

    public Account? SelectedAccount => Accounts.Selected;

    /// <summary>Number of jars in the instance's mods directory.</summary>
    public int ModCount(Instance instance)
    {
        try
        {
            if (!Directory.Exists(instance.ModsDirectory))
            {
                return 0;
            }
            return Directory.EnumerateFiles(instance.ModsDirectory, "*.jar").Count();
        }
        catch (IOException)
        {
            return 0;
        }
    }

    /// <summary>Re-reads both stores from disk, used after an external change.</summary>
    public void Reload()
    {
        Current = Bootstrap();
    }
}
