-- Button layout wizard: pick a MUD pad pack and a size preset.
-- Pattern matches buttonlist.lua — module() sandboxes globals, so pull what we need.
local luajava = _G["luajava"]
local LinearLayoutParams = _G["LinearLayoutParams"]
local LinearLayout = _G["LinearLayout"]
local Button = _G["Button"]
local TextView = _G["TextView"]
local ScrollView = _G["ScrollView"]
local Color = _G["Color"]
local View = _G["View"]
local density = _G["density"]
local pairs = _G["pairs"]
local ipairs = _G["ipairs"]
local table = _G["table"]
local tonumber = _G["tonumber"]
local tostring = _G["tostring"]
local type = _G["type"]
local string = _G["string"]
local math = _G["math"]
local pcall = _G["pcall"]
local Note = _G["Note"]
local PluginXCallS = _G["PluginXCallS"]
local serialize = _G["serialize"]
local DialogInterface = _G["DialogInterface"]
local FILL_PARENT = _G["FILL_PARENT"]
local WRAP_CONTENT = _G["WRAP_CONTENT"]
module(...)

local context = nil
local dialog = nil
local selectedPackId = nil
local selectedSizeId = nil

local SIZE_CHOICES = {
	{ id = "compact",     label = "Compact (32)" },
	{ id = "comfortable", label = "Comfortable (42)" },
	{ id = "large",       label = "Large (56)" },
	{ id = "xl",          label = "Extra large (72)" },
	{ id = "fit_square",  label = "Fit to screen" },
}

local PACK_RADIO_BASE = 100
local SIZE_RADIO_BASE = 200

function init(pContext)
	context = pContext
end

local function truthy(v)
	return v == true or v == "true" or v == "1"
end

local function normalizePreset(raw)
	if raw == nil then return "" end
	local s = string.lower(tostring(raw))
	s = s:match("^%s*(.-)%s*$") or s
	if s == "fit" then return "fit_square" end
	if s == "extra_large" or s == "extralarge" or s == "extra large" then
		return "xl"
	end
	return s
end

local function fillParams()
	local fp = FILL_PARENT
	local wp = WRAP_CONTENT
	if fp == nil then fp = -1 end
	if wp == nil then wp = -2 end
	return luajava.new(LinearLayoutParams, fp, wp)
end

local function wrapParams()
	local wp = WRAP_CONTENT
	if wp == nil then wp = -2 end
	return luajava.new(LinearLayoutParams, wp, wp)
end

local function addSectionHeader(parent, text)
	local header = luajava.new(TextView, context)
	header:setText(text)
	header:setTextSize(16)
	header:setPadding(
		math.floor(4 * density), math.floor(12 * density),
		math.floor(4 * density), math.floor(4 * density))
	if Color ~= nil then
		header:setTextColor(Color:argb(255, 0x88, 0xCC, 0xFF))
	end
	header:setLayoutParams(fillParams())
	parent:addView(header)
end

local function addHint(parent, text)
	local hint = luajava.new(TextView, context)
	hint:setText(text)
	hint:setTextSize(11)
	hint:setPadding(
		math.floor(4 * density), math.floor(4 * density),
		math.floor(4 * density), math.floor(8 * density))
	hint:setLayoutParams(fillParams())
	parent:addView(hint)
end

