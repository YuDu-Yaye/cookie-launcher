# Minecraft Launcher Landscape: Architecture, Core, Downloads, GUI and Trade-offs

**Scope.** Every launcher below was inspected on 2026-10-21 through the GitHub REST API
(repository metadata, language byte shares, top-level trees, latest release asset names) plus each
project's own `README` / `BUILD` / build-manifest files. Star counts, licences and last-push dates
are from that date. Behavioural claims sourced from issue trackers, release notes or community
discussion are marked *reported*. If a subsystem could not be confirmed from the repository, the
cell says so instead of guessing.

**Why this document exists.** Cookie Launcher is a from-scratch launcher. Before product code we
need to know which architecture survives a decade (MultiMC's instance model), which download
strategy survives real networks (HMCL's provider chain), which GUI stack keeps contributors (and
which one drives them away), and which features are table stakes versus differentiators. This is
that evidence base.

---

## 1. The market at a glance

22 repositories, ordered by stars.

| # | Launcher | Repo | Stars | License | Primary stack | Archived | Last push |
|---|---|---|---|---|---|---|---|
| 1 | Prism Launcher | `PrismLauncher/PrismLauncher` | 10,799 | GPL-3.0 | C++ 93% / Qt 6 | no | 2026-10-20 |
| 2 | HMCL | `HMCL-dev/HMCL` | 10,463 | GPL-3.0 (+§7) | Java 98% / JavaFX | no | 2026-10-20 |
| 3 | PojavLauncher | `PojavLauncherTeam/PojavLauncher` | 9,447 | LGPL-3.0 | Java 62% + C 36% (Android) | no | 2025-09-23 |
| 4 | PCL2 | `Meloong-Git/PCL` | 8,279 | no OSI licence (author terms) | VB.NET 97% / WinForms | no | 2026-10-20 |
| 5 | FoldCraftLauncher | `FCL-Team/FoldCraftLauncher` | 4,998 | GPL-3.0 | Java 68% + Kotlin 16% (Android) | no | 2026-10-20 |
| 6 | MultiMC | `MultiMC/Launcher` | 4,681 | custom (Apache-2.0 + fork policy) | C++ 82% / Qt 5-6 | no | 2026-09-26 |
| 7 | PCL2 CE | `PCL-Community/PCL-CE` | 4,661 | Apache-2.0 | C# 99% / WPF + HLSL | no | 2026-10-20 |
| 8 | Modrinth App | `modrinth/code` | 2,459 | mixed (app GPL-3.0) | Rust 40% + Vue 35% + TS 22% / Tauri | no | 2026-10-21 |
| 9 | PolyMC | `PolyMC/PolyMC` | 2,104 | GPL-3.0 | C++ 90% / Qt 6 | no | 2026-10-12 |
| 10 | ZalithLauncher 2 | `ZalithLauncher/ZalithLauncher2` | 2,067 | GPL-3.0 | Kotlin 66% + Java 21% (Android) | no | 2026-10-20 |
| 11 | XMCL | `Voxelum/x-minecraft-launcher` | 1,660 | MIT | TS 73% + Vue 23% / Electron | no | 2026-10-20 |
| 12 | GDLauncher | `gorilla-devs/GDLauncher` | 1,196 | GPL-3.0 | JS 94% / Electron + React | **yes (2026-08)** | 2026-08-21 |
| 13 | HeliosLauncher | `dscalzi/HeliosLauncher` | 1,030 | MIT | JS 60% + CSS 22% + EJS 16% / Electron | no | 2026-05-04 |
| 14 | PandoraLauncher | `Moulberry/PandoraLauncher` | 935 | MIT | Rust 99% / native UI (no webview) | no | 2026-10-07 |
| 15 | FreesmLauncher | `FreesmTeam/FreesmLauncher` | 874 | GPL-3.0 | C++ 93% / Qt 6 (Prism fork) | no | 2026-10-18 |
| 16 | ATLauncher | `ATLauncher/ATLauncher` | 856 | GPL-3.0 | Java 98% / Swing | no | 2026-09-19 |
| 17 | SKCraft Launcher | `SKCraft/Launcher` | 657 | custom | Java 99% / Swing | no | 2026-04-08 |
| 18 | SJMCL | `UNIkeEN/SJMCL` | 650 | GPL-3.0 | TS 57% + Rust 39% / Tauri v2 | no | 2026-10-21 |
| 19 | portablemc | `theorzr/portablemc` | 554 | Apache-2.0 | Rust 90% | no | 2026-10-18 |
| 20 | BetaCraft Launcher | `betacraftuk/betacraft-launcher` | 440 | GPL-3.0 | Java 99% / Swing | no | 2025-02-19 |
| 21 | FluentLauncher | `Xcube-Studio/Natsurainko.FluentLauncher` | 421 | MIT | C# 99% / WinUI 3 | no | 2026-03-21 |
| 22 | headlessmc | `headlesshq/headlessmc` | 416 | MIT | Java 99% / CLI | no | 2026-10-20 |

Adjacent projects worth reading for core logic: `FabricMC/fabric-installer` (Apache-2.0),
`NeoForged/NeoForge` (LGPL-2.1), `MinecraftForge/MinecraftForge`,
`Voxelum/minecraft-launcher-core-node` (MIT).

**Reading of the table.** The two most popular launchers sit on opposite ends: a 10k-star C++/Qt
codebase and a 10k-star Java/JavaFX one. Neither language explains adoption; both have a
decade-old, battle-tested instance + download core. The one 8k-star closed-source outlier (PCL2)
wins on cold-start speed and China-network tuning, not on architecture. Every Electron launcher
that stopped shipping (GDLauncher, archived 2026-08) shows the failure mode: the UI layer is fast to
build and slow to keep alive at launcher scale.

---

## 2. Deep dives

### 2.1 Prism Launcher — C++/Qt, GPL-3.0, 10.8k

| Aspect | Detail |
|---|---|
| **Architecture** | Two trees: `launcher/` (application + UI) and `libraries/` (game logic: `launcher/`, `javacheck/`, `rainbow`, `qdcss`, `murmur2`, `libnbtplusplus`, `LocalPeer/`). Clean seam — UI never parses Mojang metadata itself. |
| **Core model** | `BaseInstance` / `BaseVersion` / `BaseVersionList`; instances are **directories** described by `mmc-pack.json` + `instance.cfg`. Isolation is structural, not bolted on. |
| **Download** | Qt `QNetworkAccessManager` jobs (`NetJob`, `DownloadJob`) plus an HTTP meta cache so version JSONs are fetched once and shared. Mirror support is coarse compared with HMCL. |
| **GUI language** | Qt 6 Widgets, `.ui` files, QSS theme engine (`qdcss`), custom `instanceview/` delegates. Native widgets, no webview, no GPU compositor needed. |
| **Release shape** | AppImage x86_64/aarch64, macOS `.dmg`/`.zip`, Windows MinGW `.zip` (x64 + arm64), portable tarballs, Flatpak, Nix flake. |
| **Strengths** | Best instance model in the market; per-instance Java and settings; Modrinth/CurseForge/FTB/Technic/ATLauncher pack support; excellent Linux/Wayland packaging; large contributor base; transparent governance after the 2022 fork. |
| **Weaknesses** | Qt/C++ narrows contribution to a community that mostly writes Java; heavy first run; no built-in authlib-injector flow, so third-party-auth users need external tools *reported*; CurseForge depends on API keys and upstream policy *reported*; occasional Qt-upgrade UI regressions. |

### 2.2 HMCL — Java/JavaFX, GPL-3.0 (+§7), 10.5k

| Aspect | Detail |
|---|---|
| **Architecture** | Three Gradle modules: `HMCL` (UI), `HMCLCore` (game logic), `HMCLBoot` (self-update + JVM bootstrap). Core has no UI imports — the cleanest separation of the three majors. |
| **Core model** | `HMCLCore/.../{game,launch,auth,modpack,java,download,task,event}`. The `task/` framework is a cancellable task graph; `download/` holds provider implementations. |
| **Download** | Download **providers** with a preference chain (Mojang official, BMCLAPI, community mirrors), executed by concurrent cancellable tasks with retry. Strongest mirror story in the ecosystem. |
| **GUI language** | JavaFX with CSS themes plus JFoenix widgets; the build explicitly opens JavaFX internals (Prism, Glass, Quantum) — deep and JDK-version-sensitive integration. |
| **Release shape** | `HMCL-3.16.4.jar` (universal), `.exe`, `.deb`, `.sh`; the boot layer self-updates the jar. |
| **Strengths** | Widest loader coverage (Forge, NeoForge, Cleanroom, Fabric, Legacy Fabric, Quilt, LiteLoader, OptiFine); automatic Java provisioning; user-selectable sources; authlib-injector accounts; x86/ARM/RISC-V/LoongArch; strong i18n. |
| **Weaknesses** | Needs a Java runtime to start — the largest first-run failure mode *reported*; JavaFX asset fetch is slow or blocked on some networks *reported*; dense UI intimidates new users; very large codebase; no actionable crash remediation beyond log display. |

### 2.3 PCL2 — VB.NET/WinForms, author licence (not OSI open source), 8.3k

| Aspect | Detail |
|---|---|
| **Architecture** | Single solution `Plain Craft Launcher 2.sln` with `MeloongCore` and `PCLCS/`. The repository is a **periodic source drop**: its README states the code is not updated live but synchronised at each PCL release. The `LICENCE` file is the author's own terms, so the project is source-available but **not open source in the OSI sense**. |
| **Core model** | Custom VB.NET launcher core: version assembly, download, Java detection, modpack install, all inside one solution. |
| **Download** | Multi-mirror with automatic selection tuned for mainland China; one-click modpack and mod install; heavy reliance on community mirrors. |
| **GUI language** | WinForms as the host window with an in-house animation/UI library painting everything — WinForms is only the shell, the visuals are hand-drawn. |
| **Release shape** | Windows-only executables distributed from the author's site; the repository publishes source archives and carries no binary release assets. |
| **Strengths** | Extremely fast cold start and low idle memory for a .NET desktop app; best China-network tuning; one-click modpack/mod; very large Chinese-speaking user base; help library maintained in a separate repo. |
| **Weaknesses** | Windows-only by construction; **not open source** and only periodically synced, so no audit, fork or port; VB.NET caps contributor supply and long-term maintainability; unsigned binaries trigger SmartScreen *reported*; single-maintainer bus factor; dense non-standard UI with weak accessibility. |

### 2.4 PCL2 Community Edition — C#/WPF, Apache-2.0, 4.7k

| Aspect | Detail |
|---|---|
| **Architecture** | `PCL.Core` (core library) + `PCL.Core.SourceGenerators` + `PCL.Core.Test` + `Plain Craft Launcher 2.slnx` (WPF app). This is what an open-source PCL2 looks like: core extracted, tests added, generators for boilerplate. |
| **Core model** | `PCL.Core/{App,IO,Link,Logging,Minecraft,Model,UI,Utils,ViewModel}` — Minecraft logic finally separated from window code. |
| **Download** | PCL2's multi-mirror strategy reimplemented in C#. |
| **GUI language** | WPF on `net10.0-windows` with HLSL pixel shaders for visual effects. |
| **Release shape** | `PCL2_CE_Release_x64.exe` / `ARM64.exe`, each with a **Sigstore bundle** (`.sigstore.json`) — the only launcher here publishing provenance metadata. |
| **Strengths** | Genuinely open (Apache-2.0) continuation of PCL2's UX; community review; faster triage; familiar workflow with modernised internals; signed provenance. |
| **Weaknesses** | Still Windows-only — WPF is not portable, so the open-source win does not deliver Linux/macOS; inherits PCL2 architectural debt; young and API-unstable; smaller ecosystem than upstream. |

### 2.5 MultiMC / PolyMC / Freesm — C++/Qt, 4.7k / 2.1k / 0.9k

| Aspect | Detail |
|---|---|
| **Architecture** | The original `launcher/` + `libraries/` split Prism inherited; `libraries/` includes `rainbow`, `quazip`, `libnbtplusplus`, `LocalPeer`. PolyMC and Freesm are direct forks with the same skeleton. |
| **Core model** | Instance-as-directory with `instance.cfg`; `BaseVersionList` + meta cache — the design the ecosystem copied. |
| **Download** | `NetJob`/`DownloadJob` over Qt network with an HTTP meta cache. Import-oriented rather than browsable-catalogue. |
| **GUI language** | Qt Widgets, native look, `MainWindow.ui`; PolyMC/Freesm add QSS theming. |
| **Release shape** | MultiMC: Windows/macOS/Linux archives, no binaries in this repo's releases. PolyMC/Freesm: AppImage, macOS tar, Windows zip, aarch64 + legacy builds. |
| **Strengths** | Proven instance model; stability-first; low memory for a Qt app; solid log tooling; MultiMC documents its toolchain properly in `BUILD.md`; PolyMC/Freesm keep the GPL guarantee and modern packaging. |
| **Weaknesses** | MultiMC's redistribution policy blocks downstream reuse (the reason Prism and PolyMC exist); slow feature velocity; catalogue integration limited; dated visuals; no third-party auth in any of the three; PolyMC permanently shadowed by the 2022 maintainer-access controversy *reported*. |

### 2.6 XMCL — TypeScript/Vue/Electron, MIT, 1.7k

| Aspect | Detail |
|---|---|
| **Architecture** | pnpm monorepo: `packages/` (~30 packages: `core`, `client`, `file-transfer`, `mod-parser`, `modrinth`, `curseforge`, `installer`, `instance`, `game-data`, `gamesetting`, `asm`, `bytebuffer`, `discord-rpc`, …) and `xmcl-runtime/` (Electron main: `agent`, `authlibInjector`, `bedrock`, `credential`, `infra`, `install`, `instance`). Genuinely modular. |
| **Core model** | Instance + **shared mod store with links**, so duplicate mods across instances cost one copy on disk. Also manages Bedrock Edition. |
| **Download** | `file-transfer` package: resumable, multi-source transfers; `.sha256` published per artifact. |
| **GUI language** | Vue 3 SFCs inside Electron; `i18n/` for translations. |
| **Release shape** | Per-platform `.asar` + `.sha256` + `.gz`; winget/Homebrew/Flathub distribution. |
| **Strengths** | Best disk-efficiency story (link-based mod store); authlib-injector built in; resumable transfers; MIT licence; modern task-oriented UI. |
| **Weaknesses** | Electron baseline memory is high for a launcher *reported*; large modpacks stress renderer/IPC; feature breadth (Java + Bedrock + mods + servers + peers) makes the UI hard to learn; uneven modpack compatibility historically *reported*; smaller contributor pool than Prism/HMCL. |

### 2.7 Modrinth App — Rust + Vue/Tauri, 2.5k

| Aspect | Detail |
|---|---|
| **Architecture** | Monorepo `apps/` (`app` = Tauri desktop, `frontend`, `labrinth` = API, `daedalus_client`, `docs`) + shared `packages/`. The app composes `daedalus` (Rust Minecraft/meta/download crate) with `ariadne` (Modrinth API client). |
| **Core model** | Instance directories; pack installation through daedalus with dependency resolution against the Modrinth catalogue. |
| **Download** | daedalus owns hashing, mirrorless CDN fetch and pack file resolution; shared by server and app. |
| **GUI language** | Vue 3 + Tauri v2 webview; Nuxt marketing site in the same monorepo. |
| **Release shape** | `.exe` (NSIS), `.dmg` universal, `.AppImage`, `.deb`, `.rpm`. |
| **Strengths** | First-party catalogue with real dependency resolution; low memory next to Electron; modern consistent UI; strong governance and funding; Rust core reusable outside the app. |
| **Weaknesses** | No CurseForge by policy, splitting the pack ecosystem; no authlib-injector flow *reported*; fewer expert-level instance/JVM knobs; young feature set versus Prism. |

### 2.8 ATLauncher / SKCraft / BetaCraft — Java Swing, 0.9k / 0.7k / 0.4k

| Aspect | Detail |
|---|---|
| **Architecture** | Single Gradle module (`src/`), Swing UI and core interleaved. SKCraft splits `launcher/`, `launcher-bootstrap/`, `launcher-builder/`, `launcher-fancy/` plus `creator-tools/`. |
| **Core model** | Pack-first: curated pack platform, server-pack tooling, one instance per pack. SKCraft generates one launcher per modpack. |
| **Download** | Own downloader against the pack CDN; ATLauncher also imports CurseForge packs. |
| **GUI language** | Java Swing, dated look; BetaCraft ships a retro skin. |
| **Release shape** | ATLauncher `.exe`/`.jar`/`.zip`; BetaCraft `.jar` + portable `.exe`. |
| **Strengths** | Simple Gradle build that is easy to compile; reliable pack/server tooling; long operational history; zero native dependencies. |
| **Weaknesses** | Swing looks and behaves dated; needs a Java runtime; small community, slow cadence; weak HiDPI and accessibility; no modern catalogue browsing. |

### 2.9 SJMCL / Pandora / FluentLauncher — modern newcomers, 0.65k / 0.94k / 0.42k

| Aspect | Detail |
|---|---|
| **Architecture** | SJMCL: Tauri v2 (`src-tauri/` Rust crates + `src/` frontend, `cli/`, `docs/`). Pandora: pure Rust workspace — `crates/{auth,backend,bridge,command,frontend,ftree,nbt,pandora_launcher,reqwest_client,schema,t}` with `vendor/` and `wrapper/`, **no webview**. FluentLauncher: WinUI 3 plus `Natsurainko.FluentCore` split out and infra projects (extension host, localisation, settings, UI). |
| **Core model** | Pandora: mod sandboxing, cross-instance options/saves sync, reflink/hardlink dedup, OS keyring credentials, automatic token redaction in logs. SJMCL: extension system, deeplinks, MCP integration. |
| **Download** | SJMCL uses CurseForge + Modrinth APIs; Pandora avoids third-party metadata servers and fetches from Mojang directly; FluentLauncher uses FluentCore's downloader. |
| **GUI language** | Tauri webview (SJMCL), native Rust UI widgets (Pandora), WinUI 3/XAML (FluentLauncher). |
| **Release shape** | SJMCL: `.deb`/`.rpm`/portable/`.dmg`/`.app.tar.gz` for aarch64 + x86_64 — the broadest Linux matrix among newcomers. Pandora: portable `.exe`/`.AppImage`/`.deb`/`.dmg` + update manifests. FluentLauncher: `.msixbundle`. |
| **Strengths** | Pandora's security posture (sandbox, keyring, token redaction, no metadata middleman) is the strongest in the ecosystem; SJMCL's deeplink/extension surface is unique; FluentLauncher shows a native Windows look with a separated core. |
| **Weaknesses** | All three are young with one or few maintainers; Pandora is Windows-first and explicitly WIP; SJMCL's JVM tuning and crash tooling lag Prism; FluentLauncher is Windows-only and slow-moving. |

### 2.10 CLI-first projects — portablemc 0.55k, headlessmc 0.42k

Both matter for the *core*, not the shell. `portablemc/src` is a textbook file layout: `download.rs`,
`http.rs`, `maven.rs`, `moj/` (Mojang meta), `fabric/`, `forge/`, `msa.rs` (Microsoft auth),
`path.rs`, `serde.rs`, `tokio.rs`. `headlessmc` splits Gradle modules by responsibility (`auth`,
`commands`, `config`, `console`, `files`, `java`, `launcher`, `logging`) and ships a real CLI
distribution per platform. Neither has a GUI — which is exactly why both are readable. Cookie
Launcher's CLI-parity requirement comes from here.

### 2.11 Mobile — out of scope, recorded for completeness

PojavLauncher (9.4k, Java + C, Android, last push 2025-09), FoldCraftLauncher (5.0k, GPL-3.0),
ZalithLauncher2 (2.1k, Kotlin, GPL-3.0). All Android-only, all embedding LWJGL ports and touch input
maps. They prove demand but carry a maintenance cost (native GL bridges, per-device input) that
would consume a desktop launcher's entire budget. **Out of scope for Cookie Launcher.**

---

## 3. Side by side: architecture, core, download, GUI

| Launcher | Architecture seam | Core abstraction | Download strategy | GUI stack | Runtime weight |
|---|---|---|---|---|---|
| HMCL | 3 Gradle modules (UI / Core / Boot) | version + task graph + provider chain | provider preference chain, cancellable concurrent tasks | JavaFX + CSS + JFoenix | JavaFX + JFoenix + jwebp |
| Prism | `launcher/` vs `libraries/` | instance dir + `mmc-pack.json`, BaseVersionList | Qt jobs + HTTP meta cache, coarse mirrors | Qt 6 Widgets + QSS | Qt 6, quazip, libnbt++ |
| MultiMC | same as Prism | same as Prism | same as Prism | Qt Widgets | Qt 5/6, quazip |
| PCL2 | one solution, core + UI libs | custom VB core | multi-mirror auto-select, China-tuned | WinForms, fully custom-drawn | .NET Framework |
| PCL-CE | `PCL.Core` library + WPF app | separated Minecraft model | multi-mirror | WPF + HLSL | .NET 10 |
| XMCL | pnpm monorepo, ~30 packages | instance + linked shared mod store | `file-transfer`, resumable multi-source | Vue 3 in Electron | Electron |
| Modrinth | Cargo workspace apps/packages | daedalus crate | daedalus + Modrinth CDN + hashing | Vue 3 in Tauri v2 | Tauri |
| ATLauncher | single module | pack-centric | own downloader + CF import | Swing | JDK only |
| GDLauncher | single React app | instance + CF | Electron downloader | React + Ant Design | Electron (archived) |
| SJMCL | Tauri app + Rust crates + frontend | instance + extensions | CF + Modrinth APIs | webview in Tauri v2 | Tauri |
| Pandora | Rust workspace, 12 crates | instance + sandbox + keyring | Mojang-direct, no metadata middleman | native Rust UI | none (vendored) |
| portablemc | single crate + CLI | version + Maven model | async Rust HTTP | none (CLI) | none |
| headlessmc | 10 Gradle modules | modular services | Java HTTP client | none (CLI) | none |

**Pattern.** Projects that lasted separated game logic from presentation and treated download as a
first-class, cancellable, mirror-aware subsystem. Projects that struggled glued UI to core (Swing
launchers, PCL2's single solution) or paid a runtime tax for presentation (Electron).

---

## 4. Feature matrix

Legend: Y = first-class, P = partial/indirect, N = absent.

| Feature | HMCL | Prism | MultiMC | PCL2 | PCL-CE | XMCL | Modrinth | ATL | GD | SJMCL | Pandora |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Microsoft auth | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y |
| Offline mode | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y |
| authlib-injector | Y | N | N | Y | Y | Y | N | N | P | Y | P |
| Multiple accounts | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y |
| Instance isolation | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y |
| Per-instance Java | Y | Y | Y | Y | Y | Y | P | P | P | Y | Y |
| Auto Java download | Y | Y | P | Y | Y | Y | Y | P | P | Y | Y |
| Forge / NeoForge | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y |
| Fabric / Quilt | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y |
| Modrinth packs | Y | Y | P | Y | Y | Y | Y | P | P | Y | Y |
| CurseForge packs | Y | Y | P | Y | Y | Y | N | Y | Y | Y | Y |
| Mod browsing in-app | Y | Y | N | Y | Y | Y | Y | P | P | Y | Y |
| Portable mode | Y | Y | Y | Y | Y | P | P | P | P | Y | P |
| Resumable downloads | Y | P | P | Y | Y | Y | Y | P | P | Y | Y |
| Mirror failover | Y | P | P | Y | Y | Y | P | P | P | Y | P |
| Crash analysis (cause + fix) | P | P | P | P | P | P | P | P | P | P | P |
| Mod conflict detection | P | P | N | P | P | P | P | N | N | P | P |
| Full i18n | Y | Y | Y | P | P | Y | Y | Y | Y | Y | P |
| Keyboard-operable UI | P | P | P | N | N | P | P | P | P | P | P |
| Self-update | Y | Y | Y | Y | Y | Y | Y | Y | N | Y | Y |
| Signed releases / provenance | N | P | N | N | Y (Sigstore) | P | P | N | N | P | P |
| Headless / CLI parity | N | N | N | N | N | N | N | N | N | P | N |
| Mod sandboxing | N | N | N | N | N | N | N | N | N | N | Y |

**Three rows decide the product.** Every launcher is P or N on *crash analysis with a fix*, on
*keyboard-operable UI*, and on *CLI parity*. Those are not features to match — they are gaps a new
project can win outright.

---

## 5. Cross-cutting lessons

1. **Instance = directory.** Prism, MultiMC, HMCL, PCL2, XMCL and ATLauncher all converged on it.
   Registry- or database-bound designs do not survive users moving folders between drives.
2. **Download is the product.** Users judge a launcher by whether a pack installs on the first
   attempt over a bad connection. HMCL's provider chain and XMCL's resumable transfers are the
   reference behaviours; opaque single-CDN downloaders generate the largest complaint category.
3. **Java provisioning is non-optional.** Mojang publishes per-platform JREs; skipping the fetch
   only moves the support burden onto users.
4. **Stack choice determines contributor supply.** Prism's C++/Qt and PCL2's VB.NET both cap
   contribution; the modding community writes Java. A Java core has the widest pool.
5. **Presentation tax is real.** Electron launchers idle at hundreds of MB; Pandora's native Rust UI
   and the JavaFX/Swing launchers idle far lower. For a tool users keep open, that is a feature.
6. **Auth is three products, not one.** Microsoft device code, offline UUIDs and Yggdrasil via
   authlib-injector have different failure modes and deserve separate code paths and error messages.
7. **Trust is a differentiator.** PCL-CE's Sigstore bundles are the only provenance story in the
   market. A launcher handles account tokens; it should ship checksums, an SBOM and a reproducible
   build recipe.
8. **Licences decide forks.** MultiMC's fork policy produced Prism and PolyMC. GPL-3.0 prevents a
   proprietary relabel; MIT/Apache invite closed forks but grow ecosystems. Pick deliberately.

---

## 6. Gaps Cookie Launcher will attack

| Gap | Evidence | Response |
|---|---|---|
| No actionable crash diagnosis | Matrix row: all P/N; nothing maps a signature to a fix | Rule-table analyser → ranked cause + concrete fix + one-click repair, in GUI and `analyze` CLI |
| No CLI parity | Matrix row: all N or P | Every GUI action has a headless command, documented exit codes, scriptable on servers and CI |
| No keyboard-first UI | Matrix row: all P/N | Full tab traversal, visible focus ring, shortcuts, accessible names |
| Weak integrity + fallback | HMCL/XMCL closest | Per-file SHA-1/SHA-256, ordered mirror candidates, atomic temp-then-move, resumable |
| Presentation tax | Electron rows | Java 21 + self-painted Swing: no Chromium, no JavaFX runtime download; target idle < 120 MB |
| Undocumented architecture | No launcher publishes a maintainable architecture guide | `docs/ARCHITECTURE.md` + `docs/CONTRIBUTING.md` written before code freeze |
| Weak provenance | Only PCL-CE | Checksums + SBOM + reproducible build recipe in every release |

**Deliberate non-goals:** Electron/Chromium, Qt/C++ or .NET UI, Android/iOS, bundled mods, telemetry,
cracked-auth shortcuts, in-app ads.

---

## 7. Limits of this research (what repository inspection cannot tell us)

- **PCL2 runtime behaviour.** The repository is a periodic source drop under author terms. Cold-start
  time, idle memory, mirror-selection heuristics and SmartScreen behaviour cannot be measured from
  source alone; those claims are marked *reported*.
- **Closed launchers.** TLauncher, SKLauncher, Lunar and Badlion are closed or freemium with
  anti-cheat; their internals are unverifiable and their licences forbid reuse. They appear only as
  cautionary context, never as reference implementations.
- **Per-region network performance.** "BMCLAPI is faster in mainland China" is widely reported and
  consistent with the mirror's purpose, but was not benchmarked here.
- **Real pack-install success rates.** No launcher publishes them; the claim that pack installs drive
  retention comes from issue-tracker volume, not from a controlled study.

---

## 8. Sources

- Repositories (metadata, trees, releases retrieved 2026-10-21):
  https://github.com/HMCL-dev/HMCL · https://github.com/PrismLauncher/PrismLauncher ·
  https://github.com/MultiMC/Launcher · https://github.com/PolyMC/PolyMC ·
  https://github.com/Meloong-Git/PCL · https://github.com/PCL-Community/PCL-CE ·
  https://github.com/Voxelum/x-minecraft-launcher · https://github.com/ATLauncher/ATLauncher ·
  https://github.com/gorilla-devs/GDLauncher · https://github.com/modrinth/code ·
  https://github.com/UNIkeEN/SJMCL · https://github.com/Moulberry/PandoraLauncher ·
  https://github.com/FreesmTeam/FreesmLauncher · https://github.com/theorzr/portablemc ·
  https://github.com/headlesshq/headlessmc · https://github.com/dscalzi/HeliosLauncher ·
  https://github.com/Xcube-Studio/Natsurainko.FluentLauncher ·
  https://github.com/betacraftuk/betacraft-launcher · https://github.com/SKCraft/Launcher ·
  https://github.com/PojavLauncherTeam/PojavLauncher ·
  https://github.com/ZalithLauncher/ZalithLauncher2 · https://github.com/FCL-Team/FoldCraftLauncher
- Documentation read: HMCL `HMCL/build.gradle.kts`, `settings.gradle.kts`; Prism `CMakeLists.txt`;
  MultiMC `BUILD.md`; PCL2 `README.md`; PCL-CE `README.md`, `README-EN.md`; XMCL `README.md`,
  `CONTRIBUTING.md`; Modrinth `README.md`; SKCraft `README.md`; portablemc `README.md`;
  headlessmc `README.md`.
- APIs: GitHub REST (`/repos`, `/languages`, `/contents`, `/releases/latest`),
  https://docs.github.com/en/rest
- Game metadata referenced by the download design:
  https://piston-meta.mojang.com/mc/game/version_manifest_v2.json ·
  https://meta.fabricmc.net/v2 · https://meta.quiltmc.org/v3
