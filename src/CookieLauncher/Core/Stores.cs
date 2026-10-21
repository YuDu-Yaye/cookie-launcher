using System.IO;
using System.Text.Json.Serialization;

namespace CookieLauncher.Core;

/// <summary>Root object of <c>instances.json</c>.</summary>
internal sealed class InstanceDocument
{
    public List<Instance> Instances { get; set; } = [];
    public string Selected { get; set; } = "";
}

/// <summary>Persistent list of game instances.</summary>
/// <remarks>
/// An instance is a directory plus a small record here. Keeping the index separate from
/// the directory means a user can move or copy an instance folder and re-point it without
/// the launcher losing track of anything.
/// </remarks>
public sealed class InstanceStore
{
    private static readonly char[] Alphabet = "abcdefghijklmnopqrstuvwxyz0123456789".ToCharArray();
    private readonly string _file;
    private readonly List<Instance> _instances = [];
    private string _selected = "";

    private InstanceStore(string file) => _file = file;

    public static InstanceStore Load(string file)
    {
        InstanceStore store = new(file);
        InstanceDocument? document = Json.Read<InstanceDocument>(file);
        if (document is not null)
        {
            store._selected = document.Selected;
            foreach (Instance instance in document.Instances)
            {
                if (!string.IsNullOrWhiteSpace(instance.Id))
                {
                    store._instances.Add(instance);
                }
            }
        }
        return store;
    }

    public IReadOnlyList<Instance> All => _instances;

    public bool IsEmpty => _instances.Count == 0;

    public Instance? Find(string? id) =>
        id is null ? null : _instances.FirstOrDefault(i => i.Id == id);

    /// <summary>The selected instance, falling back to the first one that exists.</summary>
    public Instance? Selected
    {
        get
        {
            Instance? chosen = Find(_selected);
            return chosen ?? (_instances.Count > 0 ? _instances[0] : null);
        }
    }

    public void Select(string id)
    {
        if (Find(id) is not null)
        {
            _selected = id;
            Save();
        }
    }

    /// <summary>Creates an instance with its own game directory and selects it.</summary>
    public Instance Create(string? name, string? version)
    {
        Instance instance = new()
        {
            Id = NewId(),
            Name = string.IsNullOrWhiteSpace(name) ? "Instance " + (_instances.Count + 1) : name.Trim(),
            Version = version ?? "",
        };
        instance.GameDir = AppPaths.Instance(instance.Id);
        Directory.CreateDirectory(instance.ModsDirectory);
        _instances.Add(instance);
        _selected = instance.Id;
        Save();
        return instance;
    }

    public void Update(Instance instance)
    {
        int index = _instances.FindIndex(i => i.Id == instance.Id);
        if (index >= 0)
        {
            _instances[index] = instance;
        }
        else
        {
            _instances.Add(instance);
            _selected = instance.Id;
        }
        Save();
    }

    public bool Remove(string id)
    {
        bool removed = _instances.RemoveAll(i => i.Id == id) > 0;
        if (removed)
        {
            if (_selected == id)
            {
                _selected = _instances.Count > 0 ? _instances[0].Id : "";
            }
            Save();
        }
        return removed;
    }

    public void Save() => Json.Write(_file, new InstanceDocument
    {
        Instances = _instances,
        Selected = _selected,
    });

    public static string NewId() => new(
        Enumerable.Range(0, 8)
            .Select(_ => Alphabet[Random.Shared.Next(Alphabet.Length)])
            .ToArray());
}

/// <summary>Root object of <c>accounts.json</c>.</summary>
internal sealed class AccountDocument
{
    public List<Account> Accounts { get; set; } = [];
    public string Selected { get; set; } = "";
}

/// <summary>Persistent account list.</summary>
/// <remarks>
/// Tokens live here rather than in <c>config.json</c> so that sharing a settings file never
/// shares credentials. The file is only ever written to the launcher's own data directory.
/// </remarks>
public sealed class AccountStore
{
    private readonly string _file;
    private readonly List<Account> _accounts = [];
    private string _selected = "";

    private AccountStore(string file) => _file = file;

    public static AccountStore Load(string file)
    {
        AccountStore store = new(file);
        AccountDocument? document = Json.Read<AccountDocument>(file);
        if (document is not null)
        {
            store._selected = document.Selected;
            store._accounts.AddRange(document.Accounts);
        }
        return store;
    }

    public IReadOnlyList<Account> All => _accounts;

    public bool IsEmpty => _accounts.Count == 0;

    public Account? Find(string? id) =>
        id is null ? null : _accounts.FirstOrDefault(a => a.Id == id);

    public Account? Selected
    {
        get
        {
            Account? chosen = Find(_selected);
            return chosen ?? (_accounts.Count > 0 ? _accounts[0] : null);
        }
    }

    public void Select(string id)
    {
        if (Find(id) is not null)
        {
            _selected = id;
            Save();
        }
    }

    public void Add(Account account)
    {
        if (string.IsNullOrWhiteSpace(account.Id))
        {
            account.Id = account.Type + ":" + account.Username + ":" + Guid.NewGuid().ToString("N")[..8];
        }
        _accounts.RemoveAll(a => a.Id == account.Id);
        _accounts.Add(account);
        _selected = account.Id;
        Save();
    }

    public void Update(Account account)
    {
        int index = _accounts.FindIndex(a => a.Id == account.Id);
        if (index >= 0)
        {
            _accounts[index] = account;
            Save();
        }
        else
        {
            Add(account);
        }
    }

    public bool Remove(string id)
    {
        bool removed = _accounts.RemoveAll(a => a.Id == id) > 0;
        if (removed)
        {
            if (_selected == id)
            {
                _selected = _accounts.Count > 0 ? _accounts[0].Id : "";
            }
            Save();
        }
        return removed;
    }

    public void Save() => Json.Write(_file, new AccountDocument
    {
        Accounts = _accounts,
        Selected = _selected,
    });
}
