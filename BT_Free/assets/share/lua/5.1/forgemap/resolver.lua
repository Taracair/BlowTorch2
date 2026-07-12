local M = {}

local function trim(s)
	if s == nil then return "" end
	return (tostring(s):gsub("^%s+", ""):gsub("%s+$", ""))
end

local function lower(s)
	return string.lower(trim(s))
end

local function stripAnsi(s)
	if s == nil then return "" end
	return tostring(s):gsub(string.char(27) .. "%[[0-9;]*m", "")
end

local function normalizeTitle(s)
	s = lower(stripAnsi(s))
	s = s:gsub("[%p%c]", " ")
	s = s:gsub("%s+", " ")
	return trim(s)
end

local function hashString(s)
	local h = 5381
	for i = 1, #s do
		h = (h * 33 + string.byte(s, i)) % 2147483647
	end
	return string.format("%08x", h)
end

local function sortedExitKeys(exits)
	local keys = {}
	if exits == nil then return keys end
	for k in pairs(exits) do table.insert(keys, k) end
	table.sort(keys)
	return keys
end

function M.fingerprintFromParts(title, exits, area, desc)
	local parts = {}
	table.insert(parts, normalizeTitle(title))
	table.insert(parts, lower(area or ""))
	if exits ~= nil then
		local keys = sortedExitKeys(exits)
		for _, k in ipairs(keys) do
			local v = exits[k]
			if v ~= nil and v ~= "" then
				table.insert(parts, k .. ":" .. tostring(v))
			else
				table.insert(parts, k .. ":?")
			end
		end
	end
	if desc ~= nil and desc ~= "" then
		local d = normalizeTitle(desc)
		if #d > 96 then d = string.sub(d, 1, 96) end
		table.insert(parts, "d:" .. d)
	end
	return "fp_" .. hashString(table.concat(parts, "|"))
end

function M.fingerprintFromGmcp(room)
	if room == nil then return nil, "none" end
	local vnum = room.num or room.id or room.vnum
	if vnum ~= nil and tostring(vnum) ~= "" and tonumber(vnum) ~= -1 then
		return "vn_" .. tostring(vnum), "gmcp_vnum"
	end
	if room.coord ~= nil and room.coord.x ~= nil and room.coord.y ~= nil then
		local cx, cy = room.coord.x, room.coord.y
		local cz = room.coord.z or 0
		return string.format("gc_%s_%s_%s_%s", tostring(cx), tostring(cy), tostring(cz), normalizeTitle(room.name)), "gmcp_coord"
	end
	local fp = M.fingerprintFromParts(room.name, room.exits, room.zone or room.area, room.desc)
	return fp, "gmcp_hash"
end

function M.fingerprintFromText(title, exitLine, area, desc)
	local exits = {}
	if exitLine ~= nil and exitLine ~= "" then
		local clean = lower(stripAnsi(exitLine))
		for word in clean:gmatch("[%a]+") do
			if #word <= 2 then exits[word] = "?" end
		end
	end
	return M.fingerprintFromParts(title, exits, area, desc), "text_hash"
end

function M.scoreMatch(tile, observation)
	if tile == nil or observation == nil then return 0 end
	local score = 0
	if observation.vnum ~= nil and tile.vnum ~= nil and tostring(observation.vnum) == tostring(tile.vnum) then
		return 100
	end
	if observation.fingerprint ~= nil and tile.fingerprint == observation.fingerprint then
		return 95
	end
	local nt = normalizeTitle(tile.name)
	local nn = normalizeTitle(observation.name)
	if nt ~= "" and nn ~= "" then
		if nt == nn then score = score + 60
		elseif string.find(nt, nn, 1, true) or string.find(nn, nt, 1, true) then
			score = score + 40
		end
	end
	if observation.area ~= nil and tile.area ~= nil and lower(observation.area) == lower(tile.area) then
		score = score + 10
	end
	return score
end

function M.bestMatch(store, observation, minScore)
	minScore = minScore or 55
	local bestId, bestScore = nil, 0
	for id, tile in pairs(store.getState().tiles) do
		local s = M.scoreMatch(tile, observation)
		if s > bestScore then bestId, bestScore = id, s end
	end
	if bestScore >= minScore then return bestId, bestScore end
	return nil, bestScore
end

function M.parseExitsLine(line)
	local exits = {}
	if line == nil then return exits end
	local clean = lower(stripAnsi(line))
	local body = clean:match("exits?:%s*(.+)$") or clean
	for token in body:gmatch("[%a][%a%-]*") do
		local dir = token
		if dir == "north" then dir = "n"
		elseif dir == "south" then dir = "s"
		elseif dir == "east" then dir = "e"
		elseif dir == "west" then dir = "w"
		elseif dir == "up" then dir = "u"
		elseif dir == "down" then dir = "d"
		elseif dir == "northeast" or dir == "ne" then dir = "ne"
		elseif dir == "northwest" or dir == "nw" then dir = "nw"
		elseif dir == "southeast" or dir == "se" then dir = "se"
		elseif dir == "southwest" or dir == "sw" then dir = "sw"
		end
		if #dir <= 2 then exits[dir] = "?" end
	end
	return exits
end

return M
