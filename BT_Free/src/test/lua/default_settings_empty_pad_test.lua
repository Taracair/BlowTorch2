-- New MUD profiles must not seed an on-screen pad. The wizard is the choice.
--
-- Run:
--     lua5.1 BT_Free/src/test/lua/default_settings_empty_pad_test.lua

local function scriptDir()
	local path = arg and arg[0] or ""
	return path:match("^(.*)[/\\][^/\\]*$") or "."
end

local CONFIG = scriptDir() .. "/../../../config"
local files = {
	CONFIG .. "/default_settings_main.xml",
	CONFIG .. "/default_settings_test.xml",
}

local failures = 0
local function check(cond, msg)
	if not cond then
		failures = failures + 1
		print("  FAIL: " .. msg)
	end
end

local function readAll(path)
	local handle = assert(io.open(path, "r"), "cannot open " .. path)
	local text = handle:read("*a")
	handle:close()
	return text
end

local function eachButtonset(xml, name, fn)
	local pos = 1
	while true do
		local s, e, attrs = xml:find('<buttonset%s+([^>]*)>', pos)
		if not s then return end
		local isName = attrs:find('name="' .. name .. '"', 1, true)
		local isSetName = attrs:find('setName="' .. name .. '"', 1, true)
		local close = xml:find('</buttonset>', e, true)
		check(close ~= nil, name .. " buttonset is closed")
		if close == nil then return end
		if isName or isSetName then
			fn(xml:sub(e + 1, close - 1))
		end
		pos = close + 1
	end
end

for _, path in ipairs(files) do
	print("checking " .. path)
	local xml = readAll(path)
	local defaultCount, tutorialCount = 0, 0
	eachButtonset(xml, "default", function(inner)
		defaultCount = defaultCount + 1
		check(inner:find("<button", 1, true) == nil,
			path .. " default buttonset #" .. defaultCount .. " must have no tiles")
	end)
	eachButtonset(xml, "tutorial", function(inner)
		tutorialCount = tutorialCount + 1
		check(inner:find("<button", 1, true) ~= nil,
			path .. " tutorial buttonset #" .. tutorialCount .. " must keep its tiles")
	end)
	check(defaultCount == 2, path .. " has Java + plugin default sets (got "
		.. tostring(defaultCount) .. ")")
	check(tutorialCount == 2, path .. " has Java + plugin tutorial sets (got "
		.. tostring(tutorialCount) .. ")")
end

if failures > 0 then
	print(failures .. " failure(s)")
	os.exit(1)
end
print("OK")
