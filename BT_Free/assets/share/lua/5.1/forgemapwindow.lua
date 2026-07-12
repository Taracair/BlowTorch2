local props = require("forgemapconfig")
local config = require("forgemap.config")
local store = require("forgemap.store")
local renderer = require("forgemap.renderer")

local pluginName = props.plugin

Bitmap = luajava.bindClass("android.graphics.Bitmap")
BitmapConfig = luajava.bindClass("android.graphics.Bitmap$Config")
PaintClass = luajava.bindClass("android.graphics.Paint")
MotionEvent = luajava.bindClass("android.view.MotionEvent")
AlertDialog = luajava.bindClass("android.app.AlertDialog")
EditText = luajava.bindClass("android.widget.EditText")
LinearLayout = luajava.bindClass("android.widget.LinearLayout")
LinearLayoutParams = luajava.bindClass("android.widget.LinearLayoutParams")
TextView = luajava.bindClass("android.widget.TextView")
ScrollView = luajava.bindClass("android.widget.ScrollView")
Button = luajava.bindClass("android.widget.Button")
Toast = luajava.bindClass("android.widget.Toast")
Gravity = luajava.bindClass("android.view.Gravity")

local density = GetDisplayDensity()
local paint = PaintClass()
paint:setAntiAlias(true)

local mapLayer = nil
local mapCanvas = nil
local currentView = nil
local mapMode = "minimap"
local visible = true
local longPressAt = 0
local touchStartX, touchStartY = 0, 0
local zoomScale = 1.0

local function toast(msg)
	Toast:makeText(GetActivity(), msg, Toast.LENGTH_SHORT):show()
end

local function redraw()
	if mapCanvas == nil then return end
	currentView = renderer.computeView(store.getState(), {
		tileSizeDp = (store.getUi().tileSizeDp or config.TILE_SIZE_DP) * zoomScale,
		radius = mapMode == "full" and 12 or math.floor(config.MINIMAP_TILES / 2),
		density = density,
	})
	currentView.width = view:getWidth()
	currentView.height = view:getHeight()
	renderer.drawMap(mapCanvas, paint, currentView, mapMode)
	view:invalidate()
end

local function allocBitmap()
	if view == nil then return end
	local w, h = view:getWidth(), view:getHeight()
	if w <= 0 or h <= 0 then return end
	if mapLayer ~= nil then mapLayer:recycle() end
	mapLayer = Bitmap:createBitmap(w, h, BitmapConfig.ARGB_8888)
	mapCanvas = luajava.newInstance("android.graphics.Canvas", mapLayer)
	redraw()
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
	view:setVisibility(visible and 0 or 8)
	if visible then redraw() end
end

function setAutoOpen(v)
end

local function addLabelRow(parent, text)
	local tv = TextView(GetActivity())
	tv:setText(text)
	tv:setTextSize(12)
	parent:addView(tv)
end

