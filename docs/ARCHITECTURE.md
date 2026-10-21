# Architecture

This document explains why Cookie Launcher is shaped the way it is. The short version:
game logic and transport are pure JVM code with no UI knowledge, and the UI is a
self-painted Material Design 3 layer on top. Everything else follows from those two
decisions.

---

## 1. Boundaries

```
core  ──────────────►  game  ──────────────►  ui
  │                     │                     │
  │ transport, storage  │ Minecraft domain    │ presentation
  │ JSON, hashing, fs   │ install, launch     │ theme, components, pages
  │ no game knowledge    │ no UI knowledge     │ no protocol knowledge
                                                            ▲
                                          cli ──────────────┘
```

| Rule | Why |
|---|---|
| `core/` must not import `game/` | Transport, hashing and JSON stay reusable and testable without a Minecraft version |
| `game/` must not import `ui/` | The CLI is not a second-class citizen; every feature works headless |
| `ui/` may import both | Presentation is allowed to know the domain |

A violation is caught at compile time, not by review discipline.

---

## 2. Package responsibilities

### `core/`

| Package | Responsibility | Notable decisions |
|---|---|---|
| `AppPaths` | Every on-disk location | One class resolves root, portable mode, and Mojang-compatible layout so no other class hardcodes a path |
| `json` | RFC 8259 reader/writer | Hand-written because a launcher parsing untrusted remote metadata should not need three dependencies; integers stay `Long`, decimals stay `Double` |
| `net` | HTTP transport, downloader, mirrors | One shared `HttpClient`; per-file SHA-1; deterministic fallback order |
| `util` | fs, hashing, zip, OS, formatting, progress | Null-hostile helpers; `Progress` carries bytes, fraction, speed and ETA in one object |
| `log` | Bounded history + file | 5,000-line ring buffer so the log page never grows without limit |
| `config` | Settings, instances, accounts | Atomic writes; corrupt files fall back to defaults instead of failing startup |

### `game/`

| Package | Responsibility | Notable decisions |
|---|---|---|
| `meta` | Mojang metadata model | `inheritsFrom` resolution merges child-wins scalars, unioned libraries, concatenated arguments |
| `install` | Game, assets, natives | Plan is built completely before any byte moves, so progress is accurate from frame one |
| `loader` | Fabric, Quilt, Forge, NeoForge | Fabric/Quilt = metadata; Forge/NeoForge = run the upstream installer headlessly |
| `java` | Discovery and provisioning | Selects the *lowest sufficient* major version, so 1.16.5 does not get Java 21 |
| `launch` | Command assembly | Handles both metadata generations; never passes quick-play flags to versions that cannot parse them |
| `auth` | Three account types | Separate code paths because their failure modes share nothing |
| `mod` | Metadata scanning | Reads `fabric.mod.json`, `mods.toml`, `neoforge.mods.toml`, `mcmod.info` without loading mod code |
| `diagnose` | Crash rule engine | Rules are data; the engine only matches and ranks |

### `ui/`

| Package | Responsibility |
|---|---|
| `theme` | OKLCH colour math, M3 tonal palettes, type scale, shape and spacing tokens |
| `component` | Material components: button, card, chip, progress, text field, ripple |
| `page` | Six pages, each owning its own layout and data loading |

---

## 3. The five decisions that matter

### 3.1 Instance is a directory

An instance is one folder containing its own `mods/`, `saves/`, `config/`, plus a small
entry in `instances.json`. Users move installations between drives; registry- or
database-bound designs do not survive that. This is the one thing every mature launcher
agrees on, and it is why the layout mirrors Mojang's own.

### 3.2 Downloads are planned, verified, then executed

```
build plan  ──►  filter already-valid  ──►  parallel transfer  ──►  verify each file
 (size/hash)      (skip = 0 bytes)          (shared HTTP/2)        (retry next mirror)
```

Consequences:

- Progress is correct immediately because the total is known before the first request.
- Reinstalling a healthy version moves zero bytes.
- A mirror serving corrupt data cannot poison the install: the hash check deletes the file
  before the next candidate is tried.
- Resumption works because transfers write to `.part` and issue range requests.

