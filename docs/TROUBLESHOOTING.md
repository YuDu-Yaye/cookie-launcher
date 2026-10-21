# Troubleshooting

Start with the built-in diagnostics; they usually name the problem outright.

```bash
cookie-launcher doctor              # environment, Java, network reachability
cookie-launcher analyze <dir>       # explain a crash from an instance directory
```

---

## The launcher will not start

| Symptom | Cause | Fix |
|---|---|---|
| Nothing happens when you double-click | Running from a jar without Java, or macOS quarantining the app | Use the platform bundle from Releases; on macOS run `xattr -dr com.apple.quarantine "Cookie Launcher.app"` |
| Window opens blank then closes | A corrupt `config.json` | The launcher already falls back to defaults; check `logs/launcher.log` |
| `Startup failed` dialog | Anything during boot | The dialog names the log file; read the last 30 lines |

---

## Installation problems

### A download fails

The launcher already retries the mirror. If both fail:

- Check whether you are behind a proxy: **Settings → Downloads → Proxy**.
- Try forcing the mirror in **Settings → Downloads → Source**.
- Corporate networks sometimes block `piston-data.mojang.com`; the mirror exists for this.

### "file(s) failed; run install again to resume"

Partial downloads are kept as `.part` files and resumed. Simply run the same install again.

### Repair does nothing

Repair verifies every file against its SHA-1. If it reports success, the installation really
is intact, and the problem lies elsewhere (usually Java or a mod).

---

## Java problems

### "The Java version is too old for this Minecraft version"

| Minecraft | Required Java |
|---|---|
| 1.7.10 – 1.16.5 | Java 8 |
| 1.17 – 1.20.4 | Java 17 |
| 1.20.5 and newer | Java 21 |

Open **Settings → Java**, pick a newer runtime, or let the launcher provision one. The
launcher always chooses the *lowest* runtime that satisfies the version, because old mods
break on new JDKs.

### No Java detected at all

That is fine: on first launch the launcher downloads a Mojang-published runtime matching the
version. This requires network access once.

### The game ran out of memory

Raise the maximum memory in **Settings → Memory**. Large modpacks commonly need 6–8 GB. Also
confirm the runtime is 64-bit; a 32-bit JVM cannot exceed roughly 1.5 GB.

Do not set the maximum to your total system memory: the OS and the game's off-heap buffers
need room too.

---

## Launch problems

### The game exits immediately after the window flashes

Run `cookie-launcher analyze <instance-dir>`. Common causes are a mod built for a different
loader, a missing dependency, or a graphics driver that cannot create an OpenGL context.

### "Failed to verify authentication" (401)

Expected for **offline accounts**: the game asks Mojang about a session token that does not
exist. It is logged as an error but does not stop singleplayer.

For online accounts this means the token expired or the account does not own Java Edition.
Sign out and sign in again.

### GLFW error / "Failed to create window"

The GPU driver could not provide the required OpenGL context.

- Update the driver from the vendor (NVIDIA, AMD, Intel), not through Windows Update.
- On laptops with dual GPUs, force the launcher onto the dedicated GPU.
- Remote desktop sessions often cannot create a hardware context; run the game locally.

### Linux: shared library errors

Install the runtime packages the game needs:

```bash
# Debian / Ubuntu
sudo apt install libgl1-mesa-glx libopenal1 libx11-6 libxext6 libxrandr2 libxcursor1 libxi6
# Fedora
sudo dnf install mesa-libGL openal-soft libX11 libXext libXrandr libXcursor libXi
```

### The launcher says a version is "Not installed" but the files exist

Verify the version JSON is present:

```bash
ls "<data-dir>/versions/1.20.1/1.20.1.json"
```

If only the `.jar` is there, the metadata download was interrupted. Run the install again; it
will fetch only the missing JSON.

---

## Java 21 specific: "Unsupported JNI version detected"

A warning from LWJGL on newer JDKs. Harmless; the game continues normally.

---

## Reporting an issue

Use `cookie-launcher doctor` and copy its output, then attach the last ~200 lines of
`logs/launcher.log`. If the game crashed, include the output of
`cookie-launcher analyze <instance-dir>`.

Never paste an access token. If you find one in a log, report it privately: that is a
security bug, not a support question.
