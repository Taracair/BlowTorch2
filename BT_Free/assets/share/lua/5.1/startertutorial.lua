-- BlowTorch 2 starter tutorial (.tutorial)
-- Client-only notes; never sent to the MUD.
-- Option key (plugin XML): show_on_connect — Options → Starter Tutorial → Show on connect

local OPTION_SHOW = "show_on_connect"

local Colorizer = nil
do
	local ok, cls = pcall(function()
		return luajava.bindClass("com.resurrection.blowtorch2.lib.service.Colorizer")
	end)
	if ok and cls ~= nil then
		Colorizer = cls
	end
end

--- Ask Colorizer for a colour, and fall back rather than throw.
---
--- Calling a method that does not exist on the Java object raises out of
--- luajava, and the whole command dies with "error in error handling" - which
--- says nothing about the real cause. That is exactly what happened with
--- getBrightGreenColor, which is not a method Colorizer has. A missing colour
--- should cost the colour, not the command.
local function colorOf(method, fallback)
	if Colorizer == nil then
		return fallback
	end
	local ok, value = pcall(function()
		return Colorizer[method](Colorizer)
	end)
	if ok and type(value) == "string" then
		return value
	end
	return fallback
end

local function cyan()
	return colorOf("getBrightCyanColor", "\027[1;36m")
end

local function white()
	return colorOf("getWhiteColor", "\027[0;37m")
end

local function noteBlock(title, body)
	local sep = "----------------------------------------"
	local t = cyan() .. sep .. "\n"
	if title ~= nil and title ~= "" then
		t = t .. title .. "\n" .. sep .. "\n"
	end
	t = t .. white() .. body .. "\n" .. cyan() .. sep .. white() .. "\n"
	Note(t)
end

local function noteLine(msg)
	Note(white() .. msg .. "\n")
end

-- Ordered topic list for start / next / prev
local TOPIC_ORDER = {
	"welcome",
	"practice_world",
	"client_commands",
	"buttons_basics",
	"buttons_swipe",
	"buttons_hold",
	"buttons_accordion",
	"buttons_super",
	"buttons_sets",
	"buttons_make",
	"buttons_edit",
	"movement",
	"aliases",
	"triggers",
	"timers",
	"sensors",
	"coloring",
	"tappable",
	"keyboard",
	"completion",
	"search",
	"mapper",
	"wrap",
	"logging_export",
	"overflow_menu",
	"gmcp",
	"mcp",
	"stay_connected",
	"disconnect_reconnect",
	"copy_text",
	"options_cleanup",
	"display",
	"plugins",
	"finish",
}

local TOPICS = {}

TOPICS.practice_world = function()
	noteBlock("Starter Tutorial — Bex and the Practice Yard",
[[This session also holds a small practice yard with a tutor in it, called
Bex. It is offline: nothing you type there reaches the network. Three rooms,
no exploring - the point is the lessons, not the map.

Bex teaches four things and then checks your work for real: buttons, aliases,
triggers and timers. When you say you have made an alias, Bex looks at the
alias you actually made and says what is wrong with it if anything is - a
missing capture, a $1 that is never used, a trigger with a pattern and nothing
attached.

Anything you can type is marked like this in the text:
  >> ask bex about lessons

To begin, type:  look
Then:            ask bex about lessons

This reading tour is unchanged and always here: .tutorial next]])
end

TOPICS.welcome = function()
	noteBlock("Starter Tutorial — Welcome",
[[BlowTorch 2 talks to a MUD over the network, but many helpers stay on the
phone. Anything you type starting with a single dot (.) is a client command
and is not sent to the game unless you escape it with ..

Because the app covers a lot of ground for many different players, this
tutorial (and the Help / user guide) may occasionally be slightly out of
date in a few places. When that happens, the app itself — what it shows on
screen — is the source of truth. Please report mistakes on GitHub:
  https://github.com/Taracair/BlowTorch2/issues

Try it now:
  • Tap NEXT / PREV / TOPICS on the pad (or type .tutorial next)
  • Tap HELP anytime to restart (.tutorial start)
  • Tap LOAD to try .loadset tutorial (hold LOAD or flip → .loadset default)

This tour is hands-on: you will build a .loadset button, learn triggers for
beginners, and poke swipe / hold / accordion demos. Lessons also cover
aliases, timers, sensors, colors, keyboard, completion, search, mapper, wrap,
logging, ⋮ menu, GMCP/MCP, reconnect, copy, Options, display, and plugins.

Type:  .tutorial next
Or:    .tutorial topics
Disable later: Options → Starter Tutorial → Show on connect = off]])
end

TOPICS.overview = TOPICS.welcome

TOPICS.client_commands = function()
	noteBlock("Client commands (.commands)",
[[Lines that start with a single . are client commands (also called period
or system commands). They run on the phone — aliases and button commands
use the same path. Full list: overflow → Help (or .tutorial topics for
this tour).

Escape / toggle:
  ..look     sends .look to the MUD (leading . is not a client command)
  ..         alone toggles .command processing on or off

Client-only echo (never sent to the MUD):
  .note <text>
Try:  .note hello from the tutorial

Settings without reaching for the menu:
  .options           open the Options screen, same as the ⋮ menu.
                     Handy on a button when the menu is out of reach

Two you only need when something goes wrong:
  .settings          what is on disk; backup / restore the kept copy
  .echo on|off       unmask the input bar if a MUD took echoing over
                     at a password prompt and never gave it back

Semicolons: Options → Service → Process Semicolons? (default on) turns ;
into a newline so look;score sends two lines. Turn it off if your MUD
uses ; in commands.

Also: Options → Service → Process System Commands? must be on (default)
for .commands to work.]])
end

TOPICS.buttons_basics = function()
	noteBlock("Buttons — Tap",
[[On-screen buttons send their Command when you tap and release inside the
tile. That goes through the same path as typing: aliases and .commands are
processed. A button whose command is .clearbuttons only clears the layout;
the MUD never sees it.

Try now:
  • Tap LOOK or SCORE — here they echo a short .note tip (offline-safe)
  • Tap HELP → restarts this tour
  • Tap CLEAR → only BACK remains; tap BACK to restore
  • Tap LOAD → .loadset tutorial (compact set); hold/flip LOAD → default

Empty commands do nothing. Gesture demo tiles (SWIPE / HOLD / ACC) teach
the next lessons.]])
end

TOPICS.buttons_swipe = function()
	noteBlock("Buttons — Swipe",
[[Each button can define eight swipe commands: the four straight directions
↑ ↓ ← → and the four corners ↖ ↗ ↙ ↘. Drag roughly a finger-width off the
tile in that direction.

Try now: open the SWIPE tile and flick it — each direction has a different
tip. Compass tiles also have one demo swipe each: straight tiles use the
straight swipes, corner tiles (NW/NE/SW/SE) use the matching corner.

A corner swipe with nothing bound falls back to the nearest straight
direction, so a button that only uses ↑ ↓ ← → still behaves exactly as it
always did — you do not have to aim more carefully than before.

Swipe overrides the older Flip action when a swipe command is set. In Edit
mode, open a button and fill the Swipe fields. Optional markings draw on the
tile: letters on the edges for straight swipes, small arrows in the corners
for diagonals, H for hold, a chevron for an accordion.

Two switches decide whether they are drawn, and the first one wins:

  all buttons   Edit mode → the gear button → Markings on buttons
  this button   the button's own Swipe tab, same wording

With the all-buttons switch off nothing is drawn anywhere, however the
individual buttons are set. With it on, each button may still opt out.]])
end

