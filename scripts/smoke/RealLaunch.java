import dev.cookielauncher.core.AppPaths;
import dev.cookielauncher.core.config.Account;
import dev.cookielauncher.core.config.Config;
import dev.cookielauncher.core.config.Instance;
import dev.cookielauncher.core.util.Fs;
import dev.cookielauncher.core.util.Progress;
import dev.cookielauncher.game.GameLauncher;
import dev.cookielauncher.game.java.JavaLocator;
import dev.cookielauncher.game.java.JavaRuntime;
import dev.cookielauncher.game.launch.GameProcess;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * End-to-end launch check: installs nothing, but requires an installed version.
 * Run FullInstall first, then this. It starts a real Minecraft process and waits
 * for the log to show that the game reached its main loop.
 */
public final class RealLaunch {

    public static void main(String[] args) throws Exception {
        Path home = Path.of(System.getProperty("java.io.tmpdir"), "cookie-full");
        AppPaths.setRoot(home);
        AppPaths.ensureDirectories();

        String version = args.length > 0 ? args[0] : "1.20.1";
        System.out.println("== detected Java runtimes");
        JavaLocator locator = new JavaLocator();
        for (JavaRuntime runtime : locator.all()) {
            System.out.println("   " + runtime.label() + "  " + runtime.executable());
        }
        JavaRuntime selected = locator.select(17, null);
        if (selected == null) {
            System.out.println("!! no Java 17+ available; cannot launch");
            return;
        }
        System.out.println("== will use " + selected.label());

        Config config = new Config();
        config.maxMemoryMb = 2048;
        config.jvmArgs = "-XX:+UseG1GC";
        GameLauncher launcher = new GameLauncher(config);

        Instance instance = new Instance();
        instance.id = "smoke";
        instance.name = "Smoke";
        instance.version = version;
        instance.gameDir = home.resolve("gamedir").toString();
        Fs.mkdirs(Path.of(instance.gameDir));

        CountDownLatch finished = new CountDownLatch(1);
        StringBuilder captured = new StringBuilder();
        AtomicBoolean reachedMainLoop = new AtomicBoolean(false);

        GameProcess process = launcher.launch(instance, Account.offline("SmokeTester"),
                Progress.noop(), line -> {
                    captured.append(line).append('\n');
                    if (line.contains("Setting user") || line.contains("Backend library")) {
                        reachedMainLoop.set(true);
                        System.out.println("  [game] " + line.trim());
                    }
                });
        System.out.println("== started pid " + process.pid());
        process.onExit(code -> {
            System.out.println("== exited with code " + code);
            finished.countDown();
        });

        boolean exited = finished.await(90, TimeUnit.SECONDS);
        if (!exited) {
            System.out.println("== still running after 90s, which means the window is up");
            process.stop();
            Thread.sleep(3000);
        }
        System.out.println("== reached the game main loop: " + reachedMainLoop.get());
        System.out.println("== captured " + captured.toString().lines().count() + " log lines");
        System.out.println(reachedMainLoop.get() ? "REAL LAUNCH PASSED" : "REAL LAUNCH FAILED");
    }
}
