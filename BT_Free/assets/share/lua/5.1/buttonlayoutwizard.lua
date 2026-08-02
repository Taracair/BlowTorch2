-- Button layout wizard: pick packs, names, size, align, colors.
-- Pattern matches buttonlist.lua — module() sandboxes globals, so pull what we need.
-- Never index _G after module(...); pass Apply state only via serialize payload.
local luajava = _G["luajava"]
local LinearLayoutParams = _G["LinearLayoutParams"]
local LinearLayout = _G["LinearLayout"]
local Button = _G["Button"]
local TextView = _G["TextView"]
local ScrollView = _G["ScrollView"]
local EditText = _G["EditText"]
local Color = _G["Color"]
local View = _G["View"]
local CheckBox = _G["CheckBox"]
local RadioGroup = _G["RadioGroup"]
local RadioButton = _G["RadioButton"]
local SeekBar = _G["SeekBar"]
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

local SIZE_CHOICES = {
	{ id = "compact",     label = "Compact (32)" },
	{ id = "comfortable", label = "Comfortable (42)" },
	{ id = "large",       label = "Large (56)" },
	{ id = "xl",          label = "Extra large (72)" },
	{ id = "fit_square",  label = "Fit to screen" },
}

local ALIGN_CHOICES = {
	{ id = "left",   label = "Left" },
	{ id = "center", label = "Center" },
	{ id = "right",  label = "Right" },
}

local SIZE_RADIO_BASE = 400
local ALIGN_RADIO_BASE = 500
local LOAD_RADIO_BASE = 600

local DEFAULT_PRIMARY = nil
local DEFAULT_SELECTED = nil

function init(pContext)
	context = pContext
	if Color ~= nil then
		DEFAULT_PRIMARY = Color:argb(0x88, 0x00, 0x00, 0xFF)
		DEFAULT_SELECTED = Color:argb(0x88, 0x00, 0xFF, 0x00)
	else
		DEFAULT_PRIMARY = 0x880000FF
		DEFAULT_SELECTED = 0x8800FF00
	end
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

local function weightParams(weight)
	local fp = FILL_PARENT or -1
	local wp = WRAP_CONTENT or -2
	local lp = luajava.new(LinearLayoutParams, 0, wp, weight or 1)
	return lp
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

local function existingNameSet(existingNames)
	local set = {}
	if type(existingNames) ~= "table" then
		return set
	end
	local n = #existingNames
	if n > 0 then
		for i = 1, n do
			local name = existingNames[i]
			if name ~= nil and tostring(name) ~= "" then
				set[string.lower(tostring(name))] = true
			end
		end
		return set
	end
	for _, name in pairs(existingNames) do
		if name ~= nil and tostring(name) ~= "" then
			set[string.lower(tostring(name))] = true
		end
	end
	return set
end

local RESERVED_SET_NAMES = { default = true, tutorial = true }

-- Mirror of buttonserver.suggestSetName's cleanup. The EditText takes anything,
-- but the name ends up as a buttonsets key AND inside the ".loadset <name>"
-- cross-links rewriteCommandLinks writes into the pack tiles. Those links are
-- read back with "^%.loadset%s+([%w_%-]+)%s*$", so a name with a space or
-- punctuation produces a MORE/jump button that silently loads nothing. Sanitize
-- at Apply, not per keystroke: rewriting the field under the cursor fights the
-- player typing.
local function sanitizeSetName(raw)
	local s = string.lower(tostring(raw or ""))
	s = s:match("^%s*(.-)%s*$") or s
	s = string.gsub(s, "%s+", "_")
	s = string.gsub(s, "[^a-z0-9_%-]", "")
	if #s > 24 then s = string.sub(s, 1, 24) end
	return s
end

local function nameExists(name, existingSet)
	if name == nil or tostring(name) == "" then
		return false
	end
	local key = string.lower(tostring(name))
	if RESERVED_SET_NAMES[key] then
		return true
	end
	return existingSet[key] == true
end

