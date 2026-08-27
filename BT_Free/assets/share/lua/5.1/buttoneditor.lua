local buttons = _G["buttons"]
local LinearLayoutParams = _G["LinearLayoutParams"]
local LinearLayout = _G["LinearLayout"]
local luajava = _G["luajava"]
local TextView = _G["TextView"]
local Gravity = _G["Gravity"]
local Color = _G["Color"]
local TabHost = _G["TabHost"]
local TabWidget = _G["TabWidget"]
local android_R_id = _G["android_R_id"]
local R_drawable = _G["R_drawable"]
local Button = _G["Button"]
local FrameLayout = _G["FrameLayout"]
local EditText = _G["EditText"]
local density = _G["density"]
local TYPE_TEXT_FLAG_MULTI_LINE = _G["TYPE_TEXT_FLAG_MULTI_LINE"]
local Validator = _G["Validator"]
local Validator_Number_Not_Blank = _G["Validator_Number_Not_Blank"];
local Validator_Number_Or_Blank = _G["Validator_Number_Or_Blank"];
local ORIENTATION_LANDSCAPE = _G["ORIENTATION_LANDSCAPE"]
local Context = _G["Context"]
local ScrollView = _G["ScrollView"]
local require = _G["require"]
local View = _G["View"]
local Note = _G["Note"]
local pairs = _G["pairs"]
local math = _G["math"]
local tonumber = _G["tonumber"]
local tostring = _G["tostring"]
local table = _G["table"]
local string = _G["string"]
local PluginXCallS = _G["PluginXCallS"]
local buttonEditorDone = _G["buttonEditorDone"]
-- Base functions are NOT in scope inside a bare module(...). Without this the
-- guard at showEditorDialog raises "attempt to call a nil value (global
-- 'error')" instead of the message it was written to produce.
local error = _G["error"]
local pcall = _G["pcall"]
module(...)

local textSize = (14)
local textSizeSmall = (10)
-- Sensors / alias-list chrome. Title bar is 42dip ALL CAPS, not leftover grey.
local chromeDescText = Color:argb(255, 0x9A, 0xA3, 0xAD)
local chromeChipText = Color:argb(255, 0xC7, 0xCD, 0xD4)
local chromeChipBg = Color:argb(255, 0x27, 0x2B, 0x31)
local tabMinHeight = math.floor(32 * density + 0.5)
local tabTextSize = 13

local WRAP_CONTENT = LinearLayoutParams.WRAP_CONTENT
local FILL_PARENT = LinearLayoutParams.FILL_PARENT
local GRAVITY_CENTER = Gravity.CENTER

local context = nil
local editorDialog

local doneClickListener
local cancelClickListener

--widgets
local title = nil --title text view
local clickLabelEdit --click state label editor
local clickCmdEdit --click state command editor
local flipLabelEdit --flip state label editor
local flipCmdEdit --flip state command editor
local flipSwipeNote
local accordionFlipLockNote
local holdCmdEdit
local swipeUpCmdEdit
local swipeDownCmdEdit
local swipeLeftCmdEdit
local swipeRightCmdEdit
local swipeUpLeftCmdEdit
local swipeUpRightCmdEdit
local swipeDownLeftCmdEdit
local swipeDownRightCmdEdit
-- Globals on purpose: showEditorDialog is already at Lua 5.1's 60-upvalue
-- ceiling, and two more file locals push it over. gestureLabelCarried holds the
-- value the dialog opened with, for when the checkbox is disabled (editing
-- several buttons at once) and would otherwise read back as unchecked.
gestureLabelCb = nil
gestureLabelCarried = true
-- Same reasoning, same pair, for the per-button "show hints on this button".
gestureHintsCb = nil
gestureHintsCarried = true
-- Values as they were when the dialog opened. Used when the matching field is
-- disabled (editing several buttons at once), so saving does not blank out
-- diagonal commands the fields were never allowed to show.
local carriedDiagonalSwipes = {}
local accordionDirSpinner
local accordionLayoutSpinner
local accordionTriggerSpinner
local accordionHoldMsEdit
local accordionAutoCloseCheck
local accordionChildLabelEdits = {}
local accordionChildCmdEdits = {}
-- Globals on purpose: showEditorDialog is already at Lua 5.1's 60-upvalue
-- ceiling (see gestureLabelCb). These two would push Done/show over the limit.
accordionLockedSwipeEdits = {}
harvestAccordionChildren = nil
accordionLanesEdit = nil

