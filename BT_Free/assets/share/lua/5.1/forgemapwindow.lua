local props = require("forgemapconfig")
local config = require("forgemap.config")
local store = require("forgemap.store")
local renderer = require("forgemap.renderer")
local compass = require("forgemap.compass")

local pluginName = props.plugin

PaintClass = luajava.bindClass("android.graphics.Paint")
ColorClass = luajava.bindClass("android.graphics.Color")
MotionEvent = luajava.bindClass("android.view.MotionEvent")
AlertDialog = luajava.bindClass("android.app.AlertDialog")
EditText = luajava.bindClass("android.widget.EditText")
LinearLayout = luajava.bindClass("android.widget.LinearLayout")
LinearLayoutParams = luajava.bindClass("android.widget.LinearLayout$LayoutParams")
TextView = luajava.bindClass("android.widget.TextView")
ScrollView = luajava.bindClass("android.widget.ScrollView")
Button = luajava.bindClass("android.widget.Button")
Toast = luajava.bindClass("android.widget.Toast")
Gravity = luajava.bindClass("android.view.Gravity")

local density = GetDisplayDensity()
local paint = luajava.new(PaintClass)
paint:setAntiAlias(true)

local currentView = nil
local compassLayout = nil
local mapMode = "minimap"
local compassMode = "walk"
local visible = false
local longPressAt = 0
local touchStartX, touchStartY = 0, 0
local touchStartTime = 0
local isPanning = false
local panDx, panDy = 0, 0
local zoomScale = 1.0
local stripHeightPx = math.floor(config.STRIP_HEIGHT_DP * density)
local longPressMs = 450
local chromeTopPx = 0

local function updateChromeInsets()
	chromeTopPx = tonumber(GetStatusBarHeight()) or 0
end

local function headerHeightPx()
	return math.floor(config.HEADER_DP * density) + chromeTopPx
end

local function dirFromCell(cell)
	if cell == nil then return nil end
	if cell.dy < 0 and cell.dx < 0 then return "nw"
	elseif cell.dy < 0 and cell.dx == 0 then return "n"
	elseif cell.dy < 0 and cell.dx > 0 then return "ne"
	elseif cell.dy > 0 and cell.dx < 0 then return "sw"
	elseif cell.dy > 0 and cell.dx == 0 then return "s"
	elseif cell.dy > 0 and cell.dx > 0 then return "se"
	elseif cell.dx < 0 then return "w"
	elseif cell.dx > 0 then return "e" end
	return nil
end

local function isAdjacent(cell)
	return cell ~= nil and math.max(math.abs(cell.dx), math.abs(cell.dy)) == 1
end

local function raiseMap()
	view:bringToFront()
	view:setClickable(true)
	view:setScrollingEnabled(false)
	view:setBufferText(false)
	view:clearText()
	view:setBackgroundColor(ColorClass:parseColor("#0A0A10"))
end

local function refreshView()
	if view == nil then return end
	local w, h = view:getWidth(), view:getHeight()
	if w <= 0 or h <= 0 then return end
	updateChromeInsets()
	local headerPx = headerHeightPx()
	compassLayout = compass.computeLayout(w, h, headerPx, density)
	local ui = store.getUi()
	currentView = renderer.computeView(store.getState(), {
		tileSizeDp = (ui.tileSizeDp or config.TILE_SIZE_DP) * zoomScale,
		maxGrid = mapMode == "full" and 11 or config.MINIMAP_TILES,
		density = density,
		viewWidth = w,
		mapWidth = compassLayout.mapWidth,
		viewHeight = h,
		headerDp = config.HEADER_DP,
		headerExtraPx = chromeTopPx,
		panDx = panDx,
		panDy = panDy,
	})
	currentView.width = w
	currentView.height = h
end

local function applyWindowLayout()
	if visible then
		view:setVisibility(0)
		view:setHeight(stripHeightPx)
	else
		view:setVisibility(8)
		view:setHeight(0)
	end
	view:requestLayout()
	local parent = view:getParent()
	if parent ~= nil then parent:requestLayout() end
