package dev.cookielauncher.core.config;

import dev.cookielauncher.core.json.Json;
import dev.cookielauncher.core.json.JsonArray;
import dev.cookielauncher.core.json.JsonObject;
import dev.cookielauncher.core.util.Fs;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Persistent account list. */
public final class AccountStore {

    private final Path file;
    private final List<Account> accounts = new ArrayList<>();
    private String selectedId = "";

    private AccountStore(Path file) {
        this.file = file;
    }

    public static AccountStore load(Path file) {
        AccountStore store = new AccountStore(file);
        if (file != null && Fs.exists(file)) {
            try {
                JsonObject root = Json.parseObject(file);
                store.selectedId = root.getString("selected", "");
                JsonArray array = root.getArray("accounts");
                if (array != null) {
                    for (JsonObject object : array.objects()) {
                        store.accounts.add(Account.fromJson(object));
                    }
                }
            } catch (Exception ignored) {
                // Corrupt store: users can re-add accounts.
            }
        }
        return store;
    }

    public synchronized List<Account> all() {
        return new ArrayList<>(accounts);
    }

    public synchronized boolean isEmpty() {
        return accounts.isEmpty();
    }

    public synchronized Account find(String id) {
        if (id == null) {
            return null;
        }
        for (Account account : accounts) {
            if (account.id.equals(id)) {
                return account;
            }
        }
        return null;
    }

    public synchronized Account selected() {
        Account account = find(selectedId);
        return account != null ? account : (accounts.isEmpty() ? null : accounts.get(0));
    }

    public synchronized void select(String id) {
        if (find(id) != null) {
            selectedId = id;
            save();
        }
    }

    public synchronized void add(Account account) {
        if (account.id == null || account.id.isBlank()) {
            account.id = account.type + ":" + account.username + ":" + System.nanoTime();
        }
        accounts.removeIf(existing -> existing.id.equals(account.id));
        accounts.add(account);
        selectedId = account.id;
        save();
    }

    public synchronized void update(Account account) {
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).id.equals(account.id)) {
                accounts.set(i, account);
                save();
                return;
            }
        }
        add(account);
    }

    public synchronized boolean remove(String id) {
        boolean removed = accounts.removeIf(account -> account.id.equals(id));
        if (removed) {
            if (id.equals(selectedId)) {
                selectedId = accounts.isEmpty() ? "" : accounts.get(0).id;
            }
            save();
        }
        return removed;
    }

    public synchronized void save() {
        if (file == null) {
            return;
        }
        JsonArray array = new JsonArray();
        for (Account account : accounts) {
            array.add(account.toJson());
        }
        JsonObject root = JsonObject.of("accounts", array, "selected", selectedId);
        try {
            Fs.writeAtomic(file, Json.pretty(root));
        } catch (IOException e) {
            throw new Fs.UncheckedIoException("Cannot save accounts to " + file, e);
        }
    }
}
