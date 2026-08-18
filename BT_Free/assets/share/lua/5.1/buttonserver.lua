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

-- loadstring returns nil plus a message when the string does not compile, so
-- the bare `loadstring(data)()` this file used to do is `nil()` on a truncated
-- or half-written blob: "attempt to call a nil value", which the Java pcall
-- turns into one red line and no buttons at all. Guard instead, name what
-- failed, and leave the previous value in place so one bad section does not
-- take the whole load down. Same shape as buttonwindow.applyFloatPosition.
local function loadSerialized(data, what)
	if(type(data) ~= "string" or data == "") then
		Note(string.format("\nbutton server: %s is empty; skipped.\n", what))
		return nil
	end
	local chunk = loadstring(data)
	if(chunk == nil) then
		Note(string.format("\nbutton server: %s could not be parsed; skipped.\n", what))
		return nil
	end
	local ok, value = pcall(chunk)
	if(not ok or type(value) ~= "table") then
		Note(string.format("\nbutton server: %s is not a table; skipped.\n", what))
		return nil
	end
	return value
end

debugString("Button Server Loading...")

buttonsets = {} --raw table, holds tables of buttons.
buttonset_defaults = {} --raw table, holds defaults for a set name.

set_def = BUTTONSET_DATA:new()
set = {}

lob = {}

