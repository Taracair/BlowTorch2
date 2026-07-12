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
Toast = luajava.bindClass("android.widget.Toast")

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

local function showTileSheet(cell)
	if cell == nil then return end
	local tile = cell.tile
	local title = tile and tile.name or "Unexplored"
	local note = tile and tile.note or ""
	local ctx = GetActivity()
	local builder = AlertDialog.Builder(ctx)
	builder:setTitle(title)
	local layout = LinearLayout(ctx)
	layout:setOrientation(LinearLayout.VERTICAL)
	layout:setPadding(24, 16, 24, 8)
	local noteEdit = EditText(ctx)
	noteEdit:setText(note)
	noteEdit:setHint("Note for this tile")
	layout:addView(noteEdit)
	builder:setView(layout)
	builder:setPositiveButton("Save", luajava.createProxy("android.content.DialogInterface$OnClickListener", {
		onClick = function()
			if tile ~= nil then
				PluginXCallS(pluginName, "onMapSetNote", tile.id .. "\31" .. noteEdit:getText():toString())
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
	builder:show()
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
		elseif cell ~= nil then
			if cell.fog then
				showTileSheet(cell)
			elseif cell.id ~= nil and cell.id ~= store.getState().currentTileId then
				PluginXCallS(pluginName, "onMapWalkTo", cell.id)
			else
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
			PluginXCallS(pluginName, "onMapManualHere", "?")
			toast("Marked current location")
			return true
		end
	}))
end
