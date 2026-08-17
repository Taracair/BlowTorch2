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
	.. "__TEST_TOP_PAD = PACK_TOP_PAD_DP\n"
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
-- Wizard pads are anchored by their TOP edge, this far under the action bar.
-- Bottom-anchoring put them behind the soft keyboard on the device.
local TOP_PAD = __TEST_TOP_PAD
-- With the keyboard up the visible game area on the maintainer's Pixel 9a ends
-- about here (measured: 1272px of 2424). A pad at a normal preset has to sit
-- entirely above it.
local KEYBOARD_LINE = HEIGHT * 0.525
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
		alignButtonSet(id, "right", "pack")
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
		alignButtonSet(id, "right", "pack")
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
	alignButtonSet("compass", "right", "pack")
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
	local look = tileByLabel("compass", "LOOK")
	local inv = tileByLabel("compass", "INV")
	check(look and inv and se, preset .. ": LOOK/INV/SE present")
	if look and inv and se then
		check(look.x > inv.x, preset .. ": LOOK is right of INV (rose is on the right)")
		check(look.y > inv.y, preset .. ": LOOK is below INV (rose is at the bottom)")
		check(se.x >= look.x - 1, preset .. ": SE is at the right of the rose")
	end
end

print("8. pack anchor sits under the action bar, clear of the soft keyboard")
-- The regression this replaces: the pad was anchored to the BOTTOM of the
-- screen, which on the device put it behind the keyboard. Anchor is now the top
-- edge, PACK_TOP_PAD_DP under the action bar.
for id, source in pairs(sources) do
	for _, preset in ipairs(PRESETS) do
		local ps, pp = geometryFor(source, preset)
		rebuildPackSet(id, source, pp, ps)
		alignButtonSet(id, "right", "pack")
		local topEdge, bottomEdge = math.huge, 0
		for _, b in ipairs(buttonsets[id]) do
			local t = b.y - (ps * DENSITY) / 2
			local e = b.y + (ps * DENSITY) / 2
			if t < topEdge then topEdge = t end
			if e > bottomEdge then bottomEdge = e end
		end
		check(math.abs(topEdge - (ACTIONBAR + TOP_PAD * DENSITY)) < 1,
			id .. "/" .. preset .. " top edge sits " .. TOP_PAD
			.. "dp under the action bar (got " .. math.floor(topEdge) .. "px)")
		-- Named presets must be entirely usable with the keyboard up. Fit to
		-- screen deliberately grows past that: it is asked to fill the width.
		if preset ~= "fit_square" then
			check(bottomEdge <= KEYBOARD_LINE,
				id .. "/" .. preset .. " whole pad clears the keyboard ("
				.. math.floor(bottomEdge) .. "px vs line at "
				.. math.floor(KEYBOARD_LINE) .. "px)")
		end
		-- And it must never reach the input bar, keyboard or not.
		check(bottomEdge <= HEIGHT - __TEST_BOTTOM_CHROME * DENSITY + 1,
			id .. "/" .. preset .. " pad clears the input bar")
	end
end

-- The offline starter/tutorial placement is a separate mode and must not have
-- moved: alignDefaultButtons shares this function with the teaching pad.
local size, pitch = geometryFor(sources.compass, "comfortable")
rebuildPackSet("compass", sources.compass, pitch, size)
alignButtonSet("compass", "right", "pack")
local packTop = math.huge
for _, b in ipairs(buttonsets.compass) do
	if b.y < packTop then packTop = b.y end
end
rebuildPackSet("compass", sources.compass, pitch, size)
alignButtonSet("compass", "right")
local legacyTop = math.huge
for _, b in ipairs(buttonsets.compass) do
	if b.y < legacyTop then legacyTop = b.y end
end
check(legacyTop > packTop,
	"legacy (starter/tutorial) placement is still the lower of the two")