function loadButtonSet(args)

	-- args reaches here from .loadset, from a button's "Switch to button set",
	-- and from current_set at connect. A nil got as far as the concatenation
	-- below and raised there, which reads as a Lua error rather than as "that
	-- set does not exist".
	if args == nil then
		Note("\nNo button set was named, so nothing was loaded.\n")
		return
	end

	debugString("Button Server sending button set, "..args)

	lob.name = args
	lob.set = buttonsets[args]

	if(lob.set == nil) then
		-- Said out loud, not through debugString: debugInfo is false in every
		-- shipped build, so a set name that does not exist produced no buttons
		-- and no message at all -- including at connect, where the name comes
		-- from the profile rather than from anything the player typed.
		Note("\nButton set \"" .. tostring(args) .. "\" does not exist, so the"
			.. " buttons on screen are unchanged. Options -> Button sets lists"
			.. " the ones this profile has.\n")
		return
	end

	debugString("Button Set "..args.." has ".. #lob.set .." buttons");

	-- Never hand the window a set with tiles outside the screen: they cannot be
	-- tapped, and if the set has no reachable way back the session is stuck.
	local okSanitize, repaired = pcall(sanitizeButtonSet, args)

	lob.set = buttonsets[args]
	lob.default = buttonset_defaults[args]

	current_set = args
	local payload = marshal.encode(lob)
	WindowXCallB(buttonWindowName,"loadButtons",payload)

	-- Hand the buttons over first, then save. Both WindowXCallB and SaveSettings
	-- only post to the same Connection handler, so whichever is queued first
	-- runs first: saving before this put an entire settings write in front of
	-- the payload, and the player sat looking at the old set until it finished.
	if okSanitize and repaired then
		Note("\nButton set \"" .. tostring(args) .. "\" had buttons off screen; moved them back into view.\n")
		if SaveSettings ~= nil then
			pcall(SaveSettings)
		end
	end
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
-- Always-reachable layout wizard from the input bar (UI owns the dialog).
RegisterSpecialCommand("layoutwizard","showLayoutWizardCmd")
RegisterSpecialCommand("buttonopacity","setButtonOpacity")
RegisterSpecialCommand("buttonsopacity","setButtonOpacity")

function setButtonOpacity(arg)
	WindowXCallS(buttonWindowName, "setButtonOpacity", arg ~= nil and tostring(arg) or "")
end

-- "default" is the set every profile starts with. This used to read
-- `current_set = DEFAULT`, and DEFAULT is not defined anywhere in the Lua tree,
-- so it was `current_set = nil` until the profile's <selected> element was
-- parsed. Anything that saved before that point wrote its buttons under a set
-- with no name; a live profile here has exactly that -- a nameless set holding
-- two buttons called "newb0" that nothing can reach.
--
-- scripts/lua_unbound.py did not catch it: that guard reads module(...) files,
-- and this one is a plain script.
current_set = "default"

function clearButtons()
	--all that needs to be done is call into the window to kick the process off
	WindowXCallS(buttonWindowName,"clearButtons","")
end

function saveButtons(arg)
	--Note("SAVE BUTTONS IMPL")
	
	local tmp = loadSerialized(arg, "the button set being saved")
	if(tmp == nil) then return end

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

--- A name like "main" that no set is using yet: "main copy", then "main copy 2".
local function freeSetName(base)
	local candidate = base .. " copy"
	if buttonsets[candidate] == nil then
		return candidate
	end
	-- Bounded rather than while true: a corrupt table that somehow answers to
	-- every name must not spin the service thread.
	for n = 2, 99 do
		candidate = base .. " copy " .. n
		if buttonsets[candidate] == nil then
			return candidate
		end
	end
	return nil
end

--- Copy a table of button data, one level of nesting deep.
---
--- Deep enough for what a button holds: the values are scalars apart from
--- accordionChildren, which is a list of scalars. A shallow copy would leave the
--- two sets sharing that list, so editing the copy's accordion would silently
--- edit the original's.
---
--- rawget/pairs on purpose: only a button's **own** values are copied, and
--- inherited ones must stay inherited. Copying the resolved values instead would
--- freeze the factory defaults into the new set — the same trap that made button
--- sizes revert (see dropRedundantOwnValues in buttonwindow.lua).
local function copyButtonData(src)
	local out = {}
	for k, v in pairs(src) do
		if type(v) == "table" then
			local inner = {}
			for ik, iv in pairs(v) do
				inner[ik] = iv
			end
			out[k] = inner
		else
			out[k] = v
		end
	end
	return out
end

--- Duplicate a whole button set, buttons and set defaults, under a new name.
function copyButtonSet(name)
	local source = buttonsets[name]
	if source == nil then
		Note("\nNo button set called " .. tostring(name) .. ".\n")
		return
	end
	local target = freeSetName(name)
	if target == nil then
		Note("\nToo many copies of " .. tostring(name) .. " already.\n")
		return
	end

	local copiedButtons = {}
	for i = 1, #source do
		if source[i] ~= nil then
			copiedButtons[#copiedButtons + 1] = copyButtonData(source[i])
		end
	end
	buttonsets[target] = copiedButtons

	local sourceDefaults = buttonset_defaults[name]
	buttonset_defaults[target] = sourceDefaults ~= nil
			and copyButtonData(sourceDefaults) or {}

	Note("\nCopied " .. name .. " to \"" .. target .. "\" ("
		.. #copiedButtons .. " button(s)).\n")
	-- Save immediately: a copy that only exists in memory is lost to the next
	-- restart, and the player has no way to tell the difference until then.
	if SaveSettings ~= nil then
		pcall(SaveSettings)
	end
	getButtonSetList()
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
	tmp.swipeUpLeftCommand = a:getValue("","swipeUpLeftCommand") or ""
	tmp.swipeUpRightCommand = a:getValue("","swipeUpRightCommand") or ""
	tmp.swipeDownLeftCommand = a:getValue("","swipeDownLeftCommand") or ""
	tmp.swipeDownRightCommand = a:getValue("","swipeDownRightCommand") or ""
	tmp.showGestureLabel = a:getValue("","showGestureLabel") ~= "false"
	tmp.showGestureHints = a:getValue("","showGestureHints") ~= "false"
	tmp.wrapLabel = a:getValue("","wrapLabel") == "true"
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
	local loaded = loadSerialized(body, "the saved button sets")
	if(loaded == nil) then return end
	buttonsets = loaded
end
buttonserializer = {}
buttonserializer["end"] = handleButtonSerializer
buttonserializer_cb = luajava.createProxy("android.sax.TextElementListener",buttonserializer)

function handleChromeGestures(body)
	options.chrome_gestures = body or ""
	loadOptions()
end
chromegestureserializer = {}
chromegestureserializer["end"] = handleChromeGestures
chromegestureserializer_cb = luajava.createProxy("android.sax.TextElementListener",chromegestureserializer)

function handleButtonSetSerializer(body)
	--Note("doing string serailze for buttonsets")
	local loaded = loadSerialized(body, "the saved button set defaults")
	if(loaded == nil) then return end
	buttonset_defaults = loaded
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
	local chromegestures = sets:getChild("chromegestures")

	set:setStartElementListener(bset_cb)
	button:setStartElementListener(button_cb)
	selected:setTextElementListener(selected_cb)
	
	buttons:setTextElementListener(buttonserializer_cb)
	defaults:setTextElementListener(buttonsetserializer_cb)
	chromegestures:setTextElementListener(chromegestureserializer_cb)
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
	local loaded = loadSerialized(data, "the defaults for set "..tostring(current_set))
	if(loaded == nil) then return end
	defaults = loaded

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

	-- Chrome gestures ride along here rather than in the plugin's settings
	-- options: those are written by the options dialog only, and this is edited
	-- from the button editor. Written as its own element so an older profile
	-- without it simply never fires the listener.
	out:startTag("","chromegestures")
		out:cdsect(options.chrome_gestures or "")
	out:endTag("","chromegestures")
	out:endTag("","buttonsets")
	--delta = System:currentTimeMillis() - now
	----Note("saved all buttons, took "..delta.." millis.")
end

function buttonLayerReady()
	local added = false
	local okEnsure, result = pcall(ensureLayoutSettingsOptions)
	if okEnsure and result then
		added = true
	end
	loadButtonSet(current_set)
	loadOptions()
	if added and SaveSettings ~= nil then
		pcall(SaveSettings)
	end
	-- Connect-time replay of stored options is over; from here a change to the
	-- size dropdown is the player picking one, and should resize the set.
	layoutOptionsPrimed = true
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
	-- The button editor's checkbox calls this setter and nothing else, so the
	-- choice used to live in this table only: on the next launch the stored
	-- option still said true and every button came back covered in badges.
	persistToggleOption("show_gesture_hints", on)
end

-- Gesture bindings for the input bar and the Edit / Send / overflow buttons.
-- One option holding every binding, because nineteen separate options would be
-- unreadable in the settings file. MainWindow parses it (ChromeGestures.java).
function setChromeGestures(value)
	options.chrome_gestures = value ~= nil and value or ""
	loadOptions()
	-- Force the plugin XML to be rewritten so the bindings survive a restart.
	if SaveSettings ~= nil then
		pcall(SaveSettings)
	end
end

function setShowSwipePreview(value)
	-- Same contract as setShowGestureHints: string from PluginXCallS, boolean
	-- from the OnOptionChanged path.
	local on = (value == true or value == "true" or value == "1")
	options.show_swipe_preview = on and "true" or "false"
	loadOptions()
	-- Same story as show_gesture_hints. The option row itself is injected by
	-- ensureLayoutSettingsOptions, because this key was never in the settings XML.
	persistToggleOption("show_swipe_preview", on)
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
-- Columns 1-3 are a full eight-direction compass rose so the player can see the
-- diagonal layout straight away; column 4 is tutorial navigation. Same gesture map
-- as the fresh-MUD default: tap=walk, outward swipe=look, opposite swipe=open,
-- flip/hold=close. The diagonal tiles use the diagonal swipes for that, which
-- doubles as a live demo of eight-way gestures.
local STARTER_DEFAULT_BUTTONS = {
	{ x=23,  y=23,  label="PREV",   command=".tutorial prev",   labelSize=11 },
	{ x=68,  y=23,  label="NEXT",   command=".tutorial next",   labelSize=11 },
	{ x=113, y=23,  label="TOPICS", command=".tutorial topics", labelSize=10 },
	{ x=158, y=23,  label="HELP",   command=".tutorial start",  labelSize=11 },

	{ x=23,  y=68,  label="NW",     command="northwest", flipCommand="close nw", holdCommand="close nw", labelSize=14,
	  swipeUpLeftCommand="look nw", swipeDownRightCommand="open nw" },
	{ x=68,  y=68,  label="N",      command="north", flipCommand="close n", holdCommand="close n", labelSize=14,
	  swipeUpCommand="look n", swipeDownCommand="open n" },
	{ x=113, y=68,  label="NE",     command="northeast", flipCommand="close ne", holdCommand="close ne", labelSize=14,
	  swipeUpRightCommand="look ne", swipeDownLeftCommand="open ne" },
	{ x=158, y=68,  label="INV",    command=".note INV: on a real MUD this sends inventory.", labelSize=11 },

	{ x=23,  y=113, label="W",      command="west",  flipCommand="close w", holdCommand="close w", labelSize=14,
	  swipeLeftCommand="look w", swipeRightCommand="open w" },
	{ x=68,  y=113, label="LOOK",   command=".note LOOK: tap=look; swipes peek in all eight directions on a live pad.", labelSize=11,
	  swipeUpCommand=".note Swipe↑ = look n", swipeDownCommand=".note Swipe↓ = look s",
	  swipeLeftCommand=".note Swipe← = look w", swipeRightCommand=".note Swipe→ = look e",
	  swipeUpLeftCommand=".note Swipe↖ = look nw", swipeUpRightCommand=".note Swipe↗ = look ne",
	  swipeDownLeftCommand=".note Swipe↙ = look sw", swipeDownRightCommand=".note Swipe↘ = look se" },
	{ x=113, y=113, label="E",      command="east",  flipCommand="close e", holdCommand="close e", labelSize=14,
	  swipeRightCommand="look e", swipeLeftCommand="open e" },
	{ x=158, y=113, label="SWIPE",  command=".tutorial buttons_swipe", labelSize=11,
	  swipeUpCommand=".note Swipe↑ tip: about one finger-width past the tile edge.",
	  swipeDownCommand=".note Swipe↓ tip: swipe beats Flip when both are set.",
	  swipeLeftCommand=".note Swipe← tip: outward=look, opposite=open — diagonals too.",
	  swipeRightCommand=".note Swipe→ tip: flip/hold = close that way.",
	  swipeUpLeftCommand=".note Swipe↖ tip: corners are their own gesture now.",
	  swipeDownRightCommand=".note Swipe↘ tip: a corner with nothing set falls back to the nearest straight swipe." },

	{ x=23,  y=158, label="SW",     command="southwest", flipCommand="close sw", holdCommand="close sw", labelSize=14,
	  swipeDownLeftCommand="look sw", swipeUpRightCommand="open sw" },
	{ x=68,  y=158, label="S",      command="south", flipCommand="close s", holdCommand="close s", labelSize=14,
	  swipeDownCommand="look s", swipeUpCommand="open s" },
	{ x=113, y=158, label="SE",     command="southeast", flipCommand="close se", holdCommand="close se", labelSize=14,
	  swipeDownRightCommand="look se", swipeUpLeftCommand="open se" },
	{ x=158, y=158, label="HOLD",   command=".tutorial buttons_hold", labelSize=11,
	  holdCommand=".note Hold tip: H badge = Hold is set. On the compass, hold = close." },

	{ x=23,  y=203, label="U",      command="up",    flipCommand="close u", holdCommand="close u", labelSize=14,
	  swipeUpCommand="look u", swipeDownCommand="open u" },
	{ x=68,  y=203, label="D",      command="down",  flipCommand="close d", holdCommand="close d", labelSize=14,
	  swipeDownCommand="look d", swipeUpCommand="open d" },
	{ x=113, y=203, label="ACC",    command="", labelSize=11 },
	{ x=158, y=203, label="CLEAR",  command=".clearbuttons", labelSize=11 },

	{ x=23,  y=248, label="LOAD",   command=".loadset tutorial", labelSize=11,
	  holdCommand=".note Hold LOAD: .loadset default — restore the full starter pad.",
	  flipCommand=".loadset default" },
}

-- The smaller pad that LOAD switches to, so ".loadset" can be demonstrated.
-- DEF sits in it as the visible way back to the main pad.
--
-- This has to exist as a canonical dp table for the same reason the pad above
-- does: alignDefaultButtons scales dp to pixels in place, so any set it touches
-- must be rebuilt from dp first or it gets multiplied by the display density all
-- over again on every run.
local STARTER_TUTORIAL_BUTTONS = {
	{ x=23,  y=23,  label="START",  command=".tutorial start",             labelSize=11 },
	{ x=68,  y=23,  label="TOPICS", command=".tutorial topics",            labelSize=10 },
	{ x=113, y=23,  label="SWIPE",  command=".tutorial buttons_swipe",     labelSize=11 },
	{ x=23,  y=68,  label="HOLD",   command=".tutorial buttons_hold",      labelSize=11 },
	{ x=68,  y=68,  label="ACC",    command=".tutorial buttons_accordion", labelSize=11 },
	{ x=113, y=68,  label="LOOK",   command="look",                        labelSize=11 },
	{ x=23,  y=113, label="N",      command="north", labelSize=14 },
	{ x=68,  y=113, label="W",      command="west",  labelSize=14 },
	{ x=113, y=113, label="S",      command="south", labelSize=14 },
	{ x=23,  y=158, label="E",      command="east",  labelSize=14 },
	{ x=68,  y=158, label="DEF",    command=".loadset default", labelSize=11,
	  holdCommand=".note DEF goes back to the main pad — this is how you undo LOAD." },
	{ x=113, y=158, label="CLEAR",  command=".clearbuttons", labelSize=11 },
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

local function rebuildStarterSet(setName, source)
	buttonset_defaults[setName] = buttonset_defaults[setName] or {}
	for k,v in pairs(STARTER_SET_DEFAULTS) do
		buttonset_defaults[setName][k] = v
	end
	local set = {}
	for i,src in ipairs(source) do
		set[i] = cloneStarterButton(src)
	end
	buttonsets[setName] = set
end

-- Rebuild the starter sets from the canonical DP layouts, pin them, refresh UI.
--
-- Both sets are rebuilt, not just "default". alignDefaultButtons scales dp to
-- pixels in place and is applied to both, so a set it aligns without rebuilding
-- gets multiplied by the display density again on every run. That is what
-- happened to "tutorial": its coordinates compounded to around 1e15, which put
-- nearly the whole pad — including the DEF button that leads back — far off
-- screen, leaving a handful of stray tiles and no way to undo LOAD.
function installStarterButtonLayout(args)
	rebuildStarterSet("default", STARTER_DEFAULT_BUTTONS)
	rebuildStarterSet("tutorial", STARTER_TUTORIAL_BUTTONS)
	current_set = "default"
	local okAlign, alignErr = pcall(alignDefaultButtons)
	if not okAlign then
		Note("\nButton align failed: " .. tostring(alignErr) .. "\n")
	end
	pcall(ensureTutorialAccordion, "")
	pcall(loadButtonSet, "default")
	-- Offline tutorial owns the pad — never auto-offer the MUD layout wizard.
	options.layout_wizard_pending = "false"
	persistLayoutOption("layout_wizard_pending", false, true)
	if SaveSettings ~= nil then
		pcall(SaveSettings)
	end
end

--------------------------------------------------------------------------
-- MUD layout packs (canonical DP tables). Separate from STARTER_* offline
-- pads — installPack must never call installStarterButtonLayout or rewrite
-- those tables. Accordion children live here in Lua only: HyperSAX XML
-- drops accordion/floating fields, so packs cannot be seeded from XML.
--------------------------------------------------------------------------

local PACK_SET_DEFAULTS = {
	width = 42, height = 42, labelSize = 12, gridXwidth = 45, gridYwidth = 45,
}

-- The canonical PACK_* tables are laid out on a fixed lattice: centres at
-- 23, 68, 113, … i.e. PACK_PITCH_DP apart, tile PACK_TILE_DP across, so the gap
-- between neighbours is PACK_GAP_DP. Every size preset is that same lattice
-- scaled by pitch/PACK_PITCH_DP, which is why the compass rose survives a
-- resize: geometry is regenerated from the table, never re-flowed from whatever
-- is on screen.
local PACK_PITCH_DP = 45
local PACK_TILE_DP = 42
local PACK_GAP_DP = PACK_PITCH_DP - PACK_TILE_DP

-- Bottom chrome the pad must clear: the input bar plus the edit/Send row plus
-- the gesture bar. Measured at ~82dp on a Pixel 9a (1080x2424 @2.625) with the
-- input bar showing; 96 leaves a little air. The service cannot see the window's
-- real height — it only has DisplayMetrics — so this is a constant, and it is
-- the one number to change if a pad still lands under the input bar.
local PACK_BOTTOM_CHROME_DP = 96
local PACK_EDGE_MARGIN_DP = 10

-- Where a wizard-installed pad's top edge goes: this far under the action bar.
--
-- Anchoring to the bottom of the screen was wrong, and the device said so: with
-- the soft keyboard up, the visible game area on a Pixel 9a ends around 485dp of
-- 923dp, so a bottom-anchored pad was almost entirely behind the keyboard. This
-- is the placement measured off the maintainer's own screenshot (pad top 239px
-- with a 147px action bar → 35dp), which clears the keyboard with room to spare.
--
-- Below the pad there is now the whole game area, which is where every pack's
-- accordion (MORE, NAV, TIP, CAST, DOORS, CHAT) opens: they sit on the pack's
-- last row and expand DOWNWARD, so they no longer unfold over the compass rose.
local PACK_TOP_PAD_DP = 35

-- Fraction of the screen height still visible once the soft keyboard is up.
-- Measured on the maintainer's Pixel 9a with SwiftKey: the game area ended at
-- 1272px of 2424. A fraction rather than a dp constant because IMEs scale with
-- the screen. Named size presets are capped so the whole pad stays above this
-- line; "Fit to screen" is exempt, since being asked to fill the screen is the
-- entire point of it.
local PACK_KEYBOARD_VISIBLE_FRACTION = 0.525

-- GetActionBarHeight(), made safe to anchor against.
--
-- It comes from a SharedPreference two activities used to disagree about, read
-- once when the Connection is constructed: ChromeController wrote the top inset,
-- the launcher wrote contentViewTop - statusBarHeight, which under a NoActionBar
-- theme is *minus* the status bar height. A -152 landed a pad's first row above
-- the top of the screen. The launcher is fixed, but this value crosses a process
-- boundary and survives in preferences, so refuse anything it cannot sensibly be:
-- never negative, never more than a quarter of the screen.
local function safeActionBarHeight(screenHeightPx)
	local ab = tonumber(GetActionBarHeight()) or 0
	if ab ~= ab or ab < 0 then
		return 0
	end
	local cap = (tonumber(screenHeightPx) or 0) * 0.25
	if cap > 0 and ab > cap then
		return cap
	end
	return ab
end

local function packHasAccordion(source)
	for _, b in ipairs(source) do
		if type(b.accordionChildren) == "table" and #b.accordionChildren > 0 then
			return true
		end
	end
	return false
end

-- Distinct lattice columns/rows a pack occupies. Counted from the table rather
-- than assumed: compass is 5x5, explorer 5x4, and a pack that does not fit at
-- the asked-for size has to be scaled down, not left to overlap.
local function packExtent(source)
	local xs, ys, cols, rows = {}, {}, 0, 0
	for _, b in ipairs(source) do
		local x, y = tonumber(b.x), tonumber(b.y)
		if x ~= nil and not xs[x] then xs[x] = true; cols = cols + 1 end
		if y ~= nil and not ys[y] then ys[y] = true; rows = rows + 1 end
	end
	return math.max(1, cols), math.max(1, rows)
end

-- Resolve a wizard size preset to an actual tile size and lattice pitch, capped
-- so the whole pad fits the screen. Uncapped is what produced 72dp tiles on a
-- 45dp pitch: every tile overlapping its neighbour by 27dp, and the right-hand
-- column off the edge.
local function packGeometryFor(source, preset)
	local p = string.lower(tostring(preset or ""))
	p = p:match("^%s*(.-)%s*$") or p
	if p == "fit" then p = "fit_square" end
	if p == "extra_large" or p == "extralarge" or p == "extra large" then p = "xl" end

	local named = { compact = 32, comfortable = 42, large = 56, xl = 72 }
	local want = named[p]
	if want == nil and p ~= "fit_square" then
		want = PACK_TILE_DP
	end

	local cols, rows = packExtent(source)
	-- A bottom-row accordion needs one more row of clear space beneath the pad
	-- than the pad itself occupies, or its opened row runs into the input bar.
	local rowsNeeded = rows
	if packHasAccordion(source) then
		rowsNeeded = rows + 1
	end
	local d = tonumber(GetDisplayDensity()) or 1
	local availW, availH, keyboardH = 0, 0, 0
	if context ~= nil then
		local ok, metrics = pcall(function()
			return context:getResources():getDisplayMetrics()
		end)
		if ok and metrics ~= nil then
			local screenPx = tonumber(metrics.heightPixels) or 0
			local screenH = screenPx / d
			local ab = safeActionBarHeight(screenPx) / d
			local padTop = ab + PACK_TOP_PAD_DP
			availW = (tonumber(metrics.widthPixels) or 0) / d - 2 * PACK_EDGE_MARGIN_DP
			-- Room for the pad plus its accordion row above the input bar.
			availH = screenH - padTop - PACK_BOTTOM_CHROME_DP - PACK_EDGE_MARGIN_DP
			-- Room for the pad alone above the soft keyboard.
			keyboardH = screenH * PACK_KEYBOARD_VISIBLE_FRACTION
				- padTop - PACK_EDGE_MARGIN_DP
		end
	end

	local pitch
	if want ~= nil then
		pitch = want + PACK_GAP_DP
	else
		pitch = 1e9 -- fit_square: take the largest pitch the screen allows
	end
	if availW > 0 then pitch = math.min(pitch, math.floor(availW / cols)) end
	if availH > 0 then pitch = math.min(pitch, math.floor(availH / rowsNeeded)) end
	-- Keep named presets usable while typing. Six rows of 72dp does not fit above
	-- the keyboard, so compass at Extra large comes back a little smaller — still
	-- clearly bigger than Large, and wholly visible, which is the point.
	if want ~= nil and keyboardH > 0 then
		pitch = math.min(pitch, math.floor(keyboardH / rows))
	end
	if pitch > 1e8 then pitch = PACK_PITCH_DP end -- no metrics: canonical size
	if pitch < 19 then pitch = 19 end             -- 16dp tile floor + gap

	return pitch - PACK_GAP_DP, pitch
end

local function clonePackButton(src)
	local b = {}
	for k,v in pairs(src) do
		b[k] = v
	end
	-- Accordion children are a nested list — shallow-clone so a later edit of
	-- one set does not mutate the canonical pack table's child rows.
	if type(src.accordionChildren) == "table" then
		local kids = {}
		for i,child in ipairs(src.accordionChildren) do
			local c = {}
			for ck,cv in pairs(child) do
				c[ck] = cv
			end
			kids[i] = c
		end
		b.accordionChildren = kids
	end
	return b
end

-- Rebuild a set from its canonical DP table, optionally at a different tile size.
--
-- pitchDp/sizeDp default to the canonical lattice, so old two-argument callers
-- (and the density test) are unchanged. When they differ, both the centres and
-- the grid pitch scale together — resizing a pack must never leave 72dp tiles
-- sitting on a 45dp lattice, which is what made them overlap into a solid slab.
-- Coordinates are always re-derived from `source`, never from the set already in
-- `buttonsets`, so repeated resizes cannot compound the way the tutorial pad did.
function rebuildPackSet(setName, source, pitchDp, sizeDp)
	local pitch = tonumber(pitchDp) or PACK_PITCH_DP
	local size = tonumber(sizeDp) or PACK_TILE_DP
	local scale = pitch / PACK_PITCH_DP

	buttonset_defaults[setName] = buttonset_defaults[setName] or {}
	for k,v in pairs(PACK_SET_DEFAULTS) do
		buttonset_defaults[setName][k] = v
	end
	buttonset_defaults[setName].width = size
	buttonset_defaults[setName].height = size
	buttonset_defaults[setName].gridXwidth = pitch
	buttonset_defaults[setName].gridYwidth = pitch

	local set = {}
	for i,src in ipairs(source) do
		local b = clonePackButton(src)
		b.x = (tonumber(b.x) or 0) * scale
		b.y = (tonumber(b.y) or 0) * scale
		-- Per-button size too: alignButtonSet and sanitizeButtonSet both measure
		-- tiles from b.width/b.height first and only fall back to the defaults.
		b.width = size
		b.height = size
		set[i] = b
	end
	buttonsets[setName] = set
end

-- Compass rose tile: tap=walk, outward swipe=look, opposite swipe=open,
-- flip/hold=close. Matches the shipped default_settings_main.xml dialect
-- (full walk cmds + short look/open/close forms: "look n", "open nw", …).
local function compassDir(x, y, label, walkCmd, dirShort)
	local b = {
		x = x, y = y, label = label, command = walkCmd, labelSize = 14,
		flipCommand = "close " .. dirShort,
		holdCommand = "close " .. dirShort,
	}
	local look = "look " .. dirShort
	local open = "open " .. dirShort
	if dirShort == "n" or dirShort == "u" then
		b.swipeUpCommand = look
		b.swipeDownCommand = open
	elseif dirShort == "s" or dirShort == "d" then
		b.swipeDownCommand = look
		b.swipeUpCommand = open
	elseif dirShort == "e" then
		b.swipeRightCommand = look
		b.swipeLeftCommand = open
	elseif dirShort == "w" then
		b.swipeLeftCommand = look
		b.swipeRightCommand = open
	elseif dirShort == "ne" then
		b.swipeUpRightCommand = look
		b.swipeDownLeftCommand = open
	elseif dirShort == "nw" then
		b.swipeUpLeftCommand = look
		b.swipeDownRightCommand = open
	elseif dirShort == "se" then
		b.swipeDownRightCommand = look
		b.swipeUpLeftCommand = open
	elseif dirShort == "sw" then
		b.swipeDownLeftCommand = look
		b.swipeUpRightCommand = open
	end
	return b
end

local function lookCenter(x, y)
	return {
		x = x, y = y, label = "LOOK", command = "look", labelSize = 11,
		swipeUpCommand = "look n",
		swipeDownCommand = "look s",
		swipeLeftCommand = "look w",
		swipeRightCommand = "look e",
		swipeUpLeftCommand = "look nw",
		swipeUpRightCommand = "look ne",
		swipeDownLeftCommand = "look sw",
		swipeDownRightCommand = "look se",
	}
end

-- Accordion helper (Lua-only; HyperSAX drops these fields).
--
-- Opens DOWN, in one horizontal row. Every pack accordion sits on the pack's
-- last row, and the pad is anchored near the bottom of the screen — so "up"
-- (the old default, stacking children vertically) unfolded straight over the
-- pad's own tiles, hiding the compass rose behind the thing you just tapped.
-- Down lands in the PACK_THUMB_LIFT_DP gap under the pad, and "horizontal"
-- keeps it to a single row so that gap is all it needs.
local function packAccordion(x, y, label, children, opts)
	opts = opts or {}
	return {
		x = x, y = y, label = label, command = opts.command or "", labelSize = opts.labelSize or 11,
		advanced = opts.advanced,
		accordionDirection = opts.direction or "down",
		accordionTrigger = opts.trigger or "tap",
		accordionAutoClose = true,
		accordionHoldMs = opts.holdMs or 450,
		accordionChildLayout = opts.childLayout or "horizontal",
		accordionWrapAfter = opts.wrapAfter or 0,
		accordionChildren = children,
	}
end

-- Hub pad: utilities along the top, eight-way rose bottom-right so the
-- most-used tiles sit under the right thumb. MORE/NAV stay on the last row
-- so their fans open downward into empty game text, not over the rose.
local PACK_COMPASS_BUTTONS = {
	{ x=23,  y=23,  label="INV",   command="inventory", labelSize=11 },
	{ x=68,  y=23,  label="SCORE", command="score", labelSize=10 },
	{ x=113, y=23,  label="EXITS", command="exits", labelSize=10 },
	{ x=158, y=23,  label="WHO",   command="who", labelSize=11 },
	{ x=203, y=23,  label="GET",   command="get all", labelSize=11 },

	{ x=23,  y=68,  label="MAP",   command=".map toggle", flipCommand=".map close",
	  holdCommand=".map open", labelSize=11 },
	{ x=68,  y=68,  label="CBT",   command=".loadset combat",
	  holdCommand=".clearbuttons", flipCommand=".clearbuttons", labelSize=11 },
	{ x=113, y=68,  label="EXP",   command=".loadset explorer", labelSize=11 },
	{ x=158, y=68,  label="SOC",   command=".loadset social", labelSize=11 },
	{ x=203, y=68,  label="EQ", command="equipment", labelSize=11, advanced=true },

	{ x=23,  y=113, label="SCAN", command="scan", labelSize=10, advanced=true },
	compassDir(68,  113, "U",  "up",        "u"),
	compassDir(113, 113, "NW", "northwest", "nw"),
	compassDir(158, 113, "N",  "north",     "n"),
	compassDir(203, 113, "NE", "northeast", "ne"),

	compassDir(68,  158, "D",  "down",      "d"),
	compassDir(113, 158, "W",  "west",      "w"),
	lookCenter(158, 158),
	compassDir(203, 158, "E",  "east",      "e"),

	packAccordion(23, 203, "MORE", {
		{ label = "WHO",  command = "who" },
		{ label = "EQ",   command = "equipment" },
		{ label = "SCAN", command = "scan" },
		{ label = "HELP", command = "help" },
	}, { command = "", trigger = "tap" }),
	packAccordion(68, 203, "NAV", {
		{ label = "ENTER", command = "enter" },
		{ label = "OUT",   command = "out" },
		{ label = "UNLK",  command = "unlock" },
		{ label = "EXA",   command = "examine" },
	}, { advanced = true, trigger = "tap" }),
	compassDir(113, 203, "SW", "southwest", "sw"),
	compassDir(158, 203, "S",  "south",     "s"),
	compassDir(203, 203, "SE", "southeast", "se"),
}

-- Smaller first-day pad: four-way + TIP accordion (advanced keeps accordion).
local PACK_NEWBIE_BUTTONS = {
	compassDir(23,  23,  "N", "north", "n"),
	lookCenter(68, 23),
	compassDir(113, 23,  "E", "east",  "e"),

	compassDir(23,  68,  "W", "west",  "w"),
	{ x=68,  y=68,  label="SCORE", command="score", labelSize=10 },
	compassDir(113, 68,  "S", "south", "s"),

	compassDir(23,  113, "U", "up",    "u"),
	compassDir(68,  113, "D", "down",  "d"),
	{ x=113, y=113, label="GET",  command="get all", labelSize=11 },

	{ x=23,  y=158, label="HELP", command="help", labelSize=11 },
	{ x=68,  y=158, label="DEF",  command=".loadset compass", labelSize=11 },
	packAccordion(113, 158, "TIP", {
		{ label = "WHO",   command = "who" },
		{ label = "EXITS", command = "exits" },
		{ label = "SCAN",  command = "scan" },
	}, { command = "", trigger = "tap" }),
}

local PACK_COMBAT_BUTTONS = {
	{ x=23,  y=23,  label="CON",   command="consider", labelSize=11 },
	{ x=68,  y=23,  label="KILL",  command="kill", labelSize=11 },
	{ x=113, y=23,  label="FLEE",  command="flee", labelSize=11 },

	lookCenter(23, 68),
	{ x=68,  y=68,  label="SCORE", command="score", labelSize=10 },
	{ x=113, y=68,  label="INV",   command="inventory", labelSize=11 },

	{ x=23,  y=113, label="EAT",   command="eat", labelSize=11 },
	{ x=68,  y=113, label="DRINK", command="drink", labelSize=10 },
	{ x=113, y=113, label="STAND", command="stand", labelSize=10 },

	{ x=23,  y=158, label="BACK",  command=".loadset compass", labelSize=11 },
	-- Simple strips accordionChildren → tap still casts. Advanced: hold opens CAST.
	{ x=68,  y=158, label="CAST",  command="cast", labelSize=11,
	  accordionDirection = "down", accordionTrigger = "hold", accordionAutoClose = true,
	  accordionHoldMs = 450, accordionChildLayout = "horizontal",
	  accordionChildren = {
		{ label = "CON",  command = "consider" },
		{ label = "KILL", command = "kill" },
		{ label = "FLEE", command = "flee" },
	  } },
	{ x=113, y=158, label="REST",  command="rest", labelSize=11 },
}

-- Wider explore pad: rose + utilities; advanced DOORS accordion.
local PACK_EXPLORER_BUTTONS = {
	compassDir(23,  23,  "NW", "northwest", "nw"),
	compassDir(68,  23,  "N",  "north",     "n"),
	compassDir(113, 23,  "NE", "northeast", "ne"),
	{ x=158, y=23,  label="SCAN",  command="scan", labelSize=10 },
	{ x=203, y=23,  label="EXA",   command="examine", labelSize=10 },

	compassDir(23,  68,  "W",  "west",      "w"),
	lookCenter(68, 68),
	compassDir(113, 68,  "E",  "east",      "e"),
	{ x=158, y=68,  label="GET",   command="get all", labelSize=11 },
	{ x=203, y=68,  label="ENTER", command="enter", labelSize=10 },

	compassDir(23,  113, "SW", "southwest", "sw"),
	compassDir(68,  113, "S",  "south",     "s"),
	compassDir(113, 113, "SE", "southeast", "se"),
	{ x=158, y=113, label="OUT",   command="out", labelSize=11 },
	{ x=203, y=113, label="UNLK",  command="unlock", labelSize=10 },

	compassDir(23,  158, "U",  "up",        "u"),
	compassDir(68,  158, "D",  "down",      "d"),
	{ x=113, y=158, label="INV",   command="inventory", labelSize=11 },
	packAccordion(158, 158, "DOORS", {
		{ label = "OPEN",  command = "open" },
		{ label = "CLOSE", command = "close" },
		{ label = "UNLK",  command = "unlock" },
		{ label = "LOCK",  command = "lock" },
	}, { advanced = true, trigger = "tap" }),
	{ x=203, y=158, label="BACK",  command=".loadset compass", labelSize=10 },
}

local PACK_SOCIAL_BUTTONS = {
	{ x=23,  y=23,  label="WHO",    command="who", labelSize=11 },
	lookCenter(68, 23),
	{ x=113, y=23,  label="SCORE",  command="score", labelSize=10 },

	{ x=23,  y=68,  label="SAY",    command="say", labelSize=11 },
	{ x=68,  y=68,  label="EMOTE",  command="emote", labelSize=10 },
	{ x=113, y=68,  label="TELL",   command="tell", labelSize=11 },

	{ x=23,  y=113, label="INV",    command="inventory", labelSize=11 },
	{ x=68,  y=113, label="GLANCE", command="glance", labelSize=10 },
	{ x=113, y=113, label="MAP",    command=".map toggle", flipCommand=".map close",
	  holdCommand=".map open", labelSize=11 },

	{ x=23,  y=158, label="BACK",   command=".loadset compass", labelSize=11 },
	{ x=68,  y=158, label="AFK",    command="afk", labelSize=11 },
	{ x=113, y=158, label="CBT",    command=".loadset combat", labelSize=11 },
	packAccordion(158, 158, "CHAT", {
		{ label = "SAY",   command = "say" },
		{ label = "EMOTE", command = "emote" },
		{ label = "TELL",  command = "tell" },
		{ label = "GT",    command = "gt" },
	}, { advanced = true, trigger = "tap" }),
}

-- Catalog for the UI picker. Order is the wizard display order.
local LAYOUT_PACK_LIST = {
	{ id = "compass",  title = "Compass",  blurb = "Eight-way pad, MORE utilities, jumps to combat/explorer/social." },
	{ id = "newbie",   title = "Newbie",   blurb = "Small four-way pad with help and a tip accordion." },
	{ id = "combat",   title = "Combat",   blurb = "Kill/flee/consider plus eat, drink, cast." },
	{ id = "explorer", title = "Explorer", blurb = "Wide rose with scan, doors, and examine." },
	{ id = "social",   title = "Social",   blurb = "Who/say/emote/tell with jumps to compass/combat." },
}

-- Dropdown contents for the two Options rows. Both were free-text fields where
-- the player had to type "fit_square" or "explorer" exactly right; a ListOption
-- stores an index, so these tables are the index→id contract and their ORDER IS
-- THE STORED VALUE. Append only — reordering silently rewrites saved settings.
LAYOUT_SIZE_ITEMS = { "Compact", "Comfortable", "Large", "Extra large", "Fit to screen" }
LAYOUT_SIZE_IDS = { "compact", "comfortable", "large", "xl", "fit_square" }
LAYOUT_PACK_ITEMS = { "Compass", "Newbie", "Combat", "Explorer", "Social" }
LAYOUT_PACK_IDS = { "compass", "newbie", "combat", "explorer", "social" }

-- ListOption hands OnOptionChanged a 0-based index as a string. Old profiles
-- still send the id itself until ensureLayoutSettingsOptions has migrated them,
-- and a legacy .installpack passes an id too, so both spellings must resolve.
local function idFromListValue(value, ids)
	if value == nil or type(ids) ~= "table" then return "" end
	local raw = tostring(value):match("^%s*(.-)%s*$") or ""
	if raw == "" then return "" end
	local n = tonumber(raw)
	if n ~= nil and n == math.floor(n) then
		return ids[n + 1] or ""
	end
	local lower = string.lower(raw)
	for _, id in ipairs(ids) do
		if id == lower then return id end
	end
	return lower
end

local function listIndexForId(id, ids)
	if type(ids) ~= "table" then return nil end
	local lower = string.lower(tostring(id or ""))
	for i, candidate in ipairs(ids) do
		if candidate == lower then return i - 1 end
	end
	return nil
end

local PACK_SOURCES = {
	compass  = PACK_COMPASS_BUTTONS,
	newbie   = PACK_NEWBIE_BUTTONS,
	combat   = PACK_COMBAT_BUTTONS,
	explorer = PACK_EXPLORER_BUTTONS,
	social   = PACK_SOCIAL_BUTTONS,
}

local RESERVED_SET_NAMES = { default = true, tutorial = true }

-- Same host/display globals Plugin.java sets for startertutorial.
-- installPack refuses offline so it cannot clobber the tutorial pads.
-- If these globals are absent, UI must still gate the wizard away from offline.
local function isOfflineLayoutSession()
	if type(connection_host) == "string" and string.lower(connection_host) == "offline" then
		return true
	end
	if type(connection_display) == "string" and connection_display == "Starter Tutorial" then
		return true
	end
	return false
end

local function setNameExists(name)
	if name == nil or name == "" then return false end
	local key = string.lower(tostring(name))
	if RESERVED_SET_NAMES[key] then return true end
	if buttonsets[name] ~= nil or buttonsets[key] ~= nil then return true end
	for existing, _ in pairs(buttonsets) do
		if string.lower(tostring(existing)) == key then
			return true
		end
	end
	return false
end

-- Sanitize a base name and find a free set name (avoids default/tutorial + existing).
function suggestSetName(base)
	local s = string.lower(tostring(base or ""))
	s = string.gsub(s, "%s+", "_")
	s = string.gsub(s, "[^a-z0-9_%-]", "")
	if s == "" then s = "buttons" end
	if #s > 24 then s = string.sub(s, 1, 24) end
	if not setNameExists(s) then
		return s
	end
	local i = 2
	while i <= 99 do
		local cand = s .. "_" .. tostring(i)
		if not setNameExists(cand) then
			return cand
		end
		i = i + 1
	end
	return s .. "_" .. tostring(os.time() % 100000)
end

function getExistingButtonSetNames(args)
	local list = {}
	for name, _ in pairs(buttonsets) do
		list[#list + 1] = tostring(name)
	end
	table.sort(list)
	WindowXCallS(buttonWindowName, "showExistingButtonSetNames", serialize(list))
end

-- Packs install complete: the wizard's Simple/Advanced radio is gone (2 Aug).
-- It was worth 3 tiles on compass, 1 on explorer and social, and *nothing at all*
-- on newbie — a choice nobody could read off the two words, for tiles that are
-- easier to delete in the editor than to discover missing. "simple" is still
-- honoured if an old payload or a .installpack call sends it.
--
-- mode=simple: drop advanced=true tiles; hold-trigger accordions (CAST) lose
-- accordion* fields but keep command as a plain tap; tap-trigger accordions
-- (MORE/TIP) stay intact. mode=advanced (the default): keep everything.
-- Always drop the advanced marker from saved rows.
local function filterPackSource(source, mode)
	local keepAdvanced = (tostring(mode or "advanced"):lower() ~= "simple")
	local out = {}
	for _, src in ipairs(source) do
		if (not keepAdvanced) and src.advanced then
			-- skip advanced-only tile
		else
			local b = clonePackButton(src)
			rawset(b, "advanced", nil)
			if not keepAdvanced then
				local trigger = string.lower(tostring(src.accordionTrigger or ""))
				if trigger == "hold" then
					b.accordionChildren = nil
					b.accordionDirection = nil
					b.accordionTrigger = nil
					b.accordionAutoClose = nil
					b.accordionHoldMs = nil
					b.accordionChildLayout = nil
					b.accordionWrapAfter = nil
				end
			end
			out[#out + 1] = b
		end
	end
	return out
end

local DEFAULT_PRIMARY_COLOR = 0x880000FF
local DEFAULT_SELECTED_COLOR = 0x8800FF00

local function applyPackColors(setName, colors)
	local defs = buttonset_defaults[setName]
	if defs == nil then return end
	colors = type(colors) == "table" and colors or {}
	local primary = colors.primary
	if primary == nil then primary = DEFAULT_PRIMARY_COLOR end
	local selected = colors.selected
	if selected == nil then selected = DEFAULT_SELECTED_COLOR end
	defs.primaryColor = tonumber(primary) or primary
	defs.selectedColor = tonumber(selected) or selected
end

-- Rewrite .loadset / switchTo that target catalog pack ids installed in this
-- batch to their chosen setNames. Uninstalled pack targets stay as pack ids.
local function rewriteLinkTarget(target, packIdToSetName)
	if target == nil then return nil, false end
	local name = tostring(target)
	if name == "" then return "", false end
	local mapped = packIdToSetName[name]
	if mapped ~= nil then
		return mapped, true
	end
	return name, false
end

local function rewriteCommandLinks(cmd, packIdToSetName)
	if type(cmd) ~= "string" or cmd == "" then return cmd end
	-- Any target, not just [%w_%-]+. Pack ids never contain a space, so for the
	-- catalog caller this captures exactly what it captured before and the map
	-- lookup below still decides; what it adds is renaming a set the player
	-- named "main copy", whose .loadset the old pattern could not even see.
	local target = string.match(cmd, "^%.loadset%s+(.-)%s*$")
	if target == nil or target == "" then return cmd end
	-- Catalog keys are lowercase; player setNames keep their chosen casing, and
	-- a rename map carries both spellings for exactly this reason.
	local newName, changed = rewriteLinkTarget(target, packIdToSetName)
	if not changed then
		newName, changed = rewriteLinkTarget(string.lower(target), packIdToSetName)
	end
	if not changed then return cmd end
	if newName == nil or newName == "" then return "" end
	return ".loadset " .. newName
end

local function rewriteButtonCrossLinks(btn, packIdToSetName)
	if type(btn) ~= "table" then return end
	local fields = {
		"command", "flipCommand", "holdCommand",
		"swipeUpCommand", "swipeDownCommand", "swipeLeftCommand", "swipeRightCommand",
		"swipeUpLeftCommand", "swipeUpRightCommand",
		"swipeDownLeftCommand", "swipeDownRightCommand",
	}
	for _, f in ipairs(fields) do
		if btn[f] ~= nil then
			btn[f] = rewriteCommandLinks(btn[f], packIdToSetName)
		end
	end
	if btn.switchTo ~= nil and tostring(btn.switchTo) ~= "" then
		local newName, changed = rewriteLinkTarget(btn.switchTo, packIdToSetName)
		if changed then
			btn.switchTo = newName or ""
		end
	end
	if type(btn.accordionChildren) == "table" then
		for _, child in ipairs(btn.accordionChildren) do
			rewriteButtonCrossLinks(child, packIdToSetName)
		end
	end
end

local function rewriteSetCrossLinks(setName, packIdToSetName)
	local set = buttonsets[setName]
	if set == nil then return end
	for _, btn in pairs(set) do
		rewriteButtonCrossLinks(btn, packIdToSetName)
	end
end

--- Rename a button set, and take every link to it along.
---
--- A set is addressed by its name and by nothing else: `.loadset combat`, a
--- button's switchTo, a button whose command is `.loadset combat`. Moving the
--- table to a new key and stopping there would leave all of those pointing at a
--- set that no longer exists — every one of them silently dead, with no error
--- and nothing on screen to say why. So the rename rewrites the links in the
--- same pass, reusing the cross-link rewriter the layout catalog already uses.
---
--- What it does **not** reach: chrome gestures, which are kept as one opaque
--- serialised blob that string surgery would corrupt, and triggers, aliases and
--- timers, whose commands are Java-side profile data this plugin cannot see. A
--- `.loadset` in any of those still points at the old name afterwards, so the
--- player is told to check them rather than left to find a dead alias later.
---
--- @param data the old name, a newline, the new name. Two lines rather than a
---        serialised table because the list dialog that sends it has no
---        serialiser in its environment, and a set name cannot contain a
---        newline — it is one line of an EditText.
function renameButtonSet(data)
	local text = data ~= nil and tostring(data) or ""
	local from, to = string.match(text, "^(.-)\n(.*)$")
	if from == nil then
		Note("\nRename needs an old name and a new one.\n")
		return
	end
	to = string.match(to, "^%s*(.-)%s*$") or ""

	if buttonsets[from] == nil then
		Note("\nNo button set called " .. from .. ".\n")
		return
	end
	if to == "" then
		Note("\nA button set needs a name.\n")
		return
	end
	if to == from then
		return
	end
	if buttonsets[to] ~= nil then
		Note("\nThere is already a button set called \"" .. to .. "\".\n")
		return
	end

	buttonsets[to] = buttonsets[from]
	buttonsets[from] = nil
	buttonset_defaults[to] = buttonset_defaults[from]
	buttonset_defaults[from] = nil

	-- Both spellings: a command may say .loadset Combat where the set is
	-- "combat", and rewriteCommandLinks tries the raw target then the lowered one.
	local renameMap = { [from] = to, [string.lower(from)] = to }
	for setName, _ in pairs(buttonsets) do
		rewriteSetCrossLinks(setName, renameMap)
	end

	if current_set == from then current_set = to end
	if working_set == from then working_set = to end

	Note("\nRenamed button set \"" .. from .. "\" to \"" .. to .. "\".\n")
	Note("Buttons that loaded it now load \"" .. to .. "\". A trigger, alias,"
		.. " timer or chrome gesture that says \".loadset " .. from
		.. "\" still says it — those are not button data and have to be"
		.. " changed by hand.\n")

	-- Saved at once: a rename that lives only in memory is lost at the next
	-- restart, and the links have already been rewritten to match it.
	if SaveSettings ~= nil then
		pcall(SaveSettings)
	end
	getButtonSetList()
end

function getLayoutPackList(args)
	WindowXCallS(buttonWindowName, "showLayoutPackList", serialize(LAYOUT_PACK_LIST))
end

function getLayoutWizardState(args)
	local existingNames = {}
	for name, _ in pairs(buttonsets) do
		existingNames[#existingNames + 1] = tostring(name)
	end
	table.sort(existingNames)
	local state = {
		pending = options.layout_wizard_pending,
		size = options.layout_size_preset,
		align = "right",
		mode = "advanced",
		colors = {
			primary = DEFAULT_PRIMARY_COLOR,
			selected = DEFAULT_SELECTED_COLOR,
		},
		existingNames = existingNames,
	}
	WindowXCallS(buttonWindowName, "showLayoutWizardState", serialize(state))
end

function showLayoutWizardCmd(args)
	if isOfflineLayoutSession() then
		Note("\nLayout wizard is for MUD profiles; the offline tutorial keeps its own pad.\n")
		return
	end
	WindowXCallS(buttonWindowName, "showLayoutWizard", args ~= nil and tostring(args) or "")
end

-- Install one or more named packs. Never rebuilds/deletes sibling player sets.
-- args: serialized table (preferred) or legacy "packId|sizePreset" / "packId".
--
-- Finish payload:
--   { installs={{packId,setName,overwrite},...}, loadSet, align, colors, size, mode }
-- Single-install shortcut also accepted: { packId, setName, overwrite, ... }
local function normalizeInstallSpec(t)
	if type(t) ~= "table" then return nil end
	if type(t.installs) == "table" and #t.installs > 0 then
		return t
	end
	local packId = t.packId or t.pack or t.name
	if packId == nil or tostring(packId) == "" then
		return nil
	end
	local setName = t.setName or packId
	-- Old {pack=, size=} and other single-pack shortcuts rebuild that set.
	local overwrite = t.overwrite
	if overwrite == nil then
		overwrite = true
	end
	return {
		installs = {
			{
				packId = tostring(packId),
				setName = tostring(setName),
				overwrite = overwrite,
			},
		},
		loadSet = t.loadSet or tostring(setName),
		align = t.align or "right",
		colors = t.colors,
		size = t.size or t.sizePreset,
		mode = t.mode or "advanced",
	}
end

local function parseLegacyInstallArgs(raw)
	local chosen, sizePreset = raw, ""
	local pipe = string.find(raw, "|", 1, true)
	if pipe ~= nil then
		chosen = string.sub(raw, 1, pipe - 1)
		sizePreset = string.sub(raw, pipe + 1) or ""
	end
	chosen = string.lower((string.match(chosen, "^%s*(.-)%s*$")) or "")
	if PACK_SOURCES[chosen] == nil then
		return nil
	end
	return {
		installs = {
			{ packId = chosen, setName = chosen, overwrite = true },
		},
		loadSet = chosen,
		size = sizePreset,
		align = "right",
		mode = "advanced",
	}
end

local function doInstallBatch(t)
	local align = string.lower(tostring(t.align or "right"))
	if align ~= "left" and align ~= "center" and align ~= "right" then
		align = "right"
	end
	local mode = string.lower(tostring(t.mode or "advanced"))
	if mode ~= "simple" and mode ~= "advanced" then
		mode = "advanced"
	end
	local sizePreset = t.size or t.sizePreset or ""
	local colors = t.colors
	local loadSet = t.loadSet ~= nil and tostring(t.loadSet) or ""

	local succeeded = {}
	local packIdToSetName = {}
	local skipped = 0

	for _, row in ipairs(t.installs) do
		if type(row) == "table" then
			local packId = string.lower(tostring(row.packId or row.pack or ""))
			local setName = tostring(row.setName or packId or "")
			setName = string.match(setName, "^%s*(.-)%s*$") or setName
			local overwrite = (row.overwrite == true or row.overwrite == "true" or row.overwrite == "1")
			local source = PACK_SOURCES[packId]
			if source == nil then
				Note("\nUnknown layout pack: " .. tostring(packId) .. "\n")
			elseif setName == "" then
				Note("\nlayout install: empty set name for pack " .. packId .. "\n")
			elseif RESERVED_SET_NAMES[string.lower(setName)] and not overwrite then
				Note("\nlayout install: set \"" .. setName
					.. "\" is reserved (default/tutorial); skipped.\n")
				skipped = skipped + 1
			elseif setNameExists(setName) and not overwrite then
				Note("\nlayout install: set \"" .. setName
					.. "\" already exists; skipped (overwrite=false).\n")
				skipped = skipped + 1
			else
				local filtered = filterPackSource(source, mode)
				-- Size and lattice are resolved per pack: explorer is five columns
				-- wide where combat is three, so the largest tile that fits is not
				-- the same number for both.
				local sizeDp, pitchDp = packGeometryFor(filtered, sizePreset)
				rebuildPackSet(setName, filtered, pitchDp, sizeDp)
				applyPackColors(setName, colors)
				-- "pack": top edge just under the action bar, clear of the soft
				-- keyboard. The starter and tutorial pads keep the old
				-- upper-third placement (see alignDefaultButtons).
				local okAlign, alignErr = pcall(alignButtonSet, setName, align, "pack")
				if not okAlign then
					Note("\nButton align failed for \"" .. setName .. "\": "
						.. tostring(alignErr) .. "\n")
				end
				succeeded[#succeeded + 1] = { packId = packId, setName = setName }
				packIdToSetName[packId] = setName
			end
		end
	end

	if #succeeded == 0 then
		Note("\nlayout install: no packs installed"
			.. (skipped > 0 and (" (" .. tostring(skipped) .. " skipped).") or ".")
			.. "\n")
		return false
	end

	-- Map .loadset <packId> → chosen setName only for packs installed this batch.
	if loadSet == "" then
		loadSet = succeeded[1].setName
	end
	for _, row in ipairs(succeeded) do
		rewriteSetCrossLinks(row.setName, packIdToSetName)
	end

	if buttonsets[loadSet] == nil then
		loadSet = succeeded[1].setName
	end

	current_set = loadSet
	pcall(loadButtonSet, loadSet)

	-- Wizard memory (internal; not player-typed Options fields).
	options.layout_pack = succeeded[1].packId
	persistLayoutList("layout_pack", options.layout_pack, LAYOUT_PACK_IDS)
	if sizePreset ~= nil and tostring(sizePreset) ~= "" then
		options.layout_size_preset = idFromListValue(sizePreset, LAYOUT_SIZE_IDS)
		persistLayoutList("layout_size_preset", options.layout_size_preset, LAYOUT_SIZE_IDS)
	end
	options.layout_wizard_pending = "false"
	persistLayoutOption("layout_wizard_pending", false, true)

	if SaveSettings ~= nil then
		pcall(SaveSettings)
	end

	-- UI applies size after load (bare size string is enough).
	local installedPayload = tostring(sizePreset or "")
	WindowXCallS(buttonWindowName, "onLayoutPackInstalled", installedPayload)
	pcall(loadOptions)
	return true
end

function installPack(args)
	if isOfflineLayoutSession() then
		Note("\ninstallPack refused: offline / Starter Tutorial session.\n")
		return
	end

	local t = nil
	if type(args) == "table" then
		t = normalizeInstallSpec(args)
	else
		local raw = args ~= nil and tostring(args) or ""
		if raw == "" then
			Note("\ninstallPack: missing args.\n")
			return
		end
		-- Prefer serialized table; fall back to legacy pack|size string.
		if string.sub(raw, 1, 1) == "{" then
			t = normalizeInstallSpec(loadSerialized(raw, "installPack args"))
		else
			t = parseLegacyInstallArgs(raw)
		end
	end
	if t == nil then
		Note("\ninstallPack: bad args (need installs[] or packId).\n")
		return
	end
	doInstallBatch(t)
end

-- data: serialized finish table from the wizard UI.
function applyLayoutWizardFinish(data)
	if isOfflineLayoutSession() then
		Note("\nlayout wizard refused: offline / Starter Tutorial session.\n")
		return
	end
	local t = nil
	if type(data) == "table" then
		t = normalizeInstallSpec(data)
	else
		t = normalizeInstallSpec(loadSerialized(data, "layout wizard finish data"))
	end
	if t == nil then
		Note("\nlayout wizard: no packs selected.\n")
		return
	end
	doInstallBatch(t)
end

-- Pull every tile of a set back onto the screen.
--
-- Pure clamping, never scaling. Saved sets already hold pixel coordinates, and
-- multiplying those again is exactly what drove the tutorial pad to ~1e15. This
-- runs on every load, so a set damaged by an older build — or dragged somewhere
-- silly — still comes back reachable instead of sitting off the edge where it
-- cannot be tapped or edited.
--
-- Deliberately only enforces screen bounds (half the tile from each edge). Do
-- not subtract action-bar / status-bar / input-bar chrome here: those are not
-- screen edges, and inventing a top keep-out of ~status-bar height shoved every
-- high pad down on every set switch even when tiles were already on screen.
-- buttonwindow.clampLogicalPosition still applies statusoffset when the set is
-- drawn. No keep-out under the overflow menu either — that would quietly move
-- tiles a user placed there on purpose. Starter pads clear the menu through
-- alignDefaultButtons' topPad instead.
-- One centre, one screen. Bounds are per button, from half its own size: a
-- fixed 24dp inset is a bound on the *centre*, so a 72dp tile parked at the
-- limit still hung 12dp off the right edge — visible as the clipped INV/NAV
-- column at XL. nil and NaN both fail these comparisons, so both land on the
-- minimum rather than propagating.
local function clampToScreen(x, y, bw, bh, screenW, screenH)
	local minX, maxX = bw / 2, screenW - bw / 2
	local minY, maxY = bh / 2, screenH - bh / 2
	if maxX < minX then maxX = minX end
	if maxY < minY then maxY = minY end
	if x == nil or x ~= x or x < minX then x = minX end
	if x > maxX then x = maxX end
	if y == nil or y ~= y or y < minY then y = minY end
	if y > maxY then y = maxY end
	return x, y
end

function sanitizeButtonSet(setName)
	local set = buttonsets[setName]
	if set == nil or context == nil then return false end
	local d = tonumber(GetDisplayDensity()) or 1
	local metrics = context:getResources():getDisplayMetrics()
	local w = tonumber(metrics.widthPixels) or 0
	local h = tonumber(metrics.heightPixels) or 0
	if w <= 0 or h <= 0 then return false end

	-- Clamp the pair that belongs to the screen we are actually measuring, and
	-- only that one. buttonwindow gives a button an optional landscape pair
	-- (xLand/yLand, commit "Landscape gets its own button layout"); x/y is the
	-- portrait pair. This function used to clamp x/y whichever way the phone
	-- was held, so loading a set while it was on its side measured the portrait
	-- layout against a landscape screen and dragged the portrait buttons up --
	-- the "moved them back into view" the maintainer saw after editing the
	-- landscape grid.
	--
	-- The other orientation is left alone rather than clamped against a guessed
	-- screen: swapping w and h is not the other orientation's metrics, because
	-- the system bars are inset differently there, and a guess here writes to
	-- settings. Nothing is stranded by that. buttonwindow's loadButtons calls
	-- clampAllButtons with persist unset, and so does onSizeChanged, so a
	-- button with no landscape pair of its own is still pulled onto the screen
	-- in front of the player both when the set is loaded and when the phone is
	-- turned -- it is simply not written back.
	local sideways = w > h

	local defs = buttonset_defaults[setName]
	local moved = false
	for i,b in pairs(set) do
		local bw = (tonumber(b.width) or tonumber(defs and defs.width) or 42) * d
		local bh = (tonumber(b.height) or tonumber(defs and defs.height) or 42) * d

		if sideways then
			-- Normalise the portrait pair even though this orientation must not
			-- move it: coordinates come back from the settings XML as strings,
			-- and buttonwindow compares them with numbers when it sorts and
			-- clamps. Only a value that is not a coordinate at all is replaced,
			-- and with the same minimum the clamp would have picked.
			b.x = tonumber(b.x)
			b.y = tonumber(b.y)
			if b.x == nil or b.x ~= b.x then b.x = bw / 2 end
			if b.y == nil or b.y ~= b.y then b.y = bh / 2 end

			-- The landscape pair only exists once the player has moved this
			-- button while the phone was on its side. Absent means landscape
			-- draws the portrait layout, and writing one here would invent a
			-- landscape layout nobody asked for — so a missing pair stays
			-- missing, and the portrait pair is not touched from here.
			local lx, ly = tonumber(b.xLand), tonumber(b.yLand)
			if lx ~= nil and ly ~= nil then
				local nx, ny = clampToScreen(lx, ly, bw, bh, w, h)
				if nx ~= lx or ny ~= ly then
					moved = true
				end
				b.xLand = nx
				b.yLand = ny
			end
		else
			-- Compare against the numeric original, not the raw field.
			-- Coordinates come back from the settings XML as strings, and in
			-- Lua 1 ~= "1", so comparing to b.x reported every button as moved
			-- on the first load after a profile read — a settings write and a
			-- Note() in the game window every time, for buttons that were
			-- never off screen.
			local x, y = clampToScreen(tonumber(b.x), tonumber(b.y), bw, bh, w, h)
			if x ~= tonumber(b.x) or y ~= tonumber(b.y) then
				moved = true
			end
			-- Still normalise the stored value to a number either way.
			b.x = x
			b.y = y
		end
	end
	return moved
end

-- Scale one set's DP centers by density, then pin horizontally (left|center|right)
-- and vertically to the upper third (below the status/action bar). Multiplies in
-- place: callers must rebuild from a canonical DP table first or coordinates compound.
-- No 2nd arg → legacy center-ish behaviour (starter/tutorial / density test).
-- Wizard installs pass "left"|"center"|"right" explicitly (default "right" there).
--
-- vertical: nil/"top" keeps the historic upper-third placement, which the
-- offline starter and tutorial pads rely on (alignDefaultButtons). "pack" pins
-- the pad's top edge PACK_TOP_PAD_DP under the action bar, which is where wizard
-- installs go: high enough that the soft keyboard cannot cover it. Do not change
-- the default: alignDefaultButtons is the shared caller and the tutorial pad is
-- fenced off.
function alignButtonSet(setName, align, vertical)
	local set = buttonsets[setName]
	local defaults = buttonset_defaults[setName]
	if set == nil then return end

	if align == nil then
		align = "center"
	else
		align = string.lower(tostring(align))
		if align ~= "left" and align ~= "center" and align ~= "right" then
			align = "right"
		end
	end

	local margin = 10
	local topPad = 88
	density = tonumber(GetDisplayDensity()) or 1
	local metrics = context:getResources():getDisplayMetrics()
	heightPixels = tonumber(metrics.heightPixels) or 0
	local widthPixels = tonumber(metrics.widthPixels) or 0
	-- GetActionBarHeight() returns a string from Java, and has been seen negative
	-- (see safeActionBarHeight) — never anchor against it raw.
	local ab = safeActionBarHeight(heightPixels)

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
	local xoffset
	if align == "left" then
		xoffset = margin * density - left
	elseif align == "center" then
		-- Match pre-wizard centering (clamp away from the left edge).
		xoffset = ((widthPixels - clusterW) / 2) - left
		if xoffset < margin * density then
			xoffset = margin * density - left
		end
	else
		-- right
		xoffset = (widthPixels - margin * density - clusterW) - left
	end

	local yoffset
	if tostring(vertical or "") == "pack" then
		-- Wizard packs: top edge PACK_TOP_PAD_DP under the action bar, which is
		-- above the soft keyboard. Anchoring the bottom instead put the pad
		-- behind the keyboard on a real phone — see the constant's note.
		yoffset = ab + (PACK_TOP_PAD_DP * density) - top
	else
		-- Upper third: below action/status bar + topPad, never flush with the top.
		yoffset = ab + (topPad * density) - top
		if yoffset < ab + (margin * density) then
			yoffset = ab + (margin * density)
		end
	end
	-- Keep the whole pad on-screen vertically. A tall pad (compass at XL is six
	-- rows) loses the bottom-anchor before it loses its top row.
	local maxBottom = heightPixels - (PACK_BOTTOM_CHROME_DP * density)
	if bottom + yoffset > maxBottom then
		yoffset = maxBottom - bottom
	end
	local minTop = ab + (margin * density)
	if top + yoffset < minTop then
		yoffset = minTop - top
	end

	for i,b in pairs(set) do
		b.x = b.x + xoffset
		b.y = b.y + yoffset
	end
end

-- Starter tutorial path: always center default+tutorial (unchanged behaviour).
function alignDefaultButtons()
	alignButtonSet("default", "center")
	alignButtonSet("tutorial", "center")
end

-- Fresh MUD from default_settings XML. Wizard skip leaves this pad in place,
-- so pin default right — the same side the wizard itself defaults to. Still
-- density-scale tutorial: the XML set is in dp, and skipping the scale left
-- .loadset tutorial as 42dp tiles on a 45px pitch in the corner. Offline
-- starter stays on alignDefaultButtons (both centered).
function alignMudDefaultButtons()
	alignButtonSet("default", "right")
	alignButtonSet("tutorial", "center")
end

-- Persist into the Java SettingsGroup (what SaveSettings actually writes).
-- Updating only the Lua `options` table is forgotten on the next profile load —
-- same pattern as startertutorial.setShowOnConnect.
--
-- Existing profiles loaded before these keys existed have no SettingsGroup
-- entries; updateBoolean/updateString are silent no-ops then. ensure* adds the
-- missing pending option once (defaults false so upgrades are not nagged).
-- Do not add layout_pack / layout_size_preset StringOptions — those titles
-- were player-visible Options clutter; size/pack memory persists only when
-- the keys already exist in the SettingsGroup.
function ensureLayoutSettingsOptions()
	if GetPluginSettings == nil then
		return false
	end
	local ok, settings = pcall(GetPluginSettings)
	if not ok or settings == nil then
		return false
	end
	local added = false
	local function missing(key)
		local okv, val = pcall(function() return settings:getOptionValue(key) end)
		return (not okv) or val == nil
	end
	if missing("layout_wizard_pending") then
		local okAdd = pcall(function()
			local BooleanOption = luajava.bindClass(
				"com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption")
			local opt = luajava.new(BooleanOption)
			opt:setKey("layout_wizard_pending")
			opt:setTitle("Offer button layout wizard")
			opt:setDescription(
				"Show the pack and size picker once after connect. Cleared when you finish or skip. Re-enable anytime, or use Options → Button → Load button set from wizard.")
			-- Upgrades: never auto-offer; new profiles already have true from XML.
			opt:setValue(false)
			settings:addOption(opt)
		end)
		if okAdd then added = true end
	end
	-- A profile only ever gets the rows that default_settings declared on the day
	-- it was created, so an option added later exists for new profiles and for
	-- nobody else. Measured on the maintainer's phone: samsaramoo.xml (the profile
	-- he plays) has no show_gesture_hints at all, while eden.xml — made later —
	-- does. Without the row there is nowhere for the editor's checkbox to write,
	-- and the setting came back on at every launch. show_swipe_preview was worse:
	-- it was in no settings XML at all, so no profile had it.
	local function ensureBoolean(key, title, description, default)
		if not missing(key) then
			return
		end
		local okAdd = pcall(function()
			local BooleanOption = luajava.bindClass(
				"com.resurrection.blowtorch2.lib.service.plugin.settings.BooleanOption")
			local opt = luajava.new(BooleanOption)
			opt:setKey(key)
			opt:setTitle(title)
			opt:setDescription(description)
			opt:setValue(default)
			settings:addOption(opt)
		end)
		if okAdd then added = true end
	end
	ensureBoolean("show_gesture_hints", "Show gesture hints",
		"Draw swipe arrows, hold (H), and accordion chevrons on buttons.", true)
	ensureBoolean("show_swipe_preview", "Show swipe direction arrow",
		"Draw the arrow across a button while you drag it. The command callout above the button shows either way.",
		true)
	-- layout_pack / layout_size_preset were free-text StringOptions: the player
	-- was expected to type "fit_square" correctly. They are dropdowns now, but a
	-- profile keeps the type it was created with, so the old option has to be
	-- retired before the list can take its key. removeOptionByKey is only safe
	-- here — buttonLayerReady, outside the OnOptionChanged walk.
	local function ensureList(key, title, description, items, ids, defaultIndex)
		-- Everything below runs inside pcall, so a nil items table would come back
		-- as a plain "false" and the row would stay a text field with nothing in
		-- any log to say why. Fail loudly enough to be greppable instead.
		if type(items) ~= "table" or #items == 0 or type(ids) ~= "table" then
			Note("\nButton layout: dropdown \"" .. tostring(key)
				.. "\" has no items; left as-is.\n")
			return false
		end
		local alreadyList = false
		local okIs, isList = pcall(function() return settings:isListOption(key) end)
		if okIs and isList == true then
			alreadyList = true
		end
		if alreadyList then
			return false
		end
		-- Carry the old free-text value across rather than snapping everyone back
		-- to the default: a player who had typed "large" keeps Large.
		local startIndex = defaultIndex
		local okOld, oldValue = pcall(function() return settings:getOptionValue(key) end)
		if okOld and oldValue ~= nil then
			local mapped = listIndexForId(idFromListValue(oldValue, ids), ids)
			if mapped ~= nil then
				startIndex = mapped
			end
		end
		-- Retire the StringOption (a no-op on a profile that never had one).
		pcall(function() settings:removeOptionByKey(key) end)
		local okAdd = pcall(function()
			local ListOption = luajava.bindClass(
				"com.resurrection.blowtorch2.lib.service.plugin.settings.ListOption")
			local opt = luajava.new(ListOption)
			opt:setKey(key)
			opt:setTitle(title)
			opt:setDescription(description)
			for _, item in ipairs(items) do
				opt:addItem(item)
			end
			opt:setValue(startIndex)
			settings:addOption(opt)
		end)
		return okAdd
	end

	if ensureList("layout_size_preset", "Button size",
		"Size of the tiles in the current button set. Changing this resizes the set on screen and becomes the wizard's default.",
		LAYOUT_SIZE_ITEMS, LAYOUT_SIZE_IDS, 1) then
		added = true
	end
	if ensureList("layout_pack", "Layout template",
		"Which template the wizard offers first. Changing it here does not install anything — pick names and press Apply in the wizard.",
		LAYOUT_PACK_ITEMS, LAYOUT_PACK_IDS, 0) then
		added = true
	end

	-- Overflow "Button layout…" was removed; inject the Options callback so
	-- upgraded profiles still have a GUI entry (XML only seeds new profiles).
	if missing("layout_wizard_open") then
		local okAdd = pcall(function()
			local CallbackOption = luajava.bindClass(
				"com.resurrection.blowtorch2.lib.service.plugin.settings.CallbackOption")
			local opt = luajava.new(CallbackOption)
			opt:setKey("layout_wizard_open")
			opt:setTitle("Load button set from wizard")
			opt:setDescription(
				"Pick templates and set names, size, alignment, and colors. Does not remove other button sets.")
			opt:setValue("showLayoutWizardCmd")
			settings:addOption(opt)
		end)
		if okAdd then added = true end
	end

	-- Retired from Options → Button: the editor is overflow long-press / Edit
	-- buttons. Old profiles still carry the BooleanOption row. Strip it here
	-- (buttonLayerReady), never from OnOptionChanged.
	if not missing("auto_launch") then
		pcall(function() settings:removeOptionByKey("auto_launch") end)
		added = true
	end
	return added
end

function persistLayoutOption(key, value, asBoolean)
	-- Do NOT call ensureLayoutSettingsOptions here. pushOptionsToLua walks the
	-- SettingsGroup while firing OnOptionChanged; ensure's addOption during
	-- that walk caused ConcurrentModificationException and crashed :stellar.
	-- Injection belongs in buttonLayerReady (after plugins are loaded).
	pcall(function()
		if GetPluginSettings == nil then
			return
		end
		local settings = GetPluginSettings()
		if settings == nil then
			return
		end
		if asBoolean then
			local on = (value == true or value == "true" or value == "1")
			settings:updateBoolean(key, on)
		else
			settings:updateString(key, value ~= nil and tostring(value) or "")
		end
	end)
end

-- Write a boolean the player flipped in the button editor back to the settings
-- store, so it survives a restart the way the Options dialog's own checkboxes do.
--
-- Two things this must not do, both of which cost real money on a phone:
--   * loop. SettingsGroup.updateBoolean notifies the listener, which is
--     Plugin.updateSetting -> OnOptionChanged -> this same setter. Writing only
--     when the stored value really differs stops that after one round trip.
--   * save on the connect-time replay. pushOptionsToLua walks every stored
--     option and calls the setters with values that are already stored; the
--     same difference check makes those a no-op, and SaveSettings is a
--     synchronous write of the whole settings wad.
--
-- A key the profile does not have (an option added by a newer build, before
-- ensureLayoutSettingsOptions has injected it) simply is not written: getOptionValue
-- returns nil and updateBoolean would silently do nothing anyway.
function persistToggleOption(key, on)
	local stored = nil
	pcall(function()
		if GetPluginSettings == nil then
			return
		end
		local settings = GetPluginSettings()
		if settings ~= nil then
			stored = settings:getOptionValue(key)
		end
	end)
	if stored == nil then
		return
	end
	if (tostring(stored) == "true") == on then
		return
	end
	persistLayoutOption(key, on, true)
	if SaveSettings ~= nil then
		pcall(SaveSettings)
	end
end

-- layout_pack / layout_size_preset are ListOptions now: they hold an index, so
-- updateString would write "comfortable" into a field the dialog reads as a
-- number. Falls back to the string write for a profile not yet migrated.
function persistLayoutList(key, id, ids)
	pcall(function()
		if GetPluginSettings == nil then
			return
		end
		local settings = GetPluginSettings()
		if settings == nil then
			return
		end
		local index = listIndexForId(id, ids)
		local isList = false
		local okIs, res = pcall(function() return settings:isListOption(key) end)
		if okIs and res == true then isList = true end
		if isList and index ~= nil then
			settings:updateInteger(key, index)
		else
			settings:updateString(key, id ~= nil and tostring(id) or "")
		end
	end)
end

function setLayoutWizardPending(value)
	-- OnOptionChanged-only: SettingsGroup already holds the value (Options dialog
	-- or persistLayoutOption). Writing back via updateBoolean re-enters
	-- Plugin.updateSetting → OnOptionChanged and overflows the Lua stack.
	local on = (value == true or value == "true" or value == "1")
	local next = on and "true" or "false"
	if options.layout_wizard_pending == next then
		return
	end
	options.layout_wizard_pending = next
	if UserPresent() then
		loadOptions()
	end
end

function setLayoutPack(value)
	local next = idFromListValue(value, LAYOUT_PACK_IDS)
	if options.layout_pack == next then
		return
	end
	options.layout_pack = next
	if UserPresent() then
		loadOptions()
	end
end

-- True once the connect-time push of every stored option has been replayed.
-- Without it, the size dropdown would "change" from "" to the saved value on
-- every connect and resize (and re-save) the set each time.
layoutOptionsPrimed = false

function setLayoutSizePreset(value)
	local next = idFromListValue(value, LAYOUT_SIZE_IDS)
	if options.layout_size_preset == next then
		return
	end
	local wasPrimed = layoutOptionsPrimed
	options.layout_size_preset = next
	if UserPresent() then
		loadOptions()
	end
	-- Picking a size in Options resizes the set on screen. The wizard's own
	-- Apply does not come through here: doInstallBatch sets options first, so
	-- the echo from persistLayoutOption matches and returns above.
	if wasPrimed and next ~= "" and not isOfflineLayoutSession() then
		WindowXCallS(buttonWindowName, "applyLayoutSizePresetCmd", next)
	end
end

-- UI calls this after the first-run offer is dismissed without installing.
function clearLayoutWizardPending(args)
	options.layout_wizard_pending = "false"
	persistLayoutOption("layout_wizard_pending", false, true)
	pcall(loadOptions)
	if SaveSettings ~= nil then
		pcall(SaveSettings)
	end
end

-- First-run soft prompt: refuse offline so the starter pad stays owned by
-- installStarterButtonLayout. Window Lua may not have connection_host.
-- Soft prompt (Open wizard / Not now) lives in the window; Options callback
-- and .layoutwizard go straight to showLayoutWizard.
function offerLayoutWizardIfPending(args)
	-- The window latches before this round trip so three near-simultaneous
	-- triggers (loadButtons / loadOptions / OnSizeChanged) cannot queue three
	-- dialogs. When the answer is "not now, but maybe later" — the pad is still
	-- being set up, or the session was offline when the first trigger fired —
	-- hand the latch back, or a brand new MUD silently never gets the offer.
	if isOfflineLayoutSession() then
		WindowXCallS(buttonWindowName, "releaseLayoutWizardOffer", "")
		return
	end
	local pending = options.layout_wizard_pending
	if not (pending == true or pending == "true" or pending == "1") then
		-- Genuinely finished or skipped: leave the latch set.
		return
	end
	WindowXCallS(buttonWindowName, "showLayoutWizardOffer", "")
end

optionsTable = {}
optionsTable.haptic_edit = setHapticFeedbackEditor
optionsTable.haptic_press = setHapticFeedbackPressed
optionsTable.haptic_flip = setHapticFeedbackFlipped
optionsTable.roundess = setRoundness
-- auto_launch is stripped from existing profiles in ensureLayoutSettingsOptions.
-- auto_create stays in the group if a profile already has it. The setters stay
-- so a preference saved by an older build still lands somewhere.
optionsTable.auto_launch = setAutoLaunch
optionsTable.auto_create = setAutoCreate
optionsTable.show_gesture_hints = setShowGestureHints
optionsTable.show_swipe_preview = setShowSwipePreview
optionsTable.chrome_gestures = setChromeGestures
-- layout_wizard_pending is the only player-facing layout Options row (boolean).
-- layout_pack / layout_size_preset stay registered so SettingsGroup can push
-- wizard memory into Lua; they are not in default_settings XML as typed fields.
optionsTable.layout_wizard_pending = setLayoutWizardPending
optionsTable.layout_pack = setLayoutPack
optionsTable.layout_size_preset = setLayoutSizePreset

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
options.show_swipe_preview = true
options.chrome_gestures = ""
-- false here: old profiles without the XML key must not get a first-run offer.
-- New profiles get true from default_settings (parent edits XML separately).
options.layout_wizard_pending = false
options.layout_pack = ""
options.layout_size_preset = ""

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

-- A second, earlier `importButtons` stood here: a stub that shadowed its own
-- parameter and discarded the result. Lua takes the last definition, so the
-- live one is further down. Removed 2 Aug 2026.

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
 local wad = loadSerialized(data, "the imported button data")
 if(wad == nil) then
   WindowXCallS(buttonWindowName,"failImport","The imported button data could not be read.")
   return
 end
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