end

local function redraw()
	refreshView()
	view:invalidate()
end

local function toast(msg)
	Toast:makeText(GetActivity(), msg, Toast.LENGTH_SHORT):show()
end

local function allocBitmap()
	refreshView()
	view:invalidate()
end

function onMapState(data)
	if data == nil or data == "" then return end
	local fn = loadstring(data)
	if fn == nil then return end
	local ok, payload = pcall(fn)
	if ok and payload ~= nil then
		store.importData(payload)
		redraw()
	end
end

function toggleVisible(arg)
	if arg == "open" then visible = true
	elseif arg == "close" then visible = false
	else visible = not visible end
	applyWindowLayout()
	if visible then
		allocBitmap()
		raiseMap()
		ScheduleCallback(11801, "raiseMapWindow", 100)
	else
		currentView = nil
	end
end

function setAutoOpen(v)
	if v == false or v == "false" or v == "0" then
		toggleVisible("close")
	else
		toggleVisible("open")
	end
end

local function addLabelRow(parent, text)
	local tv = luajava.new(TextView, GetActivity())
	tv:setText(text)
	tv:setTextSize(12)
	parent:addView(tv)
end

local function addQuickRow(parent, tileId, slot, tile)
	local row = luajava.new(LinearLayout, GetActivity())
	row:setOrientation(LinearLayout.HORIZONTAL)
	local labelEdit = luajava.new(EditText, GetActivity())
	labelEdit:setHint("Label")
	labelEdit:setLayoutParams(luajava.new(LinearLayoutParams, 0, LinearLayoutParams.WRAP_CONTENT, 0.35))
	local cmdEdit = luajava.new(EditText, GetActivity())
	cmdEdit:setHint("Command")
	cmdEdit:setLayoutParams(luajava.new(LinearLayoutParams, 0, LinearLayoutParams.WRAP_CONTENT, 0.65))
	local existing = tile and tile.quick and tile.quick[slot]
	if existing ~= nil then
		labelEdit:setText(existing.label or "")
		cmdEdit:setText(existing.cmd or "")
	end
	local runBtn = luajava.new(Button, GetActivity())
	runBtn:setText("▶")
	runBtn:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
		onClick = function()
			local cmd = cmdEdit:getText():toString()
			if cmd ~= "" then SendToServer(cmd) end
		end
	}))
	row:addView(labelEdit)
	row:addView(cmdEdit)
	row:addView(runBtn)
	parent:addView(row)
	return labelEdit, cmdEdit
end

