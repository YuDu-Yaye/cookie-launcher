package dev.cookielauncher.cli;

import dev.cookielauncher.core.AppInfo;
import dev.cookielauncher.core.AppPaths;
import dev.cookielauncher.core.config.Account;
import dev.cookielauncher.core.config.AccountStore;
import dev.cookielauncher.core.config.Config;
import dev.cookielauncher.core.config.Instance;
import dev.cookielauncher.core.config.InstanceStore;
import dev.cookielauncher.core.log.Log;
import dev.cookielauncher.core.net.Http;
import dev.cookielauncher.core.net.Mirror;
import dev.cookielauncher.core.net.NetworkProbe;
import dev.cookielauncher.core.util.Fmt;
import dev.cookielauncher.core.util.Os;
import dev.cookielauncher.core.util.Progress;
import dev.cookielauncher.game.diagnose.CrashAnalyzer;
import dev.cookielauncher.game.install.GameInstaller;
import dev.cookielauncher.game.java.JavaLocator;
import dev.cookielauncher.game.java.JavaRuntime;
import dev.cookielauncher.game.loader.LoaderInstaller;
import dev.cookielauncher.game.loader.LoaderType;
import dev.cookielauncher.game.loader.LoaderVersion;
import dev.cookielauncher.game.launch.GameProcess;
import dev.cookielauncher.game.meta.VersionManifest;
import dev.cookielauncher.game.meta.VersionMeta;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Headless command line.
 *
 * <p>Every GUI action has a CLI equivalent, which is the parity gap the market
 * research identified across all twelve launchers. Exit codes: 0 success,
 * 1 runtime failure, 2 bad arguments.</summary>
 */
public final class Cli {

    private Cli() {
    }

    public static void printHelp() {
        System.out.println("""
                Cookie Launcher - open source Minecraft: Java Edition launcher

                Usage:
                  cookie-launcher                        Start the graphical launcher
                  cookie-launcher list                   List available and installed versions
                  cookie-launcher install <version>      Install a vanilla version
                  cookie-launcher install <version> --loader fabric --loader-version 0.16.5
                  cookie-launcher launch <version>       Launch a version immediately
                  cookie-launcher repair <version>       Verify and repair every file
                  cookie-launcher java                   List detected Java runtimes
                  cookie-launcher accounts               List configured accounts
                  cookie-launcher doctor                 Environment diagnostics
                  cookie-launcher analyze [dir]          Diagnose a crash from an instance directory

                Options:
                  --home <dir>       Override the data directory
                  --source <policy>  auto | official | mirror
                  --proxy <host:port>
                  --wait             Follow the launched game until it exits
                  --debug            Verbose logging
                """);
    }

    public static void run(String[] args, String command) {
        int exit = 0;
        try {
            String home = valueOf(args, "--home");
            if (home != null && !home.isBlank()) {
                AppPaths.setRoot(Path.of(home));
            }
            AppPaths.ensureDirectories();
            Log.setFile(AppPaths.launcherLog());
            Log.setDebug(hasFlag(args, "--debug"));

            Config config = Config.load(AppPaths.configFile());
            String proxy = valueOf(args, "--proxy");
            if (proxy != null) {
                config.proxy = proxy;
            }
            String source = valueOf(args, "--source");
            if (source != null) {
                config.mirror = Mirror.parse(source).name();
            }
            Http.setProxy(config.proxy);

            switch (command) {
                case "version" -> System.out.println(AppInfo.NAME + " " + AppInfo.VERSION);
                case "list" -> list(config);
                case "install" -> install(args, config);
                case "launch" -> launch(args, config);
                case "repair" -> repair(args, config);
                case "java" -> java();
                case "accounts" -> accounts();
                case "doctor" -> doctor();
                case "analyze" -> analyze(args);
                default -> {
                    printHelp();
                    exit = 2;
                }
            }
        } catch (Exception e) {
            System.err.println("error: " + e.getMessage());
            if (hasFlag(args, "--debug")) {
                e.printStackTrace();
            }
            exit = 1;
        }
        if (exit != 0) {
            System.exit(exit);
        }
    }