local function addQuickRow(parent, tileId, slot, tile)
	local row = LinearLayout(GetActivity())
	row:setOrientation(LinearLayout.HORIZONTAL)
	local labelEdit = EditText(GetActivity())
	labelEdit:setHint("Label")
	labelEdit:setLayoutParams(luajava.new(LinearLayoutParams, 0, LinearLayoutParams.WRAP_CONTENT, 0.35))
	local cmdEdit = EditText(GetActivity())
	cmdEdit:setHint("Command")
	cmdEdit:setLayoutParams(luajava.new(LinearLayoutParams, 0, LinearLayoutParams.WRAP_CONTENT, 0.65))
	local existing = tile and tile.quick and tile.quick[slot]
	if existing ~= nil then
		labelEdit:setText(existing.label or "")
		cmdEdit:setText(existing.cmd or "")
	end
	local runBtn = Button(GetActivity())
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
	local builder = AlertDialog.Builder(ctx)
	builder:setTitle(title)

	local scroll = ScrollView(ctx)
	local layout = LinearLayout(ctx)
	layout:setOrientation(LinearLayout.VERTICAL)
	layout:setPadding(24, 16, 24, 8)

	addLabelRow(layout, "Note")
	local noteEdit = EditText(ctx)
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
		local setEdit = EditText(ctx)
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
			PluginXCallS(pluginName, "onMapSetNote", tile.id .. "\31" .. noteEdit:getText():toString())
			for slot = 1, config.MAX_QUICK do
				local pair = quickEdits[slot]
				if pair ~= nil then
					local payload = tile.id .. "\31" .. slot .. "\31" .. pair.label:getText():toString() .. "\31" .. pair.cmd:getText():toString()
					PluginXCallS(pluginName, "onMapSetQuick", payload)
				end
			end
			if quickEdits.buttonSet ~= nil then
				PluginXCallS(pluginName, "onMapSetButtonSet", tile.id .. "\31" .. quickEdits.buttonSet:getText():toString())
			end
		end
	}))

	if tile ~= nil and tile.explored then
		builder:setNeutralButton("Go", luajava.createProxy("android.content.DialogInterface$OnClickListener", {
			onClick = function()
				PluginXCallS(pluginName, "onMapWalkTo", tile.id)
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
					PluginXCallS(pluginName, "onMapLinkDir", dir .. "\31" .. dir)
					PluginXCallS(pluginName, "fmwalkCommand", dir)
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

local touchHandler = {}
function touchHandler.onTouch(v, e)
	local action = e:getAction()
	local x, y = e:getX(), e:getY()
	if action == MotionEvent.ACTION_DOWN then
		touchStartX, touchStartY = x, y
		longPressAt = os.time()
		return true
	elseif action == MotionEvent.ACTION_UP then
		local cell = renderer.hitTest(currentView, x, y)
		local elapsed = os.time() - longPressAt
		local dist = math.abs(x - touchStartX) + math.abs(y - touchStartY)
		if elapsed >= 1 or dist > 24 * density then
			showTileSheet(cell)
		elseif cell ~= nil and cell.tile ~= nil and cell.tile.quick ~= nil and dist < 8 * density then
			local relX = x - (currentView.width / 2 - currentView.tilePx / 2 + cell.dx * currentView.tilePx)
			local slot = math.min(config.MAX_QUICK, math.max(1, math.floor(relX / (currentView.tilePx * 0.28)) + 1))
			local q = cell.tile.quick[slot]
			if q ~= nil and q.cmd ~= nil and q.cmd ~= "" then
				PluginXCallS(pluginName, "onMapRunQuick", cell.id .. "\31" .. slot)
			elseif cell.fog then
				showTileSheet(cell)
			elseif cell.id ~= nil and cell.id ~= store.getState().currentTileId then
				PluginXCallS(pluginName, "onMapWalkTo", cell.id)
			else
				mapMode = mapMode == "full" and "minimap" or "full"
				redraw()
			end
		elseif cell ~= nil then
			if cell.fog then
				showTileSheet(cell)
			elseif cell.id ~= nil and cell.id ~= store.getState().currentTileId then
				PluginXCallS(pluginName, "onMapWalkTo", cell.id)
			else
				if cell.tile ~= nil and cell.tile.buttonSet ~= nil and cell.tile.buttonSet ~= "" then
					PluginXCallS(pluginName, "onMapApplyButtonSet", cell.id)
				end
				mapMode = mapMode == "full" and "minimap" or "full"
				redraw()
			end
		end
		return true
	end
	return true
end

function OnCreate()
	view:setOnTouchListener(luajava.createProxy("android.view.View$OnTouchListener", touchHandler))
	AddOptionCallback("toggleForgeMap", "ForgeMap", nil)
	PluginXCallS(pluginName, "toggleMapWindow", "open")
end

function OnSizeChanged(w, h, oldw, oldh)
	allocBitmap()
end

function OnDraw(canvas)
	if not visible then return end
	if mapLayer ~= nil then
		canvas:drawBitmap(mapLayer, 0, 0, nil)
	end
	paint:setColor(0xAAFFFFFF)
	paint:setTextSize(11 * density)
	local cur = store.getCurrentTile()
	local label = "ForgeMap"
	if cur ~= nil then label = label .. " — " .. (cur.name or "?") end
	canvas:drawText(label, 8 * density, 14 * density, paint)
end

function OnDestroy()
	if mapLayer ~= nil then
		mapLayer:recycle()
		mapLayer = nil
	end
	mapCanvas = nil
end

function PopulateMenu(menu)
	local item = menu:add(0, 501, 501, "ForgeMap")
	item:setOnMenuItemClickListener(luajava.createProxy("android.view.MenuItem$OnClickListener", {
		onMenuItemClick = function()
			toggleVisible("")
			return true
		end
	}))
	local full = menu:add(0, 502, 502, "Map: fullscreen")
	full:setOnMenuItemClickListener(luajava.createProxy("android.view.MenuItem$OnClickListener", {
		onMenuItemClick = function()
			mapMode = "full"
			redraw()
			return true
		end
	}))
	local here = menu:add(0, 503, 503, "Map: mark here")
	here:setOnMenuItemClickListener(luajava.createProxy("android.view.MenuItem$OnClickListener", {
		onMenuItemClick = function()
			PluginXCallS(pluginName, "onMapManualHere", "?")
			toast("Marked current location")
			return true
		end
	}))
end
