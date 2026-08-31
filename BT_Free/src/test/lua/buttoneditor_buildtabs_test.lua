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

-- Editing several buttons at once registers one tab (Others), so tab index 3 no
-- longer exists on that path: the old jump to it would land on nothing.
for _, line in ipairs(editorLines) do
	if line:match("setCurrentTab%(3%)") then
		check(false, "setCurrentTab(3) cannot survive a one-tab multi-button edit: " .. line)
	end
end

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
		_selection = 0,
		_hint = "",
	}
	local function noop() end
	setmetatable(v, {
		__index = function()
			return noop
		end,
	})
	function v:addView(child) self._children[#self._children + 1] = child end
	function v:removeAllViews() self._children = {} end
	function v:setLayoutParams(p) self._layoutParams = p end
	function v:setText(t) self._text = t end
	function v:getText() return { toString = function() return self._text end } end
	function v:setHint(h) self._hint = h end
	function v:setId(id) self._id = id end
	function v:setInputType(t) self._inputType = t end
	function v:setVisibility(vis) self._visibility = vis end
	function v:setChecked(c) self._checked = c end
	function v:getParent() return nil end
	function v:setEnabled(e) self._enabled = e end
	function v:isEnabled() return self._enabled end
	function v:setSelection(i) self._selection = i end
	function v:getSelectedItemPosition() return self._selection or 0 end
	function v:setOnClickListener(l) self._click = l end
	function v:setOnItemSelectedListener(l) self._itemSelected = l end
	function v:addTextChangedListener(l) self._watcher = l end
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

-- Flip-gate mirror of buttoneditor.lua trimSwipeCmdText / updateFlipForSwipes,
-- so this file can assert the open-time ordering bug without loading Android.
local flipTracked = {}
local function trimSwipeCmdText(edit, lockTable)
	if edit == nil then return "" end
	local locked = lockTable ~= nil and lockTable[edit]
	if not edit:isEnabled() and not locked then
		return ""
	end
	local t = edit:getText():toString()
	if t == nil then return "" end
	return (string.gsub(t, "^%s*(.-)%s*$", "%1"))
end

local function makeFlipGate(lockTableRef)
	return function()
		local accordionBlocks = false
		if lockTableRef.table ~= nil then
			for _ in pairs(lockTableRef.table) do
				accordionBlocks = true
				break
			end
		end
		local blocked = accordionBlocks
		if not blocked then
			for i = 1, #flipTracked do
				if trimSwipeCmdText(flipTracked[i], lockTableRef.table) ~= "" then
					blocked = true
					break
				end
			end
		end
		local flipLabel = lockTableRef.flipLabelEdit
		local flipCmd = lockTableRef.flipCmdEdit
		local note = lockTableRef.flipSwipeNote
		local accNote = lockTableRef.accordionFlipLockNote
		if flipLabel ~= nil then flipLabel:setEnabled(not blocked) end
		if flipCmd ~= nil then flipCmd:setEnabled(not blocked) end
		if accNote ~= nil then
			accNote:setVisibility(accordionBlocks and _G.View.VISIBLE or _G.View.GONE)
		end
		if note ~= nil then
			note:setVisibility((blocked and not accordionBlocks) and _G.View.VISIBLE or _G.View.GONE)
		end
		lockTableRef.lastBlocked = blocked
		lockTableRef.lastAccordionBlocks = accordionBlocks
	end
end

local flipGateState = { table = {}, lastBlocked = false }
-- Shared lock table wired BEFORE buildTabs — same order as buttoneditor.lua.
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
		accordionChildren = {
			{ label = "A", command = "a" },
			{ label = "B", command = "b" },
		},
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
	accordionLockedSwipeEdits = flipGateState.table,
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
	trackSwipeEditForFlip = function(edit)
		flipTracked[#flipTracked + 1] = edit
		return edit
	end,
	updateFlipForSwipes = makeFlipGate(flipGateState),
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
	_G.Gravity = { RIGHT = 3, CENTER = 17, CENTER_VERTICAL = 16 }
_G.TextView = "TextView"
_G.EditText = "EditText"
_G.Spinner = "Spinner"
_G.CheckBox = "CheckBox"
_G.ArrayAdapter = "ArrayAdapter"
_G.View = { VISIBLE = 0, GONE = 8 }
_G.TYPE_TEXT_FLAG_MULTI_LINE = 0x20000
_G.TYPE_CLASS_TEXT = 0x1
_G.Color = { argb = function(_, a, r, g, b) return (a * 16777216) + (r * 65536) + (g * 256) + b end }
_G.density = 2.625
_G.PluginXCallS = function() end
_G.drawButtons = function() end
_G.view = nil
_G.buttonShowHints = true
_G.buttonShowSwipePreview = false
_G.Button = "Button"
_G.pairs = pairs
_G.ipairs = ipairs
_G.tostring = tostring
_G.tonumber = tonumber
_G.math = math
_G.table = table
_G.string = string

package.path = ROOT .. "/?.lua;" .. package.path
local buildtabs = require("buttoneditor_buildtabs")

buildtabs.buildClickTab(host, content, o)
flipGateState.flipLabelEdit = o.widgets.flipLabelEdit
flipGateState.flipCmdEdit = o.widgets.flipCmdEdit
flipGateState.flipSwipeNote = o.widgets.flipSwipeNote
flipGateState.accordionFlipLockNote = o.widgets.accordionFlipLockNote
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
check(o.widgets.clickLabelEdit._inputType
			== _G.TYPE_CLASS_TEXT + _G.TYPE_TEXT_FLAG_MULTI_LINE,
	"click label CLASS_TEXT|MULTI_LINE so Enter inserts a newline")
check(o.widgets.flipLabelEdit._inputType
			== _G.TYPE_CLASS_TEXT + _G.TYPE_TEXT_FLAG_MULTI_LINE,
	"flip label CLASS_TEXT|MULTI_LINE so Enter inserts a newline")
check(o.widgets.swipeUpLeftCmdEdit ~= nil, "diagonal swipe edit missing")
check(o.carriedDiagonalSwipes ~= nil, "carriedDiagonalSwipes must be set")
check(o.carriedDiagonalSwipes.swipeUpLeftCommand == "ne", "diagonal values must be carried")

print("4. the Accordion tab greys out for a super button, live")
-- A super button's accordion is stripped on save, so every field on the tab has
-- to be dead while 'Float over the game' is ticked -- not only the direction
-- spinner, and without waiting for the editor to be reopened.
check(type(o.updateAccordionEnabled) == "function",
	"buildTabs must publish o.updateAccordionEnabled")

local function accordionStaticWidgets()
	return {
		o.widgets.accordionDirSpinner,
		o.widgets.accordionLayoutSpinner,
		o.widgets.accordionTriggerSpinner,
		o.widgets.accordionHoldMsEdit,
		o.widgets.accordionLanesEdit,
		o.widgets.accordionAutoCloseCheck,
	}
end

local function accordionChildFieldWidgets()
	local list = {}
	local labels = o.widgets.accordionChildLabelEdits or {}
	local cmds = o.widgets.accordionChildCmdEdits or {}
	for i = 1, #labels do
		list[#list + 1] = labels[i]
		list[#list + 1] = cmds[i]
	end
	return list
end

for _, w in ipairs(accordionStaticWidgets()) do
	check(w ~= nil, "accordion widget missing from tabState.widgets")
end
check(#accordionChildFieldWidgets() == 4, "seeded with two children → four edits")
check(o.widgets.accordionSuperNote ~= nil, "super-button note missing")
check(o.widgets.accordionAddButton ~= nil, "add affordance missing")
check(o.widgets.accordionChildrenContainer ~= nil, "children container missing")

-- The three pages are hidden rather than skipped for a multi-button edit: Done
-- reads every field on the way out, so the widgets have to exist. buttoneditor
-- needs a handle on each scroller to hide it.
check(o.widgets.clickPageScroller ~= nil, "click page scroller must be published")
check(o.widgets.swipePageScroller ~= nil, "swipe page scroller must be published")
check(o.widgets.accordionPageScroller ~= nil, "accordion page scroller must be published")

-- Built with floating unset, so the tab starts usable and the note is hidden.
for _, w in ipairs(accordionStaticWidgets()) do
	check(w._enabled == true, "accordion field should start enabled")
end
for _, w in ipairs(accordionChildFieldWidgets()) do
	check(w._enabled == true, "accordion child field should start enabled")
end
check(o.widgets.accordionSuperNote._visibility == _G.View.GONE,
	"super-button note should start hidden")

o.updateAccordionEnabled(true)
for _, w in ipairs(accordionStaticWidgets()) do
	check(w._enabled == false, "accordion field must be disabled for a super button")
end
for _, w in ipairs(accordionChildFieldWidgets()) do
	check(w._enabled == false, "accordion child field must be disabled for a super button")
end
check(o.widgets.accordionAddButton._enabled == false,
	"add button must be disabled for a super button")
check(o.widgets.accordionSuperNote._visibility == _G.View.VISIBLE,
	"super-button note must be shown when floating")

o.updateAccordionEnabled(false)
for _, w in ipairs(accordionStaticWidgets()) do
	check(w._enabled == true, "unticking float must give the accordion fields back")
end
check(o.widgets.accordionSuperNote._visibility == _G.View.GONE,
	"super-button note must be hidden again")

-- Editing several buttons at once closes the tab too, and keeps it closed even
-- when the button is not floating.
o.numediting = 3
o.updateAccordionEnabled(false)
for _, w in ipairs(accordionStaticWidgets()) do
	check(w._enabled == false, "accordion field must stay disabled for a multi-button edit")
end
check(o.widgets.accordionSuperNote._visibility == _G.View.GONE,
	"multi-button edit is not a super button, so no super-button note")
o.numediting = 1
o.updateAccordionEnabled(false)

print("5. simulated host:addTab accepts all built tab specs")
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

print("6. dynamic sub-button list: insert, delete, reorder, add-gate, 20 cap")
check(type(o.accordionInsertChild) == "function", "insert helper missing")
check(o.accordionChildDraftCount() == 2, "seeded draft count")
check(o.accordionCanAdd() == true, "filled last row allows add")

-- Blank last row blocks add.
o.accordionInsertChild(nil, "", "")
check(o.accordionChildDraftCount() == 3, "insert at end")
check(o.accordionCanAdd() == false, "blank last row must block add")
check(o.widgets.accordionAddButton._enabled == false, "add button disabled while last blank")
check(o.widgets.accordionAddHint._visibility == _G.View.VISIBLE, "hint visible while gated")

o.accordionSetChildText(3, "C", "c")
check(o.accordionCanAdd() == true, "filling last row re-opens add")
check(o.widgets.accordionAddButton._enabled == true, "add button enabled after fill")

-- Reorder: move last up one → A, C, B
check(o.accordionMoveChild(3, -1) == true, "move C up one")
local harvested = o.harvestAccordionChildren()
check(harvested[1].label == "A" and harvested[2].label == "C" and harvested[3].label == "B",
	"move swaps neighbors in harvest order")

check(o.accordionDeleteChild(2) == true, "delete middle")
check(o.accordionChildDraftCount() == 2, "count after delete")
harvested = o.harvestAccordionChildren()
check(#harvested == 2 and harvested[1].label == "A" and harvested[2].label == "B",
	"harvest drops nothing with content")

-- Empty rows dropped on harvest.
o.accordionInsertChild(2, "", "")
check(o.accordionChildDraftCount() == 3, "blank insert in middle")
harvested = o.harvestAccordionChildren()
check(#harvested == 2, "empty rows dropped on harvest")

-- Direction wording tracks stack orientation (mirrors accordionStackVertical).
local wordsDown = o.accordionDirectionWords("down", "along")
check(wordsDown.before == "Above" and wordsDown.after == "Below"
	and wordsDown.addWord == "below", "down+along → Above/Below/below")
local wordsUp = o.accordionDirectionWords("up", "along")
check(wordsUp.addWord == "above", "up+along → add above")
local wordsRight = o.accordionDirectionWords("right", "along")
check(wordsRight.before == "Left" and wordsRight.after == "Right"
	and wordsRight.addWord == "right", "right+along → Left/Right/right")
local wordsLeft = o.accordionDirectionWords("left", "along")
check(wordsLeft.addWord == "left", "left+along → add left")
local wordsVertRight = o.accordionDirectionWords("right", "vertical")
check(wordsVertRight.before == "Above" and wordsVertRight.addWord == "below",
	"forced vertical column uses Above/Below")
local wordsHorizDown = o.accordionDirectionWords("down", "horizontal")
check(wordsHorizDown.before == "Left" and wordsHorizDown.addWord == "right",
	"forced horizontal row uses Left/Right")

	-- Live re-label of the end affordance when Expand spinner changes — captions
-- only, no row rebuild (production path after the spinner-focus fix).
o.widgets.accordionDirSpinner:setSelection(2) -- Up
o.updateAccordionControlCaptions()
check(o.widgets.accordionAddButton._text:find("above", 1, true) ~= nil,
	"add button reads 'above' when Expand=Up")
o.widgets.accordionDirSpinner:setSelection(3) -- Right
o.widgets.accordionLayoutSpinner:setSelection(0) -- along
o.updateAccordionControlCaptions()
check(o.widgets.accordionAddButton._text:find("right", 1, true) ~= nil,
	"add button reads 'right' when Expand=Right")
-- Captions must not destroy the Label/Command EditTexts.
local labelBefore = o.widgets.accordionChildLabelEdits[1]
o.updateAccordionControlCaptions()
check(o.widgets.accordionChildLabelEdits[1] == labelBefore,
	"caption update must not rebuild child EditTexts")

-- Cap at 20: start clean, fill twenty content rows, then the limit sentence.
while o.accordionChildDraftCount() > 0 do
	check(o.accordionDeleteChild(o.accordionChildDraftCount()) == true,
		"clear draft before cap test")
end
check(o.accordionInsertChild(nil, "x1", "y1") == true, "seed first of 20")
while o.accordionChildDraftCount() < 20 do
	local nexti = o.accordionChildDraftCount() + 1
	check(o.accordionInsertChild(nil, "x" .. nexti, "y" .. nexti) == true,
		"insert content row " .. tostring(nexti))
end
check(o.accordionChildDraftCount() == 20, "reached 20")
check(o.accordionCanAdd() == false, "cannot add past 20")
check(o.accordionInsertChild(nil, "nope", "nope") == false, "insert helper rejects at cap")
check(o.widgets.accordionLimitNote._visibility == _G.View.VISIBLE,
	"limit sentence visible at 20")
check(o.widgets.accordionAddButton._visibility == _G.View.GONE,
	"add control hidden at 20 (replaced by sentence)")
local harvested20 = o.harvestAccordionChildren()
check(#harvested20 == 20, "harvest keeps 20 content rows")

print("6b. harvest keeps pin ids; a pin-only row counts as filled")
while o.accordionChildDraftCount() > 0 do
	check(o.accordionDeleteChild(o.accordionChildDraftCount()) == true,
		"clear draft before pin-id harvest")
end
check(o.accordionInsertChild(nil, "", "", "b99") == true, "insert pin-only row")
check(o.accordionCanAdd() == true, "pin-only row with id counts as filled")
local harvestedPin = o.harvestAccordionChildren()
check(#harvestedPin == 1 and harvestedPin[1].id == "b99",
	"Done harvest must keep the pin id")
check(harvestedPin[1].label == "" and harvestedPin[1].command == "",
	"pin-only snapshot may have empty label/command")
o.accordionSetChildText(1, "LOOK", "look")
harvestedPin = o.harvestAccordionChildren()
check(harvestedPin[1].id == "b99" and harvestedPin[1].label == "LOOK",
	"editing label must not strip the pin id")

print("7. trigger exclusivity locks the opening gesture live")
-- Reset to a small configured accordion: Expand=Down, Open with=Tap, one child.
local guard = 0
while o.accordionChildDraftCount() > 1 do
	guard = guard + 1
	check(o.accordionDeleteChild(o.accordionChildDraftCount()) == true,
		"delete while trimming to one child")
	if guard > 25 then
		check(false, "delete loop failed to shrink draft")
		break
	end
end
o.accordionSetChildText(1, "LOOK", "look")
o.widgets.accordionDirSpinner:setSelection(1) -- Down
o.widgets.accordionTriggerSpinner:setSelection(0) -- Tap
o.updateAccordionGestureLocks()
check(o.widgets.clickCmdEdit._enabled == false, "tap cmd locked when accordion opens on tap")
check(o.widgets.accordionTapLockNote._visibility == _G.View.VISIBLE, "tap lock note shown")
check(o.widgets.holdCmdEdit._enabled == true, "hold stays editable for tap trigger")
check(o.widgets.swipeDownCmdEdit._enabled == true, "swipe stays editable for tap trigger")

o.widgets.accordionTriggerSpinner:setSelection(1) -- Hold
o.updateAccordionGestureLocks()
check(o.widgets.clickCmdEdit._enabled == true, "tap unlocked when trigger is hold")
check(o.widgets.holdCmdEdit._enabled == false, "hold cmd locked")
check(o.widgets.accordionHoldLockNote._visibility == _G.View.VISIBLE, "hold lock note shown")

o.widgets.accordionTriggerSpinner:setSelection(2) -- Swipe
o.widgets.accordionDirSpinner:setSelection(1) -- Down
o.updateAccordionGestureLocks()
check(o.widgets.holdCmdEdit._enabled == true, "hold unlocked when trigger is swipe")
check(o.widgets.swipeDownCmdEdit._enabled == false, "down swipe locked for expand down")
check(o.widgets.accordionSwipeLockNoteDown._visibility == _G.View.VISIBLE,
	"down swipe lock note shown")
check(o.widgets.swipeUpCmdEdit._enabled == true, "other swipe directions stay editable")
check(o.accordionLockedSwipeEdits[o.widgets.swipeDownCmdEdit] == true,
	"locked swipe edit recorded for Flip gate")
check(o.widgets.flipCmdEdit._enabled == false,
	"swipe-to-expand locks Flip even when other swipe cmds exist")
check(o.widgets.accordionFlipLockNote._visibility == _G.View.VISIBLE,
	"accordion flip lock note shown for swipe trigger")
check(o.widgets.flipSwipeNote._visibility == _G.View.GONE,
	"generic swipe-vs-flip note hidden while accordion lock is the reason")

-- Keep saved text on the locked field (Done must not blank it).
o.widgets.swipeDownCmdEdit:setText("south")
o.widgets.swipeDownCmdEdit:setEnabled(false)
check(o.widgets.swipeDownCmdEdit:getText():toString() == "south",
	"disabled locked field still holds its text")

-- Direction None clears locks even with children.
o.widgets.accordionDirSpinner:setSelection(0)
o.updateAccordionGestureLocks()
check(o.widgets.swipeDownCmdEdit._enabled == true, "no expand direction → no swipe lock")
check(o.widgets.accordionSwipeLockNoteDown._visibility == _G.View.GONE,
	"swipe lock note hidden when not configured")

-- Floating super-button must not lock gestures (accordion will not save).
o.widgets.accordionDirSpinner:setSelection(1)
o.widgets.accordionTriggerSpinner:setSelection(0)
o.updateAccordionEnabled(true)
check(o.widgets.clickCmdEdit._enabled == true,
	"super button must not lock tap cmd via accordion")
o.updateAccordionEnabled(false)

print("8. Flip is gated on first open for a swipe-trigger accordion")
-- Reproduce the buttoneditor wiring order: lock table exists before buildTabs,
-- swipe-down command is set, trigger=swipe, Expand=Down, children present.
-- Before the fix, updateFlipForSwipes ran during build against an empty global
-- lock set and left Flip editable.
flipTracked = {}
local flipOpen = { table = {}, lastBlocked = false }
local content2 = mockView()
local o2 = {
	editorValues = {
		label = "ACC",
		command = "",
		flipLabel = "flip",
		flipCommand = "look",
		showGestureHints = true,
		showSwipePreview = false,
		showGestureLabel = true,
		holdCommand = "",
		swipeUpCommand = "",
		swipeDownCommand = "south",
		swipeLeftCommand = "",
		swipeRightCommand = "",
		swipeUpLeftCommand = "",
		swipeUpRightCommand = "",
		swipeDownLeftCommand = "",
		swipeDownRightCommand = "",
		accordionDirection = "down",
		accordionChildLayout = "along",
		accordionTrigger = "swipe",
		accordionHoldMs = 450,
		accordionAutoClose = true,
		accordionChildren = {
			{ label = "LOOK", command = "look" },
		},
		floating = false,
	},
	numediting = 1,
	fillparams = {},
	context = o.context,
	textSize = 14,
	textSizeSmall = 10,
	widgets = {},
	clickLabelEditParams = nil,
	accordionLockedSwipeEdits = flipOpen.table,
	addHelpText = o.addHelpText,
	makeTabLabel = o.makeTabLabel,
	trackSwipeEditForFlip = function(edit)
		flipTracked[#flipTracked + 1] = edit
		return edit
	end,
	updateFlipForSwipes = makeFlipGate(flipOpen),
}
buildtabs.buildClickTab(host, content2, o2)
flipOpen.flipLabelEdit = o2.widgets.flipLabelEdit
flipOpen.flipCmdEdit = o2.widgets.flipCmdEdit
flipOpen.flipSwipeNote = o2.widgets.flipSwipeNote
flipOpen.accordionFlipLockNote = o2.widgets.accordionFlipLockNote
buildtabs.buildTabs(host, content2, o2)

check(o2.widgets.swipeDownCmdEdit._enabled == false,
	"open: down swipe field locked for swipe-trigger accordion")
check(o2.accordionLockedSwipeEdits[o2.widgets.swipeDownCmdEdit] == true,
	"open: locked edit is in the shared lock table")
check(o2.widgets.flipCmdEdit._enabled == false,
	"open: Flip CMD must be gated when locked direction has a command")
check(o2.widgets.flipLabelEdit._enabled == false,
	"open: Flip label must be gated")
check(o2.widgets.accordionFlipLockNote._visibility == _G.View.VISIBLE,
	"open: accordion flip-lock note visible")
check(o2.widgets.flipSwipeNote._visibility == _G.View.GONE,
	"open: generic swipe-vs-flip note hidden under accordion lock")
check(flipOpen.lastBlocked == true, "open: flip gate reported blocked")

-- Contrasting bug reproduction: if the lock table is a *different* empty table
-- at Flip-read time (the old buttoneditor ordering), trim returns "" for the
-- disabled field and Flip would stay editable.
local orphanLocks = {}
check(trimSwipeCmdText(o2.widgets.swipeDownCmdEdit, orphanLocks) == "",
	"disabled swipe with wrong lock table reads empty (the old bug)")
check(trimSwipeCmdText(o2.widgets.swipeDownCmdEdit, o2.accordionLockedSwipeEdits) == "south",
	"disabled swipe with the shared lock table still reads its command")

print("9. swipe-to-expand with empty swipe cmds still locks Flip")
-- Characterization of old behaviour: Flip stayed editable because trim of the
-- locked empty Down field returned "". Now the lock table itself gates Flip.
flipTracked = {}
local flipEmpty = { table = {}, lastBlocked = false }
local content3 = mockView()
local o3 = {
	editorValues = {
		label = "ACC",
		command = "north",
		flipLabel = "flip",
		flipCommand = "look",
		showGestureHints = true,
		showSwipePreview = false,
		showGestureLabel = true,
		holdCommand = "",
		swipeUpCommand = "",
		swipeDownCommand = "",
		swipeLeftCommand = "",
		swipeRightCommand = "",
		swipeUpLeftCommand = "",
		swipeUpRightCommand = "",
		swipeDownLeftCommand = "",
		swipeDownRightCommand = "",
		accordionDirection = "down",
		accordionChildLayout = "along",
		accordionTrigger = "swipe",
		accordionHoldMs = 450,
		accordionAutoClose = true,
		accordionChildren = {
			{ label = "LOOK", command = "look" },
		},
		floating = false,
	},
	numediting = 1,
	fillparams = {},
	context = o.context,
	textSize = 14,
	textSizeSmall = 10,
	widgets = {},
	clickLabelEditParams = nil,
	accordionLockedSwipeEdits = flipEmpty.table,
	addHelpText = o.addHelpText,
	makeTabLabel = o.makeTabLabel,
	trackSwipeEditForFlip = function(edit)
		flipTracked[#flipTracked + 1] = edit
		return edit
	end,
	updateFlipForSwipes = makeFlipGate(flipEmpty),
}
buildtabs.buildClickTab(host, content3, o3)
flipEmpty.flipLabelEdit = o3.widgets.flipLabelEdit
flipEmpty.flipCmdEdit = o3.widgets.flipCmdEdit
flipEmpty.flipSwipeNote = o3.widgets.flipSwipeNote
flipEmpty.accordionFlipLockNote = o3.widgets.accordionFlipLockNote
buildtabs.buildTabs(host, content3, o3)

check(o3.widgets.swipeDownCmdEdit._enabled == false,
	"empty-swipe open: Down field still locked")
check(o3.accordionLockedSwipeEdits[o3.widgets.swipeDownCmdEdit] == true,
	"empty-swipe open: lock table still records Down")
check(o3.widgets.flipCmdEdit._enabled == false,
	"empty locked swipe still disables Flip CMD")
check(o3.widgets.flipLabelEdit._enabled == false,
	"empty locked swipe still disables Flip label")
check(o3.widgets.accordionFlipLockNote._visibility == _G.View.VISIBLE,
	"accordion flip-lock note shown when swipe cmds are empty")
check(o3.widgets.flipSwipeNote._visibility == _G.View.GONE,
	"generic swipe-vs-flip note stays hidden")
check(flipEmpty.lastBlocked == true,
	"flip gate blocks on the lock table, not on swipe text")
check(o3.widgets.accordionFlipLockNote ~= nil,
	"accordionFlipLockNote widget published")

print("")
if failures == 0 then
	print("ALL TESTS PASSED")
	os.exit(0)
end
print(failures .. " TEST(S) FAILED")
os.exit(1)
