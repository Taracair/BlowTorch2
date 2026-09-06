# BlowTorch 2 — Plugin authoring guide

**Audience:** developers who want to write Lua plugins for BlowTorch 2.  
**As of:** 5 September 2026 (v2.4.3 / settings `xmlversion="2"`).

This is the developer-facing reference for what plugins can do, what they
cannot, hard limits, security, packaging, and the Lua API. Player-facing
commands live in [`user-manual.md`](user-manual.md). Architecture and process
model live in [`architecture.md`](architecture.md) and
[`ORCHESTRATION.md`](ORCHESTRATION.md).

---

## 1. What a plugin is

A BlowTorch plugin is:

1. An XML descriptor (`<blowtorch xmlversion="2">` → `<plugins>` → `<plugin>`),
   and optionally
2. Sibling Lua modules / assets next to that XML.

Each plugin gets its **own Lua 5.1 VM** (LuaJIT) in the **service** process
(`:stellar`). If the plugin declares a `<window script="…">`, that window gets
a **second** Lua VM in the **UI** process, with a different API (drawing,
menus, `PluginXCallS`).

Plugins can own triggers, aliases, timers, scripts, options UI, and windows.
They can talk to the MUD (send text, GMCP, MCP), other plugins, and their own
UI windows.

Built-in plugins shipped with the Free build:

| Name | Role |
|------|------|
| `button_window` | On-screen button pad (cannot be deleted or disabled) |
| `starter_tutorial` | Interactive `.tutorial` guide and optional in-play command tips (cannot be deleted) |
| `connection_settings` | Host settings root (not a player plugin) |

Their Lua lives under `BT_Free/assets/share/lua/5.1/` and is synced into app
data at startup. Their XML is embedded in
`BT_Free/config/default_settings_*.xml`.

---

## 2. Trust model and limits

### 2.1 Security — not a sandbox

**Plugins are trusted code with the app’s privileges.** There is no Lua
sandbox, no capability allowlist, and no SecurityManager.

From [`FDROID_README.md`](FDROID_README.md):

> A Lua plugin runs with the app’s privileges … full Lua libs plus Android
> helpers. Only load plugins you trust. The plugin folder is not a sandbox.

| Capability | Allowed? |
|------------|----------|
| Full Lua stdlib (`io`, `os`, `package`, `debug`, …) | Yes (`openLibs()`) |
| Arbitrary Java via LuaJava (`luajava.bindClass`, …) | Yes — no allowlist |
| Android `Context` global | Yes (`context`) |
| Filesystem (Lua `io` / Java `File`) | Yes (Android storage perms apply) |
| Network sockets / HTTP | Yes (app has `INTERNET`) |
| Talk to the MUD | Yes (`SendToServer`, GMCP, MCP) |
| Other apps’ private data | No (Android UID isolation), unless the user grants broader storage |
| Escape a Lua CPU/time quota | N/A — **there is no timeout** |

Treat every installed plugin as full app code in `:stellar`.

### 2.2 Hard limits (enforced)

| Limit | Value |
|-------|-------|
| Extra text windows (`CreateTextWindow`) | **8** slots; names `[a-z0-9_]+`, length 1–24 |
| Reserved window names | `main`, `mainDisplay`, `button_window` |
| Alias expansion recursion | **20** passes |
| Undeletable plugins | `button_window`, `starter_tutorial`, `connection_settings` |
| Disable `button_window` | Forbidden |

### 2.3 Soft / practical constraints

- **No count cap** on plugins, triggers, aliases, timers, or script size.
- **No zip packaging** for plugins (zip is only for settings backups). Install
  UI accepts **`.xml` only**, under BlowTorch storage.
- External plugins need **mounted shared storage**; otherwise links fail as
  “storage not mounted”.
- Session variables (`SetVariable` / `GetVariable`) are **session-only** — not
  persisted across reconnects.
- `NewWindow(...)` is legacy; size arguments are largely ignored. Prefer XML
  `<window>` layouts or `CreateTextWindow` for extra panes.
