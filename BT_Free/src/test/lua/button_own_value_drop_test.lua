-- Which of a button's own values Done is allowed to throw away.
--
-- Run it with:
--     luajit BT_Free/src/test/lua/button_own_value_drop_test.lua
--
-- The bug this pins: dropping an own value makes the button inherit
-- BUTTONSET_DATA, because BUTTON_DATA.__index is that global prototype and
-- nothing links a button to its own set's `defaults`. Done used to drop any own
-- value matching the set default, so a set whose default was 44 had every
-- button's 44 thrown away and every button came back 48 — the factory width.
-- Apply size looked like it worked, and the revert arrived one step later on
-- Done, which is what made it look intermittent.
--
-- The function is pulled out of buttonwindow.lua so the test cannot drift from
-- the shipped source.

local function scriptDir()
	local path = arg and arg[0] or ""
	return path:match("^(.*)[/\\][^/\\]*$") or "."
end

local ROOT = scriptDir() .. "/../../../assets/share/lua/5.1"
local SRC = ROOT .. "/buttonwindow.lua"

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

-- The prototype every button falls back to, as button.lua declares it.
BUTTONSET_DATA = {
	width = 48, height = 48, labelSize = 16,
	primaryColor = 1000, flipColor = 2000, selectedColor = 3000,
	labelColor = 4000, flipLabelColor = 5000,
}

local startFields = findLine("^DROPPABLE_OWN_FIELDS = {", "DROPPABLE_OWN_FIELDS")
local startFn = findLine("^function dropRedundantOwnValues", "dropRedundantOwnValues")
local endFn
for i = startFn, #lines do
	if lines[i] == "end" then endFn = i break end
end
assert(endFn, "could not find the end of dropRedundantOwnValues")

local chunk = table.concat(lines, "\n", startFields, endFn)
local loader = loadstring or load
assert(loader(chunk, "dropRedundantOwnValues"))()

local function button(own)
	return { data = own }
end

print("dropRedundantOwnValues")

-- 1. The reported bug. Set default differs from the prototype, so the own value
--    is the only thing holding the size and must survive.
do
	local b = button({ width = 44, height = 44 })
	dropRedundantOwnValues(b, { width = 44, height = 44 })
	check(rawget(b.data, "width") == 44,
		"own width 44 must survive when the set default is 44 and the prototype is 48")
	check(rawget(b.data, "height") == 44,
		"own height 44 must survive alongside it")
end

-- 2. The saving the clearing was for. Set default equals the prototype, so
--    dropping changes nothing the player can see.
do
	local b = button({ width = 48, height = 48 })
	dropRedundantOwnValues(b, { width = 48, height = 48 })
	check(rawget(b.data, "width") == nil,
		"own width equal to both the set default and the prototype is redundant")
	check(rawget(b.data, "height") == nil,
		"same for height")
end

-- 3. A button deliberately different from its set keeps its own value either way.
do
	local b = button({ width = 72 })
	dropRedundantOwnValues(b, { width = 48 })
	check(rawget(b.data, "width") == 72,
		"a button sized differently from the set must never be cleared")
end

-- 4. Colours travel the same path, and had the same bug.
do
	local b = button({ primaryColor = 1000, flipColor = 9999 })
	dropRedundantOwnValues(b, { primaryColor = 1000, flipColor = 9999 })
	check(rawget(b.data, "primaryColor") == nil,
		"a colour matching both set and prototype is redundant")
	check(rawget(b.data, "flipColor") == 9999,
		"a set colour that differs from the factory must keep its own copy")
end

-- 5. A set with no opinion of its own inherits the prototype, so its buttons
--    clear exactly as they always did.
do
	local setDefaults = setmetatable({}, { __index = BUTTONSET_DATA })
	local b = button({ width = 48 })
	dropRedundantOwnValues(b, setDefaults)
	check(rawget(b.data, "width") == nil,
		"a set that never set a width behaves as before")
end

-- 6. Nothing to do, and nothing to crash on.
do
	local b = button({})
	dropRedundantOwnValues(b, { width = 44 })
	check(rawget(b.data, "width") == nil, "a button with no own width is left alone")
	dropRedundantOwnValues(nil, { width = 44 })
	dropRedundantOwnValues(button({}), nil)
end

if failures == 0 then
	print("  ok")
	os.exit(0)
end
print(failures .. " failure(s)")
os.exit(1)
