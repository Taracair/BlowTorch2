local config = require("forgemap.config")
local store = require("forgemap.store")

local M = {}
local Style = nil

function M.setColor(paint, a, r, g, b)
	paint:setARGB(a, r, g, b)
end

function M.setPaintStyle(paint)
	if Style == nil then
		Style = luajava.bindClass("android.graphics.Paint$Style")
	end
	return Style
end

function M.computeView(state, opts)
	opts = opts or {}
	local ui = store.getUi()
	local density = opts.density or 1
	local preferredPx = math.floor((opts.tileSizeDp or ui.tileSizeDp or config.TILE_SIZE_DP) * density)
	local viewW = opts.viewWidth or 0
	local mapW = opts.mapWidth or viewW
	local viewH = opts.viewHeight or 0
	local headerPx = math.floor((opts.headerDp or config.HEADER_DP) * density) + (opts.headerExtraPx or 0)
	local usableW = math.max(1, mapW)
	local usableH = math.max(1, viewH - headerPx)

	local maxGrid = opts.maxGrid or config.MINIMAP_TILES
	local radius = opts.radius
	if radius == nil then
		radius = math.floor(maxGrid / 2)
	end
	local grid = radius * 2 + 1
	local tilePx = preferredPx
	if usableW > 0 and usableH > 0 then
		tilePx = math.floor(math.min(usableW, usableH) / grid)
		local minPx = math.floor((config.MIN_TILE_DP or 18) * density)
		tilePx = math.max(minPx, math.min(tilePx, preferredPx))
		while tilePx * grid > usableH + 2 and radius > 1 do
			radius = radius - 1
			grid = radius * 2 + 1
			tilePx = math.floor(math.min(usableW, usableH) / grid)
			tilePx = math.max(minPx, math.min(tilePx, preferredPx))
		end
	end

	local cur = store.getCurrentTile()
	local cx, cy, cz = 0, 0, 0
	local area = state.currentArea or "default"
	if cur ~= nil then
		cx, cy, cz = cur.x, cur.y, cur.z or 0
		area = cur.area or area
	end
	cx = cx + (opts.panDx or 0)
	cy = cy + (opts.panDy or 0)

	local cells = {}
	for dy = -radius, radius do
		for dx = -radius, radius do
			local wx, wy = cx + dx, cy + dy
			local id, tile = store.findTileAt(area, wx, wy, cz)
			table.insert(cells, {
				dx = dx, dy = dy, wx = wx, wy = wy,
				id = id, tile = tile,
				fog = id == nil,
			})
		end
	end
	return {
		cells = cells,
		center = { x = cx, y = cy, z = cz, area = area },
		currentId = state.currentTileId,
		tilePx = tilePx,
		radius = radius,
		headerPx = headerPx,
		mapWidth = mapW,
	}
end

