local config = require("forgemap.config")
local store = require("forgemap.store")

local M = {}
local Style = nil

function M.setPaintStyle(paint)
	if Style == nil then
		Style = luajava.bindClass("android.graphics.Paint$Style")
	end
	return Style
end

function M.computeView(state, opts)
	opts = opts or {}
	local ui = store.getUi()
	local tilePx = math.floor((opts.tileSizeDp or ui.tileSizeDp or config.TILE_SIZE_DP) * (opts.density or 1))
	local cur = store.getCurrentTile()
	local cx, cy, cz = 0, 0, 0
	local area = state.currentArea or "default"
	if cur ~= nil then
		cx, cy, cz = cur.x, cur.y, cur.z or 0
		area = cur.area or area
	end
	local radius = opts.radius or math.floor(config.MINIMAP_TILES / 2)
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
	}
end

function M.drawMap(canvas, paint, view, mode)
	if canvas == nil or view == nil then return end
	local ui = store.getUi()
	local tilePx = view.tilePx
	local w = canvas:getWidth()
	local h = canvas:getHeight()
	paint:setColor(ui.fogColor or config.DEFAULT_UI.fogColor)
	canvas:drawRect(0, 0, w, h, paint)

	local ox = w / 2 - tilePx / 2
	local oy = h / 2 - tilePx / 2
	local pad = math.max(2, math.floor(tilePx * 0.08))

	for _, cell in ipairs(view.cells) do
		local left = ox + cell.dx * tilePx
		local top = oy + cell.dy * tilePx
		local explored = cell.tile ~= nil and cell.tile.explored
		if explored then
			local color = cell.tile.color or ui.exploredColor
			if cell.id == view.currentId then
				color = ui.currentColor or config.DEFAULT_UI.currentColor
			end
			paint:setColor(color)
			canvas:drawRoundRect(left + pad, top + pad, left + tilePx - pad, top + tilePx - pad, pad * 2, pad * 2, paint)
			if ui.showLabels and cell.tile.name ~= nil and mode == "full" then
				paint:setColor(0xFFFFFFFF)
				paint:setTextSize(math.max(8, tilePx * 0.22))
				local label = cell.tile.name
				if #label > 10 then label = string.sub(label, 1, 9) .. "…" end
				canvas:drawText(label, left + pad, top + tilePx - pad, paint)
			end
			if cell.tile.flags ~= nil then
				local fi = 0
				for _, flag in pairs(cell.tile.flags) do
					local icon = config.FLAG_ICONS[flag] or "•"
					paint:setColor(ui.flagColors and ui.flagColors[flag] or 0xFFFFFFFF)
					paint:setTextSize(math.max(8, tilePx * 0.28))
					canvas:drawText(icon, left + pad + fi * (tilePx * 0.25), top + pad + paint:getTextSize(), paint)
					fi = fi + 1
				end
			end
			if cell.tile.quick ~= nil then
				local qi = 0
				for slot, q in pairs(cell.tile.quick) do
					if q ~= nil and q.cmd ~= nil and q.cmd ~= "" then
						paint:setColor(0xFFAAFFAA)
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
			paint:setColor((ui.fogColor or 0xFF1A1A22) + 0x55000000)
			canvas:drawRoundRect(left + pad, top + pad, left + tilePx - pad, top + tilePx - pad, pad, pad, paint)
			paint:setColor(0x44FFFFFF)
			paint:setTextSize(math.max(7, tilePx * 0.2))
			canvas:drawText("?", left + tilePx * 0.38, top + tilePx * 0.62, paint)
		end
	end

	if ui.showGrid then
		local Style = M.setPaintStyle(paint)
		paint:setColor(0x22FFFFFF)
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
	local ox = w / 2 - tilePx / 2
	local oy = h / 2 - tilePx / 2
	local dx = math.floor((x - ox) / tilePx + 0.5)
	local dy = math.floor((y - oy) / tilePx + 0.5)
	for _, cell in ipairs(view.cells) do
		if cell.dx == dx and cell.dy == dy then
			return cell
		end
	end
	return nil
end

return M