--the rest are harvested from the advanced page editor
local advancedEditor -- the shared advanced page editor loaded from module


local swipeCmdEditsForFlip = {}
local flipGateNumEditing = 1

local function trimSwipeCmdText(edit)
	if edit == nil then
		return ""
	end
	-- Disabled fields normally read as empty so a multi-button edit does not
	-- make Flip think a swipe is set. Accordion-locked swipe fields keep their
	-- text on screen and must still block Flip — otherwise locking Down would
	-- re-enable Flip while the Down command is still there.
	local locked = accordionLockedSwipeEdits ~= nil and accordionLockedSwipeEdits[edit]
	if not edit:isEnabled() and not locked then
		return ""
	end
	local t = edit:getText():toString()
	if t == nil then
		return ""
	end
	return (string.gsub(t, "^%s*(.-)%s*$", "%1"))
end

local function anySwipeCommandSet()
	for i = 1, #swipeCmdEditsForFlip do
		if trimSwipeCmdText(swipeCmdEditsForFlip[i]) ~= "" then
			return true
		end
	end
	return false
end

-- Swipe-to-expand locks the expand-direction swipe field even when that
-- command is empty. Flip is the same drag-off, so the lock table itself
-- must gate Flip — not only a non-empty swipe command.
local function accordionSwipeLocksFlip()
	if accordionLockedSwipeEdits == nil then
		return false
	end
	for _ in pairs(accordionLockedSwipeEdits) do
		return true
	end
	return false
end

local function updateFlipForSwipes()
	if flipGateNumEditing > 1 then
		return
	end
	local accordionBlocks = accordionSwipeLocksFlip()
	local blocked = accordionBlocks or anySwipeCommandSet()
	if flipLabelEdit ~= nil then
		flipLabelEdit:setEnabled(not blocked)
	end
	if flipCmdEdit ~= nil then
		flipCmdEdit:setEnabled(not blocked)
	end
	if accordionFlipLockNote ~= nil then
		if accordionBlocks then
			accordionFlipLockNote:setVisibility(View.VISIBLE)
		else
			accordionFlipLockNote:setVisibility(View.GONE)
		end
	end
	if flipSwipeNote ~= nil then
		-- Accordion lock is the more specific reason; hide the generic
		-- "has a swipe command" note so two warnings do not stack.
		if blocked and not accordionBlocks then
			flipSwipeNote:setVisibility(View.VISIBLE)
		else
			flipSwipeNote:setVisibility(View.GONE)
		end
	end
end

local swipeFlipWatcher = luajava.createProxy("android.text.TextWatcher", {
	afterTextChanged = function(s)
		updateFlipForSwipes()
	end,
	beforeTextChanged = function(s, start, count, after) end,
	onTextChanged = function(s, start, before, count) end,
})

