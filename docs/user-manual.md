# BlowTorch User Manual

Source of truth for in-app **Help**. Keep this file in sync with
`BTLib/res/raw/user_manual.txt` (packaged into the Help dialog).

## Before you start

Because BlowTorch has a lot of features and tries to cover what many different
players need, this guide (and the Starter Tutorial) may occasionally be
slightly out of date in a few places. When that happens, the app itself — what
it shows on screen — is the source of truth. Please report mistakes on
[GitHub Issues](https://github.com/Taracair/BlowTorch2/issues).

## Dot commands

Lines that start with a single `.` are handled by BlowTorch when “process period”
is enabled (default). Type `..` alone to toggle that processing on or off.
Prefix a server command with `..` to send a leading `.` to the game without
running a client command (e.g. `..look` sends `.look`).

Aliases that share a simple name with a command win when you type `.name newtext`
(that changes the alias’s replacement text). Plugins may register additional
commands via `RegisterSpecialCommand`; those appear only while the plugin is
loaded.

Registrations live in `Connection` (built-ins) and Lua
`RegisterSpecialCommand(...)` (plugins).

## Aliases and triggers (patterns / `$1`)

BlowTorch does not use TinTin-style `%1` wildcards. Patterns are **regular
expressions** (Java regex). Captured pieces go into `$1`, `$2`, … in the
replacement or trigger action.

### Aliases

Open the alias editor from the session Options / editors list.

- **Replace** = pattern to match what you type  
- **With** = text to send (can include `$1`, `$2`, …)  
- Optional **start of line (^)** / **end of line ($)** checkboxes

Examples:

    `c`
        With: `cast`
        You type: `c fireball`
        Sent: (word alias; see below)

    `^cast (.+)$`
        With: `c $1`
        You type: `cast fireball`
        Sent: `c fireball`

    `^kill (.+)$`
        With: `k $1`
        You type: `kill goblin`
        Sent: `k goblin`

Notes:

- Without `^`/`$`, BlowTorch wraps the pattern in word boundaries and uses
  normal regex `$n` groups in **With**.
- With **both** `^` and `$`, `$1` is the first `(…)` group in the pattern
  (example above).
- With **only** `^` (no trailing `$`), the line is split on spaces: `$0` is
  the first word, `$1` the next, and so on.

**Changing an alias from the input bar:** for a simple word name (letters,
digits, `_`), type:

```
.name new replacement text
```

That updates the **With** field only. Example: alias key `c`, then
`.c cast 'fireball'` sets the replacement. Patterns with spaces or regex
(like `^cast (.+)$`) must be edited in the alias dialog — the `.name …`
shortcut only works for simple `\w+` keys.

**Local echo (per alias):** the alias editor has a **Local echo** spinner:

- **Use client setting** — follow Options → Service → Local Echo? (default)
- **Always show** — echo this alias’s expanded text even when Local Echo is off;
  also shows `[name=>…]` when a trigger/command updates the alias via `.name …`
- **Always hide** — suppress expansion echo and `.name` update echo even when
  Local Echo / Echo Alias Updates? are on

For **Use client setting**, `.name` update echoes also need **Options → Service →
Echo Alias Updates?** (default on).

Telnet password masking still wins: while the server holds ECHO, nothing is
echoed, including Always show. The typed shortcut is never what appears —
you see (or hide) the **expanded** command, same as today.

### Triggers

In the trigger editor:

- **Literal?** on → match the pattern as plain text (no regex)  
- **Literal?** off → pattern is a regular expression  
- **Group** → optional label (e.g. `combat`); blank = ungrouped. Use the
  **Group** dropdown (existing names) or type a new name below it. The Triggers
  list shows `[group]`, sorts by group, and has **Plugin** / **Group** spinners
  under search (All / Main / plugins, and All groups / (default) / named).
  Options (=) is Enable/Disable all for the current filter.
- **Conditions** → extra gate after the pattern matches — not a substitute
  for the pattern. Optional AND/OR list checked before responders. Empty =
  always fire. Types: Trigger enabled/disabled (pick another trigger;
  `plugin:name` ok), Alias enabled/disabled, Alias replacement equals,
  Variable equals/exists. Set vars with the **Set Variable** responder or
  Lua `SetVariable` / `GetVariable` / `UnsetVariable` (session only, not
  persisted).

In regex mode you can capture with `(…)` and use `$1`, `$2`, … in Ack,
Replace, Toast, Notification, Set Variable text, and similar actions.

Examples (Literal off):

    `You hit (.+) for (\d+)`
        Action text: `emote crushed $1 ($2 dmg)`
        Meaning: Name → `$1`, damage → `$2`

    `A (.+) appears`
        Action text: `kill $1`
        Meaning: Auto-target the thing that appeared

**Sample — fire only if another trigger is enabled:** create trigger
`combat_mode` (any pattern; leave it disabled until you want the mode on). On
a second trigger, under **Conditions**, Add → Trigger enabled → pick
`combat_mode`. Responders on the second trigger run only while `combat_mode`
is enabled (`.trigger on combat_mode`).

**Using an alias in the pattern.** Type an alias's **name on its own** in the
pattern box and the trigger watches for that alias's *text* instead of the name.
Edit the alias later and every trigger using it follows at once.

    Alias   _tappable1 → circuit
    Pattern `_tappable1`
    Matches the word `circuit` in the game text

To use one **inside a longer pattern**, write `$alias{name}`:

    Pattern `You see a $alias{_tappable1} here\.`
    Matches `You see a circuit here.`

Both work in Literal and regex mode — in regex mode the alias's text is pasted
in as regex, in Literal mode as plain text. The braces are required in the
second form, so `$1` is still a capture and never an alias. The preview under
the pattern box always names the alias it found and the text it will watch for.

**Four aliases cannot be used.** The pattern is then left exactly as you wrote
it, so the trigger visibly does not fire rather than quietly watching for
something else. The preview says which of the four it was:

1. **No alias of that name.**
2. **The alias is several commands** — `sip health;stand`. That is not one
   piece of text the game can print.
3. **The alias uses `$1`-style captures from what you type** — `get $1 from
   bag`. A trigger has nothing to fill those from.
4. **The alias names another alias.** One level only, so a pair of aliases
   naming each other cannot loop.

A **disabled** alias still gives its text: disabling stops it expanding what you
*type*, and the trigger is only borrowing the words.

**If you want the name itself as text:** only a pattern that is *exactly* the
name is replaced. Turn **Literal?** off and write `^name$` and it is a pattern of
its own again.

The **?** button beside Done in the trigger editor says all of this on the phone.

**GMCP note:** a **literal** trigger whose pattern starts with `%` (default
GMCP character) is a GMCP hook (`%module.path`), not a line wildcard. See
GMCP below.

Options → Service → **Regular Expression Warning?** controls the reminder
dialog when you turn Literal off.

### Aliases and Timers list filters

Triggers, Aliases, and Timers lists show **Plugin** (All / Main / plugins) next
to search. Triggers and Timers also show **Group** (All groups / (default) /
named). Changing a spinner rebuilds the list and shows a short toast.
Enable/Disable all (Triggers/Aliases) stays under Options (`=`).

Timers support an optional **Group** in the timer editor (same idea as
triggers): list subtitle `[group]`, sort by group, XML `group` attribute.

Timers also support **Conditions** in the timer editor — an extra gate when
the timer fires (same AND/OR types as triggers). Empty = always fire
responders. Types: Trigger enabled/disabled, Alias enabled/disabled, Alias
replacement equals, Variable equals/exists. Set vars with the **Set
Variable** responder or Lua `SetVariable` / `GetVariable` / `UnsetVariable`
(session only). Use `${name}` in alias or action text — variables are not
typed into the trigger pattern.

## Recipes

Worked examples. Each one is a complete thing you can build; the field names
match what you see in the editors.

### 1. A shortcut that takes an argument

**Want:** type `kk goblin`, send `kill goblin`.

Options → Aliases → new:

    Replace   `kk (.+)`
    With      `kill $1`

The `(.+)` captures what you typed after `kk`, and `$1` puts it back. **The
pattern must contain a group** — a bare `kk` has nothing to capture, and `$1`
would be sent literally.

### 2. Remember something the game said, use it later

**Want:** the game names a target; you attack it without retyping the name.

This is the one thing aliases cannot do alone: an alias only sees what *you*
type. A trigger sees what the *game* prints. A session variable joins them.

**Trigger** — Options → Triggers → new:

    Pattern    `A plush suede (\w+) sits against the wall\.`
    Literal?   **off** (this is a regex)
    Action     **Set Variable**, name `target`, value `$1`

**Alias** — Options → Aliases → new:

    Replace   `att`
    With      `kill ${target}`

Walk into the room, then type `att`. The client sends `kill recliner`.

Braces are required: `${target}` is a variable, `$1` is a capture. An unset
variable is left written as `${target}` rather than vanishing, so you can see
what went wrong.

**Tip while building one of these:** add a second action to the trigger,
**Ack** with `.note got target=$1`. That prints a line only you can see, so you
know whether the trigger fired before you start blaming the alias.

### 3. Combat mode: a set of triggers that arm and disarm together

**Want:** healing triggers that only run while you are fighting.

1. Give each combat trigger the same **Group**, e.g. `combat`.
2. Make one trigger that spots the fight starting. Its action is **Script**:
   ```lua
   EnableTriggerGroup("combat", true)
   ```
3. Another spots it ending: `EnableTriggerGroup("combat", false)`.

Turn the whole group on or off by hand any time with
`.trigger group on combat` / `.trigger group off combat`.

### 4. A trigger that only fires under a condition

**Want:** auto-eat, but only when a `hungry` flag is set.

On the trigger, under **Conditions**: Add → *Variable equals* → name `hungry`,
value `1`. Empty conditions mean "always fire".

Set the flag from another trigger's **Set Variable** action, or from Lua with
`SetVariable("hungry", "1")`.

Timers take the same conditions, which is how you get "heal every 10s, but only
in combat".

### 5. Two alias sets, one for travel and one for fighting

Aliases have no conditions, but they can be switched:

```lua
EnableAlias("travel_home", false)
EnableAlias("kk", true)
```

Put that in a trigger's Script action. From the input bar the same thing is
`.alias off travel_home` and `.alias on kk`; `.alias list` shows every alias and
whether it is on.

### 6. One button, ten commands

Edit buttons (⋮ → Edit buttons), then edit a tile:

- **Command** — plain tap
- **Swipe** — eight directions: up, down, left, right, and the four corners
  (↖ ↗ ↙ ↘); each can run a different command
- **Hold** — press-and-hold (~0.45 s)

Set **Switch to named button set** on a tile and tapping it swaps the whole pad
— a movement pad that becomes a combat pad. Same as `.loadset combat`.

### 7. Walk a route

```
.run 3n2ew
```

Three north, two east, one west. Add your own direction words under ⋮ →
Speedwalk Directions. Commas insert literal commands:
`.run 2n,open door,n`.

### 8. Put chat in its own window

Options → Window → Extra text windows → **Manage windows…** → add a slot named
`chat`. Then either:

- **GMCP**: tick the modules to route into it, e.g. `Comm.*`.
- **Triggers**: on a matching trigger, add a **Gag** or **Replace** action and
  set **retarget** to `chat`. The line leaves the main window and appears in
  the slot; **Replace** lets you rewrite it first (`$1` works). Lua
  `NoteToWindow("chat", "…")` adds client-only text without touching MUD output.

Each slot keeps its own scrollback while closed and shows what it missed when
you open it.

### 9. Highlight or hide a line

On a trigger:

- **Color** action — recolour the matching line.
- **Gag** action — hide it entirely (optional **retarget** sends the line to
  an extra text window instead of discarding it).
- **Replace** action — swap text in it, `$1` works here too (also has
  **retarget**).

Several actions on one trigger run in order, so you can gag a line *and* print
your own version of it.

### 9b. Make a word in the game text tappable

On a trigger, add the **Tappable Word** action. The trigger's pattern decides
what lights up, and tapping it sends a command.

- **Tappable part** — `0` marks the whole match. `1`–`9` marks only that
  bracketed part of the pattern, which is what you usually want: the pattern
  needs the rest of the line to recognise it. Pattern
  `You see (.+) lying here` with `1` lights up just the thing on the floor.
- **In the command**: `$word` is the text that was tapped, `$0` the whole
  match, `$1`–`$9` the bracketed parts. Pattern `(\w+) drops (\w+)` with
  `get $2` picks up what was dropped.
- **More than one command** — press *Add another command* and a tap opens a
  small menu at the word instead of sending straight away. The first command
  stays at the top of it. Long commands are shortened in the menu with `(...)`;
  the whole command is still what gets sent.
- **Underline / Bold / Frame** — any combination, or none. Colour is not here:
  put a **Color** action on the same trigger.
- Two Tappable Word actions on one trigger behave as one word that offers both
  sets of commands, and the look comes from the first of them.

The word stays tappable for as long as the line is in the buffer, not just at
the moment the trigger fired, and scrolling back does not change that.

**Worked examples.** Literal? is **off** in all of them (these are regexes).

    Pattern        You see (.+) lying here
    Tappable part  1
    Command        get $1
        "You see a rusty sword lying here" — only "a rusty sword" lights up,
        pressing it sends `get a rusty sword`.

    Pattern        (\w+) drops (\w+)
    Tappable part  2
    Commands       get $2
                   kill $1
        "Goblin drops sword" — "sword" lights up; a press offers both
        `get sword` and `kill Goblin`, because the command may use any part of
        the match, not only the part that was pressed.

    Pattern        (\w+) the (\w+) is standing here
    Tappable part  0
    Commands       kill $1
                   look $word
                   consider $1
        Whole match lights up; the menu offers three things to do with it.

    Pattern        \b(\d+) (?:gold|credits)\b
    Tappable part  1
    Command        get $1 gold
        Digits only are pressable; the currency word is context.

    Pattern        \[(\w+)\] (\w+):
    Tappable part  2
    Commands       tell $2
                   ignore $2
        A chat line "[ooc] Fred:" — press the speaker's name to reply.

Things worth knowing when the pattern gets ambitious:

- **A match may cross a colour change.** Matching runs on the whole line, so a
  phrase the MUD colours halfway through still matches; each coloured piece is
  marked in its own colour and pressing either piece does the same thing.
- **Several matches on one line** are each pressable — "You see a sword, a
  shield and a lamp lying here" with pattern `a (\w+)` gives three separate
  words. At most 16 matches per coloured run are marked, so a pattern that
  matches almost everything cannot cover the screen in boxes.
- **A group the pattern does not have** substitutes as empty, not as a literal
  `$7`.
- **Literal? on** matches the pattern as plain text, exactly as it does for
  firing the trigger — `[ 9 | -4 | 1 ]` is those characters, not a regex
  character class.
- **Conditions and groups work as usual**: a tappable trigger that is disabled,
  or whose condition is false, marks nothing.
- **Overlapping triggers**: two different triggers matching the same word each
  mark it; the press uses the last box drawn there. Two Tappable Word actions
  on *one* trigger are merged instead (see above).

### 9c. Tap a name to retarget every button

A command sent by a tappable word takes **the same road as a line you type**:
aliases expand, `.` commands run, `;` splits into several commands. So a tap can
change what your buttons do.

**Set up once.** Options → Aliases → new:

    Replace   `tgt`
    With      `nothing`

**The tappable trigger.** Options → Triggers → new (Literal? off):

    Pattern        (\w+) is standing here
    Action         Tappable Word
    Tappable part  1
    Command        .tgt $1

`.name text` is the built-in "change this alias" command, so tapping a monster's
name rewrites the alias `tgt` to that name — nothing is sent to the game.

**Your buttons.** Give them `kill tgt`, `look tgt`, `throw dagger at tgt`. A
word alias expands anywhere in the line, so every one of them follows whatever
you last tapped.

Variables do the same job with `${target}` instead: use a **Set Variable**
action on the trigger and write `kill ${target}` in the alias. The alias route
is the one a tap can change on its own, without a second trigger.

**Careful:** because a tapped command goes through alias expansion, an alias
whose name is an ordinary word ("sword", "north") will also rewrite what a tap
sends. Name aliases you use this way so they cannot collide — `tgt`, `_it`.

### 10. Start mapping

1. Open the map: ⋮ → **Map**, or `.map open`
2. Switch **Browse** → **Edit**
3. **Nav → Record** on, then walk. Rooms appear as you go.

If your MUD sends GMCP `Room.Info`, turn on **View & sync → GMCP sync** instead
and just walk — nothing to record. `.map find <text>` locates a room,
`.map goto <room>` walks you there.

Maps are per world, saved under `/BlowTorch/maps/`.

### 11. A timer that does something every N seconds

**Want:** sip a health potion every 15 seconds while fighting.

Options → Timers → new:

    Timer Name       `heal`
    Every            `0` h `0` m `15` s
    Repeat           on
    Action           **Ack With** → `drink health`

**Every** is three boxes — hours, minutes, seconds — with quick presets
(`30s`, `1m`, `5m`, `15m`, `1h`) and a running `= 15s` summary under them. The
boxes are added up rather than range-checked, so `90` in the seconds box is the
same as `1m 30s`. Timers are still stored as a total number of seconds.

Control it from the input bar, by name:

```
.timer play heal      start it
.timer pause heal     hold it where it is
.timer reset heal     back to full duration
.timer stop heal      stop and reset
.timer info heal      how long is left
.timer duration heal 30   change how long it runs (whole seconds)
```

Changing the duration — from the input bar or in the editor — does **not** stop
the timer. One that was running keeps running on the new length, starting from
now; one that was stopped stays stopped. Use `.timer stop` to stop it.

Add `silent` as a last word to suppress the toast: `.timer play heal silent`.
Useful when a trigger drives the timer and you do not want a popup each time.

### 12. A timer that only fires in combat

Timers take the same **Conditions** as triggers. On the `heal` timer:

Conditions → Add → *Variable equals* → name `fighting`, value `1`.

Now leave the timer running permanently. It ticks all the time but only acts
while `fighting` is `1`. Set that flag from your combat triggers:

- fight starts → **Set Variable** `fighting` = `1`
- fight ends → **Set Variable** `fighting` = `0`

This is usually nicer than starting and stopping the timer, because you never
end up with a timer that was left paused.

### 13. A trigger that starts and stops a timer

**Want:** the timer runs only while a specific enemy is up.

On the trigger that spots the enemy appearing, add a **Script** action:

```lua
SetVariable("fighting", "1")
```

and on the one that spots it dying:

```lua
SetVariable("fighting", "0")
```

Dot commands ride the same outbound path as typing, so **Ack With**
`.timer play heal` (or Script `SendToServer(".timer play heal")`) *can* start
a timer from a trigger. Prefer a variable + timer **Conditions** (recipe 12)
when you want the timer always armed and gated; use Ack / `SendToServer` when
you truly want play/stop. Keep process-period on (default), or a leading `.`
goes to the MUD.

### 14. One-shot reminder

Leave **Repeat** off and the timer fires once, then stops. Combined with a
trigger that starts it, that is a "remind me in 30 seconds" — a Notification
action makes it show up even if you have switched away from the app.

### 15. Debugging any of this

The single most useful habit: add an extra **Ack With** action printing a
`.note`, which is client-side only and never reaches the game.

```
.note FIRED: target=$1
```

Put one on a trigger while you are building it and you can see whether it
matched at all, and what it captured, before you start suspecting the alias or
the timer downstream. Delete it when the thing works.

`.trigger status <name>` and `.alias status <name>` tell you whether something
is enabled; `.alias list` shows every alias at once.

## Built-in commands

    `.colordebug <0|1|2|3>`             ANSI color debug: `0` normal; `1` color on + codes; `2` color off + codes; `3` color off, no codes
    `.closewindow`                      Dirty-exit the game window
    `.note <text>`                      Client-only echo to the game window; never sent to the MUD. Useful for button tips and debugging
    `.trigger …`                        Enable/disable triggers (`on`/`off`/`toggle`/`status`/`group`/`all`/`plugin`; main + plugins); see below
    `.alias …`                          Enable/disable aliases (`list`/`status`/`on`/`off`/`toggle`/`all`); see below
    `.timer <action> <name> [silent]`   Timer control: `play`, `pause`, `reset`, `stop`, `info`. Optional third token suppresses toasts (not `info`)
    `.timer duration <name> <seconds> [silent]`   Change stored duration and save. A running timer keeps running on the new length, from now
    `.settings …`                       Settings file housekeeping. No argument (or `status`) names this world's settings file and the date/size of the `.bak` copy kept beside it; `backup` saves now and refreshes that copy; `restore` puts it back and reloads. For a copy you can move off the phone use Export / **Backup All Settings** instead
    `.echo [on|off]`                    Show or hide what you type when the server has taken telnet ECHO (a password prompt). No argument prints the current state. The next change from the server wins
    `.dobell`                           Fire configured bell reaction
    `.togglefullscreen`                 Toggle fullscreen preference
    `.wrap [on|off]`                    Input bar growth (default on); also Options → Input → Grow Input Bar?
    `.editbutton [on|off]`              Show or hide the Edit button; also Options → Window → Show Edit button?
    `.editpanel [on|off]`               Toggle/show/hide the Edit tools strip (Sel/Cut/…)
    `.sendbutton [on|off]`              Show or hide the Send button; also Options → Window → Show Send button?
    `.font [size|+n|-n|default]`        Game font size without leaving the game. No argument prints it. `.font +2` steps up from where you are; clamped to 6–48. Also Options → Window → Font size
    `.width [percent|+n|-n|toggle|off]` Text canvas width as a percent of the screen (100–200). Over 100 the text is drawn wider than the screen and you drag it sideways with one finger. `toggle` flips to 100% and back to the last wide setting — put it on a button for ASCII maps. Also Options → Window → Text width (% of screen)
    `.gmcp …`                           GMCP helpers (status / sniff / version / supports / dump / send); see below
    `.frame …`                          Frames a server opened (`list`, `close <id>`, `close all`); see below. Not the same as `.window`
    `.mcp …`                            MCP helpers (Mud Client Protocol `#$#`); see below
    `.mssp`                             Dump the cached MSSP server listing (server announces it; nothing to ask for)
    `.msdp …`                           Dump the MSDP cache, or ask the server: `list`, `send <var>`, `report <var>`, `unreport <var>`, `reset <group>`
    `.keyboard` / `.kb`                 Input-bar control — see `.kb` section below
    `.disconnect`                       Disconnect the current session (same as overflow **Disconnect**)
    `.reconnect`                        Reconnect the current session (same as overflow **Reconnect**)
    `.run <directions>`                 Speedwalk; mapping from **Speedwalk Directions**; commas insert free-text commands
    `.loadset <setname>`                Built-in stub; `button_window` overrides to load a button set
    `.clearbuttons`                     Clear on-screen buttons (`button_window` may re-register)
    `.switch <connection>`              Switch foreground UI to another open connection by exact display name; bare `.switch` lists open sessions (unknown names are refused — they used to black-screen the UI)
    `.search …`                         Scrollback search; see forms below
    `.map …`                            Built-in Mapper (record/draw/links/find/path/maps); see Mapper
    `.window …`                         Extra text windows (list/show/hide/clear/create/destroy); see below

### `.trigger` forms

```
.trigger
.trigger on <name|plugin:name>
.trigger off <name|plugin:name>
.trigger toggle <name|plugin:name>
.trigger status [name]
.trigger group on <group>
.trigger group off <group>
.trigger group toggle <group>
.trigger all on
.trigger all off
.trigger plugin <plugin> all on|off
```

Unqualified names resolve **main settings** first, then a unique plugin
match; use `plugin:name` when names collide. Names and groups may contain
spaces (rest of line after the action). `status` with no name prints
main + plugin counts. Empty group name matches the default group (exact
string match, same as Lua `EnableTriggerGroup`). Group commands apply to
**main + all plugins**. `.trigger all` affects main only; use
`.trigger plugin <plugin> all on|off` for one plugin.

### `.search` forms

```
.search phrase
.search 'phrase with spaces'
.search "phrase"
.search next | n
.search prev | previous | p
.search close | hide | clear
```

Empty argument opens the search UI. Buttons may also use `/search 'phrase'`.

## Mapper

Built-in room map (not the legacy ForgeMap plugin). Open from overflow **Map**,
or `.map` / `.map open` / `.map close` / `.map toggle`. Prefer floating or
fullscreen with `.map mode float|fullscreen` (also **Options → Mapper**).
Floating windows can be dragged and resized; opacity is in Options. The overlay
stays under the ⋮ chrome so overflow remains reachable.

### Concepts

    **Tile**         One room cell on a grid (x, y) plus a **level**
    **Exit**         Edge from tile A to B via a walk command (`n`, `go west`, `out`, …)
    **Current**      Where the mapper thinks you are (green highlight)
    **Selected**     Tile you tapped (yellow outline); used by Edit / Here / Links
    **Follow**       Camera keeps current centered; pan/pinch turns Follow off until you Center
    **Level nest**   A floor anchored on a door/stairs tile (not one global stack)

Maps are JSON files under `/BlowTorch/maps/` (autosave ~2s after changes; **Save**
in **Map** or `.map export` / `.map save` forces a write). With a path,
`.map export|save <path>` writes JSON there (absolute or BlowTorch-relative).
`.map import <path|name>` loads JSON from an absolute/BlowTorch-relative path
or a maps-dir name, then copies it into `/BlowTorch/maps/`.

Title bar shows a breadcrumb when you are on a nested floor, e.g.
`map · L-1 ← Hallway` (map name · level · door you entered from). **[REC]** means
recording is on. The title has **Browse | Edit** and **Float | Full** segments
(**Browse** / **Float** default). **Browse** is view/navigate only — no
recording, Draw, Links, or tile edits. **Edit** is required to record, create
nests, use **Draw** / **Links**, and delete levels. Long-press the title opens
the **Floors** radial.

### Category radials (bottom chips; hide with ▾ tools)

Bottom chrome: **Nav** · **Floors** · **Edit** · **Map** · **View**, plus
**Browse|Edit** and **Float|Full**. The chip row scrolls if it does not fit. Tap
**▾ tools** in the title to hide that chrome when you want more map. Each
category opens an in-map pie menu (no system status-bar flash). Toggle wedges
show their current state under the label (e.g. Follow **on**).

    **Nav**
        What it is for: Getting around
        Actions: Path to, Go there, Find, Center, Follow, Record

    **Floors**
        What it is for: Levels of this map
        Actions: Floor list, Floor ↑/↓, Root floor, To entrance, Rename floor, Delete floor

    **Edit**
        What it is for: Changing the map by hand
        Actions: Draw, Link mode, Set Here, Edit tile, Spacing (spread/packed), Tidy now, 1-way specials, Moves, Link map, Undo

    **Map**
        What it is for: The map as a file
        Actions: Save, Maps, New map, Export, Capture

    **View**
        What it is for: Standing preferences
        Actions: Opacity…, Arrow labels, Window echo, GMCP sync, GMCP grow

**Close** is only the title **✕** (not in the pie menus).

Two names worth separating: **Spacing** (Edit) is a standing preference for how
far apart tiles are drawn, while **Tidy now** re-runs the layout once. **Undo**
is in Edit because it undoes edits.

**New map** works in Browse as well as Edit — starting a map is a file
operation, like Maps and Save.

**Floors → Floor list**: tap a floor to view; long-press = Go Here. In **Edit**,
**Rename…** / **Delete…** on the selected row. You cannot delete the last level.

**Draw** (Edit): grid on; tap empty cell to place; long-press empty = place + Here.
**Link mode** (Edit): tap FROM then TO, then pick a Moves verb (or unlink).
**Layout**: **spread** spaces tiles for arrows + labels; **packed** is compact
(arrows only, thinner heads). **Opacity…** (View): pick a percent — 100% is fully opaque.

While **Record**ing, outbound exits store your typed command; the reverse edge is
guessed. Walking back the same path overwrites that guess with your return
command (e.g. guessed `s` → your `go south`). Specials (`out`/`enter`) with
**1-way off** (default) close to the unique room that already leads into Here
(e.g. freezer `out` → hallway). **1-way ON** always places a new nearby tile.
**Follow** (without Record) advances Here along known exits so the map camera
tracks you.

Long-press a tile: **Path to here** (toast only) or **Go there** (toast + send
commands so the character walks). Find dialog **Go** does the same.
Long-press a tile and drag to move it on the grid (release without moving opens the
tile menu). **Double-tap** a tile = **Set as Here**. Double-tap empty map = center on
current.

Exits with a known destination draw as **arrows** between tiles. In **spread**
layout, walk-word labels sit on the shaft; in **packed**, only the shaft + heads
(including diagonals). If more than two commands share an edge in spread mode,
the label shows `cmd1 · cmd2 +N` — tap it for the full list.

Badge glyphs on a tile:

    **▲** / **▼**
        Meaning: Exit to another **floor** of this map
        Tap: Jump camera / Here to that floor

    **◆**
        Meaning: Special same-map nest (`enter` / `out`, …)
        Tap: Jump to that floor

    **○**
        Meaning: Portal to **another map file**
        Tap: Save current map, then open the linked map in the overlay

To create a portal: Edit → long-press tile → **Link to map…** (or **Edit → Link map**),
pick the destination map and Moves command. Walking that command with **Follow**
also loads the other map.

### Levels (tile-anchored)

Floors nest **per Here tile**: each stairs/door can have its own basement or attic.
**↑** / **↓** (Floors radial) follow an existing nest from Here or return to the
anchor door when leaving (**Browse**). Creating a nest needs **Edit**.

**Browse floors:** **Floors → Floor list** (name, tile count, “via …” for nests;
tap = view; long-press = Go Here), or tap ▲/▼/◆/○ badges. **Root floor** /
**To entrance** jump to the root floor or the nest’s anchor door.

Hierarchy: **map file** → **floors (levels)** → **tiles**. Same-map height or
independent floors stay in one file; a wholly separate zone is another map + **○**.

Recording still maps `up`/`down` while you walk. Use **↑**/**↓** when you need a
pocket floor that is not a simple vertical stack.

### Movement lexicon

Planar grid: **+x = east**, **+y = south** (north decreases y).

    `n`/`north`, `s`/`south`, `e`/`east`, `w`/`west` (and `go`/`walk`/`move` prefixes)   Neighbor on the same level
    `ne`/`nw`/`se`/`sw`                                                                  Diagonal
    `u`/`up`/`climb`/`ascend`                                                            Level +1 (while Recording)
    `d`/`down`/`descend`                                                                 Level −1 (while Recording)
    `in`/`enter`, `out`/`leave`/`exit`, other text                                       Special exit (nearby cell, not a compass step)

Recording prefers this built-in compass lexicon before Speedwalk key bindings
(default `.run`: `h`=nw, `j`=ne, `k`=sw, `l`=se), so typing `go se` still places a
southeast neighbor. Custom Speedwalk commands still apply for non-compass verbs.
Print the summary with `.map dirs`.

### Commands

    `.map`                                                                  Help / status
    `.map open|close|toggle`                                                Show or hide the map UI
    `.map mode fullscreen|float`                                            Presentation mode
    `.map mode browse|edit|toggle`                                          Map interaction mode (Browse default; Edit for nests/Draw/Links/delete)
    `.map record on|off|toggle`                                             Record movement into tiles/exits (`rec` alias)
    `.map follow on|off|toggle`                                             Keep the view centered on you
    `.map level list|prev|next|set <name>`                                  List / nest down (prev) / nest up (next) / jump by name
    `.map level delete <id|name>`                                           Delete a floor and all its tiles (Edit; cannot delete last)
    `.map level move <tileId> <level>`                                      Move a tile onto another level
    `.map find <query>`                                                     Search rooms (`search` alias)
    `.map path <query>`                                                     Show path commands (no send)
    `.map goto <query>`                                                     Path; send only if **Path auto-send** is on
    `.map go <query|id>`                                                    Path and **always** send walk commands (tile long-press **Go there**)
    `.map center`                                                           Center on current room
    `.map undo`                                                             Undo last graph change
    `.map dirs`                                                             Movement lexicon / grid offsets
    `.map maps` / `.map load <name>` / `.map new <name>`                    List / open / create (new name must be unique)
    `.map deletemap <name>`                                                 Delete a saved map file (UI: **Map → Maps** → long-press)
    `.map portal|linkmap <cmd> map <name> [from <id>]`                      Portal exit to another map file
    `.map levelink <cmd> new|to <levelId>|independent <name> [from <id>]`   Floor link (↑/↓ / existing / independent)
    `.map level rename [<id|name>] <newName>`                               Rename a floor
    `.map opacity [40-100]`                                                 Overlay opacity
    `.map export` / `.map save`                                             Save now (`/BlowTorch/maps/`)
    `.map export|save <path>`                                               Write JSON to that path
    `.map import <path|name>`                                               Import JSON (path or maps-dir name); copy into maps
    `.map zoom in|out|reset`                                                Zoom the open map UI
    `.map zoom <factor>`                                                    Zoom by scale factor (map UI must be open)
    `.map add [x y] [title] [here]`                                         Place a tile (optional title; `here` sets current)
    `.map here [id]`                                                        Mark current position
    `.map delete [id]`                                                      Remove a tile (and links to it)
    `.map neighbor <cmd> [from <id>]`                                       Create/link a neighbor by walk verb
    `.map move [id] <x> <y>`                                                Reposition a tile on the grid
    `.map title` / `.map note`                                              Edit current tile text
    `.map title for <id> <text>` / `.map note for <id> <text>`              Edit a specific tile
    `.map link <cmd> [from <id>] to <tileId>`                               Manual link
    `.map unlink <cmd> [from <id>]`                                         Remove an exit
    `.map conflict` / `list` / `list all`                                   List open conflicts (or all, including resolved)
    `.map conflict resolve|ignore <id|n>`                                   Mark one conflict resolved
    `.map conflict resolve|ignore all`                                      Mark all open conflicts resolved
    `.map conflict purge`                                                   Remove resolved conflicts (open ones kept)
    `.map capture preview`                                                  Match Options title/exits regex on recent buffer
    `.map capture apply`                                                    Apply last preview to the current tile

**Options → Mapper:** enable module, float/fullscreen default, opacity,
recording defaults, follow, path auto-send, Use GMCP Room,
**Configure Room Sync…** (room number / absolute coords / create exits),
auto reverse links, legacy toolbar CSV (UI uses **Nav/Floors/Edit/Map/View** chips),
**Capture Title Regex** / **Capture Exits Regex** (keys
`mapper_capture_title_regex` / `mapper_capture_exits_regex`; used by
`.map capture`).

**GMCP Room:** with GMCP and **Use GMCP Room** on (and **Room** in Manage
modules…), `Room.Info` builds the map as you walk:
- **num/id/vnum** → stable tile identity (IRE games and others)
- **coords** / **coord** `{x,y,z}` → place on the grid (z → floor)
- **exits** `{n:123,…}` → create/link neighbors (destination stubs by vnum)

Does not delete exits absent from GMCP. Does **not** parse ASCII maps from
game text — that is Capture regex / Record / Draw. Without GMCP (typical on
many MOOs), use **Rec** while walking, **Edit** for **Draw** / **Links**,
and/or `.map capture`.

### Typical workflows (mini-tutorial)

1. **Record while exploring:** `.map new mymap` → open map → **Edit** mode → **Nav → Record** → walk → Record off → **Map → Save**.
2. **Draw by hand:** **Edit** mode → **Edit → Draw** → tap empty cells → **Link mode** → **Set Here** on your room.
3. **Floors:** long-press a tile → **Add level…** → Floor ↑/↓ (new or existing) / Independent floor / Another map….
4. **Jump maps:** after linking, tap **○** on the tile (or walk the portal command with Follow on).
5. **Fix layout:** long-press-drag a tile. Use **Edit → Spacing** (spread) to see arrow labels.

### `.run` defaults

Direction letters (editable in Speedwalk Directions): `n e s w u d`,
`h`=nw, `j`=ne, `k`=sw, `l`=se. Prefix with a count. Examples:
`.run 3desw2n`, `.run 3ds,open door,3w`.
Mapper recording still treats `se`/`sw`/… as compass diagonals even when those
letters are Speedwalk *keys*.

### `.keyboard` / `.kb`

    *(no args)*                Print help
    `add` / `popup` + text     Set or append input; `popup` also shows the IME
    `flush`                    Send current input
    `close` / `clear`          Hide IME / clear text
    `sel` / `selectall`        Select all
    `cut` / `copy` / `paste`   Clipboard
    `start` / `cursorstart`    Caret to start
    `end` / `cursorend`        Caret to end
    `stepf` / `stepr`          Caret one character right
    `stepb` / `stepl`          Caret one character left
    `stepu` / `stepd`          Command history (↑ older / ↓ newer), like keyboard arrows; within multiline text, move one line first

Examples: `.kb popup reply`, `.kb sel`, `.kb cut`, `.kb start`, `.kb end`, `.kb stepf`, `.kb stepb`.

**Edit** on the input bar expands Sel/Cut/Copy/Paste plus a compact **← ↑ ↓ →** pad (hidden again with **Hide**). ↑/↓ recall previous commands (same as keyboard up/down); ←/→ move the caret.

**Options → Window → Show Edit button?** / **Show Send button?** (both on by default). Dot commands: `.editbutton on|off`, `.sendbutton on|off`, `.editpanel on|off` (tools strip). With Send hidden, use keyboard Send/Enter or `.kb flush`.

### `.editbutton`

```
.editbutton
.editbutton on | off
```

Shows or hides the **Edit** button (same as Options → Window → Show Edit button?). No argument prints status.

### `.editpanel`

```
.editpanel
.editpanel on | off
```

Toggles (or forces) the Edit tools strip above the input row. Same strip as the **Edit** button. No argument toggles.

### `.sendbutton`

```
.sendbutton
.sendbutton on | off
```

Shows or hides the **Send** button (same as Options → Window → Show Send button?). No argument prints status. When off, send with the keyboard Send/Enter key or `.kb flush`.

## Copy text from the game window

- **First finger** — touch where selection should start (marks the start).
- **Second finger** — tap to open the selection / copy widget.
- One-finger long-press alone does not open copy.
- Drag the cursors, then use the widget’s copy control. On-screen buttons may hide while selecting so the widget stays usable.
- The same two-finger gesture works in **extra text** windows (float/drawer).

## Font size

New profiles start around font size **20** (readable on phones). Change under
Options → Window → Font Size.

## Newest text at top

By default, fresh game output sits at the **bottom** of the window (classic
terminal). Enable **Options → Window → Newest text at top?** to put live lines
at the **top** and older scrollback below — handy when on-screen buttons cover
the bottom edge. Drag **up** to dig into history; the home chevron stays in the
**bottom-right** and points **up** toward live output.

**Caveat:** this reverses the on-screen order of consecutive lines. MUD maps,
room diagrams, and other ASCII graphics drawn line-by-line will appear
**upside down**. Leave the option off for those games (or use **Top padding** /
**Keep text still with keyboard?** instead to keep buttons usable without
flipping text).

**Options → Window → Top padding (px)** leaves empty space above game text for
notch/camera cutouts (on-screen buttons are unaffected). Try values like
`40`–`80` on punched-hole phones.

**Options → Window → Bottom padding (px)** does the same at the bottom edge, all
the time — use it to keep the newest line clear of the input bar or a gesture bar.

**Options → Window → Bottom padding with keyboard (px)** adds further space below
game text only while the soft keyboard is open. The two are independent: set
either on its own, or both, in which case they add up while the keyboard is out.
The gap is measured from the bottom of the text area, which rises with the
keyboard unless **Keep text still with keyboard?** is on.

**Options → Window → Keep text still with keyboard?** — when on, opening the soft
keyboard lifts only the input bar; game text stays put (may sit under the IME).
Works with either text direction. Off = classic lift (text rises with the keyboard).

## On-screen buttons: swipe + accordion

**Load a button set from the wizard:** **Options → Button → Load button set from wizard**
(or type `.layoutwizard`). Check one or more packs (Compass, Newbie, Combat,
Explorer, Social), give each a set name, and pick size / alignment / colors.
Packs install complete — there is no Simple/Advanced choice any more; it was
worth three tiles on Compass and nothing at all on Newbie, and an unwanted tile
is easier to delete than a missing one is to discover. Apply only writes the
named sets you checked — other sets stay put; same name overwrites after a
warning.

Set names are folded to lowercase and to `a–z 0–9 _ -` when you Apply (spaces
become `_`), because the name also goes into the `.loadset <name>` cross-links
the packs write between each other; the wizard tells you the name it will
actually use.

New MUD profiles may offer a soft prompt once after connect; turn
**Options → Button → Offer button layout wizard** back on to see that prompt
again. Offline Starter Tutorial keeps its own teaching pad.

The pad lands just under the action bar, high enough that the soft keyboard
cannot cover it — a pad anchored near the bottom of the screen disappears behind
the keyboard the moment you type. Its accordion tiles (MORE, NAV, TIP, CAST,
DOORS, CHAT) sit on the bottom row and open **downward** into the empty game
area beneath it, so they never cover the compass rose above them.

Named sizes are capped so the whole pad stays above the keyboard: Compass has
six rows, so **Extra large** comes back a little under 72dp on a tall phone —
still clearly bigger than Large, and wholly visible, which is the point.
**Fit to screen** is the exception. It sizes a pack so its columns span the
width, which for a tall pack means part of it sits below the keyboard line.

**Change the size later:** **Options → Button → Button size** is a dropdown
(Compact, Comfortable, Large, Extra large, Fit to screen). Picking one resizes
the set on screen straight away, keeping its arrangement — the grid spacing
moves with the tiles rather than leaving them to overlap, and nothing is
re-flowed into rows. **Layout template** next to it only chooses which pack the
wizard offers first; it installs nothing on its own.

**Edit layout:** open **⋮ → Edit buttons**, or long-press the **⋮** next to Edit/Send. In edit mode ⋮ is hidden — use the strip icons: gear (set options), **Cancel** left, **Done** right.

The default `button_window` plugin supports more than tap:

- **Swipe** — eight directions (up, down, left, right, and the four corners);
  each can run a different command (edit button → Swipe). Overrides classic Flip.
  Drag roughly a finger-width off the tile.
- **Hold** — optional command after press-and-hold.
- **Accordion** — up to five child buttons expand from a parent (direction + tap/hold/swipe trigger). Handy when you want several macros on one tile. Editor badges: **T** tap, **H** hold, **S** swipe. Options can draw gesture hints (uncheck to hide **U/D/L/R**, diagonal arrows, Hold, and accordion badges).

## Super-buttons (buttons on top of the keyboard)

Any button can also be put **on the screen itself**, over the game and over the
soft keyboard. Edit the button → **Others** → **Float over the game**.

Two modes, in the **When** picker:

- **Always visible** — the button stays on screen wherever you dragged it.
- **Show with keyboard** — the button exists only while the keyboard is open,
  and is hidden the rest of the time, including from the button grid. This is
  the keyboard-assistant mode: pair it with `.kb` commands
  (`.kb stepb`, `.kb stepf`, `.kb stepu`, `.kb paste`, `.kb close` — see
  [`.keyboard` / `.kb`](#keyboard--kb)) to get caret keys, command recall and
  paste next to your thumb while typing.

The button keeps everything it already had — tap, hold, flip, all eight swipe
directions, colours, size, `switchTo`. It can also be drawn as a circle, with
an optional outline. **Very long press (~2 s)** picks it up and moves it; a
normal hold (~0.45 s) still runs the Hold command. Where you drop it is
remembered per world.

**Permission.** Android does not let an app draw on top of the keyboard without
**"Display over other apps"**. BlowTorch asks the first time you tick the box,
never at startup, and the button is saved either way. If you refuse, the button
still exists but the keyboard covers it — which leaves the feature doing very
little, so it is worth granting.

**Android 9 and 10:** the client often cannot tell whether the keyboard is
open, so **Show with keyboard** may never appear there. Android 11 and newer
are fine. **Always visible** works everywhere.

## Extra text windows

Optional top-drawer or floating panes (chat, tells, combat, …) beside the main game
output. Each slot has a public **name** (lowercase `a-z`, `0-9`, `_`, max 8
slots). The same name is used for gag/replace **retarget**, Lua, and `.window`.

Configure under **Options → Window → Extra text windows** (**Enable**, **Manage windows…**,
or advanced JSON). Modes: **`drawer_top`** (top strip, no title bar — show/hide via
`.window show|hide` or Manage → Show window) or **`float`** (titled, draggable panes).
Overlay geometry (drawer height ≥ 50dp, float position, **opacity 40–100%**) is owned by
the UI; buffers are named `WindowToken`s.

A floating pane's chrome is per slot, in **Manage windows… → Edit**:

- **Show title bar** — the strip across the top, with the ☰ grip and the window's
  title. On by default. Turning it off **hides** it rather than removing it: the
  strip is still there and still drags the window, it is simply not drawn.
- **Show resize grip ◢** — the corner marker at the bottom right. Same idea: off
  hides the marker, the corner still resizes the window.
- **Close button ✕** — hides the window; bring it back with `.window show <slot>`
  or Manage → Show window. On by default. Off means gone, not invisible — an
  unseen ✕ inside the drag strip would close the window every time you missed.

All three off gives a bare pane with no visible chrome at all: still draggable
by its top strip and resizable from its bottom-right corner if you know they are
there, and closed only by `.window hide` or Options.

**Scroll speed** is per slot, in Manage windows. The default, *Same as main
window*, follows **Options → Window → Scroll sensitivity**, so that one control
still steers every extra window at once and a slot only breaks away when you
set it to a specific speed. Changes apply straight away, with the window open.

A slot keeps collecting text while it is closed, and shows what it missed when
you open it again — up to the most recent 128 KB.

In **Manage windows…**, pick GMCP modules with checkboxes (advanced CSV for patterns
like `Comm.*`). Routes need **Options → Service → GMCP → Use GMCP?** on.

### `.window` forms

```
.window
.window list
.window show <slot>
.window hide <slot>
.window clear <slot>
.window create <slot> [title…]
.window destroy <slot>
.window opacity <slot> [40-100]
```

### Gag / replace retarget

In the trigger gag or replace editor, pick a known slot from the spinner
(**None** = no retarget) or type a custom name. An empty retarget string means
no retarget. Gag removes the line from the main window and can forward it to the
slot; replace can rewrite text and optionally send the line to the slot.

Lua `NewTrigger` tables:

```lua
{ type = "gag", output = true, log = true, retarget = "chat" }
{ type = "replace", text = "[redacted]", retarget = "tells" }
```

### Lua (extra text)

```lua
CreateTextWindow("chat", "Chat")   -- create/update slot
DestroyTextWindow("chat")
ListTextWindows()                  -- array of names
ShowTextWindow("chat", true)
ClearTextWindow("chat")
NoteToWindow("chat", "hello")      -- client-only note into the slot
WindowExists("chat")
AppendLineToWindow("chat", line)   -- (windowName, line) — matches Java
```

### GMCP → window

**Options → Window → Extra text windows → Manage windows… → Edit** has GMCP checkboxes (and an
advanced CSV). Matching inbound GMCP packets are written into that slot as
`[GMCP] ModuleName {json…}` (passwords redacted). Patterns: exact (`Char.Vitals`),
family (`Char.` / `Char.*`), or `Comm.*`.

GMCP is out-of-band — it does **not** appear in the main mud buffer unless **Show
GMCP in game window?** is on. When a module is routed to an extra window, that
module is **not** also fed into main (intercept for the live feed only). Lua GMCP
watchers and mapper/native handlers still run. In-band MUD lines are unchanged —
use gag/replace if you also want to hide related room text.

### `.alias` forms

```
.alias list                    every alias and whether it is on
.alias status [name]           counts, or one alias and what it expands to
.alias on|off|toggle <name>    turn one on or off
.alias all on|off              every alias in main settings
```

Use `plugin:name` when the same name exists in more than one plugin. A disabled
alias stops matching immediately.

### Session variables in alias text

Write `${name}` in an alias replacement to drop in a session variable:

    `att`
        Replacement: `kill ${target}`
        Variable: `target` = `goblin`
        Sent: `kill goblin`

Set the variable from a trigger's **Set Variable** action, or from Lua with
`SetVariable("target", "$1")`. That is how text the *game* printed reaches a
command you type: the trigger captures it, the alias spends it.

Braces are required, so `${name}` never collides with the numeric `$1`
captures, and a bare `$` in text is left alone. An **unset** variable is left
written as-is rather than becoming empty — sending `kill` with no target is
worse than sending something visibly wrong.

Variables are per session and are not saved.

### Switching alias sets by mode

`EnableAlias(name)` returns whether an alias is live; `EnableAlias(name, true|false)`
turns it on or off. A disabled alias stops matching immediately.

Call it from a trigger's script action and aliases become mode-dependent — a
combat trigger can enable the combat aliases and switch off the travel ones:

```lua
EnableAlias("kk", true)
EnableAlias("travel_home", false)
```

Triggers and timers go further: both carry **conditions** (trigger/alias on or
off, alias replacement equals, variable equals/exists, combined with and/or)
that decide whether they fire at all, edited in their own editors. Aliases
themselves have no conditions — use `EnableAlias` to turn an alias on or off;
a trigger condition can *read* that on/off state or the alias **With** text.

## GMCP (short)

Enable under **Options → Service → GMCP Options**. Prefer **Manage modules…**
over editing the raw Supports String.

**If you need to know exactly what a world sends**, turn on **Log GMCP?** and
read `/BlowTorch/logs/gmcp.log`. That file is the real traffic. Text copied out
of the game window can pick up escaping on the way to the clipboard, so it is
not evidence of what was on the wire — a lesson learned the expensive way.

Helpers:

```
.gmcp                 — help
.gmcp ask|handshake   — Hello / enabled / native / seen (honest)
.gmcp modules         — enabled vs seen this session
.gmcp enable|disable  — toggle modules (+ live Add/Remove)
.gmcp renegotiate     — re-send Hello + Supports.Set
.gmcp status          — flags
.gmcp sniff [on|off]  — log handshake/packets to logs/gmcp.log
.gmcp sniff tail [N]  — last N GMCP lines in-game (0–100, default 40)
.gmcp feed [on|off]   — live IN/OUT GMCP in the mud window
.gmcp version         — client hello / syntax notes
.gmcp supports […]   — show or set supports modules
.gmcp dump [path]     — dump cached GMCP table
.gmcp send <payload>  — queue a GMCP packet
```

## Frames a server opens (`mudstd.frame`)

Some servers can ask the client to open a window of their own — a stats panel, a
map image — through the `mudstd.frame` GMCP package. It is **off by default**:
Options → Manage modules….

**Pictures are drawn.** When a server sends an image frame you get the picture
itself, and where it goes is your choice: Options → GMCP → **Pictures the server
sends**.

* **In a window of its own** (the default) — a small window over the game text.
  Drag it by the ☰ handle, resize it from the ◢ corner, close it with the ×.
  **Long-press its title** for a short menu: switch it to a drawer, change its
  opacity, or close it. Where you leave a frame is where the next one opens.
* **In the game text** — the picture is printed into the scrollback where it
  arrived, next to the room description it belongs to, and scrolls away with it.
  Set how tall it is with **Picture height in the text (lines)**.

A picture that is still being fetched says *Loading…*, and one that could not be
fetched says why. A blank box is the one thing it will not do.

Text content (`frame.terminal`) still arrives in the game window labelled
`[frame <id>]`. There is no webview, so a webview frame is reported rather than
shown.

The conversation is complete in both directions. When a server opens a frame,
BlowTorch answers `frame.opened`, and `frame.resized` once the window has
measured itself; when a server asks for something it cannot have, BlowTorch
answers `frame.closed` with `reason: system` rather than going quiet. And when
**you** close a frame — the × or `.frame close` — the server is told, with
`reason: user`.

```
.frame                — what the server has open here
.frame list           — the same
.frame close <id>     — close it and tell the server you did
.frame close all      — close every one
```

Frame ids are chosen by the server and are case-sensitive; `.frame list` shows
them exactly. This is **not** `.window`, which is BlowTorch's own extra text
windows — those are yours, frames are the server's.

## MCP (short)

Mud Client Protocol ([MCP 2.1](https://www.moo.mud.org/mcp/)) — in-band `#$#…`
(not GMCP). Off by default. **Options → Service → MCP Options**. Prefer **Manage packages…**.

Native packages (when enabled): hellmoo-status vitals, simpleedit editor,
displayurl (browser), ping auto-reply, mcp-cord, vmoo-client info.

```
.mcp                  — help
.mcp ask|status       — handshake / flags
.mcp packages         — enabled vs seen
.mcp enable|disable   — toggle packages
.mcp renegotiate      — re-send mcp-negotiate-can
.mcp sniff|feed|dump|vitals|send|ping|client
.mcp cord open|close|send|list
```

Lua: `Send_MCP_Packet(s)`, `Get_MCP_Status()`, literal triggers `@message-name`
(same idea as GMCP `%module`).

Optional protocols (Options → Service → **MUD Protocols**). **MTTS and MCCP are
on by default; MSDP and MSSP are off.** Reconnect after changing any of them.

```
.mssp   — dump MSSP cache (enable Use MSSP? first, reconnect)
.msdp   — dump MSDP cache (enable Use MSDP? first, reconnect)
```

**Use MTTS?** — TTYPE always follows the MUD Terminal Type Standard
(`BlowTorch` → `ANSI` → `MTTS <bits>`). On = bits **13** (ANSI+UTF-8+256);
off = bits **1** (ANSI only).

**Use MCCP?** — MUD Client Compression Protocol v2 (telnet option 86), on by
default; it saves bandwidth and you should not be able to tell it is there. If
decompression ever fails, the client says so, turns compression off for that
connection and reconnects once without it, rather than dumping the compressed
stream on screen. Turn the option off for a server whose compression
misbehaves.

## Passwords are hidden while the MUD asks for them

A MUD asks for a password by taking echoing over (telnet ECHO). While a server
holds it, the input bar masks what you type and the text is kept out of the
session log; it unmasks when the server hands echoing back, or on a disconnect.
Not every world uses this — some do, some do not.

If a server takes echoing and never gives it back, `.echo on` unmasks the bar by
hand (`.echo off` masks it again, `.echo` alone reports the state). The next
change from the server wins over the command.

This is separate from **Options → Service → Local Echo?**, which decides whether
your own commands are printed into the game window at all.

## Plugin commands (when loaded)

### `button_window` (default Free build)

    `.loadset <name>`   Load named button set
    `.clearbuttons`     Clear via button window
    `.layoutwizard`     Open the button layout wizard (packs, set names, size)

### `starter_tutorial` (loaded by default)

    `.tutorial …`   Starter Tutorial: `help` / `start` / `next` / `prev` / `skip` / `done` / `topics` / `<topic>`

On the default button set, tap **HELP** to run `.tutorial start`. The launcher
lists a built-in **Starter Tutorial** row first (offline — no MUD). Disable the
welcome note on normal MUDs via **Options → Starter Tutorial → Show on connect**,
or type `.tutorial done`. You can also toggle `starter_tutorial` off under
**Plugins**, which keeps it loaded but silent. It ships with the app and
**cannot be deleted** — like `button_window` and `connection_settings`, the
Plugins screen refuses to remove it.

## Session overflow menu

In order, as the menu builds them (all under ⋮):

1. **Aliases** / **Triggers** / **Timers** / **Options** — the editors
2. **Button Sets** — switch saved sets (`button_window`; the pack/size wizard
   is **Options → Button → Load button set from wizard**)
3. **Edit buttons** — enter button layout edit mode
4. **Speedwalk Directions** — the direction letters `.run` uses
5. **Map** — open the built-in mapper (same as `.map open`)
6. **Plugins** — load / enable / remove Lua plugins
7. **Reconnect** / **Disconnect** — same as `.reconnect` / `.disconnect`
8. **Quit** — leave the session window
9. **Search scrollback** — same as `.search`
10. **Reload Settings** — re-read this world's settings from disk
11. **Crash report** — Show log / Share log
12. **About**
13. **Help** — this manual

**Export Settings**, **Import Settings** and **Reset Settings** are **not** in
this menu — they live under **Options → Miscellaneous**, beside the storage
settings they depend on. Storage access is there too.

Connection duration appears on the ongoing notification and launcher row.

**Persistent Connection?** (Options → Miscellaneous): after brief network loss
(VPN/Wi-Fi flaps), keep retrying longer without the disconnect dialog and wait
for connectivity before reconnecting. Cannot keep a dead TCP socket — the MUD
session is re-established when the network returns.

## Related docs

- [`plugin-authoring.md`](plugin-authoring.md) — write Lua plugins (API, limits, packaging)
- [`options-guide.md`](options-guide.md) — Options / storage layout  
- [`FDROID_README.md`](FDROID_README.md) — permissions for F-Droid  
