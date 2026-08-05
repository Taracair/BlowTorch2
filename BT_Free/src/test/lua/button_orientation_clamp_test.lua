-- Which coordinate pair a clamp is allowed to write.
--
-- Run it with:
--     luajit BT_Free/src/test/lua/button_orientation_clamp_test.lua
--
-- A button carries a portrait pair (x/y) and an optional landscape pair
-- (xLand/yLand). An absent landscape pair means landscape draws the portrait
-- layout, which is where every existing profile starts. Two rules follow, and
-- they pull in opposite directions, which is why they are pinned here:
--
--   * turning the phone must never write anything -- landscape is narrower and
--     shorter, so a clamp that persisted came home with you and portrait was
--     rearranged for good;
--   * opening the button editor in landscape must pull a pad inherited from
--     portrait back onto the landscape screen and keep it, or the player drags
--     buttons that jump back the next time the set loads.
--
-- The block is pulled out of buttonwindow.lua so the test cannot drift from the
-- shipped source. Host globals it touches are stubbed.

local function scriptDir()
	local path = arg and arg[0] or ""
	return path:match("^(.*)[/\\][^/\\]*$") or "."
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

-- clampLogicalPosition through clampAllButtons are contiguous in the source.
local first = findLine("^function clampLogicalPosition", "clampLogicalPosition")
local stop = findLine("^function refreshStatusOffset", "refreshStatusOffset")

-- Host stubs. A Pixel 9a: 1080x2400 upright, 2400x1080 on its side.
local screenW, screenH = 1080, 2400
statusoffset = 0
view = {
	getWidth = function() return screenW end,
	getHeight = function() return screenH end,
}
buttons = {}
-- The float layer is a separate concern; record the calls so the test can say
-- the floating copy was told to move with its button.
floatShifts = 0
function shiftFloatPlacement() floatShifts = floatShifts + 1 end

assert(loadstring(table.concat(lines, "\n", first, stop - 1), "clamp-extract"))()
assert(type(clampAllButtons) == "function", "extraction failed")

local failures = 0
local function check(cond, msg)
	if not cond then
		failures = failures + 1
		print("  FAIL: " .. msg)
	end
end

-- A button as clampAllButtons sees it: data, density, and a rect it can update.
local function button(data)
	return {
		data = data,
		density = 1,
		rectX = nil,
		rectY = nil,
		updateRectAt = function(self, x, y) self.rectX, self.rectY = x, y end,
	}
end

local function pad()
	return {
		button({ x = 540, y = 300, width = 42, height = 42 }),
		-- Below the landscape screen (1080) but on the portrait one (2400).
		button({ x = 540, y = 2000, width = 42, height = 42 }),
	}
end

print("1. turning the phone does not write anything")
buttons = pad()
screenW, screenH = 2400, 1080
local moved = clampAllButtons(true)
check(moved == 1, "the low tile must be counted as needing a move, got " .. tostring(moved))
check(buttons[2].data.y == 2000, "the portrait y must survive the rotation untouched")
check(buttons[2].data.yLand == nil, "a rotation must not invent a landscape pair")
check(buttons[2].rectY ~= nil and buttons[2].rectY < 1080,
	"it must still be drawn on the landscape screen, at " .. tostring(buttons[2].rectY))

print("2. opening the editor in landscape keeps the correction, in landscape only")
buttons = pad()
screenW, screenH = 2400, 1080
local pulled = clampAllButtons(false, true)
check(pulled == 1, "one tile was off the landscape screen, got " .. tostring(pulled))
check(buttons[2].data.y == 2000, "the portrait pair must not be touched")
check(buttons[2].data.x == 540, "the portrait pair must not be touched")
check(buttons[2].data.yLand ~= nil, "the landscape pair must have been written")
check(buttons[2].data.yLand <= 1080 - 21,
	"the landscape y must be on the landscape screen, got " .. tostring(buttons[2].data.yLand))
check(buttons[2].data.xLand == 540, "x was already fine and must carry over as it is")
check(buttons[1].data.xLand == nil and buttons[1].data.yLand == nil,
	"a tile that was already on screen must not gain a landscape pair")

print("3. the same in portrait writes the portrait pair, and no landscape pair")
buttons = { button({ x = 540, y = 5000, width = 42, height = 42 }) }
screenW, screenH = 1080, 2400
check(clampAllButtons(false, true) == 1, "the tile below the screen must be pulled back")
check(buttons[1].data.y <= 2400 - 21, "the portrait y must be on the portrait screen")
check(buttons[1].data.yLand == nil, "portrait must not write a landscape pair")

print("4. a button already in view is left exactly where it is")
buttons = { button({ x = 540, y = 300, width = 42, height = 42 }) }
screenW, screenH = 1080, 2400
check(clampAllButtons(false, true) == 0, "nothing to move means nothing reported")
check(buttons[1].data.x == 540 and buttons[1].data.y == 300, "and nothing written")

print("5. the floating copy is moved with its button, not left behind")
floatShifts = 0
buttons = pad()
screenW, screenH = 2400, 1080
clampAllButtons(false, true)
check(floatShifts == 1, "the one moved button must shift its floating copy, got " .. floatShifts)

print("")
if failures == 0 then
	print("ALL TESTS PASSED")
	os.exit(0)
end
print(failures .. " TEST(S) FAILED")
os.exit(1)
