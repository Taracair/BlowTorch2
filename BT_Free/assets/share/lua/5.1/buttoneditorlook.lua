-- Icon, label, and per-button text avoidance. Built into the Advanced page.
-- No luajava calls at load beyond bindClass: tests do not execute this file.

local LinearLayout = _G["LinearLayout"]
local luajava = _G["luajava"]
local ScrollView = _G["ScrollView"]
local HorizontalScrollView = luajava.bindClass("android.widget.HorizontalScrollView")
local LinearLayoutParams = _G["LinearLayoutParams"]
local TextView = _G["TextView"]
local EditText = _G["EditText"]
local Color = _G["Color"]
local View = _G["View"]
local density = _G["density"]
local tostring = _G["tostring"]
local tonumber = _G["tonumber"]
local ipairs = _G["ipairs"]
local math = _G["math"]
local string = _G["string"]
local pcall = _G["pcall"]
local buttonlook = require("buttonlook")
local CheckBox = luajava.bindClass("android.widget.CheckBox")
local SeekBar = luajava.bindClass("android.widget.SeekBar")
local Button = luajava.bindClass("android.widget.Button")
local InputType = luajava.bindClass("android.text.InputType")
local Gravity = luajava.bindClass("android.view.Gravity")
local ImageView = luajava.bindClass("android.widget.ImageView")
local ScaleType = luajava.bindClass("android.widget.ImageView$ScaleType")
local GradientDrawable = luajava.bindClass("android.graphics.drawable.GradientDrawable")
local ButtonFaces = luajava.bindClass("com.resurrection.blowtorch2.lib.button.ButtonFaces")
local ButtonTypefaces = luajava.bindClass("com.resurrection.blowtorch2.lib.button.ButtonTypefaces")
module(...)

local context = nil
local ui = {}
local FILL = LinearLayoutParams.FILL_PARENT
local WRAP = LinearLayoutParams.WRAP_CONTENT
local SECTION_TEXT = Color:argb(255, 0x9F, 0xB6, 0xD8)
local SECTION_FILL = Color:argb(255, 0x1B, 0x1F, 0x25)
local DESC_TEXT = Color:argb(255, 0x9A, 0xA3, 0xAD)
local CELL = Color:argb(255, 0x2A, 0x31, 0x3C)
local CELL_ON = Color:argb(255, 0x3D, 0x4F, 0x68)
local INK = Color:argb(255, 0xF2, 0xF4, 0xF6)

function init(pContext)
	context = pContext
end

local function dp(n)
	return math.floor((n * (density or 1)) + 0.5)
end

local function trim(s)
	if s == nil then
		return ""
	end
	return tostring(s):match("^%s*(.-)%s*$") or ""
end

local function editText(edit)
	if edit == nil then
		return ""
	end
	local t = edit:getText()
	if t == nil then
		return ""
	end
	return trim(t:toString())
end

local function boxChecked(box)
	if box == nil then
		return false
	end
	local v = box:isChecked()
	return v == true or tostring(v) == "true"
end

local function fileName(path)
	path = trim(path)
	local name = path:match("([^/]+)$")
	if name == nil or name == "" then
		return path
	end
	return name
end

local function hostActivity()
	local c = context
	local guard = 0
	while c ~= nil and guard < 8 do
		local ok = pcall(function()
			return c:avoidButtonsUseWords()
		end)
		if ok then
			return c
		end
		local nextOk, base = pcall(function()
			return c:getBaseContext()
		end)
		if not nextOk or base == nil or base == c then
			break
		end
		c = base
		guard = guard + 1
	end
	return nil
end

local function launchPick(font, onPath)
	local ok, Pick = pcall(function()
		return luajava.bindClass("com.resurrection.blowtorch2.lib.button.ButtonAssetPick")
	end)
	if not ok or Pick == nil then
		return
	end
	local listener = luajava.createProxy(
		"com.resurrection.blowtorch2.lib.button.ButtonAssetPick$Listener", {
			onPicked = function(path)
				path = trim(path)
				if path ~= "" then
					onPath(path)
				end
			end,
		})
	ui.pickListener = listener
	pcall(function()
		Pick:launch(context, font == true, listener)
	end)
end

local function styleSection(tv, caption)
	tv:setText(caption)
	tv:setTextSize(12)
	tv:setTextColor(SECTION_TEXT)
	tv:setBackgroundColor(SECTION_FILL)
	tv:setGravity(Gravity.CENTER_VERTICAL)
	tv:setPadding(dp(10), dp(8), dp(10), dp(8))
	tv:setMinHeight(dp(34))
end

local function note(parent, text)
	local tv = luajava.new(TextView, context)
	tv:setText(text)
	tv:setTextSize(12)
	tv:setTextColor(DESC_TEXT)
	tv:setPadding(dp(10), dp(4), dp(10), dp(6))
	parent:addView(tv)
	return tv
end

local function roundBg(color)
	local d = luajava.new(GradientDrawable)
	d:setColor(color)
	d:setCornerRadius(dp(8))
	return d
end

local function typefaceFor(path, style)
	local ok, face = pcall(function()
		return ButtonTypefaces:resolve(context, path or "default", style or "regular")
	end)
	if ok then
		return face
	end
	return nil
end

local function paintFace(view, path, style)
	if view == nil then
		return
	end
	local face = typefaceFor(path, style)
	if face ~= nil then
		view:setTypeface(face)
	end
	local paint = view:getPaint()
	if paint == nil then
		return
	end
	local fake = false
	local skew = 0
	pcall(function()
		local bold = ButtonTypefaces:fakeBold(path, style)
		fake = bold == true or tostring(bold) == "true"
		skew = tonumber(ButtonTypefaces:textSkew(path, style)) or 0
	end)
	paint:setFakeBoldText(fake)
	paint:setTextSkewX(skew)
end

function paintExternalLabel(view, path, style)
	paintFace(view, path, style)
end

function currentFont()
	local path = ""
	if ui.fontMode == "path" then
		path = ui.fontPath or ""
	elseif ui.fontMode == "file" then
		path = trim(ui.fontFileText)
	end
	return path, ui.styleMode or ""
end

function setOnFontChanged(fn)
	onFontChanged = fn
end

function setOnOpacityChanged(fn)
	onOpacityChanged = fn
end

function setOnLabelOpacityChanged(fn)
	onLabelOpacityChanged = fn
end

function opacityActive()
	return ui.opacityOwn == true or ui.opacityTouched == true
end

function labelOpacityActive()
	return ui.labelOpacityOwn == true or ui.labelOpacityTouched == true
end

-- Set defaults hide these rows. That screen still stores transparency in the
-- colour, so the picker keeps its alpha bar. Multi-edit leaves both rows up.
function opacitySliderShown()
	if ui.opacityRoot == nil then
		return false
	end
	-- View.VISIBLE is 0. The returned int does not compare equal to the constant.
	return ui.opacityRoot:getVisibility() == 0
