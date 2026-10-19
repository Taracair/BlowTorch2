-- .icon set: one shown label, else one Name, else one id.
--
-- Run: lua5.1 BT_Free/src/test/lua/button_icon_target_test.lua

local function scriptDir()
	local path = arg and arg[0] or ""
	return path:match("^(.*)[/\\][^/\\]*$") or "."
end

local ROOT = scriptDir() .. "/../../../assets/share/lua/5.1"
local icon = dofile(ROOT .. "/buttonicon.lua")

local failures = 0
local function check(cond, msg)
	if not cond then
		failures = failures + 1
		print("  FAIL: " .. msg)
	end
end

local function pad()
	return {
		{ label = "LOOK", name = "", id = "b1" },
		{ label = "N", name = "", id = "b2" },
		{ label = "", name = "", id = "b3" },
		{ label = "", name = "door", id = "b4" },
	}
end

print("1. one label wins, and capitals matter")
local index, via = icon.resolve(pad(), "LOOK")
check(index == 1 and via == "label", "LOOK is the first tile")
index, via = icon.resolve(pad(), "look")
check(index == nil and via == "missing", "look is not LOOK")

print("2. a unique label beats another button's Name")
local both = pad()
both[2].name = "LOOK"
index, via = icon.resolve(both, "LOOK")
check(index == 1 and via == "label", "the tile that says LOOK wins")

print("3. two labels fall through to one Name, else refuse")
local twins = {
	{ label = "LOOK", name = "", id = "b1" },
	{ label = "LOOK", name = "door", id = "b2" },
}
index, via = icon.resolve(twins, "door")
check(index == 2 and via == "name", "Name door picks the second LOOK")
local _, why, count = icon.resolve(twins, "LOOK")
check(why == "labelled" and count == 2, "two LOOK tiles and no Name LOOK")

print("4. a blank tile is its id, or its Name")
index, via = icon.resolve(pad(), "b3")
check(index == 3 and via == "id", "b3 is the blank tile")
index, via = icon.resolve(pad(), "door")
check(index == 4 and via == "name", "door is the named blank tile")
index, via = icon.resolve(pad(), "b4")
check(index == 4 and via == "id", "the id still works after a Name is set")

print("5. two Names, two ids, empty token")
local named = {
	{ label = "", name = "door", id = "b1" },
	{ label = "", name = "door", id = "b2" },
}
_, why, count = icon.resolve(named, "door")
check(why == "named" and count == 2, "two doors")
local sameId = {
	{ label = "", name = "", id = "b3" },
	{ label = "N", name = "", id = "b3" },
}
_, why, count = icon.resolve(sameId, "b3")
check(why == "called" and count == 2, "two buttons with id b3")
_, why = icon.resolve(pad(), "")
check(why == "missing", "an empty token matches nothing")

print("6. a label that says b12 wins over another button's id")
local clash = {
	{ label = "b12", name = "", id = "b1" },
	{ label = "", name = "", id = "b12" },
}
index, via = icon.resolve(clash, "b12")
check(index == 1 and via == "label", "the words b12 win")

if failures > 0 then
	print(failures .. " failed")
	os.exit(1)
end
print("ok")
