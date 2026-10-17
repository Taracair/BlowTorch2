-- Font tag, alignment, avoid margin. Same cases as ButtonLookTest.java.
--
-- Run: lua5.1 BT_Free/src/test/lua/button_look_test.lua

local function scriptDir()
	local path = arg and arg[0] or ""
	return path:match("^(.*)[/\\][^/\\]*$") or "."
end

local ROOT = scriptDir() .. "/../../../assets/share/lua/5.1"
local look = dofile(ROOT .. "/buttonlook.lua")

local failures = 0
local function check(cond, msg)
	if not cond then
		failures = failures + 1
		print("  FAIL: " .. msg)
	end
end

print("1. {font:} only at the start")
local bold = look.parseFontTag("{font:bold}North")
check(bold.display == "North", "bold display")
check(bold.style == "bold", "bold style")
check(bold.path == nil, "bold has no path")
local mid = look.parseFontTag("go {font:bold}North")
check(mid.display == "go {font:bold}North", "mid label stays")
check(mid.style == nil, "mid has no style")
local path = look.parseFontTag("{font:bold:/storage/emulated/0/BlowTorch/fonts/My.ttf}Look")
check(path.display == "Look", "path display")
check(path.style == "bold", "path style")
check(path.path == "/storage/emulated/0/BlowTorch/fonts/My.ttf", "path kept")
local serif = look.parseFontTag("{font:serif}Say")
check(serif.display == "Say" and serif.path == "serif" and serif.style == nil, "serif")
local junk = look.parseFontTag("{font:nope}Stay")
check(junk.display == "{font:nope}Stay", "unknown token stays visible")

print("2. center baseline matches the floating copy")
local ascent, descent = -12, 3
local base = look.labelBaseline(0, 40, ascent, descent, "center", 4)
check(math.abs(base - (20 - (ascent + descent) / 2)) < 0.01, "center baseline")
check(math.abs(look.labelLeft(0, 40, 20, "center", 4) - 10) < 0.01, "center x")
check(math.abs(look.labelLeft(0, 40, 20, "left", 4) - 4) < 0.01, "left x")
check(math.abs(look.labelLeft(0, 40, 20, "right", 4) - 16) < 0.01, "right x")
check(math.abs(look.labelBaseline(0, 40, ascent, descent, "top", 4) - (4 - ascent)) < 0.01,
	"top baseline")

print("3. empty own font inherits global; tag wins")
local p, s = look.resolveFont("", "", nil, nil, "serif", "italic")
check(p == "serif" and s == "italic", "inherit global")
p, s = look.resolveFont("monospace", "regular", nil, nil, "serif", "italic")
check(p == "monospace" and s == "regular", "own wins")
p, s = look.resolveFont("monospace", "regular", "/a.ttf", "bold", "serif", "italic")
check(p == "/a.ttf" and s == "bold", "tag wins")
check(look.fontStyleForIndex(1) == "bold", "style index 1 is bold")
check(look.fontPathForIndex(0) == "default", "font index 0 is default")
check(look.alignHForIndex(1) == "center", "align index 1 is center")
check(look.alignHForIndex(0) == "left", "align index 0 is left")

print("4. avoid: missing participates, false does not, margin clamps")
check(look.buttonParticipates({ label = "N" }) == true, "missing avoid participates")
check(look.buttonParticipates({ avoidText = false }) == false, "false opts out")
check(look.avoidForced({ label = "N" }) == false, "missing avoid is not forced")
check(look.avoidForced({ avoidText = true }) == true, "own true is a hole on its own")
check(look.avoidForced({ avoidText = false }) == false, "own false is not forced")
check(look.buttonParticipates({ avoidText = "false" }) == false, "string false opts out")
check(look.avoidMode({}) == "follow", "missing follows the window")
check(look.avoidMode({ avoidText = true }) == "force", "true forces a hole")
check(look.avoidMode({ avoidText = "on" }) == "force", "on forces a hole")
check(look.avoidMode({ avoidText = false }) == "skip", "false draws under")
check(look.avoidMode({ avoidText = "off" }) == "skip", "off draws under")
check(look.avoidValueForMode("force") == true, "force stores true")
check(look.avoidValueForMode("skip") == false, "skip stores false")
check(look.avoidValueForMode("follow") == nil, "follow clears the key")
check(look.buttonMarginPx({ avoidMargin = 12 }) == 12, "margin 12")
check(look.buttonMarginPx({ avoidMargin = 900 }) == 200, "margin clamps at 200")
check(look.buttonMarginPx({}) == 0, "missing margin is 0")