local function trackSwipeEditForFlip(edit)
	if edit == nil then
		return edit
	end
	swipeCmdEditsForFlip[#swipeCmdEditsForFlip + 1] = edit
	edit:addTextChangedListener(swipeFlipWatcher)
	return edit
end

local editorFillparams = nil
local editorClickLabelEditParams = nil

local function addHelpText(parent, text)
	local help = luajava.new(TextView, context)
	help:setTextSize(textSizeSmall)
	help:setTextColor(chromeDescText)
	help:setText(text)
	help:setMaxLines(2)
	local pad = math.floor(8 * density)
	help:setPadding(pad, math.floor(4 * density), pad, math.floor(4 * density))
	help:setLayoutParams(editorFillparams)
	parent:addView(help)
end

-- Essays live behind ?. One body per tab. State warnings stay on the canvas.
-- Module members, not locals: a nested handler that closed over locals would
-- force those names through showEditorDialog's upvalue list (already 59/60).
HELP_TAP = "TAP\n"
	.. "Sends the command when you release on the button. The Label is what the tile shows.\n\n"
	.. "FLIP\n"
	.. "Drag off the button, then release, to send the flip command and show the flip label.\n\n"
	.. "Flip is blocked while any swipe command is set — that warning stays on the Tap tab.\n\n"
	.. "If an accordion opens on tap, the tap command is kept but does not fire. That warning stays on the tab too.\n\n"
	.. "If an accordion opens on swipe, Flip is kept but does not fire — drag-off is that swipe. That warning stays here too."

HELP_SWIPE = "Swipe commands override Flip when set. Drag about a finger-width (~24dp) in a direction — eight are available, four straight and four corners. A second finger cancels the gesture. Hold fires at about 0.45s.\n\n"
	.. "To edit buttons, use ⋮ → Edit buttons, or long-press the ⋮ (not the button itself).\n\n"
	.. "A corner with no command falls back to the nearest straight swipe, so adding diagonals never changes how the straight ones behave.\n\n"
	.. "If an accordion opens on swipe, that expand direction's swipe command is locked and Flip is locked. Other swipe directions still fire.\n\n"
	.. "The two checkboxes are for this button. The profile switch in set options still wins: with badges off, nothing is drawn anywhere."

HELP_ACCORDION = "Up to 20 sub-buttons. Pin existing grid tiles: in Edit buttons tap the parent, then tap another tile and choose Pin to «MORE». Tap several after the parent to pin them all. Long-press still pins too. A toast says Pinned to «MORE». Tap a pinned tile and choose Unpin from \"MORE\" — a tile can only belong to one parent. You cannot pin an accordion inside another (toast: Can't nest accordions). Pinned tiles hide in play until the parent opens, and appear where you placed them. Typed label+command rows still work (wizard packs). Super / floating buttons cannot have an accordion.\n\n"
	.. "Columns / Rows: type 2 to split ten children into two columns (or two rows). Blank = as many as fit in one lane, then wrap if the screen is short. Max 5.\n\n"
	.. "Tap = open on press, close on second press. Hold = open after the hold delay; Hold ms is on the tab only when Open with is Hold. Swipe = drag in the expand direction. Use Vertical layout to stack sub-buttons in a column when expanding left/right.\n\n"
	.. "The gesture that opens the accordion cannot also send its own command — that field is locked on the Tap/Swipe tabs, with a warning on the canvas. Swipe-to-expand also locks Flip (drag-off is the same motion).\n\n"
	.. "A super button (Float over the game) cannot have an accordion: the sub-buttons are drawn on the button grid and only exist while the parent is open. That warning stays on this tab."

HELP_OTHERS = "Name is the label in the editor list, not on the tile. Active sits to the right of Name on the same row.\n\n"
	.. "Active hides the button in play without deleting it. Untick it and the tile is gone from play (taps included); Edit buttons still shows it so you can tick Active again.\n\n"
	.. "To change button pads, put .loadset <name> in the Tap command. That is the supported way to switch sets.\n\n"
	.. "Colors: tap a swatch to change, long-press to reset to the set default. Border is the last swatch on that grid — tick Draw / border beside it. Accordion children inherit the parent's border unless they were pinned from the grid.\n\n"
	.. "Thin outline under Floating is a separate auto-contrast frame used only when Border is off.\n\n"
	.. "Width, height and position are in dp from the top-left of the button layer.\n\n"
	.. "FLOATING\n"
	.. "Tick Float over the game to put a copy of this button on the screen, over the game. When, Shape and Thin outline appear once it is ticked.\n\n"
	.. "Always visible: the button stays on screen. In play mode only the floating copy is drawn, so it does not stack on the grid tile.\n\n"
	.. "Show with keyboard: the button is there only while the keyboard is open, and is hidden everywhere otherwise, the grid included.\n\n"
	.. "Both need Display over other apps. On Android 9 and 10, Show with keyboard may never appear.\n\n"
	.. "Editing several buttons at once: size, position, colours, border and Active go to all of them. A label, command, gesture, accordion or super button belongs to one button — tap a single button for those. Setting the same X or Y stacks them; to line them up, leave X and Y empty and use Arrange in set options."

showChromeHelp = function(ctx, title, body)
	if ctx == nil then
		return
	end
	local shown = pcall(function()
		local EditorHelp = luajava.bindClass("com.resurrection.blowtorch2.lib.window.EditorHelp")
		EditorHelp:show(ctx, title, body)
	end)
	if shown then
		return
	end
	local tv = luajava.new(TextView, ctx)
	local pad = math.floor(16 * density)
	tv:setPadding(pad, pad, pad, pad)
	tv:setText(body)
	local scroll = luajava.new(ScrollView, ctx)
	scroll:addView(tv)
	local builder = luajava.newInstance("android.app.AlertDialog$Builder", ctx)
	builder:setTitle(title)
	builder:setView(scroll)
	builder:setPositiveButton("Close", nil)
	builder:show()
end

styleHelpChip = function(btn)
	btn:setText("?")
	btn:setTextSize(16)
	btn:setTextColor(chromeChipText)
	local ok = pcall(function()
		btn:setBackgroundResource(R_drawable.editor_more_button_bg)
	end)
	if not ok then
		btn:setBackgroundColor(chromeChipBg)
	end
	btn:setMinWidth(math.floor(52 * density))
	btn:setMinHeight(math.floor(44 * density))
	pcall(function()
		local Typeface = luajava.bindClass("android.graphics.Typeface")
		btn:setTypeface(Typeface.DEFAULT_BOLD)
	end)
end

-- GETGLOBAL from showEditorDialog: must not be a file local.
addEditorFooterHelp = function(finishHolder, host, numediting)
	local help = luajava.new(Button, context)
	local helpParams = luajava.new(LinearLayoutParams, WRAP_CONTENT, WRAP_CONTENT)
	local chipGap = math.floor(6 * density)
	helpParams:setMargins(chipGap, 0, chipGap, 0)
	help:setLayoutParams(helpParams)
	styleHelpChip(help)
	help:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
		onClick = function(v)
			local tab = 0
			if host ~= nil then
				tab = host:getCurrentTab() or 0
			end
			local helpTitle = "Others"
			local body = HELP_OTHERS
			if not (numediting > 1) then
				if tab == 0 then
					helpTitle = "Tap / Flip"
					body = HELP_TAP
				elseif tab == 1 then
					helpTitle = "Swipe"
					body = HELP_SWIPE
				elseif tab == 2 then
					helpTitle = "Accord."
					body = HELP_ACCORDION
				end
			end
			showChromeHelp(v:getContext(), helpTitle, body)
		end
	}))
	finishHolder:addView(help, 1)
