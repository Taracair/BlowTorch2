local LinearLayout = _G["LinearLayout"]
local luajava = _G["luajava"]
local ScrollView = _G["ScrollView"]
local Gravity = _G["Gravity"]
local LinearLayoutParams = _G["LinearLayoutParams"]
local TextView = _G["TextView"]
local EditText = _G["EditText"]
local Button = _G["Button"]
local Spinner = _G["Spinner"]
local CheckBox = _G["CheckBox"]
local ArrayAdapter = _G["ArrayAdapter"]
local ColorDrawable = luajava.bindClass("android.graphics.drawable.ColorDrawable")
local FILL_PARENT = LinearLayoutParams.FILL_PARENT
local WRAP_CONTENT = LinearLayoutParams.WRAP_CONTENT
local Color = _G["Color"]
local View = _G["View"]
local TYPE_TEXT_FLAG_MULTI_LINE = _G["TYPE_TEXT_FLAG_MULTI_LINE"]
local TYPE_CLASS_TEXT = _G["TYPE_CLASS_TEXT"]
-- Flag-only MULTI_LINE is still single-line (AOSP isMultilineInputType).
local MULTILINE_TEXT = TYPE_CLASS_TEXT + TYPE_TEXT_FLAG_MULTI_LINE
local density = _G["density"]
local PluginXCallS = _G["PluginXCallS"]
local drawButtons = _G["drawButtons"]
local view = _G["view"]
-- module(...) below swaps the environment, so a bare assignment here writes into
-- the module table and button.lua's drawing code (which reads the real globals)
-- never sees it. Keep the table itself and assign through it.
local globals = _G
local pairs = _G["pairs"]
local ipairs = _G["ipairs"]
local tostring = _G["tostring"]
local tonumber = _G["tonumber"]
local math = _G["math"]
local table = _G["table"]
local string = _G["string"]
module(...)

-- Editor cap. Runtime may raise its own draw limit separately; the editor is
-- the place that must stop the player at twenty with a visible sentence.
local MAX_ACCORDION_CHILDREN = 20

local SECTION_TEXT = Color:argb(255, 0x9F, 0xB6, 0xD8)
local SECTION_FILL = Color:argb(255, 0x1B, 0x1F, 0x25)
local DESC_TEXT = Color:argb(255, 0x9A, 0xA3, 0xAD)

local function addSectionBar(parent, text, o)
	local header = luajava.new(TextView, o.context)
	header:setText(text)
	header:setTextSize(12)
	header:setTextColor(SECTION_TEXT)
	header:setBackgroundColor(SECTION_FILL)
	local pad = math.floor(10 * density)
	header:setPadding(pad, math.floor(8 * density), pad, math.floor(8 * density))
	header:setMinHeight(math.floor(34 * density))
	header:setGravity(Gravity.CENTER_VERTICAL)
	header:setLayoutParams(o.fillparams)
	parent:addView(header)
end

local function addOneLiner(parent, text, o)
	local help = luajava.new(TextView, o.context)
	help:setText(text)
	help:setTextSize(o.textSizeSmall)
	help:setTextColor(DESC_TEXT)
	help:setMaxLines(2)
	local pad = math.floor(8 * density)
	help:setPadding(pad, math.floor(4 * density), pad, math.floor(4 * density))
	help:setLayoutParams(o.fillparams)
	parent:addView(help)
	return help
end

-- Mirror buttonwindow.accordionStackVertical: column vs row decides the words
-- on insert/move/add controls (Above/Below vs Left/Right).
local function accordionStackVertical(dir, layout)
	if layout == "vertical" then
		return true
	end
	if layout == "horizontal" then
		return false
	end
	return dir ~= "right" and dir ~= "left"
end

-- before/after = per-row insert labels; addWord = suffix on the end affordance.
local function accordionDirectionWords(dir, layout)
	if accordionStackVertical(dir, layout) then
		local addWord = "below"
		if dir == "up" then
			addWord = "above"
		end
		return { before = "Above", after = "Below", addWord = addWord }
	end
	local addWord = "right"
	if dir == "left" then
		addWord = "left"
	end
	return { before = "Left", after = "Right", addWord = addWord }
end

local function editTextString(edit)
	if edit == nil then
		return ""
	end
	local t = edit:getText()
	if t == nil then
		return ""
	end
	local s = t:toString()
	if s == nil then
		return ""
	end
	return s
end

local function childRowHasContent(label, command, id)
	if id ~= nil and id ~= "" then
		return true
	end
	return (label ~= nil and label ~= "") or (command ~= nil and command ~= "")
end

