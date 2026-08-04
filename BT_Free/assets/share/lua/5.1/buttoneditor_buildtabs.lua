local LinearLayout = _G["LinearLayout"]
local luajava = _G["luajava"]
local ScrollView = _G["ScrollView"]
local Gravity = _G["Gravity"]
local LinearLayoutParams = _G["LinearLayoutParams"]
local TextView = _G["TextView"]
local EditText = _G["EditText"]
local Spinner = _G["Spinner"]
local CheckBox = _G["CheckBox"]
local ArrayAdapter = _G["ArrayAdapter"]
local ColorDrawable = luajava.bindClass("android.graphics.drawable.ColorDrawable")
local FILL_PARENT = LinearLayoutParams.FILL_PARENT
local WRAP_CONTENT = LinearLayoutParams.WRAP_CONTENT
local Color = _G["Color"]
local View = _G["View"]
local TYPE_TEXT_FLAG_MULTI_LINE = _G["TYPE_TEXT_FLAG_MULTI_LINE"]
local density = _G["density"]
local PluginXCallS = _G["PluginXCallS"]
local drawButtons = _G["drawButtons"]
local view = _G["view"]
local buttonShowHints = _G["buttonShowHints"]
local buttonShowSwipePreview = _G["buttonShowSwipePreview"]
local pairs = _G["pairs"]
local tostring = _G["tostring"]
local math = _G["math"]
module(...)