end

function labelOpacitySliderShown()
	if ui.labelOpacityRoot == nil then
		return false
	end
	return ui.labelOpacityRoot:getVisibility() == 0
end

local function labelFor(spec)
	spec = trim(spec)
	if spec == "" then
		return "Colored pad"
	end
	if string.sub(spec, 1, 6) == "named:" then
		return fileName(string.sub(spec, 7))
	end
	for _, set in ipairs(buttonlook.FACE_CATALOG) do
		for _, icon in ipairs(set.icons) do
			if spec == "builtin:" .. set.set .. "/" .. icon.id then
				return set.label .. " — " .. icon.label
			end
		end
	end
	if string.sub(spec, 1, 8) ~= "builtin:" then
		return fileName(spec)
	end
	return spec
end

local function setPreview(iv, spec, emptyColor)
	spec = trim(spec)
	if spec == "" then
		iv:setImageBitmap(nil)
		iv:setBackgroundColor(emptyColor or Color:argb(0xFF, 0x22, 0x44, 0x88))
		return
	end
	iv:setBackgroundColor(CELL)
	local ok, bmp = pcall(function()
		return ButtonFaces:thumbnail(context, spec, dp(96))
	end)
	if ok and bmp ~= nil then
		iv:setImageBitmap(bmp)
	else
		iv:setImageBitmap(nil)
	end
end

local function showPicker(title, current, noneLabel, onPick)
	local builder = luajava.newInstance("android.app.AlertDialog$Builder", context)
	local scroll = luajava.new(ScrollView, context)
	local page = luajava.new(LinearLayout, context)
	page:setOrientation(LinearLayout.VERTICAL)
	page:setPadding(dp(8), dp(8), dp(8), dp(8))
	scroll:addView(page)
	local dialogHolder = {}

	local function finish(spec)
		onPick(spec)
		if dialogHolder.dialog ~= nil then
			dialogHolder.dialog:dismiss()
		end
	end

	local chipRow = luajava.new(LinearLayout, context)
	chipRow:setOrientation(LinearLayout.HORIZONTAL)
	local chipScroll = luajava.new(HorizontalScrollView, context)
	chipScroll:setHorizontalScrollBarEnabled(false)
	chipScroll:addView(chipRow)
	page:addView(chipScroll)

	local gridHost = luajava.new(LinearLayout, context)
	gridHost:setOrientation(LinearLayout.VERTICAL)
	page:addView(gridHost)

	local chips = {}
	local selectedSet = 1
	for i, set in ipairs(buttonlook.FACE_CATALOG) do
		for _, icon in ipairs(set.icons) do
			if trim(current) == "builtin:" .. set.set .. "/" .. icon.id then
				selectedSet = i
			end
		end
	end

	local function paintChips()
		for i = 1, #chips do
			if i == selectedSet then
				chips[i]:setBackground(roundBg(CELL_ON))
				chips[i]:setTextColor(INK)
			else
				chips[i]:setBackground(roundBg(CELL))
				chips[i]:setTextColor(DESC_TEXT)
			end
		end
	end

	local function fill(setIndex)
		gridHost:removeAllViews()
		local set = buttonlook.FACE_CATALOG[setIndex]
		if set == nil then
			return
		end
		local header = luajava.new(TextView, context)
		styleSection(header, set.label)
		header:setLayoutParams(luajava.new(LinearLayoutParams, FILL, WRAP))
		gridHost:addView(header)
		local row = nil
		local col = 0
		local function nextRow()
			row = luajava.new(LinearLayout, context)
			row:setOrientation(LinearLayout.HORIZONTAL)
			gridHost:addView(row)
			col = 0
		end
		local function addCell(spec, caption, bmpSpec)
			if row == nil or col >= 4 then
				nextRow()
			end
			local cell = luajava.new(LinearLayout, context)
			cell:setOrientation(LinearLayout.VERTICAL)
			cell:setGravity(Gravity.CENTER_HORIZONTAL)
			local lp = luajava.new(LinearLayoutParams, 0, WRAP, 1)
			lp:setMargins(dp(4), dp(4), dp(4), dp(4))
			cell:setLayoutParams(lp)
			cell:setPadding(dp(4), dp(6), dp(4), dp(6))
			if trim(current) == trim(spec) then
				cell:setBackground(roundBg(CELL_ON))
			else
				cell:setBackground(roundBg(CELL))
			end
			local iv = luajava.new(ImageView, context)
			iv:setLayoutParams(luajava.new(LinearLayoutParams, dp(56), dp(56)))
			iv:setScaleType(ScaleType.FIT_CENTER)
			setPreview(iv, bmpSpec, Color:argb(0xFF, 0x22, 0x44, 0x88))
			cell:addView(iv)
			local cap = luajava.new(TextView, context)
			cap:setText(caption)
			cap:setTextSize(11)
			cap:setTextColor(INK)
			cap:setGravity(Gravity.CENTER)
			cap:setMaxLines(2)
			cap:setPadding(0, dp(4), 0, 0)
			cell:addView(cap)
			cell:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
				onClick = function()
					finish(spec)
				end,
			}))
			row:addView(cell)
			col = col + 1
		end
		addCell("", noneLabel, "")
		for _, icon in ipairs(set.icons) do
			local spec = "builtin:" .. set.set .. "/" .. icon.id
			addCell(spec, icon.label, spec)
		end
		if row ~= nil and col > 0 and col < 4 then
			while col < 4 do
				local spacer = luajava.new(LinearLayout, context)
				spacer:setLayoutParams(luajava.new(LinearLayoutParams, 0, WRAP, 1))
				row:addView(spacer)
				col = col + 1
			end
		end
	end

	for i, set in ipairs(buttonlook.FACE_CATALOG) do
		local chip = luajava.new(TextView, context)
		chip:setText(set.label)
		chip:setTextSize(14)
		chip:setPadding(dp(14), dp(8), dp(14), dp(8))
		local lp = luajava.new(LinearLayoutParams, WRAP, WRAP)
		lp:setMargins(dp(4), dp(4), dp(4), dp(8))
		chip:setLayoutParams(lp)
		local index = i
		chip:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
			onClick = function()
				selectedSet = index
				paintChips()
				fill(selectedSet)
			end,
		}))
		chipRow:addView(chip)
		chips[#chips + 1] = chip
	end
	paintChips()
	fill(selectedSet)

	local fileRow = luajava.new(TextView, context)
	local currentFile = trim(current)
	if string.sub(currentFile, 1, 5) == "file:" then
		currentFile = trim(string.sub(currentFile, 6))
	end
	if string.sub(currentFile, 1, 6) == "named:" then
		currentFile = trim(string.sub(currentFile, 7))
	end
	local isFile = currentFile ~= "" and string.sub(currentFile, 1, 8) ~= "builtin:"
	fileRow:setText(isFile and fileName(currentFile) or "Choose image file")
	fileRow:setTextSize(15)
	fileRow:setSingleLine(true)
	fileRow:setEllipsize(luajava.bindClass("android.text.TextUtils$TruncateAt").END)
	fileRow:setGravity(Gravity.CENTER_VERTICAL)
	fileRow:setPadding(dp(12), 0, dp(12), 0)
	fileRow:setTextColor(isFile and INK or DESC_TEXT)
	fileRow:setBackground(roundBg(isFile and CELL_ON or CELL))
	local fileLp = luajava.new(LinearLayoutParams, FILL, dp(44))
	fileLp:setMargins(dp(4), dp(8), dp(4), dp(4))
	fileRow:setLayoutParams(fileLp)
	fileRow:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
		onClick = function()
			launchPick(false, function(path)
				finish(path)
			end)
		end,
	}))
	page:addView(fileRow)
	note(page, "Magenta #FF00FF is clear, and so is PNG transparency.")

	builder:setTitle(title)
	builder:setView(scroll)
	builder:setNegativeButton("Cancel", nil)
	local dialog = builder:create()
	dialogHolder.dialog = dialog
	dialog:show()
