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

local minX, maxX = 24 * DENSITY, WIDTH - 24 * DENSITY
local minY, maxY = ACTIONBAR + 8 * DENSITY, HEIGHT - 56 * DENSITY

local failures = 0
local function check(cond, msg)
	if not cond then
		failures = failures + 1
		print("  FAIL: " .. msg)
	end
end

local function allReachable(set)
	for _, b in pairs(set) do
		if type(b.x) ~= "number" or type(b.y) ~= "number" then return false end
		if b.x ~= b.x or b.y ~= b.y then return false end
		if b.x < minX or b.x > maxX or b.y < minY or b.y > maxY then return false end
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
check(allReachable(buttonsets.broken), "every tile must land back on screen")

print("3. DEF, the way back, must be reachable")
local def
for _, b in pairs(buttonsets.broken) do
	if b.label == "DEF" then def = b end
end
check(def ~= nil, "DEF still present")
check(def.x >= minX and def.x <= maxX and def.y >= minY and def.y <= maxY,
	"DEF must be tappable, otherwise LOAD cannot be undone")

print("4. nil, NaN and infinities do not slip through")
buttonsets.nasty = {
	{ label = "nan", x = 0 / 0, y = 0 / 0 },
	{ label = "inf", x = math.huge, y = -math.huge },
	{ label = "nil", x = nil, y = nil },
	{ label = "text", x = "notanumber", y = "alsonot" },
}
sanitizeButtonSet("nasty")
check(allReachable(buttonsets.nasty), "no tile may be left unreachable")

print("5. an unknown set name is a no-op, not an error")
local ok = pcall(sanitizeButtonSet, "doesnotexist")
check(ok, "must not raise for a missing set")

print("")
if failures == 0 then
	print("ALL TESTS PASSED")
	os.exit(0)
end
print(failures .. " TEST(S) FAILED")
os.exit(1)