### 3.3 Java is provisioned, not assumed

The launcher reads `javaVersion.majorVersion` from the version metadata, looks for the
lowest sufficient runtime on the machine, and otherwise downloads the Mojang-published JRE
for the current platform (`windows-x64`, `mac-os-arm64`, `linux-x64`, …).

Choosing the *lowest* sufficient version is deliberate: old mods break on new JDKs, so
1.16.5 must not be handed Java 21 merely because it exists.

### 3.4 Authentication is three products

| Type | Mechanism | Failure mode |
|---|---|---|
| Offline | `OfflinePlayer:<name>` UUID | None; works forever |
| authlib-injector | Yggdrasil login, agent patched into the JVM | Server unreachable, bad credentials |
| Microsoft | Entra device-code chain | No client id, no game ownership, expired refresh token |

They share a data class and nothing else. Collapsing them into one "login" flow produces
the unhelpful error messages every other launcher emits.

### 3.5 Crash rules are data

Adding a failure class means appending a `Rule` (id, severity, needles, explanation, fixes)
and a fixture log. The engine never grows. This is why the table can be extended by
contributors who do not want to read the launcher's internals.

---

## 4. Material Design 3 without a toolkit

Swing has no Material components, so the theme layer generates them:

1. **OKLCH → tonal palettes.** A seed colour becomes hue/chroma; each tone fixes CIE L*
   and reduces chroma until the colour fits sRGB. Tone maps through relative luminance,
   not directly to OKLab L — mapping them directly produced near-black surfaces and was
   caught by a contrast assertion.
2. **Roles, not hex codes.** `ColorScheme` exposes the M3 role set (`primary`,
   `onPrimary`, `primaryContainer`, `surfaceContainerHigh`, …) for light and dark.
3. **State layers, not colour swaps.** Hover is an 8% content-colour overlay, press is 12%,
   and the ripple is a real expanding circle. This is the detail that makes a hand-painted
   UI feel native to the platform's design language.
4. **Contrast is tested.** `ThemeTest` asserts ≥ 4.5:1 for `onSurface`/`surface` and
   `onPrimary`/`primary` across all eight themes.

---

## 5. Threading

- The UI runs on the EDT; all I/O runs on daemon worker threads.
- `Progress.Sink` is the only bridge. Implementations marshal to the EDT themselves, which
  keeps the game layer free of `SwingUtilities`.
- Downloads use a fixed pool (default 12, clamped to 32) on a shared client, so workers are
  cheap and connection reuse is maximal.
- The game log is pumped on its own thread and fanned out to listeners; a slow consumer
  cannot stall the game.

---

## 6. Data layout

```
<root>/
├── config.json            settings
├── instances.json         instance index
├── accounts.json          accounts and tokens (never leaves the machine)
├── versions/<id>/         version JSON, client jar, loader profile
├── libraries/             Maven layout, shared across versions
├── assets/
│   ├── indexes/<id>.json
│   └── objects/<ab>/<hash>
├── natives/<id>/          extracted natives for older versions
├── instances/<id>/        one folder per instance
├── cache/
│   ├── downloads/         natives jars and other intermediates
│   ├── installers/        Forge and NeoForge installer jars
│   ├── logging/           log4j configuration
│   └── version_manifest_v2.json
├── runtimes/              managed JREs
└── logs/launcher.log
```

`versions/`, `libraries/` and `assets/` use Mojang's own layout, so the launcher can share a
`.minecraft` directory with other tools instead of duplicating gigabytes.

---

## 7. Failure philosophy

| Situation | Behaviour |
|---|---|
| Corrupt `config.json` | Start with defaults, log a warning |
| Corrupt `instances.json` | Start empty, keep the file for inspection |
| Missing version JSON | Refuse to launch, offer Repair |
| Hash mismatch | Delete the file, try the next mirror, fail loudly if all fail |
| No suitable Java | Download a managed runtime |
| Missing mod dependency | Diagnose before launch, not after the crash |
| Network down | Fail with a message naming the host that could not be reached |

The rule: **never silently continue with data you cannot trust**, and never block the
launcher from starting because one file is broken.
