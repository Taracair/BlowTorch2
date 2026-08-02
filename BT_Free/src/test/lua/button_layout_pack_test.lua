-- Density idempotency for layout packs (rebuild from DP → align once).
--
-- Run:
--     luajit BT_Free/src/test/lua/button_layout_pack_test.lua
--
-- alignButtonSet multiplies DP centres by density in place. Calling it twice
-- without a rebuild compounds coordinates — the same class of bug that sent
-- the tutorial pad to ~1e15. installPack must rebuild from the canonical
-- tables every time; this test locks that contract.

local function scriptDir()
	local path = arg and arg[0] or ""
	return path:match("^(.*)[/\\][^/\\]*$") or "."
end

local SRC = scriptDir() .. "/../../../assets/share/lua/5.1/buttonserver.lua"

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

local loadfn = loadstring or load

local first = findLine("^local PACK_SET_DEFAULTS", "PACK_SET_DEFAULTS")
local stop = findLine("^function persistLayoutOption", "persistLayoutOption")

local DENSITY, WIDTH, HEIGHT, ACTIONBAR = 2.625, 1080, 2400, 96
buttonsets = {}
buttonset_defaults = {}
GetDisplayDensity = function() return DENSITY end
GetActionBarHeight = function() return tostring(ACTIONBAR) end
context = {
	getResources = function()
		return { getDisplayMetrics = function()
			return { widthPixels = WIDTH, heightPixels = HEIGHT }
		end }
	end,
}

-- Locals (PACK_SOURCES, compassDir, …) stay visible through the appended exports.
local body = table.concat(lines, "\n", first, stop - 1)
	.. "\n__TEST_PACK_SOURCES = PACK_SOURCES\n"
	.. "__TEST_PACK_GEOMETRY = packGeometryFor\n"
	.. "__TEST_BOTTOM_CHROME = PACK_BOTTOM_CHROME_DP\n"
	.. "__TEST_THUMB_LIFT = PACK_THUMB_LIFT_DP\n"
	.. "__TEST_HAS_ACCORDION = packHasAccordion\n"

assert(loadfn(body, "pack-extract"))()
assert(type(rebuildPackSet) == "function", "rebuildPackSet missing")
assert(type(alignButtonSet) == "function", "alignButtonSet missing")
assert(type(__TEST_PACK_SOURCES) == "table", "PACK_SOURCES missing")

local sources = __TEST_PACK_SOURCES
local failures = 0
local function check(cond, msg)
	if not cond then
		failures = failures + 1
		print("  FAIL: " .. msg)
	end
end