- Only **window** (UI) Lua can add global menu items (`PopulateMenu`).
- **No `NewAlias` / `NewTimer` in Lua** — declare those in XML or edit them in
  the UI. Lua can `EnableAlias` and fully create/delete triggers.
- **No plugin `Send_MSDP`** — MSDP is Options / `.msdp` only.
- ForgeMap (`forgemap`) is skipped at load in this build.

### 2.4 Process and threading (read this)

| | UI process | Service (`:stellar`) |
|--|------------|----------------------|
| Owns | Windows, overlays, **window Lua** | Connection, **plugin Lua** |

Binder is **asymmetric**:

- **UI → service is synchronous.** `PluginXCallS` runs plugin Lua on the
  **calling UI thread**. Slow Lua freezes the UI.
- **Service → UI is one-way and cannot return anything.** `WindowXCallS` /
  `WindowXCallB` go over `IWindowCallback`, which is a `oneway` AIDL interface,
  so they hand the data over and return immediately. There is no result to read
  and no way to add one — a callback method with a return value or an
  `out`/`inout` parameter will not compile. If your window has to answer a
  question, have it call **back** into the plugin with `PluginXCallS`, which is
  the direction that may block.
- That `oneway` is a barrier, not a style choice: Android freezes a backgrounded
  UI process after a couple of minutes, and a *synchronous* transaction into a
  frozen process kills it (`am_kill … Sync transaction while frozen`).
- `SaveSettings` posts on the **same** service handler queue as `WindowXCallB` —
  a heavy save can delay button / window IPC behind it.

Triggers run on the connection thread; plugin timers on a per-plugin timer
thread. Keep shared mutable state local to one thread.

---

## 3. How to build and install a plugin

### 3.1 Minimal hello-world

Create `/storage/emulated/0/BlowTorch/plugins/hello.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<blowtorch xmlversion="2">
  <plugins>
    <plugin name="hello" id="90001">
      <author>You</author>
      <description>Minimal plugin example.</description>
      <options title="Hello" summary="Demo options">
        <boolean title="Greet on startup" key="greet"
                 summary="Print a note when the session starts">true</boolean>
      </options>
      <script name="bootstrap" execute="true"><![CDATA[
local greet = true

function OnOptionChanged(key, value)
  -- Option values from XML arrive as strings.
  if key == "greet" then
    greet = (value == "true" or value == true)
  end
end

function OnBackgroundStartup()
  if greet then
    Note("hello plugin loaded\n")
  end
end

function sayHi(arg)
  Note("hi from hello: " .. tostring(arg) .. "\n")
end

RegisterSpecialCommand("hello", "sayHi")
      ]]></script>
    </plugin>
  </plugins>
</blowtorch>
```

In a live session: **Plugins → Load** → pick the file → **Install**.  
Type `.hello` in the input bar.

The main settings file gains a link (not a copy of the plugin body):

```xml
<link file="plugins/hello.xml"/>
```

### 3.2 Directory layout

| Form | Supported? |
|------|------------|
| Single `.xml` with inline `<script>` CDATA | Yes — simplest |
| Directory: `.xml` + sibling `.lua` (+ assets) | Yes — load modules with `dofile` (see below) |
| `.zip` plugin package | **No** |
| Multiple `<plugin>` nodes in one XML | Yes (“plugin group”) |

Preferred on-device layout:

```text
/storage/emulated/0/BlowTorch/plugins/
  hello.xml
  hellomodule.lua          # optional
```

Install / browse fallbacks if classic storage is unavailable:

- `{getExternalFilesDir()}/plugins`
- `{getFilesDir()}/plugins`

Link resolution order for a stored relative path `plugins/foo.xml`:

1. `/sdcard/BlowTorch/plugins/foo.xml`
2. `{externalFilesDir}/plugins/foo.xml`
3. `{externalFilesDir}/BlowTorch/plugins/foo.xml`

### 3.3 Loading sibling Lua modules (`require` caveat)

At construction, `package.path` includes the plugin directory. **During
bootstrap the path is rewritten to the shared Lua tree only**
(`{dataDir}/lua/share/5.1/?.lua`). The append of the plugin directory is
commented out in the parser.

