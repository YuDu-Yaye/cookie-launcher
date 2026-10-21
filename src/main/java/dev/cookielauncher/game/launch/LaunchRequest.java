package dev.cookielauncher.game.launch;

import dev.cookielauncher.core.config.Account;
import dev.cookielauncher.core.config.Instance;
import dev.cookielauncher.game.meta.VersionMeta;

import java.nio.file.Path;

/** Everything needed to assemble one launch command line. */
public final class LaunchRequest {

    public Instance instance;
    public Account account;
    public VersionMeta meta;
    public Path javaExecutable;
    public Path gameDirectory;
    public Path assetsDirectory;
    public Path librariesDirectory;
    public Path nativesDirectory;
    public Path clientJar;

    public int minMemoryMb = 512;
    public int maxMemoryMb = 4096;
    public String extraJvmArgs = "";
    public String extraGameArgs = "";
    public String server = "";
    public int windowWidth = 1280;
    public int windowHeight = 720;
    public boolean fullscreen;
    public boolean demo;

    /** Optional authlib-injector agent for third-party authentication. */
    public Path authlibAgent;
    public String authlibServer = "";

    /** Optional logging configuration the version declares. */
    public Path loggingConfig;
    public String loggingArgument = "";
}
