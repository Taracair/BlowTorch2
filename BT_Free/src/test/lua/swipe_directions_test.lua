-- Regression test for eight-way swipe classification.
--
-- Run it with:
--     luajit BT_Free/src/test/lua/swipe_directions_test.lua
--
-- Gradle does not pick this up; it is a standalone check, because the gesture
-- maths is pure arithmetic and does not need a device to verify.
--
-- The point of this test is the third block: adding diagonals must not change
-- what any existing button does. Buttons configured before eight-way swipe
-- existed have no diagonal commands, and for those the resolved command has to
-- stay identical for every possible finger movement.
--
-- The function bodies are pulled straight out of buttonwindow.lua rather than
-- copied here, so the test cannot silently drift away from the shipped code.
-- Boundaries are found by name, not by line number.

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

-- Everything from classifySwipe up to (not including) the next unrelated helper.
local first = findLine("^function classifySwipe%(dx", "classifySwipe")
local stop = findLine("^local function hasButtonSwitch", "hasButtonSwitch")

local chunk = table.concat(lines, "\n", first, stop - 1)
	.. "\nreturn classifySwipe, classifySwipe8, getSwipeCommand\n"

local classifySwipe, classifySwipe8, getSwipeCommand =
	assert(loadstring(chunk, "buttonwindow-extract"))()
assert(classifySwipe and classifySwipe8 and getSwipeCommand,
	"extracted chunk did not define the expected functions")

local function has(cmd) return cmd ~= nil and cmd ~= "" end

-- Mirrors the resolution order in the ACTION_UP handler in buttonwindow.lua.
local function resolveNew(data, dx, dy, threshold)
	if classifySwipe(dx, dy, threshold) == nil then return nil end
	local cmd = getSwipeCommand(data, classifySwipe8(dx, dy, threshold))
	if not has(cmd) then
		cmd = getSwipeCommand(data, classifySwipe(dx, dy, threshold))
	end
	return has(cmd) and cmd or nil
end

-- What the handler did before diagonals were added.
local function resolveOld(data, dx, dy, threshold)
	local dir = classifySwipe(dx, dy, threshold)
	if dir == nil then return nil end
	local cmd = getSwipeCommand(data, dir)
	return has(cmd) and cmd or nil
end

local failures = 0
local function check(cond, msg)
	if not cond then
		failures = failures + 1
		print("  FAIL: " .. msg)
	end
end

local THRESHOLD = 24

print("1. eight-way sectors, pure directions")
for _, case in ipairs({
	{  100,    0, "right"     }, {  100, -100, "upright"   },
	{    0, -100, "up"        }, { -100, -100, "upleft"    },
	{ -100,    0, "left"      }, { -100,  100, "downleft"  },
	{    0,  100, "down"      }, {  100,  100, "downright" },
}) do
	local got = classifySwipe8(case[1], case[2], THRESHOLD)
	check(got == case[3], string.format("dx=%d dy=%d gave %s, wanted %s",
		case[1], case[2], tostring(got), case[3]))
end

print("2. both classifiers agree on the dead zone")
for dx = -40, 40, 2 do
	for dy = -40, 40, 2 do
		check((classifySwipe(dx, dy, THRESHOLD) == nil)
			== (classifySwipe8(dx, dy, THRESHOLD) == nil),
			string.format("dead zone disagreement at dx=%d dy=%d", dx, dy))
	end
end

print("3. no regression: buttons without diagonals resolve exactly as before")
local compared = 0
for _, data in ipairs({
	{ name = "up only",      swipeUpCommand = "north" },
	{ name = "left only",    swipeLeftCommand = "west" },
	{ name = "up and down",  swipeUpCommand = "north", swipeDownCommand = "south" },
	{ name = "all four",     swipeUpCommand = "n", swipeDownCommand = "s",
	                         swipeLeftCommand = "w", swipeRightCommand = "e" },
	{ name = "no swipes",    command = "look" },
}) do
	for dx = -200, 200, 5 do
		for dy = -200, 200, 5 do
			compared = compared + 1
			check(resolveOld(data, dx, dy, THRESHOLD) == resolveNew(data, dx, dy, THRESHOLD),
				string.format("%s: dx=%d dy=%d old=%s new=%s", data.name, dx, dy,
					tostring(resolveOld(data, dx, dy, THRESHOLD)),
					tostring(resolveNew(data, dx, dy, THRESHOLD))))
		end
	end
end
print(string.format("   compared %d dx/dy combinations", compared))

print("4. diagonals fire when they are configured")
local diagonal = {
	swipeUpCommand = "north",
	swipeUpRightCommand = "northeast",
	swipeDownLeftCommand = "southwest",
}
check(resolveNew(diagonal,  100, -100, THRESHOLD) == "northeast", "45 degrees -> northeast")
check(resolveNew(diagonal, -100,  100, THRESHOLD) == "southwest", "-135 degrees -> southwest")
check(resolveNew(diagonal,    0, -100, THRESHOLD) == "north",     "straight up -> north")

print("5. a diagonal with nothing bound falls back to the straight direction")
check(resolveNew({ swipeUpCommand = "north" }, 58, -100, THRESHOLD) == "north",
	"30 degrees off vertical with no diagonal bound must still send north")
check(resolveNew({ swipeUpCommand = "north", swipeUpRightCommand = "northeast" },
	58, -100, THRESHOLD) == "northeast",
	"the same gesture must prefer the diagonal once one is bound")

print("")
if failures == 0 then
	print("ALL TESTS PASSED")
	os.exit(0)
end
print(failures .. " TEST(S) FAILED")
os.exit(1)