local function showTileSheet(cell)
	if cell == nil then return end
	local tile = cell.tile
	local title = tile and tile.name or "Unexplored"
	local note = tile and tile.note or ""
	local ctx = GetActivity()
	local builder = luajava.newInstance("android.app.AlertDialog$Builder", ctx)
	builder:setTitle(title)

	local scroll = luajava.new(ScrollView, ctx)
	local layout = luajava.new(LinearLayout, ctx)
	layout:setOrientation(LinearLayout.VERTICAL)
	layout:setPadding(24, 16, 24, 8)

	addLabelRow(layout, "Note")
	local noteEdit = luajava.new(EditText, ctx)
	noteEdit:setText(note)
	noteEdit:setMinLines(2)
	layout:addView(noteEdit)

	local quickEdits = {}
	if tile ~= nil then
		addLabelRow(layout, "Quick buttons (tap ▶ to run)")
		for slot = 1, config.MAX_QUICK do
			local le, ce = addQuickRow(layout, tile.id, slot, tile)
			quickEdits[slot] = { label = le, cmd = ce }
		end
		addLabelRow(layout, "Button set (switchTo)")
		local setEdit = luajava.new(EditText, ctx)
		setEdit:setHint("e.g. pick, patton")
		if tile.buttonSet ~= nil then setEdit:setText(tile.buttonSet) end
		layout:addView(setEdit)
		quickEdits.buttonSet = setEdit
	end

	scroll:addView(layout)
	builder:setView(scroll)

	builder:setPositiveButton("Save", luajava.createProxy("android.content.DialogInterface$OnClickListener", {
		onClick = function()
			if tile == nil then return end
			PluginXCallS( "onMapSetNote", tile.id .. "\31" .. noteEdit:getText():toString())
			for slot = 1, config.MAX_QUICK do
				local pair = quickEdits[slot]
				if pair ~= nil then
					local payload = tile.id .. "\31" .. slot .. "\31" .. pair.label:getText():toString() .. "\31" .. pair.cmd:getText():toString()
					PluginXCallS( "onMapSetQuick", payload)
				end
			end
			if quickEdits.buttonSet ~= nil then
				PluginXCallS( "onMapSetButtonSet", tile.id .. "\31" .. quickEdits.buttonSet:getText():toString())
			end
		end
	}))

	if tile ~= nil and tile.explored then
		builder:setNeutralButton("Go", luajava.createProxy("android.content.DialogInterface$OnClickListener", {
			onClick = function()
				PluginXCallS( "onMapWalkTo", tile.id)
			end
		}))
	end

	if tile ~= nil and tile.buttonSet ~= nil and tile.buttonSet ~= "" then
		-- extra: load button set when opening sheet - offer via long press menu item
	end

	if cell.fog then
		builder:setNegativeButton("Explore", luajava.createProxy("android.content.DialogInterface$OnClickListener", {
			onClick = function()
				local dir = nil
				if cell.dy < 0 then dir = "n"
				elseif cell.dy > 0 then dir = "s"
				elseif cell.dx > 0 then dir = "e"
				elseif cell.dx < 0 then dir = "w" end
				if dir ~= nil then
					PluginXCallS( "onMapLinkDir", dir .. "\31" .. dir)
					PluginXCallS( "fmwalkCommand", dir)
				end
			end
		}))
	else
		builder:setNegativeButton("Close", nil)
	end

	local dialog = builder:create()
	dialog:show()

	if tile ~= nil and tile.buttonSet ~= nil and tile.buttonSet ~= "" then
		-- show toast hint
	end
end

local function headerBtnWidth()
	return math.floor(config.HEADER_BTN_DP * density)
end

local function hitHeaderButton(x, y, w)
	local headerPx = headerHeightPx()
	if y > headerPx then return nil end
	local btnW = headerBtnWidth()
	local pad = 4 * density
	local xpos = pad
	for _, btn in ipairs(config.HEADER_BUTTONS) do
		if x >= xpos and x < xpos + btnW then
			return btn
		end
		xpos = xpos + btnW + pad
	end
	local closeX = w - btnW - pad
	if x >= closeX and x < closeX + btnW then
		return { id = "close", label = "X", action = "close" }
	end
	return nil
end

local function runHeaderAction(action)
	if action == "close" then
		toggleVisible("close")
	elseif action == "here" then
		PluginXCallS("onMapManualHere", "?")
		toast("Marked here")
	elseif action == "mode_walk" then
		compassMode = "walk"
		redraw()
		toast("GO — walk known exits")
	elseif action == "mode_map" then
		compassMode = "map"
		redraw()
		toast("MAP — draw new rooms")
	end
end

local function runCompassDir(dir, label)
	if dir == nil or dir == "" then return end
	if compassMode == "walk" then
		PluginXCallS("onMapWalkDir", dir)
		toast("Go " .. string.upper(label or dir))
	else
		PluginXCallS("onMapExploreDir", dir)
		toast("Map " .. string.upper(label or dir))
	end
end

local function headerBtnColors(action)
	if action == "mode_walk" then
		if compassMode == "walk" then return 36, 110, 62 else return 40, 50, 70 end
	elseif action == "mode_map" then
		if compassMode == "map" then return 50, 80, 130 else return 40, 50, 70 end
	end
	return 40, 50, 70