TOPICS.buttons_hold = function()
	noteBlock("Buttons — Hold",
[[Press and hold a button to fire its Hold command (separate from tap).
Use this for a second macro on the same tile — for example tap LOOK and
hold for SCORE, or hold to open a door while tap walks.

Try now:
  • Long-press HOLD until the tip appears
  • Long-press any compass letter — each has a different Hold tip
  • Long-press LOAD — restores .loadset default

Edit the button → Hold command. A small H marker appears when gesture
hints are on.]])
end

TOPICS.buttons_accordion = function()
	noteBlock("Buttons — Accordion",
[[Tap ACC on the bottom row — it expands child buttons downward (LOOK /
SCORE / TIP) so they stay clear of the compass and HELP row above. TIP
re-opens this lesson via .tutorial.

An accordion parent expands up to twenty child buttons (label + command)
in a chosen direction. In Edit buttons → Accord., add them one by one
(+ Add sub-button below/above/left/right follows the expand layout),
reorder with the row controls, and drop empties on Done. Trigger can be
tap, hold, or swipe.

The gesture that opens the accordion cannot also send its own command —
that field stays filled but locked on the Tap / Swipe tabs.

Editor badges: T = tap, H = hold, S = swipe. Children can auto-close after
use. Accordion data is stored with the button set (Lua); use Edit buttons
to build your own.

Expanded children draw on top of neighbouring grid buttons. To make the
fan stand out, open the parent in Others and turn on Border with a colour
— children reuse that border.

Handy when one corner of the screen must hold several macros.]])
end

TOPICS.buttons_super = function()
	noteBlock("Buttons — Super-buttons",
[[A button does not have to stay in the grid. Edit a button, open Others,
and tick "Float over the game": a copy appears on the screen itself, on
top of the game and on top of the keyboard. Very long press moves it.

Two modes. "Always visible" keeps it on screen all the time — in play
mode only the floating copy is drawn, so it does not stack on the grid
tile and look smudged. "Show with keyboard" gives you a keyboard
assistant -- it exists only while the keyboard is open, and is hidden
everywhere otherwise, the grid included.
Put .kb commands on that one (.kb stepb, .kb stepf, .kb stepu, .kb paste,
.kb close) and you have caret keys, command recall and paste under your
thumb while typing.

Drawing over the keyboard needs Android's "Display over other apps"
permission; you are asked the first time. Without it the button is still
there, but the keyboard covers it.

Others → Border strokes the floating copy too (Square/Round shape). Thin
outline is a separate auto-contrast frame used when Border is off.

On Android 9 and 10 "Show with keyboard" may never appear -- the client
cannot reliably tell the keyboard is open there.]])
end

TOPICS.buttons_sets = function()
	noteBlock("Buttons — Sets & .loadset",
[[Button layouts live in named sets (default, tutorial, or names you add).
Switching sets is a client command — perfect on a button:

  .loadset <name>     load that set (button_window plugin)
  .clearbuttons       clear on-screen buttons (BACK restores)

Try now:
  1. Tap LOAD          → switches to the compact "tutorial" set
  2. Hold or flip LOAD → .loadset default (full starter pad)
  3. Or type:  .loadset default

Why this matters: one pad for combat, another for shopping, another for
crafting — flip between them with one tap instead of re-editing.

In edit mode, the gear opens set options (name, size, grid, …). Done saves.

Do not want to build a pad from nothing? Options → Button → Load button
set from wizard (or .layoutwizard) offers ready-made packs — Compass,
Newbie, Combat, Explorer, Social — each written to a set name you pick.

Next lesson: make your own .loadset button.]])
end

TOPICS.buttons_make = function()
	noteBlock("Buttons — Make a .loadset button",
[[Short exercise (about a minute):

  1. Overflow → Edit buttons  (or long-press ⋮ next to Edit/Send)
  2. Long-press empty space to add a tile (if auto-create is on)
  3. Tap the new tile → set Label to e.g. COMBAT
  4. Set Command to:  .loadset default
     (or .loadset tutorial — any set name you have)
  5. Done to save

Optional: a second button with the other .loadset, or put default on Hold
and an alternate set on Tap (like the LOAD demo).

Tips:
  • .commands on buttons never go to the MUD
  • After CLEAR, BACK brings the previous layout back
  • You can rename sets in the gear → set options

When you are done experimenting: .loadset default
Then: .tutorial next]])
end

TOPICS.buttons_edit = function()
	noteBlock("Buttons — Edit layout",
[[Enter edit mode: overflow menu → Edit buttons, or long-press the ⋮ next
to Edit/Send. In edit mode the ⋮ is hidden — use the strip: gear (set
options), Cancel (left), Done (right).

Long-press empty space (if auto-create is on) to add a button. Drag to
move, tap a tile to edit label/commands/gestures/accordion. Done saves
the set.

Try: move CLEAR slightly, Done, then CLEAR / BACK to confirm the set
saved. Undo a bad edit with Cancel before Done.]])
end

TOPICS.movement = function()
	noteBlock("Movement",
[[Default compass buttons send north/east/south/west/up/down. Flip or
swipe on a direction can open that exit (e.g. open n) if configured.

Speedwalk: .run <directions> using letters from Speedwalk Directions
(overflow → Speedwalk Directions). Defaults: n e s w u d, plus diagonals
h=nw, j=ne, k=sw, l=se. Prefix with a count.

Examples:
  .run 3n2e
  .run 3n,open door,2e
  .run 3desw2n

Commas insert free-text commands between walks. Edit the letter map in
Speedwalk Directions if your MUD uses different shortcuts.

Try: type .run n  (offline it still goes through the client path).]])
end

TOPICS.aliases = function()
	noteBlock("Aliases — rewrite what you type",
[[An alias watches what YOU send and rewrites it before it reaches the
server. Example: typing "k goblin" becomes "kill goblin".

Open: action bar / ⋮ → Aliases. Patterns use Java regular expressions;
captures are $1, $2, … in the With text. Literal-friendly patterns avoid
regex until you turn Literal off.

Live edit for a simple word name (letters, digits, _):
  .c cast 'fireball'     if alias key is c, updates its With text
  .name new text         same idea for any simple alias key

Patterns with spaces or ^…$ regex must be edited in the Aliases dialog.

Aliases are for shortcuts you type. Triggers (next) react to game text —
but a trigger can borrow an alias's text as its pattern, so one alias can
be the single place a word is written down. See Triggers.

The ? button on the Aliases list, and beside Done in the alias editor,
explains the fields and the ^ / $ checkboxes with examples.]])
end

