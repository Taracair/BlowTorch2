-- Accordion pin-by-id helpers (grid tiles, not pack snapshots).
--
-- Run:
--     lua5.1 BT_Free/src/test/lua/accordion_pin_test.lua
--
-- LOOK left of MORE → expand left. copyAccordionChildRows must not mutate
-- the BUTTONSET_DATA shared {}. Packs stay label+command with no id.

local function scriptDir()
	local path = arg and arg[0] or ""
	local dir = path:match("^(.*)[/\\][^/\\]*$")
	return dir or "."
end

local ROOT = scriptDir() .. "/../../../assets/share/lua/5.1"
local SRC = ROOT .. "/buttonwindow.lua"
local SERVER = ROOT .. "/buttonserver.lua"

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

local first = findLine("^function copyAccordionChildRows", "copyAccordionChildRows")
local stop = findLine("^function isPlayModePinnedAccordionSource",
	"isPlayModePinnedAccordionSource")

local chunk = table.concat(lines, "\n", first, stop - 1)
	.. "\nreturn copyAccordionChildRows, inferAccordionDirection, "
	.. "accordionPinPlan, accordionParentsOfChildId, PINNED_OVERLAY_COMMAND_FIELDS\n"

local loadfn = loadstring or load
local copyAccordionChildRows, inferAccordionDirection, accordionPinPlan,
	accordionParentsOfChildId, PINNED_OVERLAY_COMMAND_FIELDS =
	assert(loadfn(chunk, "accordion-pin-extract"))()

local failures = 0
local function check(cond, msg)
	if not cond then
		failures = failures + 1
		print("  FAIL: " .. msg)
	end
end

print("1. LOOK left of MORE → expand left")
check(inferAccordionDirection(100, 50, 20, 50) == "left",
	"LOOK to the left of MORE must infer left")
check(inferAccordionDirection(100, 50, 180, 50) == "right",
	"child to the right infers right")
check(inferAccordionDirection(100, 80, 100, 20) == "up",
	"child above infers up")
check(inferAccordionDirection(100, 20, 100, 80) == "down",
	"child below infers down")
-- Tie on axis: horizontal wins when |dx| >= |dy|.
check(inferAccordionDirection(100, 50, 50, 10) == "left",
	"wider horizontal gap still infers left")

print("2. copyAccordionChildRows does not mutate the prototype table")
local proto = {}
local kids = copyAccordionChildRows(proto)
kids[#kids + 1] = { label = "LOOK", command = "look", id = "b1" }
check(#proto == 0, "appending to the copy must leave the source empty")
check(kids[1].id == "b1", "copy keeps a pin id")

local src = {
	{ label = "LOOK", command = "look" },
	{ label = "SCORE", command = "score", id = "b2" },
}
local copied = copyAccordionChildRows(src)
copied[1].label = "MUTATED"
copied[2].id = "b999"
check(src[1].label == "LOOK", "row tables must be copied, not aliased")
check(src[2].id == "b2", "pin id on the source row must stay")
check(copied[1].id == nil, "snapshot rows have no id")
check(copied[2].id == "b999", "the copy is independent")

print("3. wizard packs still seed label+command with no id")
local server = assert(io.open(SERVER, "r"), "cannot open " .. SERVER)
local body = server:read("*a")
server:close()
check(body:find('{ label = "CON",  command = "consider" }', 1, true) ~= nil,
	"CAST pack still has CON snapshot")
check(body:find('{ label = "LOOK", command = "look" }', 1, true) ~= nil,
	"tutorial ACC still has LOOK snapshot")
local packChildIds = 0
for block in body:gmatch("accordionChildren%s*=%s*(%b{})") do
	if block:match("id%s*=") then
		packChildIds = packChildIds + 1
	end
end
check(packChildIds == 0,
	"pack accordionChildren tables must not mint button ids")

print("4. pin plan: unpin / full / attach; one parent only")
local action, planned = accordionPinPlan(
	{ { label = "LOOK", command = "look", id = "b1" } }, "b1", "LOOK", "look", 20)
check(action == "unpin", "existing id on this parent is unpin, not a second pin")
action, planned = accordionPinPlan({}, "b1", "LOOK", "look", 20)
check(action == "ok" and planned[1].id == "b1", "empty parent accepts a pin")
local twenty = {}
for i = 1, 20 do
	twenty[i] = { label = "x" .. i, command = "y" .. i }
end
action = accordionPinPlan(twenty, "b99", "NEW", "new", 20)
check(action == "full", "21st child without a same-label attach is full")
action, planned = accordionPinPlan(twenty, "b99", "x1", "y1", 20)
check(action == "ok" and planned[1].id == "b99" and #planned == 20,
	"same-label attach onto a full list does not grow it")

buttons = {
	{ data = { label = "MORE", accordionChildren = { { id = "b1", label = "LOOK" } } } },
	{ data = { label = "CAST", accordionChildren = {} } },
}
local parents = accordionParentsOfChildId("b1")
check(#parents == 1 and parents[1].data.label == "MORE",
	"LOOK is pinned only to MORE")
check(#accordionParentsOfChildId("b99") == 0, "unknown id has no parent")
local sawHold, sawSwipe = false, false
for i = 1, #PINNED_OVERLAY_COMMAND_FIELDS do
	if PINNED_OVERLAY_COMMAND_FIELDS[i] == "holdCommand" then sawHold = true end
	if PINNED_OVERLAY_COMMAND_FIELDS[i] == "swipeUpCommand" then sawSwipe = true end
end
check(sawHold and sawSwipe, "pinned overlay must copy hold and swipe")

if failures > 0 then
	print(failures .. " failure(s)")
	os.exit(1)
end
print("ok")
