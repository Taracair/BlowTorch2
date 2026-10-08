-- Button heatmap encode, scale, and the world-prefixed payload.
--
-- Run:
--     lua5.1 BT_Free/src/test/lua/button_heat_test.lua

local function scriptDir()
	local path = arg and arg[0] or ""
	local dir = path:match("^(.*)[/\\][^/\\]*$")
	return dir or "."
end

local SRC = scriptDir() .. "/../../../assets/share/lua/5.1/buttonheat.lua"
local loadfn = loadstring or load
local chunk = assert(loadfile(SRC))
local heat = chunk()

local failures = 0
local function check(cond, msg)
	if not cond then
		failures = failures + 1
		print("  FAIL: " .. msg)
	end
end

print("1. unused is the dim end, the hottest is the bright end")
check(heat.alpha(0, 0) == heat.MIN_ALPHA, "nothing counted → dim")
check(heat.alpha(0, 500) == heat.MIN_ALPHA, "zero beside a hot tile → dim")
check(heat.alpha(500, 500) == heat.MAX_ALPHA, "the max is fully bright")
check(heat.alpha(1, 1) == heat.MAX_ALPHA, "a single use is the max")

print("2. brightness is the count divided by the hottest tile")
local span = heat.MAX_ALPHA - heat.MIN_ALPHA
local a300 = heat.alpha(300, 500)
local a500 = heat.alpha(500, 500)
local expect300 = math.floor(heat.MIN_ALPHA + (300 / 500) * span + 0.5)
check(a300 == expect300,
	string.format("300/500 should be 0.6 of the span, got %d want %d", a300, expect300))
local gap = a500 - a300
check(gap >= 50,
	string.format("300 beside 500 must stay visibly dimmer (gap %d)", gap))
local once = heat.alpha(1, 500)
check(once <= heat.MIN_ALPHA + 4,
	string.format("one use beside 500 stays near dim, got %d", once))
local a1 = heat.alpha(1, 1000)
local a10 = heat.alpha(10, 1000)
local a100 = heat.alpha(100, 1000)
local a1000 = heat.alpha(1000, 1000)
check(a1 <= a10 and a10 < a100 and a100 < a1000,
	string.format("not increasing: %d %d %d %d", a1, a10, a100, a1000))
check(a1 >= heat.MIN_ALPHA and a1000 <= heat.MAX_ALPHA, "alpha stays in range")

print("3. label is light on a dim tile and dark on a bright one")
local lr, lg, lb = heat.labelRgb(heat.MIN_ALPHA)
check(lr == 255 and lg == 255 and lb == 255, "dim tile keeps a light label")
lr, lg, lb = heat.labelRgb(heat.MAX_ALPHA)
check(lr == 20 and lg == 20 and lb == 24, "bright tile gets a dark label")

print("4. encode/decode keeps odd labels and does not mix prefixes")
local counts = {
	["N|tap"] = 3,
	["NORTH|tap"] = 9,
	["go\nthere|hold"] = 2,
	["100%|tap"] = 4,
	["a\tb|swipe-up"] = 1,
	["gone|tap"] = 0,
}
local back = heat.decode(heat.encode(counts))
check(back["N|tap"] == 3, "N")
check(back["NORTH|tap"] == 9, "NORTH must not merge with N")
check(back["go\nthere|hold"] == 2, "newline in the label")
check(back["100%|tap"] == 4, "percent")
check(back["a\tb|swipe-up"] == 1, "tab")
check(back["gone|tap"] == nil, "zero is dropped")
check(heat.total(back, "N") == 3, "total for N is only N")
check(heat.total(back, "NORTH") == 9, "total for NORTH")
check(heat.total(back, "go\nthere") == 2, "total across the newline id")

print("5. a world name with a newline round-trips in front of the body")
local world = "world\nA"
local packed = heat.pack(world, heat.encode({ ["look|tap"] = 7 }))
local w, decoded = heat.unpack(packed)
check(w == world, "world bytes")
check(decoded["look|tap"] == 7, "counts after the world")

print("6. garbage and an empty file are an empty map")
local emptyW, emptyCounts = heat.unpack("")
check(emptyW == "" and next(emptyCounts) == nil, "empty payload")
check(next(heat.decode("nope")) == nil, "bad header")
check(next(heat.decode("v1")) == nil, "header only")

if failures > 0 then
	print(failures .. " failed")
	os.exit(1)
end
print("ok")
