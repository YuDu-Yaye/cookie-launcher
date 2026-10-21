package dev.cookielauncher.ui;

import dev.cookielauncher.core.AppPaths;
import dev.cookielauncher.core.config.AccountStore;
import dev.cookielauncher.core.config.Config;
import dev.cookielauncher.core.config.Instance;
import dev.cookielauncher.core.config.InstanceStore;
import dev.cookielauncher.core.log.Log;
import dev.cookielauncher.core.net.Http;
import dev.cookielauncher.core.net.Mirror;
import dev.cookielauncher.core.net.NetworkProbe;
import dev.cookielauncher.game.GameLauncher;
import dev.cookielauncher.ui.theme.Theme;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Shared application state: configuration, stores, the launcher and the theme.
 * Pages receive this instead of reaching for statics, which keeps them testable
 * and makes the theme switch a single assignment.
 */
public final class AppContext {

    private final Config config;
    private final InstanceStore instances;
    private final AccountStore accounts;
    private final GameLauncher launcher;
    private final List<Consumer<String>> logListeners = new CopyOnWriteArrayList<>();
    private Theme theme;
    private Consumer<String> statusSink = message -> {
    };
    private Consumer<Theme> themeListener = newTheme -> {
    };

    public AppContext(Config config, InstanceStore instances, AccountStore accounts) {
        this.config = config;
        this.instances = instances;
        this.accounts = accounts;
        this.launcher = new GameLauncher(config);
        this.theme = Theme.byId(config.themeId);
        Log.addListener(line -> {
            for (Consumer<String> listener : logListeners) {
                listener.accept(line);
            }
        });
    }

    /** Loads everything from disk and applies network settings. */
    public static AppContext bootstrap() {
        AppPaths.ensureDirectories();
        Log.setFile(AppPaths.launcherLog());
        Config config = Config.load(AppPaths.configFile());
        Http.setProxy(config.proxy);
        if (config.maxMemoryMb <= 0) {
            config.maxMemoryMb = Config.defaultMaxMemoryMb();
        }
        InstanceStore instances = InstanceStore.load(AppPaths.instancesFile());
        AccountStore accounts = AccountStore.load(AppPaths.accountsFile());
        return new AppContext(config, instances, accounts);
    }

    /**
     * Probes the two download hosts and, in AUTO mode, records which one is faster
     * so the first install already uses the better path.
     */
    public NetworkProbe.Result probeNetwork() {
        NetworkProbe.Result result = NetworkProbe.probe();
        if (config.mirrorPolicy() == Mirror.Policy.AUTO) {
            Log.info("AUTO policy resolved to " + result.recommendation()
                    + " for this session (" + result.describe() + ")");
        }
        return result;
    }

    public Config config() {
        return config;
    }

    public InstanceStore instances() {
        return instances;
    }

    public AccountStore accounts() {
        return accounts;
    }

    public GameLauncher launcher() {
        return launcher;
    }

    public Theme theme() {
        return theme;
    }

    public void setTheme(Theme value) {
        this.theme = value;
        config.themeId = value.id;
        themeListener.accept(value);
    }

    public void onThemeChanged(Consumer<Theme> listener) {
        this.themeListener = listener == null ? newTheme -> {
        } : listener;
    }

    public void onLog(Consumer<String> listener) {
        logListeners.add(listener);
    }

    public void removeLogListener(Consumer<String> listener) {
        logListeners.remove(listener);
    }

    public void setStatusSink(Consumer<String> sink) {
        this.statusSink = sink == null ? message -> {
        } : sink;
    }

    public void status(String message) {
        statusSink.accept(message);
    }

    public void saveConfig() {
        config.normalize();
        try {
            config.save(AppPaths.configFile());
        } catch (IOException e) {
            Log.error("Cannot save settings", e);
        }
        Http.setProxy(config.proxy);
    }

    /** Selected instance, creating a first one on a fresh install. */
    public Instance ensureInstance() {
        Instance selected = instances.selected();
        if (selected != null) {
            return selected;
        }
        List<String> installed = dev.cookielauncher.game.install.GameInstaller.installedVersions();
        String version = installed.isEmpty() ? "" : installed.get(0);
        return instances.create("My world", version);
    }

    public Path dataDirectory() {
        return AppPaths.root();
    }

    public void shutdown() {
        saveConfig();
        launcher.stop();
    }
}