TOPICS.triggers = function()
	noteBlock("Triggers — react to the game",
[[A trigger watches incoming text from the MUD (or GMCP/MCP hooks) and
runs responders when a pattern matches. Beginners usually start with:

  1. Pattern — plain text or regex that appears in game output
  2. One responder — Ack (send a command), Toast, Gag, Color, …

Example idea: when the game says you are hungry, Ack sends "eat bread".
Another: gag spammy combat lines; Color important tells; Toast a warning.

Open: ⋮ → Triggers → add. Keep Literal on until you need regex. Captures
in regex mode become $1, $2 in Ack / Replace / Toast text. The ? button
beside Done explains the pattern box, and the preview under the box says
what your pattern will really do before you save it. The ? on the
Triggers list itself explains what a trigger is, with examples.

Using an alias as the pattern: type an alias's NAME on its own and the
trigger watches for that alias's TEXT instead. With an alias spares that
types "circuit", a pattern of spares waits for the word circuit. Change
the alias later — in the Aliases dialog or with .spares newtext — and
every trigger using it follows at once. Inside a longer pattern name it
with $alias{spares}. Four aliases cannot be used and the preview says
which: no such alias, several commands (a;b), $1-style captures from what
you type, or an alias naming another alias. Then the pattern is left as
you wrote it, so the trigger visibly does not fire.

Testing without the game: .note some text prints a line to the window and
nothing else, so you can see a trigger colour or a tappable word fire.

Enable / disable:
  • Each trigger has an on/off toggle in the Triggers list (row toolbar).
  • Options (=) menu: "Enable all triggers (current list)" and
    "Disable ALL triggers (current list)" — these affect every trigger in
    the active filter only (Main/plugin + optional group). Disable asks
    for confirmation first.
  • Options (=) also has Filter by group (All / (default) / named groups).
  • From the input bar: .trigger on|off|toggle <name|plugin:name>,
    .trigger group …, .trigger all on|off,
    .trigger plugin <plugin> all on|off (see .trigger for help).
  • Lua plugins can still use EnableTrigger / TriggerEnabled /
    EnableTriggerGroup.

Groups:
  • In the trigger editor, set Group (e.g. combat). Leave blank for none.
    The field suggests existing group names from the current list.
  • The list shows [group] before the pattern and sorts by group.
  • Then: .trigger group off combat  (or group on / toggle; main+plugins)

Conditions (advanced, in the trigger editor):
  • Extra gate after the pattern matches — not a substitute for the pattern.
    Optional IF checks (All/AND or Any/OR) run before responders.
    Empty list = always fire (old behavior).
  • Example: only Ack when another trigger "combat_mode" is enabled —
    add Condition "Trigger enabled" and pick combat_mode.
  • Variables: Set Variable responder (or Lua SetVariable) stores a session
    string; condition "Variable equals/exists" can gate later triggers.

GMCP hooks: literal pattern starting with % (e.g. %Char.Vitals).
MCP hooks: @message-name.

Responders worth knowing early:
  Ack            send text (or Lua) — can use $1
  Replace / Gag  change or hide the matched line
  Color          tint matching text
  Toast / Notification   phone-side alerts
  Set Variable   store a session name=value (may use $1)

Try: open Triggers, glance at the list, toggle one if you already have
any — then come back with .tutorial next.]])
end

-- Back-compat topic name from older builds / notes
TOPICS.aliases_triggers = function()
	TOPICS.aliases()
	noteLine("Continuing with triggers…")
	TOPICS.triggers()
end

TOPICS.timers = function()
	noteBlock("Timers",
[[Open Timers from the action bar / overflow. Each timer has a name, an
interval, optional repeat, and responders (same kinds as triggers: Ack,
Toast, Notification, …).

The interval is the "Every:" row — three boxes, hours / minutes /
seconds — with presets (30s, 1m, 5m, 15m, 1h) and a running summary
underneath. The boxes are added up, so 90 in the seconds box is the same
as 1m 30s.

Conditions (in the timer editor):
  • Extra gate when the timer fires — same AND/OR types as triggers.
    Empty = always fire responders. Set Variable / session vars still apply.

Control from the input bar:
  .timer play <name>
  .timer pause <name>
  .timer reset <name>
  .timer stop <name>
  .timer info <name>
  .timer duration <name> <seconds>

Optional third token suppresses toasts (not used with info), e.g.
  .timer play mytick silent

Changing the duration does not stop the timer: one that was running
keeps running on the new length, one that was stopped stays stopped.

Name matches the timer list (not a numeric index). Useful for ticks,
cooldowns, or reminder toasts while you play.

The ? button beside Done in the timer editor says all of this on the
phone, including that Conditions are checked when the timer fires and not
while it counts, and that Group is for finding timers in the list — there
is no .timer group command. The ? on the Timers list explains what a
timer is and how it differs from a trigger.]])
end

TOPICS.sensors = function()
	noteBlock("Sensors — the phone as a trigger source",
[[Not the button gestures. Swipes and holds on a tile are set in the button
editor. This lesson is the phone's own hardware: proximity, motion, light,
the charger, the headphone jack, the screen.

Each reading is an ordinary trigger, so everything a trigger already does
works with it — send a command, run a script, speak, play a sound, ring the
bell, set a variable, or gate itself on a condition.

Ask your phone what it has first; models differ a lot:
  .sensor            what is set up here
  .sensor caps       which hardware provides each reading on this phone

Point one at a command:
  .sensor facedown afk
  .sensor faceup afk off
  .sensor cover flee

Try it without moving the phone (this works offline, here, now):
  .sensor fire facedown

That runs whatever you set up; it does not prove your phone can see the
gesture. For that, Options -> Device -> Sensors... and Test on the row: it
watches the sensor while you do it and tells you whether the phone noticed.
Worth doing before you build anything on a reading, because sensor hardware
differs by model.

Readings: wave, cover, facedown, faceup, shake, pickup, moving, still,
gotdark, gotbright, headphonesout, headphonesin, powerin, powerout,
screenoff, screenon. The last six need no sensor chip — Android tells every
app — so a profile built on those works on any phone.

Often better than a reading of its own: use the phone as a CONDITION.
On any trigger or timer, Conditions → The phone gives you "Headphones are
plugged in", "Screen is off", "Phone is charging" without typing a variable
name. A Speak action gated on headphones never reads your tells out loud on
the bus.

Behind that picker are session variables you can also read from Lua:
  .sensor watch on         keep device.* up to date
  .sensor state            show them now
  GetVariable("device.charging")

Everything here is off until you ask for it, and movement readings are held
back while the screen is off or the app is in the background — a phone in a
pocket cannot send commands. Both switches sit in Options → Device, next to
Calibrate shake and Calibrate light. The Sensors… row in that same group is
the list of readings, not the switches.

One thing to know: a sensor trigger is not aimed at one world. It fires in
every world you have open, so with two MUDs connected one shake sends twice.]])
end

TOPICS.coloring = function()
	noteBlock("Coloring — .colordebug",
[[ANSI colors from the MUD are drawn in the game window. To debug them:

  .colordebug 0   normal display
  .colordebug 1   color on, show codes
  .colordebug 2   color off, show codes
  .colordebug 3   color off, no codes

Window options also control word wrap, hyperlinks, and font size under
Options → Window. Trigger Color responders can tint matched lines.]])
end