    private static void list(Config config) throws Exception {
        System.out.println("Fetching the version manifest (" + config.mirrorPolicy() + ")");
        VersionManifest manifest = new GameInstaller(config.mirrorPolicy(), config.downloadWorkers)
                .metaService().manifest(0L);
        System.out.println("Latest release: " + manifest.latestRelease
                + "   Latest snapshot: " + manifest.latestSnapshot);
        System.out.println();
        System.out.println("Available (" + manifest.versions.size() + "):");
        int shown = 0;
        for (VersionManifest.Entry entry : manifest.filter(false, false, "")) {
            if (shown++ >= 30) {
                break;
            }
            System.out.printf("  %-24s %-10s %s%n", entry.id(), entry.displayType(), entry.shortDate());
        }
        List<String> installed = GameInstaller.installedVersions();
        System.out.println();
        System.out.println("Installed (" + installed.size() + "):");
        for (String id : installed) {
            System.out.println("  " + id);
        }
    }

    private static void install(String[] args, Config config) throws Exception {
        String version = positional(args, 0);
        if (version == null) {
            throw new IllegalArgumentException("install requires a version id");
        }
        LoaderType loader = LoaderType.parse(valueOf(args, "--loader"));
        String loaderVersion = valueOf(args, "--loader-version");
        Progress progress = consoleProgress();
        if (loader == LoaderType.VANILLA) {
            new GameInstaller(config.mirrorPolicy(), config.downloadWorkers)
                    .install(version, progress);
            System.out.println();
            System.out.println("Installed " + version);
            return;
        }
        LoaderInstaller installer = new LoaderInstaller(config.mirrorPolicy(), config.downloadWorkers);
        if (loaderVersion == null) {
            LoaderVersion latest = installer.latest(loader, version);
            if (latest == null) {
                throw new IllegalStateException("No " + loader.display() + " build for " + version);
            }
            loaderVersion = latest.version();
        }
        String installedId = installer.install(loader, version, loaderVersion, progress);
        System.out.println();
        System.out.println("Installed " + installedId);
    }

    private static void launch(String[] args, Config config) throws Exception {
        String version = positional(args, 0);
        if (version == null) {
            throw new IllegalArgumentException("launch requires a version id");
        }
        InstanceStore instances = InstanceStore.load(AppPaths.instancesFile());
        AccountStore accounts = AccountStore.load(AppPaths.accountsFile());
        Account account = accounts.selected();
        if (account == null) {
            account = Account.offline(System.getProperty("user.name", "Player"));
        }
        Instance instance = instances.findByVersion(version);
        if (instance == null) {
            instance = new Instance();
            instance.id = InstanceStore.newId();
            instance.name = version;
            instance.version = version;
            instance.gameDir = AppPaths.instance(instance.id).toString();
        }
        GameProcess process = new dev.cookielauncher.game.GameLauncher(config)
                .launch(instance, account, consoleProgress(), line -> {
                });
        System.out.println("Launched " + version + " (pid " + process.pid() + ")");
        if (hasFlag(args, "--wait")) {
            while (process.isRunning()) {
                Thread.sleep(400);
            }
            System.out.println("Exited with code " + process.exitCode());
        }
    }

    private static void repair(String[] args, Config config) throws Exception {
        String version = positional(args, 0);
        if (version == null) {
            throw new IllegalArgumentException("repair requires a version id");
        }
        VersionMeta meta = VersionMeta.resolve(AppPaths.versions(), version);
        new GameInstaller(config.mirrorPolicy(), config.downloadWorkers)
                .installFiles(meta, consoleProgress());
        System.out.println();
        System.out.println("Repaired " + version);
    }

