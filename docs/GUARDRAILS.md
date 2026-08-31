# Guardrails

What this project enforces with code instead of with a sentence.

A sentence in a document is advice. On turn forty of a long session, with a full
context window, advice is something the model may or may not still be attending
to. A script that returns a non-zero exit code is not. This is ORCHESTRATION
rule 9, prefer barriers to fixes, applied to the working agreement itself.

**Every rule listed here has been deleted from the prose.** That is the point of
the exercise. If a rule lives in both places, nothing was gained and there is a
new opportunity for the two copies to disagree.

## What is blocked, and where

| Rule | Enforced by | Behaviour |
|---|---|---|
| Never `adb uninstall` | `beforeShellExecution` | Command denied before it runs |
| Commit on `main` is unusual | git `pre-commit` | Prints a notice, does not refuse |
| No `git reset --hard`, `git clean -f` | `beforeShellExecution` | Denied |
| No `rm -rf` outside `.scratch/`, `build/`, `/tmp/` | `beforeShellExecution` | Denied |
| No StrictMode `penaltyDeath` | `beforeShellExecution`, `check.sh` | Denied, and CI fails |
| Lua syntax (Gradle checks none) | git `pre-commit`, `check.sh`, `deploy.sh` | Commit and CI fail |
| Shipped Lua changed without a `BLOWTORCH_LUA_LIBS_VERSION` bump | git `pre-commit` | Commit fails |
| Only allowlisted files in `docs/` | git `pre-commit`, `check.sh` | Commit and CI fail |
| `MAIN`/`LAUNCHER` component does not move | git `pre-commit`, `check.sh` | Commit and CI fail |
| Probes do not ride along in a real commit | git `commit-msg` | Commit fails unless the message says probe |
| A responder type with no case in `TriggerData` | `check.sh` | CI fails |
| `TriggerData`'s parcel written and read out of step | `check.sh` | CI fails |
| No `BTPROF` left in tracked code | `check.sh` | CI fails |
| arm64 `.so` in `BTLib/libs` aligned below 16 KB | `check.sh` | CI fails |
| The rule list does not drift between files | `check.sh` | CI fails |
| Reviewer Task is not the Composer-pinned `bugbot` type | `preToolUse`, `subagentStart`, `check.sh` | Task rewritten to `generalPurpose` + Grok; leftover `bugbot` launches denied |
| Reviewer does not dump whole-tree `git diff` | `preToolUse`, `check.sh` | Bugbot Tasks get `scripts/review-diff.sh` prepended; the rule file must name that script |
| Starter tutorial rule is not always-on | `check.sh` | `.cursor/rules/starter-tutorial.mdc` must use `globs`, not `alwaysApply` |
| Parent Cursor hooks/rules, if present, match the repo | `check.sh` (local) | Skip when `../.cursor/` is absent (CI). Missing `beforeShellExecution` there means shell guards do not run |
| Wrap-up omitted "what was not verified" | Claude Code `Stop` (`claude-stop-reminder.sh`) | Continues the turn once; Cursor cannot do this |

The 16 KB check earns its place by having caught a real one on the day it was
written. `BTLib/libs` is not in git and is built by a script nobody remembers to
re-run, so a test APK went out carrying libraries left over from an older NDK,
aligned to 4 KB, which Android 15+ refuses. Nothing in the Gradle build noticed.
It checks `arm64-v8a` only: 16 KB pages are a 64-bit feature, and a recent NDK
correctly leaves `armeabi-v7a` at 4 KB. It skips, rather than fails, when the
libraries have not been built or no `llvm-objdump` is on the machine — a fresh
clone must still go green.

## What is deliberately not blocked

**`git push` on `staging`.** The version of these guards this project started
from denied it, on the theory that the maintainer pushes and the agent does not.
That is wrong here: the maintainer does not use git directly, so a denied push
leaves the work on one laptop with no copy anywhere. Committing and pushing
`staging` is the backup, and the agent does both without being asked.

**Commits and merges on `main`.** Same reasoning: if promoting `staging` to
`main` is denied, nothing can ever ship, because there is nobody else at the
keyboard. `pre-commit` prints a notice when a commit lands on `main` so an
accident is visible, and then gets out of the way.

What is left is judgment and cannot be a pattern match, so it lives in
`.cursor/rules/release-workflow.mdc`: promote to `main` only after the
maintainer has confirmed the **test APK on the phone**, and ask before tags,
GitHub releases and production APKs.

Both of these were denials in the guard set this project started from. They were
removed on request, and removing them was right: neither had ever prevented a
real mistake here, and both blocked the maintainer's actual working method.

## Layers, and why there are three

**Editor hooks** stop a destructive command before it executes. This is the only
layer that can prevent `adb uninstall`, because by the time git sees anything the
profiles are gone.

