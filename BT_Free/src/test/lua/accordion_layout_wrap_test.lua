-- Accordion child layout wrapping (up to MAX_ACCORDION_CHILDREN).
--
-- Run:
--     lua5.1 BT_Free/src/test/lua/accordion_layout_wrap_test.lua
--
-- Twenty children in a line run off the screen; computeAccordionChildCentres
-- wraps into the next lane. Slots that cannot fit without stacking are dropped
-- (buildAccordionOverlay Notes the player) — centres must never coincide.

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

local first = findLine("^MAX_ACCORDION_CHILDREN =", "MAX_ACCORDION_CHILDREN")
local stop = findLine("^function collapseAccordion", "collapseAccordion")

local chunk = table.concat(lines, "\n", first, stop - 1)
	.. "\nreturn MAX_ACCORDION_CHILDREN, computeAccordionChildCentres, "
	.. "accordionWrapAfterForLanes\n"

local loadfn = loadstring or load
local MAX_ACCORDION_CHILDREN, computeAccordionChildCentres,
	accordionWrapAfterForLanes =
	assert(loadfn(chunk, "accordion-layout-extract"))()

local failures = 0
local function check(cond, msg)
	if not cond then
		failures = failures + 1
		print("  FAIL: " .. msg)
	end
end

local DENSITY = 2.0
local TILE = 48
local VIEW_W, VIEW_H = 1080, 1920
local STATUS = 0

local function childHalf()
	return (TILE / 2) * DENSITY
end

local function onScreen(c, viewW, viewH)
	viewW = viewW or VIEW_W
	viewH = viewH or VIEW_H
	local half = childHalf()
	return c.x - half >= -0.01
		and c.x + half <= viewW + 0.01
		and c.y - half + STATUS >= -0.01
		and c.y + half + STATUS <= viewH + 0.01
end

local function centresUnique(centres)
	for i = 1, #centres do
		for j = i + 1, #centres do
			if math.abs(centres[i].x - centres[j].x) < 0.51
					and math.abs(centres[i].y - centres[j].y) < 0.51 then
				return false, i, j
			end
		end
	end
	return true
end

print("1. MAX_ACCORDION_CHILDREN is 20")
check(MAX_ACCORDION_CHILDREN == 20, "limit is 20, got " .. tostring(MAX_ACCORDION_CHILDREN))

print("2. five children down/along stay in one column (no wrap needed)")
local centres = computeAccordionChildCentres(
	540, 400, TILE, TILE, "down", "along", 5, TILE, TILE, DENSITY,
	VIEW_W, VIEW_H, STATUS)