end

local function makeTabLabel(text)
	local label = luajava.new(TextView, context)
	local params = luajava.new(LinearLayoutParams, 0, tabMinHeight, 1.0)
	label:setLayoutParams(params)
	label:setText(text)
	label:setTextSize(tabTextSize)
	label:setBackgroundResource(R_drawable.tab_background)
	label:setGravity(GRAVITY_CENTER)
	label:setSingleLine(true)
	label:setTextColor(Color:argb(255, 0xFF, 0xFF, 0xFF))
	label:setPadding(math.floor(2 * density), 0, math.floor(2 * density), 0)
	return label
end

function init(pContext)
	context = pContext
end

function showEditorDialog(editorValues,numediting)
	flipGateNumEditing = numediting
	swipeCmdEditsForFlip = {}
	--make the parent view.
	--local button = nil
	
	--local context = view:getContext()
  local utils = require("buttonutils")
	local width_param,height_param = utils.getDialogDimensions(context)
	
	local top = luajava.new(LinearLayout,context)
	local topparams = luajava.new(LinearLayoutParams,width_param,height_param)
	-- topparams = luajava.new(LinearLayoutParams,WRAP_CONTENT,WRAP_CONTENT)
	--Note("\nlayout params:"..width_param.." "..height_param.."\n")
	top:setLayoutParams(topparams)
	--top:setScrollContainer(false)
	
	local title = luajava.new(TextView,context)
	top:setOrientation(LinearLayout.VERTICAL)
	local titletextParams = luajava.new(LinearLayoutParams,FILL_PARENT,math.floor(42 * density + 0.5))
	title:setLayoutParams(titletextParams)
	title:setTextSize(textSize)
	-- The count, because a multi-button edit looked exactly like a single one
	-- and the obvious question was which button it was editing. The answer is
	-- none of them on their own: it edits what they have in common.
	if numediting > 1 then
		title:setText("EDIT " .. numediting .. " BUTTONS")
	else
		title:setText("EDIT BUTTON")
	end
	title:setGravity(GRAVITY_CENTER)
	title:setTextColor(Color:argb(255, 0xF2, 0xF4, 0xF6))
	title:setBackgroundColor(Color:argb(255, 0x1E, 0x21, 0x26))
	do
		local Typeface = luajava.bindClass("android.graphics.Typeface")
		title:setTypeface(Typeface.DEFAULT_BOLD)
	end
	title:setId(1)
	top:addView(title)

	--make the new tabhost.	
	local params = luajava.new(LinearLayoutParams,WRAP_CONTENT,WRAP_CONTENT)
	local fillparams = luajava.new(LinearLayoutParams,FILL_PARENT,WRAP_CONTENT,1)
	editorFillparams = fillparams
	local contentparams = luajava.new(LinearLayoutParams,FILL_PARENT,WRAP_CONTENT)

	local hostparams = luajava.new(LinearLayoutParams,FILL_PARENT,WRAP_CONTENT,2)
	local host = luajava.new(TabHost,context)

	host:setId(3)
	host:setLayoutParams(hostparams)
	
	
	--make the done and cancel buttons.
	--have to stuff them in linearlayout.
	local finishHolderParams = luajava.new(LinearLayoutParams,FILL_PARENT,WRAP_CONTENT)
	--finishHolderParams:addRule(RelativeLayout.BELOW,3)
	local finishHolder = luajava.new(LinearLayout,context)
	finishHolder:setLayoutParams(finishHolderParams)
	finishHolder:setId(2)
	
	--finishbuttonParams = luajava.new(RelativeLayoutParams,RLayoutParams.FILL_PARENT,WRAP_CONTENT)
	local done = luajava.new(Button,context)
	done:setLayoutParams(fillparams)
	done:setText("Done")
	done:setOnClickListener(doneClickListener)
	
	local cancel = luajava.new(Button,context)
	cancel:setLayoutParams(fillparams)
	cancel:setText("Cancel")
	cancel:setOnClickListener(cancelClickListener)
	finishHolder:addView(cancel)
	finishHolder:addView(done)
	top:addView(host)
	top:addView(finishHolder)
	
	
	local holder = luajava.new(LinearLayout,context)
	holder:setOrientation(LinearLayout.VERTICAL)
	holder:setLayoutParams(fillparams)
	
	local widget = luajava.new(TabWidget,context)
	widget:setId(android_R_id.tabs)

	local tabWidgetParams = luajava.new(LinearLayoutParams, FILL_PARENT, tabMinHeight)
	widget:setLayoutParams(tabWidgetParams)
	-- Editing several buttons at once registers one tab, not four; the weight
	-- sum has to follow or the single tab renders a quarter of the way across.
	if numediting > 1 then
		widget:setWeightSum(1)
	else
		widget:setWeightSum(4)
	end
	
	local content = luajava.new(FrameLayout,context)
	content:setId(android_R_id.tabcontent)
	content:setLayoutParams(contentparams)
	holder:addView(widget)
	holder:addView(content)
	
	host:addView(holder)
	host:setup()
	
	
	local buildtabs = require("buttoneditor_buildtabs")
	local tabState = {
		editorValues = editorValues,
		numediting = numediting,
		fillparams = fillparams,
		clickLabelEditParams = nil,
		context = context,
		textSize = textSize,
		textSizeSmall = textSizeSmall,
		addHelpText = addHelpText,
		makeTabLabel = makeTabLabel,
		trackSwipeEditForFlip = trackSwipeEditForFlip,
		updateFlipForSwipes = updateFlipForSwipes,
		widgets = {},
	}
	-- Wire the lock table BEFORE buildTabs. buildTabs disables swipe fields and
	-- marks them here, then calls updateFlipForSwipes; trimSwipeCmdText must
	-- already see that same table or Flip stays editable on first open.
	tabState.accordionLockedSwipeEdits = {}
	accordionLockedSwipeEdits = tabState.accordionLockedSwipeEdits
	buildtabs.buildClickTab(host, content, tabState)
	editorClickLabelEditParams = tabState.clickLabelEditParams
	local w = tabState.widgets
	clickLabelEdit = w.clickLabelEdit
	clickCmdEdit = w.clickCmdEdit
	flipLabelEdit = w.flipLabelEdit
	flipCmdEdit = w.flipCmdEdit
	flipSwipeNote = w.flipSwipeNote
	accordionFlipLockNote = w.accordionFlipLockNote
	local clickLabelEditParams = tabState.clickLabelEditParams
	buildtabs.buildTabs(host, content, tabState)
	w = tabState.widgets
	holdCmdEdit = w.holdCmdEdit
	swipeUpCmdEdit = w.swipeUpCmdEdit
	swipeDownCmdEdit = w.swipeDownCmdEdit
	swipeLeftCmdEdit = w.swipeLeftCmdEdit
	swipeRightCmdEdit = w.swipeRightCmdEdit
	swipeUpLeftCmdEdit = w.swipeUpLeftCmdEdit
	swipeUpRightCmdEdit = w.swipeUpRightCmdEdit
	swipeDownLeftCmdEdit = w.swipeDownLeftCmdEdit
	swipeDownRightCmdEdit = w.swipeDownRightCmdEdit
	gestureLabelCb = w.gestureLabelCb
	gestureHintsCb = w.gestureHintsCb
	accordionDirSpinner = w.accordionDirSpinner
	accordionLayoutSpinner = w.accordionLayoutSpinner
	accordionTriggerSpinner = w.accordionTriggerSpinner
	accordionHoldMsEdit = w.accordionHoldMsEdit
	accordionLanesEdit = w.accordionLanesEdit
	accordionAutoCloseCheck = w.accordionAutoCloseCheck
	accordionChildLabelEdits = w.accordionChildLabelEdits
	accordionChildCmdEdits = w.accordionChildCmdEdits
	-- Keep pointing at the table buildTabs mutated (do not replace it).
	if tabState.accordionLockedSwipeEdits ~= nil then
		accordionLockedSwipeEdits = tabState.accordionLockedSwipeEdits
	end
	harvestAccordionChildren = tabState.harvestAccordionChildren
	if tabState.gestureLabelCarried ~= nil then gestureLabelCarried = tabState.gestureLabelCarried end
	if tabState.gestureHintsCarried ~= nil then gestureHintsCarried = tabState.gestureHintsCarried end
	if tabState.carriedDiagonalSwipes ~= nil then carriedDiagonalSwipes = tabState.carriedDiagonalSwipes end
	-- Re-apply after wiring: belt-and-braces if a future build path locks after
	-- the in-build updateFlipForSwipes call.
	updateFlipForSwipes()

	local tabOthers = host:newTabSpec("tab_others_btn_tab")
	local labelOthers = makeTabLabel("Others")
	
	--tmpview3 = luajava.new(TextView,context)
	--tmpview3:setText("third page")
	--tmpview3:setId(3)
	--tmpview3:setLayoutParams(params);	
	advancedEditor = require("buttoneditoradvanced")
	advancedEditor.init(context)
	-- Registered before makeUI: makeUI ticks the box to match the button, which
	-- fires the listener, and the listener kept from the previous dialog would
	-- otherwise reach into that dialog's dead widgets.
	advancedEditor.setFloatingChangedCallback(function(isFloating)
		if tabState.updateAccordionEnabled ~= nil then
			tabState.updateAccordionEnabled(isFloating)
		end
	end)
	local scrollerpage = advancedEditor.makeUI(editorValues,numediting)
	local parent = scrollerpage:getParent()
	if(parent ~= nil) then
		parent:removeView(scrollerpage)
	end
	--buttonNameRow:setVisibility(View.VISIBLE)
	
	
	Validator:reset()
	if(editorValues.width ~= "MULTI") then
		Validator:add(advancedEditor.getWidthEdit(),Validator_Number_Not_Blank,"Width")
	else
		Validator:add(advancedEditor.getWidthEdit(),Validator_Number_Or_Blank,"Width")
	end
	
	if(editorValues.height ~= "MULTI") then
		Validator:add(advancedEditor.getHeightEdit(),Validator_Number_Not_Blank,"Height")
	else
		Validator:add(advancedEditor.getHeightEdit(),Validator_Number_Or_Blank,"Height")
	end
	
	if(editorValues.x ~= "MULTI") then
		Validator:add(advancedEditor.getXCoordEdit(),Validator_Number_Not_Blank,"X Coordinate")
	else
		Validator:add(advancedEditor.getXCoordEdit(),Validator_Number_Or_Blank,"X Coordinate")
	end
	
	if(editorValues.y ~="MULTI") then
		Validator:add(advancedEditor.getYCoordEdit(),Validator_Number_Not_Blank,"Y Coordinate")
	else
		Validator:add(advancedEditor.getYCoordEdit(),Validator_Number_Or_Blank,"Y Coordinate")
	end
	
	if(editorValues.labelSize ~= "MULTI") then
		Validator:add(advancedEditor.getLabelSizeEdit(),Validator_Number_Not_Blank,"Label size")
	else
		Validator:add(advancedEditor.getLabelSizeEdit(),Validator_Number_Or_Blank,"Label size")
	end
	
	content:addView(scrollerpage)
	tabOthers:setIndicator(labelOthers)
	tabOthers:setContent(5)

	local tabs = tabState.tabs
	if tabs == nil or tabs.click == nil or tabs.swipe == nil or tabs.accordion == nil then
		error("button editor: tab specs were not built in buttoneditor_buildtabs")
	end
	-- Editing several buttons at once, Tap, Swipe and Accord. had every field
	-- greyed out: three tabs of dead boxes, and no word about which button was
	-- being edited. A label, a command, a gesture and an accordion each belong
	-- to one button, so for a multi-button edit those tabs are not registered at
	-- all and only Others -- the one that does something -- is left.
	--
	-- The pages behind them are still built and still hold their widgets: Done
	-- reads every field on the way out, and a page that was never built would
	-- take the dialog down with it. They are hidden here instead, after setup
	-- and after the tabs are registered, because TabHost only manages the
	-- visibility of content it knows about and would otherwise leave them
	-- stacked over the Others page.
	local multi = numediting > 1
	if not multi then
		host:addTab(tabs.click)
		host:addTab(tabs.swipe)
		host:addTab(tabs.accordion)
	end
	host:addTab(tabOthers)
	host:setCurrentTab(0)

	if multi then
		local hidden = {
			tabState.widgets.clickPageScroller,
			tabState.widgets.swipePageScroller,
			tabState.widgets.accordionPageScroller,
		}
		for i = 1, #hidden do
			if hidden[i] ~= nil then
				hidden[i]:setVisibility(View.GONE)
			end
		end
	end

	addEditorFooterHelp(finishHolder, host, numediting)

	--dialogView = top
	--else
		--set up the dialog
		--Note("already constructed editor"..dialogView:toString())
	--end
	
	editorDialog = luajava.newInstance("com.resurrection.blowtorch2.lib.window.LuaDialog",context,top,false,nil)
	-- Opt-in before show(): onCreate reads this to set ADJUST_RESIZE and pad for
	-- IME insets. Default stays off for every other LuaDialog host.
	editorDialog:setAdjustForIme(true)
	editorDialog:show()
	context = nil