function buildClickTab(host, content, o)
	local editorValues = o.editorValues
	local textSize = o.textSize
	local textSizeSmall = o.textSizeSmall
	o.widgets = o.widgets or {}

	local tab1 = host:newTabSpec("tab_one_btn_tab")
	local label1 = o.makeTabLabel("Tap / Flip")

	local clickPageScroller = luajava.new(ScrollView, o.context)
	clickPageScroller:setLayoutParams(o.fillparams)
	clickPageScroller:setId(1)

	local clickPage = luajava.new(LinearLayout, o.context)
	clickPage:setLayoutParams(o.fillparams)
	clickPage:setId(11)
	clickPage:setOrientation(LinearLayout.VERTICAL)

	local function addGestureSection(title, explanation)
		addSectionBar(clickPage, title, o)
		addOneLiner(clickPage, explanation, o)
	end

	addGestureSection("TAP", "Sends when you release")

	local clickLabelRow = luajava.new(LinearLayout, o.context)
	clickLabelRow:setLayoutParams(o.fillparams)

	local clickLabel = luajava.new(TextView, o.context)
	clickLabel:setTextSize(textSize)
	clickLabel:setText("Label:")
	clickLabel:setGravity(Gravity.RIGHT)
	local clickLabelParams = luajava.new(LinearLayoutParams, 80 * density, WRAP_CONTENT)
	clickLabel:setLayoutParams(clickLabelParams)

	local clickLabelEditParams = luajava.new(LinearLayoutParams, FILL_PARENT, WRAP_CONTENT)
	o.clickLabelEditParams = clickLabelEditParams

	local clickLabelEdit = luajava.new(EditText, o.context)
	clickLabelEdit:setTextSize(textSize)
	clickLabelEdit:setInputType(MULTILINE_TEXT)
	clickLabelEdit:setHorizontallyScrolling(false)
	clickLabelEdit:setMinLines(1)
	clickLabelEdit:setMaxLines(4)
	clickLabelEdit:setLayoutParams(clickLabelEditParams)
	if o.numediting > 1 then
		clickLabelEdit:setEnabled(false)
	elseif editorValues.label ~= nil then
		clickLabelEdit:setText(editorValues.label)
	end
	clickLabelRow:addView(clickLabel)
	clickLabelRow:addView(clickLabelEdit)

	local clickCmdRow = luajava.new(LinearLayout, o.context)
	clickCmdRow:setLayoutParams(o.fillparams)

	local clickCmdLabel = luajava.new(TextView, o.context)
	clickCmdLabel:setTextSize(textSize)
	clickCmdLabel:setText("CMD:")
	clickCmdLabel:setGravity(Gravity.RIGHT)
	clickCmdLabel:setLayoutParams(clickLabelParams)

	local clickCmdEdit = luajava.new(EditText, o.context)
	clickCmdEdit:setTextSize(textSize)
	clickCmdEdit:setInputType(TYPE_TEXT_FLAG_MULTI_LINE)
	clickCmdEdit:setHorizontallyScrolling(false)
	clickCmdEdit:setMaxLines(1000)
	clickCmdEdit:setLayoutParams(clickLabelEditParams)
	if o.numediting > 1 then
		clickCmdEdit:setEnabled(false)
	elseif editorValues.command ~= nil then
		clickCmdEdit:setText(editorValues.command)
	end
	clickCmdRow:addView(clickCmdLabel)
	clickCmdRow:addView(clickCmdEdit)
	clickPage:addView(clickLabelRow)
	clickPage:addView(clickCmdRow)

	-- Accordion trigger exclusivity: shown when Open with = Tap and the accordion
	-- is configured. Same pattern as flipSwipeNote below.
	local accordionTapLockNote = luajava.new(TextView, o.context)
	accordionTapLockNote:setTextSize(textSizeSmall)
	accordionTapLockNote:setText("Tap opens the accordion — this command is kept but does not fire.")
	local tapLockPad = math.floor(8 * density)
	accordionTapLockNote:setPadding(tapLockPad, 0, tapLockPad, tapLockPad)
	accordionTapLockNote:setLayoutParams(o.fillparams)
	accordionTapLockNote:setVisibility(View.GONE)
	clickPage:addView(accordionTapLockNote)

	addGestureSection("FLIP", "Drag off, then release")

	local flipHeaderPad = math.floor(8 * density)
	local flipSwipeNote = luajava.new(TextView, o.context)
	flipSwipeNote:setTextSize(textSizeSmall)
	flipSwipeNote:setText("This button has a swipe command, so flip does not run.")
	flipSwipeNote:setPadding(flipHeaderPad, 0, flipHeaderPad, flipHeaderPad)
	flipSwipeNote:setLayoutParams(o.fillparams)
	flipSwipeNote:setVisibility(View.GONE)
	clickPage:addView(flipSwipeNote)

	-- Same slot as flipSwipeNote: swipe-to-expand locks Flip even when every
	-- swipe command is empty. Only one of the two notes is visible at a time.
	local accordionFlipLockNote = luajava.new(TextView, o.context)
	accordionFlipLockNote:setTextSize(textSizeSmall)
	accordionFlipLockNote:setText("Swipe opens the accordion — flip does not run.")
	accordionFlipLockNote:setPadding(flipHeaderPad, 0, flipHeaderPad, flipHeaderPad)
	accordionFlipLockNote:setLayoutParams(o.fillparams)
	accordionFlipLockNote:setVisibility(View.GONE)
	clickPage:addView(accordionFlipLockNote)

	local flipLabelRow = luajava.new(LinearLayout, o.context)
	flipLabelRow:setLayoutParams(o.fillparams)

	local flipLabel = luajava.new(TextView, o.context)
	flipLabel:setTextSize(textSize)
	flipLabel:setText("Label:")
	flipLabel:setGravity(Gravity.RIGHT)
	flipLabel:setLayoutParams(clickLabelParams)

	local flipLabelEdit = luajava.new(EditText, o.context)
	flipLabelEdit:setTextSize(textSize)
	flipLabelEdit:setInputType(MULTILINE_TEXT)
	flipLabelEdit:setHorizontallyScrolling(false)
	flipLabelEdit:setMinLines(1)
	flipLabelEdit:setMaxLines(4)
	flipLabelEdit:setLayoutParams(clickLabelEditParams)
	if o.numediting > 1 then
		flipLabelEdit:setEnabled(false)
	elseif editorValues.flipLabel ~= nil then
		flipLabelEdit:setText(editorValues.flipLabel)
	end
	flipLabelRow:addView(flipLabel)
	flipLabelRow:addView(flipLabelEdit)

	local flipCmdRow = luajava.new(LinearLayout, o.context)
	flipCmdRow:setLayoutParams(o.fillparams)

	local flipCmdLabel = luajava.new(TextView, o.context)
	flipCmdLabel:setTextSize(textSize)
	flipCmdLabel:setText("CMD:")
	flipCmdLabel:setGravity(Gravity.RIGHT)
	flipCmdLabel:setLayoutParams(clickLabelParams)

	local flipCmdEdit = luajava.new(EditText, o.context)
	flipCmdEdit:setTextSize(textSize)
	flipCmdEdit:setInputType(TYPE_TEXT_FLAG_MULTI_LINE)
	flipCmdEdit:setHorizontallyScrolling(false)
	flipCmdEdit:setMaxLines(1000)
	flipCmdEdit:setLayoutParams(clickLabelEditParams)
	if o.numediting > 1 then
		flipCmdEdit:setEnabled(false)
	elseif editorValues.flipCommand ~= nil then
		flipCmdEdit:setText(editorValues.flipCommand)
	end
	flipCmdRow:addView(flipCmdLabel)
	flipCmdRow:addView(flipCmdEdit)
	clickPage:addView(flipLabelRow)
	clickPage:addView(flipCmdRow)

	clickPageScroller:addView(clickPage)
	content:addView(clickPageScroller)
	o.widgets.clickPageScroller = clickPageScroller
	tab1:setIndicator(label1)
	tab1:setContent(1)
	o.tabs = o.tabs or {}
	o.tabs.click = tab1

	o.widgets.clickLabelEdit = clickLabelEdit
	o.widgets.clickCmdEdit = clickCmdEdit
	o.widgets.flipLabelEdit = flipLabelEdit
	o.widgets.flipCmdEdit = flipCmdEdit
	o.widgets.flipSwipeNote = flipSwipeNote
	o.widgets.accordionFlipLockNote = accordionFlipLockNote
	o.widgets.accordionTapLockNote = accordionTapLockNote
end

