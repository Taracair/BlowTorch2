local serialize = require("serialize")
local props = require("forgemapconfig")
local store = require("forgemap.store")
local tracker = require("forgemap.tracker")
local pathfind = require("forgemap.pathfind")

local windowName = props.name

local function pushState()
	WindowXCallS(windowName, "onMapState", serialize(store.exportData()))
end

function OnBackgroundStartup()
	RegisterSpecialCommand("map", "toggleMapWindow")
	RegisterSpecialCommand("fmwalk", "fmwalkCommand")
	RegisterSpecialCommand("fmgo", "fmgoCommand")
	RegisterSpecialCommand("fmnote", "fmnoteCommand")
	RegisterSpecialCommand("fmflag", "fmflagCommand")
end

function toggleMapWindow(arg)
	WindowXCallS(windowName, "toggleVisible", arg or "")
	return nil
end

function fmwalkCommand(arg)
	local dir = string.lower(trim(arg or ""))
	if dir == "" then return nil end
	tracker.notePendingDirection(dir)
	SendToServer(dir)
	return nil
end

function fmgoCommand(arg)
	local targetId = trim(arg or "")
	local fromId = store.getCurrentTile()
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
	local curId = store.getCurrentTile()
	if curId == nil then return nil end
	local t = store.getTile(curId)
	if t ~= nil then
		t.note = arg or ""
		SaveSettings()
		pushState()
	end
	return nil
end

function fmflagCommand(arg)
	local curId = store.getCurrentTile()
	if curId == nil then return nil end
	local t = store.getTile(curId)
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
	if room == nil then return end
	tracker.onGmcpRoom(room)
	SaveSettings()
	pushState()
end

function update_gmcp_area(area)
	if area == nil then return end
	local name = area.name or area.zone or area
	if name ~= nil then store.ensureArea(tostring(name)) end
	SaveSettings()
	pushState()
end

function forgemap_room_title(name, line, matches)
	local title = matches and matches[1] or name
	tracker.onTextRoom(title, nil, store.getState().currentArea, nil)
	SaveSettings()
	pushState()
end

function forgemap_room_exits(name, line, matches)
	local curId = store.getCurrentTile()
	local cur = curId and store.getTile(curId) or nil
	if cur ~= nil and line ~= nil then
		local exits = require("forgemap.resolver").parseExitsLine(line)
		for k, v in pairs(exits) do cur.exits[k] = v end
		SaveSettings()
		pushState()
	end
end

function forgemap_enter_room(name, line, matches)
	local title = matches and matches[1] or line
	tracker.onTextRoom(title, nil, store.getState().currentArea, nil)
	SaveSettings()
	pushState()
end

function onMapWalkTo(tileId)
	if tileId == nil or tileId == "" then return end
	local fromId = store.getCurrentTile()
	local path = pathfind.findPath(fromId, tileId)
	if path == nil then
		Note("ForgeMap: path blocked")
		return
	end
	local sw = pathfind.pathToSpeedwalk(path)
	if sw ~= nil and sw ~= "" then
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

function onMapManualHere(name)
	tracker.manualPlace(name)
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
