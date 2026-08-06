 respath = package.path
respath = string.sub(respath,0,string.find(respath,"?")-1).."res"

require("button")
require("serialize")
require("bit")
local marshal = require("marshal")
defaults = nil
-- When true, loadButtons skips notifyFloatingButtonsChanged so a following
-- enterManagerMode can hide floaters without a one-frame flash.
local suppressFloatingNotify = false

local props = require("config")

debugInfo = false

local function debugString(string)
	if(debugInfo) then
		Note(string.format("\n%s\n",string))
	end
end

-- A bare `loadstring(data)()` is `nil()` when the string does not compile, and
-- these strings arrive over the binder from :stellar. Guard, say which payload
-- failed, and leave the previous value alone. Same shape as applyFloatPosition.
local function loadSerialized(data, what)
	if(type(data) ~= "string" or data == "") then
		Note(string.format("\nbutton window: %s is empty; skipped.\n", what))
		return nil
	end
	local chunk = loadstring(data)
	if(chunk == nil) then
		Note(string.format("\nbutton window: %s could not be parsed; skipped.\n", what))
		return nil
	end
	local ok, value = pcall(chunk)
	if(not ok or type(value) ~= "table") then
		Note(string.format("\nbutton window: %s is not a table; skipped.\n", what))
		return nil
	end
	return value
end

debugString("Button Window Script Loading...")

density = GetDisplayDensity()
Configuration = luajava.bindClass("android.content.res.Configuration")

R_id = luajava.bindClass("com.resurrection.blowtorch2.lib.R$id")
R_layout = luajava.bindClass("com.resurrection.blowtorch2.lib.R$layout")
R_drawable = luajava.bindClass("com.resurrection.blowtorch2.lib.R$drawable")
android_R_id = luajava.bindClass("android.R$id")
ViewGroup = luajava.bindClass("android.view.ViewGroup")
View = luajava.bindClass("android.view.View")
LinearLayoutParams = luajava.bindClass("android.widget.LinearLayout$LayoutParams")
KeyEvent = luajava.bindClass("android.view.KeyEvent")
TranslateAnimation = luajava.bindClass("android.view.animation.TranslateAnimation")
ScrollView = luajava.bindClass("android.widget.ScrollView")
AnimationSet = luajava.bindClass("android.view.animation.AnimationSet")
LayoutAnimationController = luajava.bindClass("android.view.animation.LayoutAnimationController")
HapticFeedbackConstants = luajava.bindClass("android.view.HapticFeedbackConstants")
Validator = luajava.newInstance("com.resurrection.blowtorch2.lib.validator.Validator")
MenuItem = luajava.bindClass("android.view.MenuItem")
DialogInterface = luajava.bindClass("android.content.DialogInterface")
Context = luajava.bindClass("android.content.Context")
Validator_Number = Validator.VALIDATE_NUMBER
Validator_Not_Blank = Validator.VALIDATE_NOT_BLANK
Validator_Number_Not_Blank = bit.bor(Validator_Number,Validator_Not_Blank)
Validator_Number_Or_Blank = Validator.VALIDATE_NUMBER_OR_BLANK
lastLoadedSet = nil

Configuration = luajava.bindClass("android.content.res.Configuration");
InputType = luajava.bindClass("android.text.InputType")
TYPE_TEXT_FLAG_MULTI_LINE = InputType.TYPE_TEXT_FLAG_MULTI_LINE
TYPE_CLASS_NUMBER = InputType.TYPE_CLASS_NUMBER
ORIENTATION_LANDSCAPE = Configuration.ORIENTATION_LANDSCAPE
ORIENTATION_PORTRAIT = Configuration.ORIENTATION_PORTRAIT

DisplayMetrics = view:getContext():getResources():getDisplayMetrics()

suppress_editor = false

function loadButtons(args)

	debugString("Button Window loading buttons...")
	package.loaded["button"] = nil
	require("button")

	-- marshal is a native library and args comes across the binder, where the
	-- parcel budget can truncate. Do not assume decode returns a table: an
	-- unchecked tmp.name here is a red error and no buttons at all.
	local ok, tmp = pcall(marshal.decode, args)
	if(not ok or type(tmp) ~= "table" or type(tmp.set) ~= "table") then
		Note("\nbutton window: the button set could not be decoded; buttons left unchanged.\n")
		-- false, not nil: loadAndEditSet must not open the editor on the set
		-- that is still loaded, because saving from there would write it over
		-- the set the player actually asked for.
		return false
	end
	lastLoadedSet = tmp.name
	debugString("Button Window decompressed data, set name: "..tostring(lastLoadedSet))

	defaults = BUTTONSET_DATA:new(tmp.default)

	BUTTON_DATA.__index = defaults
	buttons = {}
	local set = tmp.set
	local strippedAccordions = 0
	for i=1,#set do
		buttons[i] = BUTTON:new(set[i],density)
		-- A profile written before this rule, or imported from someone else,
		-- can carry both. Fix it on the way in rather than drawing a button
		-- that cannot do what its own settings say.
		if enforceNoAccordionOnSuperButton(buttons[i]) then
			strippedAccordions = strippedAccordions + 1
		end
	end
	if strippedAccordions > 0 then
		Note("BlowTorch: " .. strippedAccordions
			.. " super button(s) had an accordion, which cannot work from the"
			.. " floating layer. The accordion was removed; the buttons still"
			.. " float. Untick 'Super button' to use an accordion instead.")
	end
	clampAllButtons()
	drawButtons()
	view:invalidate()
	if not suppressFloatingNotify then
		notifyFloatingButtonsChanged()
	end

	debugString(string.format("Button Window loaded button set, %s successfully",lastLoadedSet))
	maybeOfferLayoutWizard()
	return true
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

Bitmap = luajava.bindClass("android.graphics.Bitmap")
BitmapConfig = luajava.bindClass("android.graphics.Bitmap$Config")
PorterDuffMode = luajava.bindClass("android.graphics.PorterDuff$Mode")
PaintClass = luajava.bindClass("android.graphics.Paint")
Array = luajava.bindClass("java.lang.reflect.Array")

selectedLayer = nil
selectedCanvas = nil
anySelected = false

moveTouch = {}
touchMoving = false
moveBounds = luajava.newInstance("android.graphics.RectF")
moveBitmap = nil
moveCanvas = nil
moveStart = {}
moveStart.x = 0
moveStart.y = 0
moveDelta = {}
moveDelta.x = 0
moveDelta.y = 0
moveCurrent = {}
moveCurrent.x = 0
moveCurrent.y = 0
moveCapOnce = true
totalDelta = {}
totalDelta.x = 0
totalDelta.y = 0
function moveTouch.onTouch(v,e)
	local x = e:getX()
	local y = e:getY()
	
	if(e:getAction() == MotionEvent.ACTION_DOWN) then
		if(moveBounds:contains(x,y)) then
			touchMoving = true
			
			moveCurrent.x = x
			moveCurrent.y = y
			if(moveCapOnce) then
				moveStart.x = x
				moveStart.y = y
				moveCapOnce = false
			end
			return true
		else 
			touchMoving = false
		end
	end
	
	if(e:getAction() == MotionEvent.ACTION_MOVE) then
		if(touchMoving) then
			moveDelta.x = x - moveCurrent.x
			moveDelta.y = y - moveCurrent.y
			moveCurrent.x = x
			moveCurrent.y = y
			if(gridsnap) then
				local tmpx = x % (gridXwidth/2)
				local tmpy = y % (gridYwidth/2)
				local gridx = math.floor(x/(gridXwidth/2))
				local gridy = math.floor(y/(gridYwidth/2))
				local wtmp = (moveBounds.right - moveBounds.left)/2
				local htmp = (moveBounds.bottom - moveBounds.top)/2
				totalDelta.x = totalDelta.x - (moveBounds.left - (gridx*(gridXwidth/2)-wtmp))
				totalDelta.y = totalDelta.y - (moveBounds.top - (gridy*(gridYwidth/2)-htmp))
				moveBounds:offsetTo(gridx*(gridXwidth/2)-wtmp,gridy*(gridYwidth/2)-htmp)
			else
				totalDelta.x = totalDelta.x + moveDelta.x
				totalDelta.y = totalDelta.y + moveDelta.y
				moveBounds:offset(moveDelta.x,moveDelta.y)
			end
			view:invalidate()
			return true
		else
			return true
		end
	end
	
	if(e:getAction() == MotionEvent.ACTION_UP) then
		if(touchMoving == false) then
			exitMoveMode()
			return true
		else
			touchmoving = false
			return true
		end
	end

	if(e:getAction() == MotionEvent.ACTION_CANCEL) then
		touchMoving = false
		-- Global export (local cancelActiveTouchGesture is not in scope here).
		if cancelTouchGesture ~= nil then
			cancelTouchGesture()
		end
		return true
	end
	
	return true
end
moveTouch_cb = luajava.createProxy("android.view.View$OnTouchListener",moveTouch)

function enterMoveMode()
	--create a bounding box
	local x1 = 0
	local y1 = 0
	local x2 = 0
	local y2 = 0
	local first = true
	for i,b in pairs(buttons) do
		if(b.selected == true) then
			local r = b.rect
			if(first) then
				x1 = r.left
				y1 = r.top
				x2 = r.right
				y2 = r.bottom
				first = false
			else
				if(r.left < x1) then
					x1 = r.left
				end
				if(r.top < y1) then
					y1 = r.top
				end
				if(r.right > x2) then
					x2 = r.right
				end
				if(r.bottom > y2) then
					y2 = r.bottom
				end
				
			end
		end
	end
	moveBounds:set(x1,y1,x2,y2)
	
	moveBounds:inset(-40,-40)
	
	local width = moveBounds.right - moveBounds.left
	local height = moveBounds.bottom - moveBounds.top
	moveBitmap = Bitmap:createBitmap(width,height,BitmapConfig.ARGB_8888)
	moveCanvas = luajava.newInstance("android.graphics.Canvas",moveBitmap)
	moveCanvas:drawARGB(0x88,0x88,0x88,0x88)
	view:setOnTouchListener(moveTouch_cb)
	moveCanvas:save()
	moveCanvas:translate(40,40)
	moveCanvas:translate(-1*x1,-1*y1)
	for i,b in pairs(buttons) do
		if(b.selected == true) then
			b:draw(1,moveCanvas)
		end
	end
	drawButtonsNoSelected()
	moveCanvas:restore()
	view:invalidate()
end

function exitMoveMode()
	-- Before anything is written: the drag only accumulated totalDelta, so the
	-- buttons still hold the positions they had when the finger went down.
	pushUndo()
	moveCanvas = nil
	moveBitmap = nil
	local dx = totalDelta.x
	local dy = totalDelta.y
			
	totalDelta.x = 0
	totalDelta.y = 0
	for i,b in pairs(buttons) do
		if(b.selected == true) then
			local r = b.rect
			local beforeX, beforeY = posX(b.data), posY(b.data)
			local movedX, movedY = clampLogicalPosition(beforeX + dx, beforeY + dy, b)
			setPos(b, movedX, movedY)
			shiftFloatPlacement(b, movedX - beforeX, movedY - beforeY)
			b.selected = false
			updateSelected(b,false)
			refreshRect(b)
		end
	end
	drawButtons()
	btprofReportHints("enterManagerMode")
	view:setOnTouchListener(managerTouch_cb)
	
	view:invalidate()
end

touchStartX = 0
touchStartY = 0
managerTouch = {}
gridsnap = true
function managerTouch.onTouch(v,e)
	local x = e:getX()
	local y = e:getY()
	if(e:getAction() == MotionEvent.ACTION_DOWN) then
		--find if press was in a button
		ret,b,index = buttonTouched(x,y)
		if(ret) then
			touchStartX = x
			touchStartY = y
			fingerdown = true
			touchedbutton = b
			touchedindex = index
			b.selected = true
			updateSelected(b,true)
			view:invalidate()
			if(b.selected) then
				selectedtouchstart = true
			else
				selectedtouchstart = false
			end
			return true
		else
			--we are draggin now
			buttoncleared = false
			fingerdown = false
			for i,b in ipairs(buttons) do
				if b.selected then
					b.selected = false
					updateSelected(b,false)
					buttoncleared = true
				end
			end
			if buttoncleared then 
				view:invalidate() 
				
			end
			if(manage) then
				dragstart.x = x
				dragstart.y = y
				return true
			end
		end
	end

	if(e:getAction() == MotionEvent.ACTION_MOVE) then
	
		if(prevevent == 0) then
			prevevent = e:getEventTime()
		else
			now = e:getEventTime()
			local elapsed = now - prevevent
			if(elapsed > 10) then
				prevevent = now
			else
				return true --consume but dont process.
			end
		end


		if(not fingerdown and manage) then
			--we are drag moving now.
			dragcurrent.x = x
			dragcurrent.y = y
			--compute distance
			distance = math.sqrt(math.pow(dragcurrent.x-dragstart.x,2)+math.pow(dragcurrent.y-dragstart.y,2))
			if(distance < 10*density) then
				return true
			end
			dragmoving = true
			
			checkIntersects()
			view:invalidate()
			return true
		
		 end
		 
		 if(fingerdown and selectedtouchstart) then
		 	local diffx = math.abs(x - touchStartX)
		 	local diffy = math.abs(y - touchStartY)
		 	
		 	local dist = math.sqrt(diffx*diffx + diffy*diffy)
		 	if(dist < 10*density) then
		 		return true
		 	end
		 
		 	touchMoving = true
		 	moveCurrent.x = x
			moveCurrent.y = y
			if(moveCapOnce) then
				moveStart.x = x
				moveStart.y = y
				moveCapOnce = false
			end
			fingerdown = false
			selectedtouchstart = false
		 	enterMoveMode()
		 	return true
		 end
	end

	if(e:getAction() == MotionEvent.ACTION_UP) then
		if(dragmoving) then
			dragmoving = false
			view:invalidate()
			return true
		end
		if(manage and not fingerdown and not buttoncleared) then
			local modx = (math.floor(x/gridXwidth)*gridXwidth)+(gridXwidth/2)
			local mody = (math.floor(y/gridYwidth)*gridYwidth)+(gridYwidth/2)
			-- Held long enough, with something copied? Paste instead of
			-- creating. Taken from the event rather than a stopwatch field, so
			-- there is no state to get out of step, and a short tap reaches the
			-- create path exactly as before.
			local held = e:getEventTime() - e:getDownTime()
			if held >= BUTTON_PASTE_LONG_PRESS_MS and hasButtonsToPaste() then
				pasteButtons(modx,mody-statusoffset)
				return true
			end
			pushUndo()
			local butt = addButton(modx,mody-statusoffset)
			butt:draw(0,buttonCanvas)
			view:invalidate()
			return true
		end
		if(manage and fingerdown and touchedbutton.selected == true and selectedtouchstart) then
			showEditorSelection()
			touchedbutton={}
			fingerdown = false
			return true
		elseif(manage and fingerdown and touchedbutton.selected == false) then
			for i,b in ipairs(buttons) do
				if(b.selected) then
					b.selected = false
					updateSelected(b,false)
				end
			end
			touchedbutton.selected = true
			updateSelected(touchedbutton,true)
			view:invalidate()
			fingerdown = false
			touchedbutton = {}
			
		end

		touchedbutton = {}
		fingerdown = false
		return true
	end

	if(e:getAction() == MotionEvent.ACTION_CANCEL) then
		-- Gesture stolen (e.g. edge-back); do not open editor or create buttons.
		dragmoving = false
		fingerdown = false
		selectedtouchstart = false
		if touchedbutton ~= nil then
			touchedbutton.selected = false
		end
		touchedbutton = {}
		if cancelTouchGesture ~= nil then
			cancelTouchGesture()
		end
		view:invalidate()
		return true
	end

	return false
end

managerTouch_cb = luajava.createProxy("android.view.View$OnTouchListener",managerTouch)

HOLD_CALLBACK_ID = 101
EDITOR_CALLBACK_ID = 100
ACCORDION_HOLD_CALLBACK_ID = 102
HOLD_DELAY_MS = 450
SWIPE_THRESHOLD_DP = 24
HOLD_CANCEL_MOVE_DP = 10
shortHoldFired = false
accordionHoldFired = false
accordionWasExpandedAtDown = false
-- Direction currently shown by the live swipe arrow, nil when nothing is shown.
-- Kept so the layer is only repainted when the direction actually changes.
swipePreviewDir = nil
-- Text currently shown in the gesture callout, nil when none is shown. Same
-- reason: repaint only when the wording changes, not on every move event.
gestureLabelText = nil

local function hasButtonCommand(cmd)
	return cmd ~= nil and cmd ~= ""
end

function classifySwipe(dx, dy, threshold)
	if math.abs(dx) < threshold and math.abs(dy) < threshold then
		return nil
	end
	if math.abs(dx) >= math.abs(dy) then
		if dx > 0 then
			return "right"
		end
		return "left"
	end
	if dy > 0 then
		return "down"
	end
	return "up"
end

-- Sector order starting at "right" and going counter-clockwise, 45 degrees each.
local SWIPE8_SECTORS = {
	"right", "upright", "up", "upleft", "left", "downleft", "down", "downright",
}

-- Eight-way classifier, deliberately a separate function rather than a rewrite of
-- classifySwipe.
--
-- classifySwipe must keep returning only up/down/left/right: the accordion swipe
-- trigger compares its result against accordionDirection, which is never diagonal.
-- Diagonals are resolved on top of the 4-way answer in the touch handler, so a
-- button with no diagonal commands behaves exactly as it did before.
--
-- Same threshold and same dead zone as classifySwipe, so both agree on whether a
-- gesture counts as a swipe at all.
function classifySwipe8(dx, dy, threshold)
	if math.abs(dx) < threshold and math.abs(dy) < threshold then
		return nil
	end
	-- Screen y grows downward; negate it so the angle reads like ordinary maths.
	local angle = math.deg(math.atan2(-dy, dx))
	if angle < 0 then
		angle = angle + 360
	end
	-- The +22.5 offset puts straight up/down/left/right in the middle of a sector
	-- instead of on a boundary between two.
	local sector = math.floor((angle + 22.5) / 45) % 8
	return SWIPE8_SECTORS[sector + 1]
end

local function getSwipeCommand(data, direction)
	if direction == "up" then
		return data.swipeUpCommand
	elseif direction == "down" then
		return data.swipeDownCommand
	elseif direction == "left" then
		return data.swipeLeftCommand
	elseif direction == "right" then
		return data.swipeRightCommand
	elseif direction == "upleft" then
		return data.swipeUpLeftCommand
	elseif direction == "upright" then
		return data.swipeUpRightCommand
	elseif direction == "downleft" then
		return data.swipeDownLeftCommand
	elseif direction == "downright" then
		return data.swipeDownRightCommand
	end
	return nil
end

-- Which swipe direction this movement will actually fire, or nil for none.
--
-- Prefer the diagonal the finger drew; fall back to the four-way direction when
-- the button has nothing bound there. Both the live preview arrow and the
-- dispatch on finger-up go through here, so the arrow can never point somewhere
-- other than where the release will send you.
-- Arrow glyph per direction, so the callout says which gesture it is describing.
local DIRECTION_GLYPHS = {
	up = "↑", down = "↓", left = "←", right = "→",
	upleft = "↖", upright = "↗", downleft = "↙", downright = "↘",
}