end

local function iconTile(parent, title, onChoose)
	local col = luajava.new(LinearLayout, context)
	col:setOrientation(LinearLayout.VERTICAL)
	col:setGravity(Gravity.CENTER_HORIZONTAL)
	local lp = luajava.new(LinearLayoutParams, 0, WRAP, 1)
	lp:setMargins(dp(4), dp(4), dp(4), dp(4))
	col:setLayoutParams(lp)
	col:setPadding(dp(6), dp(8), dp(6), dp(8))
	col:setBackground(roundBg(CELL))
	local iv = luajava.new(ImageView, context)
	iv:setLayoutParams(luajava.new(LinearLayoutParams, dp(64), dp(64)))
	iv:setScaleType(ScaleType.FIT_CENTER)
	col:addView(iv)
	local cap = luajava.new(TextView, context)
	cap:setText(title)
	cap:setTextSize(12)
	cap:setTextColor(INK)
	cap:setGravity(Gravity.CENTER)
	cap:setPadding(0, dp(6), 0, 0)
	col:addView(cap)
	local sub = luajava.new(TextView, context)
	sub:setTextSize(11)
	sub:setTextColor(DESC_TEXT)
	sub:setGravity(Gravity.CENTER)
	sub:setMaxLines(2)
	sub:setPadding(0, dp(2), 0, 0)
	col:addView(sub)
	col:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
		onClick = function()
			onChoose()
		end,
	}))
	parent:addView(col)
	return iv, sub, col
end

local function paintChoice(view, on)
	if on then
		view:setBackground(roundBg(CELL_ON))
		view:setTextColor(INK)
	else
		view:setBackground(roundBg(CELL))
		view:setTextColor(DESC_TEXT)
	end
end

local function keepScroll(anchor, fn)
	local sv = anchor
	local y = 0
	while sv ~= nil do
		local name = ""
		pcall(function()
			name = sv:getClass():getName()
		end)
		if name == "android.widget.ScrollView" then
			y = sv:getScrollY()
			break
		end
		sv = sv:getParent()
	end
	fn()
	if sv ~= nil and y > 0 then
		local scroller = sv
		local restore = y
		pcall(function()
			scroller:post(luajava.createProxy("java.lang.Runnable", {
				run = function()
					scroller:scrollTo(0, restore)
				end,
			}))
		end)
	end
end

local function showSingle(numediting)
	return not (numediting ~= nil and numediting > 1)
end

local function paintAlign()
	if ui.alignInheritChip ~= nil then
		paintChoice(ui.alignInheritChip, ui.useAlign == true)
	end
	if ui.alignCells == nil then
		return
	end
	for i = 1, #ui.alignCells do
		local cell = ui.alignCells[i]
		local on = ui.useAlign ~= true and cell.h == ui.alignH and cell.v == ui.alignV
		if on then
			cell.view:setBackground(roundBg(CELL_ON))
			cell.mark:setTextColor(INK)
		else
			cell.view:setBackground(roundBg(CELL))
			cell.mark:setTextColor(DESC_TEXT)
		end
	end
end

local FOLLOW = "Follow global button options"

local function fontSamplePath()
	if ui.fontMode == "path" and ui.fontPath ~= nil and ui.fontPath ~= "" then
		return ui.fontPath
	end
	if ui.fontMode == "file" and ui.fontFileText ~= nil and ui.fontFileText ~= "" then
		return ui.fontFileText
	end
	return "default"
end

local function fontCaption()
	if ui.fontMode == "file" then
		if ui.fontFileText ~= nil and ui.fontFileText ~= "" then
			return fileName(ui.fontFileText)
		end
		return "Font file"
	end
	if ui.fontMode == "path" then
		for i = 1, #buttonlook.FONT_PATHS do
			if buttonlook.FONT_PATHS[i] == ui.fontPath then
				return buttonlook.FONT_LABELS[i]
			end
		end
	end
	return FOLLOW
end

local function styleCaption()
	if ui.styleMode == "regular" then
		return "Regular"
	end
	if ui.styleMode == "bold" then
		return "Bold"
	end
	if ui.styleMode == "italic" then
		return "Italic"
	end
	if ui.styleMode == "bolditalic" then
		return "Bold italic"
	end
	return FOLLOW
end

local function paintFontSummary()
	if ui.fontName == nil then
		if onFontChanged ~= nil then
			onFontChanged()
		end
		return
	end
	local nameText = fontCaption()
	local styleText = styleCaption()
	ui.fontName:setText(nameText)
	if ui.fontStyleLabel ~= nil then
		if styleText == nameText then
			ui.fontStyleLabel:setVisibility(View.GONE)
		else
			ui.fontStyleLabel:setVisibility(View.VISIBLE)
			ui.fontStyleLabel:setText(styleText)
		end
	end
	local style = ui.styleMode
	if style == nil or style == "" then
		style = "regular"
	end
	paintFace(ui.fontName, fontSamplePath(), style)
	if onFontChanged ~= nil then
		onFontChanged()
	end
end