TOPICS.tappable = function()
	noteBlock("Tappable words — press what the game printed",
[[A trigger can make what it matched pressable in the game text. Options →
Triggers → your trigger → add action → Tappable Word.

  Pattern        You see (.+) lying here
  Tappable part  1
  Command        get $1

Tappable part 0 lights up the whole match; 1 to 9 lights up only that
bracket. A pattern usually needs the rest of the line to recognise it, and
you rarely want the whole sentence pressable.

In the command:
  $word   the text that was pressed
  $0      the whole match
  $1..$9  the bracketed parts of the pattern

Add a second command and pressing the word opens a small menu at it instead
of sending straight away - the first command stays on top of the menu.

Underline, Bold and Frame mark the word; use any of them or none. Color is
not here on purpose: put a Color action on the same trigger.

The word stays pressable while the line is in the buffer, so scrolling back
and pressing something from ten lines ago works.]])
end

TOPICS.keyboard = function()
	noteBlock("Keyboard — .kb",
[[Control the input bar without the system IME:

  .kb            help
  .kb popup text show IME with text
  .kb add text   append
  .kb flush      send current input
  .kb clear      clear text
  .kb sel / cut / copy / paste
  .kb start|end|stepf|stepb|stepu|stepd

Edit on the input bar expands Sel/Cut/Copy/Paste and a compact arrow pad.
Up/down recall command history.]])
end

TOPICS.completion = function()
	noteBlock("Suggestions and the prompt — .suggest / .prompt",
[[The soft keyboard never learns a mob called grizzled or a player called
Tonkatsu, and corrects them into English. Suggestions offer back what the world
actually said. All of it is off until you ask, under Options → Input or on
.suggest (.complete still works and means the same thing):

  .suggest on|off         offer words the game just used
  .suggest lines N        how far back counts as recent (lines, default 300;
                          0 = the whole session)
  .suggest 1 .. 8         take that chip — for a super button, not the bar:
                          typing into the bar empties the strip
  .suggest loose on|off   forgive typos once the exact spelling finds nothing
                          (grzld finds grizzled)
  .suggest phrases on|off offer whole names, not only the one word
  .suggest ghost on|off   draw the rest of the word after the cursor, dimmed;
                          drawn only, never sent
  .suggest show N         at most N suggestions total (bar + ghost), 1-8
  .suggest ghostlines N   extra rows the field may grow by (1-6). At 1 the
                          others still fill the rest of the line you are on;
                          not the same as show — does not cap how many offered
  .suggest where floating|bar|off   where the chips go. off still leaves the
                          ghost and .suggest 1..8 working, with no bar at all
  .suggest persist on|off keep the bar up even when it has nothing to say
  .suggest opacity N      how solid the floating chips are (10-100)

    The game says:  A grizzled cave troll lumbers in.
    You type:       k gri
    The strip:      1 grizzled     ← tap it, or .suggest 1 from a button

The prompt bar is separate. A prompt is the short status line most MUDs print
after every command — [HP 450/500 EN 300/300] > and the like. It repeats down
the whole screen, which on a phone costs you half of what you can see.

  .prompt on|off          pin that line in one place above the input instead,
                          rewritten each time a new one arrives
  .prompt                 also reports "prompts seen: N" — zero means this
                          world sends no prompt, which many MOOs do not

The bar shows the world's own text and nothing more: it does not read the
numbers and draws no health bar. A bar that fills and empties is a trigger with
a capture, or GMCP.

Vocabulary is forgotten when you connect, so one world's names never show up in
another.]])
end

TOPICS.search = function()
	noteBlock("Search — .search",
[[Search the scrollback:

  .search                open search UI
  .search phrase
  .search 'with spaces'
  .search next | n
  .search prev | p
  .search close

Also: overflow → Search scrollback. Buttons may use /search 'phrase'.
Matches highlight in the buffer; next/prev walk through them.]])
end

TOPICS.mapper = function()
	noteBlock("Mapper — room map",
[[Built-in map of rooms (tiles) and exits — not the old ForgeMap plugin.
Open it from overflow → Map, or type .map open (close / toggle also work).

What you see
  • Green tile  = current (where the mapper thinks you are)
  • Yellow edge = selected (last tap)
  • [REC] in the title = recording is on
  • Title: Browse | Edit (Browse default) + nest breadcrumb
  • Full / Float / ✕ = window mode and close
  The map stays under ⋮ so the overflow menu stays usable.
  Edit mode is required to create nests, Draw, Links, and delete levels.

Two ways to build
  1) Record while you walk
     .map new mymap   (optional fresh file)
     Open the map → Rec → walk the MUD as usual → Stop → Save
     Outbound commands become exits. Compass moves (n/e/s/w, go west,
     go se, …) place neighbors on a grid; up/down change level while
     Recording; out/in become special exits beside the room.
  2) Draw by hand (no walking required)
     Title → Edit → ⚙ → Draw → tap empty cells to place rooms
     Long-press empty = place and set Here
     Links → tap FROM then TO → type the walk verb (go west, n, out…)
     Or long-press a tile → Add neighbor… / Move… / Set as Here / Delete

Levels (tile-anchored — not one global stack)
  Each Here tile can open its own basement/attic (per-door nests).
  L-/L+ = nest down/up from Here. Browse: follow/return only; create needs Edit.
  Browse floors: ↕ → List (tap = view; long-press = Go Here), or tap ▲/▼/◆
  badges. Edit adds Delete… (confirm; removes floor + tiles; not last level).
  ↕ Levels radial: List, ↑, ↓, Root, Door, Delete (Delete = Edit only)
  ⚙ Tools radial: Paths, Draw, Links, Here, Edit, Save, Find, Rec
  (long-press title also opens a radial). up/down while Recording still works;
  L-/L+ is the manual tool for weird MUDs (e.g. west into a cellar).

Toolbar cheatsheet
  Rec/Stop  Follow  L-/L+  Find  Undo  Center   (CSV-configurable)
  ↕ Levels   ⚙ Tools   (always present; no Draw/Links strip)
  Paths = space for arrows; Pack = tight tiles

Gestures
  Long-press tile + drag = move (release without move = tile menu)
  Double-tap tile = Set as Here
  Tap arrow label / +N = list walk verbs on that edge (unlink optional)
  Tap ▲/▼/◆ = jump to linked floor (browse)

Movement lexicon (summary; full list: .map dirs)
  +x = east, +y = south on the grid
  n/s/e/w (+ go/walk/move) → grid step
  ne/nw/se/sw → diagonal
  up/climb vs down/descend → level change while Recording
  in/enter, out/leave → special
  Built-in compass wins over Speedwalk keys (h/j/k/l = nw/ne/sw/se)

Useful .map commands
  .map / .map help
  .map mode float|fullscreen
  .map mode browse|edit|toggle
  .map record|follow …
  .map level list|prev|next|set <name>   (prev/next = L-/L+ nests)
  .map level delete <id|name>
  .map find|path|goto <query>
  .map maps | load <name> | new <name>
  .map import <path|name>   .map export|save [path]
  .map add | here | delete | neighbor | move
  .map link|unlink …   .map dirs   .map zoom in|out|reset|<factor>
  .map conflict [list [all]|resolve|ignore <id|n>|all|purge]
  .map capture preview|apply   (Options regex; or toolbar Capture dialog)

Files live under /BlowTorch/maps/ (autosave after edits).
Options → Mapper: enable, float default, opacity, follow, path auto-send,
Use GMCP Room (also builds neighbors from Room exits), toolbar CSV
(optional capture token), Capture Title/Exits Regex.
Without GMCP (many MOOs), prefer Rec + Edit Draw/Links.
Full reference: overflow → Help → Mapper.]])
end