print("5. option labels stay in the settings XML, same order")
local function readAll(path)
	local handle = assert(io.open(path, "r"))
	local src = handle:read("*a")
	handle:close()
	return src
end
local function assertOrder(xml, labels, key)
	local at = xml:find('key="' .. key .. '"', 1, true)
	check(at ~= nil, key .. " is in the xml")
	if at == nil then
		return
	end
	local chunk = xml:sub(at, at + 1800)
	local pos = 1
	for i = 1, #labels do
		local found = chunk:find("<item>" .. labels[i] .. "</item>", pos, true)
		check(found ~= nil, key .. " has " .. labels[i])
		if found == nil then
			return
		end
		pos = found + 1
	end
end
local repo = scriptDir() .. "/../../.."
for _, name in ipairs({ "default_settings_test.xml", "default_settings_main.xml" }) do
	local xml = readAll(repo .. "/config/" .. name)
	assertOrder(xml, look.FONT_LABELS, "button_label_font")
	assertOrder(xml, look.STYLE_LABELS, "button_label_style")
	assertOrder(xml, look.ALIGN_H_LABELS, "button_label_align_h")
	assertOrder(xml, look.ALIGN_V_LABELS, "button_label_align_v")
	check(xml:find('key="button_label_font_file"', 1, true) ~= nil, name .. " has font file")
end

print("6. opacity scales the colour alpha; a swipe hides the held picture")
check(look.opacityPercent(nil) == 100, "missing opacity is solid")
check(look.opacityPercent("40") == 40, "string percent")
check(look.opacityPercent(150) == 100, "percent clamps")
check(look.mergedAlpha(0x88, nil) == 0x88, "100% keeps the pad alpha")
check(look.mergedAlpha(0x88, 100) == 0x88, "explicit 100% keeps the pad alpha")
check(look.mergedAlpha(200, 50) == 100, "50% of 200")
check(look.mergedAlpha(255, 0) == 0, "0% is clear")
check(look.opacityAlpha(100) == 255, "stored 100% is solid")
check(look.opacityAlpha(50) == 128, "stored 50% is half of solid")
check(look.alphaPercent(0x88) == 53, "pad alpha 0x88 is 53%")
check(look.alphaPercentFromArgb(0x880000FF) == 53, "argb pad alpha is 53%")
check(look.pictureState(1, false) == 1, "a press shows the held picture")
check(look.pictureState(2, false) == 2, "a flip shows the flip picture")
check(look.pictureState(1, true) == 0, "a swipe hides the held picture")
check(look.pictureState(2, true) == 0, "a swipe hides the flip picture")
check(look.pictureState(0, true) == 0, "idle stays idle")

print("7. grid still draws a pressed or flip picture when the idle picture is empty")
local buttonSrc = readAll(ROOT .. "/button.lua")
local at = buttonSrc:find("local function tryDrawButtonFace", 1, true)
check(at ~= nil, "tryDrawButtonFace exists")
if at ~= nil then
	local chunk = buttonSrc:sub(at, at + 700)
	check(chunk:find('face == "" and pressed == "" and flip == ""', 1, true) ~= nil,
		"empty idle does not skip a pressed or flip picture")
end
check(buttonSrc:find("buttonlook.pictureState", 1, true) ~= nil, "draw asks pictureState")
check(buttonSrc:find("buttonlook.opacityAlpha", 1, true) ~= nil,
	"stored opacity replaces the colour alpha")
local lookSrc = readAll(ROOT .. "/buttoneditorlook.lua")
check(lookSrc:find("Break around every hole", 1, true) ~= nil,
	"the text break heading says what it does")
local advancedSrc = readAll(ROOT .. "/buttoneditoradvanced.lua")
check(advancedSrc:find("opacitySliderShown", 1, true) ~= nil,
	"the colour picker hides alpha only while Opacity is on screen")