local function showFontDialog()
	local builder = luajava.newInstance("android.app.AlertDialog$Builder", context)
	local root = luajava.new(LinearLayout, context)
	root:setOrientation(LinearLayout.VERTICAL)
	root:setBackgroundColor(Color:argb(255, 0x16, 0x18, 0x1C))
	local bar = luajava.new(LinearLayout, context)
	bar:setOrientation(LinearLayout.HORIZONTAL)
	bar:setBackgroundColor(Color:argb(255, 0x1E, 0x21, 0x26))
	bar:setPadding(dp(6), dp(6), dp(6), dp(6))
	local scroll = luajava.new(ScrollView, context)
	local metrics = context:getResources():getDisplayMetrics()
	local scrollH = math.floor(metrics.heightPixels * 0.55)
	scroll:setLayoutParams(luajava.new(LinearLayoutParams, FILL, scrollH))
	local page = luajava.new(LinearLayout, context)
	page:setOrientation(LinearLayout.VERTICAL)
	page:setBackgroundColor(Color:argb(255, 0x16, 0x18, 0x1C))
	scroll:addView(page)
	root:addView(scroll)
	root:addView(bar)
	local rows = {}
	local chips = {}
	local TruncateAt = luajava.bindClass("android.text.TextUtils$TruncateAt")
	local ROW = Color:argb(255, 0x16, 0x18, 0x1C)
	local ROW_ON = Color:argb(255, 0x22, 0x26, 0x2C)
	local ROW_TEXT = Color:argb(255, 0xC7, 0xCD, 0xD4)

	local function paintListRow(view, on)
		if on then
			view:setBackgroundColor(ROW_ON)
			view:setTextColor(INK)
		else
			view:setBackgroundColor(ROW)
			view:setTextColor(ROW_TEXT)
		end
	end

	local function paintDialog()
		for i = 1, #rows do
			local row = rows[i]
			local on = false
			if row.mode == "inherit" then
				on = ui.fontMode == "inherit"
			elseif row.mode == "file" then
				on = ui.fontMode == "file"
			else
				on = ui.fontMode == "path" and row.path == ui.fontPath
			end
			paintListRow(row.view, on)
			local sample = "default"
			if row.mode == "path" then
				sample = row.path
			elseif row.mode == "file" then
				sample = trim(ui.fontFileText)
				if sample == "" then
					sample = "default"
				end
			end
			paintFace(row.view, sample, "regular")
		end
		local samplePath = fontSamplePath()
		for i = 1, #chips do
			local chip = chips[i]
			paintListRow(chip.view, chip.id == ui.styleMode)
			local style = chip.id
			if style == "" then
				style = "regular"
			end
			paintFace(chip.view, samplePath, style)
		end
		paintFontSummary()
	end

	local function addRow(caption, mode, path)
		local tv = luajava.new(TextView, context)
		tv:setText(caption)
		tv:setTextSize(16)
		tv:setSingleLine(true)
		tv:setEllipsize(TruncateAt.END)
		tv:setGravity(Gravity.CENTER_VERTICAL)
		tv:setIncludeFontPadding(false)
		tv:setPadding(dp(12), dp(10), dp(12), dp(10))
		tv:setLayoutParams(luajava.new(LinearLayoutParams, FILL, WRAP))
		tv:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
			onClick = function()
				if mode == "file" then
					launchPick(true, function(picked)
						ui.fontMode = "file"
						ui.fontFileText = picked
						tv:setText(fileName(picked))
						paintDialog()
					end)
					return
				end
				ui.fontMode = mode
				if mode == "path" then
					ui.fontPath = path
				end
				paintDialog()
			end,
		}))
		page:addView(tv)
		local line = luajava.new(View, context)
		line:setBackgroundColor(Color:argb(255, 0x23, 0x26, 0x2B))
		line:setLayoutParams(luajava.new(LinearLayoutParams, FILL, math.max(1, dp(1))))
		page:addView(line)
		rows[#rows + 1] = { view = tv, mode = mode, path = path }
	end

	addRow(FOLLOW, "inherit", "")
	for i = 1, #buttonlook.FONT_LABELS do
		addRow(buttonlook.FONT_LABELS[i], "path", buttonlook.FONT_PATHS[i])
	end
	local fileLabel = "Choose font file"
	if trim(ui.fontFileText) ~= "" then
		fileLabel = fileName(ui.fontFileText)
	end
	addRow(fileLabel, "file", "")
	local styleHead = luajava.new(TextView, context)
	styleSection(styleHead, "STYLE")
	styleHead:setLayoutParams(luajava.new(LinearLayoutParams, FILL, WRAP))
	page:addView(styleHead)
	local styles = {
		{ id = "", label = FOLLOW },
		{ id = "regular", label = "Regular" },
		{ id = "bold", label = "Bold" },
		{ id = "italic", label = "Italic" },
		{ id = "bolditalic", label = "Bold italic" },
	}
	for i = 1, #styles do
		local spec = styles[i]
		local tv = luajava.new(TextView, context)
		tv:setText(spec.label)
		tv:setTextSize(16)
		tv:setSingleLine(true)
		tv:setEllipsize(TruncateAt.END)
		tv:setGravity(Gravity.CENTER_VERTICAL)
		tv:setIncludeFontPadding(false)
		tv:setPadding(dp(12), dp(10), dp(12), dp(10))
		tv:setLayoutParams(luajava.new(LinearLayoutParams, FILL, WRAP))
		local id = spec.id
		tv:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
			onClick = function()
				ui.styleMode = id
				paintDialog()
			end,
		}))
		page:addView(tv)
		local line = luajava.new(View, context)
		line:setBackgroundColor(Color:argb(255, 0x23, 0x26, 0x2B))
		line:setLayoutParams(luajava.new(LinearLayoutParams, FILL, math.max(1, dp(1))))
		page:addView(line)
		chips[#chips + 1] = { view = tv, id = id }
	end
	local dialogHolder = {}
	local snap = {
		fontMode = ui.fontMode,
		fontPath = ui.fontPath,
		fontFileText = ui.fontFileText,
		styleMode = ui.styleMode,
	}
	local function restoreSnap()
		ui.fontMode = snap.fontMode
		ui.fontPath = snap.fontPath
		ui.fontFileText = snap.fontFileText
		ui.styleMode = snap.styleMode
		paintFontSummary()
	end
	local weight = luajava.new(LinearLayoutParams, 0, WRAP, 1)
	local cancel = luajava.new(Button, context)
	cancel:setLayoutParams(weight)
	cancel:setText("Cancel")
	cancel:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
		onClick = function()
			if dialogHolder.dialog ~= nil then
				dialogHolder.dialog:cancel()
			end
		end,
	}))
	local done = luajava.new(Button, context)
	done:setLayoutParams(luajava.new(LinearLayoutParams, 0, WRAP, 1))
	done:setText("Done")
	done:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
		onClick = function()
			dialogHolder.keep = true
			paintFontSummary()
			if dialogHolder.dialog ~= nil then
				dialogHolder.dialog:dismiss()
			end
		end,
	}))
	bar:addView(cancel)
	bar:addView(done)
	paintDialog()
	builder:setTitle("Font")
	builder:setView(root)
	local dialog = builder:create()
	dialogHolder.dialog = dialog
	dialog:setOnCancelListener(luajava.createProxy("android.content.DialogInterface$OnCancelListener", {
		onCancel = function()
			if dialogHolder.keep == true then
				return
			end
			restoreSnap()
		end,
	}))
	dialog:show()
	pcall(function()
		local ColorDrawable = luajava.bindClass("android.graphics.drawable.ColorDrawable")
		local bg = luajava.new(ColorDrawable, Color:argb(255, 0x16, 0x18, 0x1C))
		dialog:getWindow():setBackgroundDrawable(bg)
	end)
