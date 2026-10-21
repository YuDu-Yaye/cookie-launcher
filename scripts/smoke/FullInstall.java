import dev.cookielauncher.core.AppPaths;
import dev.cookielauncher.core.net.Mirror;
import dev.cookielauncher.core.util.Fmt;
import dev.cookielauncher.core.util.Fs;
import dev.cookielauncher.core.util.Progress;
import dev.cookielauncher.game.install.GameInstaller;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * End-to-end installation check against the live Mojang CDN.
 * Not part of the unit suite: it requires network access.
 */
public final class FullInstall {

    public static void main(String[] args) throws Exception {
        Path home = Path.of(System.getProperty("java.io.tmpdir"), "cookie-full");
        AppPaths.setRoot(home);
        AppPaths.ensureDirectories();
        String version = args.length > 0 ? args[0] : "1.20.1";
        System.out.println("== installing " + version + " into " + AppPaths.root());

        Progress progress = new Progress(new Progress.Sink() {
            @Override
            public void onStatus(String message) {
                System.out.println("  " + message);
            }

            @Override
            public void onProgress(long done, long total, double fraction) {
            }
        });

        long start = System.currentTimeMillis();
        new GameInstaller(Mirror.Policy.AUTO, 12).install(version, progress);
        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("== installed in %.1f s%n", elapsed / 1000.0);

        System.out.printf("libraries: %d files, %s%n",
                Fs.walk(AppPaths.libraries(), 12).size(), Fmt.bytes(Fs.directorySize(AppPaths.libraries())));
        System.out.printf("assets:    %d objects, %s%n",
                Fs.walk(AppPaths.assetObjects(), 12).size(), Fmt.bytes(Fs.directorySize(AppPaths.assetObjects())));
        System.out.printf("client:    %s%n",
                Fmt.bytes(Fs.size(AppPaths.version(version).resolve(version + ".jar"))));
        System.out.printf("metadata:  %s%n", Files.isRegularFile(
                AppPaths.version(version).resolve(version + ".json")) ? "present" : "MISSING");
        System.out.printf("natives:   %d files%n", Fs.walk(AppPaths.natives(version), 2).size());
        System.out.printf("installed: %s%n", GameInstaller.installedVersions());

        long second = System.currentTimeMillis();
        new GameInstaller(Mirror.Policy.AUTO, 12).install(version, progress);
        System.out.printf("== second install (must transfer nothing): %.2f s%n",
                (System.currentTimeMillis() - second) / 1000.0);
        System.out.println("FULL INSTALL PASSED");
    }
}
