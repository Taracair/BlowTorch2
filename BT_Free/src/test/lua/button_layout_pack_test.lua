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

if failures > 0 then
	print(failures .. " failure(s)")
	os.exit(1)
end
print("OK")