So **do not rely on `require("hellomodule")` for files next to your XML.**
Prefer:

```lua
local dir = GetPluginInstallDirectory()  -- parent of the plugin XML
dofile(dir .. "/hellomodule.lua")
```

Or extend `package.path` yourself after bootstrap. Built-in modules such as
`serialize`, `copytable`, `buttonserver` live in the shared path and
`require` fine.

`GetPluginInstallDirectory()` uses the external plugin’s XML path; it is not
meaningful for internal/bundled plugins (`fullPath` is null).

### 3.4 Bundled vs user-installed

| | Bundled | User-installed |
|--|---------|----------------|
| Source | Inside default/main settings XML | External XML + `<link file="…"/>` |
| Location | `INTERNAL` | `EXTERNAL` |
| Delete | Refused | Removes link + reloads |
| Disable | `starter_tutorial` yes; `button_window` no | Yes |

Disable keeps the plugin loaded but skips triggers/aliases/callbacks that
check `isEnabled()`.

### 3.5 Testing tips

1. Iterate external plugins **without** rebuilding the APK.
2. Lua errors show as **red text** in the game window; the connection stays up.
3. Check `/sdcard/BlowTorch/logs/blowtorch2.log` and logcat.
4. Gradle does **not** syntax-check Lua. For shipped modules:
   `luac5.1 -p BT_Free/assets/share/lua/5.1/*.lua`
5. Option and XML values are **strings** in Lua (`1 ~= "1"`).
6. Offline **Starter Tutorial** launcher row is useful for client-only tests.
7. Failed links appear as missing rows — restore the file or delete the link.

---

## 4. Plugin XML reference

Root must be modern format (`xmlversion="2"`). Legacy root `<root>` is probed
for migration; new plugins must use `<blowtorch>`.

```xml
<blowtorch xmlversion="2">
  <plugins>
    <plugin name="…" id="…" enabled="true">
      <author>…</author>
      <description>…</description>
      <version>1.0</version>   <!-- human metadata; not read by PluginParser -->
      <windows>…</windows>
      <aliases>…</aliases>
      <triggers>…</triggers>
      <timers>…</timers>
      <options title="…" summary="…">…</options>
      <script name="bootstrap" execute="true"><![CDATA[ … ]]></script>
    </plugin>
  </plugins>
</blowtorch>
```

### 4.1 `<plugin>` attributes

| Attribute | Required | Meaning |
|-----------|----------|---------|
| `name` | yes | Identity used everywhere (commands, CallPlugin, UI) |
| `id` | yes | Integer (`Integer.parseInt` — missing/non-numeric fails parse) |
| `enabled` | no | `"false"` loads but disables; default enabled |

INTERNAL vs EXTERNAL is **not** an XML attribute; it depends on how the file
was loaded (embedded vs link).

### 4.2 `<script>`

There is **no** wrapping `<scripts>` node. Each `<script>` is a direct child
of `<plugin>`.

| Attribute | Meaning |
|-----------|---------|
| `name` | Script id; window `script="…"` and `ExecuteScript` use this |
| `execute` | `"true"` → run at load. Name `"bootstrap"` also forces execute |

### 4.3 Triggers, aliases, timers

**Alias** attrs: `pre`, `post`, `enabled`, optional `localEcho` (`on` /
`off`; omit = follow the connection Local Echo? setting). An `<alias>` may
nest `<setVariable>` the same way a trigger does (`name`, `value`, optional
`mode` / `persist`). That runs when the player types a matching line, in
addition to `post`. Live values of kept names are **not** in this XML.

**Trigger** attrs:

| Attr | Meaning |
|------|---------|
| `title` | Name (not `name`) |
| `pattern` | Match text or regex |
| `regexp` / `interpretLiteral` | Regex mode (`regexp="true"` preferred on save) |
| `fireOnce` (`true` / `"send"`), `hidden`, `enabled` | Behaviour flags |
| `sequence` | Order (default 10) |
| `group` | Group name |
| `keepEvaluating` | Continue after match. Default true (omit the attribute). Write `keepEvaluating="false"` to stop later triggers on that line. |