end

local function drawHeaderButtons(canvas, w, headerPx)
	local btnW = headerBtnWidth()
	local pad = 4 * density
	local top = chromeTopPx + 2
	local bottom = headerPx - 2
	local textY = headerPx - 4 * density
	local xpos = pad
	for _, btn in ipairs(config.HEADER_BUTTONS) do
		local r, g, b = headerBtnColors(btn.action)
		renderer.setColor(paint, 255, r, g, b)
		local bw = btnW
		if btn.action == "mode_walk" or btn.action == "mode_map" then
			bw = math.floor(btnW * 1.15)
		end
		canvas:drawRoundRect(xpos, top, xpos + bw, bottom, 4, 4, paint)
		renderer.setColor(paint, 255, 220, 230, 255)
		paint:setTextSize(10 * density)
		local tx = xpos + bw * 0.18
		if btn.label == "+" then tx = xpos + bw * 0.28 end
		canvas:drawText(btn.label, tx, textY, paint)
		xpos = xpos + bw + pad
	end
	local closeX = w - btnW - pad
	renderer.setColor(paint, 255, 90, 30, 30)
	canvas:drawRoundRect(closeX, top, closeX + btnW, bottom, 4, 4, paint)
	renderer.setColor(paint, 255, 255, 220, 220)
	paint:setTextSize(11 * density)
	canvas:drawText("X", closeX + btnW * 0.32, textY, paint)
end

local function showGoEditor(slot, goKey)
	local cur = store.getCurrentTile()
	local curId = store.getState().currentTileId
	if curId == nil then
		toast("Place @ first")
		return
	end
	local existing
	if goKey ~= nil then
		existing = store.getGoByKey(cur, goKey)
	else
		existing = store.getGoSlot(cur, slot)
	end
	local ctx = GetActivity()
	local builder = luajava.newInstance("android.app.AlertDialog$Builder", ctx)
	builder:setTitle(goKey and ("Go: " .. string.upper(goKey)) or ("Custom go #" .. slot))
	local layout = luajava.new(LinearLayout, ctx)
	layout:setOrientation(LinearLayout.VERTICAL)
	layout:setPadding(24, 16, 24, 8)
	local labelEdit = luajava.new(EditText, ctx)
	labelEdit:setHint("Label (enter, door…)")
	labelEdit:setText(existing and existing.label or "")
	layout:addView(labelEdit)
	local cmdEdit = luajava.new(EditText, ctx)
	cmdEdit:setHint("MUD command")
	cmdEdit:setText(existing and existing.cmd or "")
	layout:addView(cmdEdit)
	builder:setView(layout)
	builder:setPositiveButton("Save", luajava.createProxy("android.content.DialogInterface$OnClickListener", {
		onClick = function()
			local label = labelEdit:getText():toString()
			local cmd = cmdEdit:getText():toString()
			if goKey ~= nil then
				PluginXCallS("onMapSetGoKey", curId .. "\31" .. goKey .. "\31" .. label .. "\31" .. cmd)
			else
				PluginXCallS("onMapSetGo", curId .. "\31" .. slot .. "\31" .. label .. "\31" .. cmd)
			end
			toast("Go saved")
		end
	}))
	builder:setNeutralButton("Run", luajava.createProxy("android.content.DialogInterface$OnClickListener", {
		onClick = function()
			local cmd = cmdEdit:getText():toString()
			if cmd ~= "" then SendToServer(cmd) end
		end
	}))
	builder:setNegativeButton("Cancel", nil)
	builder:create():show()
end

