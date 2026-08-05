-- Line up, spread, and undo in the button editor.
--
-- Run it with:
--     luajit BT_Free/src/test/lua/button_layout_tools_test.lua
--
-- Three claims are pinned here, because all three are easy to write and hard to
-- see going wrong on a phone:
--
--   * lining up moves the buttons onto one line and nowhere else along it. The
--     anchor is the extreme button, so one of them does not move at all;
--   * spreading keeps the block on screen. A group already near the bottom used
--     to be laid out downwards from where it sat and its last rows went off the
--     edge, where nothing could reach them;
--   * undo restores a layout, not a reference to one. Restoring the same
--     snapshot twice must give the same result twice, which is what undo, redo,
--     undo does.
--
-- The blocks are pulled out of buttonwindow.lua so the test cannot drift from
-- the shipped source. Host globals they touch are stubbed.

local function scriptDir()
	local path = arg and arg[0] or ""
	return path:match("^(.*)[/\\][^/\\]*$") or "."
end

local ROOT = scriptDir() .. "/../../../assets/share/lua/5.1"
local SRC = ROOT .. "/buttonwindow.lua"
package.path = ROOT .. "/?.lua;" .. package.path

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

local failures = 0
local function check(cond, msg)
	if not cond then
		failures = failures + 1
		print("  FAIL: " .. msg)
	end
end

