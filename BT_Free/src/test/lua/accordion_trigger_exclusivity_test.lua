-- Accordion trigger exclusivity predicates and swipe-preview suppression.
--
-- Run:
--     lua5.1 BT_Free/src/test/lua/accordion_trigger_exclusivity_test.lua
--
-- The gesture that opens an accordion must not fire that gesture's normal
-- button command. Predicates are extracted from buttonwindow.lua so this file
-- cannot drift from what the touch handler uses.

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

local first = findLine("^function classifySwipe%(dx", "classifySwipe")
local stop = findLine("^local function hasButtonSwitch", "hasButtonSwitch")

local chunk = "local function hasButtonCommand(cmd) return cmd ~= nil and cmd ~= '' end\n"
	.. table.concat(lines, "\n", first, stop - 1)
	.. "\nreturn accordionOwnsTap, accordionOwnsHold, accordionOwnsSwipeDirection,"
	.. " accordionParentFingerUpAction, resolveSwipeDirection, getSwipeCommand, classifySwipe\n"

local loadfn = loadstring or load
local ownsTap, ownsHold, ownsSwipe, fingerUp, resolveSwipeDirection, getSwipeCommand, classifySwipe =
	assert(loadfn(chunk, "accordion-exclusivity-extract"))()

local failures = 0
local function check(cond, msg)
	if not cond then
		failures = failures + 1
		print("  FAIL: " .. msg)
	end
end

print("1. tap trigger owns only tap")
check(ownsTap("tap") == true, "tap owns tap")
check(ownsHold("tap") == false, "tap does not own hold")
check(ownsSwipe("tap", "down", "down") == false, "tap does not own swipe")

print("2. hold trigger owns only hold")
check(ownsTap("hold") == false, "hold does not own tap")
check(ownsHold("hold") == true, "hold owns hold")
check(ownsSwipe("hold", "down", "down") == false, "hold does not own swipe")

print("3. swipe trigger owns only accordionDirection")
check(ownsTap("swipe") == false, "swipe does not own tap")
check(ownsHold("swipe") == false, "swipe does not own hold")
check(ownsSwipe("swipe", "down", "down") == true, "swipe owns matching direction")
check(ownsSwipe("swipe", "down", "up") == false, "swipe does not own other direction")
check(ownsSwipe("swipe", "down", nil) == false, "swipe does not own nil")

print("4. resolveSwipeDirection suppresses owned accordion swipe command")
local data = {
	accordionDirection = "down",
	accordionTrigger = "swipe",
	accordionChildren = { { label = "A", command = "a" } },
	swipeDownCommand = "south",
	swipeUpCommand = "north",
	swipeLeftCommand = "west",
	swipeRightCommand = "east",
}
local THRESHOLD = 24
check(resolveSwipeDirection(data, 0, 100, THRESHOLD) == nil,
	"owned down swipe must not resolve to a command")
check(resolveSwipeDirection(data, 0, -100, THRESHOLD) == "up",
	"other direction still resolves")
check(classifySwipe(0, 100, THRESHOLD) == "down", "classifier still reports down")

print("5. non-swipe trigger still resolves swipe commands")
data.accordionTrigger = "tap"
check(resolveSwipeDirection(data, 0, 100, THRESHOLD) == "down",
	"tap-trigger accordion still fires swipe commands")
check(getSwipeCommand(data, "down") == "south", "getSwipeCommand unchanged")

print("6. drifted tap on tap-trigger accordion toggles, never fires command/flip")
-- Finger passed the swipe threshold (swipeDir non-nil) but no swipe command
-- was bound/fired (swipeHandled=false). Release inside the tile.
check(fingerUp("tap", true, "down", false) == "toggle",
	"inside drift → toggle")
check(fingerUp("tap", false, "down", false) == "none",
	"outside drift → no flipCommand")
check(fingerUp("tap", true, nil, false) == "toggle",
	"plain inside tap → toggle")
check(fingerUp("tap", true, "down", true) == "none",
	"already-handled swipe → none")
check(fingerUp("hold", true, "down", false) == "tap_command",
	"hold-trigger drift inside still allows tap command")
check(fingerUp("hold", false, nil, false) == "flip_command",
	"hold-trigger outside still flips")

if failures > 0 then
	print(string.format("FAILED (%d)", failures))
	os.exit(1)
end
print("ok")