Cursor's wiring (`.cursor/hooks.json`) is in the repo. Claude Code's is **not**:
sessions start in the parent staging folder, so Claude Code reads
`../.claude/settings.json` and `$CLAUDE_PROJECT_DIR` is the parent, not this
repo. A `.claude/settings.json` here would be silently ignored. The adapters it
points at (`scripts/hooks/claude-*.sh`) are versioned; the wiring that enables
them is not, and cannot be reviewed in a diff.

Open `BlowTorch/` as the Cursor workspace. Project hook commands run from that
root, so they must be `.cursor/hooks/…` — `hooks/…` is the user-hooks layout
and looks for a folder that does not exist. Cursor then errors on every tool
call. If the parent folder is open instead, Cursor reads `../.cursor/hooks.json`,
which must list the same four events (`beforeShellExecution`, `afterFileEdit`,
`preToolUse`, `subagentStart`) and the parent `rules/*.mdc` files must match
this repo — `check.sh` fails locally when they drift. Parent `rules/` are
usually symlinks into this repo; `hooks.json` cannot be, because the python
paths differ (`BlowTorch/.cursor/hooks/…` from the parent root). Without
`beforeShellExecution` there, `adb uninstall` is not denied in the editor.

**Git `pre-commit`** (`scripts/hooks/pre-commit`) is the layer that does not care
which editor or which model produced the change. It is also the only blocking
enforcement for file content in Cursor, because Cursor's `afterFileEdit` is a
notification hook: it cannot refuse an edit and cannot hand a message back to
the agent. It can only write to the Hooks output channel, for a human.

Install it once, and it is version-controlled unlike `.git/hooks`:

```sh
git config core.hooksPath scripts/hooks
```

**`scripts/check.sh`** is the backstop and what CI runs. Slowest, most thorough,
catches what survived the first two.

## Layout

```
scripts/guards/          rules, one file each, exit-code based
  shell-guard.sh         every shell command rule
  lua-syntax.sh          luac -p
  docs-allowlist.sh      what may live in docs/
  launcher-component.sh  MAIN/LAUNCHER stays on FreeLauncher
  task_model.py          reviewer Task is Grok, not Composer-pinned bugbot
scripts/review-diff.sh   per-file review bundle; whole-tree git diff truncates
scripts/hooks/
  pre-commit             git hook: branch, Lua, docs, Lua libs version, manifest
  commit-msg             git hook: the probe check, which needs the real message
  claude-bash-guard.sh   Claude Code adapter, unused on Cursor
  claude-edit-guard.sh   Claude Code adapter, unused on Cursor
  claude-stop-reminder.sh  Claude Code Stop: wrap-up must name what was not verified
.cursor/hooks/
  before-shell-execution.py
  after-file-edit.py
  pre-tool-use.py          Task/subagentStart: reviewer model is Grok
```

`scripts/adb-device.sh` is **not** in this list and not in git: it is gitignored
and holds the maintainer's two phone addresses and scan timeouts.
`scripts/deploy.sh` calls it by path and degrades to "built, not installed" if
it is missing.

`docs-allowlist.sh` skips gitignored paths, so `docs/HANDOFF.md`,
`docs/changelog_draft.md` and the audits stay editable without argument.

The guards contain the logic. The hook files only translate between a tool's
protocol and an exit code. Moving from Cursor to Claude Code is therefore one
config file, not a rewrite of the safety net, and the guards keep working if a
third tool turns up.

## Tool differences worth knowing

| | Cursor | Claude Code |
|---|---|---|
| Block a shell command | yes, `beforeShellExecution` returns `deny` | yes, `PreToolUse` exit 2 |
| Block a file edit | no | yes, `PostToolUse` exit 2 |
| Feed a message back to the model after an edit | no | yes, on stderr |
| End-of-turn hook can refuse to stop | no, `stop` is notification only | yes |

So on Cursor a bad Lua edit is caught at commit time; on Claude Code it would be
caught in the same turn and fixed immediately. That is a real difference, and the
only one that argues for switching. It is not urgent.

## Getting past a guard

For the maintainer, on their own machine:

```sh
BT_GUARD_OFF=1 <command>      # shell rules, as an env var or a command prefix
git commit --no-verify        # the git hooks
git config --unset core.hooksPath   # turn the git hooks off entirely
```

A guard a human cannot get past is a wall, not a barrier. These rules exist to
stop an agent doing something on autopilot.

An agent using `BT_GUARD_OFF` is working around a fact about this project. If it
is genuinely necessary, say so out loud in the same message, and say why.

## Changing a guard

Guards are code and get the same treatment as code: change in its own commit,
with a sentence on why. If a guard fires on something legitimate, that is worth
knowing, so widen it deliberately rather than working around it with
`--no-verify`.

If a rule turns out to be wrong, say so loudly and in place rather than quietly
editing it. A durable note carrying a plausible falsehood is worse than no note,
and the same is true of a guard enforcing a rule nobody believes any more.