Nested responders: `<ack>`, `<toast>`, `<notification>`, `<script function="…">`,
`<replace>`, `<color>`, `<gag>`, `<setVariable>`. Common attr: `fireWhen`
(window open / closed / both / never). `<setVariable>` also takes `name`,
`value`, optional `mode` (`set` / `add` / `subtract` / `append` / `unset`;
omit when set) and `persist="true"` (Keep after restart; omit when false). Live
values of kept names are **not** in this XML.

Conditions:

```xml
<conditions op="and|or">
  <condition type="triggerEnabled|triggerDisabled|aliasEnabled|aliasDisabled|aliasEquals|variableEquals|variableExists|variableBelow|variableAbove"
             name="..." plugin="optional" value="for aliasEquals/variableEquals/variableBelow/variableAbove"/>
</conditions>
```

**Timer** attrs: `name`, `seconds`, `repeat`, `playing`, `ordinal`, `group`.
Timers can nest the same responder/condition machinery as triggers.

**GMCP / MCP triggers:** literal patterns starting with `%Module.Name` (GMCP)
or `@message-name` (MCP) route into the plugin’s GMCP/MCP callback path and
typically call a Lua function via a `<script>` responder.

### 4.4 Windows

```xml
<windows>
  <window name="my_win" id="91001" script="ui_script">
    <layoutGroup target="normal">
      <layout orientation="landscape" width="fill_parent" height="200"
              above="40"/>
      <layout orientation="portrait" width="fill_parent" height="200"
              above="40"/>
    </layoutGroup>
    <options>
      <option key="font_size">14</option>
      <option key="line_extra">2</option>
    </options>
  </window>
</windows>
```

| Attr | Meaning |
|------|---------|
| `name` | Window id |
| `id` | Unique integer |
| `script` | Named `<script>` loaded into **window** Lua |

Layout attrs include `width` / `height` (`N`, `fill_parent`, `wrap_content`),
`above` / `below` / `leftOf` / `rightOf`, `alignParent*`, margins.
`layoutGroup target` is a size bucket: `normal`, `large`, `xlarge`.

Useful window option keys: `font_size`, `line_extra`, `font_path`,
`buffer_size`, `word_wrap`, `hyperlinks_enabled`, `hyperlink_color`,
`hyperlink_mode`, `hyperlink_bare_domains`, `hyperlink_extra_tlds`,
`color_option`.

### 4.5 Options UI

Container attrs: `title`, `summary`. Children:

| Tag | Notes |
|-----|-------|
| `<boolean key="…" title="…" summary="…">true</boolean>` | |
| `<string>` / `<integer>` / `<encoding>` | Text body is the value |
| `<color>` | `#AARRGGBB` (alpha, red, green, blue) |
| `<callback>` | Body = Lua function name invoked when tapped |
| `<list>` | Children `<value>` (selected index), `<item>` (labels) |
| `<file>` | Children `<value>`, `<path>`, `<extension>` |

Options appear under **Options → \<title\>**. Changes call Lua
**`OnOptionChanged(key, value)`** — older Doxygen text says
`OnOptionsChanged`; that name is **wrong**. Values arrive as **strings**.

**A saved profile keeps the option shape it was created with.** The `<options>`
block in your XML seeds a profile once; after that the player's settings file is
the record. Editing the XML — adding an option, or changing a `<string>` into a
`<list>` — therefore only ever reaches **new** profiles. Everyone already using
your plugin keeps the old one.

To change an option's *type* in place, drop the old key and add the new one from
Lua, through the `SettingsGroup` that `GetPluginSettings()` returns:

```lua
local settings = GetPluginSettings()
if not settings:isListOption("my_key") then          -- still the old StringOption
  -- Carry the value across, or the player is snapped back to the default.
  local previous = settings:getOptionValue("my_key")
  settings:removeOptionByKey("my_key")               -- no-op if it was never there
  local ListOption = luajava.bindClass(
    "com.resurrection.blowtorch2.lib.service.plugin.settings.ListOption")
  local opt = luajava.new(ListOption)
  opt:setKey("my_key"); opt:setTitle("My key")
  opt:addItem("First"); opt:addItem("Second")
  opt:setValue(indexFor(previous) or 0)
  settings:addOption(opt)
end
```

