local config = require("forgemap.config")
local store = require("forgemap.store")
local renderer = require("forgemap.renderer")

local M = {}

M.MAP_DIRS = {
	{ id = "nw", label = "NW", dx = -1, dy = -1 },
	{ id = "n",  label = "N",  dx = 0,  dy = -1 },
	{ id = "ne", label = "NE", dx = 1,  dy = -1 },
	{ id = "w",  label = "W",  dx = -1, dy = 0 },
	{ id = "e",  label = "E",  dx = 1,  dy = 0 },
	{ id = "sw", label = "SW", dx = -1, dy = 1 },
	{ id = "s",  label = "S",  dx = 0,  dy = 1 },
	{ id = "se", label = "SE", dx = 1,  dy = 1 },
}

M.VERT_DIRS = {
	{ id = "u",   label = "U",   explore = "u" },
	{ id = "in",  label = "IN",  goKey = "in" },
	{ id = "out", label = "OUT", goKey = "out" },
	{ id = "d",   label = "D",   explore = "d" },
}

function M.computeLayout(viewW, viewH, headerPx, density)
	density = density or 1
	headerPx = headerPx or math.floor(config.HEADER_DP * density)
	local bodyTop = headerPx
	local bodyH = math.max(1, viewH - headerPx)
	local dockW = math.floor(math.min(viewW * 0.42, 168 * density))
	dockW = math.max(math.floor(118 * density), dockW)
	local mapW = math.max(1, viewW - dockW)
	local pad = math.max(2, math.floor(3 * density))
	local dockLeft = mapW
	local innerW = dockW - pad * 2
	local innerH = bodyH - pad * 2
	local gridH = math.floor(innerH * 0.58)
	local vertH = math.floor(innerH * 0.18)
	local goH = innerH - gridH - vertH - pad
	if goH < math.floor(22 * density) then
		goH = math.floor(22 * density)
		gridH = innerH - goH - vertH - pad * 2
	end
	local cols, rows = 3, 3
	local cellW = math.floor(innerW / cols)
	local cellH = math.floor(gridH / rows)
	local cellPx = math.max(math.floor(18 * density), math.min(cellW, cellH) - pad)
	local gridOx = dockLeft + pad + math.floor((innerW - cellPx * cols) / 2)
	local gridOy = bodyTop + pad + math.floor((gridH - cellPx * rows) / 2)
	local vertOy = bodyTop + pad + gridH + math.floor(pad / 2)
	local vertCellW = math.floor((innerW - pad * 3) / 4)
	local goOy = vertOy + vertH + math.floor(pad / 2)
	local goCols = config.MAX_GO_SLOTS or 4
	local goCellW = math.floor((innerW - pad * (goCols - 1)) / goCols)
	local buttons = {}
	for _, d in ipairs(M.MAP_DIRS) do
		local col, row = 1, 1
		if d.dx < 0 then col = 0 elseif d.dx > 0 then col = 2 end
		if d.dy < 0 then row = 0 elseif d.dy > 0 then row = 2 end
		local left = gridOx + col * cellPx
		local top = gridOy + row * cellPx
		table.insert(buttons, {
			kind = "map",
			id = d.id,
			label = d.label,
			explore = d.id,
			left = left, top = top,
			right = left + cellPx, bottom = top + cellPx,
		})
	end
	table.insert(buttons, {
		kind = "center",
		id = "here",
		label = "@",
		left = gridOx + cellPx, top = gridOy + cellPx,
		right = gridOx + cellPx * 2, bottom = gridOy + cellPx * 2,
	})
	for i, d in ipairs(M.VERT_DIRS) do
		local left = dockLeft + pad + (i - 1) * (vertCellW + pad)
		table.insert(buttons, {
			kind = d.explore and "vertical" or "gokey",
			id = d.id,
			label = d.label,
			explore = d.explore,
			goKey = d.goKey,
			left = left, top = vertOy,
			right = left + vertCellW, bottom = vertOy + vertH,
		})
	end
	for slot = 1, goCols do
		local left = dockLeft + pad + (slot - 1) * (goCellW + pad)
		table.insert(buttons, {
			kind = "go",
			id = "go" .. slot,
			slot = slot,
			left = left, top = goOy,
			right = left + goCellW, bottom = goOy + goH,
		})
	end
	return {
		mapWidth = mapW,
		dockLeft = dockLeft,
		dockWidth = dockW,
		bodyTop = bodyTop,
		bodyHeight = bodyH,
		buttons = buttons,
	}
end

function M.getGoLabel(tile, slot)
	local g = store.getGoSlot(tile, slot)
	if g == nil then return "+" end
	local lbl = g.label
	if lbl == nil or lbl == "" then
		lbl = g.cmd or "+"
	end
	if #lbl > 5 then lbl = string.sub(lbl, 1, 5) end
	return lbl
end

function M.draw(canvas, paint, layout, tile)
	if canvas == nil or layout == nil then return end
	local dockLeft = layout.dockLeft
	local w = dockLeft + layout.dockWidth
	renderer.setColor(paint, 255, 8, 10, 14)
	canvas:drawRect(dockLeft, layout.bodyTop, w, layout.bodyTop + layout.bodyHeight, paint)
	renderer.setColor(paint, 255, 28, 34, 48)
	canvas:drawRect(dockLeft, layout.bodyTop, dockLeft + 1, layout.bodyTop + layout.bodyHeight, paint)
	for _, btn in ipairs(layout.buttons) do
		local label = btn.label
		if btn.kind == "go" then
			label = M.getGoLabel(tile, btn.slot)
		elseif btn.kind == "gokey" and tile ~= nil then
			local g = store.getGoByKey(tile, btn.goKey)
			if g ~= nil and g.label ~= nil and g.label ~= "" then
				label = g.label
				if #label > 4 then label = string.sub(label, 1, 4) end
			end
		end
		if btn.kind == "center" then
			renderer.setColor(paint, 255, 255, 140, 20)
		elseif btn.kind == "map" or btn.kind == "vertical" then
			renderer.setColor(paint, 255, 32, 48, 68)
		elseif btn.kind == "go" or btn.kind == "gokey" then
			renderer.setColor(paint, 255, 42, 58, 42)
		end
		canvas:drawRoundRect(btn.left + 1, btn.top + 1, btn.right - 1, btn.bottom - 1, 5, 5, paint)
		renderer.setColor(paint, 255, 80, 180, 255)
		local Style = renderer.setPaintStyle(paint)
		paint:setStyle(Style.STROKE)
		paint:setStrokeWidth(2)
		canvas:drawRoundRect(btn.left + 2, btn.top + 2, btn.right - 2, btn.bottom - 2, 5, 5, paint)
		paint:setStyle(Style.FILL)
		renderer.setColor(paint, 255, 230, 240, 255)
		local size = math.max(8, math.min(11, (btn.bottom - btn.top) * 0.38))
		paint:setTextSize(size)
		local tw = paint:measureText(label)
		local tx = btn.left + (btn.right - btn.left - tw) / 2
		local ty = btn.top + (btn.bottom - btn.top) * 0.68
		canvas:drawText(label, tx, ty, paint)
	end
end

function M.hitTest(layout, x, y)
	if layout == nil then return nil end
	if x < layout.dockLeft then return nil end
	for _, btn in ipairs(layout.buttons) do
		if x >= btn.left and x < btn.right and y >= btn.top and y < btn.bottom then
			return btn
		end
	end
	return nil
end

return M
