package dev.cookielauncher.game;

import dev.cookielauncher.core.AppPaths;
import dev.cookielauncher.core.config.Account;
import dev.cookielauncher.core.config.Config;
import dev.cookielauncher.core.config.Instance;
import dev.cookielauncher.core.log.Log;
import dev.cookielauncher.core.util.Fs;
import dev.cookielauncher.core.util.Progress;
import dev.cookielauncher.game.auth.AuthlibInjector;
import dev.cookielauncher.game.auth.YggdrasilAuth;
import dev.cookielauncher.game.install.GameInstaller;
import dev.cookielauncher.game.java.JavaInstaller;
import dev.cookielauncher.game.java.JavaLocator;
import dev.cookielauncher.game.java.JavaRuntime;
import dev.cookielauncher.game.launch.GameProcess;
import dev.cookielauncher.game.launch.LaunchBuilder;
import dev.cookielauncher.game.launch.LaunchCommand;
import dev.cookielauncher.game.launch.LaunchRequest;
import dev.cookielauncher.game.meta.VersionMeta;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * End-to-end launch orchestration: resolve Java, verify every file, renew
 * third-party sessions, assemble the command line and start the process.
 *
 * <p>This is the only class the UI needs in order to play, which keeps pages free
 * of installation and authentication details.</p>
 */
public final class GameLauncher {

    private final Config config;
    private final JavaLocator javaLocator = new JavaLocator();
    private volatile GameProcess process;

    public GameLauncher(Config config) {
        this.config = config;
    }

    public JavaLocator javaLocator() {
        return javaLocator;
    }

    public GameProcess process() {
        return process;
    }

    public boolean isRunning() {
        GameProcess current = process;
        return current != null && current.isRunning();
    }

    /**
     * Launches an instance and returns once the process has started; the caller
     * drives the returned process (log sink, exit callback, stop).
     */
    public GameProcess launch(Instance instance, Account account, Progress progress,
                              Consumer<String> logSink) throws IOException {
        Progress sink = progress == null ? Progress.noop() : progress;
        if (isRunning()) {
            throw new IOException("A game instance is already running");
        }
        if (instance == null) {
            throw new IOException("No instance selected");
        }
        if (instance.version == null || instance.version.isBlank()) {
            throw new IOException("This instance has no installed version yet");
        }

        String versionId = instance.version;
        sink.status("Resolving " + versionId);
        VersionMeta meta;
        try {
            meta = VersionMeta.resolve(AppPaths.versions(), versionId);
        } catch (IOException e) {
            throw new IOException("Cannot read the metadata for " + versionId
                    + "; run Repair to reinstall it", e);
        }

        Path gameDirectory = resolveGameDirectory(instance);
        Fs.mkdirs(gameDirectory);

        int required = meta.javaMajor <= 0 ? 8 : meta.javaMajor;
        String override = !instance.javaPath.isBlank() ? instance.javaPath : config.javaPath;
        JavaRuntime runtime = javaLocator.select(required, override);
        if (runtime == null) {
            sink.status("No suitable Java " + required + " found; downloading a managed runtime");
            Path managed = JavaInstaller.ensure(required, sink);
            runtime = JavaLocator.inspect(managed);
            if (runtime == null) {
                throw new IOException("The downloaded Java runtime could not be executed");
            }
            javaLocator.register(managed);
        }
        sink.status("Using " + runtime.label());

        Account usableAccount = renewIfNeeded(account, sink);

        GameInstaller installer = new GameInstaller(config.mirrorPolicy(), config.downloadWorkers);
        sink.status("Verifying files for " + versionId);
        installer.installFiles(meta, sink);

        LaunchRequest request = new LaunchRequest();
        request.instance = instance;
        request.account = usableAccount;
        request.meta = meta;
        request.javaExecutable = runtime.executable();
        request.gameDirectory = gameDirectory;
        request.assetsDirectory = AppPaths.assets();
        request.librariesDirectory = AppPaths.libraries();
        request.nativesDirectory = AppPaths.natives(versionId);
        request.clientJar = AppPaths.version(versionId).resolve(versionId + ".jar");
        request.maxMemoryMb = instance.memoryMb > 0 ? instance.memoryMb : config.maxMemoryMb;
        request.minMemoryMb = Math.min(config.minMemoryMb, request.maxMemoryMb);
        request.extraJvmArgs = joinArgs(config.jvmArgs, instance.jvmArgs);
        request.extraGameArgs = config.gameArgs;
        request.windowWidth = config.windowWidth;
        request.windowHeight = config.windowHeight;
        request.fullscreen = config.fullscreen;
        request.server = "";

        // Modern versions ship a log4j configuration and expect the launcher to
        // pass -Dlog4j.configurationFile. Without it the game logs in a different
        // format and crash reports lose their structured header.
        if (!meta.loggingConfigName().isBlank()) {
            Path loggingFile = AppPaths.logging().resolve(meta.loggingConfigName());
            if (Fs.exists(loggingFile)) {
                request.loggingConfig = loggingFile;
                request.loggingArgument = meta.loggingArgument()
                        .replace("${path}", loggingFile.toString());
            } else {
                Log.warn("Logging configuration " + meta.loggingConfigName() + " is missing; "
                        + "run Repair to fetch it");
            }
        }

        if (usableAccount != null && "authlib".equals(usableAccount.type)) {
            applyAuthlib(request, usableAccount, sink);
        }

        LaunchCommand command = LaunchBuilder.build(request);
        Log.info("Launching instance '" + instance.name + "' (" + versionId + ")");
        GameProcess gameProcess = GameProcess.start(command, logSink);
        this.process = gameProcess;
        sink.status("Minecraft started (pid " + gameProcess.pid() + ")");
        return gameProcess;
    }

