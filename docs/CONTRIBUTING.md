# Contributing to Cookie Launcher

Thanks for taking the time to help. This project is deliberately small and dependency-free;
please keep it that way.

---

## Ground rules

1. **English only.** Code, comments, commit messages, UI strings and documentation are all
   English. This keeps the project accessible to the widest contributor base.
2. **No new runtime dependencies.** The launcher ships as one jar with no libraries. If you
   think a dependency is unavoidable, open an issue and justify it before writing code.
3. **Every behaviour change needs a test.** The suite runs in under a second, so there is no
   excuse to skip it.
4. **Match the existing style.** Four-space indent, 100-column soft limit, `final` on
   classes that are not designed for extension, no wildcard imports.

---

## Getting started

```bash
git clone https://github.com/YuDu-Yaye/cookie-launcher.git
cd cookie-launcher
scripts/build.sh          # compiles and runs the tests
scripts/build.sh --package  # also builds a native app image
```

Requirements: JDK 21. There is no Gradle, Maven or npm step, and you do not need one.

On Windows use `scripts\build.bat`.

### Trying the UI

```bash
java -jar build/dist/cookie-launcher.jar
```

To keep test data out of your real profile:

```bash
java -Dcookielauncher.home=/tmp/cookie-data -jar build/dist/cookie-launcher.jar
```

---

## Where things live

| You want to change | Look at |
|---|---|
| A colour, radius, font | `ui/theme/` |
| A button, card, chip, progress bar | `ui/component/` |
| A page | `ui/page/` |
| How versions are downloaded | `game/install/`, `core/net/` |
| How the command line is assembled | `game/launch/LaunchBuilder.java` |
| How a loader is installed | `game/loader/` |
| A crash diagnosis | `game/diagnose/CrashAnalyzer.java` (add a `Rule`) |
| A CLI command | `cli/Cli.java` |

---

## Adding a crash rule

This is the most useful contribution you can make, and the least risky.

1. Add a `Rule` to the `RULES` list in `CrashAnalyzer`:

```java
new Rule("my-rule-id",
        Diagnosis.Severity.ERROR,                 // BLOCKER, ERROR or WARNING
        "A short title the user will read",
        List.of("exact log signature", "another variant"),
        "Why this happens, in plain language.",
        List.of("The first concrete thing to try.",
                "A second option if the first does not help."))
```

2. Add a test in `CrashAnalyzerTest` that feeds the signature in and asserts the rule fires.
   The existing `testEveryRuleFiresOnItsOwnSignature` covers you automatically, but add a
   case asserting the severity and the absence of false positives on a healthy log.

Guidance:

- `needles` are matched case-insensitively as substrings, so keep them specific. A needle
  like `"error"` would fire on every log.
- Every rule must offer at least one fix. "Look at the log" is not a fix.
- Prefer `BLOCKER` only for conditions where the game genuinely cannot start.

---

## Adding a component

Components live in `ui/component/` and follow Material Design 3 specs. Before adding one:

- Read the relevant M3 spec section (colour roles, state layers, shape scale).
- Reuse `TypeScale` tokens; do not hardcode font sizes.
- Paint with `Graphics2D` antialiasing and use `colors.alpha(...)` for transparency.
- Respect `isEnabled()` and `isFocusOwner()`; every control must look right when disabled
  and show a visible focus ring.
- Add a case to `ThemeTest` if you introduce a new colour role.

---

## Pull requests

1. One logical change per pull request.
2. Describe **why**, not just what. Link the issue if there is one.
3. Include the test output (`scripts/build.sh` prints the summary).
4. If you changed the UI, include a screenshot.
5. If you changed the file layout, update `docs/ARCHITECTURE.md` in the same pull request.

---

## Commit messages

```
area: short imperative summary

Optional body explaining why. Wrap at 72 columns.
```

Examples:

```
net: retry the mirror before failing a file
diagnose: add a rule for OptiFine on 1.21
ui: fix progress bar thickness on HiDPI
```

---

## Reporting bugs

Include:

- Launcher version and platform (`cookie-launcher doctor` prints both).
- What you expected and what happened.
- The relevant lines from `logs/launcher.log` (token values are already redacted).
- For crashes: the output of `cookie-launcher analyze <instance-dir>`.

**Never paste an access token.** If you see one in a log, that is a security bug — report it
privately rather than in a public issue.

---

## Code of conduct

Be direct about code and generous with people. Disagreement about technical decisions is
welcome; personal attacks are not.
