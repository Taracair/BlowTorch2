require("serialize")
local props = require("forgemapconfig")
local config = require("forgemap.config")
local store = require("forgemap.store")
local tracker = require("forgemap.tracker")
local pathfind = require("forgemap.pathfind")

local windowName = props.name

local function autoTrackEnabled()
	return config.AUTO_TRACK == true
end

local function pushState()
	WindowXCallS(windowName, "onMapState", serialize(store.exportData()))
end

function syncMapToWindow()
	pushState()
end

function OnBackgroundStartup()
	RegisterSpecialCommand("map", "toggleMapWindow")
	RegisterSpecialCommand("fmhere", "fmhereCommand")
	RegisterSpecialCommand("fmwalk", "fmwalkCommand")
	RegisterSpecialCommand("fmgo", "fmgoCommand")
	RegisterSpecialCommand("fmnote", "fmnoteCommand")
	RegisterSpecialCommand("fmflag", "fmflagCommand")
	syncMapToWindow()
end

function toggleMapWindow(arg)
	WindowXCallS(windowName, "toggleVisible", arg or "")
	return nil
end

function fmhereCommand(arg)
	onMapManualHere(arg ~= "" and arg or "Here")
	return nil
end

function fmwalkCommand(arg)
	local dir = string.lower(trim(arg or ""))
	if dir == "" then return nil end
	onMapMoveDir(dir)
	return nil
end

function fmgoCommand(arg)
	local targetId = trim(arg or "")
	local fromId = store.getCurrentTileId()
	if fromId == nil or targetId == "" then return nil end
	local path = pathfind.findPath(fromId, targetId)
	if path == nil then
		Note("ForgeMap: no path to " .. targetId)
		return nil
	end
	local sw = pathfind.pathToSpeedwalk(path)
	if sw == nil or sw == "" then return nil end
	Note("ForgeMap: walking " .. sw)
	SendToServer(".run " .. sw)
	return nil
end

function fmnoteCommand(arg)
	local t = store.getCurrentTile()
	if t == nil then return nil end
	t.note = arg or ""
	SaveSettings()
	pushState()
	return nil
end

function fmflagCommand(arg)
	local t = store.getCurrentTile()
	if t == nil then return nil end
	t.flags = t.flags or {}
	local flag = trim(arg or "")
	if flag == "" then return nil end
	t.flags[flag] = true
	SaveSettings()
	pushState()
	return nil
end

function trim(s)
	if s == nil then return "" end
	return (tostring(s):gsub("^%s+", ""):gsub("%s+$", ""))
end

function got_gmcp_room(room)
	if not autoTrackEnabled() then return end
	if room == nil then return end
	tracker.onGmcpRoom(room)
	SaveSettings()
	pushState()
end

function update_gmcp_area(area)
	if not autoTrackEnabled() then return end
	if area == nil then return end
	local name = area.name or area.zone or area
	if name ~= nil then store.ensureArea(tostring(name)) end
	SaveSettings()
	pushState()
end

function forgemap_room_title(name, line, matches)
	if not autoTrackEnabled() then return end
	local title = matches and matches[1] or name
	tracker.onTextRoom(title, nil, store.getState().currentArea, nil)
	SaveSettings()
	pushState()
end

function forgemap_room_exits(name, line, matches)
	if not autoTrackEnabled() then return end
	local cur = store.getCurrentTile()
	if cur ~= nil and line ~= nil then
		local exits = require("forgemap.resolver").parseExitsLine(line)
		for k, v in pairs(exits) do cur.exits[k] = v end
		SaveSettings()
		pushState()
	end
end

function forgemap_enter_room(name, line, matches)
	if not autoTrackEnabled() then return end
	local title = matches and matches[1] or line
	tracker.onTextRoom(title, nil, store.getState().currentArea, nil)
	SaveSettings()
	pushState()
end

function onMapWalkTo(tileId)
	if tileId == nil or tileId == "" then return end
	local fromId = store.getCurrentTileId()
	if fromId == nil then
		Note("ForgeMap: place start (+) first")
		return
	end
	local path = pathfind.findPath(fromId, tileId)
	if path == nil then
		Note("ForgeMap: no path")
		return
	end
	local steps = #path
	local sw = pathfind.pathToSpeedwalk(path)
	if sw ~= nil and sw ~= "" then
		Note("ForgeMap: walking " .. steps .. " step(s) — " .. sw)
		SendToServer(".run " .. sw)
	end
end

function onMapLinkDir(data)
	if data == nil then return end
	local sep = string.find(data, "\31")
	local dir, cmd
	if sep then
		dir = string.sub(data, 1, sep - 1)
		cmd = string.sub(data, sep + 1)
	else
		dir, cmd = data, data
	end
	tracker.linkDirection(dir, cmd)
	SaveSettings()
	pushState()
end

function onMapSetNote(data)
	if data == nil then return end
	local sep = string.find(data, "\31")
	if sep == nil then return end
	local id = string.sub(data, 1, sep - 1)
	local note = string.sub(data, sep + 1)
	local t = store.getTile(id)
	if t ~= nil then
		t.note = note or ""
		SaveSettings()
		pushState()
	end
end

function onMapSetQuick(data)
	if data == nil then return end
	local parts = {}
	for p in string.gmatch(data, "[^\31]+") do
		table.insert(parts, p)
	end
	if #parts < 4 then return end
	store.setQuick(parts[1], tonumber(parts[2]), parts[3], parts[4])
	SaveSettings()
	pushState()
end