local function handleCompassButton(btn, longPress)
	if btn == nil then return end
	if btn.kind == "center" then
		if store.getCurrentTile() == nil then
			PluginXCallS("onMapManualHere", "Start")
			toast("Start placed")
		else
			PluginXCallS("onMapSelectTile", store.getState().currentTileId)
		end
		return
	end
	if btn.kind == "map" or btn.kind == "vertical" then
		if longPress then
			if btn.explore ~= nil then
				showGoEditor(nil, btn.explore)
			end
			return
		end
		if btn.explore ~= nil then
			runCompassDir(btn.explore, btn.label)
		end
		return
	end
	if btn.kind == "gokey" then
		if longPress then
			showGoEditor(nil, btn.goKey)
			return
		end
		local g = store.getGoByKey(store.getCurrentTile(), btn.goKey)
		if g ~= nil and g.cmd ~= nil and g.cmd ~= "" then
			PluginXCallS("onMapRunGoKey", btn.goKey)
		else
			showGoEditor(nil, btn.goKey)
		end
		return
	end
	if btn.kind == "go" then
		if longPress then
			showGoEditor(btn.slot, nil)
			return
		end
		local g = store.getGoSlot(store.getCurrentTile(), btn.slot)
		if g ~= nil and g.cmd ~= nil and g.cmd ~= "" then
			PluginXCallS("onMapRunGo", tostring(btn.slot))
		else
			showGoEditor(btn.slot, nil)
		end
	end
end

local touchHandler = {}
function touchHandler.onTouch(v, e)
	local action = e:getAction()
	local x, y = e:getX(), e:getY()
	local headerPx = headerHeightPx()
	if action == MotionEvent.ACTION_DOWN then
		touchStartX, touchStartY = x, y
		touchStartTime = e:getEventTime()
		longPressAt = touchStartTime
		isPanning = false
		return true
	elseif action == MotionEvent.ACTION_MOVE then
		local dist = math.abs(x - touchStartX) + math.abs(y - touchStartY)
		if dist > 10 * density then
			isPanning = true
		end
		return true
	elseif action == MotionEvent.ACTION_UP then
		local dist = math.abs(x - touchStartX) + math.abs(y - touchStartY)
		local elapsed = e:getEventTime() - touchStartTime
		local w = view:getWidth()
		local headerBtn = hitHeaderButton(x, y, w)
		if headerBtn ~= nil and dist < 18 * density then
			runHeaderAction(headerBtn.action)
			return true
		end
		if y <= headerPx and dist < 14 * density then
			toggleVisible("")
			return true
		end
		if isPanning and currentView ~= nil and dist > 14 * density then
			local tilePx = math.max(1, currentView.tilePx)
			panDx = panDx - math.floor((x - touchStartX) / tilePx + 0.5)
			panDy = panDy - math.floor((y - touchStartY) / tilePx + 0.5)
			redraw()
			return true
		end
		local cbtn = compass.hitTest(compassLayout, x, y)
		if cbtn ~= nil then
			handleCompassButton(cbtn, elapsed >= longPressMs)
			return true
		end
		if compassLayout ~= nil and x >= compassLayout.dockLeft then
			return true
		end
		local cell = renderer.hitTest(currentView, x, y)
		if elapsed >= longPressMs then
			showTileSheet(cell)
			return true
		end
		if cell == nil then return true end
		if cell.dx == 0 and cell.dy == 0 and store.getCurrentTile() == nil then
			PluginXCallS("onMapManualHere", "Start")
			toast("Start placed — tap N/S/E/W to map")
			return true
		end
		if isAdjacent(cell) then
			local dir = dirFromCell(cell)
			if dir ~= nil then
				runCompassDir(dir, dir)
				return true
			end
		end
		if cell.fog then
			if compassMode == "map" then
				toast("Tap direction to add a room")
			else
				toast("No room there — switch to MAP")
			end
			return true
		end
		if cell.id ~= nil and cell.id ~= store.getState().currentTileId then
			if compassMode == "walk" and cell.tile ~= nil and cell.tile.explored then
				PluginXCallS("onMapWalkTo", cell.id)
				toast("Walking…")
			else
				PluginXCallS("onMapSelectTile", cell.id)
			end
			return true
		end
		if cell.id == store.getState().currentTileId then
			mapMode = mapMode == "full" and "minimap" or "full"
			redraw()
		end
		return true
	end
	return true
