# PCL2 Feature Inventory

Source: the owner's installed copy of **Plain Craft Launcher 2, version 2.13.1.1**
(`Plain Craft Launcher 2.exe`, 5.7 MB, Copyright © Chengdu Guapi Long Technology Co., Ltd.),
inspected on 2026-10-22. Evidence comes from four places, not from memory:

| Evidence | What it proves |
|---|---|
| `PCL/Log1.txt` (154 KB) | Internal module names, which reveal the architecture |
| `PCL/LatestLaunch.bat` | The exact JVM and game arguments a launch produces |
| `.minecraft/PCL.ini`, `PCL/Setup.ini` | Persisted settings and their key names |
| `E:\.minecraft\versions\` | How instances are actually named and laid out |

This document exists because the owner asked for feature parity: *"PCL2 has it, so Cookie
Launcher should have it."* It is the checklist the rest of the work is measured against.

---

## 1. Module architecture, read from the log

PCL2's own log prints its class names, so the internal structure is directly observable:

| Module | Calls in one session | Responsibility |
|---|---|---|
| `ModCrash` | 328 | Crash detection and reporting — by far the busiest module |
| `ModLaunch` | 268 | Process assembly and hand-off |
| `ModLoader` | 193 | Forge / Fabric / NeoForge / OptiFine installation |
| `ModNet` | 55 | HTTP, mirrors, download scheduling |
| `ModMinecraft` | 16 | Version metadata and file layout |
| `ModJava` | 9 | Runtime discovery and validation |
| `ModDownload` | 7 | Named download tasks |
| `ModSecret` | 4 | Credential and token handling |
| `Modrinth` | 11 | Modrinth catalogue integration |
| `ModFileParser` | 4 | Reading mod metadata out of jars |
| `PageLaunchLeft` / `PageLaunchRight` / `PageSelectLeft` / `PageInstanceMod` | 8 | The four page regions |

Two conclusions that shape our design:

1. **Crash handling is the hottest path in the program**, three times busier than launching.
   That is not an accident of logging: it reflects how often users need help. It is also the
   one place our research found no competitor does well.
2. **The UI is split into a small fixed set of page regions**, not a free-form navigation. The
   left rail carries selection, the page body carries detail.

---

## 2. Feature checklist

Status legend: `DONE` shipped, `PART` partial, `TODO` not started.

### 2.1 Launch and instances

| Feature | Evidence | Cookie status |
|---|---|---|
| One-click launch of the selected instance | `PageLaunchLeft` | PART — launch pipeline ported, not wired to the button |
| Per-instance game directory | `E:\.minecraft\versions\<name>` | DONE |
| Instance naming from the pack, e.g. `1.21.1-Fabric 0.18.4` | version folders on disk | DONE (`Subtitle()`) |
| Custom window size | `--width`, `--height` in the bat | DONE (`/width`, `/height` in M3 tools docs) |
| Per-instance memory | `-Xmx15360m` in the bat | DONE (`Instance.MemoryMb`) |
| Per-instance JVM arguments | `-DFabricMcEmu`, `-Dfml.*`, `-Djna.*` | DONE (`Instance.JvmArgs`) |
| Instances visible as a picker, sorted by last played | `PageSelectLeft` | TODO |
| Play time tracking | not visible in logs | DONE (data model only) |
| Quick join a server | `--server` style args absent in this bat | TODO |

### 2.2 Versions and loaders

| Feature | Evidence | Cookie status |
|---|---|---|
| Vanilla version catalogue | `ModMinecraft` | DONE — 920 versions loaded live |
| Snapshot / old version filters | version list shows only releases by default | DONE |
| Forge, Fabric, NeoForge, OptiFine installation | `ModLoader` (193 calls) | PART — Fabric/Quilt designed, Forge/NeoForge planned |
| Loader version picking per game version | `1.20.1-Forge_47.4.26` naming | TODO |
| Repair / verify an installation | standard in every launcher | TODO |
| Delete an installation | standard | TODO |
| Auto-select the right Java per version | `ModJava` | DONE (selection logic) |

### 2.3 Downloads

| Feature | Evidence | Cookie status |
|---|---|---|
| Multi-mirror with fallback | `ModNet` (55 calls) | DONE (`Mirror.Candidates`) |
| Download speed display | `33.7 K/s` in the log | TODO |
| Parallel file downloads | implied by `ModNet` | TODO |
| Resumable transfers | not observable from logs | TODO |
| Per-file hash verification | Mojang metadata requires it | TODO |
| Community mirror selection in settings | `Setup.ini` has no mirror key in this build | TODO |

### 2.4 Mods, packs and resources

| Feature | Evidence | Cookie status |
|---|---|---|
| List installed mods | `PageInstanceMod` | TODO |
| Enable / disable a mod by renaming | standard, `.disabled` convention | TODO |
| Read mod metadata without loading it | `ModFileParser` | TODO |
| Modrinth browsing and install | `Modrinth` (11 calls) | TODO |
| Modpack import (zip, `.mrpack`, CurseForge) | not in this log | TODO |
| Resource pack management | keyword `资源包` absent in this log | TODO |
| Shader pack management | absent in this log | TODO |
| World / save management | keyword `存档` absent | TODO |
| Duplicate mod detection | `ModCrash` fires constantly | TODO |

### 2.5 Accounts

| Feature | Evidence | Cookie status |
|---|---|---|
| Offline account | `LoginPageType:0` in `Setup.ini` | DONE |
| Microsoft sign-in | `--xuid`, `--clientId` in the bat | PART — device-code flow ported, dormant |
| Third-party authlib login | keyword `登录` (36 calls) | PART — Yggdrasil designed |
| Refresh an expired session | log shows `刷新登录` | TODO |
| Multiple accounts with a switcher | standard | DONE (store + selection) |
| Skin preview | keyword `皮肤` (2 calls) | TODO |

### 2.6 Crash handling — PCL2's strongest area

| Feature | Evidence | Cookie status |
|---|---|---|
| Detect a crash after exit | `ModCrash` (328 calls) | TODO |
| Classify the cause | same | PART — 14-rule analyser written, not wired |
| Show an actionable fix | same | PART |
| Extract the relevant log excerpt | same | TODO |
| Offer to upload / copy the report | same | TODO |
| Detect a mod that is known to crash | same | TODO |

### 2.7 Settings and shell

| Feature | Evidence | Cookie status |
|---|---|---|
| Theme selection | keyword `主题` (1 call) | DONE — 7 seeds, light and dark |
| Window height memory | `WindowHeight:520` | DONE |
| Music player (in-launcher background music) | `PCL/Musics/` directory | TODO — deliberate low priority |
| Picture / screenshot browser | `PCL/Pictures/` directory | TODO |
| Announcements | keyword `公告` (1 call) | TODO — will not implement (it calls home) |
| Self-update | keyword `更新` (21 calls) | TODO |
| Notification centre | not in this build | TODO |
| Launch folder selection | `LaunchFolderSelect:E:\.minecraft\` | DONE (`--home`) |

---

## 3. Deliberate non-parity

These are things PCL2 does that Cookie Launcher will not, with the reason:

| PCL2 behaviour | Why we skip it |
|---|---|
| Bundled announcements fetched from the author's server | Unannounced network calls; our rule is nothing leaves the machine except metadata the user asked for |
| Card-key / 卡密 licensing (`CardKey1`, `CardCount` in `PCL.ini`) | Closed-source monetisation mechanism; incompatible with GPL-3.0 |
| Windows-only WinForms rendering | We chose WPF; the visual model differs but the features do not |

---

## 4. What "parity" means concretely

The owner's instruction is that after the GUI is finished, Cookie Launcher should have what
PCL2 has. In build order, that means:

1. **Launch path** — instance picker, launch button, memory and JVM overrides, exit handling.
2. **Version installation** — vanilla plus Fabric, Quilt, Forge, NeoForge, with progress and
   mirror fallback visible.
3. **Mods page** — list, enable, disable, delete, and read metadata from jars.
4. **Accounts page** — offline creation, authlib login, Microsoft device code.
5. **Crash diagnosis wired to the Play page** — this is where we can beat PCL2 rather than
   match it.
6. **Settings** — theme, Java path, memory, mirror, proxy, data directory.
7. **Repair, delete, and instance management** — the housekeeping every launcher needs.

Items in section 2.7 beyond theme and window size (music player, picture browser,
announcements, self-update) come last, and announcements will not be implemented at all.