Two rules on that pair, both learned the hard way:

- **`isListOption(key)` exists because Lua has no `instanceof`.** LuaJava
  exposes `bindClass` / `new` / `newInstance` / `array` / `loadLib` /
  `createProxy` and nothing else, so without it a plugin cannot tell an
  already-migrated option from the one it replaced — and would drop and rebuild
  it, losing the player's choice, on every single connect.
- **`removeOptionByKey(key)` is only safe outside a walk of `getOptions()`.**
  Run the migration from a ready/startup hook, never from inside
  `OnOptionChanged` — mutating the option tree while the host is dumping it is a
  crash on connect. (`Plugin.dumpOption` snapshots the list for exactly this
  reason.) `button_window` does this in its `buttonLayerReady` path; copy that
  shape.

### 4.6 Custom XML (`OnPrepareXML` / `OnXmlExport`)

For data the stock parser does not know (e.g. `button_window`’s
`<buttonsets>`), define `OnPrepareXML(rootElement)` to attach SAX listeners,
then rewrite custom nodes in `OnXmlExport(XmlSerializer)` on save.

---

## 5. Lifecycle and host callbacks

```text
Parse XML → new Plugin + initLua (register API)
         → run bootstrap / execute="true" scripts
         → optional OnPrepareXML + second parse pass
loadPlugins → init timers, build triggers/aliases
            → push all options via OnOptionChanged
Connection ready → OnBackgroundStartup()   (before TCP connect)
UI attaches windows → OnCreate / OnDraw / …
Option change → OnOptionChanged(key, value)
Save → OnXmlExport(out)
Shutdown → window OnDestroy; plugin Lua state dropped
```

### 5.1 Service (plugin) callbacks

| Callback | When |
|----------|------|
| Bootstrap / `execute="true"` | During load |
| `OnPrepareXML(root)` | After bootstrap, if defined |
| `OnOptionChanged(key, value)` | Load dump + each Options change |
| `OnBackgroundStartup()` | All plugins loaded, before connect |
| `OnXmlExport(serializer)` | Settings save |
| Trigger `<script function="fn">` | Match → global `fn` |
| `RegisterSpecialCommand(cmd, fn)` | Player types `.cmd …` |

Java also calls a **global** `OnCommandTip(commandName)` on `starter_tutorial`
after any other `.command` runs. That is how in-play reminders work; a third-party
plugin does not get that callback unless it is named `starter_tutorial`.

### 5.2 Window callbacks

| Callback | When |
|----------|------|
| `OnCreate` | Window created |
| `OnMeasure` / `OnSizeChanged` | Layout |
| `OnDraw(canvas)` | Each frame (when not scrolling) — `android.graphics.Canvas` |
| `PopulateMenu(menu)` | Overflow / action menu |
| `OnDestroy` | Teardown |

Drawing belongs in **window** Lua only. Service-side `DrawWindow` is disabled.

---

## 6. Lua API — service (plugin) VM

Registered in `Plugin.initLua`. Inline Doxygen in
`BTLib/.../service/plugin/Plugin*LuaFunctions.java` is the historical API
comment set; this section is the maintained summary.

### 6.1 Globals

| Global | Meaning |
|--------|---------|
| `triggers` | Java `HashMap` of **this plugin’s** triggers |
| `context` | Android `Context` (service process) |
| `connection_display` | Connection display name |
| `connection_host` | Hostname |

### 6.2 Host / environment

| Function | Description |
|----------|-------------|
| `Note(text)` | Client-only note to the main window |
| `SendToServer(str)` | Send as if typed (aliases / special commands apply) |
| `GetPluginID()` | Numeric `id` from XML |
| `GetPluginName()` | Plugin name |
| `GetPluginInstallDirectory()` | Parent dir of external plugin XML |
| `GetExternalStorageDirectory()` | External storage root |
| `GetDisplayDensity()` | Display density |
| `GetStatusBarHeight()` / `GetActionBarHeight()` | UI metrics |
| `UserPresent()` | Whether the foreground window is showing |

