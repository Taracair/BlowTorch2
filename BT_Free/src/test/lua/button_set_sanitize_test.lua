-- Regression test for sanitizeButtonSet.
--
-- Run it with:
--     luajit BT_Free/src/test/lua/button_set_sanitize_test.lua
--
-- The tutorial pad once drifted to coordinates around 1e15, because
-- alignDefaultButtons scales dp to pixels in place and was applied to a set that
-- was never rebuilt from dp. Nearly every tile ended up off screen, including the
-- DEF button that leads back, so LOAD could not be undone.
--
-- The rebuild fixes the cause. This covers the safety net: whatever a set's
-- coordinates are, loading it must leave every tile somewhere reachable.
--
-- The function body is pulled out of buttonserver.lua so the test cannot drift
-- from the shipped source. Host globals it touches are stubbed.

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

local first = findLine("^function sanitizeButtonSet", "sanitizeButtonSet")
local stop = findLine("^function alignDefaultButtons", "alignDefaultButtons")

-- Stubs for the host side: a 1080x2400 screen at density 2.625, like a Pixel 9a.
local DENSITY, WIDTH, HEIGHT, ACTIONBAR = 2.625, 1080, 2400, 96
buttonsets = {}
-- sanitizeButtonSet reads this for the per-set fallback tile size. It is a
-- module-level table in buttonserver.lua (never nil there), so the harness has
-- to provide one too or the extracted body indexes nil.
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

assert(loadstring(table.concat(lines, "\n", first, stop - 1), "sanitize-extract"))()
assert(type(sanitizeButtonSet) == "function", "extraction failed")

-- The bound is half the tile's own size, not a flat inset: a 72dp tile pinned to
-- a 24dp inset still hangs 12dp off the edge, which is what clipped the right
-- column at Extra large. So the acceptance window is per button, and it is wider
-- for a small tile than for a big one — mirror that here rather than hardcoding.
local DEFAULT_TILE_DP = 42

local function tileSize(b, setName)
	local defs = buttonset_defaults[setName]
	local bw = tonumber(b.width) or tonumber(defs and defs.width) or DEFAULT_TILE_DP
	local bh = tonumber(b.height) or tonumber(defs and defs.height) or DEFAULT_TILE_DP
	return bw * DENSITY, bh * DENSITY
end

local function boundsFor(b, setName)
	local bw, bh = tileSize(b, setName)
	local minX, maxX = bw / 2, WIDTH - bw / 2
	local minY, maxY = ACTIONBAR + 8 * DENSITY + bh / 2, HEIGHT - 56 * DENSITY - bh / 2
	if maxX < minX then maxX = minX end
	if maxY < minY then maxY = minY end
	return minX, maxX, minY, maxY
end

local failures = 0
local function check(cond, msg)
	if not cond then
		failures = failures + 1
		print("  FAIL: " .. msg)
	end
end

local function reachable(b, setName)
	if type(b.x) ~= "number" or type(b.y) ~= "number" then return false end
	if b.x ~= b.x or b.y ~= b.y then return false end
	local minX, maxX, minY, maxY = boundsFor(b, setName)
	return b.x >= minX and b.x <= maxX and b.y >= minY and b.y <= maxY
end

local function allReachable(set, setName)
	for _, b in pairs(set) do
		if not reachable(b, setName) then return false end
	end
	return true
end

print("1. a sane pad is left exactly as it is")
buttonsets.sane = {
	{ label = "N", x = 540, y = 300 },
	{ label = "S", x = 540, y = 500 },
	{ label = "E", x = 700, y = 400 },
}
local before = {}
for i, b in ipairs(buttonsets.sane) do before[i] = { x = b.x, y = b.y } end
local moved = sanitizeButtonSet("sane")
check(moved == false, "a pad already on screen must report no change")
for i, b in ipairs(buttonsets.sane) do
	check(b.x == before[i].x and b.y == before[i].y,
		"tile " .. i .. " must not be nudged")
end

print("2. the real corruption: coordinates around 1e15")
buttonsets.broken = {
	{ label = "START", x = 3.0512631862281e+15, y = -9.1537895586816e+15 },
	{ label = "TOPICS", x = 6.102526372456e+15, y = -9.1537895586816e+15 },
	{ label = "DEF", x = 81.375, y = -6.1025263724541e+15 },
}
check(sanitizeButtonSet("broken") == true, "a broken pad must report that it moved")
check(allReachable(buttonsets.broken, "broken"), "every tile must land back on screen")

print("3. DEF, the way back, must be reachable")
local def
for _, b in pairs(buttonsets.broken) do
	if b.label == "DEF" then def = b end
end
check(def ~= nil, "DEF still present")
check(reachable(def, "broken"),
	"DEF must be tappable, otherwise LOAD cannot be undone")

print("4. nil, NaN and infinities do not slip through")
buttonsets.nasty = {
	{ label = "nan", x = 0 / 0, y = 0 / 0 },
	{ label = "inf", x = math.huge, y = -math.huge },
	{ label = "nil", x = nil, y = nil },
	{ label = "text", x = "notanumber", y = "alsonot" },
}
sanitizeButtonSet("nasty")
check(allReachable(buttonsets.nasty, "nasty"), "no tile may be left unreachable")

print("5. an unknown set name is a no-op, not an error")
local ok = pcall(sanitizeButtonSet, "doesnotexist")
check(ok, "must not raise for a missing set")

print("6. a big tile is bounded by half its own size, not a flat inset")
-- The Extra large regression: a 72dp tile parked past the right edge used to be
-- pinned by a 24dp inset on its *centre*, leaving 12dp of it off screen.
buttonsets.big = {
	{ label = "INV", width = 72, height = 72, x = 99999, y = 99999 },
	{ label = "NAV", width = 72, height = 72, x = -99999, y = -99999 },
}
check(sanitizeButtonSet("big") == true, "off-screen tiles must report that they moved")
check(allReachable(buttonsets.big, "big"), "a 72dp tile must sit wholly on screen")
for _, b in pairs(buttonsets.big) do
	local half = 72 * DENSITY / 2
	check(b.x - half >= -0.001 and b.x + half <= WIDTH + 0.001,
		b.label .. ": no part of the tile may hang off the left or right edge")
end

print("7. the set's own default size is used when a tile carries none")
-- A tile with no width of its own falls back to buttonset_defaults[set], and
-- only then to 42dp. Without this the fallback chain is never exercised.
buttonset_defaults.sized = { width = 72, height = 72 }
buttonsets.sized = { { label = "BIG", x = 99999, y = 300 } }
sanitizeButtonSet("sized")
local big = buttonsets.sized[1]
check(math.abs(big.x - (WIDTH - 72 * DENSITY / 2)) < 0.001,
	"a tile sized by the set default must be clamped by that size, not by 42dp")

print("8. a tile wider than the screen is pinned, not left with maxX < minX")
buttonsets.huge = { { label = "SLAB", width = 9999, height = 9999, x = 0, y = 0 } }
local okHuge = pcall(sanitizeButtonSet, "huge")
check(okHuge, "an absurd tile size must not raise")
local slab = buttonsets.huge[1]
check(type(slab.x) == "number" and slab.x == slab.x, "x must still be a real number")
check(type(slab.y) == "number" and slab.y == slab.y, "y must still be a real number")

print("")
if failures == 0 then
	print("ALL TESTS PASSED")
	os.exit(0)
end
print(failures .. " TEST(S) FAILED")
os.exit(1)
