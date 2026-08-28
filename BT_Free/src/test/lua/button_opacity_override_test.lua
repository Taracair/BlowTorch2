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

print("4. alphaFromArgb of signed 0x880000FF and opaque/transparent")
local alphaFirst = findLine("^function alphaFromArgb", "alphaFromArgb")
local alphaStop = findLine("^function colorWithForcedAlpha", "colorWithForcedAlpha")
local alphaChunk = table.concat(lines, "\n", alphaFirst, alphaStop - 1)
	.. "\nreturn alphaFromArgb\n"
local alphaFromArgb = assert(loadfn(alphaChunk, "alphaFromArgb-extract"))()
local a = alphaFromArgb(signed)
check(a == 0x88, string.format("signed 0x880000FF alpha → %s", tostring(a)))
check(alphaFromArgb(4294967295) == 255, "0xFFFFFFFF → 255") -- unsigned 32-bit all ones
check(alphaFromArgb(0x00FF8000) == 0, "transparent fill → 0")
check(alphaFromArgb(nil) == 255, "nil → opaque so badges are not hidden by a missed fill")

print("5. effectiveButtonOpacityOverride pauses the session value while editing")
local helperFirst = findLine("^function effectiveButtonOpacityOverride",
		"effectiveButtonOpacityOverride")
local helperBody = table.concat(lines, "\n", helperFirst, first - 1)
local paused = assert(loadfn(
		"manage = true\nbuttonOpacityOverride = 0\n"
				.. helperBody
				.. "\nreturn effectiveButtonOpacityOverride()\n",
		"opacity-paused"))()
check(paused == nil, "editing → nil (own alpha)")
local playing = assert(loadfn(
		"manage = false\nbuttonOpacityOverride = 0\n"
				.. helperBody
				.. "\nreturn effectiveButtonOpacityOverride()\n",
		"opacity-playing"))()
check(playing == 0, "not editing → session 0")

print("6. loadButtons keeps the session override across require(\"button\")")
local BW = scriptDir() .. "/../../../assets/share/lua/5.1/buttonwindow.lua"
local bwLines = {}
local bwHandle = assert(io.open(BW, "r"), "cannot open " .. BW)
for line in bwHandle:lines() do bwLines[#bwLines + 1] = line end
bwHandle:close()

local loadStart, loadEnd
for i, line in ipairs(bwLines) do
	if line:match("^function loadButtons") then
		loadStart = i
	elseif loadStart and not loadEnd and line:match("^function ") then
		loadEnd = i - 1
		break
	end
end
check(loadStart ~= nil, "found loadButtons")
check(loadEnd ~= nil, "found end of loadButtons")

local captureLine, unloadLine, requireLine, applyLine, restoreLine
if loadStart and loadEnd then
	for i = loadStart, loadEnd do
		local line = bwLines[i]
		if line:match("^%s*local%s+savedOpacityOverride%s*=%s*buttonOpacityOverride%s*$") then
			captureLine = i
		end
		if line:match('package%.loaded%["button"%]%s*=%s*nil') then
			unloadLine = i
		end
		if line:match('require%("button"%)') then
			requireLine = i
		end
		if line:match("^%s*applyButtonDrawOptions%s*%(") then
			applyLine = i
		end
		if line:match("^%s*buttonOpacityOverride%s*=%s*savedOpacityOverride%s*$") then
			restoreLine = i
		end
	end
end
check(captureLine ~= nil, "captures buttonOpacityOverride before reload")
check(unloadLine ~= nil, "unloads package.loaded[\"button\"]")
check(requireLine ~= nil, "requires button.lua")
check(applyLine ~= nil, "calls applyButtonDrawOptions")
check(restoreLine ~= nil, "assigns the saved override back")
check(captureLine and unloadLine and captureLine < unloadLine,
	"capture is before package.loaded[\"button\"] = nil")
check(requireLine and restoreLine and restoreLine > requireLine,
	"restore is after require(\"button\")")
check(applyLine and restoreLine and restoreLine > applyLine,
	"restore is after applyButtonDrawOptions")

if captureLine and restoreLine then
	local preserveChunk = table.concat(bwLines, "\n", captureLine, restoreLine)
	local origRequire = require
	function require(mod)
		if mod == "button" then
			-- button.lua top-level: buttonOpacityOverride = nil
			buttonOpacityOverride = nil
			return true
		end
		return origRequire(mod)
	end
	function applyButtonDrawOptions()
		-- restores roundness/hints/swipe preview from Options, not opacity
	end
	buttonOpacityOverride = 255
	assert(loadfn(preserveChunk, "loadButtons-opacity-255"))()
	check(buttonOpacityOverride == 255, "forced 100% (255) survives the reload")
	buttonOpacityOverride = 0
	assert(loadfn(preserveChunk, "loadButtons-opacity-0"))()
	check(buttonOpacityOverride == 0, "forced 0% survives the reload")
	buttonOpacityOverride = nil
	assert(loadfn(preserveChunk, "loadButtons-opacity-nil"))()
	check(buttonOpacityOverride == nil, "no override stays nil")
	require = origRequire
end

if failures > 0 then
	print(string.format("FAILED (%d)", failures))
	os.exit(1)
end
print("ok")
