local config = require("forgemap.config")

local M = {}
local nextId = 1

local state = {
	version = 1,
	areas = {},
	tiles = {},
	currentTileId = nil,
	currentArea = "default",
	ui = nil,
	stats = { rooms = 0, links = 0 },
}

local function copyTable(t)
	if t == nil then return nil end
	local o = {}
	for k, v in pairs(t) do o[k] = v end
	return o
end

local function newId()
	local id = "t_" .. tostring(nextId)
	nextId = nextId + 1
	return id
end

function M.reset()
	state.areas = { default = { name = "default", z = 0 } }
	state.tiles = {}
	state.currentTileId = nil
	state.currentArea = "default"
	state.ui = copyTable(config.DEFAULT_UI)
	state.stats = { rooms = 0, links = 0 }
	nextId = 1
end

M.reset()

function M.getState()
	return state
end

function M.getUi()
	if state.ui == nil then state.ui = copyTable(config.DEFAULT_UI) end
	return state.ui
end

function M.setUi(ui)
	state.ui = copyTable(ui)
end

function M.getTile(id)
	return state.tiles[id]
end

function M.getCurrentTile()
	if state.currentTileId == nil then return nil end
	return state.tiles[state.currentTileId]
end

function M.setCurrentTile(id)
	state.currentTileId = id
end

function M.ensureArea(name)
	name = name or "default"
	if state.areas[name] == nil then
		state.areas[name] = { name = name, z = 0 }
	end
	state.currentArea = name
	return state.areas[name]
end

function M.createTile(opts)
	opts = opts or {}
	local id = opts.id or newId()
	local tile = {
		id = id,
		area = opts.area or state.currentArea or "default",
		x = opts.x or 0,
		y = opts.y or 0,
		z = opts.z or 0,
		name = opts.name or "Unknown",
		note = opts.note or "",
		color = opts.color or config.DEFAULT_UI.exploredColor,
		flags = opts.flags or {},
		quick = opts.quick or {},
		buttonSet = opts.buttonSet,
		fingerprint = opts.fingerprint,
		source = opts.source or "manual",
		vnum = opts.vnum,
		exits = opts.exits or {},
		links = opts.links or {},
		explored = opts.explored ~= false,
		visitCount = opts.visitCount or 0,
		lastSeen = opts.lastSeen or os.time(),
	}
	state.tiles[id] = tile
	state.stats.rooms = 0
	for _ in pairs(state.tiles) do state.stats.rooms = state.stats.rooms + 1 end
	return tile
end

function M.linkTiles(fromId, dir, toId, cmd)
	local from = state.tiles[fromId]
	local to = state.tiles[toId]
	if from == nil or to == nil then return false end
	from.links = from.links or {}
	from.links[dir] = { to = toId, cmd = cmd or dir }
	from.exits = from.exits or {}
	from.exits[dir] = toId
	if to.links == nil then to.links = {} end
	local rev = M.reverseDir(dir)
	if rev and to.links[rev] == nil then
		to.links[rev] = { to = fromId, cmd = rev }
		to.exits = to.exits or {}
		to.exits[rev] = fromId
	end
	state.stats.links = 0
	for _, t in pairs(state.tiles) do
		for _ in pairs(t.links or {}) do state.stats.links = state.stats.links + 1 end
	end
	return true
end

function M.reverseDir(dir)
	local map = {
		n = "s", s = "n", e = "w", w = "e", u = "d", d = "u",
		ne = "sw", sw = "ne", nw = "se", se = "nw",
	}
	return map[dir]
end

function M.findTileAt(area, x, y, z)
	for id, t in pairs(state.tiles) do
		if t.area == area and t.x == x and t.y == y and t.z == (z or 0) then
			return id, t
		end
	end
	return nil, nil
end

function M.findTileByFingerprint(fp)
	if fp == nil or fp == "" then return nil end
	for id, t in pairs(state.tiles) do
		if t.fingerprint == fp then return id, t end
	end
	return nil
end

function M.findTileByVnum(vnum)
	if vnum == nil then return nil end
	local vs = tostring(vnum)
	for id, t in pairs(state.tiles) do
		if t.vnum ~= nil and tostring(t.vnum) == vs then return id, t end
	end
	return nil
end

function M.neighborCoord(x, y, z, dir)
	local d = config.DIR_DELTA[dir]
	if d == nil then return x, y, z end
	return x + d[1], y + d[2], z + d[3]
end

function M.getOrCreateAt(area, x, y, z, name)
	local id, tile = M.findTileAt(area, x, y, z)
	if id ~= nil then return id, tile, false end
	tile = M.createTile({ area = area, x = x, y = y, z = z or 0, name = name or "?", explored = false })
	return tile.id, tile, true
end

function M.markExplored(id)
	local t = state.tiles[id]
	if t == nil then return end
	t.explored = true
	t.visitCount = (t.visitCount or 0) + 1
	t.lastSeen = os.time()
end

function M.setQuick(id, slot, label, cmd)
	local t = state.tiles[id]
	if t == nil then return end
	t.quick = t.quick or {}
	t.quick[slot] = { label = label or "", cmd = cmd or "" }
end

function M.exportData()
	return {
		version = state.version,
		areas = state.areas,
		tiles = state.tiles,
		currentTileId = state.currentTileId,
		currentArea = state.currentArea,
		ui = state.ui,
		nextId = nextId,
	}
end

function M.importData(data)
	if data == nil then M.reset(); return end
	state.version = data.version or 1
	state.areas = data.areas or { default = { name = "default", z = 0 } }
	state.tiles = data.tiles or {}
	state.currentTileId = data.currentTileId
	state.currentArea = data.currentArea or "default"
	state.ui = data.ui or copyTable(config.DEFAULT_UI)
	nextId = data.nextId or 1
	if nextId < 100 then
		for id in pairs(state.tiles) do
			local n = tonumber(string.match(id, "^t_(%d+)$"))
			if n and n >= nextId then nextId = n + 1 end
		end
	end
end

return M
