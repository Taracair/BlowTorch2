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
	.. " accordionParentFingerUpAction, resolveSwipeDirection, getSwipeCommand, classifySwipe,"
	.. " gestureLabelFor, accordionDrawState\n"

local loadfn = loadstring or load
local ownsTap, ownsHold, ownsSwipe, fingerUp, resolveSwipeDirection, getSwipeCommand, classifySwipe, gestureLabelFor, drawState =
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

print("7. swipe-trigger does not flip; preview must not name flip")
-- Old behaviour (pinned then changed): outside → flip_command, preview → "look".
check(fingerUp("swipe", false, "left", false) == "none",
	"swipe-trigger release outside must not flip")
check(fingerUp("swipe", true, nil, false) == "tap_command",
	"swipe-trigger inside still allows tap command")
local flipPreview = {
	accordionDirection = "down",
	accordionTrigger = "swipe",
	accordionChildren = { { label = "A", command = "a" } },
	flipCommand = "look",
	swipeDownCommand = "",
}
check(gestureLabelFor(flipPreview, nil, true) == nil,
	"swipe-to-expand preview must not name flip")
-- Opening swipe still does not resolve to a swipe command (test 4).
-- Tap-trigger preview still names flip even though dispatch does not fire it
-- (tap-open exclusivity); that mismatch is unchanged here.
data.accordionTrigger = "swipe"
data.flipCommand = "look"
check(gestureLabelFor(data, nil, true) == nil,
	"swipe-trigger with other swipe cmds still hides flip preview")
data.accordionTrigger = "tap"
check(gestureLabelFor(data, nil, true) == "look",
	"tap-trigger outside still names flip in the preview")

print("8. swipe-to-expand draw state stays pressed, not flip colours")
check(drawState("swipe", true) == 1, "swipe-trigger inside is pressed")
check(drawState("swipe", false) == 1, "swipe-trigger outside stays pressed (not flip)")
check(drawState("tap", false) == 2, "tap-trigger outside still uses flip draw state")
check(drawState("hold", false) == 2, "hold-trigger outside still uses flip draw state")
check(drawState("tap", true) == 1, "tap-trigger inside is pressed")

if failures > 0 then
	print(string.format("FAILED (%d)", failures))
	os.exit(1)
end
print("ok")