end

local function refreshFaces()
	if ui.facePreview == nil then
		return
	end
	setPreview(ui.facePreview, ui.face, ui.padColor)
	ui.faceCaption:setText(labelFor(ui.face))
	setPreview(ui.pressedPreview, ui.facePressed, Color:argb(0xFF, 0x33, 0x33, 0x33))
	if ui.facePressed == "" then
		ui.pressedCaption:setText("Darkens")
	else
		ui.pressedCaption:setText(labelFor(ui.facePressed))
	end
	setPreview(ui.flipPreview, ui.faceFlip, Color:argb(0xFF, 0x33, 0x33, 0x33))
	if ui.faceFlip == "" then
		ui.flipCaption:setText("Same icon")
	else
		ui.flipCaption:setText(labelFor(ui.faceFlip))
	end
end

local function paintOpacity()
	if ui.opacityLabel == nil then
		return
	end
	if ui.opacityMixed == true and ui.opacityTouched ~= true then
		ui.opacityLabel:setText("Button opacity: mixed")
		return
	end
	ui.opacityLabel:setText("Button opacity: " .. buttonlook.opacityPercent(ui.opacity) .. "%")
end

local function paintLabelOpacity()
	if ui.labelOpacityCaption == nil then
		return
	end
	if ui.labelOpacityMixed == true and ui.labelOpacityTouched ~= true then
		ui.labelOpacityCaption:setText("Label opacity: mixed")
		return
	end
	ui.labelOpacityCaption:setText("Label opacity: " .. buttonlook.opacityPercent(ui.labelOpacity) .. "%")
end

-- Pad alpha changed (long-press reset) before Opacity owns the button.
-- Move the readout. Do not mark it as a saved percent.
function showUnreadOpacity(color)
	if opacityActive() then
		return
	end
	ui.opacity = buttonlook.alphaPercentFromArgb(color)
	ui.opacityAdjusting = true
	if ui.opacitySeek ~= nil then
		ui.opacitySeek:setProgress(ui.opacity)
	end
	ui.opacityAdjusting = false
	ui.opacityTouched = false
	paintOpacity()
end

-- Label alpha changed (long-press reset) before Label opacity owns the button.
function showUnreadLabelOpacity(color)
	if labelOpacityActive() then
		return
	end
	ui.labelOpacity = buttonlook.alphaPercentFromArgb(color)
	ui.labelOpacityAdjusting = true
	if ui.labelOpacitySeek ~= nil then
		ui.labelOpacitySeek:setProgress(ui.labelOpacity)
	end
	ui.labelOpacityAdjusting = false
	ui.labelOpacityTouched = false
	paintLabelOpacity()
end

local function applyIcon(values)
	values = values or {}
	ui.face = trim(values.face)
	ui.facePressed = trim(values.facePressed)
	ui.faceFlip = trim(values.faceFlip)
	ui.padColor = values.primaryColor or ui.padColor
	refreshFaces()
	if ui.showLabel ~= nil then
		ui.showLabel:setChecked(values.faceOnly ~= true)
	end
	if ui.wrapLabel ~= nil then
		ui.wrapLabel:setChecked(values.wrapLabel == true)
	end
	local h = trim(values.labelAlignH)
	local v = trim(values.labelAlignV)
	ui.alignOrigH = h
	ui.alignOrigV = v
	ui.alignTouched = false
	if h == "" and v == "" then
		ui.useAlign = true
		ui.alignH = "center"
		ui.alignV = "center"
	else
		ui.useAlign = false
		ui.alignH = h
		ui.alignV = v
	end
	local path = trim(values.fontPath)
	ui.fontPath = path
	local known = false
	if path == "" then
		ui.fontMode = "inherit"
		known = true
	else
		for i = 1, #buttonlook.FONT_PATHS do
			if buttonlook.FONT_PATHS[i] == path then
				ui.fontMode = "path"
				known = true
				break
			end
		end
	end
	if not known then
		ui.fontMode = "file"
		local shown = path
		if string.sub(shown, 1, 5) == "file:" then
			shown = string.sub(shown, 6)
		end
		ui.fontFileText = shown
	else
		ui.fontFileText = ""
	end
	ui.styleMode = trim(values.fontStyle)
	ui.opacityMixed = values.opacityMixed == true
	if ui.opacityMixed then
		ui.opacityOwn = false
		ui.opacity = 100
	elseif values.opacity ~= nil then
		ui.opacityOwn = true
		ui.opacity = buttonlook.opacityPercent(values.opacity)
	else
		ui.opacityOwn = false
		ui.opacity = buttonlook.alphaPercentFromArgb(values.primaryColor)
	end
	ui.opacityTouched = false
	ui.opacityAdjusting = true
	if ui.opacitySeek ~= nil then
		ui.opacitySeek:setProgress(ui.opacity)
	end
	ui.opacityAdjusting = false
	ui.opacityTouched = false
	paintOpacity()
	ui.labelOpacityMixed = values.labelOpacityMixed == true
	if ui.labelOpacityMixed then
		ui.labelOpacityOwn = false
		ui.labelOpacity = 100
	elseif values.labelOpacity ~= nil then
		ui.labelOpacityOwn = true
		ui.labelOpacity = buttonlook.opacityPercent(values.labelOpacity)
	else
		ui.labelOpacityOwn = false
		ui.labelOpacity = buttonlook.alphaPercentFromArgb(values.labelColor)
	end
	ui.labelOpacityTouched = false
	ui.labelOpacityAdjusting = true
	if ui.labelOpacitySeek ~= nil then
		ui.labelOpacitySeek:setProgress(ui.labelOpacity)
	end
	ui.labelOpacityAdjusting = false
	ui.labelOpacityTouched = false
	paintLabelOpacity()
	paintAlign()
	paintFontSummary()
end

