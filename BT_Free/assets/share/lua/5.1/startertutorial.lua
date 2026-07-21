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

local function cyan()
	if Colorizer ~= nil then
		return Colorizer:getBrightCyanColor()
	end
	return "\027[1;36m"
end

local function white()
	if Colorizer ~= nil then
		return Colorizer:getWhiteColor()
	end
	return "\027[0;37m"
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
	"client_commands",
	"buttons_basics",
	"buttons_swipe",
	"buttons_hold",
	"buttons_accordion",
	"buttons_sets",
	"buttons_make",
	"buttons_edit",
	"movement",
	"aliases",
	"triggers",
	"timers",
	"coloring",
	"keyboard",
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

TOPICS.welcome = function()
	noteBlock("Starter Tutorial — Welcome",
[[BlowTorch 2 talks to a MUD over the network, but many helpers stay on the
phone. Anything you type starting with a single dot (.) is a client command
and is not sent to the game unless you escape it with ..

Try it now:
  • Tap NEXT / PREV / TOPICS on the pad (or type .tutorial next)
  • Tap HELP anytime to restart (.tutorial start)
  • Tap LOAD to try .loadset tutorial (hold LOAD or flip → .loadset default)

This tour is hands-on: you will build a .loadset button, learn triggers for
beginners, and poke swipe / hold / accordion demos. Lessons also cover
aliases, timers, colors, keyboard, search, mapper, wrap, logging, ⋮ menu,
GMCP/MCP, reconnect, copy, Options, display, and plugins.

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
[[Each button can define swipeUp / swipeDown / swipeLeft / swipeRight
commands. Drag roughly a finger-width off the tile in that direction.

Try now: open the SWIPE tile and flick it ↑ ↓ ← → — each direction has a
different tip. Compass tiles (N/E/S/W/U/D) also have one demo swipe each.

Swipe overrides the older Flip action when a swipe command is set. In Edit
mode, open a button and fill the Swipe fields. Optional gesture-hint arrows
draw on the tile (Options → Button → Show gesture hints).]])
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

An accordion parent expands up to five child buttons (label + command)
in a chosen direction. Trigger can be tap, hold, or swipe.

Editor badges: T = tap, H = hold, S = swipe. Children can auto-close after
use. Accordion data is stored with the button set (Lua); use Edit buttons
to build your own.

Handy when one corner of the screen must hold several macros.]])
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

Aliases are for shortcuts you type. Triggers (next) react to game text.]])
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
in regex mode become $1, $2 in Ack / Replace / Toast text.

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
[[Open Timers from the action bar / overflow. Each timer has a name,
interval (seconds), optional repeat, and responders (same kinds as
triggers: Ack, Toast, Notification, …).

Conditions (in the timer editor):
  • Extra gate when the timer fires — same AND/OR types as triggers.
    Empty = always fire responders. Set Variable / session vars still apply.

Control from the input bar:
  .timer play <name>
  .timer pause <name>
  .timer reset <name>
  .timer stop <name>
  .timer info <name>

Optional third token suppresses toasts (not used with info), e.g.
  .timer play mytick silent

Name matches the timer list (not a numeric index). Useful for ticks,
cooldowns, or reminder toasts while you play.]])
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

Import / export this session’s settings:
  Overflow → Export Settings / Import Settings
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
  Reload / Reset Settings
  Export / Import Settings
  Crash report            Show log / Share log
  About
  Help                    this app’s user manual

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
  Miscellaneous   storage / import paths

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

To remove the tutorial entirely, delete starter_tutorial under Plugins
(you can Load it again later). Only load plugins you trust.]])
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
buttons_hold, buttons_accordion, buttons_sets, buttons_make,
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
	lines[#lines + 1] = "Open with: .tutorial <name>"
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
