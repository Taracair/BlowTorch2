local store = require("forgemap.store")

local M = {}

local function tileCost(tile)
	if tile == nil then return 1 end
	for _, f in pairs(tile.flags or {}) do
		if f == "danger" then return 8 end
	end
	return 1
end

function M.buildGraph(area, z)
	local graph = {}
	for id, tile in pairs(store.getState().tiles) do
		if tile.area == area and (tile.z or 0) == (z or 0) and tile.explored then
			graph[id] = {}
			for dir, dest in pairs(tile.links or {}) do
				local toId = type(dest) == "table" and dest.to or dest
				if toId ~= nil and store.getTile(toId) ~= nil then
					graph[id][dir] = toId
				end
			end
		end
	end
	return graph
end

local function heuristic(idA, idB)
	local a, b = store.getTile(idA), store.getTile(idB)
	if a == nil or b == nil then return 0 end
	return math.abs(a.x - b.x) + math.abs(a.y - b.y) + math.abs((a.z or 0) - (b.z or 0))
end

function M.findPath(fromId, toId)
	if fromId == nil or toId == nil or fromId == toId then return {} end
	local from = store.getTile(fromId)
	if from == nil then return nil end
	local graph = M.buildGraph(from.area, from.z or 0)
	local open = { fromId }
	local came = {}
	local g = { [fromId] = 0 }
	local f = { [fromId] = heuristic(fromId, toId) }

	local function lowest()
		local best, bestF = nil, nil
		for _, id in ipairs(open) do
			local fv = f[id] or 999999
			if bestF == nil or fv < bestF then best, bestF = id, fv end
		end
		return best
	end

	while #open > 0 do
		local current = lowest()
		if current == nil then break end
		if current == toId then
			local path = {}
			local node = toId
			while node ~= nil do
				table.insert(path, 1, node)
				local step = came[node]
				node = step and step.from or nil
			end
			return path
		end
		for i, v in ipairs(open) do
			if v == current then table.remove(open, i); break end
		end
		local neighbors = graph[current] or {}
		for dir, nid in pairs(neighbors) do
			local tg = (g[current] or 0) + tileCost(store.getTile(nid))
			if g[nid] == nil or tg < g[nid] then
				came[nid] = { from = current, dir = dir }
				g[nid] = tg
				f[nid] = tg + heuristic(nid, toId)
				local found = false
				for _, v in ipairs(open) do if v == nid then found = true; break end end
				if not found then table.insert(open, nid) end
			end
		end
	end
	return nil
end

function M.pathToSpeedwalk(path)
	if path == nil or #path < 2 then return "" end
	local out = {}
	for i = 2, #path do
		local step = nil
		for dir, dest in pairs(store.getTile(path[i - 1]).links or {}) do
			local toId = type(dest) == "table" and dest.to or dest
			if toId == path[i] then
				local cmd = type(dest) == "table" and dest.cmd or dir
				table.insert(out, cmd)
				break
			end
		end
	end
	return table.concat(out, "")
end

function M.pathDirections(path)
	if path == nil or #path < 2 then return {} end
	local dirs = {}
	for i = 2, #path do
		for dir, dest in pairs(store.getTile(path[i - 1]).links or {}) do
			local toId = type(dest) == "table" and dest.to or dest
			if toId == path[i] then
				table.insert(dirs, dir)
				break
			end
		end
	end
	return dirs
end

return M