function buildTabs(host, content, o)
	local editorValues = o.editorValues
	local textSize = o.textSize
	local textSizeSmall = o.textSizeSmall
	o.widgets = o.widgets or {}
	local tabSwipe = host:newTabSpec("tab_swipe_btn_tab")
	local labelSwipe = o.makeTabLabel("Swipe")
	
	local swipePageScroller = luajava.new(ScrollView,o.context)
	swipePageScroller:setLayoutParams(o.fillparams)
	swipePageScroller:setId(3)
	
	local swipePage = luajava.new(LinearLayout,o.context)
	swipePage:setLayoutParams(o.fillparams)
	swipePage:setId(33)
	swipePage:setOrientation(LinearLayout.VERTICAL)
	
	local Spinner = luajava.bindClass("android.widget.Spinner")
	local ArrayAdapter = luajava.bindClass("android.widget.ArrayAdapter")
	local CheckBox = luajava.bindClass("android.widget.CheckBox")
	local ColorDrawable = luajava.bindClass("android.graphics.drawable.ColorDrawable")
	local pkg = o.context:getPackageName()
	local res = o.context:getResources()
	local spinnerItemLayout = res:getIdentifier("spinner_item_dark", "layout", pkg)
	local spinnerDropdownLayout = res:getIdentifier("spinner_dropdown_item_dark", "layout", pkg)
	-- Fully opaque black popup (ARGB via Color.argb — avoids Lua int/sign issues).
	local spinnerPopupBg = luajava.new(ColorDrawable, Color:argb(255, 0, 0, 0))
	local function makeSpinnerAdapter(items)
		local adapter = luajava.new(ArrayAdapter, o.context, spinnerItemLayout)
		for i = 1, #items do
			adapter:add(items[i])
		end
		adapter:setDropDownViewResource(spinnerDropdownLayout)
		return adapter
	end
	local function styleSpinner(spinner)
		spinner:setPopupBackgroundDrawable(spinnerPopupBg)
		spinner:setBackgroundColor(Color:argb(255, 0, 0, 0))
	end

	-- Per button, not profile-wide. It used to be the profile switch shown here,
	-- which is why turning it off on one button turned it off on all of them
	-- while the switch below it changed only one — three switches side by side
	-- with two of them secretly global. The profile switch is still in the
	-- editor settings sheet and still wins: with it off nothing is drawn
	-- anywhere, and with it on this decides for this tile.
	o.widgets.gestureHintsCb = luajava.new(CheckBox,o.context)
	local gestureHintsCb = o.widgets.gestureHintsCb
	gestureHintsCb:setText("Show swipe letters, corner arrows, hold and accordion badges")
	gestureHintsCb:setChecked(editorValues.showGestureHintsButton ~= false)
	if o.numediting > 1 then
		gestureHintsCb:setEnabled(false)
	end
	swipePage:addView(gestureHintsCb)

	local function addGestureRow(parent, labelText, initialValue)
		local row = luajava.new(LinearLayout,o.context)
		row:setLayoutParams(o.fillparams)
		local label = luajava.new(TextView,o.context)
		label:setTextSize(textSize)
		label:setText(labelText)
		label:setGravity(Gravity.RIGHT)
		local labelParams = luajava.new(LinearLayoutParams,100*density,WRAP_CONTENT)
		label:setLayoutParams(labelParams)
		local edit = luajava.new(EditText,o.context)
		edit:setTextSize(textSize)
		edit:setInputType(TYPE_TEXT_FLAG_MULTI_LINE)
		edit:setHorizontallyScrolling(false)
		edit:setMaxLines(4)
		edit:setLayoutParams(o.clickLabelEditParams)
		if(o.numediting > 1) then
			edit:setEnabled(false)
		elseif(initialValue ~= nil) then
			edit:setText(initialValue)
		end
		row:addView(label)
		row:addView(edit)
		parent:addView(row)
		return edit
	end
	
	local function addSectionHeader(parent, text)
		addSectionBar(parent, text, o)
	end

	local function addSwipeRow(parent, labelText, initialValue)
		return o.trackSwipeEditForFlip(addGestureRow(parent, labelText, initialValue))
	end

	o.widgets.holdCmdEdit = addGestureRow(swipePage, "Hold:", editorValues.holdCommand)
	local accordionHoldLockNote = luajava.new(TextView, o.context)
	accordionHoldLockNote:setTextSize(textSizeSmall)
	accordionHoldLockNote:setText("Hold opens the accordion — this command is kept but does not fire.")
	local holdLockPad = math.floor(8 * density)
	accordionHoldLockNote:setPadding(holdLockPad, 0, holdLockPad, holdLockPad)
	accordionHoldLockNote:setLayoutParams(o.fillparams)
	accordionHoldLockNote:setVisibility(View.GONE)
	swipePage:addView(accordionHoldLockNote)
	o.widgets.accordionHoldLockNote = accordionHoldLockNote

	local function makeSwipeLockNote(msg)
		local note = luajava.new(TextView, o.context)
		note:setTextSize(textSizeSmall)
		note:setText(msg)
		note:setPadding(holdLockPad, 0, holdLockPad, holdLockPad)
		note:setLayoutParams(o.fillparams)
		note:setVisibility(View.GONE)
		return note
	end

	-- One note under each swipe row (same pattern as the tap-command lock),
	-- visibility follows the Accordion Expand spinner.
	addSectionHeader(swipePage, "STRAIGHT SWIPES")
	o.widgets.swipeUpCmdEdit = addSwipeRow(swipePage, "↑  Up:", editorValues.swipeUpCommand)
	o.widgets.accordionSwipeLockNoteUp = makeSwipeLockNote(
		"Up swipe opens the accordion — this command is kept but does not fire.")
	swipePage:addView(o.widgets.accordionSwipeLockNoteUp)
	o.widgets.swipeDownCmdEdit = addSwipeRow(swipePage, "↓  Down:", editorValues.swipeDownCommand)
	o.widgets.accordionSwipeLockNoteDown = makeSwipeLockNote(
		"Down swipe opens the accordion — this command is kept but does not fire.")
	swipePage:addView(o.widgets.accordionSwipeLockNoteDown)
	o.widgets.swipeLeftCmdEdit = addSwipeRow(swipePage, "←  Left:", editorValues.swipeLeftCommand)
	o.widgets.accordionSwipeLockNoteLeft = makeSwipeLockNote(
		"Left swipe opens the accordion — this command is kept but does not fire.")
	swipePage:addView(o.widgets.accordionSwipeLockNoteLeft)
	o.widgets.swipeRightCmdEdit = addSwipeRow(swipePage, "→  Right:", editorValues.swipeRightCommand)
	o.widgets.accordionSwipeLockNoteRight = makeSwipeLockNote(
		"Right swipe opens the accordion — this command is kept but does not fire.")
	swipePage:addView(o.widgets.accordionSwipeLockNoteRight)

	o.gestureLabelCarried = editorValues.showGestureLabel ~= false
	o.gestureHintsCarried = editorValues.showGestureHintsButton ~= false

	o.carriedDiagonalSwipes = {
		swipeUpLeftCommand = editorValues.swipeUpLeftCommand or "",
		swipeUpRightCommand = editorValues.swipeUpRightCommand or "",
		swipeDownLeftCommand = editorValues.swipeDownLeftCommand or "",
		swipeDownRightCommand = editorValues.swipeDownRightCommand or "",
	}

	-- Nine command rows is a lot to take in, and most buttons only ever use the
	-- four straight directions. Keep the diagonals folded away, and unfold them
	-- automatically for a button that already uses one so nothing hides.
	local diagonalsInUse = false
	for _, value in pairs(o.carriedDiagonalSwipes) do
		if value ~= "" then diagonalsInUse = true end
	end

	local diagonalBox = luajava.new(LinearLayout, o.context)
	diagonalBox:setLayoutParams(o.fillparams)
	diagonalBox:setOrientation(LinearLayout.VERTICAL)

	local diagonalsCb = luajava.new(CheckBox, o.context)
	diagonalsCb:setText("Diagonal swipes   ↖ ↗ ↙ ↘")
	diagonalsCb:setChecked(diagonalsInUse)
	diagonalsCb:setOnCheckedChangeListener(luajava.createProxy("android.widget.CompoundButton$OnCheckedChangeListener",{
		onCheckedChanged = function(v, isChecked)
			if isChecked then
				diagonalBox:setVisibility(View.VISIBLE)
			else
				diagonalBox:setVisibility(View.GONE)
			end
		end
	}))
	swipePage:addView(diagonalsCb)
	swipePage:addView(diagonalBox)
	if diagonalsInUse then
		diagonalBox:setVisibility(View.VISIBLE)
	else
		diagonalBox:setVisibility(View.GONE)
	end

	o.widgets.gestureLabelCb = luajava.new(CheckBox,o.context)
	local gestureLabelCb = o.widgets.gestureLabelCb
	-- "this button" spelled out, because the two switches that used to sit
	-- beside it looked identical and were profile-wide.
	gestureLabelCb:setText("Name the command above this button while gesturing")
	gestureLabelCb:setChecked(editorValues.showGestureLabel ~= false)
	if o.numediting > 1 then
		gestureLabelCb:setEnabled(false)
	end
	swipePage:addView(gestureLabelCb)

	o.widgets.swipeUpLeftCmdEdit = addSwipeRow(diagonalBox, "↖  Up-left:", editorValues.swipeUpLeftCommand)
	o.widgets.swipeUpRightCmdEdit = addSwipeRow(diagonalBox, "↗  Up-right:", editorValues.swipeUpRightCommand)
	o.widgets.swipeDownLeftCmdEdit = addSwipeRow(diagonalBox, "↙  Down-left:", editorValues.swipeDownLeftCommand)
	o.widgets.swipeDownRightCmdEdit = addSwipeRow(diagonalBox, "↘  Down-right:", editorValues.swipeDownRightCommand)
	
	swipePageScroller:addView(swipePage)
	content:addView(swipePageScroller)
	o.widgets.swipePageScroller = swipePageScroller
	tabSwipe:setIndicator(labelSwipe)
	tabSwipe:setContent(3)
	
	-- "Sub-btn layout:" is the longest label on this tab and it was cut off: the
	-- labels here never set a text size, so they drew at the system default
	-- rather than the editor's, in a box measured for the editor's. Both halves
	-- are fixed -- the size below, and a little more room here.
	local accordionLabelWidth = 104 * density

	local tabAccordion = host:newTabSpec("tab_accordion_btn_tab")
	local labelAccordion = o.makeTabLabel("Accord.")
	
	local accordionPageScroller = luajava.new(ScrollView,o.context)
	accordionPageScroller:setLayoutParams(o.fillparams)
	accordionPageScroller:setId(4)
	
	local accordionPage = luajava.new(LinearLayout,o.context)
	accordionPage:setLayoutParams(o.fillparams)
	accordionPage:setId(44)
	accordionPage:setOrientation(LinearLayout.VERTICAL)
	
	local dirRow = luajava.new(LinearLayout,o.context)
	dirRow:setLayoutParams(o.fillparams)
	local dirLabel = luajava.new(TextView,o.context)
	dirLabel:setText("Expand:")
	dirLabel:setTextSize(textSize)
	dirLabel:setGravity(Gravity.RIGHT)
	dirLabel:setLayoutParams(luajava.new(LinearLayoutParams,accordionLabelWidth,WRAP_CONTENT))
	o.widgets.accordionDirSpinner = luajava.new(Spinner,o.context)
	local accordionDirSpinner = o.widgets.accordionDirSpinner
	accordionDirSpinner:setLayoutParams(o.clickLabelEditParams)
	styleSpinner(accordionDirSpinner)
	local dirAdapter = makeSpinnerAdapter({"None", "Down", "Up", "Right", "Left"})
	accordionDirSpinner:setAdapter(dirAdapter)
	local currentDir = editorValues.accordionDirection or ""
	if currentDir == "down" then accordionDirSpinner:setSelection(1)
	elseif currentDir == "up" then accordionDirSpinner:setSelection(2)
	elseif currentDir == "right" then accordionDirSpinner:setSelection(3)
	elseif currentDir == "left" then accordionDirSpinner:setSelection(4)
	else accordionDirSpinner:setSelection(0) end
	-- A super button has no accordion: the fan is drawn on the button grid and
	-- the children only live while the parent is open, neither of which a
	-- floating window over the game can do. Saving strips it anyway
	-- (enforceNoAccordionOnSuperButton) — this is so the player is told here
	-- instead of finding out afterwards. Every field on this tab is greyed out,
	-- not only the direction: typing a sub-button command that is thrown away on
	-- Done is exactly the surprise this is here to prevent.
	--
	-- The note sits at the top of the tab so it is the first thing read. It is
	-- built once and shown or hidden, because the ticking of 'Float over the
	-- game' on the Others tab drives this live and re-adding it on each change
	-- would stack copies.
	local accordionSuperNote = luajava.new(TextView, o.context)
	accordionSuperNote:setTextSize(o.textSizeSmall)
	accordionSuperNote:setText(
		"This is a super button, so it cannot have an accordion: the "
		.. "sub-buttons are drawn on the button grid and only exist while "
		.. "the parent is open. Untick 'Float over the game' on the "
		.. "Others tab to use one.")
	local notePad = math.floor(8 * density)
	accordionSuperNote:setPadding(notePad, notePad, notePad, notePad)
	accordionSuperNote:setLayoutParams(o.fillparams)
	accordionSuperNote:setVisibility(View.GONE)
	accordionPage:addView(accordionSuperNote)
	o.widgets.accordionSuperNote = accordionSuperNote

	dirRow:addView(dirLabel)
	dirRow:addView(accordionDirSpinner)
	accordionPage:addView(dirRow)

	local layoutRow = luajava.new(LinearLayout,o.context)
	layoutRow:setLayoutParams(o.fillparams)
	local layoutLabel = luajava.new(TextView,o.context)
	layoutLabel:setText("Sub-btn layout:")
	layoutLabel:setTextSize(textSize)
	layoutLabel:setGravity(Gravity.RIGHT)
	layoutLabel:setLayoutParams(luajava.new(LinearLayoutParams,accordionLabelWidth,WRAP_CONTENT))
	o.widgets.accordionLayoutSpinner = luajava.new(Spinner,o.context)
	local accordionLayoutSpinner = o.widgets.accordionLayoutSpinner
	accordionLayoutSpinner:setLayoutParams(o.clickLabelEditParams)
	styleSpinner(accordionLayoutSpinner)
	local layoutAdapter = makeSpinnerAdapter({
		"Auto (follow expand)",
		"Vertical (column)",
		"Horizontal (row)"
	})
	accordionLayoutSpinner:setAdapter(layoutAdapter)
	local currentLayout = editorValues.accordionChildLayout or "along"
	if currentLayout == "vertical" then accordionLayoutSpinner:setSelection(1)
	elseif currentLayout == "horizontal" then accordionLayoutSpinner:setSelection(2)
	else accordionLayoutSpinner:setSelection(0) end
	layoutRow:addView(layoutLabel)
	layoutRow:addView(accordionLayoutSpinner)
	accordionPage:addView(layoutRow)

	local lanesRow = luajava.new(LinearLayout, o.context)
	lanesRow:setLayoutParams(o.fillparams)
	local lanesLabel = luajava.new(TextView, o.context)
	lanesLabel:setText("Columns:")
	lanesLabel:setTextSize(textSize)
	lanesLabel:setGravity(Gravity.RIGHT)
	lanesLabel:setLayoutParams(luajava.new(LinearLayoutParams, accordionLabelWidth, WRAP_CONTENT))
	o.widgets.accordionLanesLabel = lanesLabel
	o.widgets.accordionLanesEdit = luajava.new(EditText, o.context)
	local accordionLanesEdit = o.widgets.accordionLanesEdit
	local InputTypeLanes = luajava.bindClass("android.text.InputType")
	accordionLanesEdit:setInputType(InputTypeLanes.TYPE_CLASS_NUMBER)
	accordionLanesEdit:setLayoutParams(o.clickLabelEditParams)
	local lanesSeed = tonumber(editorValues.accordionLanes) or 0
	if lanesSeed >= 2 then
		accordionLanesEdit:setText(tostring(math.floor(lanesSeed)))
	else
		accordionLanesEdit:setText("")
	end
	lanesRow:addView(lanesLabel)
	lanesRow:addView(accordionLanesEdit)
	accordionPage:addView(lanesRow)
	o.widgets.accordionLanesHint = addOneLiner(accordionPage,
		"Type 2 for two columns (ten children become two of five). Blank = "
		.. "as many as fit in one column, then wrap if the screen is short. Max 5.",
		o)
	if o.widgets.accordionLanesHint ~= nil then
		o.widgets.accordionLanesHint:setMaxLines(3)
	end
	addOneLiner(accordionPage,
		"In Edit buttons: tap the parent, then tap another tile and choose "
		.. "Pin to \"MORE\". Tap several after the parent to pin them all. "
		.. "Long-press still pins too. Unpin from the tile menu. A tile "
		.. "belongs to one parent. You cannot pin an accordion inside another "
		.. "(toast: Can't nest accordions). Pinned tiles hide in play until "
		.. "the parent opens, then they appear where you placed them. Typed "
		.. "rows below still work for packs.",
		o)
	addOneLiner(accordionPage,
		"Unpinned children fill one column or row (as many as fit), then a new "
		.. "lane beside that column — never on the parent — or type 2 in "
		.. "Columns/Rows to force two lanes. Leftovers that still do not fit "
		.. "are dropped with a Note.",
		o)
	
	local triggerRow = luajava.new(LinearLayout,o.context)
	triggerRow:setLayoutParams(o.fillparams)
	local triggerLabel = luajava.new(TextView,o.context)
	triggerLabel:setText("Open with:")
	triggerLabel:setTextSize(textSize)
	triggerLabel:setGravity(Gravity.RIGHT)
	triggerLabel:setLayoutParams(luajava.new(LinearLayoutParams,accordionLabelWidth,WRAP_CONTENT))
	o.widgets.accordionTriggerSpinner = luajava.new(Spinner,o.context)
	local accordionTriggerSpinner = o.widgets.accordionTriggerSpinner
	accordionTriggerSpinner:setLayoutParams(o.clickLabelEditParams)
	styleSpinner(accordionTriggerSpinner)
	local triggerAdapter = makeSpinnerAdapter({
		"Tap (press)",
		"Hold",
		"Swipe (expand dir)"
	})
	accordionTriggerSpinner:setAdapter(triggerAdapter)
	local currentTrigger = editorValues.accordionTrigger or "tap"
	if currentTrigger == "hold" then accordionTriggerSpinner:setSelection(1)
	elseif currentTrigger == "swipe" then accordionTriggerSpinner:setSelection(2)
	else accordionTriggerSpinner:setSelection(0) end
	triggerRow:addView(triggerLabel)
	triggerRow:addView(accordionTriggerSpinner)
	accordionPage:addView(triggerRow)
	
	local InputType = luajava.bindClass("android.text.InputType")
	local holdMsRow = luajava.new(LinearLayout,o.context)
	holdMsRow:setLayoutParams(o.fillparams)
	local holdMsLabel = luajava.new(TextView,o.context)
	holdMsLabel:setText("Hold ms:")
	holdMsLabel:setTextSize(textSize)
	holdMsLabel:setGravity(Gravity.RIGHT)
	holdMsLabel:setLayoutParams(luajava.new(LinearLayoutParams,accordionLabelWidth,WRAP_CONTENT))
	o.widgets.accordionHoldMsEdit = luajava.new(EditText,o.context)
	local accordionHoldMsEdit = o.widgets.accordionHoldMsEdit
	accordionHoldMsEdit:setInputType(InputType.TYPE_CLASS_NUMBER)
	accordionHoldMsEdit:setLayoutParams(o.clickLabelEditParams)
	local holdMs = editorValues.accordionHoldMs
	if holdMs == nil or holdMs == "MULTI" then
		accordionHoldMsEdit:setText("450")
	else
		accordionHoldMsEdit:setText(tostring(math.floor(holdMs)))
	end
	holdMsRow:addView(holdMsLabel)
	holdMsRow:addView(accordionHoldMsEdit)
	accordionPage:addView(holdMsRow)
	o.widgets.accordionHoldMsRow = holdMsRow
	-- Hold delay is only meaningful when Open with = Hold. Still saved.
	if currentTrigger == "hold" then
		holdMsRow:setVisibility(View.VISIBLE)
	else
		holdMsRow:setVisibility(View.GONE)
	end
	
	o.widgets.accordionAutoCloseCheck = luajava.new(CheckBox,o.context)
	local accordionAutoCloseCheck = o.widgets.accordionAutoCloseCheck
	accordionAutoCloseCheck:setText("Auto-close sub-buttons after tap")
	if editorValues.accordionAutoClose == false then
		accordionAutoCloseCheck:setChecked(false)
	else
		accordionAutoCloseCheck:setChecked(true)
	end
	accordionPage:addView(accordionAutoCloseCheck)

	-- Dynamic sub-button list. Rebuild this container only — never the whole tab.
	local childrenContainer = luajava.new(LinearLayout, o.context)
	childrenContainer:setLayoutParams(o.fillparams)
	childrenContainer:setOrientation(LinearLayout.VERTICAL)
	accordionPage:addView(childrenContainer)
	o.widgets.accordionChildrenContainer = childrenContainer

	local childrenDraft = {}
	local seedChildren = editorValues.accordionChildren or {}
	for i = 1, #seedChildren do
		local c = seedChildren[i] or {}
		childrenDraft[i] = {
			label = c.label or "",
			command = c.command or "",
			id = c.id,
		}
	end

	o.widgets.accordionChildLabelEdits = {}
	o.widgets.accordionChildCmdEdits = {}
	o.widgets.accordionChildRowControls = {}
	o.widgets.accordionAddButton = nil
	o.widgets.accordionAddHint = nil
	o.widgets.accordionLimitNote = nil
	-- Prefer a table the host already wired (buttoneditor points its Flip-gate
	-- global at this before buildTabs). Replacing it here would leave Flip
	-- reading an empty set while swipe fields are already disabled.
	o.accordionLockedSwipeEdits = o.accordionLockedSwipeEdits or {}

	local accordionFieldsEnabled = true
	-- AdapterView.selectionChanged posts onItemSelected when mInLayout /
	-- mBlockLayoutRequests (AbsSpinner.setSelectionInt blocks layout requests
	-- then lays out; setSelection(int) requestLayout's). The first delivery
	-- after setOnItemSelectedListener is that init selection, not a user tap.
	-- A synchronous suppress flag cleared at end of build does not cover a
	-- posted SelectionNotifier. Ignore the first callback per spinner instead.
	local dirSpinnerSeenSelection = false
	local layoutSpinnerSeenSelection = false
	local triggerSpinnerSeenSelection = false

	local function selectedDirKey()
		local idx = tonumber(accordionDirSpinner:getSelectedItemPosition()) or 0
		local map = { "", "down", "up", "right", "left" }
		return map[idx + 1] or ""
	end

	local function selectedLayoutKey()
		local idx = tonumber(accordionLayoutSpinner:getSelectedItemPosition()) or 0
		local map = { "along", "vertical", "horizontal" }
		return map[idx + 1] or "along"
	end

	local function selectedTriggerKey()
		local idx = tonumber(accordionTriggerSpinner:getSelectedItemPosition()) or 0
		local map = { "tap", "hold", "swipe" }
		return map[idx + 1] or "tap"
	end

	local function syncDraftFromEdits()
		local labels = o.widgets.accordionChildLabelEdits
		local cmds = o.widgets.accordionChildCmdEdits
		local n = math.max(#labels, #childrenDraft)
		for i = 1, n do
			if labels[i] ~= nil and cmds[i] ~= nil then
				childrenDraft[i] = {
					label = editTextString(labels[i]),
					command = editTextString(cmds[i]),
					id = (childrenDraft[i] ~= nil and childrenDraft[i].id) or nil,
				}
			end
		end
	end

	local function draftHasAnyContent()
		for i = 1, #childrenDraft do
			local c = childrenDraft[i]
			if c ~= nil and childRowHasContent(c.label, c.command, c.id) then
				return true
			end
		end
		-- Also check live edits (draft may lag mid-keystroke before sync).
		local labels = o.widgets.accordionChildLabelEdits
		local cmds = o.widgets.accordionChildCmdEdits
		for i = 1, #labels do
			if childRowHasContent(editTextString(labels[i]), editTextString(cmds[i])) then
				return true
			end
		end
		return false
	end

	local function lastDraftFilled()
		local n = #childrenDraft
		if n == 0 then
			return true
		end
		local c = childrenDraft[n]
		if c == nil then
			return false
		end
		if childRowHasContent(c.label, c.command, c.id) then
			return true
		end
		local labels = o.widgets.accordionChildLabelEdits
		local cmds = o.widgets.accordionChildCmdEdits
		if labels[n] ~= nil and cmds[n] ~= nil then
			return childRowHasContent(editTextString(labels[n]), editTextString(cmds[n]))
		end
		return false
	end

	o.accordionStackVertical = accordionStackVertical
	o.accordionDirectionWords = accordionDirectionWords
	o.accordionMaxChildren = MAX_ACCORDION_CHILDREN

	o.currentAccordionDirectionWords = function()
		return accordionDirectionWords(selectedDirKey(), selectedLayoutKey())
	end

	-- Harvest for Done: drop empty rows, keep display order, cap at 20.
	o.harvestAccordionChildren = function()
		syncDraftFromEdits()
		local out = {}
		for i = 1, #childrenDraft do
			local c = childrenDraft[i]
			if c ~= nil and childRowHasContent(c.label, c.command, c.id) then
				local row = { label = c.label or "", command = c.command or "" }
				if c.id ~= nil and c.id ~= "" then
					row.id = c.id
				end
				out[#out + 1] = row
			end
			if #out >= MAX_ACCORDION_CHILDREN then
				break
			end
		end
		return out
	end

	local rebuildAccordionChildRows

	local function makeSmallButton(label)
		local b = luajava.new(Button, o.context)
		b:setText(label)
		b:setTextSize(textSizeSmall)
		b:setLayoutParams(luajava.new(LinearLayoutParams, WRAP_CONTENT, WRAP_CONTENT))
		b:setMinWidth(0)
		b:setMinimumWidth(0)
		b:setMinHeight(math.floor(32 * density))
		b:setPadding(math.floor(6 * density), 0, math.floor(6 * density), 0)
		return b
	end

	local childTextWatcher = luajava.createProxy("android.text.TextWatcher", {
		afterTextChanged = function(s)
			syncDraftFromEdits()
			if o.updateAccordionAddGate ~= nil then
				o.updateAccordionAddGate()
			end
			if o.updateAccordionGestureLocks ~= nil then
				o.updateAccordionGestureLocks()
			end
		end,
		beforeTextChanged = function(s, start, count, after) end,
		onTextChanged = function(s, start, before, count) end,
	})

	o.updateAccordionAddGate = function()
		syncDraftFromEdits()
		local addBtn = o.widgets.accordionAddButton
		local hint = o.widgets.accordionAddHint
		local limit = o.widgets.accordionLimitNote
		local n = #childrenDraft
		if limit ~= nil then
			if n >= MAX_ACCORDION_CHILDREN then
				limit:setVisibility(View.VISIBLE)
			else
				limit:setVisibility(View.GONE)
			end
		end
		if addBtn == nil then
			return
		end
		if n >= MAX_ACCORDION_CHILDREN then
			addBtn:setVisibility(View.GONE)
			if hint ~= nil then
				hint:setVisibility(View.GONE)
			end
			return
		end
		addBtn:setVisibility(View.VISIBLE)
		-- Disabled with a hint (not hidden): an absent Add control looks like a
		-- bug when the list is short; a greyed button with one line says why.
		local allow = accordionFieldsEnabled and lastDraftFilled()
		addBtn:setEnabled(allow)
		if hint ~= nil then
			if allow or not accordionFieldsEnabled then
				hint:setVisibility(View.GONE)
			else
				hint:setText("Fill in the last sub-button before adding another.")
				hint:setVisibility(View.VISIBLE)
			end
		end
	end

	o.updateAccordionGestureLocks = function()
		o.accordionLockedSwipeEdits = o.accordionLockedSwipeEdits or {}
		for k in pairs(o.accordionLockedSwipeEdits) do
			o.accordionLockedSwipeEdits[k] = nil
		end
		local holdRow = o.widgets.accordionHoldMsRow
		if holdRow ~= nil then
			if selectedTriggerKey() == "hold" then
				holdRow:setVisibility(View.VISIBLE)
			else
				holdRow:setVisibility(View.GONE)
			end
		end
		if o.numediting > 1 then
			return
		end
		-- Super-button: accordion will not save, so do not lock gesture commands.
		local floating = o._accordionFloating == true
		local dir = selectedDirKey()
		local trigger = selectedTriggerKey()
		syncDraftFromEdits()
		local configured = (not floating) and dir ~= "" and draftHasAnyContent()
		local lockTap = configured and trigger == "tap"
		local lockHold = configured and trigger == "hold"
		local lockSwipeDir = nil
		if configured and trigger == "swipe" then
			lockSwipeDir = dir
		end

		local function setLock(edit, note, locked)
			if edit == nil then
				return
			end
			edit:setEnabled(not locked)
			if note ~= nil then
				if locked then
					note:setVisibility(View.VISIBLE)
				else
					note:setVisibility(View.GONE)
				end
			end
		end

		setLock(o.widgets.clickCmdEdit, o.widgets.accordionTapLockNote, lockTap)
		setLock(o.widgets.holdCmdEdit, o.widgets.accordionHoldLockNote, lockHold)

		local swipeMap = {
			up = { edit = o.widgets.swipeUpCmdEdit, note = o.widgets.accordionSwipeLockNoteUp },
			down = { edit = o.widgets.swipeDownCmdEdit, note = o.widgets.accordionSwipeLockNoteDown },
			left = { edit = o.widgets.swipeLeftCmdEdit, note = o.widgets.accordionSwipeLockNoteLeft },
			right = { edit = o.widgets.swipeRightCmdEdit, note = o.widgets.accordionSwipeLockNoteRight },
		}
		for key, pair in pairs(swipeMap) do
			local locked = lockSwipeDir == key
			setLock(pair.edit, pair.note, locked)
			if locked and pair.edit ~= nil then
				o.accordionLockedSwipeEdits[pair.edit] = true
			end
		end
		-- Flip gate: locking a swipe direction must disable Flip even when
		-- that field is empty (drag-off is the same motion as swipe-to-expand).
		if o.updateFlipForSwipes ~= nil then
			o.updateFlipForSwipes()
		end
	end

	local function applyRowControlEnabled()
		local controls = o.widgets.accordionChildRowControls or {}
		local n = #controls
		local atCap = n >= MAX_ACCORDION_CHILDREN
		for i = 1, n do
			local row = controls[i]
			if row ~= nil then
				-- order: insertBefore, insertAfter, moveBefore, moveAfter, delete
				for j, btn in ipairs(row) do
					if btn ~= nil then
						local on = accordionFieldsEnabled
						if j == 3 and i <= 1 then
							on = false
						elseif j == 4 and i >= n then
							on = false
						elseif (j == 1 or j == 2) and atCap then
							on = false
						end
						btn:setEnabled(on)
					end
				end
			end
		end
		if o.widgets.accordionAddButton ~= nil then
			o.updateAccordionAddGate()
		end
	end

	rebuildAccordionChildRows = function()
		-- Callers that must keep mid-edit text sync first. Syncing here would
		-- overwrite insert/delete/reorder that already mutated childrenDraft
		-- while the old EditTexts still hold the previous order.
		childrenContainer:removeAllViews()
		o.widgets.accordionChildLabelEdits = {}
		o.widgets.accordionChildCmdEdits = {}
		o.widgets.accordionChildRowControls = {}
		o.widgets.accordionAddButton = nil
		o.widgets.accordionAddHint = nil
		o.widgets.accordionLimitNote = nil

		local words = accordionDirectionWords(selectedDirKey(), selectedLayoutKey())
		local labelWeight = luajava.new(LinearLayoutParams, 0, WRAP_CONTENT, 1)
		local cmdWeight = luajava.new(LinearLayoutParams, 0, WRAP_CONTENT, 1)
		local indexWidth = math.floor(28 * density)

		for i = 1, #childrenDraft do
			local child = childrenDraft[i] or { label = "", command = "" }
			local block = luajava.new(LinearLayout, o.context)
			block:setLayoutParams(o.fillparams)
			block:setOrientation(LinearLayout.VERTICAL)
			local pad = math.floor(4 * density)
			block:setPadding(0, pad, 0, pad)

			local fields = luajava.new(LinearLayout, o.context)
			fields:setLayoutParams(o.fillparams)
			fields:setOrientation(LinearLayout.HORIZONTAL)

			local indexTv = luajava.new(TextView, o.context)
			indexTv:setText(tostring(i))
			indexTv:setTextSize(textSize)
			indexTv:setGravity(Gravity.CENTER)
			indexTv:setLayoutParams(luajava.new(LinearLayoutParams, indexWidth, WRAP_CONTENT))
			fields:addView(indexTv)

			local labelEdit = luajava.new(EditText, o.context)
			labelEdit:setHint("Label")
			labelEdit:setText(child.label or "")
			labelEdit:setTextSize(textSize)
			labelEdit:setLines(1)
			labelEdit:setLayoutParams(labelWeight)
			labelEdit:setEnabled(accordionFieldsEnabled)
			labelEdit:addTextChangedListener(childTextWatcher)
			fields:addView(labelEdit)

			local cmdEdit = luajava.new(EditText, o.context)
			cmdEdit:setHint("Command")
			cmdEdit:setText(child.command or "")
			cmdEdit:setTextSize(textSize)
			cmdEdit:setInputType(TYPE_TEXT_FLAG_MULTI_LINE)
			cmdEdit:setMaxLines(2)
			cmdEdit:setLayoutParams(cmdWeight)
			cmdEdit:setEnabled(accordionFieldsEnabled)
			cmdEdit:addTextChangedListener(childTextWatcher)
			fields:addView(cmdEdit)
			block:addView(fields)

			local controls = luajava.new(LinearLayout, o.context)
			controls:setLayoutParams(o.fillparams)
			controls:setOrientation(LinearLayout.HORIZONTAL)

			local insertBefore = makeSmallButton("+" .. words.before)
			local insertAfter = makeSmallButton("+" .. words.after)
			local moveBefore = makeSmallButton(words.before)
			local moveAfter = makeSmallButton(words.after)
			local deleteBtn = makeSmallButton("Del")
			controls:addView(insertBefore)
			controls:addView(insertAfter)
			controls:addView(moveBefore)
			controls:addView(moveAfter)
			controls:addView(deleteBtn)
			block:addView(controls)
			childrenContainer:addView(block)

			o.widgets.accordionChildLabelEdits[i] = labelEdit
			o.widgets.accordionChildCmdEdits[i] = cmdEdit
			o.widgets.accordionChildRowControls[i] = {
				insertBefore, insertAfter, moveBefore, moveAfter, deleteBtn,
			}

			local index = i
			insertBefore:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
				onClick = function(v)
					if not accordionFieldsEnabled then return end
					if #childrenDraft >= MAX_ACCORDION_CHILDREN then return end
					syncDraftFromEdits()
					table.insert(childrenDraft, index, { label = "", command = "" })
					rebuildAccordionChildRows()
					if o.updateAccordionGestureLocks ~= nil then
						o.updateAccordionGestureLocks()
					end
				end
			}))
			insertAfter:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
				onClick = function(v)
					if not accordionFieldsEnabled then return end
					if #childrenDraft >= MAX_ACCORDION_CHILDREN then return end
					syncDraftFromEdits()
					table.insert(childrenDraft, index + 1, { label = "", command = "" })
					rebuildAccordionChildRows()
					if o.updateAccordionGestureLocks ~= nil then
						o.updateAccordionGestureLocks()
					end
				end
			}))
			moveBefore:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
				onClick = function(v)
					if not accordionFieldsEnabled then return end
					if index <= 1 then return end
					syncDraftFromEdits()
					childrenDraft[index], childrenDraft[index - 1] =
						childrenDraft[index - 1], childrenDraft[index]
					rebuildAccordionChildRows()
				end
			}))
			moveAfter:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
				onClick = function(v)
					if not accordionFieldsEnabled then return end
					if index >= #childrenDraft then return end
					syncDraftFromEdits()
					childrenDraft[index], childrenDraft[index + 1] =
						childrenDraft[index + 1], childrenDraft[index]
					rebuildAccordionChildRows()
				end
			}))
			deleteBtn:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
				onClick = function(v)
					if not accordionFieldsEnabled then return end
					syncDraftFromEdits()
					table.remove(childrenDraft, index)
					rebuildAccordionChildRows()
					if o.updateAccordionGestureLocks ~= nil then
						o.updateAccordionGestureLocks()
					end
				end
			}))

			-- Disable move at the ends so the player is not tapping a no-op.
			if index <= 1 then
				moveBefore:setEnabled(false)
			end
			if index >= #childrenDraft then
				moveAfter:setEnabled(false)
			end
			if not accordionFieldsEnabled then
				insertBefore:setEnabled(false)
				insertAfter:setEnabled(false)
				moveBefore:setEnabled(false)
				moveAfter:setEnabled(false)
				deleteBtn:setEnabled(false)
			elseif #childrenDraft >= MAX_ACCORDION_CHILDREN then
				insertBefore:setEnabled(false)
				insertAfter:setEnabled(false)
			end
		end

		local footer = luajava.new(LinearLayout, o.context)
		footer:setLayoutParams(o.fillparams)
		footer:setOrientation(LinearLayout.VERTICAL)
		local footPad = math.floor(8 * density)
		footer:setPadding(0, footPad, 0, footPad)

		local limitNote = luajava.new(TextView, o.context)
		limitNote:setTextSize(textSizeSmall)
		limitNote:setText("20 sub-buttons is the limit.")
		limitNote:setPadding(footPad, 0, footPad, footPad)
		limitNote:setLayoutParams(o.fillparams)
		limitNote:setVisibility(View.GONE)
		footer:addView(limitNote)
		o.widgets.accordionLimitNote = limitNote

		local addBtn = luajava.new(Button, o.context)
		addBtn:setText("+ Add sub-button " .. words.addWord)
		addBtn:setTextSize(textSize)
		addBtn:setLayoutParams(o.fillparams)
		addBtn:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
			onClick = function(v)
				if not accordionFieldsEnabled then return end
				syncDraftFromEdits()
				if #childrenDraft >= MAX_ACCORDION_CHILDREN then
					return
				end
				if not lastDraftFilled() then
					return
				end
				childrenDraft[#childrenDraft + 1] = { label = "", command = "" }
				rebuildAccordionChildRows()
				if o.updateAccordionGestureLocks ~= nil then
					o.updateAccordionGestureLocks()
				end
			end
		}))
		footer:addView(addBtn)
		o.widgets.accordionAddButton = addBtn

		local addHint = luajava.new(TextView, o.context)
		addHint:setTextSize(textSizeSmall)
		addHint:setText("Fill in the last sub-button before adding another.")
		addHint:setPadding(footPad, 0, footPad, 0)
		addHint:setLayoutParams(o.fillparams)
		addHint:setVisibility(View.GONE)
		footer:addView(addHint)
		o.widgets.accordionAddHint = addHint

		childrenContainer:addView(footer)
		o.updateAccordionAddGate()
	end

	o.rebuildAccordionChildRows = rebuildAccordionChildRows

	-- Direction / layout only change control captions (Above/Below vs Left/Right).
	-- Rebuilding every EditText for that would drop focus and dismiss the IME —
	-- including when AdapterView posts a spurious init onItemSelected after open.
	local function updateAccordionControlCaptions()
		local words = accordionDirectionWords(selectedDirKey(), selectedLayoutKey())
		local controls = o.widgets.accordionChildRowControls or {}
		for i = 1, #controls do
			local row = controls[i]
			if row ~= nil then
				if row[1] ~= nil then row[1]:setText("+" .. words.before) end
				if row[2] ~= nil then row[2]:setText("+" .. words.after) end
				if row[3] ~= nil then row[3]:setText(words.before) end
				if row[4] ~= nil then row[4]:setText(words.after) end
			end
		end
		if o.widgets.accordionAddButton ~= nil then
			o.widgets.accordionAddButton:setText("+ Add sub-button " .. words.addWord)
		end
		local stackV = accordionStackVertical(selectedDirKey(), selectedLayoutKey())
		if o.widgets.accordionLanesLabel ~= nil then
			if stackV then
				o.widgets.accordionLanesLabel:setText("Columns:")
			else
				o.widgets.accordionLanesLabel:setText("Rows:")
			end
		end
		if o.widgets.accordionLanesHint ~= nil then
			if stackV then
				o.widgets.accordionLanesHint:setText(
					"Type 2 for two columns (ten children become two of five). "
					.. "Blank = as many as fit in one column, then wrap if the "
					.. "screen is short. Max 5.")
			else
				o.widgets.accordionLanesHint:setText(
					"Type 2 for two rows (ten children become two of five). "
					.. "Blank = as many as fit in one row, then wrap if the "
					.. "screen is short. Max 5.")
			end
		end
	end
	o.updateAccordionControlCaptions = updateAccordionControlCaptions

	-- Test / host helpers that mutate the draft without going through clicks.
	o.accordionInsertChild = function(atIndex, label, command, id)
		syncDraftFromEdits()
		if #childrenDraft >= MAX_ACCORDION_CHILDREN then
			return false
		end
		local i = atIndex or (#childrenDraft + 1)
		if i < 1 then i = 1 end
		if i > #childrenDraft + 1 then i = #childrenDraft + 1 end
		table.insert(childrenDraft, i, {
			label = label or "",
			command = command or "",
			id = id,
		})
		rebuildAccordionChildRows()
		return true
	end
	o.accordionDeleteChild = function(index)
		syncDraftFromEdits()
		if index < 1 or index > #childrenDraft then
			return false
		end
		table.remove(childrenDraft, index)
		rebuildAccordionChildRows()
		return true
	end
	o.accordionMoveChild = function(index, delta)
		syncDraftFromEdits()
		local j = index + (delta or 0)
		if index < 1 or index > #childrenDraft or j < 1 or j > #childrenDraft then
			return false
		end
		childrenDraft[index], childrenDraft[j] = childrenDraft[j], childrenDraft[index]
		rebuildAccordionChildRows()
		return true
	end
	o.accordionChildDraftCount = function()
		syncDraftFromEdits()
		return #childrenDraft
	end
	o.accordionCanAdd = function()
		syncDraftFromEdits()
		return #childrenDraft < MAX_ACCORDION_CHILDREN and lastDraftFilled()
	end
	o.accordionSetChildText = function(index, label, command)
		syncDraftFromEdits()
		if index < 1 or index > #childrenDraft then
			return false
		end
		local keepId = childrenDraft[index] ~= nil and childrenDraft[index].id or nil
		childrenDraft[index] = { label = label or "", command = command or "", id = keepId }
		local le = o.widgets.accordionChildLabelEdits[index]
		local ce = o.widgets.accordionChildCmdEdits[index]
		if le ~= nil then le:setText(label or "") end
		if ce ~= nil then ce:setText(command or "") end
		syncDraftFromEdits()
		o.updateAccordionAddGate()
		o.updateAccordionGestureLocks()
		return true
	end

	-- One place decides whether this tab can be typed into, because two things
	-- close it: editing several buttons at once (an accordion is per button),
	-- and the button being a super button. The second can be switched on and off
	-- on the Others tab while this tab is open, so this is a function rather
	-- than a run of setEnabled calls at build time.
	o.updateAccordionEnabled = function(isFloating)
		o._accordionFloating = (isFloating == true)
		local on = (o.numediting <= 1) and (isFloating ~= true)
		accordionFieldsEnabled = on
		accordionDirSpinner:setEnabled(on)
		accordionLayoutSpinner:setEnabled(on)
		accordionTriggerSpinner:setEnabled(on)
		accordionHoldMsEdit:setEnabled(on)
		if o.widgets.accordionLanesEdit ~= nil then
			o.widgets.accordionLanesEdit:setEnabled(on)
		end
		accordionAutoCloseCheck:setEnabled(on)
		local labels = o.widgets.accordionChildLabelEdits or {}
		local cmds = o.widgets.accordionChildCmdEdits or {}
		for i = 1, #labels do
			if labels[i] ~= nil then labels[i]:setEnabled(on) end
			if cmds[i] ~= nil then cmds[i]:setEnabled(on) end
		end
		applyRowControlEnabled()
		-- Only the super-button case gets the note. Editing several buttons at
		-- once already greys the whole editor and says so in its title.
		if isFloating == true and o.numediting <= 1 then
			accordionSuperNote:setVisibility(View.VISIBLE)
		else
			accordionSuperNote:setVisibility(View.GONE)
		end
		o.updateAccordionGestureLocks()
	end

	accordionDirSpinner:setOnItemSelectedListener(luajava.createProxy(
		"android.widget.AdapterView$OnItemSelectedListener", {
		onItemSelected = function(parent, view, position, id)
			if not dirSpinnerSeenSelection then
				dirSpinnerSeenSelection = true
				return
			end
			updateAccordionControlCaptions()
			o.updateAccordionGestureLocks()
		end,
		onNothingSelected = function(parent) end,
	}))
	accordionLayoutSpinner:setOnItemSelectedListener(luajava.createProxy(
		"android.widget.AdapterView$OnItemSelectedListener", {
		onItemSelected = function(parent, view, position, id)
			if not layoutSpinnerSeenSelection then
				layoutSpinnerSeenSelection = true
				return
			end
			updateAccordionControlCaptions()
			o.updateAccordionGestureLocks()
		end,
		onNothingSelected = function(parent) end,
	}))
	accordionTriggerSpinner:setOnItemSelectedListener(luajava.createProxy(
		"android.widget.AdapterView$OnItemSelectedListener", {
		onItemSelected = function(parent, view, position, id)
			if not triggerSpinnerSeenSelection then
				triggerSpinnerSeenSelection = true
				return
			end
			o.updateAccordionGestureLocks()
		end,
		onNothingSelected = function(parent) end,
	}))

	rebuildAccordionChildRows()
	updateAccordionControlCaptions()
	o.updateAccordionEnabled(editorValues ~= nil and editorValues.floating == true)

	accordionPageScroller:addView(accordionPage)
	content:addView(accordionPageScroller)
	o.widgets.accordionPageScroller = accordionPageScroller
	tabAccordion:setIndicator(labelAccordion)
	tabAccordion:setContent(4)

	o.tabs = o.tabs or {}
	o.tabs.swipe = tabSwipe
	o.tabs.accordion = tabAccordion

	o.updateFlipForSwipes()
	

end