check(#centres == 5, "5 centres")
for i = 2, 5 do
	check(math.abs(centres[i].x - centres[1].x) < 0.01,
		"column shares x at index " .. i)
	check(centres[i].y > centres[i - 1].y, "descending y at index " .. i)
end

print("3. twenty children down/along wrap and stay on screen")
-- Parent high enough that wrapping sideways can host all 20; near the bottom
-- edge the view simply cannot hold 20 full-size tiles (that case is test 6/7).
centres = computeAccordionChildCentres(
	540, 1200, TILE, TILE, "down", "along", 20, TILE, TILE, DENSITY,
	VIEW_W, VIEW_H, STATUS)
check(#centres == 20, "20 centres, got " .. #centres)
local wrapped = false
for i = 1, #centres do
	check(onScreen(centres[i]), string.format("child %d on screen (%.1f,%.1f)",
		i, centres[i].x, centres[i].y))
	if i > 1 and math.abs(centres[i].x - centres[1].x) > 1 then
		wrapped = true
	end
end
check(wrapped, "expected at least one wrapped column")

print("4. twenty children right/along wrap vertically and stay on screen")
centres = computeAccordionChildCentres(
	700, 960, TILE, TILE, "right", "along", 20, TILE, TILE, DENSITY,
	VIEW_W, VIEW_H, STATUS)
check(#centres == 20, "20 centres right, got " .. #centres)
wrapped = false
for i = 1, #centres do
	check(onScreen(centres[i]), string.format("right child %d on screen (%.1f,%.1f)",
		i, centres[i].x, centres[i].y))
	if i > 1 and math.abs(centres[i].y - centres[1].y) > 1 then
		wrapped = true
	end
end
check(wrapped, "expected wrap into another row")

print("5. horizontal under down centres the first row when it fits")
centres = computeAccordionChildCentres(
	540, 400, TILE, TILE, "down", "horizontal", 3, TILE, TILE, DENSITY,
	VIEW_W, VIEW_H, STATUS)
check(#centres == 3, "3 centres horizontal")
local mid = (centres[1].x + centres[3].x) * 0.5
check(math.abs(mid - 540) < 1.0, "first row centred on parent")
check(math.abs(centres[1].y - centres[2].y) < 0.01, "same row y")

print("6. cramped view drops overflow instead of stacking")
local smallW, smallH = 360, 360
centres = computeAccordionChildCentres(
	180, 100, TILE, TILE, "down", "along", 20, TILE, TILE, DENSITY,
	smallW, smallH, STATUS)
check(#centres < 20, "must drop some children in a cramped view, got " .. #centres)
check(#centres >= 1, "still places at least one when room exists, got " .. #centres)
for i = 1, #centres do
	check(onScreen(centres[i], smallW, smallH),
		string.format("cramped child %d on screen (%.1f,%.1f)", i, centres[i].x, centres[i].y))
end
local uniq, a, b = centresUnique(centres)
check(uniq, string.format("cramped centres must be unique (collision %s vs %s)",
	tostring(a), tostring(b)))

print("8. bottom-edge down accordion still shows children")
-- Advisor measured 0 centres here before the clamp-first-slot fix.
centres = computeAccordionChildCentres(
	540, 1800, TILE, TILE, "down", "along", 3, TILE, TILE, DENSITY,
	VIEW_W, VIEW_H, STATUS)
check(#centres >= 1, "bottom edge must show at least one child, got " .. #centres)
check(#centres == 3, "bottom edge should place all 3 sideways, got " .. #centres)
for i = 1, #centres do
	check(onScreen(centres[i]), string.format("bottom child %d on screen", i))
end
uniq, a, b = centresUnique(centres)
check(uniq, "bottom-edge centres unique")

print("9. near-edge horizontal fan packs one row when it fits")
-- Advisor measured 3+3 wrap although all six fit from x≈48.
centres = computeAccordionChildCentres(
	60, 400, TILE, TILE, "down", "horizontal", 6, TILE, TILE, DENSITY,
	VIEW_W, VIEW_H, STATUS)
check(#centres == 6, "six children placed, got " .. #centres)
local rowY = centres[1].y
local oneRow = true
for i = 2, #centres do
	if math.abs(centres[i].y - rowY) > 0.51 then
		oneRow = false
	end
end
check(oneRow, "all six on one row near left edge")
uniq, a, b = centresUnique(centres)
check(uniq, "near-edge horizontal centres unique")

print("10. lane order is consistent (preferred side, then the other)")
centres = computeAccordionChildCentres(
	540, 1200, TILE, TILE, "down", "along", 20, TILE, TILE, DENSITY,
	VIEW_W, VIEW_H, STATUS)
local laneOrder = {}
local seen = {}
for i = 1, #centres do
	local key = string.format("%.0f", centres[i].x)
	if not seen[key] then
		seen[key] = true
		laneOrder[#laneOrder + 1] = centres[i].x
	end
end
check(#laneOrder >= 2, "expected wrapped lanes")
check(math.abs(laneOrder[1] - 540) < 1, "first lane is parent column")
-- Old bug: 540, 642, 438, 744 (alternating). Preferred side must run out
-- before the opposite side appears.
local preferredSign = (laneOrder[2] > laneOrder[1]) and 1 or -1
local sawOpposite = false
for i = 2, #laneOrder do
	local onPreferred = (laneOrder[i] - laneOrder[1]) * preferredSign > 0
	if not onPreferred then
		sawOpposite = true
	elseif sawOpposite then
		check(false, "preferred-side lane after opposite side (alternating)")
	end
end
if #laneOrder >= 3 and not sawOpposite then
	-- All on one side: must be monotonic away from parent.
	for i = 3, #laneOrder do
		check((laneOrder[i] - laneOrder[i - 1]) * preferredSign > 0,
			"preferred side not monotonic at lane " .. i)
	end
end

print("7. no two centres coincide (corner cases up to 20)")
local dirs = { "down", "up", "left", "right" }
local layouts = { "along", "vertical", "horizontal" }
local parents = {
	{ 60, 60 }, { 220, 40 }, { 40, 220 }, { 200, 200 }, { 100, 150 },
}
local tinyW, tinyH = 320, 320
for _, dir in ipairs(dirs) do
	for _, layout in ipairs(layouts) do
		for _, parent in ipairs(parents) do
			for count = 1, 20 do
				centres = computeAccordionChildCentres(
					parent[1], parent[2], TILE, TILE, dir, layout, count,
					TILE, TILE, DENSITY, tinyW, tinyH, STATUS)
				check(#centres <= count,
					string.format("%s/%s@%d,%d n=%d returned too many",
						dir, layout, parent[1], parent[2], count))
				-- View can host a 48dp tile: always at least one child.
				check(#centres >= 1,
					string.format("%s/%s@%d,%d n=%d expected >=1 centre, got %d",
						dir, layout, parent[1], parent[2], count, #centres))
				for i = 1, #centres do
					check(onScreen(centres[i], tinyW, tinyH),
						string.format("%s/%s@%d,%d n=%d child %d off screen",
							dir, layout, parent[1], parent[2], count, i))
				end
				uniq, a, b = centresUnique(centres)
				check(uniq, string.format(
					"%s/%s@%d,%d n=%d collision %s vs %s",
					dir, layout, parent[1], parent[2], count,
					tostring(a), tostring(b)))
			end
		end
	end
end

print("8. wrapAfter 3 with 7 children expand-up along → 3+3+1 packed lanes")
centres = computeAccordionChildCentres(
	540, 1200, TILE, TILE, "up", "along", 7, TILE, TILE, DENSITY,
	VIEW_W, VIEW_H, STATUS, 3)
check(#centres == 7, "wrapAfter 3 should still place all 7 on a tall view, got "
	.. tostring(#centres))
uniq, a, b = centresUnique(centres)
check(uniq, "wrapAfter 3 collision")
local xs, ys = {}, {}
for i = 1, #centres do
	local xk = string.format("%.0f", centres[i].x)
	local yk = string.format("%.0f", centres[i].y)
	xs[xk] = (xs[xk] or 0) + 1
	ys[yk] = (ys[yk] or 0) + 1
end
local laneCount, firstLane = 0, 0
for _, n in pairs(xs) do
	laneCount = laneCount + 1
	if n > firstLane then firstLane = n end
end
check(laneCount == 3, "expected 3 columns, got " .. tostring(laneCount))
check(firstLane == 3, "longest column should be 3, got " .. tostring(firstLane))
-- wrapAfter 0 keeps today's "as many as fit" on one lane when the view is tall
centres = computeAccordionChildCentres(
	540, 400, TILE, TILE, "down", "along", 7, TILE, TILE, DENSITY,
	VIEW_W, VIEW_H, STATUS, 0)
local xs0 = {}
for i = 1, #centres do
	xs0[string.format("%.0f", centres[i].x)] = true
end
local lanes0 = 0
for _ in pairs(xs0) do lanes0 = lanes0 + 1 end
check(lanes0 == 1, "wrapAfter 0 down+along on a tall view is one column, got "
	.. tostring(lanes0))

print("9. type 2 in Columns → ten children in two columns of five")
check(accordionWrapAfterForLanes(10, 2) == 5, "10 children / 2 lanes → wrapAfter 5")
check(accordionWrapAfterForLanes(10, 0) == 0, "blank lanes stays auto")
check(accordionWrapAfterForLanes(10, 1) == 0, "1 lane is auto, not a forced wrap")
centres = computeAccordionChildCentres(
	540, 400, TILE, TILE, "down", "along", 10, TILE, TILE, DENSITY,
	VIEW_W, VIEW_H, STATUS, accordionWrapAfterForLanes(10, 2))
check(#centres == 10, "two columns still place all 10 on a tall view, got "
	.. tostring(#centres))
local xs2 = {}
for i = 1, #centres do
	local key = string.format("%.0f", centres[i].x)
	xs2[key] = (xs2[key] or 0) + 1
end
local lane2 = 0
for _ in pairs(xs2) do lane2 = lane2 + 1 end
check(lane2 == 2, "expected 2 columns, got " .. tostring(lane2))
for _, n in pairs(xs2) do
	check(n == 5, "each column should hold 5, got " .. tostring(n))
end

if failures > 0 then
	print(string.format("FAILED (%d)", failures))
	os.exit(1)
end
print("ok")