-- What the gesture callout should say right now, or nil for nothing.
--
-- While aiming a swipe it names that swipe's command. When the finger has slid
-- off the tile it names the flip command that would fire on release, unless a
-- swipe in that direction would win instead. Before any swipe starts, and while
-- still on the tile, it names the hold command.
local function gestureLabelFor(data, direction, outsideButton)
	if data == nil then
		return nil
	end
	if direction ~= nil then
		local cmd = getSwipeCommand(data, direction)
		if hasButtonCommand(cmd) then
			local glyph = DIRECTION_GLYPHS[direction] or ""
			return glyph .. "  " .. cmd
		end
	end
	if outsideButton and hasButtonCommand(data.flipCommand) then
		return data.flipCommand
	end
	if direction == nil and not outsideButton then
		if hasButtonCommand(data.holdCommand) then
			return "hold  " .. data.holdCommand
		end
	end
	return nil
end

local function resolveSwipeDirection(data, dx, dy, threshold)
	if classifySwipe(dx, dy, threshold) == nil then
		return nil
	end
	local diagonal = classifySwipe8(dx, dy, threshold)
	if hasButtonCommand(getSwipeCommand(data, diagonal)) then
		return diagonal
	end
	local straight = classifySwipe(dx, dy, threshold)
	if hasButtonCommand(getSwipeCommand(data, straight)) then
		return straight
	end
	return nil
end

local function hasButtonSwitch(data)
	return data ~= nil and data.switchTo ~= nil and data.switchTo ~= ""
end

local function hasAccordionConfig(data)
	if data == nil or data.accordionDirection == nil or data.accordionDirection == "" then
		return false
	end
	return data.accordionChildren ~= nil and #data.accordionChildren > 0
end

local function getAccordionTrigger(data)
	local trigger = data.accordionTrigger
	if trigger == nil or trigger == "" then
		return "tap"
	end
	return trigger
end

local function getAccordionHoldMs(data)
	local ms = tonumber(data.accordionHoldMs)
	if ms == nil or ms < 100 then
		return HOLD_DELAY_MS
	end
	if ms > 3000 then
		return 3000
	end
	return math.floor(ms)
end

local function toggleAccordion(button)
	if button.expanded then
		collapseAccordion(button)
	else
		expandAccordion(button)
	end
end

local function dispatchButtonAction(cmd)
	if buttonsCleared then
		revertButtons()
		return true
	end
	mainwindow:jumpToStart()
	if hasButtonSwitch(touchedbutton.data) then
		PluginXCallS("loadButtonSet", touchedbutton.data.switchTo)
		return true
	end
	if not hasButtonCommand(cmd) then
		return false
	end
	SendToServer(cmd)
	return true
end

local function resetTouchedButtonVisual()
	-- The swipe arrow and the gesture callout are drawn outside the tile, so
	-- taking them away needs the whole layer cleared. A plain tap dirties only
	-- the one tile and can repaint just that.
	local hadOverlay = swipePreviewDir ~= nil or gestureLabelText ~= nil
	normalTouchState = 0
	swipePreviewDir = nil
	gestureLabelText = nil
	if touchedbutton ~= nil then
		touchedbutton.selected = false
	end
	-- "No button touched" is spelled {} here, not nil, so a nil check passes an
	-- empty table straight through -- which is how the fast path below reached
	-- clearButton with no paintOpts and killed a cancelled gesture. Test for the
	-- fields a real BUTTON has instead.
	local realButton = touchedbutton ~= nil
			and touchedbutton.paintOpts ~= nil
			and touchedbutton.rect ~= nil
	-- drawButtons() clears the layer, which also takes away the live swipe arrow.
	-- It is also a full repaint of every tile, measured at ~40ms on a 93 button
	-- set, and it ran on every single button press.
	if hadOverlay or manage or not realButton
			or touchedbutton.expanded or touchedbutton.isAccordionChild then
		drawButtons()
	else
		clearButton(touchedbutton)
		touchedbutton:draw(0, buttonCanvas)
	end
	view:invalidate()
end

function doShortHold()
	if suppress_editor or not fingerdown or shortHoldFired then
		return
	end
	if touchedbutton ~= nil and hasButtonCommand(touchedbutton.data.holdCommand) then
		shortHoldFired = true
		dispatchButtonAction(touchedbutton.data.holdCommand)
	end
end

function doAccordionHold()
	if suppress_editor or not fingerdown or accordionHoldFired then
		return
	end
	if touchedbutton == nil or touchedbutton.isAccordionChild then
		return
	end
	if not hasAccordionConfig(touchedbutton.data) or getAccordionTrigger(touchedbutton.data) ~= "hold" then
		return
	end
	accordionHoldFired = true
	performHapticPress()
	toggleAccordion(touchedbutton)
end

multiTouchCancelled = false

local function cancelActiveTouchGesture()
	-- Abort paths (ACTION_CANCEL, multi-touch, system back): clear pressed/highlight without click.
	CancelCallback(EDITOR_CALLBACK_ID)
	CancelCallback(HOLD_CALLBACK_ID)
	CancelCallback(ACCORDION_HOLD_CALLBACK_ID)
	shortHoldFired = false
	accordionHoldFired = false
	-- Always clear visuals — fingerdown may already be false when the system steals the gesture.
	resetTouchedButtonVisual()
	fingerdown = false
	selectedtouchstart = false
	multiTouchCancelled = false
end

-- Exported for MainWindow (edge-back / onPause) via windowCall.
function cancelTouchGesture()
	cancelActiveTouchGesture()
end

normalTouch = {}
normalTouchState = 0
function normalTouch.onTouch(v,e)
	local retvalue = false
	local x = e:getX()
	local y = e:getY()
	local masked = e:getActionMasked()
	if masked == ACTION_POINTER_DOWN then
		if fingerdown then
			multiTouchCancelled = true
			cancelActiveTouchGesture()
			return true
		end
	end
	if masked == ACTION_DOWN then
		multiTouchCancelled = false
		prevevent = 0
		shortHoldFired = false
		accordionWasExpandedAtDown = false
		accordionHoldFired = false
		ret,b,index = buttonTouched(x,y)
		if(ret) then
			local needsFullRedraw = false
			if hasAccordionConfig(b.data) and not b.isAccordionChild then
				accordionWasExpandedAtDown = b.expanded
				if getAccordionTrigger(b.data) == "tap" and not b.expanded then
					expandAccordion(b, true)
					needsFullRedraw = true
				end
			end
			if not b.isAccordionChild then
				-- Editor is opened only via long-press on the wrench icon.
				local skipHoldForTapExpand = hasAccordionConfig(b.data)
					and getAccordionTrigger(b.data) == "tap"
					and not accordionWasExpandedAtDown
				if not skipHoldForTapExpand and hasButtonCommand(b.data.holdCommand) then
					ScheduleCallback(HOLD_CALLBACK_ID,"doShortHold",HOLD_DELAY_MS)
				end
				if hasAccordionConfig(b.data) and getAccordionTrigger(b.data) == "hold" then
					ScheduleCallback(ACCORDION_HOLD_CALLBACK_ID,"doAccordionHold", getAccordionHoldMs(b.data))
				end
			end
			fingerdown = true
			touchStartX = x
			touchStartY = y
			touchedbutton = b
			b.selected = true
			touchedindex = index
			normalTouchState = 1
			-- Show the hold command straight away, while there is still time to
			-- lift off before it fires.
			gestureLabelText = gestureLabelFor(b.data, nil, false)
			if needsFullRedraw or b.isAccordionChild or b.expanded or gestureLabelText ~= nil then
				drawButtons()
			end
			b:draw(normalTouchState,buttonCanvas)
			b:drawGestureLabel(buttonCanvas, gestureLabelText, width)
			performHapticPress()
			selectedtouchstart = true
			view:invalidate()
			return true
		else
			fingerdown = false;
			--debugPrint("action down, returning false")
			return false
		end			
	elseif(masked == ACTION_MOVE) then
		--debugPrint("move1")
		
		if(fingerdown == false) then
			--debugPrint("action move, no finger down, returning false")
			return false
		end
		--debugPrint("move2")
		
		if(prevevent == 0) then
			prevevent = e:getEventTime()
		else
			now = e:getEventTime()
			local elapsed = now - prevevent
			if(elapsed > 5) then
			--proceed
				--debugPrint("processing move event")
				prevevent = now
			else
				--debugPrint("action move, consuming, returning true")
			
				return true --consume but dont process.
			end
		end
	
		if(fingerdown) then
			local dx = x - touchStartX
			local dy = y - touchStartY
			local moveDist = math.sqrt(dx * dx + dy * dy)
			if moveDist > (HOLD_CANCEL_MOVE_DP * density) then
				CancelCallback(HOLD_CALLBACK_ID)
				CancelCallback(ACCORDION_HOLD_CALLBACK_ID)
			end
			local r = touchedbutton.rect
			local insideButton = r:contains(x,y)
			if not insideButton then
				CancelCallback(EDITOR_CALLBACK_ID)
			end
			local wantState = insideButton and 1 or 2
			local flipping = (wantState == 2 and normalTouchState ~= 2)

			-- Direction a release right now would fire, or nil for none.
			local previewDir = nil
			if buttonShowSwipePreview ~= false then
				previewDir = resolveSwipeDirection(touchedbutton.data, dx, dy,
						SWIPE_THRESHOLD_DP * density)
			end

			-- One repaint path for the pressed/flip state and the arrow together.
			-- Only repaint when something actually changed: the direction only
			-- changes when the finger crosses a sector edge, not on every move
			-- event. drawButtons() clears first so the old arrow goes with it,
			-- then the touched button is drawn on top exactly as before.
			local labelText = gestureLabelFor(touchedbutton.data, previewDir,
					wantState == 2)

			if wantState ~= normalTouchState or previewDir ~= swipePreviewDir
					or labelText ~= gestureLabelText then
				normalTouchState = wantState
				swipePreviewDir = previewDir
				gestureLabelText = labelText
				drawButtons()
				touchedbutton:draw(normalTouchState,buttonCanvas)
				if swipePreviewDir ~= nil then
					touchedbutton:drawSwipePreview(buttonCanvas, swipePreviewDir)
				end
				touchedbutton:drawGestureLabel(buttonCanvas, gestureLabelText, width)
				if flipping then
					performHapticFlip()
				end
				view:invalidate()
			end
			
			--debugPrint("action move, moving button, returning true")
			
			return true
		else
			--debugPrint("reached end of normal touch handler, returning false")
			return false
		end
	elseif(masked == ACTION_UP or masked == ACTION_POINTER_UP) then
		if multiTouchCancelled then
			if e:getPointerCount() <= 1 then
				multiTouchCancelled = false
			end
			return true
		end
		if(fingerdown) then
			CancelCallback(EDITOR_CALLBACK_ID)
			CancelCallback(HOLD_CALLBACK_ID)
			CancelCallback(ACCORDION_HOLD_CALLBACK_ID)
			fingerdown = false
			selectedtouchstart = false
			if accordionHoldFired then
				accordionHoldFired = false
				resetTouchedButtonVisual()
				return true
			end
			if shortHoldFired then
				shortHoldFired = false
				resetTouchedButtonVisual()
				return true
			end
			local r = touchedbutton.rect
			local dx = x - touchStartX
			local dy = y - touchStartY
			local swipeThreshold = SWIPE_THRESHOLD_DP * density
			local swipeDir = classifySwipe(dx, dy, swipeThreshold)
			local sent = false
			if swipeDir ~= nil then
				if hasAccordionConfig(touchedbutton.data)
					and getAccordionTrigger(touchedbutton.data) == "swipe"
					and swipeDir == touchedbutton.data.accordionDirection then
					-- Accordion still matches on the 4-way direction only.
					toggleAccordion(touchedbutton)
					sent = true
				else
					local fireDir = resolveSwipeDirection(touchedbutton.data, dx, dy, swipeThreshold)
					if fireDir ~= nil then
						sent = dispatchButtonAction(getSwipeCommand(touchedbutton.data, fireDir))
					end
				end
			end
			if not sent then
				if touchedbutton.isAccordionChild and touchedbutton.accordionParent ~= nil then
					sent = dispatchButtonAction(touchedbutton.data.command)
					if touchedbutton.accordionParent.data.accordionAutoClose ~= false then
						collapseAccordion(touchedbutton.accordionParent)
					end
				elseif isAccordionCloseHit(touchedbutton, x, y) then
					collapseAccordion(touchedbutton)
					sent = true
				elseif hasAccordionConfig(touchedbutton.data) and r:contains(x,y) and swipeDir == nil then
					if getAccordionTrigger(touchedbutton.data) == "tap" and accordionWasExpandedAtDown then
						collapseAccordion(touchedbutton)
					end
					sent = getAccordionTrigger(touchedbutton.data) == "tap"
				elseif(r:contains(x,y)) then
					sent = dispatchButtonAction(touchedbutton.data.command)
				else
					sent = dispatchButtonAction(touchedbutton.data.flipCommand)
				end
			end
			resetTouchedButtonVisual()
			return true
		else
			--debugPrint("button not touched, returning false")
			return false
		end
	elseif masked == ACTION_CANCEL then
		-- Edge-back / IME / parent intercept abort the gesture with CANCEL, not UP.
		-- Without this, buttons stay visually pressed and hold callbacks may still fire.
		multiTouchCancelled = false
		cancelActiveTouchGesture()
		return true
	end
	--debugPrint("reached end of normal touch handler, returning false")
	return false
end
normalTouch_cb = luajava.createProxy("android.view.View$OnTouchListener",normalTouch)
view:setOnTouchListener(normalTouch_cb)

function doEdit()
	-- Entered only from long-press on the wrench / overflow icon.
	if suppress_editor or manage then
		return
	end
	performHapticEdit()
	enterManagerMode()
	showeditormenu = true
	PushMenuStack("onEditorBackPressed")
end

function editorMenuDone()
	return buttonsetMenuDoneClicked.onMenuItemClick(nil)
end

function editorMenuCancel()
	return buttonsetCancelClicked.onMenuItemClick(nil)
end

function editorMenuSettings()
	return buttonsetSettingsClicked.onMenuItemClick(nil)
end
--this window is a full screen window, so we don't really need to concern ourselves with bounds and the such, but we do need to create a button class.
RectFClass = luajava.bindClass("android.graphics.RectF")
function updateSelected(b,sel)
	local p = b.paintOpts
	--clearButton(b)
	--local redrawScreen = false
	if(sel) then
		--p:setShadowLayer(1,0,0,Color.WHITE)
		b.selected = true
		b:draw(1,buttonCanvas)
	else
		--p:setShadowLayer(0,0,0,Color.WHITE)
		b.selected = false
		b:draw(0,buttonCanvas)
	end
	
	
	--invalidate()
end

dragRect = luajava.newInstance("android.graphics.RectF")
selectedBounds = luajava.newInstance("android.graphics.RectF")
function checkIntersects()
	--compute new drag rect
	local x1 = 0
	local y1 = 0
	local x2 = 0
	local y2 = 0
	
	if(dragstart.x < dragcurrent.x) then
		x1 = dragstart.x
		x2 = dragcurrent.x
	else 
		x1 = dragcurrent.x
		x2 = dragstart.x
	end
	
	if(dragstart.y < dragcurrent.y) then
		y1 = dragstart.y
		y2 = dragcurrent.y
	else 
		y1 = dragcurrent.y
		y2 = dragstart.y
	end
	
	dragRect:set(x1,y1,x2,y2)
	local redrawscreen = false
	anySelected = false
	for i,b in pairs(buttons) do
		local rect = b.rect
			if(intersectMode == 0) then
				if(RectFClass:intersects(dragRect,rect) or dragRect:contains(rect)) then
				if(b.selected == false) then
					updateSelected(b,true)
					--anySelected = true
				end
			else
				if(b.selected == true) then
					updateSelected(b,false)
					--redrawscreen = true
				end
			end
		end
		
		if(intersectMode == 1) then
			if(dragRect:contains(rect)) then
				if(b.selected == false) then
					updateSelected(b,true)
					--anySelected = true
				end
			else
				if(b.selected == true) then
					updateSelected(b,false)
					--redrawscreen = true
				end
			end	
		end
		
		
	end 
	
	--if(redrawscreen) then
	--	drawButtons()
	--end
end


buttons = {}

--BitmapFactory = luajava.bindClass("android.graphics.BitmapFactory")
--bmp = BitmapFactory:decodeFile("/mnt/sdcard/BlowTorch/testimage.png")

fingerdown = false
manage = false

paint = luajava.new(PaintClass)
paint:setAntiAlias(true)
bounds = nil

statusoffset = 0
statusHidden = false

function clampLogicalPosition(x, y, b)
	local w = view:getWidth()
	local h = view:getHeight()
	if w <= 0 or h <= 0 then
		return x, y
	end
	local halfW = (b.data.width / 2) * b.density
	local halfH = (b.data.height / 2) * b.density
	local minX = halfW
	local maxX = w - halfW
	local minY = halfH
	local maxY = h - statusoffset - halfH
	if minY > maxY then
		minY = halfH
		maxY = h - halfH
	end
	if x < minX then x = minX end
	if x > maxX then x = maxX end
	if y < minY then y = minY end
	if y > maxY then y = maxY end
	return x, y
end

-- Landscape may have a layout of its own. Until the player moves a button while
-- the phone is on its side, xLand/yLand are nil and landscape simply shows the
-- portrait layout -- which is the state every existing profile is in. The first
-- deliberate move in landscape writes the landscape pair, and from then on the
-- two orientations are independent. Nothing writes a pair because the phone was
-- turned: clamping for a narrower screen is a drawing concern (clampAllButtons
-- persists only when a layout action asked it to).
function isLandscapeNow()
	if view == nil then
		return false
	end
	local w, h = view:getWidth(), view:getHeight()
	if w <= 0 or h <= 0 then
		return false
	end
	return w > h
end

-- Position to draw a button at, in the orientation we are in now.
function posX(d)
	if d == nil then
		return 0
	end
	if isLandscapeNow() and tonumber(d.xLand) ~= nil then
		return d.xLand
	end
	return d.x
end

function posY(d)
	if d == nil then
		return 0
	end
	if isLandscapeNow() and tonumber(d.yLand) ~= nil then
		return d.yLand
	end
	return d.y
end

-- Write a position the player chose into the pair for this orientation.
function setPos(b, x, y)
	if b == nil or b.data == nil then
		return
	end
	if isLandscapeNow() then
		b.data.xLand = x
		b.data.yLand = y
	else
		b.data.x = x
		b.data.y = y
	end
end

-- Draw a button where this orientation says it belongs.
function refreshRect(b)
	if b ~= nil and b.data ~= nil then
		b:updateRectAt(posX(b.data), posY(b.data), statusoffset)
	end
end

