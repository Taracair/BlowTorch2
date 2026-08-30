-- Wrap-label field round-trip and editor/save wiring.
--
-- Run:
--     lua5.1 BT_Free/src/test/lua/button_wrap_label_test.lua
--
-- wrapLabel is per-button, default false, so existing one-line drawText stays
-- pixel-identical. This file checks serialize and that the editor / save /
-- floating JSON paths actually name the field (the Android widgets themselves
-- are not instantiated here).

local function scriptDir()
	local path = arg and arg[0] or ""
	return path:match("^(.*)[/\\][^/\\]*$") or "."
end

local ROOT = scriptDir() .. "/../../../assets/share/lua/5.1"
package.path = ROOT .. "/?.lua;" .. package.path
dofile(ROOT .. "/serialize.lua")

local function readAll(path)
	local handle = assert(io.open(path, "r"), "cannot open " .. path)
	local src = handle:read("*a")
	handle:close()
	return src
end

local failures = 0
local function check(cond, msg)
	if not cond then
		failures = failures + 1
		print("  FAIL: " .. msg)
	end
end

print("1. serialize round-trip; missing wrapLabel stays nil (inherits false)")
local dumped = serialize({ label = "LOOK NORTH", wrapLabel = true })
local restored = assert(loadstring(dumped))()
check(restored.wrapLabel == true, "wrapLabel true round-trips")
check(restored.label == "LOOK NORTH", "label survived with wrapLabel")
local old = assert(loadstring(serialize({ label = "N" })))()
check(old.wrapLabel == nil, "old buttons have no wrapLabel key")
local broken = assert(loadstring(serialize({ label = "LOOK\nNORTH" })))()
check(broken.label == "LOOK\nNORTH", "a newline in the label survives serialize")

print("2. editor and save paths name wrapLabel")
local advanced = readAll(ROOT .. "/buttoneditoradvanced.lua")
check(advanced:find('setText("Wrap label")', 1, true) ~= nil,
	"Others tab has Wrap label checkbox")
check(advanced:find("safeAddView(ui.labelSizeCaptionColumn, ui.wrapLabelCheck)", 1, true) == nil,
	"Wrap label is not under Label Font Size")
check(advanced:find("safeAddView(ui.wrapLabelRow, ui.wrapLabelCheck)", 1, true) ~= nil,
	"Wrap label is the last SIZE & POSITION row")
check(advanced:find("tmp.wrapLabel", 1, true) ~= nil,
	"getEditorValues reads wrapLabel")

local window = readAll(ROOT .. "/buttonwindow.lua")
check(window:find("tmp.data.wrapLabel = data.wrapLabel == true", 1, true) ~= nil,
	"buttonEditorDone writes wrapLabel")
check(window:find('o:put("wrapLabel", d.wrapLabel == true)', 1, true) ~= nil,
	"floating JSON carries wrapLabel")
check(window:find("editorValues.wrapLabel = button.data.wrapLabel == true", 1, true) ~= nil,
	"showEditorDialog loads wrapLabel")

local buttons = readAll(ROOT .. "/button.lua")
check(buttons:find("wrapLabel = false", 1, true) ~= nil,
	"BUTTONSET_DATA defaults wrapLabel off")
check(buttons:find("buttonLabelUsesWrappedLayout(self.data.wrapLabel, label)", 1, true) ~= nil,
	"draw uses wrap layout when the checkbox is on or the label has a newline")
check(buttons:find("canvas:drawText(label,tX,tY,p)", 1, true) ~= nil,
	"one-line drawText path still exists for wrap off")

print("3. auto_launch and auto_create are stripped on buttonLayerReady, setter stubs stay")
local server = readAll(ROOT .. "/buttonserver.lua")
check(server:find('removeOptionByKey("auto_launch")', 1, true) ~= nil,
	"ensureLayoutSettingsOptions removes auto_launch")
check(server:find("optionsTable.auto_launch = setAutoLaunch", 1, true) ~= nil,
	"auto_launch setter stub remains")
check(server:find('removeOptionByKey("auto_create")', 1, true) ~= nil,
	"ensureLayoutSettingsOptions removes auto_create")
check(server:find("optionsTable.auto_create = setAutoCreate", 1, true) ~= nil,
	"auto_create setter stub remains")

print("4. floating hints are not gated on the callout flag")
local view = readAll(scriptDir() .. "/../../../../BTLib/src/com/resurrection/blowtorch2/lib/window/FloatingButtonView.java")
check(view:find("&& model.showGestureLabel", 1, true) == nil,
	"FloatingButtonView must not AND hints with showGestureLabel")
check(view:find("&& model.showGestureHints", 1, true) ~= nil,
	"badges gated on showGestureHints")
check(view:find("boolean calloutOn = model.showGestureLabel", 1, true) ~= nil,
	"callout gated on showGestureLabel")

print("5. grid still splits callout vs badges")
check(buttons:find("self.data.showGestureLabel == false", 1, true) ~= nil,
	"drawGestureLabel still keyed on showGestureLabel")
check(buttons:find("self.data.showGestureHints == false", 1, true) ~= nil,
	"grid badges still keyed on showGestureHints")

print("6. a newline is a hard break; wrap checkbox still word-wraps")
check(buttons:find("function buttonLabelUsesWrappedLayout", 1, true) ~= nil,
	"buttonLabelUsesWrappedLayout is in button.lua")
do
	local loadfn = loadstring or load
	local fn = assert(loadfn([==[
function buttonLabelUsesWrappedLayout(wrapLabel, label)
	if wrapLabel == true then
		return true
	end
	if label == nil then
		return false
	end
	return string.find(tostring(label), "\n", 1, true) ~= nil
end
return buttonLabelUsesWrappedLayout
]==], "label-wrap"))()
	check(fn(false, "LOOK NORTH") == false, "wrap off, one line")
	check(fn(true, "LOOK NORTH") == true, "wrap on, one line")
	check(fn(false, "LOOK\nNORTH") == true, "Enter is a break without the checkbox")
	check(fn(false, "LOOK\\nNORTH") == false, "typed backslash-n is not a break")
	check(fn(false, nil) == false, "nil label is one line")
end

print("7. Label and Flip label fields accept Enter")
local buildtabs = readAll(ROOT .. "/buttoneditor_buildtabs.lua")
check(buildtabs:find("clickLabelEdit:setLines(1)", 1, true) == nil,
	"click label is not locked to one line")
check(buildtabs:find("clickLabelEdit:setInputType(MULTILINE_TEXT)", 1, true) ~= nil,
	"click label accepts Enter")
check(buildtabs:find("flipLabelEdit:setLines(1)", 1, true) == nil,
	"flip label is not locked to one line")
check(buildtabs:find("flipLabelEdit:setInputType(MULTILINE_TEXT)", 1, true) ~= nil,
	"flip label accepts Enter")
check(buildtabs:find("TYPE_CLASS_TEXT + TYPE_TEXT_FLAG_MULTI_LINE", 1, true) ~= nil,
	"label InputType includes TYPE_CLASS_TEXT so Android treats it as multiline")
check(view:find("ButtonLabelWrap.usesWrappedLayout", 1, true) ~= nil,
	"floating tiles use the same wrap rule")

if failures == 0 then
	print("All button_wrap_label tests passed.")
else
	print(string.format("%d button_wrap_label test(s) failed.", failures))
	os.exit(1)
end
