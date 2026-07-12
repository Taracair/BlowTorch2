local config = require("forgemap.config")
local store = require("forgemap.store")
local resolver = require("forgemap.resolver")

local M = {}

local pendingDir = nil
local pendingAt = 0
local lastObservation = nil
local directionCmds = {
	n = "n", s = "s", e = "e", w = "w", u = "u", d = "d",
	ne = "ne", nw = "nw", se = "se", sw = "sw",
	north = "north", south = "south", east = "east", west = "west",
	up = "up", down = "down",
}

function M.setDirectionCommands(map)
	if map ~= nil then directionCmds = map end
end

function M.notePendingDirection(dir)
	if dir == nil or dir == "" then return end
	dir = string.lower(dir)
	pendingDir = dir
	pendingAt = os.time() * 1000
end

function M.consumePendingDirection()
	if pendingDir == nil then return nil end
	local now = os.time() * 1000
	if now - pendingAt > config.PENDING_MOVE_MS then
		pendingDir = nil
		return nil
	end
	local d = pendingDir
	pendingDir = nil
	return d
end

local function applyObservation(obs, source)
	lastObservation = obs
	local tileId = nil
	if obs.vnum ~= nil then tileId = store.findTileByVnum(obs.vnum) end
	if tileId == nil and obs.fingerprint ~= nil then tileId = store.findTileByFingerprint(obs.fingerprint) end
	if tileId == nil then
		tileId = resolver.bestMatch(store, obs, 55)
	end

	local prevId = store.getCurrentTile()
	local prev = prevId and store.getTile(prevId) or nil
	local dir = M.consumePendingDirection()

	if tileId == nil then
		local x, y, z = 0, 0, 0
		local area = obs.area or store.getState().currentArea or "default"
		if prev ~= nil then
			area = prev.area or area
			x, y, z = prev.x, prev.y, prev.z or 0
			if dir ~= nil then
				x, y, z = store.neighborCoord(x, y, z, dir)
			end
		end
		local id, tile, created = store.getOrCreateAt(area, x, y, z, obs.name or "?")
		tileId = id
		tile.name = obs.name or tile.name
		tile.fingerprint = obs.fingerprint or tile.fingerprint
		tile.vnum = obs.vnum or tile.vnum
		tile.source = source or tile.source
		if obs.exits ~= nil then
			for k, v in pairs(obs.exits) do tile.exits[k] = v end
		end
		if prev ~= nil and dir ~= nil and created then
			store.linkTiles(prevId, dir, tileId, directionCmds[dir] or dir)
		end
	else
		local tile = store.getTile(tileId)
		if obs.name ~= nil and obs.name ~= "" then tile.name = obs.name end
		if obs.fingerprint ~= nil then tile.fingerprint = obs.fingerprint end
		if obs.vnum ~= nil then tile.vnum = obs.vnum end
		if obs.exits ~= nil then
			for k, v in pairs(obs.exits) do tile.exits[k] = v end
		end
		if prev ~= nil and dir ~= nil and prevId ~= tileId then
			store.linkTiles(prevId, dir, tileId, directionCmds[dir] or dir)
		end
	end

	store.markExplored(tileId)
	store.setCurrentTile(tileId)
	return tileId, store.getTile(tileId)
end

function M.onGmcpRoom(room)
	local fp, src = resolver.fingerprintFromGmcp(room)
	local obs = {
		name = room.name,
		area = room.zone or room.area,
		vnum = room.num or room.id,
		fingerprint = fp,
		exits = room.exits,
	}
	if room.coord ~= nil then
		obs.coord = room.coord
	end
	return applyObservation(obs, src)
end

function M.onTextRoom(title, exitLine, area, desc)
	local fp, src = resolver.fingerprintFromText(title, exitLine, area, desc)
	local obs = {
		name = title,
		area = area,
		fingerprint = fp,
		exits = resolver.parseExitsLine(exitLine),
	}
	return applyObservation(obs, src)
end

function M.manualPlace(name)
	local obs = { name = name or "Here", fingerprint = resolver.fingerprintFromParts(name, {}, store.getState().currentArea, nil) }
	return applyObservation(obs, "manual")
end

function M.linkDirection(dir, cmd)
	local curId = store.getCurrentTile()
	if curId == nil then return false end
	local cur = store.getTile(curId)
	local x, y, z = store.neighborCoord(cur.x, cur.y, cur.z or 0, dir)
	local toId, toTile, _ = store.getOrCreateAt(cur.area, x, y, z, "?")
	store.linkTiles(curId, dir, toId, cmd or dir)
	return true, toId
end

function M.getLastObservation()
	return lastObservation
end

return M