function attachIcon(parent, values, numediting)
	if context == nil or parent == nil then
		return
	end
	values = values or {}
	if ui.iconRoot == nil then
		ui.iconRoot = luajava.new(LinearLayout, context)
		ui.iconRoot:setOrientation(LinearLayout.VERTICAL)
		ui.iconRoot:setLayoutParams(luajava.new(LinearLayoutParams, FILL, WRAP))
		local bar = luajava.new(TextView, context)
		styleSection(bar, "ICON")
		ui.iconRoot:addView(bar)
		note(ui.iconRoot, "Tap a tile. The icon replaces the colored pad.")
		local row = luajava.new(LinearLayout, context)
		row:setOrientation(LinearLayout.HORIZONTAL)
		ui.iconRoot:addView(row)
		ui.facePreview, ui.faceCaption = iconTile(row, "Button", function()
			showPicker("Button icon", ui.face, "Colored pad", function(spec)
				ui.face = trim(spec)
				refreshFaces()
			end)
		end)
		ui.pressedPreview, ui.pressedCaption = iconTile(row, "While held", function()
			showPicker("While held", ui.facePressed, "Darken", function(spec)
				ui.facePressed = trim(spec)
				refreshFaces()
			end)
		end)
		ui.flipPreview, ui.flipCaption = iconTile(row, "When flipped", function()
			showPicker("When flipped", ui.faceFlip, "Same icon", function(spec)
				ui.faceFlip = trim(spec)
				refreshFaces()
			end)
		end)
		ui.opacityRoot = luajava.new(LinearLayout, context)
		ui.opacityRoot:setOrientation(LinearLayout.VERTICAL)
		ui.opacityRoot:setLayoutParams(luajava.new(LinearLayoutParams, FILL, WRAP))
		ui.opacityLabel = luajava.new(TextView, context)
		ui.opacityLabel:setTextSize(13)
		ui.opacityLabel:setTextColor(INK)
		ui.opacityLabel:setPadding(dp(10), dp(8), dp(10), dp(2))
		ui.opacityRoot:addView(ui.opacityLabel)
		ui.opacitySeek = luajava.new(SeekBar, context)
		ui.opacitySeek:setMax(100)
		ui.opacitySeek:setOnSeekBarChangeListener(luajava.createProxy(
			"android.widget.SeekBar$OnSeekBarChangeListener", {
				onProgressChanged = function(bar, progress, fromUser)
					if ui.opacityAdjusting == true then
						ui.opacity = progress
						paintOpacity()
						return
					end
					ui.opacity = progress
					paintOpacity()
					local user = fromUser == true or tostring(fromUser) == "true"
					if user then
						ui.opacityTouched = true
						if onOpacityChanged ~= nil then
							onOpacityChanged(buttonlook.opacityPercent(ui.opacity))
						end
					end
				end,
				onStartTrackingTouch = function(bar) end,
				onStopTrackingTouch = function(bar) end,
			}))
		ui.opacityRoot:addView(ui.opacitySeek)
		ui.opacityNote = note(ui.opacityRoot, "Shows the pad until you drag it. Then it fades the pad and the icon. The colour picker sets the colour only.")

		ui.labelRoot = luajava.new(LinearLayout, context)
		ui.labelRoot:setOrientation(LinearLayout.VERTICAL)
		ui.labelRoot:setLayoutParams(luajava.new(LinearLayoutParams, FILL, WRAP))
		local labelBar = luajava.new(TextView, context)
		styleSection(labelBar, "LABEL")
		ui.labelRoot:addView(labelBar)
		ui.labelOpacityRoot = luajava.new(LinearLayout, context)
		ui.labelOpacityRoot:setOrientation(LinearLayout.VERTICAL)
		ui.labelOpacityRoot:setLayoutParams(luajava.new(LinearLayoutParams, FILL, WRAP))
		ui.labelOpacityCaption = luajava.new(TextView, context)
		ui.labelOpacityCaption:setTextSize(13)
		ui.labelOpacityCaption:setTextColor(INK)
		ui.labelOpacityCaption:setPadding(dp(10), dp(8), dp(10), dp(2))
		ui.labelOpacityRoot:addView(ui.labelOpacityCaption)
		ui.labelOpacitySeek = luajava.new(SeekBar, context)
		ui.labelOpacitySeek:setMax(100)
		ui.labelOpacitySeek:setOnSeekBarChangeListener(luajava.createProxy(
			"android.widget.SeekBar$OnSeekBarChangeListener", {
				onProgressChanged = function(bar, progress, fromUser)
					if ui.labelOpacityAdjusting == true then
						ui.labelOpacity = progress
						paintLabelOpacity()
						return
					end
					ui.labelOpacity = progress
					paintLabelOpacity()
					local user = fromUser == true or tostring(fromUser) == "true"
					if user then
						ui.labelOpacityTouched = true
						if onLabelOpacityChanged ~= nil then
							onLabelOpacityChanged(buttonlook.opacityPercent(ui.labelOpacity))
						end
					end
				end,
				onStartTrackingTouch = function(bar) end,
				onStopTrackingTouch = function(bar) end,
			}))
		ui.labelOpacityRoot:addView(ui.labelOpacitySeek)
		ui.labelOpacityNote = note(ui.labelOpacityRoot, "Shows the words until you drag it. Then it fades the label only. The colour picker sets the colour only.")
		ui.labelRoot:addView(ui.labelOpacityRoot)
		ui.labelDetails = luajava.new(LinearLayout, context)
		ui.labelDetails:setOrientation(LinearLayout.VERTICAL)
		ui.labelDetails:setLayoutParams(luajava.new(LinearLayoutParams, FILL, WRAP))
		ui.labelRoot:addView(ui.labelDetails)
		local checks = luajava.new(LinearLayout, context)
		checks:setOrientation(LinearLayout.HORIZONTAL)
		checks:setPadding(dp(4), dp(2), dp(4), 0)
		ui.showLabel = luajava.new(CheckBox, context)
		ui.showLabel:setText("Show label")
		ui.showLabel:setLayoutParams(luajava.new(LinearLayoutParams, 0, WRAP, 1))
		checks:addView(ui.showLabel)
		ui.wrapLabel = luajava.new(CheckBox, context)
		ui.wrapLabel:setText("Wrap label")
		ui.wrapLabel:setLayoutParams(luajava.new(LinearLayoutParams, 0, WRAP, 1))
		checks:addView(ui.wrapLabel)
		ui.labelDetails:addView(checks)
		note(ui.labelDetails, "{font:bold} at the start of the label still wins.")

		local alignHead = luajava.new(TextView, context)
		alignHead:setText("Alignment")
		alignHead:setTextSize(13)
		alignHead:setTextColor(INK)
		alignHead:setPadding(dp(10), dp(2), dp(10), dp(2))
		ui.labelDetails:addView(alignHead)
		ui.alignInheritChip = luajava.new(TextView, context)
		ui.alignInheritChip:setText(FOLLOW)
		ui.alignInheritChip:setTextSize(12)
		ui.alignInheritChip:setPadding(dp(8), dp(2), dp(8), dp(2))
		local chipLp = luajava.new(LinearLayoutParams, FILL, WRAP)
		chipLp:setMargins(dp(8), 0, dp(8), dp(4))
		ui.alignInheritChip:setLayoutParams(chipLp)
		ui.alignInheritChip:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
			onClick = function()
				ui.useAlign = true
				ui.alignTouched = true
				paintAlign()
			end,
		}))
		ui.labelDetails:addView(ui.alignInheritChip)
		local alignRow = luajava.new(LinearLayout, context)
		alignRow:setOrientation(LinearLayout.VERTICAL)
		alignRow:setPadding(dp(8), 0, dp(8), dp(4))
		local grid = luajava.new(LinearLayout, context)
		grid:setOrientation(LinearLayout.VERTICAL)
		grid:setLayoutParams(luajava.new(LinearLayoutParams, FILL, WRAP))
		ui.alignCells = {}
		local hs = { "left", "center", "right" }
		local vs = { "top", "center", "bottom" }
		for r = 1, 3 do
			local line = luajava.new(LinearLayout, context)
			line:setOrientation(LinearLayout.HORIZONTAL)
			line:setLayoutParams(luajava.new(LinearLayoutParams, FILL, WRAP))
			for c = 1, 3 do
				local cell = luajava.new(LinearLayout, context)
				cell:setGravity(Gravity.CENTER)
				local clp = luajava.new(LinearLayoutParams, 0, dp(30), 1)
				clp:setMargins(dp(1), dp(1), dp(1), dp(1))
				cell:setLayoutParams(clp)
				local mark = luajava.new(TextView, context)
				mark:setText("●")
				mark:setTextSize(10)
				mark:setGravity(Gravity.CENTER)
				cell:addView(mark)
				local h = hs[c]
				local v = vs[r]
				cell:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
					onClick = function()
						ui.useAlign = false
						ui.alignH = h
						ui.alignV = v
						ui.alignTouched = true
						paintAlign()
					end,
				}))
				line:addView(cell)
				ui.alignCells[#ui.alignCells + 1] = { view = cell, mark = mark, h = h, v = v }
			end
			grid:addView(line)
		end
		alignRow:addView(grid)
		ui.labelDetails:addView(alignRow)

		local fontHead = luajava.new(TextView, context)
		fontHead:setText("Font")
		fontHead:setTextSize(13)
		fontHead:setTextColor(INK)
		fontHead:setPadding(dp(10), dp(8), dp(10), dp(4))
		ui.labelDetails:addView(fontHead)
		local TruncateAt = luajava.bindClass("android.text.TextUtils$TruncateAt")
		local fontChip = luajava.new(LinearLayout, context)
		fontChip:setOrientation(LinearLayout.HORIZONTAL)
		fontChip:setGravity(Gravity.CENTER_VERTICAL)
		fontChip:setBackgroundColor(Color:argb(255, 0x16, 0x18, 0x1C))
		fontChip:setPadding(dp(10), dp(4), dp(6), dp(4))
		local fontLp = luajava.new(LinearLayoutParams, FILL, WRAP)
		fontChip:setLayoutParams(fontLp)
		local names = luajava.new(LinearLayout, context)
		names:setOrientation(LinearLayout.VERTICAL)
		names:setLayoutParams(luajava.new(LinearLayoutParams, 0, WRAP, 1))
		ui.fontName = luajava.new(TextView, context)
		ui.fontName:setTextSize(16)
		ui.fontName:setSingleLine(true)
		ui.fontName:setEllipsize(TruncateAt.END)
		ui.fontName:setIncludeFontPadding(false)
		ui.fontName:setGravity(Gravity.CENTER_VERTICAL)
		ui.fontName:setTextColor(INK)
		ui.fontName:setLayoutParams(luajava.new(LinearLayoutParams, FILL, dp(24)))
		names:addView(ui.fontName)
		ui.fontStyleLabel = luajava.new(TextView, context)
		ui.fontStyleLabel:setTextSize(12)
		ui.fontStyleLabel:setSingleLine(true)
		ui.fontStyleLabel:setEllipsize(TruncateAt.END)
		ui.fontStyleLabel:setTextColor(DESC_TEXT)
		ui.fontStyleLabel:setLayoutParams(luajava.new(LinearLayoutParams, FILL, dp(18)))
		names:addView(ui.fontStyleLabel)
		fontChip:addView(names)
		local change = luajava.new(Button, context)
		change:setText("Change")
		change:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
			onClick = function()
				showFontDialog()
			end,
		}))
		fontChip:addView(change)
		fontChip:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
			onClick = function()
				showFontDialog()
			end,
		}))
		ui.labelDetails:addView(fontChip)

		parent:addView(ui.iconRoot)
		parent:addView(ui.opacityRoot)
		parent:addView(ui.labelRoot)
	end
	applyIcon(values)
	local single = showSingle(numediting)
	local vis = single and View.VISIBLE or View.GONE
	ui.iconRoot:setVisibility(vis)
	-- Label opacity stays up for several buttons. Font, wrap and alignment do not.
	ui.labelRoot:setVisibility(View.VISIBLE)
	if ui.labelDetails ~= nil then
		ui.labelDetails:setVisibility(vis)
	end
	if ui.opacityRoot ~= nil then
		ui.opacityRoot:setVisibility(View.VISIBLE)
	end
	if ui.labelOpacityRoot ~= nil then
		ui.labelOpacityRoot:setVisibility(View.VISIBLE)
	end
	if ui.opacityNote ~= nil then
		if single then
			ui.opacityNote:setText("Shows the pad until you drag it. Then it fades the pad and the icon. The colour picker sets the colour only.")
		else
			ui.opacityNote:setText("Drag to fade every selected button. The colour picker changes the colour only.")
		end
	end
	if ui.labelOpacityNote ~= nil then
		if single then
			ui.labelOpacityNote:setText("Shows the words until you drag it. Then it fades the label only. The colour picker sets the colour only.")
		else
			ui.labelOpacityNote:setText("Drag to fade the label on every selected button. The colour picker changes the colour only.")
		end
	end