print("1. rebuild + align once leaves centres in pixel range")
for id, source in pairs(sources) do
	rebuildPackSet(id, source)
	alignButtonSet(id)
	local set = buttonsets[id]
	check(set ~= nil and #set > 0, id .. " has buttons")
	for _, b in ipairs(set) do
		local x, y = tonumber(b.x), tonumber(b.y)
		check(x ~= nil and y ~= nil, id .. " numeric coords")
		check(x > 0 and x < WIDTH and y > 0 and y < HEIGHT,
			id .. " " .. tostring(b.label) .. " on screen after one align")
	end
end

print("2. second align without rebuild compounds (documents density trap)")
rebuildPackSet("compass", sources.compass)
alignButtonSet("compass")
local snap = {}
for i, b in ipairs(buttonsets.compass) do
	snap[i] = { x = b.x, y = b.y }
end
alignButtonSet("compass")
local compounded = false
for i, b in ipairs(buttonsets.compass) do
	if math.abs(b.x - snap[i].x) > 1 or math.abs(b.y - snap[i].y) > 1 then
		compounded = true
		break
	end
end
check(compounded, "second align without rebuild must move coords")

print("3. rebuild then align is idempotent (installPack contract)")
rebuildPackSet("compass", sources.compass)
alignButtonSet("compass")
local after = {}
for i, b in ipairs(buttonsets.compass) do
	after[i] = { x = b.x, y = b.y }
end
rebuildPackSet("compass", sources.compass)
alignButtonSet("compass")
for i, b in ipairs(buttonsets.compass) do
	check(math.abs(b.x - after[i].x) < 1 and math.abs(b.y - after[i].y) < 1,
		"rebuild+align is idempotent for " .. tostring(b.label))
end

-- Everything below locks the 2 Aug fixes: the wizard shipped 72dp tiles on a
-- 45dp lattice (solid overlapping slab, right column off screen) and let the
-- window re-flow the pads into reading order (compass directions no longer
-- matching the tile positions).

local geometryFor = __TEST_PACK_GEOMETRY
-- Pad bottom lands this far above the screen bottom: input-bar chrome plus the
-- thumb lift, which is also the room a bottom-row accordion opens into.
local BOTTOM_ALLOWANCE = __TEST_BOTTOM_CHROME + __TEST_THUMB_LIFT
local PRESETS = { "compact", "comfortable", "large", "xl", "fit_square" }

print("4. every preset gives a lattice at least as wide as the tile")
for id, source in pairs(sources) do
	for _, preset in ipairs(PRESETS) do
		local size, pitch = geometryFor(source, preset)
		check(size >= 16, id .. "/" .. preset .. " tile >= 16dp (got " .. size .. ")")
		check(pitch >= size, id .. "/" .. preset
			.. " pitch " .. pitch .. " must not be under tile " .. size)
	end
end

print("5. the whole pad fits the screen at every preset")
for id, source in pairs(sources) do
	for _, preset in ipairs(PRESETS) do
		local size, pitch = geometryFor(source, preset)
		rebuildPackSet(id, source, pitch, size)
		alignButtonSet(id, "right", "bottom")
		local half = (size * DENSITY) / 2
		for _, b in ipairs(buttonsets[id]) do
			check(b.x - half >= -1 and b.x + half <= WIDTH + 1,
				id .. "/" .. preset .. " " .. tostring(b.label) .. " within screen width")
			check(b.y - half >= -1 and b.y + half <= HEIGHT + 1,
				id .. "/" .. preset .. " " .. tostring(b.label) .. " within screen height")
		end
	end
end

print("6. no two tiles of a pack overlap at any preset")
for id, source in pairs(sources) do
	for _, preset in ipairs(PRESETS) do
		local size, pitch = geometryFor(source, preset)
		rebuildPackSet(id, source, pitch, size)
		alignButtonSet(id, "right", "bottom")
		local set = buttonsets[id]
		local side = size * DENSITY
		local clash = nil
		for i = 1, #set do
			for j = i + 1, #set do
				local dx = math.abs(set[i].x - set[j].x)
				local dy = math.abs(set[i].y - set[j].y)
				-- Axis-aligned squares miss when they are clear on either axis.
				if dx < side - 0.5 and dy < side - 0.5 then
					clash = tostring(set[i].label) .. "/" .. tostring(set[j].label)
				end
			end
		end
		check(clash == nil, id .. "/" .. preset .. " tiles overlap: " .. tostring(clash))
	end
end

print("7. compass rose keeps its bearings at every preset")
-- The bug the screenshots caught: N ended up left of NE and S jumped onto the
-- second row, because tidyButtonLayout re-flowed the set by reading order.
local function tileByLabel(setName, label)
	for _, b in ipairs(buttonsets[setName]) do
		if tostring(b.label) == label then return b end
	end
end
for _, preset in ipairs(PRESETS) do
	local size, pitch = geometryFor(sources.compass, preset)
	rebuildPackSet("compass", sources.compass, pitch, size)
	alignButtonSet("compass", "right", "bottom")
	local nw, n, ne = tileByLabel("compass", "NW"), tileByLabel("compass", "N"), tileByLabel("compass", "NE")
	local w, e = tileByLabel("compass", "W"), tileByLabel("compass", "E")
	local sw, s, se = tileByLabel("compass", "SW"), tileByLabel("compass", "S"), tileByLabel("compass", "SE")
	check(nw and n and ne and w and e and sw and s and se, preset .. ": all eight bearings present")
	if nw and n and ne and w and e and sw and s and se then
		check(nw.x < n.x and n.x < ne.x, preset .. ": NW left of N left of NE")
		check(sw.x < s.x and s.x < se.x, preset .. ": SW left of S left of SE")
		check(w.x < e.x, preset .. ": W left of E")
		check(n.y < s.y, preset .. ": N above S")
		check(nw.y < w.y and w.y < sw.y, preset .. ": NW above W above SW")
		check(math.abs(n.y - ne.y) < 1 and math.abs(n.y - nw.y) < 1,
			preset .. ": north row is level")
		check(math.abs(n.x - s.x) < 1, preset .. ": N and S share a column")
	end
end

print("8. bottom anchor puts the pad in thumb reach, top anchor does not")
local size, pitch = geometryFor(sources.compass, "comfortable")
rebuildPackSet("compass", sources.compass, pitch, size)
alignButtonSet("compass", "right", "bottom")
local lowest = 0
for _, b in ipairs(buttonsets.compass) do
	if b.y > lowest then lowest = b.y end
end
check(lowest > HEIGHT * 0.6,
	"bottom-anchored pad reaches past 60% of the screen (got "
	.. math.floor(lowest / HEIGHT * 100) .. "%)")
check(lowest <= HEIGHT - BOTTOM_ALLOWANCE * DENSITY + 1,
	"bottom-anchored pad still clears the input bar")

-- alignButtonSet clamps min-top last, so a pad too tall for the screen would
-- silently ride back up while the "past 60%" check above still passed. Assert
-- the bottom edge really lands on the chrome line for every pack and preset.
for id, source in pairs(sources) do
	for _, preset in ipairs(PRESETS) do
		local ps, pp = geometryFor(source, preset)
		rebuildPackSet(id, source, pp, ps)
		alignButtonSet(id, "right", "bottom")
		local edge = 0
		for _, b in ipairs(buttonsets[id]) do
			local e = b.y + (ps * DENSITY) / 2
			if e > edge then edge = e end
		end
		check(math.abs(edge - (HEIGHT - BOTTOM_ALLOWANCE * DENSITY)) < 1,
			id .. "/" .. preset .. " bottom edge sits on the chrome line, not clamped up")
	end
end

rebuildPackSet("compass", sources.compass, pitch, size)
alignButtonSet("compass", "right")
local topLowest = 0
for _, b in ipairs(buttonsets.compass) do
	if b.y > topLowest then topLowest = b.y end
end
check(topLowest < lowest, "default (starter/tutorial) placement stays higher than bottom anchor")

print("9. resizing repeatedly does not compound coordinates")
-- rebuildPackSet always re-derives from the canonical table, so going
-- comfortable → xl → comfortable must land exactly where comfortable started.
local s1, p1 = geometryFor(sources.compass, "comfortable")
rebuildPackSet("compass", sources.compass, p1, s1)
alignButtonSet("compass", "right", "bottom")
local baseline = {}
for i, b in ipairs(buttonsets.compass) do baseline[i] = { x = b.x, y = b.y } end
local s2, p2 = geometryFor(sources.compass, "xl")
rebuildPackSet("compass", sources.compass, p2, s2)
alignButtonSet("compass", "right", "bottom")
rebuildPackSet("compass", sources.compass, p1, s1)
alignButtonSet("compass", "right", "bottom")
for i, b in ipairs(buttonsets.compass) do
	check(math.abs(b.x - baseline[i].x) < 1 and math.abs(b.y - baseline[i].y) < 1,
		"round trip through xl returns " .. tostring(b.label) .. " to its place")
end

print("10. accordions open downward into the gap, not over the pad")
-- Every pack accordion sits on the pack's last row. With the pad anchored near
-- the bottom, the old "up" default unfolded its children straight over the
-- compass rose. Down + horizontal puts one row in the thumb-lift gap instead.
for id, source in pairs(sources) do
	for _, preset in ipairs(PRESETS) do
		local size, pitch = geometryFor(source, preset)
		rebuildPackSet(id, source, pitch, size)
		alignButtonSet(id, "right", "bottom")
		local set = buttonsets[id]
		local lowest = 0
		for _, b in ipairs(set) do
			if b.y > lowest then lowest = b.y end
		end
		for _, b in ipairs(set) do
			if type(b.accordionChildren) == "table" and #b.accordionChildren > 0 then
				check(b.accordionDirection == "down",
					id .. "/" .. preset .. " " .. tostring(b.label) .. " opens down")
				check(b.accordionChildLayout == "horizontal",
					id .. "/" .. preset .. " " .. tostring(b.label) .. " opens in one row")
				check(math.abs(b.y - lowest) < 1,
					id .. "/" .. preset .. " " .. tostring(b.label)
					.. " is on the pack's last row (nothing of the pad below it)")
				-- One child row, gap included, has to land above the input bar.
				local childEdge = b.y + (size * DENSITY) / 2
					+ 3 * DENSITY + size * DENSITY
				check(childEdge <= HEIGHT - __TEST_BOTTOM_CHROME * DENSITY + 1,
					id .. "/" .. preset .. " " .. tostring(b.label)
					.. " opened row clears the input bar")
			end
		end
	end
end

print("11. packs with an accordion never out-grow the gap it opens into")
for id, source in pairs(sources) do
	if __TEST_HAS_ACCORDION(source) then
		for _, preset in ipairs(PRESETS) do
			local _, pitch = geometryFor(source, preset)
			check(pitch <= __TEST_THUMB_LIFT,
				id .. "/" .. preset .. " pitch " .. pitch
				.. " must fit the " .. __TEST_THUMB_LIFT .. "dp accordion gap")
		end
	end
end

if failures > 0 then
	print(failures .. " failure(s)")
	os.exit(1)
end
print("OK")
