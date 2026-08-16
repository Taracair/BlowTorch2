-- rgbFromArgb: Java ARGB as a signed int → R,G,B.
--
-- Run:
--     lua5.1 BT_Free/src/test/lua/button_opacity_override_test.lua

local function scriptDir()
	local path = arg and arg[0] or ""
	local dir = path:match("^(.*)[/\\][^/\\]*$")
	return dir or "."
end

local SRC = scriptDir() .. "/../../../assets/share/lua/5.1/button.lua"

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

local first = findLine("^function rgbFromArgb", "rgbFromArgb")
local stop = findLine("^function colorWithForcedAlpha", "colorWithForcedAlpha")
local chunk = table.concat(lines, "\n", first, stop - 1)
	.. "\nreturn rgbFromArgb\n"

local loadfn = loadstring or load
local rgbFromArgb = assert(loadfn(chunk, "rgbFromArgb-extract"))()

local failures = 0
local function check(cond, msg)
	if not cond then
		failures = failures + 1
		print("  FAIL: " .. msg)
	end
end

print("1. opaque orange 0xFFFF8000")
local r, g, b = rgbFromArgb(4294934528) -- 0xFFFF8000
check(r == 255 and g == 128 and b == 0,
	string.format("got %d,%d,%d", r, g, b))

print("2. default button blue with alpha 0x88 (Java signed)")
-- 0x880000FF as signed 32-bit
local signed = 2281701631 - 4294967296
r, g, b = rgbFromArgb(signed)
check(r == 0 and g == 0 and b == 255,
	string.format("signed 0x880000FF → %d,%d,%d", r, g, b))

print("3. nil-ish non-number")
r, g, b = rgbFromArgb(nil)
check(r == 0 and g == 0 and b == 0, "nil → 0,0,0")

if failures > 0 then
	print(string.format("FAILED (%d)", failures))
	os.exit(1)
end
print("ok")