end

local function paintBreak()
	if ui.breakLetters == nil then
		return
	end
	paintChoice(ui.breakLetters, ui.breakWords ~= true)
	paintChoice(ui.breakWordsChip, ui.breakWords == true)
end

local function readBreakWords()
	ui.breakWords = false
	local host = hostActivity()
	if host == nil then
		return
	end
	local ok, words = pcall(function()
		return host:avoidButtonsUseWords()
	end)
	if ok and (words == true or tostring(words) == "true") then
		ui.breakWords = true
	end
end

local function setBreakWords(words)
	ui.breakWords = words == true
	local host = hostActivity()
	if host ~= nil then
		pcall(function()
			host:setAvoidButtonsUseWords(ui.breakWords)
		end)
	end
	paintBreak()
end

function attachAvoid(parent, values, numediting)
	if context == nil or parent == nil then
		return
	end
	values = values or {}
	if ui.avoidRoot == nil then
		ui.avoidRoot = luajava.new(LinearLayout, context)
		ui.avoidRoot:setOrientation(LinearLayout.VERTICAL)
		ui.avoidRoot:setLayoutParams(luajava.new(LinearLayoutParams, FILL, WRAP))
		local bar = luajava.new(TextView, context)
		styleSection(bar, "TEXT")
		ui.avoidRoot:addView(bar)
		local row = luajava.new(LinearLayout, context)
		row:setOrientation(LinearLayout.HORIZONTAL)
		row:setGravity(Gravity.CENTER_VERTICAL)
		row:setPadding(dp(4), dp(4), dp(8), 0)
		ui.avoid = luajava.new(CheckBox, context)
		ui.avoid:setText("Game text avoids this button")
		ui.avoid:setLayoutParams(luajava.new(LinearLayoutParams, FILL, WRAP))
		row:addView(ui.avoid)
		ui.avoidRoot:addView(row)
		local underRow = luajava.new(LinearLayout, context)
		underRow:setOrientation(LinearLayout.HORIZONTAL)
		underRow:setGravity(Gravity.CENTER_VERTICAL)
		underRow:setPadding(dp(4), 0, dp(8), 0)
		ui.drawUnder = luajava.new(CheckBox, context)
		ui.drawUnder:setText("Game text draws under this button")
		ui.drawUnder:setLayoutParams(luajava.new(LinearLayoutParams, FILL, WRAP))
		underRow:addView(ui.drawUnder)
		ui.avoidRoot:addView(underRow)
		ui.avoid:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
			onClick = function()
				if boxChecked(ui.avoid) and ui.drawUnder ~= nil then
					ui.drawUnder:setChecked(false)
				end
			end,
		}))
		ui.drawUnder:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
			onClick = function()
				if boxChecked(ui.drawUnder) and ui.avoid ~= nil then
					ui.avoid:setChecked(false)
				end
			end,
		}))
		local breakHead = luajava.new(TextView, context)
		breakHead:setText("Break around every hole")
		breakHead:setTextSize(13)
		breakHead:setTextColor(INK)
		breakHead:setPadding(dp(10), dp(2), dp(10), dp(2))
		ui.avoidRoot:addView(breakHead)
		local breakRow = luajava.new(LinearLayout, context)
		breakRow:setOrientation(LinearLayout.HORIZONTAL)
		breakRow:setPadding(dp(8), 0, dp(8), 0)
		local function breakCell(caption, words)
			local tv = luajava.new(TextView, context)
			tv:setText(caption)
			tv:setTextSize(13)
			tv:setGravity(Gravity.CENTER)
			tv:setPadding(dp(6), 0, dp(6), 0)
			local lp = luajava.new(LinearLayoutParams, 0, dp(30), 1)
			lp:setMargins(dp(2), 0, dp(2), 0)
			tv:setLayoutParams(lp)
			tv:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
				onClick = function()
					setBreakWords(words)
				end,
			}))
			breakRow:addView(tv)
			return tv
		end
		ui.breakLetters = breakCell("Letters", false)
		ui.breakWordsChip = breakCell("Words", true)
		ui.avoidRoot:addView(breakRow)
		local marginRow = luajava.new(LinearLayout, context)
		marginRow:setOrientation(LinearLayout.HORIZONTAL)
		marginRow:setGravity(Gravity.CENTER_VERTICAL)
		marginRow:setPadding(dp(10), dp(8), dp(8), 0)
		local marginLabel = luajava.new(TextView, context)
		marginLabel:setText("Extra margin")
		marginLabel:setTextSize(15)
		marginLabel:setTextColor(INK)
		marginLabel:setLayoutParams(luajava.new(LinearLayoutParams, 0, WRAP, 1))
		marginRow:addView(marginLabel)
		ui.marginEdit = luajava.new(EditText, context)
		ui.marginEdit:setSingleLine(true)
		ui.marginEdit:setInputType(InputType.TYPE_CLASS_NUMBER)
		ui.marginEdit:setHint("0")
		local mlp = luajava.new(LinearLayoutParams, dp(88), WRAP)
		ui.marginEdit:setLayoutParams(mlp)
		marginRow:addView(ui.marginEdit)
		ui.avoidRoot:addView(marginRow)
		note(ui.avoidRoot, "Avoid keeps a hole even when the window switch is off. Draw under lets text paint through this button even when that switch is on. Both off follows the window switch. Letters or Words is the same for every hole.")
		parent:addView(ui.avoidRoot)
	end
	ui.avoid:setChecked(values.avoidText == true)
	if ui.drawUnder ~= nil then
		ui.drawUnder:setChecked(values.avoidSkip == true)
	end
	readBreakWords()
	paintBreak()
	local margin = tonumber(values.avoidMargin) or 0
	if margin > 0 then
		ui.marginEdit:setText(tostring(math.floor(margin)))
	else
		ui.marginEdit:setText("")
	end
	ui.avoidRoot:setVisibility(showSingle(numediting) and View.VISIBLE or View.GONE)
