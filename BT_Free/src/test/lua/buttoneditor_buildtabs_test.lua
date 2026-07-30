-- Regression tests for the split button editor tab builder.
--
-- Run it with:
--     luajit BT_Free/src/test/lua/buttoneditor_buildtabs_test.lua
--
-- The Click/Swipe/Accordion tabs were moved into buttoneditor_buildtabs.lua to
-- stay under Lua 5.1's 60-upvalue limit. TabSpec objects must be returned through
-- tabState.tabs; buttoneditor.lua must not call host:addTab on stale globals.

local function scriptDir()
	local path = arg and arg[0] or ""
	return path:match("^(.*)[/\\][^/\\]*$") or "."
end

local ROOT = scriptDir() .. "/../../../assets/share/lua/5.1"
local EDITOR = ROOT .. "/buttoneditor.lua"
local BUILDTABS = ROOT .. "/buttoneditor_buildtabs.lua"

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

local editorLines = readLines(EDITOR)
local buildtabsLines = readLines(BUILDTABS)

print("1. buttoneditor.lua must register tabs from tabState.tabs")
local sawTabStateAdd = false
for _, line in ipairs(editorLines) do
	if line:match("host:addTab%(tab1%)")
		or line:match("host:addTab%(tabSwipe%)")
		or line:match("host:addTab%(tabAccordion%)") then
		check(false, "stale global tab variable in addTab: " .. line)
	end
	if line:match("host:addTab%(tabs%.") then
		sawTabStateAdd = true
	end
end
check(sawTabStateAdd, "expected host:addTab(tabs.*) calls in buttoneditor.lua")

print("2. buttoneditor_buildtabs.lua must publish tab specs on tabState")
local sawClickTab = false
local sawSwipeTab = false
local sawAccordionTab = false
for _, line in ipairs(buildtabsLines) do
	if line:match("o%.tabs%.click%s*=") then sawClickTab = true end
	if line:match("o%.tabs%.swipe%s*=") then sawSwipeTab = true end
	if line:match("o%.tabs%.accordion%s*=") then sawAccordionTab = true end
	if line:match("pairs%(carriedDiagonalSwipes%)") then
		check(false, "bare carriedDiagonalSwipes in pairs(): " .. line)
	end
end
check(sawClickTab, "buildClickTab must assign o.tabs.click")
check(sawSwipeTab, "buildTabs must assign o.tabs.swipe")
check(sawAccordionTab, "buildTabs must assign o.tabs.accordion")

print("3. mocked buildClickTab/buildTabs produce usable TabSpec objects")