-- Host stubs. A Pixel 9a upright.
local screenW, screenH = 1080, 2400
statusoffset = 0
density = 1
view = {
	getWidth = function() return screenW end,
	getHeight = function() return screenH end,
	invalidate = function() end,
}
buttons = {}
defaults = { width = 42, height = 42, gridXwidth = 50, gridYwidth = 50 }
gridXwidth = 50
gridYwidth = 50
manage = true
managerCanvas = nil
notes = {}
function Note(s) notes[#notes + 1] = s end
function drawButtons() end
function drawManagerGrid() end
function saveDefaultOptions() end
function shiftFloatPlacement() end
function refreshEditorUndoChrome() end

-- A button just rich enough for these tools: data, a density, and the rect
-- update they call after every move.
BUTTON = {}
function BUTTON:new(data, dens)
	-- selected = false, as the real BUTTON:new sets it.
	local o = { data = data, density = dens, rectAt = nil, selected = false }
	setmetatable(o, self)
	self.__index = self
	return o
end
function BUTTON:updateRectAt(x, y, off)
	self.rectAt = { x = x, y = y, off = off }
end

local function newButton(x, y, w, h)
	return BUTTON:new({ x = x, y = y, width = w or 42, height = h or 42 }, density)
end

-- clampLogicalPosition through clampAllButtons: the real posX/posY/setPos and
-- clamp, so "did not run off the screen" is checked against the shipped rule.
local clampFirst = findLine("^function clampLogicalPosition", "clampLogicalPosition")
local clampStop = findLine("^function refreshStatusOffset", "refreshStatusOffset")
assert(loadstring(table.concat(lines, "\n", clampFirst, clampStop - 1), "clamp"))()
assert(type(clampLogicalPosition) == "function", "clamp extraction failed")

-- tidyButtonLayout, alignSelectedButtons and spreadSelectedButtons are
-- contiguous, and all three read the selection through layoutTargets.
local toolsFirst = findLine("^function tidyButtonLayout", "tidyButtonLayout")
local toolsStop = findLine("^%-%- Pick a grid that tiles", "fitGridToScreen header")
assert(loadstring(table.concat(lines, "\n", toolsFirst, toolsStop - 1), "tools"))()
assert(type(alignSelectedButtons) == "function", "tools extraction failed")

-- The tools ask for the selection; here it is always every button.
function layoutTargets()
	local all = {}
	for i = 1, #buttons do all[#all + 1] = buttons[i] end
	return all, true
end

print("1. lining up puts the buttons on one line and leaves the other axis alone")
buttons = { newButton(100, 300), newButton(160, 500), newButton(220, 700) }
alignSelectedButtons("x")
check(buttons[1].data.x == 100, "leftmost button must not move")
check(buttons[2].data.x == 100 and buttons[3].data.x == 100,
	"the others must come to the leftmost X")
check(buttons[1].data.y == 300 and buttons[2].data.y == 500
	and buttons[3].data.y == 700, "Y must be untouched by a vertical line-up")

buttons = { newButton(100, 300), newButton(160, 500), newButton(220, 700) }
alignSelectedButtons("y")
check(buttons[1].data.y == 300, "topmost button must not move")
check(buttons[2].data.y == 300 and buttons[3].data.y == 300,
	"the others must come to the topmost Y")
check(buttons[1].data.x == 100 and buttons[2].data.x == 160
	and buttons[3].data.x == 220, "X must be untouched by a horizontal line-up")

print("2. lining up a single button does nothing and says so")
buttons = { newButton(100, 300) }
notes = {}
alignSelectedButtons("x")
check(buttons[1].data.x == 100 and buttons[1].data.y == 300, "one button cannot line up")
check(#notes == 1, "a one-button line-up must explain itself")

print("3. spread shapes: column, row, square-ish block")
local function spreadAndCount(shape, n)
	buttons = {}
	for i = 1, n do buttons[i] = newButton(100 + i * 7, 200 + i * 3) end
	spreadSelectedButtons(shape)
	local xs, ys = {}, {}
	for i = 1, n do
		xs[buttons[i].data.x] = true
		ys[buttons[i].data.y] = true
	end
	local cols, rows = 0, 0
	for _ in pairs(xs) do cols = cols + 1 end
	for _ in pairs(ys) do rows = rows + 1 end
	return cols, rows
end

local cols, rows = spreadAndCount("column", 6)
check(cols == 1 and rows == 6, "column: one column, six rows (got "
	.. cols .. "x" .. rows .. ")")

cols, rows = spreadAndCount("row", 6)
check(cols == 6 and rows == 1, "row: six columns, one row (got "
	.. cols .. "x" .. rows .. ")")

cols, rows = spreadAndCount("grid", 9)
check(cols == 3 and rows == 3, "block: nine buttons make a 3x3 (got "
	.. cols .. "x" .. rows .. ")")

cols, rows = spreadAndCount("grid", 6)
check(cols == 3 and rows == 2, "block: six buttons make 3 across, 2 down (got "
	.. cols .. "x" .. rows .. ")")

print("4. a spread never leaves a button off the screen")
-- The regression: a group sitting low down was laid out downwards from there.
buttons = {}
for i = 1, 12 do buttons[i] = newButton(500, screenH - 120 + i) end
spreadSelectedButtons("column")
for i = 1, #buttons do
	local d = buttons[i].data
	local half = d.height / 2
	check(d.y - half >= -0.5 and d.y + half <= screenH + 0.5,
		"button " .. i .. " is on screen (y=" .. d.y .. ")")
	check(d.x - d.width / 2 >= -0.5 and d.x + d.width / 2 <= screenW + 0.5,
		"button " .. i .. " is within the screen width (x=" .. d.x .. ")")
end
-- Twelve 42dp buttons on a 50dp pitch need 600px and the screen has 2400, so
-- pulling the block back on screen must not have stacked them on the edge.
local distinct = {}
for i = 1, #buttons do distinct[buttons[i].data.y] = true end
local seen = 0
for _ in pairs(distinct) do seen = seen + 1 end
check(seen == 12, "a column that fits must keep twelve separate rows, got " .. seen)

print("5. undo restores the layout, and restores it again on the way back")
local undoFirst = findLine("^%-%- Undo and redo for the button editor%.", "undo block")
local undoStop = findLine("^%-%- Buttons the layout tools act on", "layoutTargets header")
assert(loadstring(table.concat(lines, "\n", undoFirst, undoStop - 1), "undo"))()
assert(type(pushUndo) == "function", "undo extraction failed")

function refreshRect(b) b:updateRectAt(b.data.x, b.data.y, statusoffset) end

buttons = { newButton(100, 300), newButton(200, 400) }
pushUndo()
check(#undoStack == 1, "pushUndo must record one step")

buttons[1].data.x = 999
buttons[2].data.x = 888
defaults.width = 60
editorMenuUndo()
check(buttons[1].data.x == 100 and buttons[2].data.x == 200,
	"undo must put the positions back")
check(defaults.width == 42, "undo must put the set defaults back")
check(#undoStack == 0 and #redoStack == 1, "undo moves the step onto the redo stack")

editorMenuRedo()
check(buttons[1].data.x == 999 and buttons[2].data.x == 888,
	"redo must bring the change back")
check(defaults.width == 60, "redo must bring the defaults back")

editorMenuUndo()
check(buttons[1].data.x == 100 and buttons[2].data.x == 200,
	"the same snapshot must restore a second time, not hand out a reference")

print("5b. undo keeps the selection, because the tools act on it")
buttons = { newButton(10, 10), newButton(20, 20), newButton(30, 30) }
buttons[1].selected = true
buttons[3].selected = true
clearUndoHistory()
pushUndo()
buttons[1].data.x = 77
editorMenuUndo()
check(buttons[1].selected == true and buttons[3].selected == true,
	"the two selected buttons must still be selected after an undo")
check(buttons[2].selected == false, "the unselected one must stay unselected")

print("6. undo survives a delete, and only remembers the last twenty steps")
buttons = { newButton(10, 10), newButton(20, 20), newButton(30, 30) }
clearUndoHistory()
pushUndo()
buttons[3] = nil
buttons[2] = nil
editorMenuUndo()
check(#buttons == 3, "undo must bring deleted buttons back, got " .. #buttons)
check(buttons[3] ~= nil and buttons[3].data.x == 30, "the third button comes back where it was")

clearUndoHistory()
for i = 1, 25 do
	buttons[1].data.x = i
	pushUndo()
end
check(#undoStack == UNDO_LIMIT, "the stack is capped at " .. UNDO_LIMIT
	.. ", got " .. #undoStack)

print("6b. deleting the selection keeps the survivors, in order and in place")
local delFirst = findLine("^function deleteSelectedButtons", "deleteSelectedButtons")
local delStop = findLine("^arrangeListener = {}", "arrangeListener")
assert(loadstring(table.concat(lines, "\n", delFirst, delStop - 1), "delete"))()
assert(type(deleteSelectedButtons) == "function", "delete extraction failed")

buttons = { newButton(10, 10), newButton(20, 20), newButton(30, 30), newButton(40, 40) }
local sameTable = buttons
buttons[2].selected = true
buttons[4].selected = true
clearUndoHistory()
deleteSelectedButtons()
check(#buttons == 2, "two of four survive, got " .. #buttons)
check(buttons[1].data.x == 10 and buttons[2].data.x == 30,
	"the survivors keep the order they were in")
check(buttons == sameTable,
	"the table itself must survive: other modules hold a reference to it")
editorMenuUndo()
check(#buttons == 4, "undo brings the deleted buttons back, got " .. #buttons)
check(buttons[2].data.x == 20 and buttons[4].data.x == 40,
	"and brings them back where they were")

print("7. a new change drops the redo branch")
buttons = { newButton(10, 10) }
clearUndoHistory()
pushUndo()
buttons[1].data.x = 50
editorMenuUndo()
check(#redoStack == 1, "there is something to redo")
pushUndo()
check(#redoStack == 0, "making a change must drop what was undone")

print("8. nothing is recorded outside the editor")
clearUndoHistory()
manage = false
pushUndo()
check(#undoStack == 0, "play mode must not collect undo steps")
manage = true

print("")
if failures == 0 then
	print("ALL TESTS PASSED")
	os.exit(0)
end
print(failures .. " TEST(S) FAILED")
os.exit(1)