### 6.3 Windows and extra text

| Function | Description |
|----------|-------------|
| `NewWindow(name, x, y, w, h, script)` | Legacy token create (sizes largely ignored) |
| `GetWindowTokenByName(name)` | Raw `WindowToken` Java object |
| `AppendLineToWindow(name, line)` | Append `TextTree.Line` or string |
| `AppendWindowSettings(name)` | Nest window options into plugin Options UI |
| `InvalidateWindowText(name)` | Force redraw |
| `WindowBuffer(name, state)` | Buffer into named window |
| `WindowXCallS(token, function, data)` | Call window Lua with a string |
| `WindowXCallB(token, function, bytes)` | Same, binary-safe |
| `CreateTextWindow(name [, title])` | Extra-text slot (max 8) |
| `DestroyTextWindow(name)` | Remove slot |
| `ListTextWindows()` | 1-based array of slot names |
| `ShowTextWindow(name, visible)` | Show / hide overlay |
| `ClearTextWindow(name)` | Clear buffer (`main` → `mainDisplay`) |
| `NoteToWindow(name, text)` | Colored client-only note into a slot |
| `WindowExists(name)` | Slot or token exists |

### 6.4 Settings, scripts, commands

| Function | Description |
|----------|-------------|
| `ExecuteScript(name)` | Run a named `<script>` body |
| `GetPluginSettings()` | Plugin `SettingsGroup` Java object. Beyond `getOptionValue` / `addOption` it carries **`isListOption(key)`** and **`removeOptionByKey(key)`** — the pair you need to change an option's *type* in a profile that already has it. Read §4.5 before using them: one is only safe outside `OnOptionChanged` |
| `ReloadSettings()` | Full settings reload |
| `SaveSettings()` | Persist connection settings (queued) |
| `RegisterSpecialCommand(shortName, callbackName)` | `.shortName` → global Lua fn |

### 6.5 Triggers, aliases, variables

| Function | Description |
|----------|-------------|
| `NewTrigger(name, pattern, config, action…)` | Create trigger + responder tables |
| `DeleteTrigger(name)` / `DeleteTriggerGroup(name)` | Remove |
| `EnableTrigger(name [, state])` | Get/set enabled |
| `EnableTriggerGroup(name, state)` | Group enable |
| `EnableAlias(name [, state])` | Get/set alias enabled + rebuild matcher |
| `SetVariable(name, value)` | Session var for `${name}` |
| `GetVariable(name)` | Read (nil if unset) |
| `UnsetVariable(name)` | Clear |

**`NewTrigger` config table** (common keys): `regex`, `group`, `once`
(`true` = until enabled again; `"send"` = until you type a command; also
accepted as `fireOnce`), `enabled`, `sequence` (number; smaller runs first, default 10),
`style` (nested table — match incoming SGR, not a Color action):

```lua
NewTrigger("red_says", "says", {
  regex = false,
  style = {
    fg = 196,          -- number is xterm 256; or "ansi:32" / "xterm:208" / "rgb:#ff8700"
    bright = true,     -- SGR 1 (require). false = forbid. omit = ignore
    italic = true,
  },
}, { type = "gag" })

NewTrigger("any_red", "", { style = { fg = 196 } },
  { type = "color", foreground = "#ff8800", background = false })

NewTrigger("any_bright", "", { style = { bright = true }, once = "send" },
  { type = "send", text = "look $1" })
```

A blank pattern has no regex group; `$0` and `$1` are both the styled run,
so `send` `$1` is the bright phrase. Do not put match keys on `{ type = "color", ... }` — `bold` there is Color Bold
(private SGR 66), not MUD `[1m`. Prefixed `style_fg` / `style_bright` keys still
work if you prefer a flat config table.

**Action tables** (`type` required):

