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
local PluginXCallS = _G["PluginXCallS"]
local buttonEditorDone = _G["buttonEditorDone"]
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
local holdCmdEdit
local swipeUpCmdEdit
local swipeDownCmdEdit
local swipeLeftCmdEdit
local swipeRightCmdEdit
local accordionDirSpinner
local accordionLayoutSpinner
local accordionTriggerSpinner
local accordionHoldMsEdit
local accordionAutoCloseCheck
local accordionChildLabelEdits = {}
local accordionChildCmdEdits = {}

--the rest are harvested from the advanced page editor
local advancedEditor -- the shared advanced page editor loaded from module

function init(pContext)
	context = pContext
end

function showEditorDialog(editorValues,numediting)
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
	title:setText("EDIT BUTTON")
	title:setGravity(GRAVITY_CENTER)
	title:setTextColor(Color:argb(255,0x33,0x33,0x33))
	title:setBackgroundColor(bgGrey)
	title:setId(1)
	top:addView(title)

	--make the new tabhost.	
	local params = luajava.new(LinearLayoutParams,WRAP_CONTENT,WRAP_CONTENT)
	local fillparams = luajava.new(LinearLayoutParams,FILL_PARENT,WRAP_CONTENT,1)
	local contentparams = luajava.new(LinearLayoutParams,FILL_PARENT,WRAP_CONTENT)

	local hostparams = luajava.new(LinearLayoutParams,FILL_PARENT,WRAP_CONTENT,2)
	local host = luajava.new(TabHost,context)

	host:setId(3)
	host:setLayoutParams(hostparams)
	
	local function addHelpText(parent, text)
		local help = luajava.new(TextView, context)
		help:setTextSize(textSizeSmall)
		help:setText(text)
		local pad = math.floor(8 * density)
		help:setPadding(pad, pad, pad, pad)
		help:setLayoutParams(fillparams)
		parent:addView(help)
	end
	
	
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

	local tabWidgetParams = luajava.new(LinearLayoutParams, FILL_PARENT, tabMinHeight)
	widget:setLayoutParams(tabWidgetParams)
	widget:setWeightSum(5)
	
	local content = luajava.new(FrameLayout,context)
	content:setId(android_R_id.tabcontent)
	content:setLayoutParams(contentparams)
	holder:addView(widget)
	holder:addView(content)
	
	host:addView(holder)
	host:setup()
	
	
	local tab1 = host:newTabSpec("tab_one_btn_tab")
	local label1 = makeTabLabel("Click")
	
	--first page.
	
	--tmpview1 = luajava.new(TextView,context)
	--tmpview1:setText("first page")
	--tmpview1:setId(1)
	--tmpview1:setLayoutParams(fillparams);
	local clickPageScroller = luajava.new(ScrollView,context)
	clickPageScroller:setLayoutParams(fillparams)
	clickPageScroller:setId(1)
	
	local clickPage = luajava.new(LinearLayout,context)
	clickPage:setLayoutParams(fillparams)
	clickPage:setId(11)
	clickPage:setOrientation(LinearLayout.VERTICAL)
	
	local clickLabelRow = luajava.new(LinearLayout,context)
	clickLabelRow:setLayoutParams(fillparams)
	
	local clickLabel = luajava.new(TextView,context)
	clickLabel:setTextSize(textSize)
	clickLabel:setText("Label:")
	clickLabel:setGravity(Gravity.RIGHT)
	local clickLabelParams = luajava.new(LinearLayoutParams,80*density,WRAP_CONTENT)
	clickLabel:setLayoutParams(clickLabelParams)
	
	clickLabelEdit = luajava.new(EditText,context)
	clickLabelEdit:setTextSize(textSize)
	local clickLabelEditParams = luajava.new(LinearLayoutParams,FILL_PARENT,WRAP_CONTENT)
	clickLabelEdit:setLines(1)
	clickLabelEdit:setLayoutParams(clickLabelEditParams)
	if(numediting > 1) then
		clickLabelEdit:setEnabled(false)
	else
		if(editorValues.label ~= nil) then
			clickLabelEdit:setText(editorValues.label)
		end
	end
	
	
	clickLabelRow:addView(clickLabel)
	clickLabelRow:addView(clickLabelEdit)
	
	
	local clickCmdRow = luajava.new(LinearLayout,context)
	clickCmdRow:setLayoutParams(fillparams)
	
	local clickCmdLabel = luajava.new(TextView,context)
	clickCmdLabel:setTextSize(textSize)
	clickCmdLabel:setText("CMD:")
	clickCmdLabel:setGravity(Gravity.RIGHT)
	local clickCmdLabelParams = luajava.new(LinearLayoutParams,80*density,WRAP_CONTENT)
	clickCmdLabel:setLayoutParams(clickLabelParams)
	
	clickCmdEdit = luajava.new(EditText,context)
	clickCmdEdit:setTextSize(textSize)
	local clickCmdEditParams = luajava.new(LinearLayoutParams,WRAP_CONTENT,WRAP_CONTENT)
	clickCmdEdit:setInputType(TYPE_TEXT_FLAG_MULTI_LINE)
	clickCmdEdit:setHorizontallyScrolling(false)
	clickCmdEdit:setMaxLines(1000)
	clickCmdEdit:setLayoutParams(clickLabelEditParams)
	if(numediting > 1) then
		clickCmdEdit:setEnabled(false)
	else
		if(editorValues.command ~= nil) then
			clickCmdEdit:setText(editorValues.command)
		end
	end
	
	clickCmdRow:addView(clickCmdLabel)
	clickCmdRow:addView(clickCmdEdit)
	clickPage:addView(clickLabelRow)
	clickPage:addView(clickCmdRow)
	
	clickPageScroller:addView(clickPage)
	content:addView(clickPageScroller)
	tab1:setIndicator(label1)
	tab1:setContent(1)
	
	local tab2 = host:newTabSpec("tab_two_btn_tab")
	local label2 = makeTabLabel("Flip")
	
	--second, flip page.
	local flipPageScroller = luajava.new(ScrollView,context)
	flipPageScroller:setLayoutParams(fillparams)
	flipPageScroller:setId(2)
	
	local flipPage = luajava.new(LinearLayout,context)
	flipPage:setLayoutParams(fillparams)
	flipPage:setId(22)
	flipPage:setOrientation(LinearLayout.VERTICAL)
	
	addHelpText(flipPage, "Flip: drag outside the button in any direction, then release. Works even if you leave through a corner or the side.")
	
	local flipLabelRow = luajava.new(LinearLayout,context)
	flipLabelRow:setLayoutParams(fillparams)
	
	local flipLabel = luajava.new(TextView,context)
	flipLabel:setTextSize(textSize)
	flipLabel:setText("Label:")
	flipLabel:setGravity(Gravity.RIGHT)
	local flipLabelParams = luajava.new(LinearLayoutParams,80*density,WRAP_CONTENT)
	flipLabel:setLayoutParams(flipLabelParams)
	
	flipLabelEdit = luajava.new(EditText,context)
	flipLabelEdit:setTextSize(textSize)
	local flipLabelEditParams = luajava.new(LinearLayoutParams,FILL_PARENT,WRAP_CONTENT)
	flipLabelEdit:setLines(1)
	flipLabelEdit:setLayoutParams(clickLabelEditParams)
	if(numediting > 1) then
		flipLabelEdit:setEnabled(false)
	else
		if(editorValues.flipLabel ~= nil) then
			flipLabelEdit:setText(editorValues.flipLabel)
		end
	end
	
	flipLabelRow:addView(flipLabel)
	flipLabelRow:addView(flipLabelEdit)
	
	
	local flipCmdRow = luajava.new(LinearLayout,context)
	flipCmdRow:setLayoutParams(fillparams)
	
	local flipCmdLabel = luajava.new(TextView,context)
	flipCmdLabel:setTextSize(textSize)
	flipCmdLabel:setText("CMD:")
	flipCmdLabel:setGravity(Gravity.RIGHT)
	local flipCmdLabelParams = luajava.new(LinearLayoutParams,80*density,WRAP_CONTENT)
	flipCmdLabel:setLayoutParams(clickLabelParams)
	
	flipCmdEdit = luajava.new(EditText,context)
	flipCmdEdit:setTextSize(textSize)
	local flipCmdEditParams = luajava.new(LinearLayoutParams,FILL_PARENT,WRAP_CONTENT)
	flipCmdEdit:setInputType(TYPE_TEXT_FLAG_MULTI_LINE)
	flipCmdEdit:setHorizontallyScrolling(false)
	flipCmdEdit:setMaxLines(1000)
	flipCmdEdit:setLayoutParams(flipLabelEditParams)
	if(numediting > 1) then
		flipCmdEdit:setEnabled(false)
	else
		if(editorValues.flipCommand ~= nil) then
			flipCmdEdit:setText(editorValues.flipCommand)
		end
	end
	
	flipCmdRow:addView(flipCmdLabel)
	flipCmdRow:addView(flipCmdEdit)
	flipPage:addView(flipLabelRow)
	flipPage:addView(flipCmdRow)
	--tmpview2 = luajava.new(TextView,context)
	--tmpview2:setText("second page")
	----tmpview2:setId(2)
	--tmpview2:setLayoutParams(fillparams);
	flipPageScroller:addView(flipPage)
	content:addView(flipPageScroller)
	tab2:setIndicator(label2)
	tab2:setContent(2)
	
	local tabSwipe = host:newTabSpec("tab_swipe_btn_tab")
	local labelSwipe = makeTabLabel("Swipe")
	
	local swipePageScroller = luajava.new(ScrollView,context)
	swipePageScroller:setLayoutParams(fillparams)
	swipePageScroller:setId(3)
	
	local swipePage = luajava.new(LinearLayout,context)
	swipePage:setLayoutParams(fillparams)
	swipePage:setId(33)
	swipePage:setOrientation(LinearLayout.VERTICAL)
	
	local Spinner = luajava.bindClass("android.widget.Spinner")
	local ArrayAdapter = luajava.bindClass("android.widget.ArrayAdapter")
	local CheckBox = luajava.bindClass("android.widget.CheckBox")
	local AndroidR_layout = luajava.bindClass("android.R$layout")

	local showHintsCb = luajava.new(CheckBox,context)
	showHintsCb:setText("Show U/D/L/R, Hold and accordion badges on buttons")
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
	
	addHelpText(swipePage, "Swipe commands override Flip when set. Drag ~24dp in a direction. A second finger cancels the gesture. Hold fires at ~0.45s. Long press (1s) still opens the editor.")
	
	local function addGestureRow(parent, labelText, initialValue)
		local row = luajava.new(LinearLayout,context)
		row:setLayoutParams(fillparams)
		local label = luajava.new(TextView,context)
		label:setTextSize(textSize)
		label:setText(labelText)
		label:setGravity(Gravity.RIGHT)
		local labelParams = luajava.new(LinearLayoutParams,90*density,WRAP_CONTENT)
		label:setLayoutParams(labelParams)
		local edit = luajava.new(EditText,context)
		edit:setTextSize(textSize)
		edit:setInputType(TYPE_TEXT_FLAG_MULTI_LINE)
		edit:setHorizontallyScrolling(false)
		edit:setMaxLines(4)
		edit:setLayoutParams(clickLabelEditParams)
		if(numediting > 1) then
			edit:setEnabled(false)
		elseif(initialValue ~= nil) then
			edit:setText(initialValue)
		end
		row:addView(label)
		row:addView(edit)
		parent:addView(row)
		return edit
	end
	
	holdCmdEdit = addGestureRow(swipePage, "Hold:", editorValues.holdCommand)
	swipeUpCmdEdit = addGestureRow(swipePage, "Swipe up:", editorValues.swipeUpCommand)
	swipeDownCmdEdit = addGestureRow(swipePage, "Swipe down:", editorValues.swipeDownCommand)
	swipeLeftCmdEdit = addGestureRow(swipePage, "Swipe left:", editorValues.swipeLeftCommand)
	swipeRightCmdEdit = addGestureRow(swipePage, "Swipe right:", editorValues.swipeRightCommand)
	
	swipePageScroller:addView(swipePage)
	content:addView(swipePageScroller)
	tabSwipe:setIndicator(labelSwipe)
	tabSwipe:setContent(3)
	
	local tabAccordion = host:newTabSpec("tab_accordion_btn_tab")
	local labelAccordion = makeTabLabel("Accord.")
	
	local accordionPageScroller = luajava.new(ScrollView,context)
	accordionPageScroller:setLayoutParams(fillparams)
	accordionPageScroller:setId(4)
	
	local accordionPage = luajava.new(LinearLayout,context)
	accordionPage:setLayoutParams(fillparams)
	accordionPage:setId(44)
	accordionPage:setOrientation(LinearLayout.VERTICAL)
	
	addHelpText(accordionPage, "Up to 5 sub-buttons expand from the parent. Badges on the button: T/H/S = tap/hold/swipe open.")
	
	local dirRow = luajava.new(LinearLayout,context)
	dirRow:setLayoutParams(fillparams)
	local dirLabel = luajava.new(TextView,context)
	dirLabel:setText("Expand:")
	dirLabel:setGravity(Gravity.RIGHT)
	dirLabel:setLayoutParams(luajava.new(LinearLayoutParams,90*density,WRAP_CONTENT))
	accordionDirSpinner = luajava.new(Spinner,context)
	accordionDirSpinner:setLayoutParams(clickLabelEditParams)
	local dirAdapter = luajava.new(ArrayAdapter,context,AndroidR_layout.simple_spinner_item)
	dirAdapter:add("None")
	dirAdapter:add("Down")
	dirAdapter:add("Up")
	dirAdapter:add("Right")
	dirAdapter:add("Left")
	dirAdapter:setDropDownViewResource(AndroidR_layout.simple_spinner_dropdown_item)
	accordionDirSpinner:setAdapter(dirAdapter)
	local currentDir = editorValues.accordionDirection or ""
	if currentDir == "down" then accordionDirSpinner:setSelection(1)
	elseif currentDir == "up" then accordionDirSpinner:setSelection(2)
	elseif currentDir == "right" then accordionDirSpinner:setSelection(3)
	elseif currentDir == "left" then accordionDirSpinner:setSelection(4)
	else accordionDirSpinner:setSelection(0) end
	if(numediting > 1) then
		accordionDirSpinner:setEnabled(false)
	end
	dirRow:addView(dirLabel)
	dirRow:addView(accordionDirSpinner)
	accordionPage:addView(dirRow)

	local layoutRow = luajava.new(LinearLayout,context)
	layoutRow:setLayoutParams(fillparams)
	local layoutLabel = luajava.new(TextView,context)
	layoutLabel:setText("Sub-btn layout:")
	layoutLabel:setGravity(Gravity.RIGHT)
	layoutLabel:setLayoutParams(luajava.new(LinearLayoutParams,90*density,WRAP_CONTENT))
	accordionLayoutSpinner = luajava.new(Spinner,context)
	accordionLayoutSpinner:setLayoutParams(clickLabelEditParams)
	local layoutAdapter = luajava.new(ArrayAdapter,context,AndroidR_layout.simple_spinner_item)
	layoutAdapter:add("Auto (follow expand)")
	layoutAdapter:add("Vertical (column)")
	layoutAdapter:add("Horizontal (row)")
	layoutAdapter:setDropDownViewResource(AndroidR_layout.simple_spinner_dropdown_item)
	accordionLayoutSpinner:setAdapter(layoutAdapter)
	local currentLayout = editorValues.accordionChildLayout or "along"
	if currentLayout == "vertical" then accordionLayoutSpinner:setSelection(1)
	elseif currentLayout == "horizontal" then accordionLayoutSpinner:setSelection(2)
	else accordionLayoutSpinner:setSelection(0) end
	if(numediting > 1) then
		accordionLayoutSpinner:setEnabled(false)
	end
	layoutRow:addView(layoutLabel)
	layoutRow:addView(accordionLayoutSpinner)
	accordionPage:addView(layoutRow)
	
	local triggerRow = luajava.new(LinearLayout,context)
	triggerRow:setLayoutParams(fillparams)
	local triggerLabel = luajava.new(TextView,context)
	triggerLabel:setText("Open with:")
	triggerLabel:setGravity(Gravity.RIGHT)
	triggerLabel:setLayoutParams(luajava.new(LinearLayoutParams,90*density,WRAP_CONTENT))
	accordionTriggerSpinner = luajava.new(Spinner,context)
	accordionTriggerSpinner:setLayoutParams(clickLabelEditParams)
	local triggerAdapter = luajava.new(ArrayAdapter,context,AndroidR_layout.simple_spinner_item)
	triggerAdapter:add("Tap (press)")
	triggerAdapter:add("Hold")
	triggerAdapter:add("Swipe (expand dir)")
	triggerAdapter:setDropDownViewResource(AndroidR_layout.simple_spinner_dropdown_item)
	accordionTriggerSpinner:setAdapter(triggerAdapter)
	local currentTrigger = editorValues.accordionTrigger or "tap"
	if currentTrigger == "hold" then accordionTriggerSpinner:setSelection(1)
	elseif currentTrigger == "swipe" then accordionTriggerSpinner:setSelection(2)
	else accordionTriggerSpinner:setSelection(0) end
	if(numediting > 1) then
		accordionTriggerSpinner:setEnabled(false)
	end
	triggerRow:addView(triggerLabel)
	triggerRow:addView(accordionTriggerSpinner)
	accordionPage:addView(triggerRow)
	
	addHelpText(accordionPage, "Tap = open on press, close on second press. Hold = open after hold delay (ms). Swipe = drag in expand direction. Use Vertical layout to stack sub-buttons in a column when expanding left/right.")
	
	local holdMsRow = luajava.new(LinearLayout,context)
	holdMsRow:setLayoutParams(fillparams)
	local holdMsLabel = luajava.new(TextView,context)
	holdMsLabel:setText("Hold ms:")
	holdMsLabel:setGravity(Gravity.RIGHT)
	holdMsLabel:setLayoutParams(luajava.new(LinearLayoutParams,90*density,WRAP_CONTENT))
	accordionHoldMsEdit = luajava.new(EditText,context)
	local InputType = luajava.bindClass("android.text.InputType")
	accordionHoldMsEdit:setInputType(InputType.TYPE_CLASS_NUMBER)
	accordionHoldMsEdit:setLayoutParams(clickLabelEditParams)
	local holdMs = editorValues.accordionHoldMs
	if holdMs == nil or holdMs == "MULTI" then
		accordionHoldMsEdit:setText("450")
	else
		accordionHoldMsEdit:setText(tostring(math.floor(holdMs)))
	end
	if(numediting > 1) then
		accordionHoldMsEdit:setEnabled(false)
	end
	holdMsRow:addView(holdMsLabel)
	holdMsRow:addView(accordionHoldMsEdit)
	accordionPage:addView(holdMsRow)
	
	accordionAutoCloseCheck = luajava.new(CheckBox,context)
	accordionAutoCloseCheck:setText("Auto-close sub-buttons after tap")
	if editorValues.accordionAutoClose == false then
		accordionAutoCloseCheck:setChecked(false)
	else
		accordionAutoCloseCheck:setChecked(true)
	end
	if(numediting > 1) then
		accordionAutoCloseCheck:setEnabled(false)
	end
	accordionPage:addView(accordionAutoCloseCheck)
	
	accordionChildLabelEdits = {}
	accordionChildCmdEdits = {}
	local children = editorValues.accordionChildren or {}
	for i = 1, 5 do
		local child = children[i] or {}
		local childLabelRow = luajava.new(LinearLayout,context)
		childLabelRow:setLayoutParams(fillparams)
		local childTitle = luajava.new(TextView,context)
		childTitle:setText("Sub "..i.." label:")
		childTitle:setGravity(Gravity.RIGHT)
		childTitle:setLayoutParams(luajava.new(LinearLayoutParams,90*density,WRAP_CONTENT))
		local labelEdit = luajava.new(EditText,context)
		labelEdit:setText(child.label or "")
		labelEdit:setLayoutParams(clickLabelEditParams)
		if(numediting > 1) then
			labelEdit:setEnabled(false)
		end
		childLabelRow:addView(childTitle)
		childLabelRow:addView(labelEdit)
		accordionPage:addView(childLabelRow)
		local childCmdRow = luajava.new(LinearLayout,context)
		childCmdRow:setLayoutParams(fillparams)
		local cmdTitle = luajava.new(TextView,context)
		cmdTitle:setText("Sub "..i.." cmd:")
		cmdTitle:setGravity(Gravity.RIGHT)
		cmdTitle:setLayoutParams(luajava.new(LinearLayoutParams,90*density,WRAP_CONTENT))
		local cmdEdit = luajava.new(EditText,context)
		cmdEdit:setText(child.command or "")
		cmdEdit:setInputType(TYPE_TEXT_FLAG_MULTI_LINE)
		cmdEdit:setMaxLines(3)
		cmdEdit:setLayoutParams(clickLabelEditParams)
		if(numediting > 1) then
			cmdEdit:setEnabled(false)
		end
		childCmdRow:addView(cmdTitle)
		childCmdRow:addView(cmdEdit)
		accordionPage:addView(childCmdRow)
		accordionChildLabelEdits[i] = labelEdit
		accordionChildCmdEdits[i] = cmdEdit
	end
	
	accordionPageScroller:addView(accordionPage)
	content:addView(accordionPageScroller)
	tabAccordion:setIndicator(labelAccordion)
	tabAccordion:setContent(4)
	
	local tabOthers = host:newTabSpec("tab_others_btn_tab")
	local labelOthers = makeTabLabel("Others")
	
	--tmpview3 = luajava.new(TextView,context)
	--tmpview3:setText("third page")
	--tmpview3:setId(3)
	--tmpview3:setLayoutParams(params);	
	advancedEditor = require("buttoneditoradvanced")
	advancedEditor.init(context)
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
	
	host:addTab(tab1)
	host:addTab(tab2)
	host:addTab(tabSwipe)
	host:addTab(tabAccordion)
	host:addTab(tabOthers)
	
	
	if(numediting > 1) then
		host:setCurrentTab(4)
	else
		host:setCurrentTab(0)
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
    Validator:showMessage(view:getContext(),str)
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