function onMapRunQuick(data)
	if data == nil then return end
	local sep = string.find(data, "\31")
	local id, slot
	if sep then
		id = string.sub(data, 1, sep - 1)
		slot = tonumber(string.sub(data, sep + 1))
	else
		id = store.getCurrentTileId()
		slot = tonumber(data)
	end
	if id == nil or slot == nil then return end
	local t = store.getTile(id)
	if t == nil or t.quick == nil then return end
	local q = t.quick[slot]
	if q ~= nil and q.cmd ~= nil and q.cmd ~= "" then
		SendToServer(q.cmd)
	end
end

function onMapSetButtonSet(data)
	if data == nil then return end
	local sep = string.find(data, "\31")
	if sep == nil then return end
	local id = string.sub(data, 1, sep - 1)
	local setName = string.sub(data, sep + 1)
	local t = store.getTile(id)
	if t ~= nil then
		t.buttonSet = setName ~= "" and setName or nil
		SaveSettings()
		pushState()
	end
end

function onMapApplyButtonSet(tileId)
	local id = tileId
	if id == nil or id == "" then id = store.getCurrentTileId() end
	local t = store.getTile(id)
	if t == nil or t.buttonSet == nil or t.buttonSet == "" then return end
	if CallPlugin("button_window", "loadButtonSet", t.buttonSet) then
		return
	end
	SendToServer(".loadset " .. t.buttonSet)
end

function onMapManualHere(name)
	tracker.manualPlace(name)
	SaveSettings()
	pushState()
end

function onMapSelectTile(tileId)
	if tileId == nil or tileId == "" then return end
	store.setCurrentTile(tileId)
	store.markExplored(tileId)
	SaveSettings()
	pushState()
end

--- Walk the MUD and draw the map in one gesture.
--- Known link → send + move @. Unknown → create/link, send, move @.
function onMapMoveDir(dir)
	dir = string.lower(trim(dir or ""))
	if dir == "" then return end
	local curId = store.getCurrentTileId()
	if curId == nil then
		onMapManualHere("Start")
		curId = store.getCurrentTileId()
	end
	local cur = store.getCurrentTile()
	if cur == nil then return end

	local link = cur.links and cur.links[dir]
	if link ~= nil and link.to ~= nil then
		local cmd = link.cmd or dir
		SendToServer(cmd)
		tracker.notePendingDirection(dir)
		store.setCurrentTile(link.to)
		store.markExplored(link.to)
		SaveSettings()
		pushState()
		return
	end

	local x, y, z = store.neighborCoord(cur.x, cur.y, cur.z or 0, dir)
	local toId, toTile = store.getOrCreateAt(cur.area, x, y, z, dir:upper())
	store.linkTiles(curId, dir, toId, dir)
	store.markExplored(toId)
	if toTile ~= nil then
		toTile.explored = true
	end
	SendToServer(dir)
	tracker.notePendingDirection(dir)
	store.setCurrentTile(toId)
	SaveSettings()
	pushState()
end

-- Back-compat aliases for older window scripts / callbacks
function onMapWalkDir(dir)
	onMapMoveDir(dir)
end

function onMapExploreDir(dir)
	onMapMoveDir(dir)
end

function onMapRunGo(slot)
	slot = tonumber(slot or "")
	if slot == nil then return end
	local tile = store.getCurrentTile()
	if tile == nil then return end
	local g = store.getGoSlot(tile, slot)
	-- Only fire explicit tile-saved commands (not empty defaults).
	if g ~= nil and g.cmd ~= nil and g.cmd ~= "" then
		SendToServer(g.cmd)
	end
end

function onMapRunGoKey(key)
	key = trim(key or "")
	if key == "" then return end
	local tile = store.getCurrentTile()
	if tile == nil then return end
	local g = store.getGoByKey(tile, key)
	if g ~= nil and g.cmd ~= nil and g.cmd ~= "" then
		SendToServer(g.cmd)
	end
end

function onMapSetGo(data)
	if data == nil then return end
	local parts = {}
	for p in string.gmatch(data, "[^\31]+") do
		table.insert(parts, p)
	end
	if #parts < 4 then return end
	store.setGoSlot(parts[1], tonumber(parts[2]), parts[3], parts[4])
	SaveSettings()
	pushState()
end

function onMapSetGoKey(data)
	if data == nil then return end
	local parts = {}
	for p in string.gmatch(data, "[^\31]+") do
		table.insert(parts, p)
	end
	if #parts < 4 then return end
	store.setGoKey(parts[1], parts[2], parts[3], parts[4])
	SaveSettings()
	pushState()
end

function onMapSetUi(data)
	if data == nil or data == "" then return end
	local fn = loadstring(data)
	if fn ~= nil then
		local ok, ui = pcall(fn)
		if ok and ui ~= nil then store.setUi(ui) end
	end
	SaveSettings()
	pushState()
end

function OnPrepareXML(root)
	local node = root:getChild("forgemap")
	if node == nil then return end
	local listener = luajava.createProxy("android.sax.TextElementListener", {
		start = function() end,
		["end"] = function(body)
			local fn = loadstring(body)
			if fn == nil then return end
			local ok, payload = pcall(fn)
			if ok and payload ~= nil then store.importData(payload) end
			pushState()
		end
	})
	node:setTextElementListener(listener)
end

function OnXmlExport(out)
	out:startTag("", "forgemap")
	out:cdsect(serialize(store.exportData()))
	out:endTag("", "forgemap")
end

function OnOptionChanged(key, value)
	if key == "auto_open_map" then
		WindowXCallS(windowName, "setAutoOpen", value)
	end
end