check(advancedSrc:find("multiColorEdit", 1, true) ~= nil,
	"multi-edit changes the hue and leaves each button's transparency")
check(advancedSrc:find("fade every selected button", 1, true) ~= nil,
	"multi-edit can fade every selected button")
check(lookSrc:find("Button opacity: mixed", 1, true) ~= nil,
	"mixed button opacities stay untouched until the slider moves")
check(lookSrc:find("Label opacity: mixed", 1, true) ~= nil,
	"mixed label opacities stay untouched until the slider moves")
check(advancedSrc:find("showUnreadOpacity(color)", 1, true) ~= nil,
	"resetting the pad moves the opacity readout")
check(advancedSrc:find("showUnreadLabelOpacity(color)", 1, true) ~= nil,
	"resetting the label moves the label opacity readout")
check(advancedSrc:find("labelOpacitySliderShown", 1, true) ~= nil,
	"the label colour picker hides alpha only while Label opacity is on screen")
local windowSrc = readAll(ROOT .. "/buttonwindow.lua")
check(windowSrc:find("local function isSwipedTile(b)", 1, true) ~= nil,
	"drawButtons leaves the swiped tile out")
check(windowSrc:find(
	"touchedbutton:draw(normalTouchState, buttonCanvas, swipePreviewDir ~= nil)",
	1, true) ~= nil,
	"the swipe picture is drawn once, after the other tiles")
check(windowSrc:find(
	"buttonCanvas:drawRect(r.left, r.top, r.right, r.bottom, cpaint)", 1, true) == nil,
	"a swipe does not clear the tile rect")
check(windowSrc:find('opacity = rawget(src.data, "opacity")', 1, true) ~= nil,
	"a pinned fan copies opacity")
check(windowSrc:find('opacity = rawget(parent.data, "opacity")', 1, true) ~= nil,
	"an unpinned fan copies the parent opacity")
check(windowSrc:find('labelOpacity = rawget(src.data, "labelOpacity")', 1, true) ~= nil,
	"a pinned fan copies label opacity")
check(windowSrc:find('labelOpacity = rawget(parent.data, "labelOpacity")', 1, true) ~= nil,
	"an unpinned fan copies the parent label opacity")
check(windowSrc:find("fadeButtonOpacity(heat.fill, d)", 1, true) ~= nil,
	"a floating heatmap keeps the use-count scale")
check(windowSrc:find("replaceRgbKeepAlpha", 1, true) ~= nil,
	"multi-edit keeps each button's colour alpha")
check(windowSrc:find("b.data.opacity = buttonlook.opacityPercent(data.opacity)", 1, true) ~= nil,
	"multi-edit stores the dragged opacity on each button")
check(windowSrc:find("b.data.labelOpacity = buttonlook.opacityPercent(data.labelOpacity)", 1, true) ~= nil,
	"multi-edit stores the dragged label opacity on each button")
check(buttonSrc:find("applyLabelOpacity", 1, true) ~= nil,
	"the label uses its own opacity")
check(buttonSrc:find('rawget(o.data, "labelOpacity") == nil and rawget(o.data, "opacity")', 1, true) == nil,
	"a later load does not copy button opacity onto the label")
check(windowSrc:find("floaterArgb(d.borderColor, d, applyButtonOpacity)", 1, true) ~= nil,
	"a floating border uses button opacity")
check(buttonSrc:find("tileColor(self.data.labelColor)", 1, true) == nil,
	"button opacity does not paint the label")
check(windowSrc:find("fadeLabelOpacity(heat.label, d)", 1, true) ~= nil,
	"a floating heatmap scales the words by label opacity")
local editorSrc = readAll(ROOT .. "/buttoneditor.lua")
check(editorSrc:find('globalEnv["options"]', 1, true) ~= nil,
	"label typeface reads the live options table")
check(editorSrc:find('local buttonWindowOptions = _G["options"]', 1, true) == nil,
	"label typeface does not keep the options table from module load")

if failures == 0 then
	print("All button_look tests passed.")
else
	print(string.format("%d button_look test(s) failed.", failures))
	os.exit(1)
end
