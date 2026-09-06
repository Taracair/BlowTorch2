# Codebase traps

Facts that cost a physical phone, a logcat and usually two wrong guesses to
establish. **This is a lookup table, not a rule set.** Read the section for the
area you are touching. Do not read it end to end and do not load it into a
session that does not need it.

Five areas where reading the code confidently teaches you something false:

1. [The two processes and the binder](#1-the-two-processes-and-the-binder)
2. [Static state](#2-static-state)
3. [Thread ownership](#3-thread-ownership)
4. [Settings serialisation](#4-settings-serialisation)
5. [The Lua layer](#5-the-lua-layer)

Then: [where errors go](#where-errors-go), [per world vs app-wide](#per-world-vs-app-wide),
[mistakes already made](#mistakes-already-made), [questions already answered](#questions-already-answered).

For modules, packages and data flow, see `architecture.md`.

---

## 1. The two processes and the binder

|          | UI process | Service process (`:stellar`) |
| -------- | ---------- | ---------------------------- |
| Owns     | `MainWindow`, `Window`, overlays, the button window Lua | `Connection`, `StellarService`, plugins and their Lua |
| Talks to | the player | the MUD socket |

**The two binder legs are not symmetric. This is the single most misleading
thing in the project.**

- **UI to service is synchronous.** `PluginXCallS` to `Connection.pluginXcallS`
  to `Plugin.xcallS` runs the plugin's Lua on the calling thread. Slow Lua there
  freezes the UI directly.
- **Service to UI is `oneway`, and must stay that way.** The older note here read
  as if that direction were free. It is not: what was queued was the `Handler`
  post on the far side, while the binder transaction itself blocked. A
  synchronous transaction into a *frozen* process is a kill
  (`am_kill … Sync transaction while frozen`), and the cached-app freezer
  suspends the UI process about two minutes after it is backgrounded. That is
  what redrew the screen every time the player came back.

`IWindowCallback`, `IConnectionBinderCallback` and `ILauncherCallback` are all
`oneway` now, so a method with a return value or an `out`/`inout` parameter will
not compile. Do not "just add a getter" to one of them. That compile error is a
guardrail, not an obstacle.

Within one direction the ordering point still holds: `WindowXCallB` and
`SaveSettings` both post to the same `ConnectionHandler`, so whatever is queued
first delays the rest. A button set switch took 1.1s because a settings save was
in front of it.

**When something is slow, ask which queue it is waiting in before you look for
slow code.**

The main window's text belongs to the service, not the UI. `MainWindow.initWindow`
does `tmp.setBuffer(w.getBuffer())`, so the UI `Window` *adopts* the service-side
`WindowToken` buffer. Anything shown to the player that is not written into that
buffer disappears the next time the windows are rebuilt, which is what switching
worlds does. Send text via `Connection.sendBytesToWindow`, which buffers then
notifies. `notifyMainWindow` is only for callers that already buffered.

`⋮` is structurally above every overlay: `gameplay_chrome_overlay` is a later
sibling of `window_container` in `window_layout.xml`, and the overlays live
inside the container. So "the overlay covers ⋮" is a visibility complaint, never
a z-order one. The real hazard is the reverse: `⋮` silently takes touches from
anything parked under it.

## 2. Static state

Both processes load the same classes. A `static` field exists **twice**, and the
two copies never see each other's writes.

This caused a real bug: a cache in `SDCardUtils` was invalidated explicitly from
the UI, which did nothing for the service, and settings import/export runs in the
service.

**If you cache something static, make it self-correcting (check a cheap source of
truth) rather than relying on being told to clear it.**

## 3. Thread ownership

- **`Window.mBuffer` is UI-thread only.** `onDraw` walks the line list three
  times a frame and only the first walk is guarded, so a mutation from elsewhere
  is a crash, not a glitch. `Window.warnIfNotUiThread` logs a stack trace naming
  the culprit, once per window.
- **`Connection` legitimately mutates its own `TextTree`s off the UI thread.**
  That is why the barrier lives in `Window`, not `TextTree`. **Do not put locks
  in `TextTree`.**
- There is deliberately no lock around the buffer. A lock would pay every frame
  for a race that does not exist.
- **Responders run on two different threads**: triggers on the connection
  thread, timers on a timer thread. Anything they share must be local. One
  shared `Matcher` and `StringBuffer` used to sit in responder instance fields.

## 4. Settings serialisation

`ConnectionSettingsIO.buildSettingsPage` nests the main window's `SettingsGroup`
into root options, and `nestExtraTextUnderWindow` nests the extra-text group into
the window group. Good for the Options menu, confusing for serialisation,
because both writers walk recursively:

- `WindowTokenParser` owns window keys, writes them inside `<window>`.
- `ConnectionSetttingsParser` owns connection keys, writes them in `<options>`.

Each reaches keys the other owns. They **skip** foreign keys now
(`isWindowOptionKey` / `isConnectionOptionKey`, guarded by
`SettingsOptionKeyOwnershipTest`). They used to throw on every one: about 45
exceptions and 1.1s per save.

**Not everything with a `SettingsGroup` is persisted.** Extra-text `WindowToken`s
are rebuilt by `ensureSlots()` and never reach `settings.getWindows()`, so their
settings are not serialised. Durable per-slot state belongs in the slot JSON
(`ExtraTextSlot`).

`util/AtomicFiles` is the one place for durable writes. Do not hand-roll a file
write for anything a player cannot reconstruct.

## 5. The Lua layer

Plugins and the button window are Lua under `BT_Free/assets/share/lua/5.1/`.
`buttonserver.lua` runs in the service, `buttonwindow.lua` in the UI process.

**The Gradle build checks no Lua at all.** `scripts/check.sh` and the git
pre-commit hook do. If one of them fails on a file you just wrote, that is the
build's blind spot being covered, not a false positive.

**Changing a shipped `.lua` means bumping `BLOWTORCH_LUA_LIBS_VERSION`** in
`BT_Free/AndroidManifest.xml`. The app unpacks `assets/share/lua` to internal
storage once and compares that counter; without a bump, `install -r` puts the
new APK on the phone and the phone keeps running **the old scripts**. The
maintainer then tests unchanged code and reports "not fixed", which has happened
more than once. Now blocked by the git pre-commit hook. Verify in a built APK:

```sh
aapt2 dump xmltree --file AndroidManifest.xml <apk> | grep -A2 BLOWTORCH_LUA_LIBS
```

Traps that have already caused bugs:

- `1 ~= "1"`. Values from the settings XML arrive as strings.
- **"nothing selected" is spelled `{}`, not `nil`**, in `buttonwindow.lua`. A
  `== nil` check passes an empty table straight through, which crashed the touch
  handler on every cancelled gesture. Fixed in `6f10eb70`: `layoutTargets()`
  returns `chosen, true` / `all, false` and callers test `#targets == 0`. Do not
  reintroduce it, but do not go hunting for it either. Two audits have searched
  and found nothing live.
- `WhiteSpace extends Text` in `TextTree`, so `instanceof Text` catches
  whitespace too.
- Neither `luac5.1 -p` nor Gradle type-checks a `luajava` call. Layout params
  passed that way are unverifiable until the device runs them, which is why three
  commits went into `editoroptionsdialog.lua` in 45 minutes.

### Alias and trigger substitution

Three separate mechanisms, easy to confuse:

- `$1` in a **trigger** action comes from the MUD's output line.
- `$1` in an **alias** comes from what the player typed.
- `${name}` in either comes from a session variable (`SetVariable` / Lua), which
  is how the two worlds connect.

An alias substitutes differently depending on its anchors. See
`AliasExpansion.Mode`. That rule was implicit in a branch inside a 150-line
method for years.

---

## The chrome overlay does not move with the keyboard

`MainWindow` is declared `android:windowSoftInputMode="adjustNothing"`. Nothing
is ever resized or panned when the IME appears. The input bar and the game text
rise because `ChromeController.applyImeChromeLift` sets `translationY` on them,
and it does that to the children of `window_container` — which is **not** where
`gameplay_chrome_overlay` lives.

So anything you add to that overlay is wrong in two ways by default:

- **It stays under the keyboard.** It must be given the same `translationY` as
  the input bar, in `applyImeChromeLift`, the way the FAB strip and the
  completion chips are. There is no inset or padding that will do this for you.
- **It sits a navigation bar too low.** The overlay reaches the bottom of the
  screen; `window_container` is padded up by `bars.bottom`. A bottom margin
  measured from a view inside `window_container` must add
  `window_container.getPaddingBottom()`. `placeGameplayFabStrip` is the worked
  example.

Both were found the same way twice: the symptom looks like a margin bug and
reads as one in the code, and the cause is that the overlay is a sibling of the
thing being measured, not a parent of it.

## Where errors go

- `BlowTorchLogger.logThrowable` to the error log file the player reads after a
  crash. For failures a player could hit.
- `BlowTorchLogger.logMinor` to logcat only. Routine, locally-visible failures.
- `BlowTorchLogger.logGmcpTrace` to `logs/gmcp.log`, its own file. Protocol
  chatter must **never** go in the error log; it has been removed from there
  twice, because it rolls the crash history away.

## Per world vs app-wide

Getting this wrong is a recurring bug shape. Ask "is this about this MUD, or
about this app?" before choosing where a setting lives.

- **Per world**: maps (`openMapForHost`, keyed on `hostHint`), mapper overlay
  visibility and float geometry, per-connection settings.
- **App-wide**: the update check. It lives in `SharedPreferences`, not a
  connection profile, because whether the app looks for its own updates is a
  property of the install.

## Evidence has a provenance

**Text pasted from the game window is not a network capture.** A payload pasted
by hand appeared to show a server sending malformed JSON. A whole fix was built
on it and a public claim made that GMCP was broken on that world. The live logcat
showed perfectly well-formed JSON: the mangling happened in the copy.

For what a server really sent, use `logs/gmcp.log` (Options, Service, GMCP, Log
GMCP?) or logcat. Not a paste, not a screenshot.

**Two apps on the phone look almost identical.** "BlowTorch 2"
(`com.resurrection.blowtorch2`, production, usually old) and "BlowTorch 2 Test"
(`com.resurrection.blowtorch2.test`, where every install goes). A crash report
once described a bug that had already been fixed: it was reproduced on
production. **Ask which app before investigating any failure report.** The
version is in ⋮ → About.

---

## Mistakes already made

Each cost real time or real data.

| What happened | What to do instead |
|---|---|
| `adb uninstall` to "re-register the manifest", destroyed the maintainer's profiles | `install -r`; it does the same job. Now blocked by `shell-guard.sh` |
| Moved the `MAIN`/`LAUNCHER` filter to another component, the home screen icon died while the app was installed | Leave the component alone; theme the trampoline with `BlowTorch.Invisible`. Now checked by `launcher-component.sh` |
| Treated pasted game-window text as proof of what a server sent; built a fix and announced a bug that did not exist | `logs/gmcp.log` or logcat, never a paste |
| Cached a value in a `static` and invalidated it from one process; the other kept the stale value | Make the cache check a cheap source of truth |
| Guarded a Lua sentinel with `== nil` when it is `{}`; crashed every cancelled gesture | Test for the fields a real object has |
| Three wrong fixes to label placement, each tuning the collision search | Two labels were competing for one spot. Find the cause |
| Recommended a large change to the alias loop without reading it | Read the code that runs before recommending changes to it |
| Explained a fix in terms of implementation, twice, and lost the maintainer | Worked example of what they will see |
| Wrote a plausible mechanism for a measured number without checking it | Record the number, mark the mechanism unverified |
| Assumed colour triggers, bleed, or bold made fling janky | Probe `drawTextOnGrid` first. 5 Sep 2026, bold off (`weight=0`): colour units 1–2ms cache hits, bleed already answered (2ms / 9 lines); `gridMs` 31–34 of a 40–49ms frame was clips then per-unit `drawText`. 6 Sep 2026 settled dense-colour fling: `addMs=0`, `hw=1`, `weight=0`, `clips` 0–1, `colorMs=3`, `gridMs` 13–20 of worst 32–38ms, ~900–1100 `drawText`. No-glyph walk the same day: worst 19–22ms (`gridMs=0`, `restMs` ~12, `pinScan` 5–6). Paper fill `<1ms`. |
| Four commits guessing Mode A IME height on `getWindowVisibleDisplayFrame` under `adjustNothing` (always 0); a fifth and sixth finally read the API | Second failed attempt means read the API. If that still fails, ask the maintainer whether the goal is what they want and what you understood |
| Commit message claimed "loadPlugins already rebuilt the trigger tables"; true for the paths that reach `loadPlugins`, not for the two catch branches that fall through | A commit message is a claim. Go and look, and say how you know |
| Several commits treated a 5-row flying mini-map becoming 4 as CSI, wrap, or CR. BTPROF 4 Sep 2026: parse `abort=0` `lost=0`, then `LINE_GONE` on a line that contained `--` | Probe gag vs parse before guessing the parser. An unanchored `--` in a regex gag matches those glyphs as map tiles |

## Questions already answered

Do not re-derive these.

| Question | Answer |
|---|---|
| Is the `onDraw` bleed scan a performance problem? | No. 9 lines, 2ms worst case over 300 frames on a real MUD. The 1000-line limit only bites on a buffer with no colour at all |
| Does clipping every ASCII glyph in `drawTextOnGrid` cost much? | Yes. 5 Sep 2026, bold off, fling ~77 lines: 40–49ms/frame, `gridMs` 31–34, ~3600 ASCII glyphs, ~4400 clips. Colour units were 1–2ms cache hits. Cell origins stay; the per-character clip does not. Upright ASCII no longer clips the run either (italic still does — skew hangs into the next colour). During a fling, adjacent words of the same colour are one `drawText`, not one per unit. 6 Sep 2026 settled dense-colour fling: clips 0–1, `gridMs` 13–20 of worst 32–38ms. Skipping those `drawText`s: worst 19–22ms. |
| Does colour (or a fancier font) make scrolling slow? | Colour *in the buffer* at fling: no (1–4ms cache hits, still paid with glyphs skipped). Colour *on the canvas*: yes when it multiplies draws. 6 Sep 2026 settled dense-colour fling (`addMs=0`, `hw=1`, 79–80 lines): ~1100 COLORs, ~900–1100 `drawText`, worst 32–38ms. No-glyph walk: worst 19–22 (`gridMs=0`, `restMs` ~12, `pinScan` 5–6). Paper-only: `<1ms`; the black screen was the paper colour, not a failed draw. Sparse/map: ~74 COLORs, worst ~25. A new clip, a per-glyph loop, or a `Typeface` that misses `ensureGridCache` (italic is skew −0.25 for that reason; liga/kern are off because batched `drawText` drifted after emoji fallback) is a fling regression until measured against those numbers. Not measured: SGR 1 overlay as the fling bottleneck. Ingest of a dense dump is a *different* frame: first paint 9–10ms colour-miss plus `addMs` 43–45. |
| Is already-on-screen text a bitmap that fling just pans? | Not as one viewport snapshot. Idle frames still re-walk the line list and issue `drawText`. While the finger or fling is moving, unwrapped lines blit a per-line tile. Hardware canvas (`hw=1`) does not make `drawText` a pan. Paper-only fling was `<1ms`; that is the cost of moving a filled rect, not of the text. Same afternoon, a reverted viewport-only ARGB_8888 blit probe while coasting (`addMs=0`, `hw=1`, no overscan): blit-heavy 2s windows were `avgMs=1–2` (`lastMs` often 0; e.g. 178 blit / 185 frames). Capture hitch `worstMs` 38–57 (software canvas; HW typeset the same day was 32–38). Recapture `capN` 3–9 per 2s of coast. Black then a whole viewport of text is that recapture. `blitMs`/`dy` in those heartbeats stayed 0 because the worst frame was capture. Finger-down stayed typeset. Same day, a reverted `RenderNode` coast pan (API 29, no compositing layer, ~0.5 viewport overscan each side): `panN` 16–103 per 2s (`hasDL=1`), recapture hitch `recMs` 46–57 (`drawN` ~1400–1600 vs typeset ~900–1230), `recN` 5–24 per 2s on rapid reverse. Player: jumps, inconsistent capture, black bottom half. `panMs`/`dy` stayed 0 because the worst frame was rec. Same day, per-line tiles while coasting (unwrapped rows, 128 slots, opaque cell-height bitmaps): hit-heavy 2s windows `avgMs` 4–8 vs typeset fling 32–38 (e.g. 146 frames, `hitN=9690` `missN=0` `avgMs=6`; 128 frames `hitN=7943` `missN=382` `avgMs=7`). Finger-down that afternoon was still typeset (`hitN=0` `missN=0`, `avgMs` 14–16, `drawN` ~550–610). `worstMs` 32–44 in mixed windows was a miss or typeset frame; heartbeat `mode` is the slowest frame, not the typical one. Opaque `cellBottom` tiles clipped g/j (live typeset uses `FontMetrics` and paper cells skip the background rect). Shipped path: 256 slots, transparent union of FontMetrics and the ANSI cell, finger-down as well as coast. |
| Does SGR 22 turn off bold? | Yes (29 Aug 2026). It used to classify as `NOT_A_COLOR`, so leftover SGR 1 painted default grey (`#BBBBBB`) as bright white (`#FFFFFF`). `38;5;22` is still xterm olive: the xterm branch consumes the index before `getColorType`. |
| How are SGR italic/underline/strike/reverse/faint painted? | Skew −0.25 (not `Typeface.ITALIC` — that misses the grid cache), underline, strike-through, swap FG/BG after LightPaper remap, dim 50% toward paper. SGR 21 is underline, not xterm bold-off. SGR 5 stays `38;5;n`; blink is ignored. |
| Do emoji occupy two wrap columns? | No. Paint only: `CellWidth` is 2 cells on the canvas, clip to the line box. Wrap, NAWS and `charcount` still count one code point. ASCII maps stay 1. |
| Does Word Wrap break ASCII maps? | It used to, at the spaces inside `[ ]-[ ]` tiles. Hard-break at the column (box drawing and Block Elements too). A legend of letters on the same line (`AB: Offices`) used to look like prose, so wrap still shredded the grid; flying `oO` tiles were letters and missed the detector. 4 Sep 2026: Liberation Mono 28, cell=17, wrapCol=85. A live world's flying `map` rows were already 30 characters with LF, not CR-glued. |
| Does CSI eat ASCII map tiles? | It can, when a colour code has no `m` yet. Finals `@`-`~` include `[ ] \| \\ ` o O @`. Intermediates `0x20-0x2F` include space and `( )`. 4 Sep 2026: parameter bytes are only `0x30-0x3F`; other bytes abort and are reprocessed as text. Tests in `TextTreeCsiAsciiMapTest`. A later BTPROF on a live flying map showed `abort=0` and `lost=0` — that session's missing row was a gag, not CSI |
| Can a regex gag delete an ASCII map row? | Yes. The gag removes the whole line the match sat on. An unanchored `--` (or `\|`) matches those characters as tiles. 4 Sep 2026: the centre flying-map row contained `--`; `LINE_GONE`; working 11 lines, finished 10; the UI never saw it. `lmap` lost two rows that also contained `--` |
| Is `wait(5)` in `Window.onDraw` a real hot spot? | No, dead code from a threading model that no longer exists. Removed |
| Does reloading `button.lua` per set switch cost much? | No, 1 to 8ms measured |
| What made button set switching take 1.1s? | A settings save queued ahead of the payload on the same handler, slow because it threw about 45 exceptions |
| Why did Options, Window do nothing until restart? | `SettingsGroup.recursiveListenerUpdate` cleared the listener map on every descent into a subgroup |
| Are extra-text `WindowToken` settings persisted? | No. Use the slot JSON |
| Does the mapper parse GMCP `coord`? | Yes, several shapes, behind `mapper_gmcp_use_coords` (default off) with a Chebyshev ≤1 guard |
| Are MSSP and MTTS implemented properly? | Yes, both complete. MSSP is one-way by design; MTTS is the full three-reply TTYPE cycle |
| Was MSDP complete? | No. Transport was correct, but the client could never *ask*. `LIST`/`SEND`/`REPORT`/`UNREPORT`/`RESET` added later |
| Are the stability bugs from the 2026 AI work? | No. All inherited from 2010 to 2018 |
| Did that MUD send malformed GMCP? | No. That was a paste artefact |
