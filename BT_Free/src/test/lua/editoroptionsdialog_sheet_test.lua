-- Regression test: button set options must not cover the edit grid.
--
-- Run it with:
--     luajit BT_Free/src/test/lua/editoroptionsdialog_sheet_test.lua

local function scriptDir()
	local path = arg and arg[0] or ""
	return path:match("^(.*)[/\\][^/\\]*$") or "."
end

local SRC = scriptDir() .. "/../../../assets/share/lua/5.1/editoroptionsdialog.lua"
local JAVA = scriptDir()
	.. "/../../../../BTLib/src/com/resurrection/blowtorch2/lib/window/LuaDialog.java"

local function readLines(path)
	local lines = {}
	local handle = assert(io.open(path, "r"), "cannot open " .. path)
	for line in handle:lines() do lines[#lines + 1] = line end
	handle:close()
	return lines
end

local failures = 0
local function check(cond, msg)
	if not cond then
		failures = failures + 1
		print("  FAIL: " .. msg)
	end
end

print("1. editoroptionsdialog uses a bottom sheet root")
local lua = table.concat(readLines(SRC), "\n")
check(lua:match("LAYOUT_BOTTOM_SHEET") ~= nil,
	"expected LuaDialog.LAYOUT_BOTTOM_SHEET in editoroptionsdialog.lua")
check(lua:match("Button set options") ~= nil,
	"expected bottom-sheet header title")
check(lua:match("Panel") ~= nil and lua:match("Fullscreen") ~= nil and lua:match("Hide") ~= nil,
	"expected Panel / Fullscreen / Hide mode controls")
check(not lua:match("togglePanelButton"),
	"old Hide/Show toggle should be replaced by three mode buttons")
check(lua:match("scroller:addView%(ll%)") ~= nil,
	"settings must stay inside the scroll view")
check(not lua:match("ll:addView%(boptHolder%)"),
	"Done/Cancel must stay outside the scroller so they remain visible when collapsed")

print("2. LuaDialog exposes bottom-sheet layout mode")
local java = table.concat(readLines(JAVA), "\n")
check(java:match("LAYOUT_BOTTOM_SHEET") ~= nil,
	"LuaDialog must define LAYOUT_BOTTOM_SHEET")
check(java:match("setPresentationOverGrid") ~= nil,
	"LuaDialog must expose setPresentationOverGrid")
check(java:match("setDecorFitsSystemWindows") ~= nil,
	"fullscreen presentation must fit system windows so the dashed frame is not padded twice")
check(lua:match("setFillViewport%(true%)") ~= nil,
	"fullscreen must expand the scroll view to the available height")
check(lua:match("setBackgroundResource%(0%)") ~= nil,
	"fullscreen must not stack a second dashed frame on the panel")
check(lua:match("chromeParams") ~= nil,
	"header/footer must use weight-0 chrome params so fullscreen does not band")
check(lua:match("setScrollContentHeight%(true%)") ~= nil,
	"fullscreen fillViewport needs a MATCH_PARENT scroll child")

print("")
if failures == 0 then
	print("ALL TESTS PASSED")
	os.exit(0)
end
print(failures .. " TEST(S) FAILED")
os.exit(1)
