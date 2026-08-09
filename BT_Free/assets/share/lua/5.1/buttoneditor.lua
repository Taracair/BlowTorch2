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
module(...)

local textSizeBig = (18) -- sp value
local textSize = (14)  
local textSizeSmall = (10) 
local bgGrey = Color:argb(255,0x99,0x99,0x99) -- background color
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

--the rest are harvested from the advanced page editor
local advancedEditor -- the shared advanced page editor loaded from module


local swipeCmdEditsForFlip = {}
local flipGateNumEditing = 1

local function trimSwipeCmdText(edit)
	if edit == nil or not edit:isEnabled() then
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

local function updateFlipForSwipes()
	if flipGateNumEditing > 1 then
		return
	end
	local blocked = anySwipeCommandSet()
	if flipLabelEdit ~= nil then
		flipLabelEdit:setEnabled(not blocked)
	end
	if flipCmdEdit ~= nil then
		flipCmdEdit:setEnabled(not blocked)
	end
	if flipSwipeNote ~= nil then
		if blocked then
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
	help:setText(text)
	local pad = math.floor(8 * density)
	help:setPadding(pad, pad, pad, pad)
	help:setLayoutParams(editorFillparams)
	parent:addView(help)
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
	local titletextParams = luajava.new(LinearLayoutParams,FILL_PARENT,WRAP_CONTENT)
	
	
	title:setLayoutParams(titletextParams)
	title:setTextSize(textSizeBig)
	-- The count, because a multi-button edit looked exactly like a single one
	-- and the obvious question was which button it was editing. The answer is
	-- none of them on their own: it edits what they have in common.
	if numediting > 1 then
		title:setText("EDIT " .. numediting .. " BUTTONS")
	else
		title:setText("EDIT BUTTON")
	end
	title:setGravity(GRAVITY_CENTER)
	title:setTextColor(Color:argb(255,0x33,0x33,0x33))
	title:setBackgroundColor(bgGrey)
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
	buildtabs.buildClickTab(host, content, tabState)
	editorClickLabelEditParams = tabState.clickLabelEditParams
	local w = tabState.widgets
	clickLabelEdit = w.clickLabelEdit
	clickCmdEdit = w.clickCmdEdit
	flipLabelEdit = w.flipLabelEdit
	flipCmdEdit = w.flipCmdEdit
	flipSwipeNote = w.flipSwipeNote
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
	accordionAutoCloseCheck = w.accordionAutoCloseCheck
	accordionChildLabelEdits = w.accordionChildLabelEdits
	accordionChildCmdEdits = w.accordionChildCmdEdits
	if tabState.gestureLabelCarried ~= nil then gestureLabelCarried = tabState.gestureLabelCarried end
	if tabState.gestureHintsCarried ~= nil then gestureHintsCarried = tabState.gestureHintsCarried end
	if tabState.carriedDiagonalSwipes ~= nil then carriedDiagonalSwipes = tabState.carriedDiagonalSwipes end

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
	
	
	--dialogView = top
	--else
		--set up the dialog
		--Note("already constructed editor"..dialogView:toString())
	--end
	
	editorDialog = luajava.newInstance("com.resurrection.blowtorch2.lib.window.LuaDialog",context,top,false,nil)
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

  
    d.label = clickLabelEdit:getText():toString()
    --label = labeltmp:toString()
    d.cmd = clickCmdEdit:getText():toString()
    --cmd = cmdtmp:toString()
    d.flipLabel = flipLabelEdit:getText():toString()
    --fliplabel = fliplabeltmp:toString()
    d.flipCmd = flipCmdEdit:getText():toString()
    d.holdCommand = holdCmdEdit:getText():toString()
    d.swipeUpCommand = swipeUpCmdEdit:getText():toString()
    d.swipeDownCommand = swipeDownCmdEdit:getText():toString()
    d.swipeLeftCommand = swipeLeftCmdEdit:getText():toString()
    d.swipeRightCommand = swipeRightCmdEdit:getText():toString()

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
      d.accordionAutoClose = accordionAutoCloseCheck:isChecked()
      d.accordionChildren = {}
      for i = 1, 5 do
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