local function packListFromState(state)
	local packs = state and state.packs
	if type(packs) ~= "table" then
		return {}
	end
	-- Prefer ipairs (array from LAYOUT_PACK_LIST); fall back to pairs.
	local out = {}
	local n = #packs
	if n > 0 then
		for i = 1, n do
			local p = packs[i]
			if type(p) == "table" and p.id ~= nil then
				out[#out + 1] = p
			end
		end
		return out
	end
	for _, p in pairs(packs) do
		if type(p) == "table" and p.id ~= nil then
			out[#out + 1] = p
		end
	end
	table.sort(out, function(a, b)
		return tostring(a.title or a.id) < tostring(b.title or b.id)
	end)
	return out
end

local function clearPendingAndDismiss()
	pcall(function()
		PluginXCallS("clearLayoutWizardPending", "")
	end)
	if dialog ~= nil then
		pcall(function() dialog:dismiss() end)
		dialog = nil
	end
end

local function applyAndDismiss()
	local pack = selectedPackId
	local size = selectedSizeId
	if pack == nil or tostring(pack) == "" then
		Note("\nButton layout: pick a button pack first.\n")
		return
	end
	if size == nil or tostring(size) == "" then
		size = "comfortable"
	end
	-- Window applies this after installPack finishes (onLayoutPackInstalled).
	_G["wizardPendingSize"] = tostring(size)
	local payload = serialize({ pack = tostring(pack), size = tostring(size) })
	pcall(function()
		PluginXCallS("applyLayoutWizardFinish", payload)
	end)
	if dialog ~= nil then
		pcall(function() dialog:dismiss() end)
		dialog = nil
	end
end

-- state: { pending, pack, size, packs = {{id,title,blurb},...} }
function showWizard(state)
	if context == nil then
		Note("\nButton layout wizard: not initialised.\n")
		return
	end
	if type(state) ~= "table" then
		Note("\nButton layout wizard: missing state.\n")
		return
	end

	if dialog ~= nil then
		pcall(function() dialog:dismiss() end)
		dialog = nil
	end

	local packs = packListFromState(state)
	local pending = truthy(state.pending)
	selectedPackId = nil
	if state.pack ~= nil and tostring(state.pack) ~= "" then
		selectedPackId = string.lower(tostring(state.pack))
	elseif packs[1] ~= nil then
		selectedPackId = string.lower(tostring(packs[1].id))
	end
	selectedSizeId = normalizePreset(state.size)
	if selectedSizeId == "" then
		selectedSizeId = "comfortable"
	end

	local fill = fillParams()
	local root = luajava.new(LinearLayout, context)
	root:setOrientation(LinearLayout.VERTICAL)
	root:setLayoutParams(fill)
	local pad = math.floor(12 * density)
	root:setPadding(pad, pad, pad, pad)

	local scroll = luajava.new(ScrollView, context)
	scroll:setLayoutParams(luajava.new(LinearLayoutParams,
		FILL_PARENT or -1, math.floor(360 * density)))
	local body = luajava.new(LinearLayout, context)
	body:setOrientation(LinearLayout.VERTICAL)
	body:setLayoutParams(fill)
	scroll:addView(body)
	root:addView(scroll)

	addSectionHeader(body, "Button pack")
	local packGroup = luajava.newInstance("android.widget.RadioGroup", context)
	packGroup:setOrientation(LinearLayout.VERTICAL)
	packGroup:setLayoutParams(fill)

	local packCheckId = -1
	for i, p in ipairs(packs) do
		local rb = luajava.newInstance("android.widget.RadioButton", context)
		local title = tostring(p.title or p.id)
		local blurb = p.blurb ~= nil and tostring(p.blurb) or ""
		if blurb ~= "" then
			rb:setText(title .. "\n" .. blurb)
		else
			rb:setText(title)
		end
		rb:setTextSize(13)
		local rid = PACK_RADIO_BASE + i
		rb:setId(rid)
		rb:setTag(tostring(p.id))
		packGroup:addView(rb, fillParams())
		if string.lower(tostring(p.id)) == selectedPackId then
			packCheckId = rid
		end
	end
	if #packs == 0 then
		addHint(body, "No layout packs available.")
	else
		body:addView(packGroup)
		if packCheckId >= 0 then
			packGroup:check(packCheckId)
		elseif packs[1] ~= nil then
			packGroup:check(PACK_RADIO_BASE + 1)
			selectedPackId = string.lower(tostring(packs[1].id))
		end
	end

	local packListener = luajava.createProxy(
		"android.widget.RadioGroup$OnCheckedChangeListener", {
		onCheckedChanged = function(group, checkedId)
			local child = group:findViewById(checkedId)
			if child ~= nil then
				local tag = child:getTag()
				if tag ~= nil then
					selectedPackId = string.lower(tostring(tag))
				end
			end
		end
	})
	packGroup:setOnCheckedChangeListener(packListener)

	addSectionHeader(body, "Button size")
	local sizeGroup = luajava.newInstance("android.widget.RadioGroup", context)
	sizeGroup:setOrientation(LinearLayout.VERTICAL)
	sizeGroup:setLayoutParams(fill)

	local sizeCheckId = -1
	for i, choice in ipairs(SIZE_CHOICES) do
		local rb = luajava.newInstance("android.widget.RadioButton", context)
		rb:setText(choice.label)
		rb:setTextSize(13)
		local rid = SIZE_RADIO_BASE + i
		rb:setId(rid)
		rb:setTag(choice.id)
		sizeGroup:addView(rb, fillParams())
		if choice.id == selectedSizeId then
			sizeCheckId = rid
		end
	end
	body:addView(sizeGroup)
	if sizeCheckId >= 0 then
		sizeGroup:check(sizeCheckId)
	else
		sizeGroup:check(SIZE_RADIO_BASE + 2) -- Comfortable
		selectedSizeId = "comfortable"
	end

	local sizeListener = luajava.createProxy(
		"android.widget.RadioGroup$OnCheckedChangeListener", {
		onCheckedChanged = function(group, checkedId)
			local child = group:findViewById(checkedId)
			if child ~= nil then
				local tag = child:getTag()
				if tag ~= nil then
					selectedSizeId = tostring(tag)
				end
			end
		end
	})
	sizeGroup:setOnCheckedChangeListener(sizeListener)

	addHint(body,
		"Apply rebuilds all five starter packs (compass, newbie, combat, "
		.. "explorer, social) from templates, then loads the one you pick — "
		.. "custom edits on those set names are overwritten. Size presets keep "
		.. "positions and only change tile size / fit. Re-open anytime from "
		.. "Overflow → Button layout… or .layoutwizard.")

	local builder = luajava.newInstance("android.app.AlertDialog$Builder", context)
	builder:setTitle("Button layout")
	builder:setView(root)

	local applyListener = luajava.createProxy(
		"android.content.DialogInterface$OnClickListener", {
		onClick = function(d, which)
			applyAndDismiss()
		end
	})
	local closeListener = luajava.createProxy(
		"android.content.DialogInterface$OnClickListener", {
		onClick = function(d, which)
			clearPendingAndDismiss()
		end
	})
	local skipListener = luajava.createProxy(
		"android.content.DialogInterface$OnClickListener", {
		onClick = function(d, which)
			clearPendingAndDismiss()
		end
	})
	local cancelListener = luajava.createProxy(
		"android.content.DialogInterface$OnCancelListener", {
		onCancel = function(d)
			-- Back / outside: same as Close — stop first-run nagging.
			pcall(function()
				PluginXCallS("clearLayoutWizardPending", "")
			end)
			dialog = nil
		end
	})

	builder:setPositiveButton("Apply", applyListener)
	builder:setNegativeButton("Close", closeListener)
	if pending then
		builder:setNeutralButton("Skip for now", skipListener)
	end
	builder:setOnCancelListener(cancelListener)

	dialog = builder:create()
	dialog:show()
end
