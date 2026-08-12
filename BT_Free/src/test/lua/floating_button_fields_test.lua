-- Round-trip test for floating-button schema fields.
--
-- Run with:
--     luajit BT_Free/src/test/lua/floating_button_fields_test.lua
-- or  lua5.1 BT_Free/src/test/lua/floating_button_fields_test.lua
--
-- Proves serialize/loadstring keeps floating flags and positions intact so the
-- Java bridge can persist drag drops through the same path as saveButtons.

local function scriptDir()
	local path = arg and arg[0] or ""
	return path:match("^(.*)[/\\][^/\\]*$") or "."
end

local ROOT = scriptDir() .. "/../../../assets/share/lua/5.1"
package.path = ROOT .. "/?.lua;" .. package.path

dofile(ROOT .. "/serialize.lua")

local failures = 0
local function check(cond, msg)
	if not cond then
		failures = failures + 1
		print("  FAIL: " .. msg)
	end
end

print("1. floating schema fields survive serialize → loadstring")
local original = {
	floating = true,
	floatMode = "keyboard",
	floatX = 120.5,
	floatY = 340,
	floatXLand = 700,
	floatYLand = 300,
	floatRound = true,
	floatFrame = false,
	border = true,
	borderColor = 0xE0FF00FF,
	label = "KB←",
	command = ".kb stepb",
	width = 48,
	height = 48,
	labelSize = 16,
}

local dumped = serialize(original)
check(type(dumped) == "string" and #dumped > 0, "serialize returned a non-empty string")

local restored = assert(loadstring(dumped))()
check(restored.floating == true, "floating")
check(restored.floatMode == "keyboard", "floatMode")
check(restored.floatX == 120.5, "floatX")
check(restored.floatY == 340, "floatY")
check(restored.floatXLand == 700, "floatXLand")
check(restored.floatYLand == 300, "floatYLand")
check(restored.floatRound == true, "floatRound")
check(restored.floatFrame == false, "floatFrame")
check(restored.border == true, "border")
check(restored.borderColor == 0xE0FF00FF, "borderColor")
check(restored.label == "KB←", "label")
check(restored.command == ".kb stepb", "command")

print("2. applyFloatPosition payload shape round-trips")
local pos = { index = 3, floatX = 10, floatY = 20 }
local posDump = serialize(pos)
local posRestored = assert(loadstring(posDump))()
check(posRestored.index == 3, "index")
check(posRestored.floatX == 10, "floatX from apply payload")
check(posRestored.floatY == 20, "floatY from apply payload")

-- A drag in landscape sends the Land pair instead, so the portrait one is
-- left alone by applyFloatPosition.
local landPos = { index = 3, floatXLand = 800, floatYLand = 400 }
local landRestored = assert(loadstring(serialize(landPos)))()
check(landRestored.floatXLand == 800, "floatXLand from apply payload")
check(landRestored.floatYLand == 400, "floatYLand from apply payload")
check(landRestored.floatX == nil, "landscape payload leaves floatX unset")

print("3. notify snapshot with editing flag round-trips")
local snapshot = {
	editing = true,
	buttons = {},
}
local snapDump = serialize(snapshot)
local snap = assert(loadstring(snapDump))()
check(snap.editing == true, "editing")
check(type(snap.buttons) == "table" and #snap.buttons == 0, "empty buttons while editing")

snapshot = {
	editing = false,
	buttons = {
		{
			index = 1,
			label = "N",
			floating = true,
			floatMode = "always",
			floatX = -1,
			floatY = -1,
			floatRound = false,
			floatFrame = false,
			width = 48,
			height = 48,
			labelSize = 16,
			command = "n",
			showGestureLabel = true,
		},
	},
}
snapDump = serialize(snapshot)
snap = assert(loadstring(snapDump))()
check(snap.editing == false, "not editing")
check(#snap.buttons == 1, "one floating button")
check(snap.buttons[1].index == 1, "1-based index")
check(snap.buttons[1].floatX == -1, "unplaced floatX")

if failures == 0 then
	print("All floating_button_fields tests passed.")
else
	print(string.format("%d floating_button_fields test(s) failed.", failures))
	os.exit(1)
end
