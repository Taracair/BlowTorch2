-- Second .clearbuttons must not draw BACK as pressed.
--
-- Run:
--     lua5.1 BT_Free/src/test/lua/clearbuttons_back_pressed_test.lua
--
-- BACK is a singleton (revertButton). ACTION_DOWN sets selected=true.
-- Tapping it calls revertButtons, which used to drop touchedbutton to {}
-- before resetTouchedButtonVisual could unpress that object. The next
-- .clearbuttons reuses the same tile and drawButtons paints it pressed.

local function scriptDir()
	local path = arg and arg[0] or ""
	local dir = path:match("^(.*)[/\\][^/\\]*$")
	return dir or "."
end

local SRC = scriptDir() .. "/../../../assets/share/lua/5.1/buttonwindow.lua"

local lines = {}
local handle = assert(io.open(SRC, "r"), "cannot open " .. SRC)
for line in handle:lines() do lines[#lines + 1] = line end
handle:close()

local function findLine(pattern, what)
	for i, line in ipairs(lines) do
		if line:match(pattern) then return i end
	end
	error("could not locate " .. what .. " in " .. SRC)
end

local first = findLine("^function revertButtons", "revertButtons")
local stop = findLine("^function restoreButtons", "restoreButtons")
local chunk = table.concat(lines, "\n", first, stop - 1)
	.. "\nreturn revertButtons\n"

local loadfn = loadstring or load
local revertButtons = assert(loadfn(chunk, "clearbuttons-back-pressed"))()

local failures = 0
local function check(cond, msg)
	if not cond then
		failures = failures + 1
		print("  FAIL: " .. msg)
	end
end

local function fakeButton(label)
	return {
		label = label,
		paintOpts = {},
		rect = {},
		selected = false,
	}
end

print("1. revertButtons unpresses the BACK singleton")
local look = fakeButton("LOOK")
local back = fakeButton("BACK")
back.selected = true
revertset = { look }
revertButton = back
buttonsCleared = true
buttons = { back }
touchedbutton = back
suppress_editor = true
drawButtonsCalls = 0
function drawButtons()
	drawButtonsCalls = drawButtonsCalls + 1
end
function notifyFloatingButtonsChanged() end
view = { invalidate = function() end }

revertButtons()
check(back.selected == false, "BACK singleton is unpressed after revert")
check(buttonsCleared == false, "cleared flag is off")
check(buttons == revertset, "restored set is back on the pad")
check(drawButtonsCalls == 1, "restored set is redrawn")
check(suppress_editor == false, "editor is unsuppressed")

print("2. a second show of BACK would not draw pressed")
-- drawButtons uses b.selected to pick draw(1) vs draw(0).
check(back.selected == false, "reused BACK tile is still unpressed")

if failures > 0 then
	print(string.format("%d failure(s)", failures))
	os.exit(1)
end
print("ok")