TOPICS.wrap = function()
	noteBlock("Input wrap — .wrap",
[[.wrap controls whether the input bar grows with multiline text
(Grow Input Bar?). It is not the same as Word Wrap for game text.

  .wrap          show status
  .wrap on|off

Also: Options → Input → Grow Input Bar?
Word Wrap for output: Options → Window.]])
end

TOPICS.logging_export = function()
	noteBlock("Logging and import / export",
[[Session log (game output, ANSI stripped):
  Options → Service → Log Session to File?
  Session Log Directory blank = /BlowTorch/session_logs/
  Files: {profile}_{yyyy-MM-dd_HH-mm-ss}.txt

Import / export / reset this session’s settings:
  Options → Miscellaneous → Export / Import / Reset Settings
  Default folder: /BlowTorch/settings/ (or SAF pickers)

Storage access (Android 11+):
  Options → Miscellaneous → Manage Storage Access
  Grants All files access for a shared /BlowTorch/ tree
  (settings, backups, launcher, session_logs, logs).
  Without it, the app falls back under Android/data.

Launcher also has Export Server List / Backup All Settings.]])
end

TOPICS.overflow_menu = function()
	noteBlock("Overflow menu (⋮)",
[[The session overflow / options menu is your map of editors and tools:

  Aliases                 edit input rewrites
  Triggers                match incoming text / hooks
  Timers                  repeating / one-shot responders
  Options                 Program Settings (Display, Window, …)
  Edit buttons            button layout edit mode
  Speedwalk Directions    letters for .run
  Plugins                 load / manage Lua plugins
  Reconnect / Disconnect  connection control
  Quit                    leave the session window
  Map                     built-in Mapper (also .map open|toggle)
  Search scrollback       same as .search
  Reload Settings         re-read this world’s settings from disk
  Crash report            Show log / Share log
  About
  Help                    this app’s user manual

Export / Import / Reset Settings live under Options → Miscellaneous.

Action-bar icons may show Aliases / Triggers / Timers / Options when
there is room; otherwise they live under ⋮.]])
end

TOPICS.gmcp = function()
	noteBlock("GMCP (brief)",
[[GMCP is an out-of-band telnet channel (option 201). Enable under
Options → Service → GMCP Options. Prefer Manage modules… over editing
the raw Supports String by hand.

Useful helpers:
  .gmcp ask|handshake   what we declare vs what was seen
  .gmcp modules         enabled vs seen
  .gmcp enable|disable  toggle modules
  .gmcp renegotiate     re-send Hello + Supports.Set
  .gmcp feed on|off     echo packets in the game window

Nothing auto-enables from "seen" traffic.]])
end

TOPICS.mcp = function()
	noteBlock("MCP (brief)",
[[Mud Client Protocol uses in-band #$# messages (common on MOOs). Not the
same as GMCP. Options → Service → MCP Options (off by default).

  .mcp ask|status
  .mcp packages / enable|disable / renegotiate
  .mcp sniff|feed|dump|vitals|send|ping|client
  .mcp cord open|close|send|list

Literal triggers can hook @message-name.]])
end

TOPICS.stay_connected = function()
	noteBlock("Staying connected",
[[Options → Service:

  Auto Reconnect?          reconnect after a drop (default on)
  Auto Reconnect Tries     hard limit on attempts (default 5)
  Keep Wifi Alive?         hold a Wi-Fi lock while connected
  Battery optimization…    open the system exemption flow

A one-shot battery dialog may appear while connected if the OS still
optimizes BlowTorch. Connection duration shows on the ongoing
notification and the launcher row.

Use Keep Wifi Alive and battery exemption when you leave the screen
off mid-session.]])
end

TOPICS.disconnect_reconnect = function()
	noteBlock("Disconnect / Reconnect",
[[  .disconnect   drop the current session (same as overflow Disconnect)
  .reconnect    connect again (same as overflow Reconnect)

If Auto Reconnect is on, the client may try again on its own after a
drop (see .tutorial stay_connected). The ongoing notification and
launcher row show connection duration.]])
end

TOPICS.copy_text = function()
	noteBlock("Copy text (two fingers)",
[[To copy from the game window:

  1. First finger — touch where selection should start (marks the start).
  2. Second finger — tap to open the copy / selection widget.

One-finger long-press alone does not open copy. Drag the cursors, then
use the widget copy control. On-screen buttons may hide while selecting
so the widget stays usable.]])
end

TOPICS.options_cleanup = function()
	noteBlock("Options layout",
[[In-game Options groups settings under Program Settings, including:

  Display   orientation, fullscreen, NAWS, keep screen on
  Window    font, buffer, word wrap, hyperlinks, ANSI
  Input     history, keep last, Grow Input Bar (.wrap)
  Service   encoding, logging, battery, reconnect, Wi-Fi;
            nested GMCP / MCP / MUD Protocols
  Bell      bell reactions
  Miscellaneous   storage access and paths, Export / Import / Reset
                  Settings, persistent connection, ⋮ button look

Plugin-specific pages (Button, Starter Tutorial, …) appear when that
plugin is loaded. Prefer nested Manage modules… / Manage packages…
over raw Supports / Packages strings.]])
end

TOPICS.display = function()
	noteBlock("Display",
[[Options → Display:

  Orientation              portrait / landscape / sensor
  Keep Screen On?          stop the screen sleeping while connected
  Use Fullscreen Window?   hide the notification bar
  Terminal Width (NAWS)    columns reported to the server
  Terminal Height (NAWS)   rows (maps / ANSI layout)
  Show Terminal Size Tip?  one-time tip for new profiles

Toggle fullscreen without opening Options:
  .togglefullscreen

NAWS tells the MUD your terminal size so maps and prompts fit. After
changing width/height, some servers need a look or a reconnect.]])
end

TOPICS.plugins = function()
	noteBlock("Plugins",
[[Overflow → Plugins lists loaded Lua plugins (for example button_window
and starter_tutorial). Use Load to add a plugin from
/BlowTorch/plugins/; remove one from the list to unload it.

Each row has an on/off toggle (lightbulb). Off keeps the plugin loaded
but skips its triggers, aliases, timers, and .commands until you turn
it back on. button_window cannot be disabled (it owns the button pad).

Plugin Options pages show under Options only while that plugin is
loaded — Button for gesture hints, Starter Tutorial for Show on connect.

To stop this tour’s welcome without removing the plugin:
  Options → Starter Tutorial → Show on connect = off
  or .tutorial done
  or toggle starter_tutorial off in the Plugins list

starter_tutorial ships with the app and cannot be deleted (nor can
button_window or connection_settings) — turning it off in the Plugins
list is how you retire it. Only load plugins you trust.]])
end

TOPICS.finish = function()
	noteBlock("Tutorial — Finish",
[[You can re-open any lesson with .tutorial <topic> or walk them with
.tutorial next / .tutorial prev. List names: .tutorial topics

Quick recalls:
  HELP / .tutorial start     restart
  LOAD / .loadset …          switch button sets
  .note text                 client-only echo
  ⋮ → Triggers               match game text (toggle each on/off)

Overflow → Help opens the full user manual.

To stop the welcome note on connect:
  Options → Starter Tutorial → Show on connect = off
  or type:  .tutorial done

Happy mudding.]])
end