function buildClickTab(host, content, o)
	local editorValues = o.editorValues
	local textSize = o.textSize
	local textSizeSmall = o.textSizeSmall
	o.widgets = o.widgets or {}

	local tab1 = host:newTabSpec("tab_one_btn_tab")
	local label1 = o.makeTabLabel("Tap")

	local clickPageScroller = luajava.new(ScrollView, o.context)
	clickPageScroller:setLayoutParams(o.fillparams)
	clickPageScroller:setId(1)

	local clickPage = luajava.new(LinearLayout, o.context)
	clickPage:setLayoutParams(o.fillparams)
	clickPage:setId(11)
	clickPage:setOrientation(LinearLayout.VERTICAL)

	local function addGestureSection(title, explanation)
		local header = luajava.new(TextView, o.context)
		header:setText(title .. " — " .. explanation)
		header:setTextSize(textSize)
		header:setTextColor(Color:argb(255, 0xFF, 0xFF, 0xFF))
		local pad = math.floor(8 * density)
		header:setPadding(pad, math.floor(14 * density), pad, math.floor(4 * density))
		header:setLayoutParams(o.fillparams)
		clickPage:addView(header)
	end

	addGestureSection("Tap", "sends when you release on the button")

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
	clickLabelEdit:setLines(1)
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

	addGestureSection("Flip", "drag off the button, then release (blocked when any swipe is set)")

	local flipSwipeNote = luajava.new(TextView, o.context)
	flipSwipeNote:setTextSize(textSizeSmall)
	flipSwipeNote:setText("This button has a swipe command, so flip does not run.")
	local flipHeaderPad = math.floor(8 * density)
	flipSwipeNote:setPadding(flipHeaderPad, 0, flipHeaderPad, flipHeaderPad)
	flipSwipeNote:setLayoutParams(o.fillparams)
	flipSwipeNote:setVisibility(View.GONE)
	clickPage:addView(flipSwipeNote)

	local flipLabelRow = luajava.new(LinearLayout, o.context)
	flipLabelRow:setLayoutParams(o.fillparams)

	local flipLabel = luajava.new(TextView, o.context)
	flipLabel:setTextSize(textSize)
	flipLabel:setText("Label:")
	flipLabel:setGravity(Gravity.RIGHT)
	flipLabel:setLayoutParams(clickLabelParams)

	local flipLabelEdit = luajava.new(EditText, o.context)
	flipLabelEdit:setTextSize(textSize)
	flipLabelEdit:setLines(1)
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
	tab1:setIndicator(label1)
	tab1:setContent(1)
	o.tabs = o.tabs or {}
	o.tabs.click = tab1

	o.widgets.clickLabelEdit = clickLabelEdit
	o.widgets.clickCmdEdit = clickCmdEdit
	o.widgets.flipLabelEdit = flipLabelEdit
	o.widgets.flipCmdEdit = flipCmdEdit
	o.widgets.flipSwipeNote = flipSwipeNote
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

	local showHintsCb = luajava.new(CheckBox,o.context)
	showHintsCb:setText("Show swipe letters, corner arrows, Hold and accordion badges on buttons")
	local hintsOn = editorValues.showGestureHints
	if hintsOn == nil then hintsOn = true end
	showHintsCb:setChecked(hintsOn)
	showHintsCb:setOnCheckedChangeListener(luajava.createProxy("android.widget.CompoundButton$OnCheckedChangeListener",{
		onCheckedChanged = function(v, isChecked)
			-- PluginXCallS only accepts one data arg; update window draw state immediately.
			buttonShowHints = isChecked and true or false
			if drawButtons ~= nil then
				drawButtons()
			end
			if view ~= nil then
				view:invalidate()
			end
			PluginXCallS("setShowGestureHints", isChecked and "true" or "false")
		end
	}))
	swipePage:addView(showHintsCb)

	local swipePreviewCb = luajava.new(CheckBox,o.context)
	swipePreviewCb:setText("Show swipe direction arrow while dragging (command callouts always show)")
	local previewOn = editorValues.showSwipePreview
	if previewOn == nil then previewOn = true end
	swipePreviewCb:setChecked(previewOn)
	swipePreviewCb:setOnCheckedChangeListener(luajava.createProxy("android.widget.CompoundButton$OnCheckedChangeListener",{
		onCheckedChanged = function(v, isChecked)
			buttonShowSwipePreview = isChecked and true or false
			PluginXCallS("setShowSwipePreview", isChecked and "true" or "false")
		end
	}))
	swipePage:addView(swipePreviewCb)

	o.addHelpText(swipePage, "Swipe commands override Flip when set. Drag ~24dp in a direction — eight are available, four straight and four corners. A second finger cancels the gesture. Hold fires at ~0.45s. To edit buttons, use ⋮ → Edit buttons, or long-press the ⋮ (not the button itself).")
	
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
		local header = luajava.new(TextView, o.context)
		header:setTextSize(textSize)
		header:setText(text)
		local pad = math.floor(8 * density)
		header:setPadding(pad, math.floor(12 * density), pad, math.floor(2 * density))
		header:setLayoutParams(o.fillparams)
		parent:addView(header)
	end

	local function addSwipeRow(parent, labelText, initialValue)
		return o.trackSwipeEditForFlip(addGestureRow(parent, labelText, initialValue))
	end

	o.widgets.holdCmdEdit = addGestureRow(swipePage, "Hold:", editorValues.holdCommand)

	addSectionHeader(swipePage, "Straight swipes")
	o.widgets.swipeUpCmdEdit = addSwipeRow(swipePage, "↑  Up:", editorValues.swipeUpCommand)
	o.widgets.swipeDownCmdEdit = addSwipeRow(swipePage, "↓  Down:", editorValues.swipeDownCommand)
	o.widgets.swipeLeftCmdEdit = addSwipeRow(swipePage, "←  Left:", editorValues.swipeLeftCommand)
	o.widgets.swipeRightCmdEdit = addSwipeRow(swipePage, "→  Right:", editorValues.swipeRightCommand)

	o.gestureLabelCarried = editorValues.showGestureLabel ~= false

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

	o.addHelpText(diagonalBox, "A corner with no command falls back to the nearest straight swipe, so adding these never changes how the straight ones behave.")
	o.widgets.gestureLabelCb = luajava.new(CheckBox,o.context)
	local gestureLabelCb = o.widgets.gestureLabelCb
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
	tabSwipe:setIndicator(labelSwipe)
	tabSwipe:setContent(3)
	
	local tabAccordion = host:newTabSpec("tab_accordion_btn_tab")
	local labelAccordion = o.makeTabLabel("Accord.")
	
	local accordionPageScroller = luajava.new(ScrollView,o.context)
	accordionPageScroller:setLayoutParams(o.fillparams)
	accordionPageScroller:setId(4)
	
	local accordionPage = luajava.new(LinearLayout,o.context)
	accordionPage:setLayoutParams(o.fillparams)
	accordionPage:setId(44)
	accordionPage:setOrientation(LinearLayout.VERTICAL)
	
	o.addHelpText(accordionPage, "Up to 5 sub-buttons expand from the parent. Badges on the button: T/H/S = tap/hold/swipe open.")
	
	local dirRow = luajava.new(LinearLayout,o.context)
	dirRow:setLayoutParams(o.fillparams)
	local dirLabel = luajava.new(TextView,o.context)
	dirLabel:setText("Expand:")
	dirLabel:setGravity(Gravity.RIGHT)
	dirLabel:setLayoutParams(luajava.new(LinearLayoutParams,90*density,WRAP_CONTENT))
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
	if(o.numediting > 1) then
		accordionDirSpinner:setEnabled(false)
	end
	-- A super button has no accordion: the fan is drawn on the button grid and
	-- the children only live while the parent is open, neither of which a
	-- floating window over the game can do. Saving strips it anyway
	-- (enforceNoAccordionOnSuperButton) — this is so the player is told here
	-- instead of finding out afterwards.
	if editorValues ~= nil and editorValues.floating == true then
		accordionDirSpinner:setEnabled(false)
		o.addHelpText(accordionPage,
			"This is a super button, so it cannot have an accordion: the "
			.. "sub-buttons are drawn on the button grid and only exist while "
			.. "the parent is open. Untick 'Float over the game' on the "
			.. "Advanced tab to use one.")
	end
	dirRow:addView(dirLabel)
	dirRow:addView(accordionDirSpinner)
	accordionPage:addView(dirRow)

	local layoutRow = luajava.new(LinearLayout,o.context)
	layoutRow:setLayoutParams(o.fillparams)
	local layoutLabel = luajava.new(TextView,o.context)
	layoutLabel:setText("Sub-btn layout:")
	layoutLabel:setGravity(Gravity.RIGHT)
	layoutLabel:setLayoutParams(luajava.new(LinearLayoutParams,90*density,WRAP_CONTENT))
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
	if(o.numediting > 1) then
		accordionLayoutSpinner:setEnabled(false)
	end
	layoutRow:addView(layoutLabel)
	layoutRow:addView(accordionLayoutSpinner)
	accordionPage:addView(layoutRow)
	
	local triggerRow = luajava.new(LinearLayout,o.context)
	triggerRow:setLayoutParams(o.fillparams)
	local triggerLabel = luajava.new(TextView,o.context)
	triggerLabel:setText("Open with:")
	triggerLabel:setGravity(Gravity.RIGHT)
	triggerLabel:setLayoutParams(luajava.new(LinearLayoutParams,90*density,WRAP_CONTENT))
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
	if(o.numediting > 1) then
		accordionTriggerSpinner:setEnabled(false)
	end
	triggerRow:addView(triggerLabel)
	triggerRow:addView(accordionTriggerSpinner)
	accordionPage:addView(triggerRow)
	
	o.addHelpText(accordionPage, "Tap = open on press, close on second press. Hold = open after hold delay (ms). Swipe = drag in expand direction. Use Vertical layout to stack sub-buttons in a column when expanding left/right.")
	
	local holdMsRow = luajava.new(LinearLayout,o.context)
	holdMsRow:setLayoutParams(o.fillparams)
	local holdMsLabel = luajava.new(TextView,o.context)
	holdMsLabel:setText("Hold ms:")
	holdMsLabel:setGravity(Gravity.RIGHT)
	holdMsLabel:setLayoutParams(luajava.new(LinearLayoutParams,90*density,WRAP_CONTENT))
	o.widgets.accordionHoldMsEdit = luajava.new(EditText,o.context)
	local accordionHoldMsEdit = o.widgets.accordionHoldMsEdit
	local InputType = luajava.bindClass("android.text.InputType")
	accordionHoldMsEdit:setInputType(InputType.TYPE_CLASS_NUMBER)
	accordionHoldMsEdit:setLayoutParams(o.clickLabelEditParams)
	local holdMs = editorValues.accordionHoldMs
	if holdMs == nil or holdMs == "MULTI" then
		accordionHoldMsEdit:setText("450")
	else
		accordionHoldMsEdit:setText(tostring(math.floor(holdMs)))
	end
	if(o.numediting > 1) then
		accordionHoldMsEdit:setEnabled(false)
	end
	holdMsRow:addView(holdMsLabel)
	holdMsRow:addView(accordionHoldMsEdit)
	accordionPage:addView(holdMsRow)
	
	o.widgets.accordionAutoCloseCheck = luajava.new(CheckBox,o.context)
	local accordionAutoCloseCheck = o.widgets.accordionAutoCloseCheck
	accordionAutoCloseCheck:setText("Auto-close sub-buttons after tap")
	if editorValues.accordionAutoClose == false then
		accordionAutoCloseCheck:setChecked(false)
	else
		accordionAutoCloseCheck:setChecked(true)
	end
	if(o.numediting > 1) then
		accordionAutoCloseCheck:setEnabled(false)
	end
	accordionPage:addView(accordionAutoCloseCheck)
	
	o.widgets.accordionChildLabelEdits = {}
	o.widgets.accordionChildCmdEdits = {}
	local children = editorValues.accordionChildren or {}
	for i = 1, 5 do
		local child = children[i] or {}
		local childLabelRow = luajava.new(LinearLayout,o.context)
		childLabelRow:setLayoutParams(o.fillparams)
		local childTitle = luajava.new(TextView,o.context)
		childTitle:setText("Sub "..i.." label:")
		childTitle:setGravity(Gravity.RIGHT)
		childTitle:setLayoutParams(luajava.new(LinearLayoutParams,90*density,WRAP_CONTENT))
		local labelEdit = luajava.new(EditText,o.context)
		labelEdit:setText(child.label or "")
		labelEdit:setLayoutParams(o.clickLabelEditParams)
		if(o.numediting > 1) then
			labelEdit:setEnabled(false)
		end
		childLabelRow:addView(childTitle)
		childLabelRow:addView(labelEdit)
		accordionPage:addView(childLabelRow)
		local childCmdRow = luajava.new(LinearLayout,o.context)
		childCmdRow:setLayoutParams(o.fillparams)
		local cmdTitle = luajava.new(TextView,o.context)
		cmdTitle:setText("Sub "..i.." cmd:")
		cmdTitle:setGravity(Gravity.RIGHT)
		cmdTitle:setLayoutParams(luajava.new(LinearLayoutParams,90*density,WRAP_CONTENT))
		local cmdEdit = luajava.new(EditText,o.context)
		cmdEdit:setText(child.command or "")
		cmdEdit:setInputType(TYPE_TEXT_FLAG_MULTI_LINE)
		cmdEdit:setMaxLines(3)
		cmdEdit:setLayoutParams(o.clickLabelEditParams)
		if(o.numediting > 1) then
			cmdEdit:setEnabled(false)
		end
		childCmdRow:addView(cmdTitle)
		childCmdRow:addView(cmdEdit)
		accordionPage:addView(childCmdRow)
		o.widgets.accordionChildLabelEdits[i] = labelEdit
		o.widgets.accordionChildCmdEdits[i] = cmdEdit
	end
	
	accordionPageScroller:addView(accordionPage)
	content:addView(accordionPageScroller)
	tabAccordion:setIndicator(labelAccordion)
	tabAccordion:setContent(4)

	o.tabs = o.tabs or {}
	o.tabs.swipe = tabSwipe
	o.tabs.accordion = tabAccordion

	o.updateFlipForSwipes()
	

end