| `type` | Useful fields |
|--------|----------------|
| `notification` | `title`, `message`, `soundpath`, `vibrate`, `spawnNew` |
| `toast` | `message`, `duration` (0 short / 1 long) |
| `send` | `text` |
| `gag` | `output`, `log`, `retarget` |
| `replace` | `text`, `retarget` |
| `color` | `foreground`, `background`: xterm number, `#RRGGBB`, `false`/`"keep"`, `"default"` (background RESET), `{ xterm = n }`, `{ rgb = "#rrggbb" }` or `{ r=, g=, b= }`. Absent keys keep defaults (fg 256, bg 232). Styles: `bold` (heavier glyphs — same overlay as the Color Bold checkbox, not SGR 1 / bright), `faint`, `italic`, `underline`, `reverse`, `strike`. `backgroundMode = "xterm"` paints a numeric background including 0/16/231. |
| `script` | Lua function name (via XML responder; see also script responder in XML) |

Example:

```lua
NewTrigger("fox_color", "fox", { regex = false },
  { type = "color", foreground = "#ff8800", background = false, bold = true },
  { type = "send", text = "listen fox" })
```

The matched word is orange and heavier; the MUD background stays. `bold = true` is that Color Bold checkbox, not MUD `[1m`. Old xterm still works: `{ type = "color", foreground = 36, background = 75 }`.

### 6.6 Inspect (player main sets — read-only)

| Function | Returns (TSV, one record per line) |
|----------|-------------------------------------|
| `GetPlayerTriggers()` | `name\tpattern\tregex\tenabled\tResponderClass,…` |
| `GetPlayerAliases()` | `pre\tpost\tenabled\tlocalEcho` (`inherit`/`on`/`off`) |
| `GetPlayerTimers()` | `name\tseconds\trepeat\tplaying` |

These cannot mutate the player’s work (by design, for the tutorial).

### 6.7 Interop and protocols

| Function | Description |
|----------|-------------|
| `CallPlugin(plugin, function, data)` | Call another plugin’s global (one string arg) |
| `PluginSupports(plugin, function)` | True if that global exists |
| `Simulate(data)` | Inject bytes as server input (triggers fire) |
| `Send_GMCP_Packet(str)` | Outbound GMCP |
| `Send_MCP_Packet(str)` | Outbound MCP |
| `Get_MCP_Status()` | Table of MCP status cache |

---

## 7. Lua API — window (UI) VM

Registered on `Window` for scripts attached via `<window script="…">`.

| Function | Description |
|----------|-------------|
| `Note` | Echo to main |
| `SendToServer` | Send text |
| `PluginXCallS(function, data)` | UI → owning plugin (**sync — keep fast**) |
| `WindowCall(name, fn, arg)` | Call another window’s Lua |
| `WindowSupports(name, fn)` | Probe |
| `WindowBroadcast` | Broadcast to windows |
| `AddOptionCallback(fn, title, icon)` | Overflow menu item |
| `PushMenuStack` / `PopMenuStack` | Nested menu levels for that item |
| `ScheduleCallback` / `CancelCallback` | Timed UI callbacks |
| `Invalidate` | Ask for a redraw (`OnDraw` next frame) |
| `GetBounds` | The view’s current bounds |
| `GetActivity` | Host activity Java object |
| `PluginInstalled(name)` | Plugin loaded? |
| `GetOptionValue` | Read option |
| `CloseOptionsDialog` | Dismiss options |
| Metrics / paths | `GetDisplayDensity`, `GetStatusBarHeight`, `GetActionBarHeight`, `IsStatusBarHidden`, `GetExternalStorageDirectory`, `GetPluginInstallDirectory` |

Global: `view` = the Android `Window` view.

**There is no `PluginXCallB`.** A Doxygen note in `Window.java` still recommends
it as the faster binary route; it was never registered in the window VM. Use
`PluginXCallS` with a serialised string.

---

## 8. Inter-process communication diagram

