-- Recognising our own button payload on the system clipboard.
--
-- Run it with:
--     luajit BT_Free/src/test/lua/button_clipboard_payload_test.lua
--
-- Two mistakes are pinned here, both found on the first long press:
--
--   * the marker contains hyphens, and a hyphen is a lazy quantifier in a Lua
--     pattern. Matching the marker as a pattern found nothing, so paste would
--     never have recognised its own clipboard even after the crash was fixed;
--   * LuaJava hands a Java String back as a Lua string, which has no toString
--     method. Calling it crashed the button editor.
--
-- The marker and the prefix test are read out of buttonwindow.lua so the test
-- cannot drift from the shipped source.

local function scriptDir()
	local path = arg and arg[0] or ""
	return path:match("^(.*)[/\\][^/\\]*$") or "."
end

local SRC = scriptDir() .. "/../../../assets/share/lua/5.1/buttonwindow.lua"
local handle = assert(io.open(SRC, "r"), "cannot open " .. SRC)
local source = handle:read("*a")
handle:close()

local failures = 0
local function check(cond, msg)
	if not cond then
		failures = failures + 1
		print("  FAIL: " .. msg)
	end
end

local MARKER = source:match('BUTTON_CLIP_MARKER%s*=%s*"([^"]+)"')
assert(MARKER, "could not find BUTTON_CLIP_MARKER in " .. SRC)

print("button clipboard payload")

-- 1. The hyphen trap, stated directly. If this ever passes as a pattern the
--    marker has lost its hyphens and the guard below is no longer needed.
do
	local text = MARKER .. "\nPAYLOAD"
	check(text:match("^" .. MARKER .. "\n(.*)$") == nil,
		"a marker with hyphens must NOT be matchable as a plain Lua pattern -- "
		.. "if it is, this test is stale")
	local head = MARKER .. "\n"
	check(text:sub(1, #head) == head,
		"the prefix compare must recognise our own payload")
	check(text:sub(#head + 1) == "PAYLOAD",
		"and hand back the body after it")
end

-- 2. Someone else's clipboard is simply not ours.
do
	local head = MARKER .. "\n"
	local text = "milk, eggs, bread"
	check(text:sub(1, #head) ~= head, "an unrelated clipboard must not match")
	check(("BLOWTORCH-BUTTONS-2\nx"):sub(1, #head) ~= head,
		"a different payload version must not match")
	check((""):sub(1, #head) ~= head, "an empty clipboard must not match")
end

-- 3. The source uses a prefix compare, not a pattern match, for the marker.
do
	check(source:find("text:sub(1, #head) ~= head", 1, true) ~= nil,
		"buttonsOnClipboard must compare the marker as a plain prefix")
	check(source:find('text:match("^" .. BUTTON_CLIP_MARKER', 1, true) == nil,
		"the pattern form must not come back")
end

-- 4. A Lua string must survive the coercion helper untouched, which is the
--    crash: a Lua string has no toString.
do
	local asLuaString = source:match(
		"(local function asLuaString.-\nend)")
	assert(asLuaString, "could not find asLuaString in " .. SRC)
	local loader = loadstring or load
	local chunk = assert(loader(asLuaString .. "\nreturn asLuaString", "asLuaString"))
	local fn = chunk()

	check(fn("already a string") == "already a string",
		"a Lua string must come straight back, not have toString called on it")
	check(fn(nil) == nil, "nil in, nil out")

	local javaish = setmetatable({}, {__index = {toString = function() return "from java" end}})
	check(fn(javaish) == "from java",
		"an object that does have toString must still be converted")

	local hostile = setmetatable({}, {__index = function() error("boom") end})
	check(fn(hostile) == nil,
		"an object that throws must yield nil, not propagate out of a touch handler")
end

if failures == 0 then
	print("  ok")
	os.exit(0)
end
print(failures .. " failure(s)")
os.exit(1)