    private static void java() {
        List<JavaRuntime> runtimes = new JavaLocator().all();
        if (runtimes.isEmpty()) {
            System.out.println("No Java runtime detected.");
            return;
        }
        System.out.println(runtimes.size() + " runtime(s):");
        for (JavaRuntime runtime : runtimes) {
            System.out.println("  " + runtime.label());
            System.out.println("    " + runtime.executable());
        }
    }

    private static void accounts() {
        AccountStore store = AccountStore.load(AppPaths.accountsFile());
        List<Account> accounts = store.all();
        if (accounts.isEmpty()) {
            System.out.println("No accounts configured.");
            return;
        }
        Account selected = store.selected();
        for (Account account : accounts) {
            boolean inUse = selected != null && selected.id.equals(account.id);
            System.out.println("  " + (inUse ? "* " : "  ") + account.label()
                    + "  uuid=" + account.uuid);
        }
    }

    private static void doctor() {
        System.out.println(AppInfo.NAME + " " + AppInfo.VERSION + " doctor");
        System.out.println("  OS           : " + Os.displayName() + " (" + Os.mojangArch() + ")");
        System.out.println("  JVM          : " + System.getProperty("java.version")
                + " (" + System.getProperty("java.vendor") + ")");
        System.out.println("  CPUs         : " + Os.cpuCount());
        System.out.println("  Memory       : " + (Os.totalMemoryMb() > 0
                ? Os.totalMemoryMb() + " MB" : "unknown"));
        System.out.println("  Data dir     : " + AppPaths.root());
        System.out.println("  Portable     : " + AppPaths.isPortable());
        System.out.println("  Writable     : " + Files.isWritable(AppPaths.root()));
        System.out.println("  Proxy        : " + (Http.proxy().isBlank() ? "(none)" : Http.proxy()));

        NetworkProbe.Result probe = NetworkProbe.probe();
        System.out.println("  Network      : " + probe.describe());

        List<JavaRuntime> runtimes = new JavaLocator().all();
        System.out.println("  Java runtimes: " + runtimes.size());
        for (JavaRuntime runtime : runtimes) {
            System.out.println("    - " + runtime.label() + "  " + runtime.executable());
        }
        List<String> installed = GameInstaller.installedVersions();
        System.out.println("  Versions     : " + (installed.isEmpty()
                ? "none" : String.join(", ", installed)));
    }

    private static void analyze(String[] args) {
        String path = positional(args, 0);
        Path directory = path == null ? Path.of(".") : Path.of(path);
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Not a directory: " + directory);
        }
        System.out.print(CrashAnalyzer.render(CrashAnalyzer.analyzeDirectory(directory)));
    }

    private static Progress consoleProgress() {
        return new Progress(new Progress.Sink() {
            private long lastPrint;

            @Override
            public void onStatus(String message) {
                System.out.println("[status] " + message);
            }

            @Override
            public void onProgress(long completed, long total, double fraction) {
                long now = System.currentTimeMillis();
                if (now - lastPrint < 350) {
                    return;
                }
                lastPrint = now;
                System.out.print("\r[bytes]  " + Fmt.bytes(completed) + " / " + Fmt.bytes(total)
                        + "  " + Fmt.percent(fraction) + "      ");
            }
        });
    }

    private static String positional(String[] args, int index) {
        int seen = 0;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                if (takesValue(arg)) {
                    i++;
                }
                continue;
            }
            if (seen == index) {
                return arg;
            }
            seen++;
        }
        return null;
    }

    private static boolean takesValue(String flag) {
        return switch (flag) {
            case "--home", "--proxy", "--source", "--loader", "--loader-version", "--account" -> true;
            default -> false;
        };
    }

    private static String valueOf(String[] args, String flag) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equalsIgnoreCase(flag)) {
                return args[i + 1];
            }
        }
        return null;
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (arg.equalsIgnoreCase(flag)) {
                return true;
            }
        }
        return false;
    }
}