-- 0 = not yet on a lesson (so NEXT from bare intro opens lesson 1, not lesson 2).
local currentIndex = 0

local function topicIndex(name)
	for i, n in ipairs(TOPIC_ORDER) do
		if n == name then
			return i
		end
	end
	return nil
end

local function showTopic(name)
	local key = name
	if key == "overview" then
		key = "welcome"
	end
	local fn = TOPICS[key]
	if fn == nil then
		noteLine("Unknown topic: " .. tostring(name) .. "  (try .tutorial topics)")
		return false
	end
	local idx = topicIndex(key)
	if idx ~= nil then
		currentIndex = idx
	end
	fn()
	local nav = string.format("  [%d/%d]  .tutorial next | prev | topics",
		currentIndex, #TOPIC_ORDER)
	noteLine(nav)
	return true
end

-- Called from Java doOfflineStartup once the offline session / window is ready.
-- Do not rely on OnBackgroundStartup for lesson text: that runs during settings
-- load, before the session window is live, so Notes can be lost while still
-- advancing currentIndex (NEXT then skips welcome → lesson 2).
function starterTutorialBegin(args)
	currentIndex = 1
	showTopic(TOPIC_ORDER[1])
end

local function showHelp()
	noteBlock("Starter Tutorial — Help",
[[.tutorial              this help
.tutorial start        begin at welcome
.tutorial next|prev    walk the lesson list
.tutorial skip         jump to finish
.tutorial done         turn off Show on connect
.tutorial topics       list topic names
.tutorial <topic>      open one topic

Topics: welcome, client_commands, buttons_basics, buttons_swipe,
buttons_hold, buttons_accordion, buttons_super, buttons_sets, buttons_make,
buttons_edit, movement, aliases, triggers, timers, coloring, keyboard,
search, wrap, logging_export, overflow_menu, gmcp, mcp, stay_connected,
disconnect_reconnect, copy_text, options_cleanup, display, plugins,
finish]])
end

local function listTopics()
	local lines = { "Tutorial topics:" }
	for i, name in ipairs(TOPIC_ORDER) do
		lines[#lines + 1] = string.format("  %2d  %s", i, name)
	end
	lines[#lines + 1] = "Open with: .tutorial <number>   e.g. .tutorial 7"
	lines[#lines + 1] = "        or .tutorial <name>     e.g. .tutorial triggers"
	noteBlock("Topics", table.concat(lines, "\n"))
end

local function readShowOnConnect()
	if GetPluginSettings == nil then
		return true
	end
	local ok, settings = pcall(GetPluginSettings)
	if not ok or settings == nil then
		return true
	end
	local ok2, val = pcall(function()
		return settings:getOptionValue(OPTION_SHOW)
	end)
	if not ok2 or val == nil then
		return true
	end
	local s = string.lower(tostring(val))
	if s == "false" or s == "0" or s == "off" or s == "no" then
		return false
	end
	return true
end

local function setShowOnConnect(enabled)
	if GetPluginSettings == nil then
		return false
	end
	local ok, settings = pcall(GetPluginSettings)
	if not ok or settings == nil then
		return false
	end
	local ok2 = pcall(function()
		settings:updateBoolean(OPTION_SHOW, enabled)
	end)
	if ok2 and SaveSettings ~= nil then
		pcall(SaveSettings)
	end
	return ok2
end

function tutorialCommand(args)
	local raw = args or ""
	local trimmed = string.gsub(raw, "^%s+", "")
	trimmed = string.gsub(trimmed, "%s+$", "")
	local cmd = string.lower(trimmed)

	if cmd == "" or cmd == "help" or cmd == "?" then
		showHelp()
		return
	end
	if cmd == "start" then
		currentIndex = 1
		showTopic(TOPIC_ORDER[1])
		return
	end
	if cmd == "next" then
		-- From not-started (0), NEXT opens lesson 1; otherwise advance.
		if currentIndex < 1 then
			currentIndex = 1
		elseif currentIndex < #TOPIC_ORDER then
			currentIndex = currentIndex + 1
		end
		showTopic(TOPIC_ORDER[currentIndex])
		return
	end
	if cmd == "prev" or cmd == "previous" then
		if currentIndex > 1 then
			currentIndex = currentIndex - 1
		else
			currentIndex = 1
		end
		showTopic(TOPIC_ORDER[currentIndex])
		return
	end
	if cmd == "skip" then
		currentIndex = #TOPIC_ORDER
		showTopic("finish")
		return
	end
	if cmd == "done" then
		local ok = setShowOnConnect(false)
		if ok then
			noteBlock("Tutorial disabled",
[[Show on connect is now off. You can still run .tutorial anytime.
Re-enable under Options → Starter Tutorial → Show on connect.]])
		else
			noteBlock("Tutorial done",
[[Could not write the option from Lua (plugin option may not exist yet).
Turn off: Options → Starter Tutorial → Show on connect = off]])
		end
		return
	end
	if cmd == "topics" or cmd == "list" then
		listTopics()
		return
	end

	-- A bare number opens that topic. The list has been printing numbers all
	-- along; until now they were decoration, because only the name worked.
	local n = tonumber(string.match(cmd, "^(%d+)$") or "")
	if n ~= nil then
		if n >= 1 and n <= #TOPIC_ORDER then
			showTopic(TOPIC_ORDER[n])
		else
			noteLine("There are " .. #TOPIC_ORDER .. " topics; " .. n .. " is not one.")
		end
		return
	end

	-- First token as topic name (allow trailing junk)
	local topic = string.match(cmd, "^([%w_]+)")
	if topic ~= nil and (TOPICS[topic] ~= nil or topic == "overview") then
		showTopic(topic)
		return
	end

	noteLine("Unknown .tutorial argument: " .. trimmed)
	showHelp()
end

function starterTutorialMaybeWelcome()
	if not readShowOnConnect() then
		return
	end
	noteBlock("Welcome to BlowTorch 2",
[[Quick starter tips are available. Open the Starter Tutorial entry
on the launcher (first row), or type .tutorial start  (or .tutorial help).
To hide this welcome: Options → Starter Tutorial → Show on connect = off
or .tutorial done]])
end

local function isOfflineTutorialSession()
	local host = connection_host
	if type(host) == "string" and string.lower(host) == "offline" then
		return true
	end
	local display = connection_display
	if type(display) == "string" and display == "Starter Tutorial" then
		return true
	end
	return false
end

function OnBackgroundStartup()
	-- Never rewrite button sets on real MUDs — only the offline Starter Tutorial pad.
	-- Lesson text is shown later via starterTutorialBegin() from doOfflineStartup
	-- (window ready). Showing here races settings load and can leave only the Java
	-- nav blurb visible while currentIndex already points at welcome.
	if isOfflineTutorialSession() then
		pcall(function()
			CallPlugin("button_window", "installStarterButtonLayout", "")
		end)
		pcall(function()
			CallPlugin("button_window", "ensureTutorialAccordion", "")
		end)
	else
		starterTutorialMaybeWelcome()
	end
end

RegisterSpecialCommand("tutorial", "tutorialCommand")



--------------------------------------------------------------------------
-- The practice yard
--------------------------------------------------------------------------
-- Not a game. A tutor NPC standing in a small yard, who explains the client
-- and then checks what the player built.
--
-- Design notes, because they are easy to undo by accident:
--   * Three tiles, no exploring. The point is the lessons, not the map.
--   * Anything the player can type is marked the same way every time, with
--     the >> marker and its own colour. A wall of prose where some of it is
--     typeable and some is not is the thing that makes tutorials useless.
--   * The tutor checks real client state through GetPlayerTriggers /
--     GetPlayerAliases / GetPlayerTimers. Saying "well done" without looking
--     would be worse than saying nothing.
--   * Lessons push buttons as much as typing: on a phone, the buttons are the
--     point of this client.
--
-- Java calls OnOfflineCommand with whatever the player typed; the text we
-- return goes back through the normal incoming path, so the player's own
-- triggers fire on it and the mapper follows.

local function green()
	return colorOf("getGreenColor", "\027[0;32m")
end

local function yellow()
	return colorOf("getBrightYellowColor", "\027[1;33m")
end

--- Everything the player may type is written through this, and only this.
local function cmd(text)
	return green() .. ">> " .. text .. white()
end

local function tutorSays(lines)
	return "\n" .. cyan() .. "Bex the tutor says:" .. white() .. "\n" .. lines .. "\n"
end

--------------------------------------------------------------------------
-- The yard
--------------------------------------------------------------------------

local yard = {
	yard = {
		num = "2001",
		title = "The Practice Yard",
		body = "Hard-packed earth inside a low wall. Bex leans on a fence post, "
			.. "watching you with the patience of someone who has taught this "
			.. "many times.",
		exits = { north = "range", east = "workshop" },
	},
	range = {
		num = "2002",
		title = "The Target Range",
		body = "Straw dummies stand in a row. This is where Bex sends you to "
			.. "make something happen on purpose.",
		exits = { south = "yard" },
	},
	workshop = {
		num = "2003",
		title = "The Workshop",
		body = "A bench of spare parts. Bex uses this room to talk about "
			.. "buttons, because there is nothing here to distract you.",
		exits = { west = "yard" },
	},
}

local here = "yard"
local dummy = nil

local DIRECTIONS = {
	n = "north", north = "north",
	s = "south", south = "south",
	e = "east", east = "east",
	w = "west", west = "west",
}

local function sortedExits(room)
	local names = {}
	for dir in pairs(room.exits) do
		names[#names + 1] = dir
	end
	table.sort(names)
	return names
end

local function describeRoom()
	local room = yard[here]
	local t = "\n" .. cyan() .. room.title .. white() .. "\n\n" .. room.body .. "\n"
	if dummy ~= nil and dummy.room == here then
		t = t .. yellow() .. "A straw dummy stands here, waiting.\n" .. white()
	end
	t = t .. "Obvious exits: " .. table.concat(sortedExits(room), ", ") .. "\n"
	if here == "yard" then
		t = t .. cmd("ask bex about lessons") .. "\n"
	end
	return t
end

--------------------------------------------------------------------------
-- Reading what the player actually built
--------------------------------------------------------------------------

--- Split a tab separated record line into fields.
local function fields(line)
	local out = {}
	for field in (line .. "\t"):gmatch("([^\t]*)\t") do
		out[#out + 1] = field
	end
	return out
end

local function eachRecord(blob, fn)
	if blob == nil or blob == "" then
		return
	end
	for line in blob:gmatch("([^\n]+)") do
		fn(fields(line))
	end
end

--- @return The player's trigger whose pattern contains `needle`, or nil.
local function findTrigger(needle)
	local found = nil
	eachRecord(GetPlayerTriggers(), function(f)
		if found == nil and f[2] ~= nil
				and f[2]:lower():find(needle:lower(), 1, true) ~= nil then
			found = { name = f[1], pattern = f[2], regex = f[3] == "true",
				enabled = f[4] == "true", responders = f[5] or "" }
		end
	end)
	return found
end

--- @return The player's alias whose pre contains `needle`, or nil.
local function findAlias(needle)
	local found = nil
	eachRecord(GetPlayerAliases(), function(f)
		if found == nil and f[1] ~= nil
				and f[1]:lower():find(needle:lower(), 1, true) ~= nil then
			found = { pre = f[1], post = f[2], enabled = f[3] == "true" }
		end
	end)
	return found
end

local function anyTimer()
	local found = nil
	eachRecord(GetPlayerTimers(), function(f)
		if found == nil then
			found = { name = f[1], seconds = tonumber(f[2]) or 0,
				repeats = f[3] == "true", playing = f[4] == "true" }
		end
	end)
	return found
end

--------------------------------------------------------------------------
-- Lessons
--------------------------------------------------------------------------
-- Each has a teach() and a check(). check() reads real client state and
-- answers in three ways: not done yet, done but wrong in a named way, or done.

local lessons = {}
local lessonOrder = { "buttons", "aliases", "triggers", "timers" }
local current = nil

lessons.buttons = {
	title = "Buttons",
	teach = function()
		return tutorSays(
			"On a phone the buttons are the whole point of this client. You are\n"
			.. "not going to type " .. cmd("kill the goblin with my sword") .. " every\n"
			.. "time. You press one thing.\n\n"
			.. "Look at the pad below the text. Try these:\n"
			.. "  " .. cmd("press a button") .. " - just tap one and watch what it sends\n"
			.. "  " .. cmd("swipe a button sideways") .. " - many carry a second command\n"
			.. "  " .. cmd("hold a button") .. " - opens its editor\n\n"
			.. "To change one, either hold it, or open the menu (the three dots\n"
			.. "at the top) and choose Edit buttons. Give it a label and a\n"
			.. "command, and save.\n\n"
			.. "When you have pressed one and made one, tell me:\n"
			.. "  " .. cmd("bex i am done"))
	end,
	check = function()
		-- Buttons live in the button plugin's Lua state, not in the player's
		-- trigger/alias/timer sets, so this one is taken on trust rather than
		-- claimed to be verified. Saying so is better than pretending.
		return true, tutorSays(
			"Good. I cannot see your button pad from here, so I am taking your\n"
			.. "word for that one - the rest I will check properly.\n\n"
			.. "Next: " .. cmd("bex next"))
	end,
}

lessons.aliases = {
	title = "Aliases",
	teach = function()
		return tutorSays(
			"An alias is a short thing you type that turns into a longer thing\n"
			.. "the game receives. Make one now. Open the alias editor from the\n"
			.. "menu and create:\n\n"
			.. "  what you type:  " .. yellow() .. "zap (.+)" .. white() .. "\n"
			.. "  what is sent:   " .. yellow() .. "kill $1" .. white() .. "\n\n"
			.. "The brackets capture a word, and $1 puts it back. Without the\n"
			.. "brackets there is nothing for $1 to hold - that catches everyone\n"
			.. "once.\n\n"
			.. "Then check me: " .. cmd("bex check"))
	end,
	check = function()
		local a = findAlias("zap")
		if a == nil then
			return false, tutorSays(
				"I do not see an alias with " .. yellow() .. "zap" .. white()
				.. " in it yet. Take your time.")
		end
		if a.pre:find("%(") == nil then
			return false, tutorSays(
				"Found it: " .. yellow() .. a.pre .. white() .. "\n"
				.. "But there are no brackets, so nothing is captured and $1 will\n"
				.. "stay as the literal text $1. Try " .. yellow() .. "zap (.+)" .. white() .. ".")
		end
		if a.post:find("%$1") == nil then
			return false, tutorSays(
				"The pattern is right, but what it sends - " .. yellow() .. a.post
				.. white() .. " - never uses $1, so the captured word is thrown away.")
		end
		if not a.enabled then
			return false, tutorSays("It is there and correct, but switched off.")
		end
		return true, tutorSays(
			"That is right: " .. yellow() .. a.pre .. white() .. " sends "
			.. yellow() .. a.post .. white() .. ".\n"
			.. "Try it on the dummy at the range if you like.\n\n"
			.. "Next: " .. cmd("bex next"))
	end,
}

lessons.triggers = {
	title = "Triggers",
	teach = function()
		return tutorSays(
			"A trigger watches what the game sends you and acts on it. This is\n"
			.. "the one people find hardest, so we will do it on something real.\n\n"
			.. "Make a trigger whose pattern is:\n\n"
			.. "  " .. yellow() .. "The dummy topples over." .. white() .. "\n\n"
			.. "Give it something to do - a note, a sound, a command, anything.\n"
			.. "A pattern with nothing attached matches and then does nothing,\n"
			.. "which looks exactly like a broken trigger.\n\n"
			.. "Then: " .. cmd("bex check"))
	end,
	check = function()
		local t = findTrigger("dummy topples")
		if t == nil then
			return false, tutorSays(
				"No trigger of mine yet. The pattern to catch is\n"
				.. yellow() .. "The dummy topples over." .. white())
		end
		if t.responders == "" then
			return false, tutorSays(
				"The pattern is right, but nothing is attached to it, so it will\n"
				.. "match and do nothing at all. Open it and add a response.")
		end
		if not t.enabled then
			return false, tutorSays("Right shape, but it is switched off.")
		end
		return true, tutorSays(
			"Good - and it does something, which is the half people forget.\n\n"
			.. "Now watch it work. Go north and knock a dummy over:\n"
			.. "  " .. cmd("north") .. " then " .. cmd("hit dummy") .. "\n\n"
			.. "Next lesson: " .. cmd("bex next"))
	end,
}

lessons.timers = {
	title = "Timers",
	teach = function()
		return tutorSays(
			"A timer does something on its own, every so many seconds. Useful\n"
			.. "for the thing you always forget.\n\n"
			.. "Make any timer you like from the menu - a few seconds, repeating,\n"
			.. "and set it running.\n\n"
			.. "Then: " .. cmd("bex check"))
	end,
	check = function()
		local t = anyTimer()
		if t == nil then
			return false, tutorSays("No timers yet.")
		end
		if not t.playing then
			return false, tutorSays(
				"You made " .. yellow() .. t.name .. white()
				.. ", but it is not running. A timer that is not started never fires.")
		end
		return true, tutorSays(
			"There it is: " .. yellow() .. t.name .. white() .. ", every "
			.. t.seconds .. "s" .. (t.repeats and ", repeating" or ", once") .. ".\n\n"
			.. "That is everything I teach. The reading tour has the rest:\n"
			.. "  " .. cmd(".tutorial topics"))
	end,
}

local function lessonIndex(name)
	for i, n in ipairs(lessonOrder) do
		if n == name then
			return i
		end
	end
	return 0
end

local function startLesson(name)
	current = name
	return lessons[name].teach()
end

local function nextLesson()
	local i = lessonIndex(current)
	if i == 0 then
		return startLesson(lessonOrder[1])
	end
	if i >= #lessonOrder then
		return tutorSays("That was the last one. " .. cmd(".tutorial topics"))
	end
	return startLesson(lessonOrder[i + 1])
end

local function lessonMenu()
	local t = tutorSays(
		"I can walk you through four things. Say the word, or just take them\n"
		.. "in order.\n")
	for i, name in ipairs(lessonOrder) do
		t = t .. "  " .. i .. ". " .. cmd("bex " .. name)
			.. "  - " .. lessons[name].title .. "\n"
	end
	return t .. "\n" .. cmd("bex next") .. " walks them in order.\n"
		.. cmd("bex check") .. " has me look at what you built.\n"
end

--------------------------------------------------------------------------
-- Commands
--------------------------------------------------------------------------

local YARD_HELP =
	"In here you can:\n"
	.. "  look                     see where you are\n"
	.. "  north south east west    move between the three rooms\n"
	.. "  ask bex about lessons    the lesson list\n"
	.. "  bex next / bex check     walk lessons, have your work checked\n"
	.. "  summon dummy             a target to practise on\n"
	.. "  hit dummy                knock it over\n"
	.. "  commands                 this list\n"
	.. "\nThe reading tutorial is still here: .tutorial\n"

function OnOfflineCommand(line)
	if line == nil then
		return nil
	end
	local c = line:lower():gsub("^%s+", ""):gsub("%s+$", "")
	if c == "" then
		return nil
	end

	if c == "commands" then
		return "\n" .. cyan() .. YARD_HELP .. white()
	end

	if c == "look" or c == "l" then
		return describeRoom()
	end

	local dir = DIRECTIONS[c]
	if dir ~= nil then
		local dest = yard[here].exits[dir]
		if dest == nil then
			return "\n" .. white() .. "The wall is that way. Try "
				.. table.concat(sortedExits(yard[here]), " or ") .. ".\n"
		end
		here = dest
		return describeRoom()
	end

	if c == "ask bex about lessons" or c == "ask bex" or c == "lessons" then
		return lessonMenu()
	end

	if c == "bex next" then
		return nextLesson()
	end

	if c == "bex check" or c == "bex i am done" then
		if current == nil then
			return tutorSays("Pick a lesson first: " .. cmd("ask bex about lessons"))
		end
		local ok, text = lessons[current].check()
		return text
	end

	local which = c:match("^bex%s+(%a+)$")
	if which ~= nil and lessons[which] ~= nil then
		return startLesson(which)
	end

	if c == "summon dummy" or c == "summon" then
		dummy = { room = here, standing = true }
		return "\n" .. yellow() .. "Bex drags a straw dummy over and sets it upright.\n"
			.. white() .. cmd("hit dummy") .. "\n"
	end

	if c == "hit dummy" or c == "kill dummy" or c == "attack dummy" then
		if dummy == nil or dummy.room ~= here then
			return "\n" .. white() .. "There is no dummy here. "
				.. cmd("summon dummy") .. "\n"
		end
		if not dummy.standing then
			dummy.standing = true
			return "\n" .. white() .. "You set the dummy upright again.\n"
		end
		dummy.standing = false
		-- Fixed wording: the trigger lesson asks for exactly this line.
		return "\n" .. white() .. "You strike the dummy.\n"
			.. "The dummy topples over.\n"
	end

	return nil
end

--- Room facts for the mapper, in the shape Java expects: num, title, exits.
function OnOfflineRoomInfo()
	local room = yard[here]
	if room == nil then
		return nil
	end
	return room.num .. "\t" .. room.title .. "\t"
		.. table.concat(sortedExits(room), ",")
end
