import dev.cookielauncher.core.AppPaths;
import dev.cookielauncher.core.config.Account;
import dev.cookielauncher.core.config.AccountStore;
import dev.cookielauncher.core.config.Config;
import dev.cookielauncher.core.config.Instance;
import dev.cookielauncher.core.config.InstanceStore;
import dev.cookielauncher.core.log.Log;
import dev.cookielauncher.ui.AppContext;
import dev.cookielauncher.ui.LauncherWindow;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

/** Renders the real launcher window to a PNG for the README. */
public final class ShotGui {

    public static void main(String[] args) throws Exception {
        Path home = Path.of(System.getProperty("java.io.tmpdir"), "cookie-full");
        AppPaths.setRoot(home);
        AppPaths.ensureDirectories();
        Log.setFile(AppPaths.launcherLog());

        String version = args.length > 0 ? args[0] : "1.20.1";
        String themeId = args.length > 1 ? args[1] : "caramel-dark";
        String output = args.length > 2 ? args[2] : "gui.png";

        Config config = Config.load(AppPaths.configFile());
        config.themeId = themeId;
        InstanceStore instances = InstanceStore.load(AppPaths.instancesFile());
        if (instances.isEmpty()) {
            instances.create("Survival world", version);
        } else {
            Instance current = instances.selected().copy();
            current.version = version;
            instances.update(current);
        }
        AccountStore accounts = AccountStore.load(AppPaths.accountsFile());
        if (accounts.isEmpty()) {
            accounts.add(Account.offline("Steve"));
        }

        AppContext context = new AppContext(config, instances, accounts);
        LauncherWindow window = new LauncherWindow(context);
        window.setSize(1240, 800);
        window.setLocation(40, 40);
        window.setVisible(true);
        // A plain setVisible can leave the window behind the terminal that launched
        // it, which makes Robot capture the desktop instead of the launcher.
        window.toFront();
        window.requestFocus();
        window.setAlwaysOnTop(true);
        Thread.sleep(600);
        window.setAlwaysOnTop(false);
        window.toFront();
        Thread.sleep(2000);

        // Capture the window's own content directly: this works even when another
        // window owns the foreground, and it excludes the OS title bar.
        BufferedImage image = new BufferedImage(
                window.getContentPane().getWidth(), window.getContentPane().getHeight(),
                BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        window.getContentPane().paint(graphics);
        graphics.dispose();
        ImageIO.write(image, "png", new File(output));
        System.out.println("saved " + output + " ("
                + image.getWidth() + "x" + image.getHeight() + ")");
        window.dispose();
        System.exit(0);
    }
}