print("9. resizing repeatedly does not compound coordinates")
-- rebuildPackSet always re-derives from the canonical table, so going
-- comfortable → xl → comfortable must land exactly where comfortable started.
local s1, p1 = geometryFor(sources.compass, "comfortable")
rebuildPackSet("compass", sources.compass, p1, s1)
alignButtonSet("compass", "right", "pack")
local baseline = {}
for i, b in ipairs(buttonsets.compass) do baseline[i] = { x = b.x, y = b.y } end
local s2, p2 = geometryFor(sources.compass, "xl")
rebuildPackSet("compass", sources.compass, p2, s2)
alignButtonSet("compass", "right", "pack")
rebuildPackSet("compass", sources.compass, p1, s1)
alignButtonSet("compass", "right", "pack")
for i, b in ipairs(buttonsets.compass) do
	check(math.abs(b.x - baseline[i].x) < 1 and math.abs(b.y - baseline[i].y) < 1,
		"round trip through xl returns " .. tostring(b.label) .. " to its place")
end

print("10. accordions open downward, below the pad, clear of the input bar")
-- Every pack accordion sits on the pack's last row, so "up" (the old default,
-- stacking children vertically) unfolded straight over the compass rose. Down +
-- horizontal puts a single row in the empty game area beneath the pad.
for id, source in pairs(sources) do
	for _, preset in ipairs(PRESETS) do
		local size, pitch = geometryFor(source, preset)
		rebuildPackSet(id, source, pitch, size)
		alignButtonSet(id, "right", "pack")
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

print("11. geometry reserves a row for the accordion to open into")
for id, source in pairs(sources) do
	if __TEST_HAS_ACCORDION(source) then
		local _, rows = nil, 0
		local ys = {}
		for _, b in ipairs(source) do
			if b.y ~= nil and not ys[b.y] then ys[b.y] = true; rows = rows + 1 end
		end
		for _, preset in ipairs(PRESETS) do
			local ps, pp = geometryFor(source, preset)
			rebuildPackSet(id, source, pp, ps)
			alignButtonSet(id, "right", "pack")
			local bottomEdge = 0
			for _, b in ipairs(buttonsets[id]) do
				local e = b.y + (ps * DENSITY) / 2
				if e > bottomEdge then bottomEdge = e end
			end
			-- Pad plus one opened row still above the input bar.
			check(bottomEdge + pp * DENSITY <= HEIGHT - __TEST_BOTTOM_CHROME * DENSITY + 1,
				id .. "/" .. preset .. " leaves a row (" .. pp
				.. "dp) for the accordion above the input bar")
		end
	end
end

print("12. a poisoned GetActionBarHeight cannot push a pad off the top")
-- Measured on the device: the launcher wrote TITLE_BAR_HEIGHT as
-- contentViewTop - statusBarHeight, which under the NoActionBar theme is minus
-- the status bar height. :stellar read it once at Connection construction and
-- handed it to Lua as GetActionBarHeight() = -152, so the pack anchor
-- (ab + 35dp) put the first row 60px above the top of the screen — and
-- sanitizeButtonSet's bounds were computed from the same number, so nothing
-- caught it. Whether a profile broke depended on which activity wrote the pref
-- last. The launcher is fixed; this locks the Lua-side guard.
local realActionBar = GetActionBarHeight
for _, poison in ipairs({ "-152", "0", "-1", "99999", "not a number" }) do
	GetActionBarHeight = function() return poison end
	for id, source in pairs(sources) do
		local ps, pp = geometryFor(source, "comfortable")
		rebuildPackSet(id, source, pp, ps)
		alignButtonSet(id, "right", "pack")
		local topEdge = math.huge
		for _, b in ipairs(buttonsets[id]) do
			local t = b.y - (ps * DENSITY) / 2
			if t < topEdge then topEdge = t end
		end
		check(topEdge >= -1,
			"ab=" .. poison .. ": " .. id .. " top row stays on screen (got "
			.. math.floor(topEdge) .. "px)")
		check(topEdge < HEIGHT * 0.5,
			"ab=" .. poison .. ": " .. id .. " pad is not shoved into the lower half")
	end
end
GetActionBarHeight = realActionBar

if failures > 0 then
	print(failures .. " failure(s)")
	os.exit(1)
end
print("OK")