-- forceRect: rebuild every rect even where the clamp changed nothing. Needed
-- when statusoffset may have moved under all of them (onSizeChanged), but not
-- on the load path, where BUTTON:new has just built each rect from the same
-- position and offset -- rebuilding all of them there was pure duplicate work.
-- persist: write the clamped position back into the button data. Only a
-- deliberate layout action passes true. A rotation must not: landscape is
-- narrower and shorter, so clamping there used to overwrite the stored
-- position and portrait came back rearranged.
function clampAllButtons(forceRect, persist)
	local movedCount = 0
	for i = 1, #buttons do
		local b = buttons[i]
		local ox, oy = posX(b.data), posY(b.data)
		local nx, ny = clampLogicalPosition(ox, oy, b)
		local moved = nx ~= ox or ny ~= oy
		if moved then
			movedCount = movedCount + 1
			if persist == true then
				-- setPos writes the pair for the orientation we are in, so in
				-- landscape this is where a button that has only ever had a
				-- portrait position gains a landscape one. That is the point:
				-- the player asked for it by opening the editor here.
				setPos(b, nx, ny)
				shiftFloatPlacement(b, nx - ox, ny - oy)
			end
		end
		if moved or forceRect then
			b:updateRectAt(nx, ny, statusoffset)
		end
	end
	return movedCount
end

function refreshStatusOffset(relayoutButtons)
	local hiddenNow = IsStatusBarHidden()
	-- Edge-to-edge: keep tap targets below status icons unless fullscreen hides the bar.
	-- Editor chrome sits above the input divider and must not shift button layout.
	statusoffset = tonumber(GetStatusBarHeight()) or 0
	if hiddenNow and statusoffset <= 0 then
		statusoffset = tonumber(GetActionBarHeight()) or 0
	end
	if relayoutButtons ~= false then
		for i = 1, #buttons do
			refreshRect(buttons[i])
		end
	end
	statusHidden = hiddenNow
end


