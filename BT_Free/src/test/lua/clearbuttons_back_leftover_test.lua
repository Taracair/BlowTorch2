-- Tapping BACK after .clearbuttons must not paint BACK on the restored pad.
--
-- Run:
--     lua5.1 BT_Free/src/test/lua/clearbuttons_back_leftover_test.lua
--
-- .clearbuttons swaps the pad for one BACK tile. Tapping BACK calls
-- revertButtons (previous set is drawn) and then resetTouchedButtonVisual
-- while `touchedbutton` is still that BACK object. The fast path used to
-- draw it on top of the restored set, so BACK stayed visible with the
-- normal buttons. Same class of leftover as loadButtons.

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

local first = findLine("^local function buttonIsOnPad", "buttonIsOnPad")
local stop = findLine("^local function collapseAccordionChildParentIfNeeded",
	"collapseAccordionChildParentIfNeeded")

local chunk = table.concat(lines, "\n", first, stop - 1)
	.. "\nreturn buttonIsOnPad, resetTouchedButtonVisual\n"

local loadfn = loadstring or load
local buttonIsOnPad, resetTouchedButtonVisual =
	assert(loadfn(chunk, "clearbuttons-back-extract"))()

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
		draw = function(self)
			self.drew = (self.drew or 0) + 1
		end,
	}
end

print("1. BACK is not on the pad after revert")
local look = fakeButton("LOOK")
local score = fakeButton("SCORE")
local back = fakeButton("BACK")
buttons = { look, score }
check(buttonIsOnPad(look) == true, "LOOK is on the restored pad")
check(buttonIsOnPad(score) == true, "SCORE is on the restored pad")
check(buttonIsOnPad(back) == false, "BACK is not a member of the restored pad")
check(buttonIsOnPad(nil) == false, "nil is not on the pad")
check(buttonIsOnPad({}) == false, "empty {} is not on the pad")

print("2. finger-up on BACK after revert must not paint BACK")
local drawButtonsCalls = 0
local clearButtonCalls = 0
function drawButtons()
	drawButtonsCalls = drawButtonsCalls + 1
end
function clearButton()
	clearButtonCalls = clearButtonCalls + 1
end
view = { invalidate = function() end }
manage = false
swipePreviewDir = nil
gestureLabelText = nil
normalTouchState = 1
buttons = { look, score }
touchedbutton = back
back.selected = true
resetTouchedButtonVisual()
check(drawButtonsCalls == 1,
	"orphan BACK takes the full redraw, not the one-tile fast path")
check(clearButtonCalls == 0, "must not clearButton the leftover BACK")
check((back.drew or 0) == 0, "must not draw BACK on top of the restored set")
check(back.selected == false, "pressed highlight is cleared")

print("3. finger-up on a tile still on the pad still uses the fast path")
drawButtonsCalls = 0
clearButtonCalls = 0
look.drew = 0
look.selected = true
touchedbutton = look
swipePreviewDir = nil
gestureLabelText = nil
resetTouchedButtonVisual()
check(drawButtonsCalls == 0, "in-set tap does not full-redraw")
check(clearButtonCalls == 1, "in-set tap clears that one tile")
check(look.drew == 1, "in-set tap redraws that one tile")

if failures > 0 then
	print(string.format("%d failure(s)", failures))
	os.exit(1)
end
print("ok")