end

function hideForSetDefaults()
	if ui.iconRoot ~= nil then
		ui.iconRoot:setVisibility(View.GONE)
	end
	if ui.labelRoot ~= nil then
		ui.labelRoot:setVisibility(View.GONE)
	end
	if ui.avoidRoot ~= nil then
		ui.avoidRoot:setVisibility(View.GONE)
	end
	if ui.opacityRoot ~= nil then
		ui.opacityRoot:setVisibility(View.GONE)
	end
	if ui.labelOpacityRoot ~= nil then
		ui.labelOpacityRoot:setVisibility(View.GONE)
	end
end

function getEditorValues()
	local out = { lookEdited = true }
	if ui.facePreview == nil then
		return out
	end
	out.face = trim(ui.face)
	out.facePressed = trim(ui.facePressed)
	out.faceFlip = trim(ui.faceFlip)
	out.faceOnly = not boxChecked(ui.showLabel)
	if ui.wrapLabel ~= nil then
		out.wrapLabel = boxChecked(ui.wrapLabel)
	end
	if ui.alignTouched == true then
		if ui.useAlign then
			out.labelAlignH = ""
			out.labelAlignV = ""
		else
			out.labelAlignH = ui.alignH or ""
			out.labelAlignV = ui.alignV or ""
		end
	else
		out.labelAlignH = ui.alignOrigH or ""
		out.labelAlignV = ui.alignOrigV or ""
	end
	if ui.fontMode == "path" then
		out.fontPath = ui.fontPath or ""
	elseif ui.fontMode == "file" then
		out.fontPath = trim(ui.fontFileText)
	else
		out.fontPath = ""
	end
	out.fontStyle = ui.styleMode or ""
	if ui.opacityOwn == true or ui.opacityTouched == true then
		out.opacity = buttonlook.opacityPercent(ui.opacity)
	end
	if ui.labelOpacityOwn == true or ui.labelOpacityTouched == true then
		out.labelOpacity = buttonlook.opacityPercent(ui.labelOpacity)
	end
	if ui.avoid == nil then
		out.avoidKeep = true
	elseif boxChecked(ui.avoid) then
		out.avoidMode = "force"
	elseif boxChecked(ui.drawUnder) then
		out.avoidMode = "skip"
	else
		out.avoidMode = "follow"
	end
	out.avoidMargin = 0
	if ui.marginEdit ~= nil then
		out.avoidMargin = buttonlook.clampMarginPx(editText(ui.marginEdit))
	end
	return out
end
