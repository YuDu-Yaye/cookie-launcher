package dev.cookielauncher.core.config;

import dev.cookielauncher.core.AppPaths;
import dev.cookielauncher.core.json.Json;
import dev.cookielauncher.core.json.JsonArray;
import dev.cookielauncher.core.json.JsonObject;
import dev.cookielauncher.core.util.Fs;

import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/** Persistent instance list, newest activity first when queried sorted. */
public final class InstanceStore {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private final Path file;
    private final List<Instance> instances = new ArrayList<>();
    private String selectedId = "";

    private InstanceStore(Path file) {
        this.file = file;
    }

    public static InstanceStore load(Path file) {
        InstanceStore store = new InstanceStore(file);
        if (file != null && Fs.exists(file)) {
            try {
                JsonObject root = Json.parseObject(file);
                store.selectedId = root.getString("selected", "");
                JsonArray array = root.getArray("instances");
                if (array != null) {
                    for (JsonObject object : array.objects()) {
                        Instance instance = Instance.fromJson(object);
                        if (instance.id != null && !instance.id.isBlank()) {
                            store.instances.add(instance);
                        }
                    }
                }
            } catch (Exception ignored) {
                // Corrupt store: start empty rather than refusing to launch.
            }
        }
        return store;
    }

    public synchronized List<Instance> all() {
        return new ArrayList<>(instances);
    }

    public synchronized boolean isEmpty() {
        return instances.isEmpty();
    }

    public synchronized Instance find(String id) {
        if (id == null) {
            return null;
        }
        for (Instance instance : instances) {
            if (instance.id.equals(id)) {
                return instance;
            }
        }
        return null;
    }

    public synchronized Instance selected() {
        Instance instance = find(selectedId);
        return instance != null ? instance : (instances.isEmpty() ? null : instances.get(0));
    }

    /** First instance pointing at a given installed version, or null. */
    public synchronized Instance findByVersion(String version) {
        if (version == null) {
            return null;
        }
        for (Instance instance : instances) {
            if (version.equals(instance.version)) {
                return instance;
            }
        }
        return null;
    }

    public synchronized void select(String id) {
        if (find(id) != null) {
            selectedId = id;
            save();
        }
    }

    /** Creates an instance with its own game directory and returns it. */
    public synchronized Instance create(String name, String version) {
        Instance instance = new Instance();
        instance.id = newId();
        instance.name = name == null || name.isBlank()
                ? "Instance " + (instances.size() + 1) : name.trim();
        instance.version = version == null ? "" : version;
        instance.gameDir = AppPaths.instance(instance.id).toString();
        Fs.mkdirs(Path.of(instance.gameDir).resolve("mods"));
        instances.add(instance);
        selectedId = instance.id;
        save();
        return instance;
    }

    public synchronized void add(Instance instance) {
        if (instance.id == null || instance.id.isBlank()) {
            instance.id = newId();
        }
        instances.removeIf(existing -> existing.id.equals(instance.id));
        instances.add(instance);
        selectedId = instance.id;
        save();
    }

    public synchronized void update(Instance instance) {
        for (int i = 0; i < instances.size(); i++) {
            if (instances.get(i).id.equals(instance.id)) {
                instances.set(i, instance);
                save();
                return;
            }
        }
        add(instance);
    }

    public synchronized boolean remove(String id) {
        boolean removed = instances.removeIf(instance -> instance.id.equals(id));
        if (removed) {
            if (id.equals(selectedId)) {
                selectedId = instances.isEmpty() ? "" : instances.get(0).id;
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
        for (Instance instance : instances) {
            array.add(instance.toJson());
        }
        JsonObject root = JsonObject.of("instances", array, "selected", selectedId);
        try {
            Fs.writeAtomic(file, Json.pretty(root));
        } catch (IOException e) {
            throw new Fs.UncheckedIoException("Cannot save instances to " + file, e);
        }
    }

    public static String newId() {
        StringBuilder builder = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            builder.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }
}
