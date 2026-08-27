-- Active flag: serialize round-trip and play-mode skip.
--
-- Run:
--     lua5.1 BT_Free/src/test/lua/button_active_test.lua
--
-- Missing/nil/true = active so old sets keep working. false or "false" = off.

local function scriptDir()
	local path = arg and arg[0] or ""
	return path:match("^(.*)[/\\][^/\\]*$") or "."
end

local ROOT = scriptDir() .. "/../../../assets/share/lua/5.1"
package.path = ROOT .. "/?.lua;" .. package.path
dofile(ROOT .. "/serialize.lua")

local function readAll(path)
	local handle = assert(io.open(path, "r"), "cannot open " .. path)
	local src = handle:read("*a")
	handle:close()
	return src
end

local WINDOW = ROOT .. "/buttonwindow.lua"
local lines = {}
local handle = assert(io.open(WINDOW, "r"), "cannot open " .. WINDOW)
for line in handle:lines() do lines[#lines + 1] = line end
handle:close()

local function findLine(pattern, what)
	for i, line in ipairs(lines) do
		if line:match(pattern) then return i end
	end
	error("could not locate " .. what .. " in " .. WINDOW)
end

local first = findLine("^function isPlayModeInactive", "isPlayModeInactive")
local stop = findLine("^function drawButtons", "drawButtons")
local chunk = table.concat(lines, "\n", first, stop - 1)
	.. "\nreturn isPlayModeInactive\n"
local loadfn = loadstring or load
local isPlayModeInactive = assert(loadfn(chunk, "isPlayModeInactive-extract"))()

local failures = 0
local function check(cond, msg)
	if not cond then
		failures = failures + 1
		print("  FAIL: " .. msg)
	end
end

print("1. serialize → loadstring keeps active = false")
local dumped = serialize({ label = "N", active = false, floating = true })
check(type(dumped) == "string" and #dumped > 0, "serialize returned a non-empty string")
check(dumped:find("false", 1, true) ~= nil, "dump contains false")
local restored = assert(loadstring(dumped))()
check(restored.active == false, "active == false after loadstring")
check(type(restored.active) == "boolean", "active stays a boolean, not a string")
check(restored.label == "N", "label survived with active")

print("2. missing field is treated as active")
manage = false
check(isPlayModeInactive({ data = {} }) == false,
	"empty data (old button) is not inactive")
check(isPlayModeInactive({ data = { active = nil } }) == false,
	"explicit nil active is not inactive")
check(isPlayModeInactive({ data = { active = true } }) == false,
	"active = true is not inactive")
check(isPlayModeInactive({ data = { active = "true" } }) == false,
	"active = \"true\" is not inactive")
local old = assert(loadstring(serialize({ label = "LOOK" })))()
check(old.active == nil, "old serialized buttons have no active key")
check(isPlayModeInactive({ data = old }) == false,
	"deserialized old button is treated as active")

print("3. \"false\" string is inactive; boolean false too")
check(isPlayModeInactive({ data = { active = "false" } }) == true,
	"active = \"false\" is inactive in play")
check(isPlayModeInactive({ data = { active = false } }) == true,
	"active = false is inactive in play")
manage = true
check(isPlayModeInactive({ data = { active = false } }) == false,
	"edit mode does not skip inactive buttons")
check(isPlayModeInactive({ data = { active = "false" } }) == false,
	"edit mode does not skip string \"false\" either")
manage = false
check(isPlayModeInactive(nil) == false, "nil button is not inactive")
check(isPlayModeInactive({ }) == false, "button with no data is not inactive")

print("4. editor / save / play-mode skip paths name active")
local advanced = readAll(ROOT .. "/buttoneditoradvanced.lua")
check(advanced:find('setText("Active")', 1, true) ~= nil,
	"Others tab has Active checkbox")
check(advanced:find("tmp.active", 1, true) ~= nil,
	"getEditorValues reads active")

local window = table.concat(lines, "\n")
check(window:find("tmp.data.active = false", 1, true) ~= nil,
	"buttonEditorDone stores active = false")
check(window:find("tmp.data.active = true", 1, true) ~= nil,
	"buttonEditorDone stores active = true when present")
check(window:find("editorValues.active = not (button.data.active == false", 1, true) ~= nil,
	"showEditorDialog loads active on the single-button path")
check(window:find("or isPlayModeInactive(b)", 1, true) ~= nil,
	"drawButtons skips inactive in play")
check(window:find("and not isPlayModeInactive(b)", 1, true) ~= nil,
	"buttonTouched / overlay skip inactive in play")
check(window:find("and not isPlayModeInactive(b) then", 1, true) ~= nil,
	"floating JSON requires active (notifyFloatingButtonsChanged)")

local buttons = readAll(ROOT .. "/button.lua")
check(buttons:find("active = true", 1, true) ~= nil,
	"BUTTONSET_DATA defaults active on")

local help = readAll(ROOT .. "/buttoneditor.lua")
check(help:find("Active hides the button in play without deleting it", 1, true) ~= nil,
	"HELP_OTHERS mentions Active")

local tutorial = readAll(ROOT .. "/startertutorial.lua")
check(tutorial:find("Others → Active", 1, true) ~= nil,
	"starter tutorial buttons_edit mentions Others → Active")

if failures == 0 then
	print("All button_active tests passed.")
else
	print(string.format("%d button_active test(s) failed.", failures))
	os.exit(1)
end
