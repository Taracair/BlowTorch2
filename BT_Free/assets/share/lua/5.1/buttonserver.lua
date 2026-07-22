require("button")
require("serialize")
local marshal = require("marshal")

local props = require("config")

local buttonWindowName = props.name

debugInfo = false
local function debugString(string)
	if(debugInfo) then
		Note(string.format("\n%s\n",string))
	end
end

debugString("Button Server Loading...")

buttonsets = {} --raw table, holds tables of buttons.
buttonset_defaults = {} --raw table, holds defaults for a set name.

set_def = BUTTONSET_DATA:new()
set = {}

lob = {}

function loadButtonSet(args)
	
	debugString("Button Server sending button set, "..args)

	lob.name = args
	lob.set = buttonsets[args]
	
	if(lob.set == nil) then
		debugString("Button Set "..tostring(args).." is nil, skip load")
		return
	end

	debugString("Button Set "..args.." has ".. #lob.set .." buttons");
	lob.default = buttonset_defaults[args]
	
	current_set = args
	WindowXCallB(buttonWindowName,"loadButtons",marshal.encode(lob))
end

function loadAndEditSet(data)
	lastSelectedSet = data
	
	lob.name = data
	lob.set = buttonsets[data]
	lob.default = buttonset_defaults[data]
	
	if(lob.set ~= nil) then
		current_set = data
		WindowXCallB(buttonWindowName,"loadAndEditSet",marshal.encode(lob))
	end
end

RegisterSpecialCommand("loadset","loadButtonSet")
RegisterSpecialCommand("clearbuttons","clearButtons")

current_set = DEFAULT

function clearButtons()
	--all that needs to be done is call into the window to kick the process off
	WindowXCallS(buttonWindowName,"clearButtons","")
end

function saveButtons(arg)
	--Note("SAVE BUTTONS IMPL")
	
	local tmp = loadstring(arg)()
	
	buttonsets[current_set] = tmp
	--buttonset_defaults[current_set] = tmp.defaults
	--printTable("arg",arg)
	SaveSettings()
end

function makeNewButtonSet(name)
	buttonset_defaults[name] = {}
	buttonsets[name] = {}
	loadAndEditSet(name)
end

function deleteButtonSet(name)
	local nextset = nil
	if(name == current_set) then
		buttonSetList = {}
		for k in pairs(buttonSetList) do
			buttonSetList[k] = nil
		end
		
		
		local setdata = {}
		for i,v in pairs(buttonsets) do
			setdata[i] = #v
		end
		
		local counter = 0
		selectedIndex = -1
		for i,k in pairs(setdata) do
			tmp = {}
			tmp.name = i
			tmp.count = k
			table.insert(buttonSetList,tmp)
	
		end
		
		local sorter = function(a,b) if(a.name < b.name) then return true end return false end
		table.sort(buttonSetList,sorter)
		
		for i,b in ipairs(buttonSetList) do
			counter = counter + 1
			if(b.name == name) then
				selectedIndex = counter
			end
		end
			
		local nextindex = selectedIndex - 1
		if(nextindex > 0) then
			nextset = buttonSetList[nextindex].name
		elseif (nextindex == 0 and counter > 1) then
			nextset = buttonSetList[selectedIndex + 1].name 
		end
			
	else
	 --selected set is not the deleted set
	 nextset = current_set
	end
	
	buttonsets[name] = nil
	buttonset_defaults[name] = nil
	
	local left = 0
	for i,b in pairs(buttonsets) do
		left = left + 1
	end
	
	if(nextset ~= nil and nextset ~= current_set and left > 0) then
		loadButtonSet(nextset)
	end

	if(left == 0) then
		WindowXCallS(buttonWindowName,"updateButtonListDialogNoItems","now")
	else
  	local list = {}
    for i,v in pairs(buttonsets) do
      list[i] = #v
    end
    
  
    local data = {}
    data.setname = nextset
    data.setlist = list
		WindowXCallS(buttonWindowName,"updateButtonListDialog",serialize(data))
	end
end


function printTable(key,o)
	for i,v in pairs(o) do
		if(type(v)=="table") then
			printTable(key.."."..i,v)
		else 
			--Note(key.."."..i.."<==>"..v)
		end
	end
end

bset = {}
working_set = nil
function bset.start(a)
	
	
	local tmp = {}
	-- Plugin XML may use name= or legacy setName=; both map to the set key.
	tmp.name = a:getValue("","name") or a:getValue("","setName") or "default"
	--Note("NEW BUTTON SET:"..tmp.name)
	tmp.width = a:getValue("","width") or "80"
	tmp.height = a:getValue("","height") or "80"
	tmp.labelSize = a:getValue("","labelSize") or "23"
	local gx = a:getValue("","gridXwidth")
	if gx ~= nil then tmp.gridXwidth = gx end
	local gy = a:getValue("","gridYwidth")
	if gy ~= nil then tmp.gridYwidth = gy end
	local pColorStr = a:getValue("","primaryColor")
	if(pColorStr ~=nil) then
		local BigInt = luajava.newInstance("java.math.BigInteger",pColorStr,16)
		tmp.primaryColor = BigInt:intValue()
		--tmp.primaryColor = tonumber(pColorStr,16)
	end 
	local sColorStr = a:getValue("","selectedColor")
	if(sColorStr ~=nil) then
		local BigInt = luajava.newInstance("java.math.BigInteger",sColorStr,16)
		tmp.selectedColor = BigInt:intValue()
		--tmp.selectedColor = tonumber(sColorStr,16)
	end 
	local fColorStr = a:getValue("","flipColor")
	if(fColorStr ~=nil) then
		local BigInt = luajava.newInstance("java.math.BigInteger",fColorStr,16)
		tmp.selectedColor = BigInt:intValue()
		--tmp.flipColor = tonumber(fColorStr,16)
	end 
	local lColorStr = a:getValue("","labelColor")
	if(lColorStr ~=nil) then
		local BigInt = luajava.newInstance("java.math.BigInteger",lColorStr,16)
		tmp.labelColor = BigInt:intValue()
		--tmp.labelColor = tonumber(lColorStr,16)
	end 
	local flColorStr = a:getValue("","flipLabelColor")
	if(flColorStr ~=nil) then
		local BigInt = luajava.newInstance("java.math.BigInteger",flColorStr,16)
		tmp.flipLabelColor = BigInt:intValue()
		--tmp.selectedColor = tonumber(flColorStr,16)
	end 
	--tmp.primaryColor = a:getValue("","priary
	buttonset_defaults[tmp.name] = tmp
	working_set = tmp.name
	
	--printTable(string.format("defaults[%s]",working_set),buttonset_defaults[working_set])
	
end

button = {}
function button.start(a)
	----Note("NEW BUTTON:"..working_set)
	
	local tmp = {}
	tmp.x = a:getValue("","x")
	tmp.y = a:getValue("","y")
	tmp.label = a:getValue("","label") or ""
	tmp.flipLabel = a:getValue("","flipLabel") or ""
	tmp.labelSize = a:getValue("","labelSize") or 23
	tmp.command = a:getValue("","command") or ""
	tmp.flipCommand = a:getValue("","flipCommand") or ""
	tmp.holdCommand = a:getValue("","holdCommand") or ""
	tmp.swipeUpCommand = a:getValue("","swipeUpCommand") or ""
	tmp.swipeDownCommand = a:getValue("","swipeDownCommand") or ""
	tmp.swipeLeftCommand = a:getValue("","swipeLeftCommand") or ""
	tmp.swipeRightCommand = a:getValue("","swipeRightCommand") or ""
	tmp.switchTo = a:getValue("","switchTo") or ""
	tmp.name = a:getValue("","name")
	tmp.height = a:getValue("","height")
	tmp.width = a:getValue("","width")
	
	local pColorStr = a:getValue("","primaryColor")
	if(pColorStr ~=nil) then
		local BigInt = luajava.newInstance("java.math.BigInteger",pColorStr,16)
		tmp.primaryColor = BigInt:intValue()
		--tmp.primaryColor = tonumber(pColorStr,16)
	end 
	local sColorStr = a:getValue("","selectedColor")
	if(sColorStr ~=nil) then
		local BigInt = luajava.newInstance("java.math.BigInteger",sColorStr,16)
		tmp.primaryColor = BigInt:intValue()
		--tmp.selectedColor = tonumber(sColorStr,16)
	end 
	local fColorStr = a:getValue("","flipColor")
	if(fColorStr ~=nil) then
		local BigInt = luajava.newInstance("java.math.BigInteger",fColorStr,16)
		tmp.primaryColor = BigInt:intValue()
		--tmp.flipColor = tonumber(fColorStr,16)
	end 
	local lColorStr = a:getValue("","labelColor")
	if(lColorStr ~=nil) then
		local BigInt = luajava.newInstance("java.math.BigInteger",lColorStr,16)
		tmp.primaryColor = BigInt:intValue()
		--tmp.labelColor = tonumber(lColorStr,16)
	end 
	local flColorStr = a:getValue("","flipLabelColor")
	if(flColorStr ~=nil) then
		local BigInt = luajava.newInstance("java.math.BigInteger",flColorStr,16)
		tmp.primaryColor = BigInt:intValue()
		--tmp.flipLabelColor = tonumber(flColorStr,16)
	end 
	
	if(buttonsets[working_set] == nil) then
		buttonsets[working_set] = {}
	end
	debugString("Adding button to "..working_set)
	table.insert(buttonsets[working_set],tmp)
	--printTable(string.format("buttonsets[%s]",working_set),buttonsets)
	
end

bset_cb = luajava.createProxy("android.sax.StartElementListener",bset)
button_cb = luajava.createProxy("android.sax.StartElementListener",button)

function handleSelected(body)
	--Note("found the selectedNode:"..body)
	current_set = body
end

selectedListener = {}
selectedListener["end"] = handleSelected


selected_cb = luajava.createProxy("android.sax.TextElementListener",selectedListener)

function handleButtonSerializer(body)
	--Note("doing string serailze for buttons")
	buttonsets = loadstring(body)()
end
buttonserializer = {}
buttonserializer["end"] = handleButtonSerializer
buttonserializer_cb = luajava.createProxy("android.sax.TextElementListener",buttonserializer)

function handleButtonSetSerializer(body)
	--Note("doing string serailze for buttonsets")
	buttonset_defaults = loadstring(body)()
end
buttonsetserializer = {}
buttonsetserializer["end"] = handleButtonSetSerializer
buttonsetserializer_cb = luajava.createProxy("android.sax.TextElementListener",buttonsetserializer)

function OnPrepareXML(root)
	--Note("XMLLXLXLXLMXMXLMXLXMLXMXLMLLXMLXMXLXMLXMX")
	sets = root:getChild("buttonsets")
	set = sets:getChild("buttonset")
	button = set:getChild("button")
	selected = sets:getChild("selected")
	
	buttons = sets:getChild("buttons")
	defaults = sets:getChild("defaults")

	set:setStartElementListener(bset_cb)
	button:setStartElementListener(button_cb)
	selected:setTextElementListener(selected_cb)
	
	buttons:setTextElementListener(buttonserializer_cb)
	defaults:setTextElementListener(buttonsetserializer_cb)
end
--Note("loaded button prototypes")

function getButtonSetList(s)
	
	--Note("getting button list")

	setdata = {}
	for i,v in pairs(buttonsets) do
		setdata[i] = #v
	end
	
	WindowXCallS(buttonWindowName,"showButtonList",serialize(setdata))
end

function saveSetDefaults(data)
	defaults = loadstring(data)()
	
	buttonset_defaults[current_set] = defaults
	--wow, that was easy.
	
	--don't save here, the call to saveButtons will come next
end

function OnXmlExport(out)
	--local System = luajava.bindClass("java.lang.System")
	--now = System:currentTimeMillis()
	--Note("buttonset save routine GO!")
	
	if(out ~= nil) then
		--Note(string.format("xmlserializer is not null, %s",type(out)))
	else
		--Note("xmlserializer is null")
	end
	--local startTag = out.startTag(out)
	--local out = xout
	local bsets = buttonsets
	local bset_defaults = buttonset_defaults
	local Integer = luajava.bindClass("java.lang.Integer")
	--local startTag = out.startTag
	--local endTag = out.endTag
	--local attribute = out.attribute

	out:startTag("","buttonsets")
	--for i,b in pairs(bsets) do
	--	out:startTag("","buttonset")
	----	--Note("attempting to output set"..i)
	--	local defs = bset_defaults[i]
	--	out:attribute("","name",i)
		
	--	if(defs.primaryColor ~= nil) then
	--		out:attribute("","primaryColor",Integer:toHexString(tonumber(defs.primaryColor)))
	--	end
		
	--	if(defs.selectedColor ~= nil) then
	--		out:attribute("","selectedColor",Integer:toHexString(tonumber(defs.selectedColor)))
	--	end
		
	--	if(defs.flipColor ~= nil) then
	--		out:attribute("","flipColor",Integer:toHexString(tonumber(defs.flipColor)))
	--	end
		
	--	if(defs.labelColor ~= nil) then
	--		out:attribute("","labelColor",Integer:toHexString(tonumber(defs.labelColor)))
	--	end
		
	--	if(defs.flipLabelColor ~= nil) then
	--		out:attribute("","flipLabelColor",Integer:toHexString(tonumber(defs.flipLabelColor)))
	--	end
		
	--	if(defs.labelSize ~= nil) then
	--		out:attribute("","labelSize",tostring(defs.labelSize))
	--	end
		
	--	if(defs.height ~= nil) then
	--		out:attribute("","height",tostring(defs.height))
	--	end
		
	--	if(defs.width ~= nil) then
	--		out:attribute("","width",tostring(defs.width))
	--	end
		
	--	for k,x in pairs(b) do
	--		out:startTag("","button")
			--for l,z in pairs(x) do
	--			if(x.name ~= nil) then
	--				out:attribute("","name",x.name)
	--			end
			
	--			if(rawget(x,"primaryColor") ~= nil) then
	--				out:attribute("","primaryColor",Integer:toHexString(tonumber(x.primaryColor)))
	--			end
				
	--			if(rawget(x,"selectedColor") ~= nil) then
	--				out:attribute("","selectedColor",Integer:toHexString(tonumber(x.selectedColor)))
	--			end
				
	--			if(rawget(x,"flipColor") ~= nil) then
	--				out:attribute("","flipColor",Integer:toHexString(tonumber(x.flipColor)))
	--			end
				
	--			if(rawget(x,"labelColor") ~= nil) then
	--				out:attribute("","labelColor",Integer:toHexString(tonumber(x.labelColor)))
	--			end
				
	--			if(rawget(x,"flipLabelColor") ~= nil) then
	--				out:attribute("","flipLabelColor",Integer:toHexString(tonumber(x.flipLabelColor)))
	--			end
				
	--			if(rawget(x,"labelSize") ~= nil) then
	--				out:attribute("","labelSize",tostring(x.labelSize))
	--			end
				
	--			if(rawget(x,"height") ~= nil) then
	--				out:attribute("","height",tostring(x.height))
	--			end
				
	--			if(rawget(x,"width") ~= nil) then
	--				out:attribute("","width",tostring(x.width))
	--			end
				
	--			out:attribute("","x",tostring(x["x"]))
	--			out:attribute("","y",tostring(x["y"]))
				
	--			if(rawget(x,"command") ~= nil) then
	--				out:attribute("","command",x.command)
	--			end
				
	--			if(rawget(x,"flipCommand") ~= nil) then
	--				out:attribute("","flipCommand",x.flipCommand)
	--			end
	--			
	--			if(rawget(x,"flipLabel") ~= nil) then
	--				out:attribute("","flipLabel",x.flipLabel)
	--			end
				
	--			if(rawget(x,"label") ~= nil) then
	--				out:attribute("","label",x.label)
	--			end
			--end
	--			out:endTag("","button")
	--		end	
			--end
	--	out:endTag("","buttonset")
	--end
	out:startTag("","selected")
	out:text(current_set)
	out:endTag("","selected")
	out:startTag("","buttons")
		out:cdsect(serialize(buttonsets))
	out:endTag("","buttons")
		
	out:startTag("","defaults")
		out:cdsect(serialize(buttonset_defaults))
	out:endTag("","defaults")
	out:endTag("","buttonsets")
	--delta = System:currentTimeMillis() - now
	----Note("saved all buttons, took "..delta.." millis.")
end

function buttonLayerReady()
	loadButtonSet(current_set)
	loadOptions()
end

function legacyButtonsImported()
	--Note("doing button import")
	printTable("buttonsets",buttonsets)
	printTable("buttonset_defaults",buttonset_defaults)
end

function OnOptionChanged(key,value)
	--Note("\n"..key..":"..value.."\n")
	local func = optionsTable[key]
	if(func ~= nil) then
		func(value)
	end
	
end

--boolean windowReady
function loadOptions()
	WindowXCallS(buttonWindowName,"loadOptions",serialize(options))
end

function setAutoLaunch(value)
	-- Ignored: only the wrench long-press opens the button editor.
	options.auto_launch = false
	if(UserPresent()) then
		loadOptions()
	end
end

function setAutoCreate(value)
	options.auto_create = value
	if(UserPresent()) then
		loadOptions()
	end
end 

function setRoundness(value)
	options.roundness = value
	if(UserPresent()) then
		loadOptions()
	end
end

function setHapticFeedbackEditor(value)
	options.haptic_edit = value
	if(UserPresent()) then
		loadOptions()
	end
end

function setHapticFeedbackPressed(value)
	options.haptic_press = value
	if(UserPresent()) then
		loadOptions()
	end
end

function setHapticFeedbackFlipped(value)
	options.haptic_flip = value
	if(UserPresent()) then
		loadOptions()
	end
end

function setShowGestureHints(value)
	-- PluginXCallS passes a single string; also accept boolean from OnOptionChanged paths.
	local on = (value == true or value == "true" or value == "1")
	options.show_gesture_hints = on and "true" or "false"
	-- Always push to the window so badge/arrow drawing updates immediately.
	loadOptions()
end


Integer = luajava.newInstance("java.lang.Integer",0)
IntegerClass = Integer:getClass()
RawInteger = IntegerClass.TYPE

function makeIntArray(table)
	newarray = Array:newInstance(RawInteger,#table)
	for i,v in ipairs(table) do
		index = i-1
		intval = luajava.new(Integer,v)
		Array:setInt(newarray,index,intval:intValue())
	end
	
	return newarray
end

android_R_attr = luajava.bindClass("android.R$attr")
android_R_style = luajava.bindClass("android.R$style")
android_R_dimen = luajava.bindClass("android.R$dimen")

-- Canonical offline-tutorial pad in density-independent pixels (centers).
-- Top row = PREV / NEXT / TOPICS. Compass teaches the same gesture map as the
-- fresh-MUD default: tap=walk, outward swipe=look, opposite swipe=open, flip/hold=close.
local STARTER_DEFAULT_BUTTONS = {
	{ x=23,  y=23,  label="PREV",   command=".tutorial prev",   labelSize=11 },
	{ x=68,  y=23,  label="NEXT",   command=".tutorial next",   labelSize=11 },
	{ x=113, y=23,  label="TOPICS", command=".tutorial topics", labelSize=10 },
	{ x=23,  y=68,  label="U",      command="up",    flipCommand="close u", holdCommand="close u", labelSize=14,
	  swipeUpCommand="look u", swipeDownCommand="open u" },
	{ x=68,  y=68,  label="N",      command="north", flipCommand="close n", holdCommand="close n", labelSize=14,
	  swipeUpCommand="look n", swipeDownCommand="open n" },
	{ x=113, y=68,  label="INV",    command=".note INV: on a real MUD this sends inventory.", labelSize=11 },
	{ x=23,  y=113, label="W",      command="west",  flipCommand="close w", holdCommand="close w", labelSize=14,
	  swipeLeftCommand="look w", swipeRightCommand="open w" },
	{ x=113, y=113, label="E",      command="east",  flipCommand="close e", holdCommand="close e", labelSize=14,
	  swipeRightCommand="look e", swipeLeftCommand="open e" },
	{ x=23,  y=158, label="D",      command="down",  flipCommand="close d", holdCommand="close d", labelSize=14,
	  swipeDownCommand="look d", swipeUpCommand="open d" },
	{ x=68,  y=158, label="S",      command="south", flipCommand="close s", holdCommand="close s", labelSize=14,
	  swipeDownCommand="look s", swipeUpCommand="open s" },
	{ x=113, y=158, label="LOOK",   command=".note LOOK: tap=look; swipes peek n/s/e/w on a live pad.", labelSize=11,
	  swipeUpCommand=".note Swipe↑ = look n", swipeDownCommand=".note Swipe↓ = look s",
	  swipeLeftCommand=".note Swipe← = look w", swipeRightCommand=".note Swipe→ = look e" },
	{ x=23,  y=203, label="HELP",   command=".tutorial start", labelSize=11 },
	{ x=68,  y=203, label="SWIPE",  command=".tutorial buttons_swipe", labelSize=11,
	  swipeUpCommand=".note Swipe↑ tip: about one finger-width past the tile edge.",
	  swipeDownCommand=".note Swipe↓ tip: swipe beats Flip when both are set.",
	  swipeLeftCommand=".note Swipe← tip: on N/S/E/W outward=look, opposite=open.",
	  swipeRightCommand=".note Swipe→ tip: flip/hold = close that way." },
	{ x=113, y=203, label="HOLD",   command=".tutorial buttons_hold", labelSize=11,
	  holdCommand=".note Hold tip: H badge = Hold is set. On the compass, hold = close." },
	{ x=23,  y=248, label="ACC",    command="", labelSize=11 },
	{ x=68,  y=248, label="CLEAR",  command=".clearbuttons", labelSize=11 },
	{ x=113, y=248, label="LOAD",   command=".loadset tutorial", labelSize=11,
	  holdCommand=".note Hold LOAD: .loadset default — restore the full starter pad.",
	  flipCommand=".loadset default" },
}

local STARTER_SET_DEFAULTS = {
	width = 42, height = 42, labelSize = 12, gridXwidth = 45, gridYwidth = 45,
}

local function cloneStarterButton(src)
	local b = {}
	for k,v in pairs(src) do
		b[k] = v
	end
	return b
end

-- Rebuild default set from the canonical DP layout, pin bottom-center, refresh UI.
function installStarterButtonLayout(args)
	buttonset_defaults["default"] = buttonset_defaults["default"] or {}
	for k,v in pairs(STARTER_SET_DEFAULTS) do
		buttonset_defaults["default"][k] = v
	end
	local set = {}
	for i,src in ipairs(STARTER_DEFAULT_BUTTONS) do
		set[i] = cloneStarterButton(src)
	end
	buttonsets["default"] = set
	current_set = "default"
	local okAlign, alignErr = pcall(alignDefaultButtons)
	if not okAlign then
		Note("\nButton align failed: " .. tostring(alignErr) .. "\n")
	end
	pcall(ensureTutorialAccordion, "")
	pcall(loadButtonSet, "default")
	if SaveSettings ~= nil then
		pcall(SaveSettings)
	end
end

function alignDefaultButtons()
	-- Scale DP centers by density, then pin starter sets to upper-center
	-- (below the status/action bar — not glued to the top edge).
	local margin = 10
	local topPad = 88
	density = tonumber(GetDisplayDensity()) or 1
	local metrics = context:getResources():getDisplayMetrics()
	heightPixels = tonumber(metrics.heightPixels) or 0
	local widthPixels = tonumber(metrics.widthPixels) or 0
	-- GetActionBarHeight() returns a string from Java — must tonumber before compares.
	local ab = tonumber(GetActionBarHeight()) or 0

	local function alignSet(setName)
		local set = buttonsets[setName]
		local defaults = buttonset_defaults[setName]
		if set == nil then return end

		local right = 0
		local left = 1000000
		local bottom = 0
		local top = 1000000

		for i,b in pairs(set) do
			b.x = (tonumber(b.x) or 0) * density
			b.y = (tonumber(b.y) or 0) * density

			local width = tonumber(b.width) or tonumber(defaults and defaults.width) or 42
			local height = tonumber(b.height) or tonumber(defaults and defaults.height) or 42
			width = width * density
			height = height * density

			local l = b.x - width / 2
			local r = b.x + width / 2
			local t = b.y - height / 2
			local bot = b.y + height / 2

			if r > right then right = r end
			if l < left then left = l end
			if t < top then top = t end
			if bot > bottom then bottom = bot end
		end

		local clusterW = right - left
		local xoffset = ((widthPixels - clusterW) / 2) - left
		if xoffset < margin * density then
			xoffset = margin * density - left
		end

		-- Upper third: below action/status bar + topPad, never flush with the top.
		local yoffset = ab + (topPad * density) - top
		if yoffset < ab + (margin * density) then
			yoffset = ab + (margin * density)
		end
		-- Keep the whole pad on-screen vertically.
		local maxBottom = heightPixels - (56 * density)
		if bottom + yoffset > maxBottom then
			yoffset = maxBottom - bottom
		end

		for i,b in pairs(set) do
			b.x = b.x + xoffset
			b.y = b.y + yoffset
		end
	end

	alignSet("default")
	alignSet("tutorial")
end

optionsTable = {}
optionsTable.haptic_edit = setHapticFeedbackEditor
optionsTable.haptic_press = setHapticFeedbackPressed
optionsTable.haptic_flip = setHapticFeedbackFlipped
optionsTable.roundess = setRoundness
-- auto_launch kept for settings XML compat; long-press on buttons never opens editor.
optionsTable.auto_launch = setAutoLaunch
optionsTable.auto_create = setAutoCreate
optionsTable.show_gesture_hints = setShowGestureHints

options = {}
options.haptic_edit = 0
options.haptic_press = 0
options.haptic_flip = 0
options.roundness = 6
-- Default off: edge-back gestures were accidentally entering button edit mode.
-- Use long-press on the wrench/overflow icon instead.
options.auto_launch = false
options.auto_create = true
options.show_gesture_hints = true

function setDebug(off)
	if(not off) then
		debugString("Button server entering debug mode...")
		WindowXCallS(buttonWindowName,"setDebug","on")
		debugInfo = true
	else
		debugString("Button leaving debug mode...")
		WindowXCallS(buttonWindowName,"setDebug","off")
		debugInfo = false
	end
end


function callbackImport()
 checkImport()
end

function importButtons(data)
	local data = loadstring(data)()
end

--utility functions for the external button window to harvest the internal buttons.
function checkImport()
 if(PluginSupports("button_window","exportButtons")) then
   WindowXCallS(buttonWindowName,"askImport")
 else
   WindowXCallS(buttonWindowName,"failImport","Internal button window plugin does not support exporting buttons. Please update BlowTorch")
 end
end

function doImport()
 CallPlugin("button_window","exportButtons",props.name)
end

function exportButtons(target)
	local wad = {}
	wad.selected = current_set
	wad.sets = buttonsets
	wad.defaults = buttonset_defaults
	CallPlugin(target,"importButtons",serialize(wad))
end

function importButtons(data)
 local wad = loadstring(data)()
 current_set = wad.selected
 buttonsets = wad.sets
 buttonset_defaults = wad.defaults
 loadButtonSet(current_set)
 
 --count the buttons for the import message.
 local count = 0
 for i,v in pairs(buttonsets) do
   for j,k in pairs(v) do
     count = count + 1
   end
 end
 WindowXCallS(buttonWindowName,"importSuccess",tostring(count))
end

-- In-memory accordion demo for default/tutorial ACC buttons.
-- CallPlugin always passes one string arg (may be ""); we ignore it.
-- With accordionTrigger "tap", parent command does not fire — keep command empty.
-- Skip buttons that already have accordion children (do not overwrite custom ACC).
function ensureTutorialAccordion(args)
	local function applyAccordion(btn)
		btn.command = ""
		-- Expand downward so children do not cover HELP/SWIPE/HOLD above ACC.
		btn.accordionDirection = "down"
		btn.accordionTrigger = "tap"
		btn.accordionAutoClose = true
		btn.accordionHoldMs = 450
		btn.accordionChildLayout = "along"
		btn.accordionChildren = {
			{ label = "LOOK", command = "look" },
			{ label = "SCORE", command = "score" },
			{ label = "TIP", command = ".tutorial buttons_accordion" },
		}
	end

	local function ensureSet(setName, insertX, insertY)
		local set = buttonsets[setName]
		if set == nil then return end

		local found = nil
		for i,b in ipairs(set) do
			if b.label == "ACC" then
				found = b
				break
			end
		end

		if found ~= nil then
			-- Always refresh starter ACC so direction/layout fixes apply after updates.
			applyAccordion(found)
		else
			local tmp = {}
			tmp.x = insertX
			tmp.y = insertY
			tmp.label = "ACC"
			tmp.labelSize = 12
			applyAccordion(tmp)
			table.insert(set, tmp)
		end
	end

	ensureSet("default", 23, 248)
	ensureSet("tutorial", 68, 68)

	-- Reload if the visible set is one we mutated; pcall if window not ready yet.
	if current_set == "default" or current_set == "tutorial" then
		pcall(loadButtonSet, current_set)
	end
end

debugString("Button Server Loaded")