```text
┌─────────────────────┐   CallPlugin / PluginSupports   ┌─────────────────────┐
│ Plugin A Lua        │ ──────────────────────────────► │ Plugin B Lua        │
│ (service)           │                                 │ (service)           │
└──────────▲──────────┘                                 └─────────────────────┘
           │ PluginXCallS
           │ (UI → service, SYNCHRONOUS on UI thread, returns a value)
┌──────────┴──────────┐
│ Window Lua          │
│ (UI process)        │
└─────────────────────┘
           ▲
           │ WindowXCallS / WindowXCallB
           │ (service → UI, ONEWAY — fire and forget, no return value)
```

Shared session variables and the special-command namespace are other ways
plugins interact.

---

## 9. Native Lua libraries

Language surface is **Lua 5.1** (LuaJIT). Packaged native modules on
`package.cpath` include **lsqlite3**, **sqlite3**, **bit**, **marshal**,
**luabins**. Shared pure-Lua helpers: `serialize`, `copytable`, and the button
/ tutorial modules under `BT_Free/assets/share/lua/5.1/`.

---

## 10. Worked examples in the tree

### `starter_tutorial` (simple)

- XML in `default_settings_*.xml`: options + bootstrap `require("startertutorial")`.
- Module: `BT_Free/assets/share/lua/5.1/startertutorial.lua`.
- Uses `RegisterSpecialCommand`, `Note`, `GetPlayerTriggers` / Aliases /
  Timers, `CallPlugin("button_window", …)`, and `OnBackgroundStartup`.
- Options: `show_on_connect` (welcome note on a normal MUD) and
  `tips_while_playing` (short reminders when the player types a `.command`).
  `.tutorial` itself works in any world. `.tips on|always|off` (also
  `.tutorial tips …`).
  Java calls global `OnCommandTip` after each other `.command`.

### `button_window` (full-featured)

- XML window + options + bootstrap `require("buttonserver")`.
- Window script `require("buttonwindow")`.
- Custom `<buttonsets>` via `OnPrepareXML` / `OnXmlExport`.
- Service ↔ UI via `WindowXCallB` / `PluginXCallS`.
- Special commands `.loadset` / `.clearbuttons` / `.layoutwizard`.
- The layout wizard (`buttonlayoutwizard.lua`) is a **window**-side dialog driven
  from a service-side `<callback>` option — a worked example of the round trip.

Study these two before inventing a new IPC pattern.

Sample settings / plugin material also lives under `samples/` in the repo.

---

## 11. Checklist

1. Write `<blowtorch xmlversion="2"><plugins><plugin name="…" id="uniqueInt">`.
2. Put logic in `<script name="bootstrap" execute="true">` and/or sibling
   `.lua` loaded with `dofile(GetPluginInstallDirectory() .. "/…")`.
3. Optional: `<options>`, `<triggers>`, `<aliases>`, `<timers>`, `<windows>`.
4. Implement `OnBackgroundStartup` / `OnOptionChanged` as needed.
5. Copy to `/sdcard/BlowTorch/plugins/….xml`.
6. Session: **Plugins → Load → Install**.
7. Verify with `.yourcommand`, `Note`, logcat / error log.
8. Ship as a single XML or a folder; document the relative path under
   `BlowTorch/`. Tell users: **only install plugins they trust**.

---

## 12. Related docs

| Document | Role |
|----------|------|
| [`user-manual.md`](user-manual.md) | Player commands, `.trigger plugin:…`, built-in plugin commands |
| [`options-guide.md`](options-guide.md) | Every setting |
| [`architecture.md`](architecture.md) | Modules, processes, data flow |
| [`ORCHESTRATION.md`](ORCHESTRATION.md) | Working rules, PluginXCallS trap, Lua pitfalls |
| [`FDROID_README.md`](FDROID_README.md) | Trust / permissions wording |
| [`canvas-capabilities.md`](canvas-capabilities.md) | What the text buffer / canvas can draw (server-facing) |
| `BTLib/.../docs/DocumentsHolder.java` | Legacy Doxygen “Plugin Anatomy” (partially outdated — prefer this guide) |

**Not verified on device for this document:** end-to-end install of a fresh
external hello-world on the maintainer’s phone. API surface and limits were
read from source (`Plugin.java`, parsers, `ExtraTextSlotsStore`,
`Connection.UNDELETABLE_PLUGINS`, ORCHESTRATION).