local function mockView()
	local v = {
		_children = {},
		_text = "",
		_checked = false,
		_enabled = true,
		_visibility = 0,
		_id = 0,
		_inputType = 0,
		_layoutParams = nil,
	}
	local function noop() end
	setmetatable(v, {
		__index = function()
			return noop
		end,
	})
	function v:addView(child) self._children[#self._children + 1] = child end
	function v:setLayoutParams(p) self._layoutParams = p end
	function v:setText(t) self._text = t end
	function v:getText() return { toString = function() return self._text end } end
	function v:setId(id) self._id = id end
	function v:setInputType(t) self._inputType = t end
	function v:setVisibility(vis) self._visibility = vis end
	function v:setChecked(c) self._checked = c end
	function v:getParent() return nil end
	function v:setEnabled(e) self._enabled = e end
	return v
end

local function mockTabSpec(tag)
	return {
		tag = tag,
		indicator = nil,
		contentId = nil,
		setIndicator = function(self, label) self.indicator = label end,
		setContent = function(self, id) self.contentId = id end,
	}
end

local host = {
	newTabSpec = function(_, tag) return mockTabSpec(tag) end,
}

local content = mockView()

local o = {
	editorValues = {
		label = "N",
		command = "north",
		flipLabel = "",
		flipCommand = "",
		showGestureHints = true,
		showSwipePreview = false,
		showGestureLabel = true,
		holdCommand = "",
		swipeUpCommand = "",
		swipeDownCommand = "",
		swipeLeftCommand = "",
		swipeRightCommand = "",
		swipeUpLeftCommand = "ne",
		swipeUpRightCommand = "",
		swipeDownLeftCommand = "",
		swipeDownRightCommand = "",
		accordionDirection = "",
		accordionChildLayout = "along",
		accordionTrigger = "tap",
		accordionHoldMs = 450,
		accordionAutoClose = true,
		accordionChildren = {},
	},
	numediting = 1,
	fillparams = {},
	context = {
		getPackageName = function() return "test.pkg" end,
		getResources = function()
			return {
				getIdentifier = function() return 1 end,
			}
		end,
	},
	textSize = 14,
	textSizeSmall = 10,
	widgets = {},
	clickLabelEditParams = nil,
	addHelpText = function(parent, text)
		local tv = mockView()
		tv:setText(text)
		parent:addView(tv)
	end,
	makeTabLabel = function(text)
		local tv = mockView()
		tv:setText(text)
		return tv
	end,
	trackSwipeEditForFlip = function(edit) return edit end,
	updateFlipForSwipes = function() end,
}

-- Install globals the module expects.
_G.LinearLayout = { VERTICAL = 1 }
_G.LinearLayoutParams = { FILL_PARENT = -1, WRAP_CONTENT = -2 }
_G.luajava = {
	new = function(_, className)
		if className == "android.widget.LinearLayoutParams" then
			return {}
		end
		if className == "android.graphics.drawable.ColorDrawable" then
			return {}
		end
		return mockView()
	end,
	bindClass = function(name)
		if name == "android.widget.Spinner" then return "Spinner" end
		if name == "android.widget.ArrayAdapter" then return "ArrayAdapter" end
		if name == "android.widget.CheckBox" then return "CheckBox" end
		if name == "android.graphics.drawable.ColorDrawable" then return "ColorDrawable" end
		if name == "android.text.InputType" then
			return { TYPE_CLASS_NUMBER = 2 }
		end
		return {}
	end,
	createProxy = function() return {} end,
}
_G.ScrollView = "ScrollView"
_G.Gravity = { RIGHT = 3 }
_G.TextView = "TextView"
_G.EditText = "EditText"
_G.Spinner = "Spinner"
_G.CheckBox = "CheckBox"
_G.ArrayAdapter = "ArrayAdapter"
_G.View = { VISIBLE = 0, GONE = 8 }
_G.TYPE_TEXT_FLAG_MULTI_LINE = 0x20000
_G.Color = { argb = function(_, a, r, g, b) return (a * 16777216) + (r * 65536) + (g * 256) + b end }
_G.density = 2.625
_G.PluginXCallS = function() end
_G.drawButtons = function() end
_G.view = nil
_G.buttonShowHints = true
_G.buttonShowSwipePreview = false
_G.pairs = pairs
_G.tostring = tostring
_G.math = math

package.path = ROOT .. "/?.lua;" .. package.path
local buildtabs = require("buttoneditor_buildtabs")

buildtabs.buildClickTab(host, content, o)
buildtabs.buildTabs(host, content, o)

check(type(o.tabs) == "table", "tabState.tabs must be a table")
check(o.tabs.click ~= nil, "click tab spec missing")
check(o.tabs.swipe ~= nil, "swipe tab spec missing")
check(o.tabs.accordion ~= nil, "accordion tab spec missing")
check(o.tabs.click.indicator ~= nil, "click tab indicator missing")
check(o.tabs.swipe.indicator ~= nil, "swipe tab indicator missing")
check(o.tabs.accordion.indicator ~= nil, "accordion tab indicator missing")
check(o.tabs.click.contentId == 1, "click tab content id")
check(o.tabs.swipe.contentId == 3, "swipe tab content id")
check(o.tabs.accordion.contentId == 4, "accordion tab content id")
check(o.widgets.clickLabelEdit ~= nil, "click label edit widget missing")
check(o.widgets.swipeUpLeftCmdEdit ~= nil, "diagonal swipe edit missing")
check(o.carriedDiagonalSwipes ~= nil, "carriedDiagonalSwipes must be set")
check(o.carriedDiagonalSwipes.swipeUpLeftCommand == "ne", "diagonal values must be carried")

print("4. simulated host:addTab accepts all built tab specs")
local added = {}
local function addTab(spec)
	check(spec ~= nil, "addTab received nil TabSpec")
	check(spec.indicator ~= nil, "TabSpec missing indicator for " .. tostring(spec.tag))
	check(spec.contentId ~= nil, "TabSpec missing content id for " .. tostring(spec.tag))
	added[#added + 1] = spec.tag
end
addTab(o.tabs.click)
addTab(o.tabs.swipe)
addTab(o.tabs.accordion)
check(#added == 3, "expected three tabs to register")

print("")
if failures == 0 then
	print("ALL TESTS PASSED")
	os.exit(0)
end
print(failures .. " TEST(S) FAILED")
os.exit(1)