end


cancelClickListener = luajava.createProxy("android.view.View$OnClickListener",{
  onClick = function(v) editorDialog:dismiss() end
})

doneClickListener = luajava.createProxy("android.view.View$OnClickListener",{
  onClick = function(v) 
    local str = Validator:validate()
    if(str ~= nil) then
    -- `v` is the clicked view. `view` was a buttonwindow.lua global that
    -- module(...) cut off, so it was nil here and the validation message
    -- raised instead of showing.
    Validator:showMessage(v:getContext(),str)
      return
    end
    
    --gather up editor data to pass back into the main button window callback
    local d = {}

    -- Accordion-locked gesture fields are disabled but must keep their text:
    -- unticking the accordion should bring the command back. Read getText even
    -- when disabled (unlike trimSwipeCmdText, which is Flip-only).
    local function fieldText(edit)
      if edit == nil then
        return ""
      end
      local t = edit:getText()
      if t == nil then
        return ""
      end
      return t:toString() or ""
    end

  
    d.label = clickLabelEdit:getText():toString()
    --label = labeltmp:toString()
    d.cmd = fieldText(clickCmdEdit)
    --cmd = cmdtmp:toString()
    d.flipLabel = flipLabelEdit:getText():toString()
    --fliplabel = fliplabeltmp:toString()
    d.flipCmd = flipCmdEdit:getText():toString()
    d.holdCommand = fieldText(holdCmdEdit)
    d.swipeUpCommand = fieldText(swipeUpCmdEdit)
    d.swipeDownCommand = fieldText(swipeDownCmdEdit)
    d.swipeLeftCommand = fieldText(swipeLeftCmdEdit)
    d.swipeRightCommand = fieldText(swipeRightCmdEdit)

    -- Gesture fields are disabled when several buttons are edited at once, and a
    -- disabled field reads back empty. Fall back to what the button had on open
    -- so a multi-button edit does not blank out diagonals it never displayed.
    local function diagonalValue(edit, field)
      if edit == nil or not edit:isEnabled() then
        return carriedDiagonalSwipes[field] or ""
      end
      return edit:getText():toString()
    end
    d.swipeUpLeftCommand = diagonalValue(swipeUpLeftCmdEdit, "swipeUpLeftCommand")
    d.swipeUpRightCommand = diagonalValue(swipeUpRightCmdEdit, "swipeUpRightCommand")
    d.swipeDownLeftCommand = diagonalValue(swipeDownLeftCmdEdit, "swipeDownLeftCommand")
    d.swipeDownRightCommand = diagonalValue(swipeDownRightCmdEdit, "swipeDownRightCommand")
    if gestureLabelCb ~= nil and gestureLabelCb:isEnabled() then
      d.showGestureLabel = gestureLabelCb:isChecked()
    else
      d.showGestureLabel = gestureLabelCarried
    end
    if gestureHintsCb ~= nil and gestureHintsCb:isEnabled() then
      d.showGestureHints = gestureHintsCb:isChecked()
    else
      d.showGestureHints = gestureHintsCarried
    end

    local tmp = advancedEditor.getEditorValues()
    
    for i,v in pairs(tmp) do
      d[i] = v;
    end
    
    if accordionDirSpinner ~= nil then
      local dirIndex = tonumber(accordionDirSpinner:getSelectedItemPosition()) or 0
      local dirMap = {"", "down", "up", "right", "left"}
      d.accordionDirection = dirMap[dirIndex + 1] or ""
      if accordionLayoutSpinner ~= nil then
        local layoutIndex = tonumber(accordionLayoutSpinner:getSelectedItemPosition()) or 0
        local layoutMap = {"along", "vertical", "horizontal"}
        d.accordionChildLayout = layoutMap[layoutIndex + 1] or "along"
      end
      local triggerIndex = tonumber(accordionTriggerSpinner:getSelectedItemPosition()) or 0
      local triggerMap = {"tap", "hold", "swipe"}
      d.accordionTrigger = triggerMap[triggerIndex + 1] or "tap"
      d.accordionHoldMs = tonumber(accordionHoldMsEdit:getText():toString()) or 450
      -- wrapAfter stays 0: Columns/Rows (accordionLanes) is the player-facing
      -- control. Type 2 → two columns; overlay derives wrapAfter from that.
      d.accordionWrapAfter = 0
      local lanes = 0
      if accordionLanesEdit ~= nil then
        lanes = tonumber(accordionLanesEdit:getText():toString()) or 0
      end
      if lanes < 0 then lanes = 0 end
      if lanes > 5 then lanes = 5 end
      if lanes == 1 then lanes = 0 end
      d.accordionLanes = math.floor(lanes)
      d.accordionAutoClose = accordionAutoCloseCheck:isChecked()
      if harvestAccordionChildren ~= nil then
        d.accordionChildren = harvestAccordionChildren()
      else
        d.accordionChildren = {}
        local n = 0
        if accordionChildLabelEdits ~= nil then
          n = #accordionChildLabelEdits
        end
        for i = 1, n do
          local labelEdit = accordionChildLabelEdits[i]
          local cmdEdit = accordionChildCmdEdits[i]
          if labelEdit ~= nil and cmdEdit ~= nil then
            local label = labelEdit:getText():toString()
            local cmd = cmdEdit:getText():toString()
            if label ~= "" or cmd ~= "" then
              table.insert(d.accordionChildren, {label = label, command = cmd})
            end
          end
        end
      end
    end
    
    buttonEditorDone(d)
    
    --[[nametmp = buttonNameEdit:getText()
    name = nametmp:toString()
    targettmp = buttonTargetSetEdit:getText();
    target = targettmp:toString();
    
    xcoordtmp = xcoordEdit:getText()
    xcoord = tonumber(xcoordtmp:toString())
    ycoordtmp = ycoordEdit:getText()
    ycoord = tonumber(ycoordtmp:toString())
    labelsizetmp = labelSizeEdit:getText()
    labelsize = tonumber(labelsizetmp:toString())
    ----Note(
    heighttmp = heightEdit:getText()
    
    height = tonumber(heighttmp:toString())
    --Note("height read from editor"..height)
    widthtmp = widthEdit:getText()
    width = tonumber(widthtmp:toString())]]
      
    editorDialog:dismiss()
  end
})