function M.drawMap(canvas, paint, view, mode)
	if canvas == nil or view == nil then return end
	local ui = store.getUi()
	local tilePx = view.tilePx
	local w = canvas:getWidth()
	local h = canvas:getHeight()
	local mapClipW = view.mapWidth or w
	M.setColor(paint, 255, 10, 10, 16)
	canvas:drawRect(0, 0, mapClipW, h, paint)

	local headerPx = view.headerPx or 0
	local mapClipW = view.mapWidth or w
	local ox = mapClipW / 2 - tilePx / 2
	local oy = headerPx + (h - headerPx) / 2 - tilePx / 2
	local pad = math.max(2, math.floor(tilePx * 0.08))

	for _, cell in ipairs(view.cells) do
		local left = ox + cell.dx * tilePx
		local top = oy + cell.dy * tilePx
		local explored = cell.tile ~= nil and cell.tile.explored
		local isCurrent = cell.id ~= nil and cell.id == view.currentId
		local isCenter = cell.dx == 0 and cell.dy == 0
		local isAdjacent = math.max(math.abs(cell.dx), math.abs(cell.dy)) == 1
		if explored then
			if isCurrent then
				M.setColor(paint, 255, 255, 140, 20)
				canvas:drawRoundRect(left + pad, top + pad, left + tilePx - pad, top + tilePx - pad, pad * 2, pad * 2, paint)
				M.setColor(paint, 255, 255, 255, 255)
				paint:setTextSize(math.max(10, tilePx * 0.38))
				canvas:drawText("@", left + tilePx * 0.32, top + tilePx * 0.66, paint)
			else
				M.setColor(paint, 255, 45, 90, 120)
				canvas:drawRoundRect(left + pad, top + pad, left + tilePx - pad, top + tilePx - pad, pad * 2, pad * 2, paint)
				if ui.showLabels and cell.tile.name ~= nil then
					M.setColor(paint, 255, 220, 230, 255)
					paint:setTextSize(math.max(8, tilePx * 0.24))
					local label = cell.tile.name
					if #label > 4 then label = string.sub(label, 1, 4) end
					canvas:drawText(label, left + pad, top + tilePx - pad, paint)
				end
			end
			if cell.tile.flags ~= nil then
				local fi = 0
				for _, flag in pairs(cell.tile.flags) do
					local icon = config.FLAG_ICONS[flag] or "•"
					M.setColor(paint, 255, 255, 255, 255)
					paint:setTextSize(math.max(8, tilePx * 0.28))
					canvas:drawText(icon, left + pad + fi * (tilePx * 0.25), top + pad + paint:getTextSize(), paint)
					fi = fi + 1
				end
			end
			if cell.tile.quick ~= nil then
				local qi = 0
				for slot, q in pairs(cell.tile.quick) do
					if q ~= nil and q.cmd ~= nil and q.cmd ~= "" then
						M.setColor(paint, 255, 140, 255, 140)
						paint:setTextSize(math.max(7, tilePx * 0.22))
						local lbl = q.label
						if lbl == nil or lbl == "" then lbl = tostring(slot) end
						if #lbl > 2 then lbl = string.sub(lbl, 1, 2) end
						canvas:drawText(lbl, left + pad + qi * (tilePx * 0.28), top + tilePx - pad - 2, paint)
						qi = qi + 1
					end
				end
			end
		else
			local Style = M.setPaintStyle(paint)
			paint:setStyle(Style.FILL)
			if isCenter and view.currentId == nil then
				M.setColor(paint, 255, 30, 70, 35)
			else
				M.setColor(paint, 255, 22, 30, 42)
			end
			canvas:drawRoundRect(left + pad, top + pad, left + tilePx - pad, top + tilePx - pad, pad, pad, paint)
			if isAdjacent or (isCenter and view.currentId == nil) then
				paint:setStyle(Style.STROKE)
				paint:setStrokeWidth(math.max(2, tilePx * 0.08))
				M.setColor(paint, 255, 80, 180, 255)
				canvas:drawRoundRect(left + pad, top + pad, left + tilePx - pad, top + tilePx - pad, pad, pad, paint)
				paint:setStyle(Style.FILL)
			end
			local hint = nil
			if isCenter and view.currentId == nil then
				hint = "+"
			elseif isAdjacent then
				if cell.dy < 0 and cell.dx < 0 then hint = "NW"
				elseif cell.dy < 0 and cell.dx == 0 then hint = "N"
				elseif cell.dy < 0 and cell.dx > 0 then hint = "NE"
				elseif cell.dy > 0 and cell.dx < 0 then hint = "SW"
				elseif cell.dy > 0 and cell.dx == 0 then hint = "S"
				elseif cell.dy > 0 and cell.dx > 0 then hint = "SE"
				elseif cell.dx < 0 then hint = "W"
				elseif cell.dx > 0 then hint = "E"
				end
			end
			M.setColor(paint, 255, 255, 255, 255)
			paint:setTextSize(math.max(8, tilePx * 0.28))
			if hint ~= nil then
				canvas:drawText(hint, left + tilePx * 0.18, top + tilePx * 0.62, paint)
			elseif not isCenter then
				M.setColor(paint, 255, 70, 85, 100)
				paint:setTextSize(math.max(7, tilePx * 0.2))
				canvas:drawText("·", left + tilePx * 0.42, top + tilePx * 0.58, paint)
			end
		end
	end

	if ui.showGrid then
		local Style = M.setPaintStyle(paint)
		M.setColor(paint, 40, 255, 255, 255)
		paint:setStyle(Style.STROKE)
		for _, cell in ipairs(view.cells) do
			local left = ox + cell.dx * tilePx
			local top = oy + cell.dy * tilePx
			canvas:drawRect(left, top, left + tilePx, top + tilePx, paint)
		end
		paint:setStyle(Style.FILL)
	end
end

function M.hitTest(view, x, y)
	if view == nil then return nil end
	local w = view.width or 0
	local h = view.height or 0
	local tilePx = view.tilePx
	if tilePx == nil or tilePx <= 0 then return nil end
	local headerPx = view.headerPx or 0
	local mapClipW = view.mapWidth or w
	local ox = mapClipW / 2 - tilePx / 2
	local oy = headerPx + (h - headerPx) / 2 - tilePx / 2
	for _, cell in ipairs(view.cells) do
		local left = ox + cell.dx * tilePx
		local top = oy + cell.dy * tilePx
		if left + tilePx < 0 or left > mapClipW then
			-- skip cells outside map panel
		elseif x >= left and x < left + tilePx and y >= top and y < top + tilePx then
			return cell
		end
	end
	return nil
end

return M