function suggestSetName(base, existingNames)
	local existing = existingNameSet(existingNames)
	existing.default = true
	existing.tutorial = true
	local root = sanitizeSetName(base)
	if root == "" then root = "pack" end
	if not existing[root] then
		return root
	end
	local i = 2
	while existing[root .. "_" .. tostring(i)] do
		i = i + 1
	end
	return root .. "_" .. tostring(i)
end

local function argbWithAlpha(color, alpha)
	local c = tonumber(color) or 0
	-- Java ARGB ints may arrive signed (alpha bit set).
	if c < 0 then
		c = c + 4294967296
	end
	local a = math.floor(tonumber(alpha) or 0x88)
	if a < 0 then a = 0 end
	if a > 255 then a = 255 end
	local r = math.floor(c / 0x10000) % 256
	local g = math.floor(c / 0x100) % 256
	local b = math.floor(c) % 256
	if Color ~= nil then
		return Color:argb(a, r, g, b)
	end
	local u = a * 0x1000000 + r * 0x10000 + g * 0x100 + b
	if u >= 2147483648 then
		return u - 4294967296
	end
	return u
end

local function colorAlpha(color)
	local c = tonumber(color) or 0
	if c < 0 then
		c = c + 4294967296
	end
	return math.floor(c / 0x1000000) % 256
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

local function dismissWizardOnly()
	if dialog ~= nil then
		pcall(function() dialog:dismiss() end)
		dialog = nil
	end
end

-- Called from buttonwindow after a successful install callback.
function dismissAfterApply()
	dismissWizardOnly()
end

local function bindCheckBox()
	if CheckBox ~= nil then
		return CheckBox
	end
	return luajava.bindClass("android.widget.CheckBox")
end

local function bindRadioGroup()
	if RadioGroup ~= nil then
		return RadioGroup
	end
	return luajava.bindClass("android.widget.RadioGroup")
end

local function bindRadioButton()
	if RadioButton ~= nil then
		return RadioButton
	end
	return luajava.bindClass("android.widget.RadioButton")
end

local function bindEditText()
	if EditText ~= nil then
		return EditText
	end
	return luajava.bindClass("android.widget.EditText")
end

local function bindSeekBar()
	if SeekBar ~= nil then
		return SeekBar
	end
	return luajava.bindClass("android.widget.SeekBar")
end