    /** Renews an authlib session when its token is old; offline accounts pass through. */
    private Account renewIfNeeded(Account account, Progress sink) {
        if (account == null || !"authlib".equals(account.type)) {
            return account;
        }
        if (!account.isExpired() && !account.accessToken.isBlank()) {
            return account;
        }
        try {
            sink.status("Renewing the session for " + account.username);
            Account renewed = new YggdrasilAuth(account.serverUrl).refresh(account);
            renewed.id = account.id;
            return renewed;
        } catch (IOException e) {
            Log.warn("Session renewal failed: " + e.getMessage());
            return account;
        }
    }

    private void applyAuthlib(LaunchRequest request, Account account, Progress sink) throws IOException {
        sink.status("Preparing authlib-injector");
        Path agent = AuthlibInjector.ensure();
        request.authlibAgent = agent;
        request.authlibServer = account.serverUrl;
        String base = request.extraJvmArgs == null ? "" : request.extraJvmArgs + " ";
        request.extraJvmArgs = base + "-javaagent:" + agent + "=" + AuthlibInjector.apiRoot(account.serverUrl);
    }

    private Path resolveGameDirectory(Instance instance) {
        if (instance.gameDir != null && !instance.gameDir.isBlank()) {
            return Path.of(instance.gameDir);
        }
        return AppPaths.instance(instance.id);
    }

    private static String joinArgs(String first, String second) {
        String a = first == null ? "" : first.trim();
        String b = second == null ? "" : second.trim();
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        return a + " " + b;
    }

    public void stop() {
        GameProcess current = process;
        if (current != null) {
            current.stop();
        }
    }

    /** Human-readable reason the launch cannot proceed, or an empty string. */
    public static String preflight(Instance instance, Account account) {
        if (instance == null) {
            return "No instance selected";
        }
        if (instance.version == null || instance.version.isBlank()) {
            return "Choose a version for this instance first";
        }
        if (!GameInstaller.isInstalled(instance.version)) {
            return "Version " + instance.version + " is not installed";
        }
        if (account == null) {
            return "Add an account before launching";
        }
        return "";
    }
}
