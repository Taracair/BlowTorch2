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
| Never `git push` | `beforeShellExecution` | Denied; the maintainer pushes |
| No commits on `main` | `beforeShellExecution`, git `pre-commit` | Denied |
| No `git reset --hard`, `git clean -f` | `beforeShellExecution` | Denied |
| No `rm -rf` outside `.scratch/`, `build/`, `/tmp/` | `beforeShellExecution` | Denied |
| No StrictMode `penaltyDeath` | `beforeShellExecution`, `check.sh` | Denied, and CI fails |
| Lua syntax (Gradle checks none) | git `pre-commit`, `check.sh`, `deploy.sh` | Commit and CI fail |
| Shipped Lua changed without a `BLOWTORCH_LUA_LIBS_VERSION` bump | git `pre-commit` | Commit fails |
| Only allowlisted files in `docs/` | git `pre-commit`, `check.sh` | Commit and CI fail |
| `MAIN`/`LAUNCHER` component does not move | git `pre-commit`, `check.sh` | Commit and CI fail |
| Probes do not ride along in a real commit | git `commit-msg` | Commit fails unless the message says probe |
| No `BTPROF` left in tracked code | `check.sh` | CI fails |
| The rule list does not drift between files | `check.sh` | CI fails |

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
scripts/hooks/
  pre-commit             git hook: branch, Lua, docs, Lua libs version, manifest
  commit-msg             git hook: the probe check, which needs the real message
  claude-bash-guard.sh   Claude Code adapter, unused on Cursor
  claude-edit-guard.sh   Claude Code adapter, unused on Cursor
.cursor/hooks/
  before-shell-execution.py
  after-file-edit.py
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

## Changing a guard

Guards are code and get the same treatment as code: change in its own commit,
with a sentence on why. If a guard fires on something legitimate, that is worth
knowing, so widen it deliberately rather than working around it with
`--no-verify`.

If a rule turns out to be wrong, say so loudly and in place rather than quietly
editing it. A durable note carrying a plausible falsehood is worse than no note,
and the same is true of a guard enforcing a rule nobody believes any more.