-- state: {
--   pending, pack, size, align, mode, colors,
--   packs = {{id,title,blurb},...},
--   existingNames = {"compass", ...},
-- }
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
	local existingNames = state.existingNames
	if type(existingNames) ~= "table" then
		existingNames = {}
	end
	local existingSet = existingNameSet(existingNames)

	local selectedSizeId = normalizePreset(state.size)
	if selectedSizeId == "" then
		selectedSizeId = "comfortable"
	end
	local selectedAlignId = string.lower(tostring(state.align or "right"))
	if selectedAlignId ~= "left" and selectedAlignId ~= "center" and selectedAlignId ~= "right" then
		selectedAlignId = "right"
	end
	-- Packs install complete. The Simple/Advanced radio was removed: it changed
	-- 3 tiles on compass, 1 on explorer and social, and nothing whatsoever on
	-- newbie, which is not a choice two words can explain. Still sent so an older
	-- service build reads a mode it understands.
	local selectedModeId = "advanced"

	local primaryColor = DEFAULT_PRIMARY
	local selectedColor = DEFAULT_SELECTED
	if type(state.colors) == "table" then
		if state.colors.primary ~= nil then
			primaryColor = tonumber(state.colors.primary) or primaryColor
		end
		if state.colors.selected ~= nil then
			selectedColor = tonumber(state.colors.selected) or selectedColor
		end
	elseif state.primaryColor ~= nil then
		primaryColor = tonumber(state.primaryColor) or primaryColor
	end
	local colorAlphaValue = colorAlpha(primaryColor)
	if colorAlphaValue == 0 and colorAlpha(selectedColor) ~= 0 then
		colorAlphaValue = colorAlpha(selectedColor)
	end
	if colorAlphaValue == 0 then
		colorAlphaValue = 0x88
	end
	primaryColor = argbWithAlpha(primaryColor, colorAlphaValue)
	selectedColor = argbWithAlpha(selectedColor, colorAlphaValue)

	-- Per-pack UI row state.
	local rows = {}
	local selectedLoadKey = nil
	local overwriteAck = false

	local CheckBoxCls = bindCheckBox()
	local RadioGroupCls = bindRadioGroup()
	local RadioButtonCls = bindRadioButton()
	local EditTextCls = bindEditText()
	local SeekBarCls = bindSeekBar()

	local fill = fillParams()
	local root = luajava.new(LinearLayout, context)
	root:setOrientation(LinearLayout.VERTICAL)
	root:setLayoutParams(fill)
	local pad = math.floor(12 * density)
	root:setPadding(pad, pad, pad, pad)

	local scroll = luajava.new(ScrollView, context)
	scroll:setLayoutParams(luajava.new(LinearLayoutParams,
		FILL_PARENT or -1, math.floor(420 * density)))
	local body = luajava.new(LinearLayout, context)
	body:setOrientation(LinearLayout.VERTICAL)
	body:setLayoutParams(fill)
	scroll:addView(body)
	root:addView(scroll)

	if pending then
		local banner = luajava.new(TextView, context)
		banner:setText(
			"This is the first launch of this MUD — the button layout wizard "
			.. "can help set up your buttons.")
		banner:setTextSize(13)
		banner:setPadding(
			math.floor(4 * density), math.floor(4 * density),
			math.floor(4 * density), math.floor(8 * density))
		banner:setLayoutParams(fill)
		body:addView(banner)
	end

	addSectionHeader(body, "Install packs")
	addHint(body,
		"Apply only touches the named sets you check; other button sets are left alone.")

	local loadGroup = luajava.new(RadioGroupCls, context)
	loadGroup:setOrientation(LinearLayout.VERTICAL)
	loadGroup:setLayoutParams(fill)

	local function currentCheckedRows()
		local out = {}
		for _, row in ipairs(rows) do
			if row.checked then
				out[#out + 1] = row
			end
		end
		return out
	end

	local function radioLabel(row)
		local typed = tostring(row.setName or "")
		local name = sanitizeSetName(typed)
		if name == "" then name = tostring(row.packId) end
		return tostring(row.title or row.packId) .. " → " .. name
	end

	local function rebuildLoadRadios()
		-- clearCheck() BEFORE removeAllViews, while the checked child is still
		-- attached. RadioGroup.check(id) early-returns when id == mCheckedId, and
		-- removeAllViews does not reset mCheckedId — so re-adding a fresh (and
		-- therefore unchecked) RadioButton under the id that was checked before
		-- left the group with nothing visibly selected while selectedLoadKey still
		-- said otherwise. The listener fires with -1 here; findViewById(-1) is nil,
		-- so it no-ops and leaves selectedLoadKey alone for the keep test below.
		loadGroup:clearCheck()
		loadGroup:removeAllViews()
		for _, row in ipairs(rows) do
			row.loadRadio = nil
		end
		local checked = currentCheckedRows()
		if #checked == 0 then
			selectedLoadKey = nil
			return
		end
		local keep = nil
		for i, row in ipairs(checked) do
			local rb = luajava.new(RadioButtonCls, context)
			rb:setText(radioLabel(row))
			rb:setTextSize(13)
			local rid = LOAD_RADIO_BASE + i
			rb:setId(rid)
			rb:setTag(row.packId)
			loadGroup:addView(rb, fillParams())
			row.loadRadio = rb
			if selectedLoadKey ~= nil and tostring(selectedLoadKey) == tostring(row.packId) then
				keep = rid
			end
		end
		if keep ~= nil then
			loadGroup:check(keep)
		else
			loadGroup:check(LOAD_RADIO_BASE + 1)
			selectedLoadKey = checked[1].packId
		end
	end

	-- Warn against the name Apply will actually use, not the raw text: a player
	-- who types "Compass" must still be told it overwrites the existing
	-- "compass", and one who types "My Pad" should see it lands as "my_pad".
	local function updateRowWarning(row)
		local typed = tostring(row.setName or "")
		local name = sanitizeSetName(typed)
		local exists = nameExists(name, existingSet)
		row.collides = exists
		if row.warnView ~= nil then
			local msg = ""
			if name ~= "" and name ~= typed then
				msg = "Saved as \"" .. name .. "\""
			end
			if exists then
				if msg ~= "" then msg = msg .. " — " end
				msg = msg .. "Set exists — will overwrite if you Apply"
			end
			if msg ~= "" then
				row.warnView:setText(msg)
				row.warnView:setVisibility(View.VISIBLE)
			else
				row.warnView:setText("")
				row.warnView:setVisibility(View.GONE)
			end
		end
	end

	for i, p in ipairs(packs) do
		local packId = string.lower(tostring(p.id))
		local title = tostring(p.title or p.id)
		local blurb = p.blurb ~= nil and tostring(p.blurb) or ""
		local suggested = suggestSetName(packId, existingNames)
		-- First-run: check all packs so starters are one Apply away.
		-- Re-entry: only the first pack, so Apply stays scoped.
		local defaultChecked = pending or (i == 1)

		local rowBox = luajava.new(LinearLayout, context)
		rowBox:setOrientation(LinearLayout.VERTICAL)
		rowBox:setLayoutParams(fill)
		rowBox:setPadding(0, math.floor(4 * density), 0, math.floor(4 * density))

		local top = luajava.new(LinearLayout, context)
		top:setOrientation(LinearLayout.HORIZONTAL)
		top:setLayoutParams(fill)

		local cb = luajava.new(CheckBoxCls, context)
		cb:setText(title)
		cb:setTextSize(13)
		cb:setChecked(defaultChecked)
		cb:setLayoutParams(wrapParams())
		top:addView(cb)

		local nameEdit = luajava.new(EditTextCls, context)
		nameEdit:setText(suggested)
		nameEdit:setSingleLine(true)
		nameEdit:setHint("set name")
		nameEdit:setTextSize(13)
		nameEdit:setLayoutParams(weightParams(1))
		top:addView(nameEdit)

		rowBox:addView(top)

		if blurb ~= "" then
			local blurbView = luajava.new(TextView, context)
			blurbView:setText(blurb)
			blurbView:setTextSize(11)
			blurbView:setPadding(math.floor(28 * density), 0, 0, 0)
			blurbView:setLayoutParams(fill)
			rowBox:addView(blurbView)
		end

		local warn = luajava.new(TextView, context)
		warn:setTextSize(11)
		if Color ~= nil then
			warn:setTextColor(Color:argb(255, 0xFF, 0xAA, 0x44))
		end
		warn:setPadding(math.floor(28 * density), 0, 0, 0)
		warn:setLayoutParams(fill)
		warn:setVisibility(View.GONE)
		rowBox:addView(warn)

		local row = {
			packId = packId,
			title = title,
			checked = defaultChecked,
			setName = suggested,
			collides = false,
			warnView = warn,
			checkBox = cb,
			nameEdit = nameEdit,
		}
		rows[#rows + 1] = row
		updateRowWarning(row)

		local cbListener = luajava.createProxy(
			"android.widget.CompoundButton$OnCheckedChangeListener", {
			onCheckedChanged = function(buttonView, isChecked)
				row.checked = isChecked == true
				rebuildLoadRadios()
			end
		})
		cb:setOnCheckedChangeListener(cbListener)

		local watcher = luajava.createProxy("android.text.TextWatcher", {
			beforeTextChanged = function(s, start, count, after) end,
			onTextChanged = function(s, start, before, count) end,
			afterTextChanged = function(s)
				local text = ""
				if s ~= nil then
					text = tostring(s:toString())
				end
				text = text:match("^%s*(.-)%s*$") or text
				row.setName = text
				updateRowWarning(row)
				-- Only relabel. Rebuilding the group on every keystroke tore down
				-- and recreated the radios under the cursor for no reason.
				if row.loadRadio ~= nil then
					pcall(function() row.loadRadio:setText(radioLabel(row)) end)
				end
			end
		})
		nameEdit:addTextChangedListener(watcher)

		body:addView(rowBox)
	end

	if #packs == 0 then
		addHint(body, "No layout packs available.")
	end

	addSectionHeader(body, "Load this one after install")
	body:addView(loadGroup)
	local loadListener = luajava.createProxy(
		"android.widget.RadioGroup$OnCheckedChangeListener", {
		onCheckedChanged = function(group, checkedId)
			local child = group:findViewById(checkedId)
			if child ~= nil then
				local tag = child:getTag()
				if tag ~= nil then
					selectedLoadKey = tostring(tag)
				end
			end
		end
	})
	loadGroup:setOnCheckedChangeListener(loadListener)
	rebuildLoadRadios()

	addSectionHeader(body, "Button size")
	local sizeGroup = luajava.new(RadioGroupCls, context)
	sizeGroup:setOrientation(LinearLayout.VERTICAL)
	sizeGroup:setLayoutParams(fill)
	local sizeCheckId = -1
	for i, choice in ipairs(SIZE_CHOICES) do
		local rb = luajava.new(RadioButtonCls, context)
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
		sizeGroup:check(SIZE_RADIO_BASE + 2)
		selectedSizeId = "comfortable"
	end
	sizeGroup:setOnCheckedChangeListener(luajava.createProxy(
		"android.widget.RadioGroup$OnCheckedChangeListener", {
		onCheckedChanged = function(group, checkedId)
			local child = group:findViewById(checkedId)
			if child ~= nil and child:getTag() ~= nil then
				selectedSizeId = tostring(child:getTag())
			end
		end
	}))

	addSectionHeader(body, "Align")
	local alignGroup = luajava.new(RadioGroupCls, context)
	alignGroup:setOrientation(LinearLayout.HORIZONTAL)
	alignGroup:setLayoutParams(fill)
	local alignCheckId = -1
	for i, choice in ipairs(ALIGN_CHOICES) do
		local rb = luajava.new(RadioButtonCls, context)
		rb:setText(choice.label)
		rb:setTextSize(13)
		local rid = ALIGN_RADIO_BASE + i
		rb:setId(rid)
		rb:setTag(choice.id)
		alignGroup:addView(rb, wrapParams())
		if choice.id == selectedAlignId then
			alignCheckId = rid
		end
	end
	body:addView(alignGroup)
	if alignCheckId >= 0 then
		alignGroup:check(alignCheckId)
	else
		alignGroup:check(ALIGN_RADIO_BASE + 3)
		selectedAlignId = "right"
	end
	alignGroup:setOnCheckedChangeListener(luajava.createProxy(
		"android.widget.RadioGroup$OnCheckedChangeListener", {
		onCheckedChanged = function(group, checkedId)
			local child = group:findViewById(checkedId)
			if child ~= nil and child:getTag() ~= nil then
				selectedAlignId = tostring(child:getTag())
			end
		end
	}))

	addSectionHeader(body, "Color")
	local colorRow = luajava.new(LinearLayout, context)
	colorRow:setOrientation(LinearLayout.HORIZONTAL)
	colorRow:setLayoutParams(fill)
	colorRow:setGravity(16) -- CENTER_VERTICAL

	local swatch = luajava.new(View, context)
	local swatchSize = math.floor(36 * density)
	swatch:setLayoutParams(luajava.new(LinearLayoutParams, swatchSize, swatchSize))
	swatch:setBackgroundColor(primaryColor)
	colorRow:addView(swatch)

	local pickBtn = luajava.new(Button, context)
	pickBtn:setText("Pick color…")
	pickBtn:setTextSize(13)
	pickBtn:setLayoutParams(wrapParams())
	colorRow:addView(pickBtn)
	body:addView(colorRow)

	local alphaLabel = luajava.new(TextView, context)
	alphaLabel:setText("Alpha: " .. tostring(colorAlphaValue))
	alphaLabel:setTextSize(12)
	alphaLabel:setLayoutParams(fill)
	body:addView(alphaLabel)

	local alphaBar = luajava.new(SeekBarCls, context)
	alphaBar:setMax(255)
	alphaBar:setProgress(colorAlphaValue)
	alphaBar:setLayoutParams(fill)
	body:addView(alphaBar)

	local function refreshSwatch()
		primaryColor = argbWithAlpha(primaryColor, colorAlphaValue)
		selectedColor = argbWithAlpha(selectedColor, colorAlphaValue)
		pcall(function()
			swatch:setBackgroundColor(primaryColor)
			swatch:invalidate()
		end)
		alphaLabel:setText("Alpha: " .. tostring(colorAlphaValue))
	end

	alphaBar:setOnSeekBarChangeListener(luajava.createProxy(
		"android.widget.SeekBar$OnSeekBarChangeListener", {
		onProgressChanged = function(seekBar, progress, fromUser)
			colorAlphaValue = math.floor(tonumber(progress) or 0x88)
			refreshSwatch()
		end,
		onStartTrackingTouch = function(seekBar) end,
		onStopTrackingTouch = function(seekBar) end
	}))

	local function openColorPicker()
		local listener = luajava.createProxy(
			"com.resurrection.blowtorch2.lib.button.ColorPickerDialog$OnColorChangedListener", {
			colorChanged = function(color)
				primaryColor = tonumber(color) or primaryColor
				-- Keep selected as green-ish with the same alpha.
				local a = colorAlpha(primaryColor)
				colorAlphaValue = a
				if colorAlphaValue == 0 then colorAlphaValue = 0x88 end
				selectedColor = argbWithAlpha(DEFAULT_SELECTED, colorAlphaValue)
				primaryColor = argbWithAlpha(primaryColor, colorAlphaValue)
				pcall(function() alphaBar:setProgress(colorAlphaValue) end)
				refreshSwatch()
			end
		})
		local ok = pcall(function()
			local dlg = luajava.newInstance(
				"com.resurrection.blowtorch2.lib.button.ColorPickerDialog",
				context, listener, primaryColor)
			dlg:show()
		end)
		if not ok then
			-- Fallback: nudge RGB toward blue primary with current alpha.
			primaryColor = argbWithAlpha(DEFAULT_PRIMARY, colorAlphaValue)
			selectedColor = argbWithAlpha(DEFAULT_SELECTED, colorAlphaValue)
			refreshSwatch()
			Note("\nColor picker unavailable; restored default blue/green.\n")
		end
	end

	pickBtn:setOnClickListener(luajava.createProxy(
		"android.view.View$OnClickListener", {
		onClick = function(v)
			openColorPicker()
		end
	}))

	addHint(body,
		"Edit later: Overflow → Edit buttons, or long-press the ⋮ (three dots).")
	addHint(body,
		"Re-open anytime from Options → Button → Load button set from wizard, "
		.. "or .layoutwizard.")

	local function buildPayload()
		local installs = {}
		local loadSet = nil
		for _, row in ipairs(rows) do
			if row.checked then
				local setName = sanitizeSetName(row.setName)
				if setName == "" then
					setName = tostring(row.packId)
				end
				local collides = nameExists(setName, existingSet)
				installs[#installs + 1] = {
					packId = tostring(row.packId),
					setName = setName,
					overwrite = collides or overwriteAck,
				}
				if selectedLoadKey ~= nil
					and tostring(selectedLoadKey) == tostring(row.packId) then
					loadSet = setName
				end
			end
		end
		if loadSet == nil and installs[1] ~= nil then
			loadSet = installs[1].setName
		end
		return {
			installs = installs,
			loadSet = loadSet,
			align = selectedAlignId or "right",
			colors = {
				primary = primaryColor,
				selected = selectedColor,
			},
			size = selectedSizeId or "comfortable",
			mode = selectedModeId,
		}
	end

	local function collidingNames(payload)
		local names = {}
		for _, inst in ipairs(payload.installs or {}) do
			if nameExists(inst.setName, existingSet) then
				names[#names + 1] = tostring(inst.setName)
			end
		end
		return names
	end

	local function sendApply(payload)
		for _, inst in ipairs(payload.installs or {}) do
			if nameExists(inst.setName, existingSet) then
				inst.overwrite = true
			end
		end
		local ok, err = pcall(function()
			PluginXCallS("applyLayoutWizardFinish", serialize(payload))
		end)
		if not ok then
			Note("\nButton layout apply failed: " .. tostring(err) .. "\n")
			return
		end
		-- Do not dismiss here: doInstallBatch may skip every row. UI closes from
		-- onLayoutPackInstalled → dismissAfterApply only after a real install.
	end

	local function applyAndDismiss()
		local payload = buildPayload()
		if payload.installs == nil or #payload.installs == 0 then
			Note("\nButton layout: check at least one pack to install.\n")
			return
		end
		if payload.loadSet == nil or tostring(payload.loadSet) == "" then
			Note("\nButton layout: pick which set to load after install.\n")
			return
		end
		-- Catch two checked packs given the same new name (server would skip #2).
		local seen = {}
		for _, inst in ipairs(payload.installs or {}) do
			local key = string.lower(tostring(inst.setName or ""))
			if key ~= "" then
				if seen[key] then
					Note("\nButton layout: two packs share the set name \""
						.. tostring(inst.setName)
						.. "\". Give each a unique name.\n")
					return
				end
				seen[key] = true
			end
			if RESERVED_SET_NAMES[key] then
				Note("\nButton layout: \"" .. tostring(inst.setName)
					.. "\" is reserved (default/tutorial). Pick another name.\n")
				return
			end
		end
		local collisions = collidingNames(payload)
		if #collisions > 0 and not overwriteAck then
			local msg = "These sets will be overwritten:\n"
				.. table.concat(collisions, ", ")
				.. "\n\nContinue?"
			local confirm = luajava.newInstance("android.app.AlertDialog$Builder", context)
			confirm:setTitle("Overwrite sets?")
			confirm:setMessage(msg)
			confirm:setPositiveButton("Continue", luajava.createProxy(
				"android.content.DialogInterface$OnClickListener", {
				onClick = function(d, which)
					overwriteAck = true
					sendApply(payload)
				end
			}))
			confirm:setNegativeButton("Cancel", luajava.createProxy(
				"android.content.DialogInterface$OnClickListener", {
				onClick = function(d, which) end
			}))
			confirm:show()
			return
		end
		sendApply(payload)
	end

	local builder = luajava.newInstance("android.app.AlertDialog$Builder", context)
	builder:setTitle("Button layout")
	builder:setView(root)

	-- Placeholder listeners; Apply is rebound in onShow so a collision confirm
	-- can keep this dialog open (AlertDialog otherwise dismisses on Positive).
	builder:setPositiveButton("Apply", luajava.createProxy(
		"android.content.DialogInterface$OnClickListener", {
		onClick = function(d, which) end
	}))
	builder:setNegativeButton("Close", luajava.createProxy(
		"android.content.DialogInterface$OnClickListener", {
		onClick = function(d, which)
			clearPendingAndDismiss()
		end
	}))
	if pending then
		builder:setNeutralButton("Not now", luajava.createProxy(
			"android.content.DialogInterface$OnClickListener", {
			onClick = function(d, which)
				clearPendingAndDismiss()
			end
		}))
	end
	builder:setOnCancelListener(luajava.createProxy(
		"android.content.DialogInterface$OnCancelListener", {
		onCancel = function(d)
			pcall(function()
				PluginXCallS("clearLayoutWizardPending", "")
			end)
			dialog = nil
		end
	}))

	dialog = builder:create()
	dialog:setOnShowListener(luajava.createProxy(
		"android.content.DialogInterface$OnShowListener", {
		onShow = function(d)
			local btn = dialog:getButton(DialogInterface.BUTTON_POSITIVE)
			if btn ~= nil then
				btn:setOnClickListener(luajava.createProxy(
					"android.view.View$OnClickListener", {
					onClick = function(v)
						applyAndDismiss()
					end
				}))
			end
		end
	}))
	dialog:show()
end