function OnCreate()
	--Note("in oncreate, loading "..#buttons.." buttons.")	
	debugString("Button window in View.onCreate()")
	refreshStatusOffset(true)
	for i,b in ipairs(buttons) do
		updateRect(b)
	end
	paint:setARGB(0xAA,0x00,0x33,0xAA)
	--bounds = getBounds()
	--drawButtons()
	--addOptionCallback("buttonOptions","Lua Button Options",nil)
	AddOptionCallback("buttonList","Button Sets",nil)
	view:bringToFront()
	ScheduleCallback(9901, "delayedStatusRefresh", 300)
	
	--PluginXCallS("checkImport","blank")
end

function delayedStatusRefresh()
	refreshStatusOffset(true)
	if draw and buttonCanvas ~= nil and view:getWidth() > 0 and view:getHeight() > 0 then
		drawButtons()
	end
	view:invalidate()
end


managerLayer = nil
managerCanvas = nil


cpaint = luajava.new(PaintClass)
cpaint:setARGB(0x00,0x00,0x00,0x00)
cpaint:setXfermode(xferModeClear)

-- Opaque black wash while editing buttons so game text does not show through.
managerBgPaint = luajava.new(PaintClass)
managerBgPaint:setARGB(0xFF,0x00,0x00,0x00)

drawManagerLayer = true
function enterManagerMode()
	manage = true
	-- A fresh history per editing session. Undo is in memory only, so a stack
	-- kept from the last time the editor was open would be offering to restore
	-- a set that may not even be loaded any more.
	clearUndoHistory()
	-- Hide floaters immediately so they cannot sit over the edit grid during setup.
	notifyFloatingButtonsChanged()
	refreshStatusOffset(true)
	gridXwidth = defaults.gridXwidth*density
	gridYwidth = defaults.gridYwidth*density
	if(drawManagerLayer) then
		managerLayer = Bitmap:createBitmap(view:getWidth(),view:getHeight(),BitmapConfig.ARGB_8888)
		managerCanvas = luajava.newInstance("android.graphics.Canvas",managerLayer)
		--Note("drawingManagerLayer")
		drawManagerGrid()
	end

	--set up and add the back/options widget.
	--backWidget = makeBackWidget()
	--local parent = view:getParent()
	--parent:addView(backWidget)
	--touchedbutton = nil
		--paint:setShadowLayer(1,0,0,Color.WHITE)
	view:setOnTouchListener(managerTouch_cb)

	-- Opening the editor is a deliberate act in the orientation the phone is in,
	-- so this is the one place a clamp may be kept. A pad inherited from
	-- portrait does not fit a landscape screen: without this its lowest rows sat
	-- off the bottom, were dragged back onto the grid on screen every frame, and
	-- forgot it the moment the set was reloaded, because nothing was allowed to
	-- write a position the player had not chosen. Here they did choose: they
	-- opened the editor here. Only this orientation's pair is written -- setPos
	-- writes xLand/yLand in landscape -- so the portrait layout is untouched.
	local pulledBack = clampAllButtons(false, true)
	if pulledBack > 0 then
		local where = isLandscapeNow() and "landscape" or "portrait"
		Note("\n" .. pulledBack .. " button(s) were off the " .. where
			.. " screen and have been moved into view. This is the " .. where
			.. " layout only; the other orientation is unchanged.\n")
	end

	drawButtons()
	view:invalidate()
end

function exitManagerMode()
	-- The layer, not the flag: drawManagerLayer is a boolean and is never nil,
	-- so this tested nothing and recycled a layer that OnDestroy had already
	-- set to nil -- reloading settings while editing, then leaving edit mode,
	-- called a method on nil.
	if(managerLayer ~= nil) then
		managerCanvas = nil
		managerLayer:recycle()
		managerLayer = nil
	end
	view:setOnTouchListener(normalTouch_cb)
	manage = false
	clearUndoHistory()
	refreshStatusOffset(true)
	
	local parent = view:getParent()
	parent:removeView(backWidget)
	
	local tmp = {}
	for i,b in pairs(buttons) do
		tmp[i] = b.data
		if(b.selected) then b.selected = false end
	end
		
	PluginXCallS("saveSetDefaults",serialize(defaults))
	PluginXCallS("saveButtons",serialize(tmp))
	
	drawButtons()
	view:invalidate()
	notifyFloatingButtonsChanged()
end

function exitManagerModeNoSave()
	-- The layer, not the flag: drawManagerLayer is a boolean and is never nil,
	-- so this tested nothing and recycled a layer that OnDestroy had already
	-- set to nil -- reloading settings while editing, then leaving edit mode,
	-- called a method on nil.
	if(managerLayer ~= nil) then
		managerCanvas = nil
		managerLayer:recycle()
		managerLayer = nil
	end
	view:setOnTouchListener(normalTouch_cb)
	manage = false
	clearUndoHistory()
	refreshStatusOffset(true)
	
	local parent = view:getParent()
	parent:removeView(backWidget)
	
	local tmp = {}
	for i,b in pairs(buttons) do
		tmp[i] = b.data
		if(b.selected) then b.selected = false end
	end
	
	
	PluginXCallS("loadButtonSet",lastLoadedSet)
	view:invalidate()
	-- Do not notify here: in-memory buttons are about to be replaced by the
	-- async loadButtonSet → loadButtons path, which notifies with fresh data.
end

-- Tell Java which buttons should float. Called after load/edit/manage transitions.
-- When manage is true, editing=true and buttons=[] so the overlay hides.
-- Payload is JSON (org.json) — Java parses JSONObject, not Lua serialize.
function notifyFloatingButtonsChanged()
	pcall(function()
		local JSONObject = luajava.bindClass("org.json.JSONObject")
		local JSONArray = luajava.bindClass("org.json.JSONArray")
		local root = luajava.new(JSONObject)
		local editing = manage == true
		root:put("editing", editing)
		local arr = luajava.new(JSONArray)
		if not editing and buttons ~= nil then
			for i, b in ipairs(buttons) do
				local d = b.data
				-- Point B: an accordion never leaves the grid. Its children only
				-- exist while the parent is expanded, and the parent's press
				-- opens a fan of buttons drawn on the button window — neither
				-- can happen in a floating window over the keyboard, so a
				-- floating copy of either would be a button that does nothing.
				local accordionish = hasAccordionConfig(d) or b.isAccordionChild == true
				if d ~= nil and d.floating == true and not accordionish then
					local mode = d.floatMode
					if mode ~= "keyboard" then
						mode = "always"
					end
					local o = luajava.new(JSONObject)
					o:put("index", i)
					o:put("label", tostring(d.label or ""))
					o:put("command", tostring(d.command or ""))
					o:put("flipLabel", tostring(d.flipLabel or ""))
					o:put("flipCommand", tostring(d.flipCommand or ""))
					o:put("holdCommand", tostring(d.holdCommand or ""))
					o:put("swipeUpCommand", tostring(d.swipeUpCommand or ""))
					o:put("swipeDownCommand", tostring(d.swipeDownCommand or ""))
					o:put("swipeLeftCommand", tostring(d.swipeLeftCommand or ""))
					o:put("swipeRightCommand", tostring(d.swipeRightCommand or ""))
					o:put("swipeUpLeftCommand", tostring(d.swipeUpLeftCommand or ""))
					o:put("swipeUpRightCommand", tostring(d.swipeUpRightCommand or ""))
					o:put("swipeDownLeftCommand", tostring(d.swipeDownLeftCommand or ""))
					o:put("swipeDownRightCommand", tostring(d.swipeDownRightCommand or ""))
					o:put("showGestureLabel", d.showGestureLabel ~= false)
					o:put("switchTo", tostring(d.switchTo or ""))
					o:put("primaryColor", tonumber(d.primaryColor) or 0)
					o:put("selectedColor", tonumber(d.selectedColor) or 0)
					o:put("flipColor", tonumber(d.flipColor) or 0)
					o:put("labelColor", tonumber(d.labelColor) or 0)
					o:put("flipLabelColor", tonumber(d.flipLabelColor) or 0)
					o:put("width", tonumber(d.width) or 80)
					o:put("height", tonumber(d.height) or 80)
					o:put("labelSize", tonumber(d.labelSize) or 23)
					o:put("floating", true)
					o:put("floatMode", mode)
					o:put("floatX", tonumber(d.floatX) or -1)
					o:put("floatY", tonumber(d.floatY) or -1)
					-- Landscape gets its own pair: the activity keeps itself
					-- across a turn, so nothing re-lays the buttons out and one
					-- stored pair made a button dragged in portrait follow the
					-- portrait coordinates. Missing = -1 = never placed, which
					-- is what a new floating button already means.
					o:put("floatXLand", tonumber(d.floatXLand) or -1)
					o:put("floatYLand", tonumber(d.floatYLand) or -1)
					-- Grid centre (Lua data.x/y) so Java can seed unplaced
					-- floaters at the button that was toggled floating.
					o:put("gridX", tonumber(d.x) or 0)
					o:put("gridY", tonumber(d.y) or 0)
					-- Landscape grid pair, when the player has made one. Absent
					-- means landscape still shows the portrait layout.
					if tonumber(d.xLand) ~= nil and tonumber(d.yLand) ~= nil then
						o:put("gridXLand", tonumber(d.xLand))
						o:put("gridYLand", tonumber(d.yLand))
					end
					o:put("statusOffset", tonumber(statusoffset) or 0)
					o:put("floatRound", d.floatRound == true)
					o:put("floatFrame", d.floatFrame == true)
					arr:put(o)
				end
			end
		end
		root:put("buttons", arr)
		local activity = GetActivity()
		if activity ~= nil and activity.onFloatingButtonsChanged ~= nil then
			activity:onFloatingButtonsChanged(root:toString())
		end
	end)
end

-- A button has one position, and the player can set it in two places: on the
-- grid, or by dragging the floating copy after a 2 s hold. These two keep them
-- the same number. Moving it on the grid drops the dragged position, so the
-- floating copy is seeded from the grid again; dragging the floating copy
-- writes the grid position back (portrait only -- the grid stores one pair and
-- it is the portrait one).
-- The two cannot both be on. A super button is a copy in its own window over
-- the game (or over the keyboard); an accordion is a fan of buttons drawn on
-- the button grid and its children only exist while the parent is expanded.
-- There is no way to show that in the floating window, so the copy would be a
-- button that does nothing -- and the player, who ticked both, would have no
-- way of knowing why. Enforced on the data, not in the dialog: the editor is
-- not the only thing that writes a button (import, plugins, older profiles).
--
-- Being a super button wins, because it is the thing the player can see is on.
-- Returns true when something was taken away, so the caller can say so.
function enforceNoAccordionOnSuperButton(b)
	if b == nil or b.data == nil or b.data.floating ~= true then
		return false
	end
	local d = b.data
	local had = (d.accordionDirection ~= nil and d.accordionDirection ~= "")
			or (d.accordionChildren ~= nil and #d.accordionChildren > 0)
	if not had then
		return false
	end
	d.accordionDirection = ""
	d.accordionChildren = {}
	return true
end

-- Only ever *shift* the floating position, never recompute it from the grid.
-- The grid and the floating layer do not share an origin (status bar, chrome,
-- and in overlay mode the position is on the screen), and every absolute
-- conversion so far has left the button a status bar away from where it
-- belonged. A delta is the same number in both spaces.
-- -1 means "never placed"; that stays -1, so such a button keeps being seeded
-- the way it is seeded today.
function shiftFloatPlacement(b, dx, dy)
	if b == nil or b.data == nil or (dx == 0 and dy == 0) then
		return
	end
	local d = b.data
	if (tonumber(d.floatX) or -1) ~= -1 then
		d.floatX = d.floatX + dx
	end
	if (tonumber(d.floatY) or -1) ~= -1 then
		d.floatY = d.floatY + dy
	end
	if (tonumber(d.floatXLand) or -1) ~= -1 then
		d.floatXLand = d.floatXLand + dx
	end
	if (tonumber(d.floatYLand) or -1) ~= -1 then
		d.floatYLand = d.floatYLand + dy
	end
end

-- Java windowCall("button_window", "applyFloatPosition", serialize{index=,floatX=,floatY=})
-- Mutates in-memory float coords only; Java must call persistFloatingButtons to save.
function applyFloatPosition(data)
	if data == nil or data == "" then
		return
	end
	local chunk = loadstring(data)
	if chunk == nil then
		return
	end
	local ok, pos = pcall(chunk)
	if not ok or type(pos) ~= "table" then
		return
	end
	local index = tonumber(pos.index)
	if index == nil or buttons == nil or buttons[index] == nil then
		return
	end
	local d = buttons[index].data
	if pos.floatX ~= nil then
		d.floatX = tonumber(pos.floatX) or d.floatX
	end
	if pos.floatY ~= nil then
		d.floatY = tonumber(pos.floatY) or d.floatY
	end
	-- Java sends the Land pair instead when the drag happened in landscape.
	if pos.floatXLand ~= nil then
		d.floatXLand = tonumber(pos.floatXLand) or d.floatXLand
	end
	if pos.floatYLand ~= nil then
		d.floatYLand = tonumber(pos.floatYLand) or d.floatYLand
	end
	-- Java sends where the finger left the floating copy, as a grid centre, in
	-- portrait. The button on the grid goes there: the two are one position and
	-- the floating copy is rebuilt from the grid, so without this the drag would
	-- be undone the next time the overlay is rebuilt.
	local gx = tonumber(pos.gridX)
	local gy = tonumber(pos.gridY)
	if gx ~= nil and gy ~= nil then
		local cx, cy = clampLogicalPosition(gx, gy, buttons[index])
		setPos(buttons[index], cx, cy)
		refreshRect(buttons[index])
	end
end

-- Java windowCall("button_window", "persistFloatingButtons", "") after a drag drop.
-- Same saveButtons path as exitManagerMode; skips saveSetDefaults (positions only).
function persistFloatingButtons()
	if buttons == nil then
		return
	end
	local tmp = {}
	for i, b in pairs(buttons) do
		tmp[i] = b.data
	end
	PluginXCallS("saveButtons", serialize(tmp))
end

-- Undo and redo for the button editor.
--
-- Whole-layout snapshots, not per-tool inverses: every tool here ends the same
-- way (write b.data, refreshRect, drawButtons, invalidate), so one restore path
-- covers a drag, a delete, a tidy-up and a grid refit alike, and a tool added
-- later gets undo by calling pushUndo before it touches anything.
--
-- A snapshot is the raw data table of every button plus the set defaults the
-- layout tools write (size and grid spacing). It is not the button objects:
-- those carry a Paint and a RectF, and are rebuilt through BUTTON:new on the
-- way back in, which is the same path loadButtons uses.
--
-- In memory only, and only while the editor is open. Nothing is saved per step;
-- Done still writes the set, Cancel still reloads it.
undoStack = {}
redoStack = {}
UNDO_LIMIT = 20

local function snapshotLayout()
	local copytable = require("copytable")
	local snap = { buttons = {}, selected = {}, defaults = {} }
	for i = 1, #buttons do
		if buttons[i] ~= nil then
			snap.buttons[#snap.buttons + 1] = copytable.deep(buttons[i].data)
			-- The selection is part of the state, not decoration: the layout
			-- tools act on it, so an undo that dropped it would leave the next
			-- Line up quietly acting on every button instead of the three the
			-- player had picked. It lives on the button object, not in its data,
			-- so the deep copy above does not carry it.
			snap.selected[#snap.buttons] = buttons[i].selected == true
		end
	end
	snap.defaults.width = defaults.width
	snap.defaults.height = defaults.height
	snap.defaults.gridXwidth = defaults.gridXwidth
	snap.defaults.gridYwidth = defaults.gridYwidth
	return snap
end

local function restoreLayout(snap)
	local copytable = require("copytable")
	-- Fields, not the table: BUTTON_DATA.__index points at this very table, so
	-- swapping it for another one would cut every button off from its defaults.
	defaults.width = snap.defaults.width
	defaults.height = snap.defaults.height
	defaults.gridXwidth = snap.defaults.gridXwidth
	defaults.gridYwidth = snap.defaults.gridYwidth
	gridXwidth = defaults.gridXwidth * density
	gridYwidth = defaults.gridYwidth * density

	-- In place. Other modules took a reference to this table when they loaded,
	-- so a fresh one here would leave them looking at the old buttons.
	for i = #buttons, 1, -1 do
		buttons[i] = nil
	end
	-- Copied again on the way out, so the same snapshot can be restored twice --
	-- which is exactly what undo, redo, undo does.
	for i = 1, #snap.buttons do
		buttons[i] = BUTTON:new(copytable.deep(snap.buttons[i]), density)
		buttons[i].selected = snap.selected[i] == true
		refreshRect(buttons[i])
	end
	-- These are the buttons that were just thrown away. Leaving the touch
	-- handler holding one of them means the next finger-up reads a selected flag
	-- from an object no longer on screen.
	touchedbutton = {}
	fingerdown = false
	selectedtouchstart = false

	if manage and managerCanvas ~= nil then
		drawManagerGrid()
	end
	drawButtons()
	view:invalidate()
	refreshEditorUndoChrome()
end

-- Called by a tool before it changes anything. The redo branch is dropped: once
-- a new change is made, what was undone is no longer on the way forward.
function pushUndo()
	if not manage then
		return
	end
	undoStack[#undoStack + 1] = snapshotLayout()
	if #undoStack > UNDO_LIMIT then
		table.remove(undoStack, 1)
	end
	redoStack = {}
	refreshEditorUndoChrome()
end

function clearUndoHistory()
	undoStack = {}
	redoStack = {}
	refreshEditorUndoChrome()
end

function editorMenuUndo()
	if #undoStack == 0 then
		Note("\nNothing to undo.\n")
		return
	end
	local snap = table.remove(undoStack)
	redoStack[#redoStack + 1] = snapshotLayout()
	if #redoStack > UNDO_LIMIT then
		table.remove(redoStack, 1)
	end
	restoreLayout(snap)
end

function editorMenuRedo()
	if #redoStack == 0 then
		Note("\nNothing to redo.\n")
		return
	end
	local snap = table.remove(redoStack)
	undoStack[#undoStack + 1] = snapshotLayout()
	if #undoStack > UNDO_LIMIT then
		table.remove(undoStack, 1)
	end
	restoreLayout(snap)
end

-- Grey the two arrows on the editor strip when there is nothing behind them.
-- This plugin's Lua runs in the UI process, so it reaches the views directly
-- rather than going back out through the service.
function refreshEditorUndoChrome()
	pcall(function()
		local root = view:getRootView()
		if root == nil then
			return
		end
		local res = view:getContext():getResources()
		local pkg = view:getContext():getPackageName()
		local pairsOfIds = {
			{ name = "editor_undo", live = #undoStack > 0 },
			{ name = "editor_redo", live = #redoStack > 0 },
		}
		for i = 1, #pairsOfIds do
			local id = res:getIdentifier(pairsOfIds[i].name, "id", pkg)
			if id ~= 0 then
				local v = root:findViewById(id)
				if v ~= nil then
					v:setEnabled(pairsOfIds[i].live)
					v:setAlpha(pairsOfIds[i].live and 1.0 or 0.35)
				end
			end
		end
	end)
end

-- Buttons the layout tools act on: the selection if there is one, otherwise all
-- of them. Selecting nothing and pressing Apply meaning "everything" is the
-- behaviour people expect, and it saves a select-all step.
local function layoutTargets()
	local chosen = {}
	for i = 1, #buttons do
		if buttons[i] ~= nil and buttons[i].selected then
			chosen[#chosen + 1] = buttons[i]
		end
	end
	if #chosen > 0 then
		return chosen, true
	end
	local all = {}
	for i = 1, #buttons do
		if buttons[i] ~= nil then
			all[#all + 1] = buttons[i]
		end
	end
	return all, false
end

-- Fields a button may hold its own copy of, and otherwise inherits.
DROPPABLE_OWN_FIELDS = {
	"width", "height", "labelSize",
	"primaryColor", "flipColor", "selectedColor",
	"labelColor", "flipLabelColor",
}

--- Drop a button's own values that it would inherit unchanged anyway.
---
--- Called on Done, to keep a set from storing a copy of the default on every
--- button. The subtlety, and a bug that lived here: dropping an own value makes
--- the button inherit **BUTTONSET_DATA**, because `BUTTON_DATA.__index` is the
--- global prototype. Nothing links a button to its own set's `defaults` table.
---
--- So an own value may only be dropped when the set default and the prototype
--- agree. Otherwise the button springs back to the factory value instead of the
--- set's — "44x44 keeps reverting to 48x48 in set main, but is fine in Default",
--- 48 being BUTTONSET_DATA.width. The revert happened on Done, one step after
--- Apply size had visibly worked, which is what made it look random.
---
--- A set whose default already is the factory value clears exactly as before,
--- and that is where the saving always came from.
function dropRedundantOwnValues(b, setDefaults)
	if b == nil or b.data == nil or setDefaults == nil then
		return
	end
	for i = 1, #DROPPABLE_OWN_FIELDS do
		local field = DROPPABLE_OWN_FIELDS[i]
		local own = tonumber(rawget(b.data, field))
		local fromSet = tonumber(setDefaults[field])
		local fromPrototype = tonumber(BUTTONSET_DATA[field])
		if own ~= nil and own == fromSet and fromSet == fromPrototype then
			rawset(b.data, field, nil)
		end
	end
end

-- Give every target the same width and height, in dp.
function applyButtonSize(w, h)
	local targets, hadSelection = layoutTargets()
	if #targets == 0 then
		return
	end
	local newW = tonumber(w)
	local newH = tonumber(h)
	if newW == nil or newH == nil or newW < 16 or newH < 16 then
		return
	end
	for i = 1, #targets do
		targets[i].data.width = newW
		targets[i].data.height = newH
		refreshRect(targets[i])
	end
	if not hadSelection then
		-- Nothing selected means this was a set-wide change, so it becomes the
		-- default for buttons added later too.
		defaults.width = newW
		defaults.height = newH
	end
	drawButtons()
	view:invalidate()
	saveDefaultOptions()
	Note("\nButton size set to " .. newW .. "x" .. newH .. " for "
		.. #targets .. (hadSelection and " selected button(s).\n" or " button(s).\n"))
	-- Handed back so the options dialog can show what actually landed; it sits
	-- full screen over the buttons, so its own fields are all the user can see.
	-- The third value says whether this also became the set default, which the
	-- dialog needs: it must not let a size meant for two selected buttons end
	-- up as what every new button starts at.
	return newW, newH, not hadSelection
end

-- Lay the targets out on the grid, in reading order.
--
-- Cells are anchored to the drawn grid lines (vertical at k*gridXwidth,
-- horizontal at statusoffset + k*gridYwidth) rather than to wherever the
-- buttons already happened to sit, which is what left them a half cell out of
-- true. A button is positioned by its centre, so the centre goes to the cell
-- corner plus half the button.
--
-- Column count of 0 means "as many as fit across the screen".
function tidyButtonLayout(columns)
	local targets, hadSelection = layoutTargets()
	if #targets < 2 then
		return
	end
	-- Reading order from where the buttons already are, so a tidy-up does not
	-- shuffle them into an unfamiliar arrangement.
	local rowTolerance = 0
	for i = 1, #targets do
		local h = (tonumber(targets[i].data.height) or 42) * density
		if h > rowTolerance then rowTolerance = h end
	end
	rowTolerance = rowTolerance * 0.6
	table.sort(targets, function(a, b)
		if math.abs(posY(a.data) - posY(b.data)) > rowTolerance then
			return posY(a.data) < posY(b.data)
		end
		return posX(a.data) < posX(b.data)
	end)

	local stepX = gridXwidth
	local stepY = gridYwidth
	if stepX == nil or stepX < 1 then stepX = 45 * density end
	if stepY == nil or stepY < 1 then stepY = 45 * density end

	local screenW = view:getWidth()
	local cols = tonumber(columns) or 0
	if cols < 1 then
		cols = math.max(1, math.floor(screenW / stepX))
	end

	-- Start at the grid cell nearest the current top-left of the group, so the
	-- pad stays roughly where it was instead of jumping to the corner.
	-- Through posX/posY: the sort just above already reads the orientation in
	-- force, and setPos below writes it, so taking the origin from the portrait
	-- pair put a landscape tidy-up's origin somewhere the buttons are not.
	local minX, minY = posX(targets[1].data), posY(targets[1].data)
	for i = 1, #targets do
		local tx, ty = posX(targets[i].data), posY(targets[i].data)
		if tx < minX then minX = tx end
		if ty < minY then minY = ty end
	end
	local originCol = math.max(0, math.floor((minX - stepX * 0.5) / stepX + 0.5))
	local originRow = math.max(0, math.floor((minY - statusoffset - stepY * 0.5) / stepY + 0.5))

	-- Pull the origin back until the whole block fits on screen. Without this a
	-- group already low on the screen was laid out downwards from where it sat
	-- and its last rows went off the bottom edge: not visible, not reachable,
	-- and only rescued the next time the editor was opened. Pulling the origin
	-- back moves the block as a block; the clamp below is the backstop for a
	-- block that cannot fit whatever the origin is.
	local rows = math.ceil(#targets / cols)
	local screenH = view:getHeight() - statusoffset
	local maxCol = math.max(0, math.floor(screenW / stepX) - cols)
	local maxRow = math.max(0, math.floor(screenH / stepY) - rows)
	if originCol > maxCol then originCol = maxCol end
	if originRow > maxRow then originRow = maxRow end

	for i = 1, #targets do
		local b = targets[i]
		local col = originCol + ((i - 1) % cols)
		local row = originRow + math.floor((i - 1) / cols)
		local halfW = ((tonumber(b.data.width) or 42) * density) / 2
		local halfH = ((tonumber(b.data.height) or 42) * density) / 2
		local beforeX, beforeY = posX(b.data), posY(b.data)
		local cx, cy = clampLogicalPosition(col * stepX + halfW,
			statusoffset + row * stepY + halfH, b)
		setPos(b, cx, cy)
		shiftFloatPlacement(b, posX(b.data) - beforeX, posY(b.data) - beforeY)
		refreshRect(b)
	end
	drawButtons()
	view:invalidate()
	saveDefaultOptions()
	Note("\nArranged " .. #targets .. (hadSelection and " selected" or "")
		.. " button(s) into " .. cols .. " column(s) on the grid.\n")
end

-- Put the targets on one line, without re-flowing them.
--
-- axis "x": one vertical line, every button on the same X. axis "y": one
-- horizontal line, same Y. The anchor is whichever target is furthest that way
-- already -- leftmost for x, topmost for y -- so one button of the group never
-- moves and it is obvious from looking at it which one that was. An average
-- would move all of them and leave nothing to check the result against.
--
-- Positions are centres, so buttons of different sizes line up through their
-- middles rather than by an edge. Order is untouched: this is the tool for when
-- the arrangement is right and only the wobble is wrong, which is what a tidy-up
-- cannot do without re-flowing.
function alignSelectedButtons(axis)
	local targets, hadSelection = layoutTargets()
	if #targets < 2 then
		Note("\nSelect two or more buttons to line them up.\n")
		return
	end
	local vertical = tostring(axis or "x") ~= "y"

	local anchor = nil
	for i = 1, #targets do
		local v = vertical and posX(targets[i].data) or posY(targets[i].data)
		if anchor == nil or v < anchor then anchor = v end
	end

	for i = 1, #targets do
		local b = targets[i]
		local beforeX, beforeY = posX(b.data), posY(b.data)
		local wantX = vertical and anchor or beforeX
		local wantY = vertical and beforeY or anchor
		local cx, cy = clampLogicalPosition(wantX, wantY, b)
		setPos(b, cx, cy)
		shiftFloatPlacement(b, cx - beforeX, cy - beforeY)
		refreshRect(b)
	end
	drawButtons()
	view:invalidate()
	saveDefaultOptions()
	Note("\nLined up " .. #targets .. (hadSelection and " selected" or "")
		.. " button(s) on one " .. (vertical and "vertical" or "horizontal")
		.. " line.\n")
end

-- Spread the targets into a column, a row or a block, on the grid.
--
-- All three are tidyButtonLayout with a column count: it already sorts by
-- reading order, anchors to the drawn grid lines and keeps the block on screen.
-- The only thing decided here is the count, because "one column" and "as square
-- as it goes" are the two shapes people actually ask for and neither is
-- obvious from a number box.
function spreadSelectedButtons(shape)
	local targets = layoutTargets()
	local n = #targets
	if n < 2 then
		Note("\nSelect two or more buttons to spread them out.\n")
		return
	end
	local s = tostring(shape or "grid")
	local cols
	if s == "column" then
		cols = 1
	elseif s == "row" then
		cols = n
	else
		-- Square-ish: the block is as wide as it is tall, give or take a row.
		cols = math.ceil(math.sqrt(n))
	end
	tidyButtonLayout(cols)
end

-- Pick a grid that tiles the screen width exactly.
--
-- square = true keeps cells square, so buttons stay square and whatever is left
-- over stays at the right edge, as it does now. square = false stretches the
-- cells to fill both directions, which fills the screen but stops the cells
-- being square.
function fitGridToScreen(square)
	local screenW = view:getWidth()
	local screenH = view:getHeight() - statusoffset
	if screenW <= 0 or screenH <= 0 then
		return
	end
	local current = gridXwidth
	if current == nil or current < 1 then current = 45 * density end
	local cols = math.max(1, math.floor(screenW / current + 0.5))
	local cellX = math.floor(screenW / cols)

	local cellY
	if square then
		cellY = cellX
	else
		local rows = math.max(1, math.floor(screenH / current + 0.5))
		cellY = math.floor(screenH / rows)
	end

	gridXwidth = cellX
	gridYwidth = cellY
	defaults.gridXwidth = math.floor(cellX / density + 0.5)
	defaults.gridYwidth = math.floor(cellY / density + 0.5)

	-- Buttons follow the cell so they still fill it; square cells keep them square.
	local size = math.floor(math.min(cellX, cellY) / density + 0.5) - 3
	local appliedW, appliedH, becameDefault
	if size >= 16 then
		appliedW, appliedH, becameDefault = applyButtonSize(size, size)
	else
		drawButtons()
		saveDefaultOptions()
	end
	-- The grid lines live on the manager canvas, and nothing above touches it:
	-- applyButtonSize redraws the buttons only. So the common path moved the
	-- buttons to the new cells while the cells themselves stayed drawn at the
	-- old spacing until the editor was closed and reopened. The grid-spacing
	-- sliders next door have always redrawn both, and now so does Fit.
	--
	-- Play-mode Fit has no manager canvas, hence the guard.
	if manage == true and managerCanvas ~= nil then
		drawManagerGrid()
	end
	view:invalidate()
	Note("\nGrid set to " .. cols .. " columns of "
		.. math.floor(cellX / density + 0.5) .. "x"
		.. math.floor(cellY / density + 0.5) .. "dp"
		.. (square and " (square)." or " (stretched to the window).") .. "\n")
	return appliedW, appliedH, becameDefault
end

-- Named size presets for the layout wizard / menu path.
LAYOUT_SIZE_PRESETS = {
	compact = 32,
	comfortable = 42,
	large = 56,
	xl = 72,
	fit_square = "fit_square",
	fit = "fit_square",
}

local cachedLayoutPacks = nil
local cachedWizardState = nil
local cachedExistingSetNames = nil
local layoutWizardShowRequested = false
local layoutWizardOffered = false
local layoutWizardSoftPrompt = nil
-- The full wizard dialog is up. Kept here, not in the wizard module, because
-- loadOptions has to know not to fire the first-run soft prompt over it.
layoutWizardDialogOpen = false
local buttonWindowOptions = nil
local layoutWizardModule = nil

local function layoutPendingTruthy(v)
	return v == true or v == "true" or v == "1"
end

local function ensureLayoutWizardModule()
	if layoutWizardModule == nil then
		layoutWizardModule = require("buttonlayoutwizard")
	end
	layoutWizardModule.init(mContext)
	return layoutWizardModule
end

local function suggestLocalSetName(base, existingNames)
	local mod = layoutWizardModule
	if mod ~= nil and type(mod.suggestSetName) == "function" then
		return mod.suggestSetName(base, existingNames)
	end
	local existing = {}
	if type(existingNames) == "table" then
		for _, n in pairs(existingNames) do
			if n ~= nil and tostring(n) ~= "" then
				existing[string.lower(tostring(n))] = true
			end
		end
	end
	local root = string.lower(tostring(base or ""):match("^%s*(.-)%s*$") or "")
	if root == "" then root = "pack" end
	if not existing[root] then
		return root
	end
	local i = 2
	while existing[root .. "_" .. tostring(i)] do
		i = i + 1
	end
	return root .. "_" .. tostring(i)
end

local function tryPresentLayoutWizard()
	if not layoutWizardShowRequested then
		return
	end
	if cachedWizardState == nil then
		return
	end
	if cachedLayoutPacks == nil then
		PluginXCallS("getLayoutPackList", "")
		return
	end
	if cachedExistingSetNames == nil then
		PluginXCallS("getExistingButtonSetNames", "")
		return
	end
	local state = {}
	for k, v in pairs(cachedWizardState) do
		state[k] = v
	end
	state.packs = cachedLayoutPacks
	state.existingNames = cachedExistingSetNames
	if type(state.suggest) ~= "table" then
		state.suggest = {}
	end
	if type(state.packs) == "table" then
		for _, p in ipairs(state.packs) do
			if type(p) == "table" and p.id ~= nil then
				local id = tostring(p.id)
				if state.suggest[id] == nil then
					state.suggest[id] = suggestLocalSetName(id, state.existingNames)
				end
			end
		end
	end
	layoutWizardShowRequested = false
	layoutWizardDialogOpen = true
	ensureLayoutWizardModule().showWizard(state)
end

-- The square cell fitGridToScreen(true) is about to pick, in dp, or nil if the
-- view is not laid out. Needed up front so positions can be rescaled to the new
-- lattice before the grid itself moves.
function fitCellForScreen(square)
	local screenW = view ~= nil and view:getWidth() or 0
	if screenW <= 0 then
		return nil
	end
	local current = gridXwidth
	if current == nil or current < 1 then current = 45 * density end
	local cols = math.max(1, math.floor(screenW / current + 0.5))
	return math.floor(math.floor(screenW / cols) / density + 0.5)
end

-- Move every tile so the pad keeps its shape at a new lattice pitch.
--
-- Scales each centre's offset from the pad's top-left corner by
-- newPitch/currentPitch. The factor is read from the *live* pitch against an
-- absolute target, so the operation is idempotent: applying "large" twice, or
-- going comfortable → xl → comfortable, lands on the same coordinates. That is
-- the guard the tutorial pad needed — its DP centres were multiplied by density
-- on every align until they reached ~1e15.
function rescaleLayoutToPitch(newPitchDp)
	local target = tonumber(newPitchDp)
	if target == nil or target < 8 then
		return false
	end
	local currentDp = tonumber(defaults.gridXwidth)
	if currentDp == nil or currentDp < 1 then
		currentDp = (gridXwidth or 45 * density) / density
	end
	local factor = target / currentDp
	if math.abs(factor - 1) < 0.001 then
		return true
	end
	local targets = layoutTargets()
	if #targets == 0 then
		return false
	end
	-- Same anchor the scale is applied to, in the orientation setPos writes.
	local minX, minY = posX(targets[1].data), posY(targets[1].data)
	for i = 1, #targets do
		local tx, ty = posX(targets[i].data), posY(targets[i].data)
		if tx < minX then minX = tx end
		if ty < minY then minY = ty end
	end
	for i = 1, #targets do
		local b = targets[i]
		local beforeX, beforeY = posX(b.data), posY(b.data)
		setPos(b, minX + (beforeX - minX) * factor, minY + (beforeY - minY) * factor)
		shiftFloatPlacement(b, posX(b.data) - beforeX, posY(b.data) - beforeY)
		refreshRect(b)
	end
	return true
end

-- Set the snap lattice, in dp, both directions. Same two assignments the editor's
-- grid-spacing callbacks make; pulled out so a size change can move the pitch with
-- the tiles instead of leaving them to overlap. Play mode has no manager canvas.
function setGridPitch(dp)
	local v = tonumber(dp)
	if v == nil or v < 8 then
		return false
	end
	gridXwidth = v * density
	gridYwidth = v * density
	defaults.gridXwidth = v
	defaults.gridYwidth = v
	if manage == true and managerCanvas ~= nil then
		drawManagerGrid()
	end
	return true
end

function applyLayoutSizePreset(preset)
	if view == nil or view:getWidth() <= 0 then
		return false
	end
	local p = tostring(preset or "")
	p = string.lower(p:match("^%s*(.-)%s*$") or p)
	if p == "fit" then p = "fit_square" end
	if p == "extra_large" or p == "extralarge" or p == "extra large" then
		p = "xl"
	end

	-- No tidyButtonLayout here. Resizing must not re-flow: tidy sorts the tiles
	-- into reading order and re-lays them across the screen, which destroys any
	-- arrangement that means something — a compass rose most of all. Growing the
	-- tiles without growing the lattice is the other half of the same bug (72dp
	-- tiles on a 45dp pitch overlap by 27dp), so the pitch moves with the size
	-- and the tiles move with the pitch.
	pushUndo()
	local named = { compact = 32, comfortable = 42, large = 56, xl = 72 }
	local size = named[p]
	if size ~= nil then
		rescaleLayoutToPitch(size + 3)
		setGridPitch(size + 3)
		applyButtonSize(size, size)
	elseif p == "fit_square" then
		local cell = fitCellForScreen(true)
		if cell ~= nil then
			rescaleLayoutToPitch(cell)
		end
		fitGridToScreen(true)
	else
		return false
	end
	PluginXCallS("setLayoutSizePreset", p)
	return true
end

-- Options → Button → Button size picked a new entry. The service owns the
-- stored value; this just applies it to whatever set is on screen, pack or
-- hand-made, without re-flowing it.
function applyLayoutSizePresetCmd(args)
	local preset = args ~= nil and tostring(args) or ""
	if preset == "" then
		return
	end
	pcall(function() applyLayoutSizePreset(preset) end)
end

-- Called after installPack / applyLayoutWizardFinish loads the chosen set, to
-- dismiss the wizard: Apply keeps it open until this fires so a full skip
-- (overwrite=false / reserved) does not look like success.
--
-- The size argument is ignored, deliberately. This used to call
-- applyLayoutSizePreset, which ran tidyButtonLayout — a re-flow of every tile
-- into reading order across the screen width. It undid the geometry the service
-- had just built and is what turned the compass rose into
-- "NW N NE INV / S SE EXITS U": the bearings stopped matching the positions
-- because the positions had been thrown away. doInstallBatch now rebuilds each
-- pack from its canonical DP table at the chosen pitch and places it, so by the
-- time this runs the set on screen is already the right size in the right shape.
function onLayoutPackInstalled(args)
	layoutWizardDialogOpen = false
	pcall(function()
		ensureLayoutWizardModule().dismissAfterApply()
	end)
end

function showLayoutPackList(data)
	local packs = loadSerialized(data, "the layout pack list")
	if packs == nil then
		return
	end
	cachedLayoutPacks = packs
	tryPresentLayoutWizard()
end

function showExistingButtonSetNames(data)
	local names = loadSerialized(data, "existing button set names")
	if names == nil then
		cachedExistingSetNames = {}
	else
		-- Accept either a list {"a","b"} or a map {a=count,...} from getButtonSetList shape.
		local list = {}
		local n = #names
		if n > 0 then
			for i = 1, n do
				if names[i] ~= nil and tostring(names[i]) ~= "" then
					list[#list + 1] = tostring(names[i])
				end
			end
		else
			for k, _ in pairs(names) do
				if k ~= nil and tostring(k) ~= "" then
					list[#list + 1] = tostring(k)
				end
			end
			table.sort(list)
		end
		cachedExistingSetNames = list
	end
	tryPresentLayoutWizard()
end

function showLayoutWizardState(data)
	local state = loadSerialized(data, "the layout wizard state")
	if state == nil then
		return
	end
	cachedWizardState = state
	if type(state.existingNames) == "table" then
		cachedExistingSetNames = state.existingNames
	end
	-- Options / command path sets the request flag; soft-prompt Open does too.
	layoutWizardShowRequested = true
	tryPresentLayoutWizard()
end

function showLayoutWizard(args)
	layoutWizardShowRequested = true
	-- Re-fetch names so collision warnings stay current across re-entry.
	cachedExistingSetNames = nil
	if type(args) == "string" and args ~= "" then
		local state = loadSerialized(args, "layout wizard args")
		if state ~= nil then
			cachedWizardState = state
			if type(state.packs) == "table" then
				cachedLayoutPacks = state.packs
			end
			if type(state.existingNames) == "table" then
				cachedExistingSetNames = state.existingNames
			end
			tryPresentLayoutWizard()
			return
		end
	end
	if cachedLayoutPacks == nil then
		PluginXCallS("getLayoutPackList", "")
	end
	PluginXCallS("getExistingButtonSetNames", "")
	PluginXCallS("getLayoutWizardState", "")
end

-- First-run soft prompt before opening the full wizard.
function showLayoutWizardOffer(args)
	if mContext == nil then
		return
	end
	if layoutWizardSoftPrompt ~= nil then
		pcall(function() layoutWizardSoftPrompt:dismiss() end)
		layoutWizardSoftPrompt = nil
	end
	local builder = luajava.newInstance("android.app.AlertDialog$Builder", mContext)
	builder:setTitle("Button layout")
	builder:setMessage(
		"This is the first launch of this MUD — the button layout wizard can help set up your buttons.")
	builder:setPositiveButton("Open wizard", luajava.createProxy(
		"android.content.DialogInterface$OnClickListener", {
		onClick = function(d, which)
			layoutWizardSoftPrompt = nil
			showLayoutWizard("")
		end
	}))
	builder:setNegativeButton("Not now", luajava.createProxy(
		"android.content.DialogInterface$OnClickListener", {
		onClick = function(d, which)
			layoutWizardSoftPrompt = nil
			PluginXCallS("clearLayoutWizardPending", "")
		end
	}))
	builder:setOnCancelListener(luajava.createProxy(
		"android.content.DialogInterface$OnCancelListener", {
		onCancel = function(d)
			layoutWizardSoftPrompt = nil
			PluginXCallS("clearLayoutWizardPending", "")
		end
	}))
	layoutWizardSoftPrompt = builder:create()
	layoutWizardSoftPrompt:show()
end

-- The service declined to show the offer for a reason that may not hold next
-- time (offline session). Un-latch so a later trigger can try again.
function releaseLayoutWizardOffer(args)
	layoutWizardOffered = false
end

function maybeOfferLayoutWizard()
	local opts = buttonWindowOptions or options
	if opts == nil then
		return
	end
	if not layoutPendingTruthy(opts.layout_wizard_pending) then
		return
	end
	if layoutWizardOffered then
		return
	end
	if view == nil or view:getWidth() <= 0 then
		return
	end
	-- Latch before the service round-trip so loadButtons/loadOptions/OnSizeChanged
	-- cannot queue three offers. Cleared only when pending goes false→true again.
	layoutWizardOffered = true
	PluginXCallS("offerLayoutWizardIfPending", "")
end

function drawManagerGrid()
		local c = managerCanvas
		local width = view:getWidth()
		local height = view:getHeight()
		-- Opaque black so game text does not show through while editing.
		c:drawRect(0,0,width,height,managerBgPaint)
		-- Trim the ends of the guides so they stop short of the screen's rounded
		-- corners instead of disappearing into them. The lines keep their
		-- positions, because those positions are what they mean - a guide moved
		-- inward would be pointing at the wrong snap. Only their extents change.
		-- Padding the whole window was tried first and cost about 47px of game
		-- text on each side, which is far too much for a drawing aid.
		local edge = 10 * density
		local times = width / gridXwidth
		for x=1,times do
			local gx = gridXwidth*x
			if gx >= edge and gx <= width - edge then
				c:drawLine(gx,statusoffset + edge,gx,height - edge,dpaint)
			end
		end

		times = (height - statusoffset) / gridYwidth
		for y=1,times do
			local gy = statusoffset + gridYwidth*y
			if gy <= height - edge then
				c:drawLine(edge,gy,width - edge,gy,dpaint)
			end
		end

end


gridXwidth = 40 * density --67 * density
gridYwidth = 40 * density --67 * density

intersectMode = 1


mContext = view:getContext()
layoutInflater = mContext:getSystemService(Context.LAYOUT_INFLATER_SERVICE)

local TOOLBARHOLDER_ID = R_id.toolbarholder
local ROOT_ID = R_id.root
local EDITOR_SELECTION_LIST_ROW_ID = R_layout.editor_selection_list_row
local INFOTITLE_ID = R_id.infoTitle
local INFOEXTENDED = R_id.infoExtended		
local ICON_ID = R_id.icon
local VIEW_GONE = View.GONE

local buttonSetListDialog --need this

function showButtonList(data)
	--Note(data)
	local loaded = loadSerialized(data, "the button set list")
	if(loaded == nil) then return end
	setdata = loaded

	--launch the new editor
	
	--for key,value in pairs(luajava) do
  --	Note("\npre found member " .. key);
	--end
	if(buttonSetListDialog == nil) then
		buttonSetListDialog = require("buttonlist")
	end
	buttonSetListDialog.init(mContext)
	--Note("Showing new list\n")
	--buttonSetListDialog.init()
	buttonSetListDialog.showList(setdata,lastLoadedSet)
	return
	
end

function updateButtonListDialog(data)
	--Note("\nConfirmingDelete")
	local incoming = loadSerialized(data, "the updated button set list")
	if(incoming == nil) then return end

	buttonSetListDialog.updateButtonListDialog(incoming)
end

function updateButtonListDialogNoItems()
	buttonSetListDialog.updateButtonListDialogNoItems()
	emptyButtons()
end


function buttonOptions()
  local editorValues = {}
  editorValues.primaryColor = defaults.primaryColor
  editorValues.selectedColor = defaults.selectedColor
  editorValues.flipColor = defaults.flipColor
  editorValues.labelColor = defaults.labelColor
  editorValues.flipLabelColor = defaults.flipLabelColor
  editorValues.switchTo = ""
  editorValues.height = defaults.height
  editorValues.width = defaults.width
  editorValues.labelSize = defaults.labelSize
  editorValues.name = lastLoadedSet
  editorValues.x = 0
  editorValues.y = 0  
  
  -- Rounded: after a Fit the grid is a whole number of pixels, which divides
  -- back into something like 39.272727dp and reads as noise in the dialog.
  editorValues.gridX = math.floor(gridXwidth / density + 0.5)
  editorValues.gridY = math.floor(gridYwidth / density + 0.5)
  editorValues.gridOpacity = manageropacity
  editorValues.gridIntersectionTest = intersectMode
  editorValues.gridSnap = gridsnap
  editorValues.showGestureHints = options.show_gesture_hints == true
    or options.show_gesture_hints == "true"
    or options.show_gesture_hints == "1"
    or options.show_gesture_hints == nil
  editorValues.chromeGestures = options.chrome_gestures or ""
  editorValues.showSwipePreview = options.show_swipe_preview == true
    or options.show_swipe_preview == "true"
    or options.show_swipe_preview == "1"
    or options.show_swipe_preview == nil

  local editorSnapshot = {
    gridsnap = gridsnap,
    gridXwidth = gridXwidth,
    gridYwidth = gridYwidth,
    manageropacity = manageropacity,
    intersectMode = intersectMode,
    showGestureHints = editorValues.showGestureHints
  }

  local editorOptionsDialog = require("editoroptionsdialog")
  editorOptionsDialog.init(mContext)
  editorOptionsDialog.setEditorDoneCallback(function(tmp)
    --v is a table with the values from the setPropertiesEditor as well as the things that are handled below but those are handled responsively
    
    
    --update old button sizes to new button sizes.
    for i=1,#buttons do
      local b = buttons[i]

      local data = b.data
      --local meta = getmetatable(data)
      --local index = meta.__index
      
      dropRedundantOwnValues(b, defaults)
    end
    
    -- The defaults editor names its colours differently from the set data, and
    -- hands back nothing at all when it was never opened -- Done alone arrives
    -- with the values the dialog started from. Without a fallback every plain
    -- Done wrote nil here, and since `defaults` inherits from BUTTONSET_DATA a
    -- nil silently reverts to the factory blue/green/red, taking with it every
    -- button whose own colour was cleared just above for matching the default.
    -- pressedLabelColor was worse: nothing produces that name, so the flip
    -- label colour reset on every Done.
    defaults.width = tmp.width or defaults.width
    defaults.height = tmp.height or defaults.height
    defaults.primaryColor = tmp.normalColor or tmp.primaryColor or defaults.primaryColor
    defaults.flipColor = tmp.flipColor or defaults.flipColor
    defaults.selectedColor = tmp.pressedColor or tmp.selectedColor or defaults.selectedColor
    defaults.labelColor = tmp.normalLabelColor or tmp.labelColor or defaults.labelColor
    defaults.flipLabelColor = tmp.flipLabelColor or defaults.flipLabelColor
    defaults.labelSize = tmp.labelSize or defaults.labelSize
    
    for i=1,#buttons do
      local b = buttons[i]
      refreshRect(b)     
    end
    
    --call redraw buttons to get any new colors in there.
    drawButtons()
    view:invalidate()
    
  end)
  editorOptionsDialog.setGridSnapCallback(function(v)
    gridsnap = v
  end)
  -- Live grid feedback: the options panel is a bottom sheet so spacing and opacity
  -- sliders can be watched on the grid behind it. Report size/grid changes back
  -- into the dialog when a tool cannot be seen without closing settings.
  -- becameDefault is passed straight through: the dialog shows the size that
  -- was applied either way, but only adopts it as the set default when the
  -- change really was set-wide.
  local function reportEditorState(w, h, becameDefault)
    editorOptionsDialog.refreshValues({
      gridX = math.floor(gridXwidth / density + 0.5),
      gridY = math.floor(gridYwidth / density + 0.5),
      width = w,
      height = h,
      becameDefault = becameDefault
    })
  end

  -- Layout tools. Each acts on the selection when there is one, otherwise on
  -- every button, which is what "apply to all" means with nothing selected.
  editorOptionsDialog.setApplySizeCallback(function(w, h)
    pushUndo()
    local aw, ah, becameDefault = applyButtonSize(w, h)
    reportEditorState(aw, ah, becameDefault)
  end)
  editorOptionsDialog.setTidyLayoutCallback(function(columns)
    pushUndo()
    tidyButtonLayout(columns)
    reportEditorState()
  end)
  editorOptionsDialog.setBeginGridChangeCallback(function()
    pushUndo()
  end)
  editorOptionsDialog.setAlignSelectionCallback(function(axis)
    pushUndo()
    alignSelectedButtons(axis)
  end)
  editorOptionsDialog.setSpreadSelectionCallback(function(shape)
    pushUndo()
    spreadSelectedButtons(shape)
  end)
  editorOptionsDialog.setPasteButtonsCallback(function()
    -- Into the middle of the grid: the sheet covers the buttons, so there is no
    -- cell under a finger to aim at the way the long press has.
    local cx = (view:getWidth() / 2)
    local cy = ((view:getHeight() - statusoffset) / 2)
    pasteButtons(cx, cy)
  end)
  editorOptionsDialog.setFitGridCallback(function(square)
    pushUndo()
    local aw, ah, becameDefault = fitGridToScreen(square)
    reportEditorState(aw, ah, becameDefault)
  end)
  editorOptionsDialog.setChromeGesturesCallback(function(v)
    PluginXCallS("setChromeGestures", v ~= nil and v or "")
  end)
  editorOptionsDialog.setGridXSpacingCallback(function(v)
    gridXwidth = v*density
    defaults.gridXwidth = v
    drawManagerGrid()
    view:invalidate()
  end)
  editorOptionsDialog.setGridYSpacingCallback(function(v)
    gridYwidth = v*density
    defaults.gridYwidth = v
    drawManagerGrid()
    view:invalidate()
  end)
  editorOptionsDialog.setGridOpacityCallback(function(v)
    dpaint:setAlpha(v)
    manageropacity = v
    drawManagerGrid()
    view:invalidate()
  end)
  editorOptionsDialog.setGridSnapTestCallback(function(v)
    intersectMode = v
  end)
  editorOptionsDialog.setShowGestureHintsCallback(function(v)
    -- PluginXCallS(fn, data) only carries one data string — call the setter directly.
    PluginXCallS("setShowGestureHints", v and "true" or "false")
    buttonShowHints = v and true or false
    drawButtons()
    view:invalidate()
  end)
  editorOptionsDialog.setShowSwipePreviewCallback(function(v)
    PluginXCallS("setShowSwipePreview", v and "true" or "false")
    buttonShowSwipePreview = v and true or false
    -- Nothing to redraw: the arrow only exists while a finger is down.
  end)
  editorOptionsDialog.setEditorCancelCallback(function()
    gridsnap = editorSnapshot.gridsnap
    gridXwidth = editorSnapshot.gridXwidth
    gridYwidth = editorSnapshot.gridYwidth
    manageropacity = editorSnapshot.manageropacity
    intersectMode = editorSnapshot.intersectMode
    dpaint:setAlpha(manageropacity)
    drawManagerGrid()
    drawButtons()
    view:invalidate()
  end)
  
  editorOptionsDialog.showDialog(editorValues)
  return

end

tpaint = luajava.new(PaintClass)
tpaint:setTextSize(15)
tpaint:setARGB(0xFF,0xAA,0xAA,0xAA)
tpaint:setAntiAlias(true)
--PorterDuff = luajava.bindClass("android.graphics.PorterDuff.Mode")

bpaint = luajava.new(PaintClass)
dpaint = luajava.new(PaintClass)

Paint = luajava.bindClass("android.graphics.Paint")
Color = luajava.bindClass("android.graphics.Color")
--dpaint:setStyle(Paint.Style.STROKE)
manageropacity = 255
dpaint:setARGB(manageropacity,0xFF,0x00,0x00)
--dpaint:setShadowLayer(6,0,0,Color.YELLOW)


Float = luajava.newInstance("java.lang.Float",0)
ten = luajava.newInstance("java.lang.Float",2)
FloatClass = Float:getClass()
rawfloatclass = FloatClass.TYPE
farray = Array:newInstance(rawfloatclass, 2)
Array:setFloat(farray,0,ten:floatValue())
Array:setFloat(farray,1,ten:floatValue())
dash = luajava.newInstance("android.graphics.DashPathEffect",farray,Float:floatValue())

Style = luajava.bindClass("android.graphics.Paint$Style")
dpaint:setStyle(Style.STROKE)

MAX_ACCORDION_CHILDREN = 5

local function accordionStackVertical(dir, layout)
	if layout == "vertical" then
		return true
	end
	if layout == "horizontal" then
		return false
	end
	return dir ~= "right" and dir ~= "left"
end

local function accordionChildCoords(parent, index, childW, childH)
	local dir = parent.data.accordionDirection
	local layout = parent.data.accordionChildLayout or "along"
	local gap = 3 * density
	local px = posX(parent.data)
	local py = posY(parent.data)
	local parentHalfW = (parent.data.width / 2) * density
	local parentHalfH = (parent.data.height / 2) * density
	local childHalfW = (childW / 2) * density
	local childHalfH = (childH / 2) * density
	local childStepV = childH * density + gap
	local childStepH = childW * density + gap
	local stackV = accordionStackVertical(dir, layout)
	local count = math.min(#parent.data.accordionChildren, MAX_ACCORDION_CHILDREN)
	local alongStep = stackV and childStepV or childStepH
	local alongOffset = (index - 1) * alongStep
	local crossOffset = 0
	if not stackV and (dir == "down" or dir == "up") then
		crossOffset = (index - 1) * childStepH - (count - 1) * childStepH * 0.5
	elseif stackV and (dir == "left" or dir == "right") then
		crossOffset = (index - 1) * childStepV - (count - 1) * childStepV * 0.5
	end
	if dir == "down" then
		if stackV then
			return px, py + parentHalfH + gap + childHalfH + alongOffset
		end
		return px + crossOffset, py + parentHalfH + gap + childHalfH
	elseif dir == "up" then
		if stackV then
			return px, py - parentHalfH - gap - childHalfH - alongOffset
		end
		return px + crossOffset, py - parentHalfH - gap - childHalfH
	elseif dir == "right" then
		if stackV then
			return px + parentHalfW + gap + childHalfW, py + crossOffset
		end
		return px + parentHalfW + gap + childHalfW + alongOffset, py
	elseif dir == "left" then
		if stackV then
			return px - parentHalfW - gap - childHalfW, py + crossOffset
		end
		return px - parentHalfW - gap - childHalfW - alongOffset, py
	end
	return px, py
end

function collapseAccordion(parent, skipRedraw)
	if parent == nil then
		return
	end
	parent.expanded = false
	parent.accordionOverlay = nil
	if not skipRedraw then
		drawButtons()
		view:invalidate()
	end
end

function collapseAllAccordions(skipRedraw)
	for i = 1, #buttons do
		if buttons[i].expanded then
			collapseAccordion(buttons[i], true)
		end
	end
	if not skipRedraw then
		drawButtons()
		view:invalidate()
	end
end

function buildAccordionOverlay(parent)
	local overlay = {}
	local childW = parent.data.width
	local childH = parent.data.height
	local count = math.min(#parent.data.accordionChildren, MAX_ACCORDION_CHILDREN)
	for i = 1, count do
		local child = parent.data.accordionChildren[i]
		local cx, cy = accordionChildCoords(parent, i, childW, childH)
		local childData = {
			x = cx,
			y = cy,
			width = childW,
			height = childH,
			label = child.label or ("+" .. i),
			command = child.command or "",
			primaryColor = parent.data.primaryColor,
			selectedColor = parent.data.selectedColor,
			labelColor = parent.data.labelColor,
			labelSize = parent.data.labelSize
		}
		local btn = BUTTON:new(childData, density)
		btn.isAccordionChild = true
		btn.accordionParent = parent
		table.insert(overlay, btn)
	end
	return overlay
end

function expandAccordion(parent, skipRedraw)
	collapseAllAccordions(true)
	parent.expanded = true
	parent.accordionOverlay = buildAccordionOverlay(parent)
	if not skipRedraw then
		drawButtons()
		view:invalidate()
	end
end

function isAccordionCloseHit(parent, x, y)
	if parent == nil or not parent.expanded or parent.data.accordionAutoClose ~= false then
		return false
	end
	local rect = parent.rect
	local closeSize = 14 * density
	local left = rect.left
	local top = rect.top
	return x >= left and x <= left + closeSize and y >= top and y <= top + closeSize
end

dpaint:setPathEffect(dash)
dpaint:setStrokeWidth(2)

--Style = luajava.bindClass("android.graphics.Paint$Style")
-- A "show only with keyboard" button is a keyboard assistant, so it has no
-- business sitting in the grid while there is no keyboard. It disappears from
-- the grid too, not just from the floating layer -- otherwise the grid copy
-- stays put and reads as the floating one refusing to hide.
--
-- Still drawn while editing: the player has to be able to find it to change it.
-- "Always visible" floaters are untouched and keep their grid copy.
function isKeyboardOnlyFloater(b)
	if manage == true then
		return false
	end
	local d = b ~= nil and b.data or nil
	if d == nil then
		return false
	end
	return d.floating == true and d.floatMode == "keyboard"
end

function drawButtons()
	local canvas = buttonCanvas
	if canvas == nil then return end
	height = view:getHeight()
	width = view:getWidth()
	if width <= 0 or height <= 0 then return end

	--canvas:clearCanvas()
	canvas:drawRect(0,0,width,height,cpaint)
	
	--if(manage) then
	--	canvas:drawBitmap(managerBitmap,0,0,nil)
	--end
	--local counter = 0
	for i=1,#buttons do
	--for i,b in pairs(buttons) do
		local b = buttons[i]
		----Note("DRAWING BUTTON"..i)
		if isKeyboardOnlyFloater(b) then
			-- skip: lives above the keyboard, or nowhere
		elseif(b.selected) then
			b:draw(1,canvas)
		else
			b:draw(0,canvas)
		end
		if b.expanded and b.accordionOverlay ~= nil then
			for j = 1, #b.accordionOverlay do
				local child = b.accordionOverlay[j]
				child:draw(0, canvas)
			end
		end
		--counter = counter + 1
	end
	--Note("DRAWING "..counter.." BUTTONS")
end

function drawButtonsNoSelected()
	local canvas = buttonCanvas
	height = view:getHeight()
	width = view:getWidth()

	--canvas:clearCanvas()
	canvas:drawRect(0,0,width,height,cpaint)
	
	--if(manage) then
	--	canvas:drawBitmap(managerBitmap,0,0,nil)
	--end

	for i,b in pairs(buttons) do
		if(b.selected ~= true and not isKeyboardOnlyFloater(b)) then
			b:draw(0,buttonCanvas)
		end
	end
end

function clearButton(b)
	local canvas = buttonCanvas
	local p = b.paintOpts
	p:setXfermode(xferModeClear)
	canvas:drawRoundRect(b.rect,5,5,b.paintOpts)
	p:setXfermode(nil)
	--c:drawBitmap(bmp,b.x,b.y,nil)
	--local tX = b.x - (tpaint:measureText(b.text)/2)
	--local tY = b.y + (tpaint:getTextSize()/2)
	--canvas:drawText(b.text,tX,tY,tpaint)
end


touchedbutton = {}
touchedindex = 0
MotionEvent = luajava.bindClass("android.view.MotionEvent")
ACTION_MOVE = MotionEvent.ACTION_MOVE
ACTION_DOWN = MotionEvent.ACTION_DOWN
ACTION_UP = MotionEvent.ACTION_UP
ACTION_CANCEL = MotionEvent.ACTION_CANCEL
ACTION_POINTER_DOWN = MotionEvent.ACTION_POINTER_DOWN
ACTION_POINTER_UP = MotionEvent.ACTION_POINTER_UP
prevevent = 0;

dragmoving = false
dragstart = {}
dragstart.x = -1
dragstart.y = -1

dragcurrent = {}
dragcurrent.x = -1
dragcurrent.y = -1

String = luajava.newInstance("java.lang.String")
StringClass = String:getClass()

editorItems = Array:newInstance(StringClass,4)
Array:set(editorItems,0,"Move")
Array:set(editorItems,1,"Edit")
Array:set(editorItems,2,"Copy")
Array:set(editorItems,3,"Delete")

-- With more than one button selected there is a fourth thing worth doing, and
-- it is the reason the selection was made in the first place: arranging them.
-- The same tools live in the editor settings sheet, which is where you go to
-- work on the whole set; this is the short way round when the buttons are
-- already picked and under your finger.
editorItemsMulti = Array:newInstance(StringClass,5)
Array:set(editorItemsMulti,0,"Move")
Array:set(editorItemsMulti,1,"Edit")
Array:set(editorItemsMulti,2,"Arrange...")
Array:set(editorItemsMulti,3,"Copy")
Array:set(editorItemsMulti,4,"Delete")

arrangeItems = Array:newInstance(StringClass,5)
Array:set(arrangeItems,0,"Line up  |   (one column)")
Array:set(arrangeItems,1,"Line up  --  (one row)")
Array:set(arrangeItems,2,"Spread into a column")
Array:set(arrangeItems,3,"Spread into a row")
Array:set(arrangeItems,4,"Spread into a block")

-- Marks our own clipboard content, so pasting from a shopping list does not
-- silently produce nothing while looking like it worked.
BUTTON_CLIP_MARKER = "BLOWTORCH-BUTTONS-1"

-- How long a press on empty grid has to last to mean paste rather than "make a
-- button here". Android's own long-press threshold, so it feels like every
-- other long press on the phone.
BUTTON_PASTE_LONG_PRESS_MS = 500

local function clipboardManager()
	if view == nil then
		return nil
	end
	return view:getContext():getSystemService(Context.CLIPBOARD_SERVICE)
end

--- Put the selected buttons on the system clipboard.
---
--- Own values only — `serialize` walks with pairs, so inherited defaults stay
--- inherited. Copying the resolved values instead would freeze this set's
--- factory defaults into whatever set the buttons are pasted into, the same
--- trap that made button sizes revert.
function copySelectedButtons()
	local payload = {}
	for i = 1, #buttons do
		local b = buttons[i]
		if b ~= nil and b.selected then
			payload[#payload + 1] = b.data
		end
	end
	if #payload == 0 then
		Note("\nNothing selected to copy.\n")
		return
	end
	local cm = clipboardManager()
	if cm == nil then
		Note("\nNo clipboard available.\n")
		return
	end
	local ClipData = luajava.bindClass("android.content.ClipData")
	local text = BUTTON_CLIP_MARKER .. "\n" .. serialize(payload)
	cm:setPrimaryClip(ClipData:newPlainText("BlowTorch buttons", text))
	Note("\nCopied " .. #payload .. " button(s). Long press an empty grid cell"
		.. " in another set to paste.\n")
end

--- Whatever coerceToText handed back, as a Lua string.
---
--- LuaJava converts a Java String to a Lua string on the way out, and a Lua
--- string has no toString method — calling it crashed the editor on the first
--- long press. A CharSequence that is not a String still arrives as an object
--- and does need the call, so both have to be handled.
local function asLuaString(value)
	if value == nil then
		return nil
	end
	if type(value) == "string" then
		return value
	end
	local ok, converted = pcall(function() return value:toString() end)
	if ok and type(converted) == "string" then
		return converted
	end
	return nil
end

--- The buttons on the clipboard, or nil when it holds something else.
---
--- Every step is inside a pcall: this runs from the touch handler on a long
--- press, so anything unexpected in the clipboard would take the whole button
--- editor down with it rather than simply meaning "nothing to paste".
local function buttonsOnClipboard()
	local ok, result = pcall(function()
		local cm = clipboardManager()
		if cm == nil then
			return nil
		end
		local clip = cm:getPrimaryClip()
		if clip == nil or clip:getItemCount() < 1 then
			return nil
		end
		local item = clip:getItemAt(0)
		if item == nil then
			return nil
		end
		local text = asLuaString(item:coerceToText(view:getContext()))
		if text == nil then
			return nil
		end
		-- Plain prefix compare, not a pattern. BLOWTORCH-BUTTONS-1 contains
		-- hyphens, and a hyphen is a lazy quantifier in a Lua pattern, so
		-- matching it as one silently found nothing — paste would never have
		-- recognised its own clipboard even once the crash was fixed.
		local head = BUTTON_CLIP_MARKER .. "\n"
		if text:sub(1, #head) ~= head then
			return nil
		end
		local loaded = loadSerialized(text:sub(#head + 1), "the copied buttons")
		if type(loaded) ~= "table" or #loaded == 0 then
			return nil
		end
		return loaded
	end)
	if not ok then
		return nil
	end
	return result
end

--- True when a long press would have something to paste.
function hasButtonsToPaste()
	return buttonsOnClipboard() ~= nil
end

--- Paste the clipboard's buttons, the block's top-left landing on (pX, pY).
---
--- Relative positions are kept, so a pasted cluster arrives in the shape it was
--- copied in rather than as a stack in one cell.
function pasteButtons(pX, pY)
	local payload = buttonsOnClipboard()
	if payload == nil then
		Note("\nNothing on the clipboard to paste. Select buttons and use Copy"
			.. " first.\n")
		return false
	end

	local minX, minY = nil, nil
	for i = 1, #payload do
		local d = payload[i]
		if d ~= nil and d.x ~= nil and d.y ~= nil then
			if minX == nil or d.x < minX then minX = d.x end
			if minY == nil or d.y < minY then minY = d.y end
		end
	end
	if minX == nil then
		return false
	end

	pushUndo()
	for i = 1, #buttons do
		if buttons[i] ~= nil and buttons[i].selected then
			buttons[i].selected = false
			updateSelected(buttons[i], false)
		end
	end

	local added = 0
	for i = 1, #payload do
		local d = payload[i]
		if d ~= nil and d.x ~= nil and d.y ~= nil then
			local copy = {}
			for k, v in pairs(d) do
				if type(v) == "table" then
					local inner = {}
					for ik, iv in pairs(v) do
						inner[ik] = iv
					end
					copy[k] = inner
				else
					copy[k] = v
				end
			end
			copy.x = pX + (d.x - minX)
			copy.y = pY + (d.y - minY)
			local newb = BUTTON:new(copy, density)
			newb.data.x, newb.data.y =
				clampLogicalPosition(newb.data.x, newb.data.y, newb)
			refreshRect(newb)
			table.insert(buttons, newb)
			newb.selected = true
			updateSelected(newb, true)
			added = added + 1
		end
	end

	drawButtons()
	view:invalidate()
	saveDefaultOptions()
	Note("\nPasted " .. added .. " button(s).\n")
	return true
end

function deleteSelectedButtons()
	pushUndo()
	local newbuttons = {}
	while(table.getn(buttons) > 0) do
		b = table.remove(buttons)
		-- not b.selected, rather than == false: a button whose flag was never
		-- set is not a selected button, and the old test dropped it.
		if(not b.selected) then
			table.insert(newbuttons,b)
		else
			b = nil
		end
	end
	-- In place: other modules hold a reference to this table, so handing them a
	-- different one leaves them looking at the buttons that were just deleted.
	for i = #buttons, 1, -1 do
		buttons[i] = nil
	end
	for i = #newbuttons, 1, -1 do
		buttons[#buttons + 1] = newbuttons[i]
	end
	drawButtons()
	view:invalidate()
end

arrangeListener = {}
function arrangeListener.onClick(dialog,which)
	pushUndo()
	if(which == 0) then
		alignSelectedButtons("x")
	elseif(which == 1) then
		alignSelectedButtons("y")
	elseif(which == 2) then
		spreadSelectedButtons("column")
	elseif(which == 3) then
		spreadSelectedButtons("row")
	elseif(which == 4) then
		spreadSelectedButtons("grid")
	end
end
arrangeListener_cb = luajava.createProxy("android.content.DialogInterface$OnClickListener",arrangeListener)

function showArrangeSelection(count)
	local build = luajava.newInstance("android.app.AlertDialog$Builder",view:getContext())
	build:setItems(arrangeItems,arrangeListener_cb)
	build:setTitle("Arrange " .. count .. " buttons")
	arrangeAlert = build:create()
	arrangeAlert:show()
end

editorListener = {}
function editorListener.onClick(dialog,which)
	-- Index order must match editorItems above: Move, Edit, Copy, Delete.
	if(which == 0) then
		enterMoveMode()
	elseif(which == 1) then
		showEditorDialog()
	elseif(which == 2) then
		copySelectedButtons()
	elseif(which == 3) then
		deleteSelectedButtons()
	end
end
editorListener_cb = luajava.createProxy("android.content.DialogInterface$OnClickListener",editorListener)

-- Its own listener rather than index arithmetic on the one above: Arrange sits
-- in the middle of the list, so sharing a listener would mean every entry after
-- it meaning two different things depending on how many buttons are selected.
editorListenerMulti = {}
function editorListenerMulti.onClick(dialog,which)
	if(which == 0) then
		enterMoveMode()
	elseif(which == 1) then
		showEditorDialog()
	elseif(which == 2) then
		showArrangeSelection(numediting)
	elseif(which == 3) then
		copySelectedButtons()
	elseif(which == 4) then
		deleteSelectedButtons()
	end
end
editorListenerMulti_cb = luajava.createProxy("android.content.DialogInterface$OnClickListener",editorListenerMulti)
numediting = 0
lastselectedinex = -1
function btprofReportHints(where)
	Note("\nBTPROF " .. where .. ": buttonShowHints=" .. tostring(buttonShowHints)
		.. " type=" .. type(buttonShowHints)
		.. " optRaw=" .. tostring(options ~= nil and options.show_gesture_hints or "no-options")
		.. "\n")
end

function showEditorSelection()
	local count = 0
	for i,b in ipairs(buttons) do
		if(b.selected == true) then
			count = count + 1
			lastselectedindex = i
		end
	end
	numediting = count
	local build = luajava.newInstance("android.app.AlertDialog$Builder",view:getContext())

	if count > 1 then
		build:setItems(editorItemsMulti,editorListenerMulti_cb)
		build:setTitle(count.." buttons selected.")
	else
		build:setItems(editorItems,editorListener_cb)
		build:setTitle("1 button selected.")
	end
	alert = build:create()
	alert:show()

end

counter = 0

function addButton(pX,pY) 
	local newb = BUTTON:new({x=pX,y=pY,label=""},density)
	pX, pY = clampLogicalPosition(pX, pY, newb)
	newb.data.x = pX
	newb.data.y = pY
	--newb.x = x
	--newb.y = y
	--next two lines seem to be messing with the defaults.
	--newb.data.width = defaults.width --(gridXwidth-5)/density
	--newb.data.height = defaults.height --(gridYwidth-5)/density
	--newb.data.label = "newb"..counter
	counter = counter+1
	--newb.rect = luajava.newInstance("android.graphics.RectF")
	--newb.paintOpts = luajava.new(PaintClass,paint)
	--newb.selected = false
	refreshRect(newb)
	table.insert(buttons,newb)
	return newb
end

function buttonTouched(x,y)
	for i=1,#buttons do
		local b = buttons[i]
		if b.expanded and b.accordionOverlay ~= nil then
			for j = #b.accordionOverlay, 1, -1 do
				local child = b.accordionOverlay[j]
				if child.rect:contains(x, y) then
					return true, child, i
				end
			end
			if isAccordionCloseHit(b, x, y) then
				return true, b, i
			end
		end
	end
	for i=1,#buttons do
	--for i,b in pairs(buttons) do
		local b = buttons[i]
		if not isKeyboardOnlyFloater(b) then
			local z = b.rect
			if(z:contains(x,y)) then
				return true,b,i
			end
		end
	end
	return false
end

function updateRect(b)
	left = b.x - (b.width/2)
	right = b.x + (b.width/2)
	top = b.y - (b.height/2) + statusoffset
	bottom = b.y + (b.height/2) + statusoffset
	tmp = b.rect
	tmp:set(left,top,right,bottom) 
end

buttonLayer = nil
buttonCanvas = nil
draw = false

Integer = luajava.bindClass("java.lang.Integer")
function OnSizeChanged(w,h,oldw,oldh)
	w = tonumber(w) or 0
	h = tonumber(h) or 0
	oldw = tonumber(oldw) or 0
	oldh = tonumber(oldh) or 0
	debugString("Button Window starting View.OnSizeChanged()")
	if w <= 0 or h <= 0 then
		draw = false
		return
	end

	refreshStatusOffset(true)
	local ccl = luajava.bindClass("android.graphics.Color")
	local colord = ccl:argb(0x88,0x00,0x00,0xFF)
	
	--Note("DebugString: "..string.format("%d,%s",colord,Integer:toHexString(colord)))
	
	--Note("Window Sized Changed:"..w.."x"..h)
	
	
	if(buttonLayer) then
		--Note("freeing button layer")
		buttonCanvas = nil
		buttonLayer:recycle()
		buttonLayer = nil
		
	end
	
	if(selectedLayer) then
		selectedCanvas = nil
		selectedLayer:recycle()
		selectedLayer = nil
		
	end
	
	collectgarbage("collect")

	if view:getWidth() <= 0 or view:getHeight() <= 0 then
		draw = false
		return
	end

	buttonLayer = Bitmap:createBitmap(view:getWidth(),view:getHeight(),BitmapConfig.ARGB_8888)
	buttonCanvas = luajava.newInstance("android.graphics.Canvas",buttonLayer)
	
	selectedLayer = Bitmap:createBitmap(view:getWidth(),view:getHeight(),BitmapConfig.ARGB_8888)
	selectedCanvas = luajava.newInstance("android.graphics.Canvas",selectedLayer)

	-- The edit grid is a bitmap too, and it was the one layer this function did
	-- not rebuild. Turning the phone while editing left the portrait-sized
	-- manager layer being drawn at 0,0 over a landscape canvas: the edit grid
	-- covered the left part of the screen and game text showed through the rest.
	if manage and drawManagerLayer then
		if managerLayer ~= nil then
			managerCanvas = nil
			managerLayer:recycle()
			managerLayer = nil
		end
		managerLayer = Bitmap:createBitmap(view:getWidth(),view:getHeight(),BitmapConfig.ARGB_8888)
		managerCanvas = luajava.newInstance("android.graphics.Canvas",managerLayer)
		gridXwidth = defaults.gridXwidth*density
		gridYwidth = defaults.gridYwidth*density
		drawManagerGrid()
	end

	positionRevertButton(w, h)

	-- Turning the phone drops the history. A snapshot taken in portrait carries
	-- portrait positions, and restoring it here would write them into the
	-- landscape pair -- a layout the player never chose, which is the one thing
	-- the orientation pairs exist to prevent.
	if manage and (w > h) ~= (oldw > oldh) then
		clearUndoHistory()
	end

	-- The view just changed size, so statusoffset may have moved under every
	-- button; none of these rects can be trusted.
	clampAllButtons(true)
	drawButtons()
	draw = true

	-- First real size: offer the layout wizard once if the profile is pending.
	if oldw == 0 and w > 0 then
		maybeOfferLayoutWizard()
	end
	
	debugString("Button Window ending View.onSizeChanged()")
end

dragDashPaint = luajava.new(PaintClass)
dragDashPaint:setARGB(0xFF,0x77,0x00,0x88)
dragDashPaint:setPathEffect(dash)
dragDashPaint:setStyle(Style.STROKE)
dragDashPaint:setStrokeWidth(7)

dragBoxPaint = luajava.new(PaintClass)
dragBoxPaint:setARGB(0x33,0x77,0x00,0x33)

function OnDraw(canvas)
	--canvas:save()
	--canvas:translate(0,statusoffset)

	-- Both layers are recycled and set to nil by OnDestroy, and a frame can
	-- still arrive before they are rebuilt - Reload settings does exactly that.
	-- drawBitmap on a nil layer threw out of OnDraw on every frame afterwards,
	-- which filled the window with the same NullPointerException. A frame with
	-- nothing to draw is a frame to skip, not an error to report.
	if(manage and drawManagerLayer and managerLayer ~= nil) then
		canvas:drawBitmap(managerLayer,0,0,nil)
	end

	if(draw and buttonLayer ~= nil) then
		canvas:drawBitmap(buttonLayer,0,0,nil)
	end
	
	if(dragmoving) then
		----Note("I SHOULD BE DRAG MOVING")
		startx = 0
		starty = 0
		endx = 0
		endy = 0
		
		if(dragstart.x < dragcurrent.x) then
			startx = dragstart.x	
			endx = dragcurrent.x	
		else
			startx = dragcurrent.x
			endx = dragstart.x
		end
		
		if(dragstart.y < dragcurrent.y) then
			starty = dragstart.y
			endy = dragcurrent.y		
		else
			starty = dragcurrent.y
			endy = dragstart.y
		end
		
		canvas:drawRect(startx,starty,endx,endy,dragBoxPaint)
		canvas:drawRect(startx,starty,endx,endy,dragDashPaint)
		
	end
	
	if(moveBitmap ~= nil) then
		canvas:drawBitmap(moveBitmap,moveBounds.left,moveBounds.top,nil)
	end
	
	--canvas:restore()
	
end



function OnDestroy()
	--Note("destroying button window")
	debugString("Button Window in View.OnDestroy()")
	if(managerLayer ~= nil) then
		managerLayer:recycle()
		managerLayer = nil
		managerCanvas = nil
	end
	--Note("freeing button layer")
	if(buttonLayer ~= nil) then
		--Note("recycle")
		buttonLayer:recycle()
		--Note("layer to nil")
		buttonLayer = nil
		--Note("canvas to nil")
		buttonCanvas = nil
	end
	-- Keep the flags with the layers they describe. Leaving draw true while the
	-- bitmap is gone is what made the next frame throw.
	draw = false
	drawManagerLayer = false
	--Note("finished destroying window")
end

TabHost = luajava.bindClass("android.widget.TabHost")
TabWidget = luajava.bindClass("android.widget.TabWidget")
RelativeLayout = luajava.bindClass("android.widget.RelativeLayout")
RelativeLayoutParams = luajava.bindClass("android.widget.RelativeLayout$LayoutParams")
android_R_id = luajava.bindClass("android.R$id")
TextView = luajava.bindClass("android.widget.TextView")
Gravity = luajava.bindClass("android.view.Gravity")
FrameLayout = luajava.bindClass("android.widget.FrameLayout")
LinearLayout = luajava.bindClass("android.widget.LinearLayout")
LinearLayoutParams = luajava.bindClass("android.widget.LinearLayout$LayoutParams")
Button = luajava.bindClass("android.widget.Button")
EditText = luajava.bindClass("android.widget.EditText")
View = luajava.bindClass("android.view.View")
Color = luajava.bindClass("android.graphics.Color")

GRAVITY_CENTER = Gravity.CENTER
FILL_PARENT = LinearLayoutParams.FILL_PARENT
WRAP_CONTENT = LinearLayoutParams.WRAP_CONTENT

--dialogView = nil

--[[BEGIN GLOBAL ENTRY POINT INTO buttonEditorDone
    This is called from the buttoneditor module to process the new button data
    Leave this global
]]
local function resolveButtonColor(chosen, defaultColor)
	if chosen == nil then
		return nil
	end
	if tonumber(chosen) == tonumber(defaultColor) then
		return nil
	end
	return chosen
end

function buttonEditorDone(data)
	--apply the settings out.
	
	if(numediting == 1) then
		local tmp = buttons[lastselectedindex]

		
		--Note("EDITING SINGLE BUTTON BEFORE BUTTON:"..tmp.data.height)
		--printTable("button",tmp)
		
		
		-- Typed a new position in the editor: the floating copy moves by the
		-- same amount, so the two do not drift apart, and the pair written is
		-- the one for the orientation the phone is in.
		shiftFloatPlacement(tmp,
			(tonumber(data.xCoord) or 0) - (tonumber(posX(tmp.data)) or 0),
			(tonumber(data.yCoord) or 0) - (tonumber(posY(tmp.data)) or 0))
		setPos(tmp, data.xCoord, data.yCoord)
		tmp.data.height = data.height
		tmp.data.width = data.width
		tmp.data.labelSize = data.labelSize
		
		tmp.data.primaryColor = resolveButtonColor(data.normalColor, defaults.primaryColor)
		tmp.data.flipColor = resolveButtonColor(data.flipColor, defaults.flipColor)
		tmp.data.selectedColor = resolveButtonColor(data.pressedColor, defaults.selectedColor)
		tmp.data.labelColor = resolveButtonColor(data.normalLabelColor, defaults.labelColor)
		tmp.data.flipLabelColor = resolveButtonColor(data.flipLabelColor, defaults.flipLabelColor)
		
		tmp.data.command = data.cmd
		tmp.data.label = data.label
		tmp.data.flipLabel = data.flipLabel
		tmp.data.flipCommand = data.flipCmd
		tmp.data.holdCommand = data.holdCommand or ""
		tmp.data.swipeUpCommand = data.swipeUpCommand or ""
		tmp.data.swipeDownCommand = data.swipeDownCommand or ""
		tmp.data.swipeLeftCommand = data.swipeLeftCommand or ""
		tmp.data.swipeRightCommand = data.swipeRightCommand or ""
		tmp.data.swipeUpLeftCommand = data.swipeUpLeftCommand or ""
		tmp.data.swipeUpRightCommand = data.swipeUpRightCommand or ""
		tmp.data.swipeDownLeftCommand = data.swipeDownLeftCommand or ""
		tmp.data.swipeDownRightCommand = data.swipeDownRightCommand or ""
		tmp.data.showGestureLabel = data.showGestureLabel ~= false
		
		tmp.data.accordionDirection = data.accordionDirection or ""
		tmp.data.accordionChildren = data.accordionChildren or {}
		tmp.data.accordionTrigger = data.accordionTrigger or "tap"
		tmp.data.accordionHoldMs = tonumber(data.accordionHoldMs) or 450
		tmp.data.accordionChildLayout = data.accordionChildLayout or "along"
		if data.accordionAutoClose == nil then
			tmp.data.accordionAutoClose = true
		else
			tmp.data.accordionAutoClose = data.accordionAutoClose
		end
		
		tmp.data.name = data.name
		tmp.data.switchTo = data.target

		tmp.data.floating = data.floating == true
		local floatMode = data.floatMode
		if floatMode ~= "keyboard" then
			floatMode = "always"
		end
		tmp.data.floatMode = floatMode
		tmp.data.floatRound = data.floatRound == true
		tmp.data.floatFrame = data.floatFrame == true
		if enforceNoAccordionOnSuperButton(tmp) then
			Note("BlowTorch: an accordion cannot work from a super button —"
				.. " it is drawn on the button grid and its children only exist"
				.. " while it is open. The accordion was removed. Untick"
				.. " 'Super button' if you want the accordion instead.")
		end
		
		refreshRect(tmp)
		--Note("EDITING SINGLE BUTTON AFTER BUTTON:"..tmp.data.height)
		--printTable("edited",tmp)
		
	elseif(numediting > 1) then
		for i,b in ipairs(buttons) do
			if(b.selected == true) then
				--do the settings update for relevent data
				if(data.width ~= nil and data.width ~= editorValues.width) then
					b.data.width = data.width
				end
				
				if(data.height ~= nil and data.height ~= editorValues.height) then
					b.data.height = data.height
				end
				
				-- Multi-edit: coordinates go to the pair for this orientation,
				-- same as every other deliberate move.
				if(data.xCoord ~= nil and data.xCoord ~= editorValues.x) then
					setPos(b, data.xCoord, posY(b.data))
				end
				
				if(data.yCoord ~= nil and data.yCoord ~= editorValues.y) then
					setPos(b, posX(b.data), data.yCoord)
				end
				
				if(data.labelSize ~= nil and data.labelSize ~= editorValues.labelSize) then
					b.data.labelSize = data.labelSize
				end
				
				if(data.normalColor ~= editorValues.primaryColor) then
					b.data.primaryColor = resolveButtonColor(data.normalColor, defaults.primaryColor)
				end
				
				if(data.pressedColor ~= editorValues.selectedColor) then
					b.data.selectedColor = resolveButtonColor(data.pressedColor, defaults.selectedColor)
				end
				
				if(data.flipColor ~= editorValues.flipColor) then
					b.data.flipColor = resolveButtonColor(data.flipColor, defaults.flipColor)
				end
				
				if(data.normalLabelColor ~= editorValues.labelColor) then
					b.data.labelColor = resolveButtonColor(data.normalLabelColor, defaults.labelColor)
				end
				
				if(data.flipLabelColor ~= editorValues.flipLabelColor) then
					b.data.flipLabelColor = resolveButtonColor(data.flipLabelColor, defaults.flipLabelColor)
				end
				
				refreshRect(b)
			end
		end
	end
	
	drawButtons()
	view:invalidate()
	notifyFloatingButtonsChanged()
end
--[[END buttonEditorDone global callback]]
normalColor = nil
flipColor = nil
pressedColor = nil
normalLabelColor = nil
flipLabelColor = nil

editorValues = {}

tabMinHeight = (35 * density) -- dp value TODO
bgGrey = Color:argb(255,0x99,0x99,0x99) -- background color

textSizeBig = (18) -- sp value
textSize = (14)  
textSizeSmall = (10) 
-- Note("Density: " .. density ..", TextSize: "..textSize .. "textSizeSmall: ".. textSizeSmall)
screenlayout = view:getContext():getResources():getConfiguration().screenLayout
local test = bit.band(screenlayout,Configuration.SCREENLAYOUT_SIZE_MASK)

local function foo()
	--Note(test)
	--Note(Configuration.SCREENLAYOUT_SIZE_XLARGE)
	--Note("Entering the foo()"..test)
	if(test == Configuration.SCREENLAYOUT_SIZE_XLARGE) then
		textSizeBig = (22)
		textSize = (18)
		textSizeSmall = (14)
	end
end
pcall(foo)




function showEditorDialog()
	--make the parent view.
	--local button = nil
	editorValues = {}
	--if(dialogView == nil) then
	if(numediting == 1) then
		button = buttons[lastselectedindex]
		editorValues.label = button.data.label
		editorValues.command = button.data.command
		editorValues.flipLabel = button.data.flipLabel
		editorValues.flipCommand = button.data.flipCommand
		editorValues.holdCommand = button.data.holdCommand or ""
		editorValues.swipeUpCommand = button.data.swipeUpCommand or ""
		editorValues.swipeDownCommand = button.data.swipeDownCommand or ""
		editorValues.swipeLeftCommand = button.data.swipeLeftCommand or ""
		editorValues.swipeRightCommand = button.data.swipeRightCommand or ""
		editorValues.swipeUpLeftCommand = button.data.swipeUpLeftCommand or ""
		editorValues.swipeUpRightCommand = button.data.swipeUpRightCommand or ""
		editorValues.swipeDownLeftCommand = button.data.swipeDownLeftCommand or ""
		editorValues.swipeDownRightCommand = button.data.swipeDownRightCommand or ""
		editorValues.showGestureLabel = button.data.showGestureLabel ~= false
		editorValues.accordionDirection = button.data.accordionDirection or ""
		editorValues.accordionChildren = button.data.accordionChildren or {}
		editorValues.accordionTrigger = button.data.accordionTrigger or "tap"
		editorValues.accordionHoldMs = button.data.accordionHoldMs or 450
		editorValues.accordionChildLayout = button.data.accordionChildLayout or "along"
		editorValues.accordionAutoClose = button.data.accordionAutoClose
		if editorValues.accordionAutoClose == nil then
			editorValues.accordionAutoClose = true
		end
		editorValues.name = button.data.name
		--editorValues.name = "OMGANYTHING"
		if(not editorValues.name) then editorValues.name = "" end
		editorValues.primaryColor = button.data.primaryColor
		editorValues.labelColor = button.data.labelColor
		editorValues.selectedColor = button.data.selectedColor
		editorValues.flipColor = button.data.flipColor
		editorValues.flipLabelColor = button.data.flipLabelColor
		editorValues.height = button.data.height
		editorValues.switchTo = button.data.switchTo
		editorValues.width = button.data.width
		
		editorValues.labelSize = button.data.labelSize
		-- The editor shows and edits the position for the orientation you are
		-- holding the phone in.
		editorValues.x = posX(button.data)
		editorValues.y = posY(button.data)
		editorValues.floating = button.data.floating == true
		editorValues.floatMode = button.data.floatMode or "always"
		if editorValues.floatMode ~= "keyboard" then
			editorValues.floatMode = "always"
		end
		editorValues.floatRound = button.data.floatRound == true
		editorValues.floatFrame = button.data.floatFrame == true
		--Note("single editor loading:"..editorValues.x)
		--Note("single editor loading:"..editorValues.y)
	else 
		for i,b in pairs(buttons) do
			if(b.selected == true) then
				--start comparing values
				if(editorValues.primaryColor ~= b.data.primaryColor) then
					editorValues.primaryColor = b.data.primaryColor
				end
			
				if(editorValues.labelColor ~= b.data.labelColor) then
					editorValues.labelColor = b.data.labelColor
				end
				
				if(editorValues.selectedColor ~= b.data.selectedColor) then
					editorValues.selectedColor = b.data.selectedColor
				end
				
				if(editorValues.flipColor ~= b.data.flipColor) then
					editorValues.flipColor = b.data.flipColor
				end
				
				if(editorValues.flipLabelColor ~= b.data.flipLabelColor) then
					editorValues.flipLabelColor = b.data.flipLabelColor
				end
				
				if(editorValues.labelSize == nil) then
					editorValues.labelSize = tonumber(b.data.labelSize)
				elseif(editorValues.labelSize ~= tonumber(b.data.labelSize)) then
					editorValues.labelSize = "MULTI"
				end
				
				if(editorValues.height == nil) then
					editorValues.height = tonumber(b.data.height)
				elseif(editorValues.height ~= tonumber(b.data.height)) then
					editorValues.height = "MULTI"
				end
				
				if(editorValues.width == nil) then
					editorValues.width = tonumber(b.data.width)
					--Note("editorValue set to "..b.data.width)
				elseif(editorValues.width ~= tonumber(b.data.width)) then
					editorValues.width = "MULTI"
					--Note("editorValue set to multi because "..b.data.width)
				end
				
				if(editorValues.x == nil) then
					editorValues.x = tonumber(posX(b.data))
				elseif(editorValues.x ~= tonumber(posX(b.data))) then
					editorValues.x = "MULTI"
				end
				
				if(editorValues.y == nil) then
					editorValues.y = tonumber(posY(b.data))
				elseif(editorValues.y ~= tonumber(posY(b.data))) then
					editorValues.y = "MULTI"
				end
			end
		end
		-- Float fields are single-button only (like gestures/accordion).
		editorValues.floating = false
		editorValues.floatMode = "always"
		editorValues.floatRound = false
		editorValues.floatFrame = false
	end
	
	editorValues.defaultPrimaryColor = defaults.primaryColor
	editorValues.defaultSelectedColor = defaults.selectedColor
	editorValues.defaultFlipColor = defaults.flipColor
	editorValues.defaultLabelColor = defaults.labelColor
	editorValues.defaultFlipLabelColor = defaults.flipLabelColor
	editorValues.showGestureHints = buttonShowHints ~= false and buttonShowHints ~= "false"
		and buttonShowHints ~= 0 and buttonShowHints ~= "0"
	editorValues.showSwipePreview = buttonShowSwipePreview ~= false
		and buttonShowSwipePreview ~= "false"
		and buttonShowSwipePreview ~= 0 and buttonShowSwipePreview ~= "0"
	
 local buttonEditor = require("buttoneditor")
 buttonEditor.init(mContext)
 --Note("showing button editor "..numediting)
 buttonEditor.showEditorDialog(editorValues,numediting)
 return
end

modifyButtonSet = function(entry) 
  --Note("In Modify button set callback.")
  if(buttonsCleared) then
    revertButtons()
  end
  if(entry.name ~= lastLoadedSet) then
    PluginXCallS("loadAndEditSet",entry.name)
    return
  end

  enterManagerMode()
  showeditormenu = true
  PushMenuStack("onEditorBackPressed")
end

function loadAndEditSet(data)
	--Note("Loading and editing: "..data)
	-- Skip the play-mode notify from loadButtons; enterManagerMode hides floaters.
	suppressFloatingNotify = true
	local ok, loaded = pcall(loadButtons, data)
	suppressFloatingNotify = false
	if not ok then
		error(loaded)
	end
	if loaded == false then
		-- The payload did not decode, so `buttons` is still the previously
		-- loaded set while the server has already moved current_set on. Opening
		-- the editor here would let a Done write the old set's buttons over the
		-- set the player asked to edit. loadButtons has already said what
		-- happened.
		return
	end
	enterManagerMode()
	showeditormenu = true
	PushMenuStack("onEditorBackPressed")
	if(buttonSetListDialog ~= nil) then
	 buttonSetListDialog.dismissList()
	end
end

--delete after testing
setSettingsButtonListener = {}
function setSettingsButtonListener.onClick(v)

  local editorValues = {}
  editorValues.primaryColor = defaults.primaryColor
  editorValues.selectedColor = defaults.selectedColor
  editorValues.flipColor = defaults.flipColor
  editorValues.labelColor = defaults.labelColor
  editorValues.flipLabelColor = defaults.flipLabelColor
  editorValues.switchTo = ""
  editorValues.height = defaults.height
  editorValues.width = defaults.width
  editorValues.labelSize = defaults.labelSize
  editorValues.name = lastLoadedSet
  editorValues.x = 0
  editorValues.y = 0  
  
  local editorOptionsDialog = require("editoroptionsdialog")
  editorOptionsDialog.init(mContext)
  editorOptionsDialog.setEditorDoneCallback()
  editorOptionsDialog.showDialog(editorValues)  

end
setSettingsButton_cb = luajava.createProxy("android.view.View$OnClickListener",setSettingsButtonListener)

--keep below for handling the data coming back from the advanced editor
setEditorCancelListener = {}
function setEditorCancelListener.onClick(v)
	buttSetSettingsEditor:dismiss()
end
seteditorCancel_cb = luajava.createProxy("android.view.View$OnClickListener",setEditorCancelListener)

--delete after testing
setEditorDoneListener = {}
function setEditorDoneListener.onClick(v)
	--apply the settings.
	local str = Validator:validate()
	if(str ~= nil) then
		Validator:showMessage(view:getContext(),str)
		return
	end
	
	
	labelsizetmp = labelSizeEdit:getText()
	labelsize = tonumber(labelsizetmp:toString())
	----Note(
	heighttmp = heightEdit:getText()
	
	height = tonumber(heighttmp:toString())
	--Note("height read from editor"..height)
	widthtmp = widthEdit:getText()
	width = tonumber(widthtmp:toString())
	
	--first strip any settings that match the current default.
	for i,b in pairs(buttons) do
		if rawget(b.data,"primaryColor") == defaults.primaryColor then
			rawset(b.data,"primaryColor",nil)
		end
		
		if rawget(b.data,"selectedColor") == defaults.selectedColor then
			rawset(b.data,"selectedColor",nil)
		end
		
		if rawget(b.data,"flipColor") == defaults.flipColor then
			rawset(b.data,"flipColor",nil)
		end
		
		if rawget(b.data,"labelColor") == defaults.labelColor then
			rawset(b.data,"labelColor",nil)
		end
		
		if rawget(b.data,"flipLabelColor") == defaults.flipLabelColor then
			rawset(b.data,"flipLabelColor",nil)
		end
		
		if rawget(b.data,"height") == defaults.height then
			rawset(b.data,"height",nil)
		end
		
		if rawget(b.data,"width") == defaults.width then
			rawset(b.data,"width",nil)
		end
		
		if rawget(b.data,"labelSize") == defaults.labelSize then
			rawset(b.data,"labelSize",nil)
		end
	end
	
	defaults.primaryColor = theNormalColor
	defaults.selectedColor = thePressedColor
	defaults.flipColor = theFlipColor
	defaults.labelColor = theLabelColor
	defaults.flipLabelColor = theFlipLabelColor
	defaults.height = height
	defaults.width = width
	defaults.labelSize = labelsize
	--defaults.gridYwidth = height --* density
	--defaults.gridXwidth = width --* density
		
	--sbX:setProgress((gridXwidth/density)-32)
	--sbY:setProgress((gridYwidth/density)-32)
	
	buttSetSettingsEditor:dismiss()
	
	saveDefaultOptions()
	
	--Note("gridXwidth:" .. gridXwidth)
--	local tmp = {}
--	for i,b in pairs(buttons) do
--		tmp[i] = b.data
--	end
--		
--	PluginXCallS("saveButtons",serialize(tmp))
--	
--	PluginXCallS("saveSetDefaults",serialize(defaults))
--	
--	drawButtons()
end
seteditorDone_cb = luajava.createProxy("android.view.View$OnClickListener",setEditorDoneListener)

function saveDefaultOptions()
	local tmp = {}
	for i,b in pairs(buttons) do
		tmp[i] = b.data
	end
		
	PluginXCallS("saveButtons",serialize(tmp))
	
	PluginXCallS("saveSetDefaults",serialize(defaults))
	
	drawButtons()
end

function loadOptions(data)
	--Note("incoming options wad:"..data)
	local loaded = loadSerialized(data, "the button options")
	if(loaded == nil) then
		-- Nothing else in this file initialises `options`, so leaving it nil
		-- would move the failure to the first performHapticPress.
		options = options or {}
		buttonWindowOptions = options
		return
	end
	local prevPending = false
	if buttonWindowOptions ~= nil then
		prevPending = layoutPendingTruthy(buttonWindowOptions.layout_wizard_pending)
	end
	options = loaded
	buttonWindowOptions = loaded
	-- Any load that says "still pending" re-arms the auto-offer, so a second
	-- brand new MUD opened in the same session gets the wizard too — the latch
	-- is a duplicate-dialog guard, not a once-per-app-run switch. Never re-arm
	-- while a prompt or the wizard itself is on screen, or answering one would
	-- summon the next.
	if layoutPendingTruthy(loaded.layout_wizard_pending)
		and layoutWizardSoftPrompt == nil
		and not layoutWizardDialogOpen then
		layoutWizardOffered = false
	end
	-- Close / Not now / cancel all clear pending on the service, and that lands
	-- back here: the wizard is demonstrably gone, so drop the flag.
	if not layoutPendingTruthy(loaded.layout_wizard_pending) then
		layoutWizardDialogOpen = false
	end
	-- 6 is the default declared in default_settings_*.xml (key "roundess").
	buttonRoundness = (tonumber(options.roundness) or 6) * density
	-- nil counts as on, the same way buttonOptions reads it for the checkbox:
	-- settings saved before this option existed should still get the badges
	-- rather than silently starting switched off.
	buttonShowHints = options.show_gesture_hints == nil
		or options.show_gesture_hints == true
		or options.show_gesture_hints == "true"
		or options.show_gesture_hints == "1"
	-- The plugin's Lua runs in this process, so hand the bindings straight to the
	-- chrome listeners instead of routing them back out through the service.
	pcall(function()
		local ChromeGesturesClass =
			luajava.bindClass("com.resurrection.blowtorch2.lib.window.ChromeGestures")
		ChromeGesturesClass:publish(options.chrome_gestures or "")
	end)
	buttonShowSwipePreview = options.show_swipe_preview == nil
		or options.show_swipe_preview == true
		or options.show_swipe_preview == "true"
		or options.show_swipe_preview == "1"
	Note("\nBTPROF loadOptions: raw=" .. tostring(options.show_gesture_hints)
		.. " buttonShowHints=" .. tostring(buttonShowHints) .. "\n")
	--Note("options loaded, roundess="..buttonRoundness)
	--clearButtons()
	drawButtons()
	view:invalidate()
	-- First-run offer if options arrived after the view already has a size.
	maybeOfferLayoutWizard()
	--Note("loaded button options:"..options.auto_edit)
end

-- buttonserver.lua initialises the haptic options to numbers (0/1/2) and
-- Plugin.pushOptionsToLua replays persisted values with pushString, so the same
-- field is a number before the settings push and a string after it. Compare
-- both forms, the way the show_gesture_hints / show_swipe_preview reads above
-- already do.
local function hapticIs(value, n)
	return value == n or value == tostring(n)
end

function performHapticPress()
	--Note("performing haptic press")
	if(hapticIs(options.haptic_press, 2)) then return end

	flags = 1
	if(hapticIs(options.haptic_press, 1)) then
	--Note("overriding system")
		flags = 3
	end

	view:performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,flags)
end

function performHapticFlip()
	--Note("performing haptic flip")
	if(hapticIs(options.haptic_flip, 2)) then return end

	flags = 1
	if(hapticIs(options.haptic_flip, 1)) then
	--Note("overriding system")
		flags = 3
	end

	view:performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,flags)
end

function performHapticEdit()
	if(hapticIs(options.haptic_edit, 2)) then return end

	flags = 1
	if(hapticIs(options.haptic_edit, 1)) then
		flags = 3
	end

	view:performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,flags)
end

buttonsCleared = false
clearSet = {}
revertButtonData = {}
revertButtonData.label = "BACK"
revertButtonData.width = 70 --this unit is in dips, density is applied later
revertButtonData.height = 40 --same here.
revertButtonData.x = 100
revertButtonData.y = 100
revertButton = BUTTON:new(revertButtonData,density)
revertButtonSet = {}
revertButtonSet[1] = revertButton

-- Place BACK in the upper pad area (same band as starter buttons), not bottom-edge.
function positionRevertButton(w, h)
	w = tonumber(w) or (view and view:getWidth()) or 0
	h = tonumber(h) or (view and view:getHeight()) or 0
	if w <= 0 or h <= 0 then
		return
	end
	local dens = density or GetDisplayDensity() or 1
	local ab = tonumber(GetActionBarHeight()) or statusoffset or 0
	local topPad = 88 * dens
	local halfH = (revertButtonData.height / 2) * dens
	revertButtonData.x = w / 2
	revertButtonData.y = ab + topPad + halfH
	local minY = halfH
	local maxY = h - statusoffset - halfH
	if minY > maxY then
		minY = halfH
		maxY = h - halfH
	end
	if revertButtonData.y < minY then revertButtonData.y = minY end
	if revertButtonData.y > maxY then revertButtonData.y = maxY end
	revertButton:updateRect(statusoffset)
end

function clearButtons()
	if(buttonsCleared) then return end
	buttonsCleared = true
	revertset = buttons
	positionRevertButton()
	buttons = revertButtonSet
	drawButtons()
	suppress_editor = true
	view:invalidate()
	-- See revertButtons: the layer mirrors `buttons`, so a swap it is not told
	-- about leaves floaters on screen that no longer belong to the set.
	notifyFloatingButtonsChanged()
end


function emptyButtons()
	buttons = {}
	drawButtons()
	view:invalidate()
	notifyFloatingButtonsChanged()
end

-- Every replacement of `buttons` has to tell the floating layer, because the
-- layer mirrors this table and Java has no other way to know it changed.
--
-- This is what made keyboard-mode floaters vanish for good after a trip to
-- another app: MainWindow.onPause calls clearButtons (buttons becomes the
-- single BACK button), then onResume asks Lua to re-push the floaters
-- *before* it calls restoreButtons — so the push described the cleared set,
-- an empty list, and reverting afterwards said nothing. The floaters stayed
-- gone until some other path notified, which is why opening the button editor
-- and closing it brought them back.
function revertButtons()
	if(not buttonsCleared) then
		return
	end
	buttonsCleared = false
	buttons = revertset
	drawButtons()
	suppress_editor = false
	view:invalidate()
	notifyFloatingButtonsChanged()
end

function restoreButtons()
	if(buttonsCleared) then
		revertButtons()
	else
		drawButtons()
		view:invalidate()
	end
end

rootHolder = view:getParent()
mainwindow = rootHolder:findViewById(6666)


showeditormenu = false
editmenu = nil
topMenuItem = nil
function PopulateMenu(menu)
	--debugPrint("in options menu populate")

		-- During button edit, Settings / Done / Cancel live on the FAB strip.
		-- Overflow hides ⋮ while editing; when shown, ListPopupWindow now invokes
		-- MenuItem click listeners (see MainWindow.showGameplayOptionsMenuNow).
		if(showeditormenu) then
			return
		end
		
	--if(topMenuItem == nil) then
		topMenuItem = menu:add(0,401,401,props.label)

		topMenuItem:setIcon(R_drawable.ic_menu_button_sets)
		topMenuItem:setOnMenuItemClickListener(buttonsetMenuClicked_cb)
		
		--Note("populated lua button sets")
		foo = function(item) item:setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER) end
		if(not pcall(foo,topMenuItem))  then
			--Note("action bar not supported,android version < 3.0")
		end

	--else
	--	menu:add(topMenuItem)
	--end
end

buttonsetMenuClicked = {}
function buttonsetMenuClicked.onMenuItemClick(item)
	buttonList()
	return true
end
buttonsetMenuClicked_cb = luajava.createProxy("android.view.MenuItem$OnMenuItemClickListener",buttonsetMenuClicked)

-- AddOptionCallback("buttonList", ...) and overflow case 401 call this.
function buttonList()
	PluginXCallS("getButtonSetList","all")
end

buttonsetMenuDoneClicked = {}
function buttonsetMenuDoneClicked.onMenuItemClick(item)
	showeditormenu = false
	if(moveBitmap ~= nil) then
		exitMoveMode()
	end
	exitManagerMode()
	PopMenuStack()
	return true
end
buttonsetMenuDoneClicked_cb = luajava.createProxy("android.view.MenuItem$OnMenuItemClickListener",buttonsetMenuDoneClicked)

buttonsetSettingsClicked = {}
function buttonsetSettingsClicked.onMenuItemClick(item)
	xpcall(buttonOptions,function(error) if error ~= nil then Note(error) end end) 
	
	
	return true
end
buttonsetSettingsClicked_cb = luajava.createProxy("android.view.MenuItem$OnMenuItemClickListener",buttonsetSettingsClicked)

buttonsetCancelClicked = {}
function buttonsetCancelClicked.onMenuItemClick(item)
	showeditormenu = false
	PopMenuStack()
	if(moveBitmap ~= nil) then
		exitMoveMode()
	end
	exitManagerModeNoSave()
	return true
end
buttonsetCancelClicked_cb = luajava.createProxy("android.view.MenuItem$OnMenuItemClickListener",buttonsetCancelClicked)

resources = view:getContext():getResources()
function resLoader(root,bmp)
	local target = nil
	local metrics = resources:getDisplayMetrics()
	local d = metrics.density
	if(d < 1.0) then
		target = luajava.newInstance("android.graphics.drawable.BitmapDrawable",resources,root.."/ldpi/"..bmp)
	elseif(d >= 1.0 and d < 1.5) then
		target = luajava.newInstance("android.graphics.drawable.BitmapDrawable",resources,root.."/mdpi/"..bmp)
	elseif(d >= 1.5) then
		target = luajava.newInstance("android.graphics.drawable.BitmapDrawable",resources,root.."/hdpi/"..bmp)
	end
	
	local bmp = target:getBitmap()
	local Bitmap = luajava.bindClass("android.graphics.Bitmap")
	--scale to a bitmap of the appropriate size 40x40 dip? ish
	local resize = luajava.newInstance("android.graphics.drawable.BitmapDrawable",resources,Bitmap:createScaledBitmap(bmp,40*density,40*density,true))
	
	
	return resize
end

function onEditorBackPressed()
	showeditormenu = false
	PopMenuStack()
	exitManagerModeNoSave()
end

view:bringToFront()

function setDebug(off)
	if(off == "on") then
		debugString("Button window entering debug mode...")
		--WindowXCallS("button_window","setDebug","on")
		debugInfo = true
	else
		debugString("Button window debug mode...")
		--WindowXCallS("button_window","setDebug","off")
		debugInfo = false
	end
end

local importDialogClick = {}
function importDialogClick.onClick(dialog,which)
	local DialogInterface = luajava.bindClass("android.content.DialogInterface")
	
	if(which == DialogInterface.BUTTON_POSITIVE) then
		PluginXCallS("doImport","blank")
		dialog:dismiss()
		CloseOptionsDialog()
	else
		dialog:dismiss()
	end
end

local importDialogClick_cb = luajava.createProxy("android.content.DialogInterface$OnClickListener",importDialogClick)

local dismissDialog = {}
function dismissDialog.onClick(dialog,which)
	dialog:dismiss()
end
local dismissDialog_cb = luajava.createProxy("android.content.DialogInterface$OnClickListener",dismissDialog)

function failImport(str)
	local build = luajava.newInstance("android.app.AlertDialog$Builder",view:getContext())
	
	build:setPositiveButton("Ok",dismissDialog_cb)
	--build:setNegativeButton("No",importDialogClick_cb);
	build:setTitle("Import Failed")
	build:setMessage(str)
	alert = build:create()
	alert:show()
end

function importSuccess(str)
	local build = luajava.newInstance("android.app.AlertDialog$Builder",view:getContext())
	
	build:setPositiveButton("Ok",dismissDialog_cb)
	--build:setNegativeButton("No",importDialogClick_cb);
	build:setTitle("Import Complete")
	build:setMessage(str.." Buttons imported")
	alert = build:create()
	alert:show()
end

function askImport()
	local build = luajava.newInstance("android.app.AlertDialog$Builder",view:getContext())
	
	build:setPositiveButton("Yes",importDialogClick_cb)
	build:setNegativeButton("No",importDialogClick_cb);
	build:setTitle("Import Buttons?")
	build:setMessage("Import buttons from internal settings?")
	alert = build:create()
	alert:show()
end

PluginXCallS("buttonLayerReady","")
debugString("Button Window Script Loaded")