end

function OnCreate()
	view:setOnTouchListener(luajava.createProxy("android.view.View$OnTouchListener", touchHandler))
	AddOptionCallback("toggleForgeMap", "ForgeMap", nil)
	stripHeightPx = math.floor(config.STRIP_HEIGHT_DP * density)
	local autoOpen = GetOptionValue("auto_open_map")
	visible = not (autoOpen == "false" or autoOpen == "0")
	applyWindowLayout()
	if visible then
		raiseMap()
		PluginXCallS("syncMapToWindow", "")
		updateChromeInsets()
		allocBitmap()
		ScheduleCallback(11802, "raiseMapWindow", 300)
	end
end

function refreshChromeInsets()
	updateChromeInsets()
	redraw()
end

function raiseMapWindow()
	raiseMap()
	view:invalidate()
end

function OnSizeChanged(w, h, oldw, oldh)
	stripHeightPx = math.floor(config.STRIP_HEIGHT_DP * density)
	updateChromeInsets()
	if visible then
		applyWindowLayout()
		allocBitmap()
	end
end

function OnDraw(canvas)
	if not visible then return end
	local w, h = view:getWidth(), view:getHeight()
	if w <= 0 or h <= 0 then return end
	if currentView == nil or currentView.width ~= w or currentView.height ~= h then
		refreshView()
	end
	renderer.setColor(paint, 255, 10, 10, 16)
	canvas:drawRect(0, 0, w, h, paint)
	if currentView ~= nil then
		renderer.drawMap(canvas, paint, currentView, mapMode)
	end
	updateChromeInsets()
	local headerPx = headerHeightPx()
	renderer.setColor(paint, 255, 0, 0, 0)
	canvas:drawRect(0, chromeTopPx, w, headerPx, paint)
	renderer.setColor(paint, 255, 220, 230, 255)
	paint:setTextSize(10 * density)
	local cur = store.getCurrentTile()
	local label = "Manual map"
	if compassMode == "walk" then
		label = label .. " [GO]"
	else
		label = label .. " [MAP]"
	end
	if cur == nil then
		label = label .. " — + to start"
	else
		label = label .. " — " .. (cur.name or "?")
	end
	canvas:drawText(label, 8 * density + headerBtnWidth() * 3.2 + 8 * density, chromeTopPx + 12 * density, paint)
	drawHeaderButtons(canvas, w, headerPx)
	if compassLayout ~= nil then
		compass.draw(canvas, paint, compassLayout, cur, compassMode)
	end
end

function OnDestroy()
	currentView = nil
end

function PopulateMenu(menu)
	local item = menu:add(0, 501, 501, "ForgeMap")
	item:setOnMenuItemClickListener(luajava.createProxy("android.view.MenuItem$OnMenuItemClickListener", {
		onMenuItemClick = function()
			toggleVisible("")
			return true
		end
	}))
	local full = menu:add(0, 502, 502, "Map: fullscreen")
	full:setOnMenuItemClickListener(luajava.createProxy("android.view.MenuItem$OnMenuItemClickListener", {
		onMenuItemClick = function()
			mapMode = "full"
			redraw()
			return true
		end
	}))
	local here = menu:add(0, 503, 503, "Map: mark here")
	here:setOnMenuItemClickListener(luajava.createProxy("android.view.MenuItem$OnMenuItemClickListener", {
		onMenuItemClick = function()
			PluginXCallS( "onMapManualHere", "?")
			toast("Marked current location")
			return true
		end
	}))
	local reset = menu:add(0, 504, 504, "Map: recenter")
	reset:setOnMenuItemClickListener(luajava.createProxy("android.view.MenuItem$OnMenuItemClickListener", {
		onMenuItemClick = function()
			panDx, panDy = 0, 0
			mapMode = "minimap"
			redraw()
			return true
		end
	}))
end
