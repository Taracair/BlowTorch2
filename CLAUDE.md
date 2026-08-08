# CLAUDE.md

Work on branch `staging`. The maintainer decides what ships.

## Six rules

These are the ones that need judgment. Everything else that used to be on this
list is now enforced by a script, so you do not have to remember it. See
`docs/GUARDRAILS.md` for what is blocked and where.

1. **Measure before you touch.** Reading this code has produced a confident,
   wrong hypothesis at least six times. The device is the authority.
2. **Say what you did not verify.** Every time. Never say "works" when you mean
   "compiles" or "installed".
3. **Do not guess mechanisms.** A measurement is a fact; the explanation for it
   is a guess until checked, and a plausible wrong explanation in a durable
   place is worse than none.
4. **Fix the cause, not the symptom.** Remove the throw rather than quieten the
   log. A wider `catch` moves the symptom away from the cause.
5. **"Behaviour-preserving" needs an argument, not an assertion.** Show why the
   output is identical, or extract, test against old behaviour, then delegate.
6. **The second attempt is the signal.** Fixing the same failure in the same
   place twice means the first fix was a guess. Stop at the third: read what the
   API actually requires, then write one informed fix. If that still fails, go
   back to the maintainer before a seventh approach. Ask two things out loud:
   do they want what they said, and is what they said what you understood.

## Commands

```sh
scripts/check.sh          # everything checkable without a device; what CI runs
scripts/deploy.sh         # test, build btTest debug, resolve serial, install -r
scripts/adb-device.sh     # print a usable serial, nothing else
```

Run `scripts/deploy.sh` before reporting a code step done, once per step, after
its last commit. Report "installed", not "works".

## Where things are

| You need | Read |
|---|---|
| Why a change keeps failing in a way that makes no sense | `docs/CODEBASE-TRAPS.md` |
| The working method behind the six rules | `docs/ORCHESTRATION.md` |
| What is mechanically blocked and why | `docs/GUARDRAILS.md` |
| Modules, packages, data flow | `docs/architecture.md` |
| What already happened and how | `git log`, `git show`, `git blame` |
| What is still to do | `docs/HANDOFF.md` if it exists locally |

Read `docs/CODEBASE-TRAPS.md` **before** touching any of: the UI to `:stellar`
binder, `static` state, settings serialisation, thread ownership, the Lua to
Java boundary. Those five areas are where reading the code confidently teaches
you something false. Elsewhere, do not load it.

## Explain in examples

"You type `kk goblin` and the game receives `kill $1` instead of `kill goblin`"
beats "unanchored aliases do not substitute captures". The maintainer is testing
on a phone, not reading your diff.

Write to the maintainer in **Polish** — status, questions, release drafts —
unless they switch language. Code, comments, commit messages and everything in
`docs/` stay in English.

## Commits

Commit on `staging` after each completed logical step, without being asked. One
commit is one rollback point. Probes get their own commit. Message is one or two
sentences on why, not a file list.

**Push `staging` yourself, without being asked**, once the step is committed.
The maintainer does not use git directly; an unpushed commit exists on one
laptop and nowhere else. Pushing `staging` is the backup, not a decision.

`main` is the release branch and is only touched when the maintainer says a
release is being cut. A confirmed test APK means the work is good, not that it
ships — keep working on `staging` and do not offer the merge. When a release is
asked for, doing the `staging` → `main` merge is the agent's job.

What still needs asking every time: tags, GitHub releases, production APKs. See
`.cursor/rules/release-workflow.mdc`.

A commit message is a claim. "Nothing reads this", "equivalent to the old path",
"X already does this" are assertions about code somewhere else. Go and look
before writing them down, and say how you know.
