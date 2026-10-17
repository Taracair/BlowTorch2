local LinearLayout = _G["LinearLayout"]
local luajava = _G["luajava"]
local ScrollView = _G["ScrollView"]
local View = _G["View"]
local Configuration = _G["Configuration"]
local density = _G["density"]
local TextView = _G["TextView"]
local EditText = _G["EditText"]
local Gravity = _G["Gravity"]
local FILL_PARENT = _G["FILL_PARENT"]
local WRAP_CONTENT = _G["WRAP_CONTENT"]
local LinearLayoutParams = _G["LinearLayoutParams"]
local Color = _G["Color"]
local GRAVITY_CENTER = _G["GRAVITY_CENTER"]
local TYPE_CLASS_NUMBER = _G["TYPE_CLASS_NUMBER"]
local math = _G["math"]
local tostring = _G["tostring"]
local Note = _G["Note"]
local string = _G["string"]
local tonumber = _G["tonumber"]
local bit = _G["bit"]
local pcall = _G["pcall"]
local CheckBox = luajava.bindClass("android.widget.CheckBox")
local Spinner = luajava.bindClass("android.widget.Spinner")
local ArrayAdapter = luajava.bindClass("android.widget.ArrayAdapter")
local buttoneditorlook = require("buttoneditorlook")
local buttonlook = require("buttonlook")
module(...)

local context = nil
local quicknew = luajava.new
local fillparams = luajava.new(LinearLayoutParams,FILL_PARENT,WRAP_CONTENT,1)
local textSizeBig = (18)
local textSize = (14)
local textSizeSmall = (10)
local SECTION_TEXT = Color:argb(255, 0x9F, 0xB6, 0xD8)
local SECTION_FILL = Color:argb(255, 0x1B, 0x1F, 0x25)
local DESC_TEXT = Color:argb(255, 0x9A, 0xA3, 0xAD)

local ui = {}

local COLOR_NOTE_SLIDER = "These colors show when that state has no icon. Button opacity fades the pad. Label opacity, under Label, fades the words. Until you drag one, that colour keeps its own."
local COLOR_NOTE_PICKER = "These colors show when that state has no icon. The colour picker sets how see-through they are."
local COLOR_NOTE_MULTI = "These colors show when that state has no icon. Drag Button opacity to fade every selected button. Label opacity fades the words. A colour change keeps each button's transparency."

local function paintColorNote(mode)
  if ui.colorNote == nil then
    return
  end
  if mode == "picker" then
    ui.colorNote:setText(COLOR_NOTE_PICKER)
  elseif mode == "multi" then
    ui.colorNote:setText(COLOR_NOTE_MULTI)
  else
    ui.colorNote:setText(COLOR_NOTE_SLIDER)
  end
end

-- Told when 'Float over the game' is ticked or unticked, so tabs built by
-- another module can follow. Re-registered by buttoneditor.lua on every open:
-- ui.floatCheckListener is built once and outlives the dialog it came from.
local floatingChangedCallback = nil

local selectedColorField

local defaultColors = {}

local function argbParts(color)
  local n = tonumber(color) or 0
  if n < 0 then
    n = n + 4294967296
  end
  local a = math.floor(n / 16777216) % 256
  local r = math.floor(n / 65536) % 256
  local g = math.floor(n / 256) % 256
  local b = n % 256
  return a, r, g, b
end

local function displayColor(color, which)
  local preview = ui.previewOpacity
  if which == "label" then
    preview = ui.previewLabelOpacity
  end
  if color == nil or preview == nil then
    return color
  end
  local _, r, g, b = argbParts(color)
  return Color:argb(buttonlook.opacityAlpha(preview), r, g, b)
end

local function swatchWhich(view)
  if view == ui.normalLabelColorPicker or view == ui.flipLabelColorPicker then
    return "label"
  end
  return "pad"
end

local function showSwatch(view, color)
  if view == nil or color == nil then
    return
  end
  view:setBackgroundColor(displayColor(color, swatchWhich(view)))
  view:invalidate()
end

local function refreshSwatches()
  showSwatch(ui.normalColorPicker, ui.normalColor)
  showSwatch(ui.pressedColorPicker, ui.pressedColor)
  showSwatch(ui.flipColorPicker, ui.flipColor)
  showSwatch(ui.normalLabelColorPicker, ui.normalLabelColor)
  showSwatch(ui.flipLabelColorPicker, ui.flipLabelColor)
  showSwatch(ui.borderColorPicker, ui.borderColor)
end

function previewButtonOpacity(pct)
  if pct == nil then
    ui.previewOpacity = nil
  else
    ui.previewOpacity = buttonlook.opacityPercent(pct)
  end
  refreshSwatches()
end

function previewLabelOpacity(pct)
  if pct == nil then
    ui.previewLabelOpacity = nil
  else
    ui.previewLabelOpacity = buttonlook.opacityPercent(pct)
  end
  refreshSwatches()
end

local function labelColorField(field)
  return field == "label" or field == "flipLabel"
end

-- Single button: the matching slider owns the alpha once it has a percent.
-- Multi-edit: hue only, so a stored percent still replaces colour alpha.
-- Set defaults: the picker alpha is the transparency.
local function pickedColor(previous, picked, field)
  local shown = buttoneditorlook.opacitySliderShown()
  local active = buttoneditorlook.opacityActive()
  if labelColorField(field) then
    shown = buttoneditorlook.labelOpacitySliderShown()
    active = buttoneditorlook.labelOpacityActive()
  end
  if ui.multiColorEdit ~= true and not shown then
    return picked
  end
  local _, r, g, b = argbParts(picked)
  local a = argbParts(previous)
  if shown and active then
    a = 255
  end
  return Color:argb(a, r, g, b)
end

local function applyDefaultColor(field)
  local color = defaultColors[field]
  if color == nil then
    return
  end
  if field == "flip" then
    ui.flipColor = color
    showSwatch(ui.flipColorPicker, color)
  elseif field == "normal" then
    ui.normalColor = color
    showSwatch(ui.normalColorPicker, color)
    buttoneditorlook.showUnreadOpacity(color)
  elseif field == "pressed" then
    ui.pressedColor = color
    showSwatch(ui.pressedColorPicker, color)
  elseif field == "label" then
    ui.normalLabelColor = color
    showSwatch(ui.normalLabelColorPicker, color)
    buttoneditorlook.showUnreadLabelOpacity(color)
  elseif field == "flipLabel" then
    ui.flipLabelColor = color
    showSwatch(ui.flipLabelColorPicker, color)
  elseif field == "border" then
    ui.borderColor = color
    showSwatch(ui.borderColorPicker, color)
  end
end

local function bindColorSwatch(view)
  view:setOnClickListener(swatchClickListener)
  view:setOnLongClickListener(swatchLongClickListener)
end

local function styleSectionBar(tv, caption)
  tv:setText(caption)
  tv:setTextSize(12)
  tv:setTextColor(SECTION_TEXT)
  tv:setBackgroundColor(SECTION_FILL)
  tv:setGravity(Gravity.CENTER_VERTICAL)
  local pad = math.floor(10 * density)
  tv:setPadding(pad, math.floor(8 * density), pad, math.floor(8 * density))
  tv:setMinHeight(math.floor(34 * density))
end

local function hideEssay(view)
  if view ~= nil then
    view:setVisibility(View.GONE)
  end
end

-- When/Shape/Thin outline stay saved; they are only on screen while floating.
applyFloatRowFold = function()
  local enabled = ui.floatingCheck ~= nil and ui.floatingCheck:isEnabled()
  local on = enabled and ui.floatingCheck:isChecked()
  local vis = View.GONE
  if on then
    vis = View.VISIBLE
  end
  if ui.floatModeRow ~= nil then ui.floatModeRow:setVisibility(vis) end
  if ui.floatShapeRow ~= nil then ui.floatShapeRow:setVisibility(vis) end
  if ui.floatOutlineCheck ~= nil then
    ui.floatOutlineCheck:setVisibility(vis)
    ui.floatOutlineCheck:setEnabled(on)
  end
  if ui.floatModeSpinner ~= nil then ui.floatModeSpinner:setEnabled(on) end
  if ui.floatShapeSpinner ~= nil then ui.floatShapeSpinner:setEnabled(on) end
  hideEssay(ui.floatModeHelp)
  hideEssay(ui.floatOutlineHelp)
end

showSetEditorControls = nil
showButtonEditorControls = nil

getWidthEdit = function() return ui.widthEdit end
getHeightEdit = function() return ui.heightEdit end
getXCoordEdit = function() return ui.xCoordEdit end
getYCoordEdit = function() return ui.yCoordEdit end
getLabelSizeEdit = function() return ui.labelSizeEdit end
getButtonNameEdit = function() return ui.nameEdit end

local function safeAddView(parent, child)
  if parent == nil or child == nil then
    return
  end
  local oldParent = child:getParent()
  if oldParent ~= nil then
    oldParent:removeView(child)
  end
  parent:addView(child)
end

function setFloatingChangedCallback(c)
  floatingChangedCallback = c
end

function init(pContext)
  context = pContext
end

function makeUI(editorValues,numediting)
  
  local fnew = luajava.new
  if editorValues.opacity ~= nil then
    ui.previewOpacity = buttonlook.opacityPercent(editorValues.opacity)
  else
    ui.previewOpacity = nil
  end
  if editorValues.labelOpacity ~= nil then
    ui.previewLabelOpacity = buttonlook.opacityPercent(editorValues.labelOpacity)
  else
    ui.previewLabelOpacity = nil
  end
  ui.multiColorEdit = numediting ~= nil and numediting > 1
  defaultColors.normal = editorValues.defaultPrimaryColor or Color:argb(0x88,0x00,0x00,0xFF)
  defaultColors.pressed = editorValues.defaultSelectedColor or Color:argb(0x88,0x00,0xFF,0x00)
  defaultColors.flip = editorValues.defaultFlipColor or Color:argb(0x88,0xFF,0x00,0x00)
  defaultColors.label = editorValues.defaultLabelColor or Color:argb(0xAA,0xAA,0xAA,0xAA)
  defaultColors.flipLabel = editorValues.defaultFlipLabelColor or Color:argb(0x88,0x00,0x00,0xFF)
  defaultColors.border = editorValues.defaultBorderColor or Color:argb(0xE0, 0xFF, 0xFF, 0xFF)
  --local context = view:getContext()
  local LabelWidth = nil -- margin size for different screen layouts
  if(ui.advancedPageScroller == nil) then
    ui.advancedPageScroller = fnew(ScrollView,context)
    ui.advancedPageScroller:setId(5)
  end
  if ui.scrollFocusSet ~= true and ui.advancedPageScroller ~= nil then
    local ViewGroup = luajava.bindClass("android.view.ViewGroup")
    ui.advancedPageScroller:setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS)
    ui.advancedPageScroller:setFocusable(true)
    ui.advancedPageScroller:setFocusableInTouchMode(true)
    ui.scrollFocusSet = true
  end
  
  if(ui.advancedPage == nil) then
    ui.advancedPage = fnew(LinearLayout,context)
    ui.advancedPage:setOrientation(LinearLayout.VERTICAL)
    ui.advancedPageScroller:addView(ui.advancedPage)
  end

  -- Essays belong behind ?. Keep at most a one-liner on the canvas.
  hideEssay(ui.advancedHelp)
  if ui.othersOneLiner == nil then
    ui.othersOneLiner = fnew(TextView, context)
    ui.othersOneLiner:setTextSize(textSizeSmall)
    ui.othersOneLiner:setTextColor(DESC_TEXT)
    ui.othersOneLiner:setMaxLines(2)
    local pad = math.floor(10 * density)
    ui.othersOneLiner:setPadding(pad, math.floor(4 * density), pad, math.floor(4 * density))
    ui.othersOneLiner:setLayoutParams(fillparams)
    ui.advancedPage:addView(ui.othersOneLiner)
  end
  if numediting > 1 then
    ui.othersOneLiner:setText("Changes go to all " .. numediting
      .. " buttons. Tap one button for label, command, gestures.")
    ui.othersOneLiner:setVisibility(View.VISIBLE)
  else
    -- Set switching is .loadset on the Tap command; the old one-liner named a
    -- hidden field. Name itself stays on this tab.
    ui.othersOneLiner:setVisibility(View.GONE)
  end
  
  --ui.buttonNameRow
  if(ui.buttonNameRow == nil) then
    ui.buttonNameRow = fnew(LinearLayout,context)
    ui.buttonNameRow:setLayoutParams(fillparams)
    ui.advancedPage:addView(ui.buttonNameRow)
  end
  local othersSide = math.floor(10 * density)
  ui.buttonNameRow:setPadding(othersSide, math.floor(4 * density), othersSide, math.floor(4 * density))
  ui.buttonNameRow:setGravity(Gravity.CENTER_VERTICAL)
  
  ui.buttonNameRow:setVisibility(View.VISIBLE)
  -- Adjust margins for larger screen sizes.
  -- `test` used to be read here as a bare name. It is a *local* of
  -- buttonwindow.lua (:2558, the masked screen-layout size) and module(...)
  -- cut it off, so this read nil and the XLARGE branch could never be taken.
  -- Compute it from our own context instead. Found by scripts/lua_unbound.py.
  local screenSize = nil
  pcall(function()
    local layout = context:getResources():getConfiguration().screenLayout
    screenSize = bit.band(layout,Configuration.SCREENLAYOUT_SIZE_MASK)
  end)
  if(screenSize == Configuration.SCREENLAYOUT_SIZE_XLARGE) then
    LabelWidth = 100 * density
  else
    LabelWidth = 80 * density
  end
  
  local buttonNameLabelParams = fnew(LinearLayoutParams,LabelWidth,WRAP_CONTENT)
  
  if(ui.buttonNameLabel == nil) then
    ui.buttonNameLabel = fnew(TextView,context)
    
    ui.buttonNameLabel:setLayoutParams(buttonNameLabelParams)
    ui.buttonNameLabel:setText("Name:")
    ui.buttonNameLabel:setTextSize(textSize)
    ui.buttonNameLabel:setGravity(Gravity.LEFT+Gravity.CENTER_VERTICAL)
    ui.buttonNameRow:addView(ui.buttonNameLabel)
  end
  
  buttonNameEditParams = fnew(LinearLayoutParams, 0, WRAP_CONTENT, 1)
    
  --local buttonNameEdit = makeEdit(buttonNameEditParams)
  if(ui.nameEdit == nil) then
    ui.nameEdit = fnew(EditText,context) 
    ui.nameEdit:setTextSize(textSize)
    ui.nameEdit:setLines(1)
    ui.buttonNameRow:addView(ui.nameEdit)
  end
  ui.nameEdit:setLayoutParams(buttonNameEditParams)
  if(numediting > 1) then
    --Note("\nEditing multiple, not setting name\n")
    ui.nameEdit:setText("")
    ui.nameEdit:setEnabled(false)
    --ui.nameEdit:setVisibility(View.GONE)
  else
    ui.nameEdit:setVisibility(View.VISIBLE)
    --Note("\nSetting editor value,"..editorValues.name)
    if(editorValues.name ~= nil) then
    --Note("\nEditing button, name is,"..editorValues.name.."\n")
      ui.nameEdit:setEnabled(true)
      ui.nameEdit:setText(editorValues.name)
    else
      --Note("\nEditorvalues.name is nil\n")
    end
    
  end

  -- Same row as Name, to the right. Keep the row visible for multi-edit so
  -- Active is still reachable when the name field is disabled.
  if(ui.activeCheck == nil) then
    ui.activeCheck = fnew(CheckBox,context)
    local activeParams = fnew(LinearLayoutParams, WRAP_CONTENT, WRAP_CONTENT)
    ui.activeCheck:setLayoutParams(activeParams)
    ui.activeCheck:setText("Active")
    ui.activeCheck:setTextSize(textSize)
  end
  safeAddView(ui.buttonNameRow, ui.activeCheck)
  ui.activeCheck:setChecked(editorValues.active ~= false
    and editorValues.active ~= "false")
  
  if(ui.buttonTargetSetRow == nil) then
    ui.buttonTargetSetRow = fnew(LinearLayout,context)
    ui.buttonTargetSetRow:setLayoutParams(fillparams)
    ui.buttonTargetSetRow:setOrientation(LinearLayout.VERTICAL)
    ui.advancedPage:addView(ui.buttonTargetSetRow)
  end
  
  if(ui.buttonTargetSetLabel == nil) then
    ui.buttonTargetSetLabel = fnew(TextView,context)
    local targetLabelParams = fnew(LinearLayoutParams,FILL_PARENT,WRAP_CONTENT)
    local pad = math.floor(8 * density)
    ui.buttonTargetSetLabel:setLayoutParams(targetLabelParams)
    ui.buttonTargetSetLabel:setText("Switch to button set on tap:")
    ui.buttonTargetSetLabel:setTextSize(textSize)
    ui.buttonTargetSetLabel:setGravity(Gravity.LEFT)
    ui.buttonTargetSetLabel:setPadding(pad, math.floor(8 * density), pad, 0)
    ui.buttonTargetSetRow:addView(ui.buttonTargetSetLabel)
  end
  
  buttonTargetSetEditParams = fnew(LinearLayoutParams,FILL_PARENT,WRAP_CONTENT)
    
  if(ui.targetEdit == nil) then
    ui.targetEdit = fnew(EditText,context)  
    ui.targetEdit:setTextSize(textSize)
    ui.targetEdit:setLines(1)
    ui.targetEdit:setLayoutParams(buttonTargetSetEditParams)
    ui.buttonTargetSetRow:addView(ui.targetEdit)
  end
  if ui.buttonTargetSetHint ~= nil then
    ui.buttonTargetSetHint:setVisibility(View.GONE)
  end
  -- Hidden, not deleted: switchTo is still read on save so an old value is not
  -- wiped. Switching pads is `.loadset` on the Tap command.
  ui.buttonTargetSetRow:setVisibility(View.GONE)
  buttoneditorlook.attachIcon(ui.advancedPage, editorValues, numediting)
  if(numediting > 1) then
    ui.targetEdit:setEnabled(false)
    ui.targetEdit:setText("")
    
  else
    if(editorValues.switchTo ~= nil) then
      ui.targetEdit:setEnabled(true)
      ui.targetEdit:setText(editorValues.switchTo)
    end
  end
  
  
  colortopLabelParams = fnew(LinearLayoutParams,FILL_PARENT,WRAP_CONTENT)
    
  if(ui.colortopLabel == nil) then
    ui.colortopLabel = fnew(TextView,context)
    colortopLabelParams:setMargins(0,10,0,10)
    ui.colortopLabel:setLayoutParams(colortopLabelParams)
    ui.advancedPage:addView(ui.colortopLabel)
  end
  styleSectionBar(ui.colortopLabel, "COLORED PAD")
  if ui.colorNote == nil then
    ui.colorNote = fnew(TextView, context)
    ui.colorNote:setTextSize(textSizeSmall)
    ui.colorNote:setTextColor(DESC_TEXT)
    local pad = math.floor(10 * density)
    ui.colorNote:setPadding(pad, 0, pad, math.floor(4 * density))
    ui.colorNote:setLayoutParams(fillparams)
    ui.advancedPage:addView(ui.colorNote)
  end
  if ui.multiColorEdit == true then
    paintColorNote("multi")
  else
    paintColorNote("slider")
  end
  
  if(ui.colorRowOne == nil) then
    ui.colorRowOne = fnew(LinearLayout,context)
    ui.colorRowOne:setLayoutParams(fillparams)
    ui.advancedPage:addView(ui.colorRowOne)
  end
  
  if(ui.colorHolderA == nil) then
    ui.colorHolderA = fnew(LinearLayout,context)
    ui.colorHolderA:setLayoutParams(fillparams)
    ui.colorHolderA:setGravity(GRAVITY_CENTER)
    --Note("addiing normal color holder")
    ui.colorRowOne:addView(ui.colorHolderA)
  end
  
  wrapparams = fnew(LinearLayoutParams,WRAP_CONTENT,WRAP_CONTENT)
  touchparams = fnew(LinearLayoutParams,60,60)
  
  if(ui.normalColorPicker == nil) then
    ui.normalColorPicker = fnew(View,context)
    ui.normalColorPicker:setLayoutParams(touchparams)
    ui.normalColor = editorValues.primaryColor
    showSwatch(ui.normalColorPicker, ui.normalColor)
    ui.normalColorPicker:setTag("normal")
    bindColorSwatch(ui.normalColorPicker)
    --Note("addiing normal color")
    ui.colorHolderA:addView(ui.normalColorPicker)
  else 
    ui.normalColor = editorValues.primaryColor
    showSwatch(ui.normalColorPicker, ui.normalColor)
  end

  if(ui.colorHolderB == nil) then
    ui.colorHolderB = fnew(LinearLayout,context)
    ui.colorHolderB:setLayoutParams(fillparams)
    ui.colorHolderB:setGravity(GRAVITY_CENTER)
    --Note("addiing pressed color holder")
    ui.colorRowOne:addView(ui.colorHolderB)
  end
  
  if(ui.pressedColorPicker == nil) then
    ui.pressedColorPicker = fnew(View,context)
    ui.pressedColorPicker:setLayoutParams(touchparams)
    ui.pressedColorPicker:setTag("pressed")
    bindColorSwatch(ui.pressedColorPicker)
    --thePressedColor = Color:argb(255,120,250,250)
    ui.pressedColor = editorValues.selectedColor
    showSwatch(ui.pressedColorPicker, ui.pressedColor)
    --Note("addiing pressed color")
    ui.colorHolderB:addView(ui.pressedColorPicker)
  else
    ui.pressedColor = editorValues.selectedColor
    showSwatch(ui.pressedColorPicker, ui.pressedColor)
  end
  
  if(ui.colorHolderC == nil) then
    ui.colorHolderC = fnew(LinearLayout,context)
    ui.colorHolderC:setLayoutParams(fillparams)
    ui.colorHolderC:setGravity(GRAVITY_CENTER)
    --Note("addiing flip colo hodlerr")
    ui.colorRowOne:addView(ui.colorHolderC)
  end
  
  if(ui.flipColorPicker == nil) then
    ui.flipColorPicker = fnew(View,context)
    ui.flipColorPicker:setLayoutParams(touchparams)
    ui.flipColorPicker:setTag("flip")
    bindColorSwatch(ui.flipColorPicker)
    ui.flipColor = editorValues.flipColor
    --Note("addiing flip color")
    showSwatch(ui.flipColorPicker, ui.flipColor)
    ui.colorHolderC:addView(ui.flipColorPicker)
  else
    ui.flipColor = editorValues.flipColor
    showSwatch(ui.flipColorPicker, ui.flipColor)
  end
  
  if(ui.labelRowOne == nil) then
    ui.labelRowOne = fnew(LinearLayout,context)
    ui.labelRowOne:setLayoutParams(fillparams)
    ui.advancedPage:addView(ui.labelRowOne)
  end

  if(ui.normalLabel == nil) then
    ui.normalLabel = fnew(TextView,context)
    ui.normalLabel:setLayoutParams(fillparams)
    ui.normalLabel:setGravity(GRAVITY_CENTER)
    ui.normalLabel:setText("Normal")
    ui.normalLabel:setTextSize(textSizeSmall)
    ui.labelRowOne:addView(ui.normalLabel)
  end
  
  if(ui.pressedLabel == nil) then
    ui.pressedLabel = fnew(TextView,context)
    ui.pressedLabel:setLayoutParams(fillparams)
    ui.pressedLabel:setGravity(GRAVITY_CENTER)
    ui.pressedLabel:setText("Pressed")
    ui.pressedLabel:setTextSize(textSizeSmall)
    ui.labelRowOne:addView(ui.pressedLabel)
  end
  
  if(ui.flippedLabel == nil) then
    ui.flippedLabel = fnew(TextView,context)
    ui.flippedLabel:setLayoutParams(fillparams)
    ui.flippedLabel:setGravity(GRAVITY_CENTER)
    ui.flippedLabel:setText("Flipped")
    ui.flippedLabel:setTextSize(textSizeSmall)
    ui.labelRowOne:addView(ui.flippedLabel)
  end
  
  if(ui.colorRowTwo == nil) then
    ui.colorRowTwo = fnew(LinearLayout,context)
    colorRowTwoParams = fnew(LinearLayoutParams,fillparams)
    colorRowTwoParams:setMargins(0,10,0,0)
    ui.colorRowTwo:setLayoutParams(colorRowTwoParams)
    ui.advancedPage:addView(ui.colorRowTwo)
  end
  
  if(ui.colorHolderD == nil) then
    ui.colorHolderD = fnew(LinearLayout,context)
    ui.colorHolderD:setLayoutParams(fillparams)
    ui.colorHolderD:setGravity(GRAVITY_CENTER)
    safeAddView(ui.colorRowTwo, ui.colorHolderD)
  end
  
  if(ui.normalLabelColorPicker == nil) then
    ui.normalLabelColorPicker = fnew(View,context)
    ui.normalLabelColorPicker:setLayoutParams(touchparams)
    ui.normalLabelColor = editorValues.labelColor
    showSwatch(ui.normalLabelColorPicker, ui.normalLabelColor)
    ui.normalLabelColorPicker:setTag("label")
    bindColorSwatch(ui.normalLabelColorPicker)
    safeAddView(ui.colorHolderD, ui.normalLabelColorPicker)
  else
    ui.normalLabelColor = editorValues.labelColor
    showSwatch(ui.normalLabelColorPicker, ui.normalLabelColor)
  end
  
  if(ui.colorHolderE == nil) then
    ui.colorHolderE = fnew(LinearLayout,context)
    ui.colorHolderE:setLayoutParams(fillparams)
    ui.colorHolderE:setGravity(GRAVITY_CENTER)
    ui.colorRowTwo:addView(ui.colorHolderE)
  end
  
  if(ui.flipLabelColorPicker == nil) then
    ui.flipLabelColorPicker = fnew(View,context)
    ui.flipLabelColorPicker:setLayoutParams(touchparams)
    ui.flipLabelColorPicker:setTag("flipLabel")
    bindColorSwatch(ui.flipLabelColorPicker)
    --theFlipLabelColor = Color:argb(255,120,250,250)
    ui.flipLabelColor = editorValues.flipLabelColor
    showSwatch(ui.flipLabelColorPicker, ui.flipLabelColor)
    ui.colorHolderE:addView(ui.flipLabelColorPicker)
  else
    ui.flipLabelColor = editorValues.flipLabelColor
    showSwatch(ui.flipLabelColorPicker, ui.flipLabelColor)
  end
  
  if(ui.colorHolderF == nil) then
    ui.colorHolderF = fnew(LinearLayout,context)
    ui.colorHolderF:setLayoutParams(fillparams)
    ui.colorHolderF:setOrientation(LinearLayout.HORIZONTAL)
    ui.colorHolderF:setGravity(Gravity.CENTER)
    ui.colorRowTwo:addView(ui.colorHolderF)
  end
  if ui.invisible ~= nil then
    ui.invisible:setVisibility(View.GONE)
  end
  
  if(ui.labelRowTwo == nil) then
    ui.labelRowTwo = fnew(LinearLayout,context)
    ui.labelRowTwo:setLayoutParams(fillparams)
    ui.advancedPage:addView(ui.labelRowTwo)
  end
  
  if(ui.normalLabelLabel == nil) then
    ui.normalLabelLabel = fnew(TextView,context)
    ui.normalLabelLabel:setLayoutParams(fillparams)
    ui.normalLabelLabel:setGravity(GRAVITY_CENTER)
    ui.normalLabelLabel:setText("Label")
    ui.normalLabelLabel:setTextSize(textSizeSmall)
    ui.labelRowTwo:addView(ui.normalLabelLabel)
  end
  
  if(ui.flipLabelLabel == nil) then
    ui.flipLabelLabel = fnew(TextView,context)
    ui.flipLabelLabel:setLayoutParams(fillparams)
    ui.flipLabelLabel:setGravity(GRAVITY_CENTER)
    ui.flipLabelLabel:setText("FlipLabel")
    ui.flipLabelLabel:setTextSize(textSizeSmall)
    ui.labelRowTwo:addView(ui.flipLabelLabel)
  end
  
  if(ui.invisLabel == nil) then
    ui.invisLabel = fnew(TextView,context)
    ui.invisLabel:setLayoutParams(fillparams)
    ui.invisLabel:setGravity(GRAVITY_CENTER)
    ui.invisLabel:setTextSize(textSizeSmall)
    ui.labelRowTwo:addView(ui.invisLabel)
  end
  ui.invisLabel:setText("Border")
  ui.invisLabel:setVisibility(View.VISIBLE)

  -- Same 60px swatch as Normal/Pressed, checkbox beside it — not a separate
  -- BORDER bar whose weight-1 checkbox shoved a 36*density tile off the row.
  if ui.borderSectionLabel ~= nil then
    ui.borderSectionLabel:setVisibility(View.GONE)
  end
  if ui.borderLine ~= nil then
    ui.borderLine:setVisibility(View.GONE)
  end
  if ui.borderColorRow ~= nil then
    ui.borderColorRow:setVisibility(View.GONE)
  end
  if ui.borderColorLabel ~= nil then
    ui.borderColorLabel:setVisibility(View.GONE)
  end

  if(ui.borderColorPicker == nil) then
    ui.borderColorPicker = fnew(View,context)
    ui.borderColorPicker:setLayoutParams(touchparams)
    ui.borderColorPicker:setTag("border")
    bindColorSwatch(ui.borderColorPicker)
  end
  safeAddView(ui.colorHolderF, ui.borderColorPicker)
  ui.borderColor = editorValues.borderColor or defaultColors.border
  showSwatch(ui.borderColorPicker, ui.borderColor)

  if(ui.borderCheck == nil) then
    ui.borderCheck = fnew(CheckBox,context)
    local checkParams = fnew(LinearLayoutParams, WRAP_CONTENT, WRAP_CONTENT)
    ui.borderCheck:setLayoutParams(checkParams)
    ui.borderCheck:setSingleLine(false)
    ui.borderCheck:setMaxLines(2)
    ui.borderCheck:setText("Draw\nborder")
    ui.borderCheck:setTextSize(textSizeSmall)
  end
  safeAddView(ui.colorHolderF, ui.borderCheck)
  ui.borderCheck:setChecked(editorValues.border == true)

  hideEssay(ui.borderHelp)

  local function syncBorderSwatchEnabled()
    local on = ui.borderCheck ~= nil and ui.borderCheck:isChecked()
    if ui.borderColorPicker ~= nil then
      ui.borderColorPicker:setEnabled(on)
      -- Dim when off so it is obvious the colour only applies with the tick.
      if on then
        ui.borderColorPicker:setAlpha(1.0)
      else
        ui.borderColorPicker:setAlpha(0.4)
      end
    end
    if ui.borderColorLabel ~= nil then
      if on then
        ui.borderColorLabel:setAlpha(1.0)
      else
        ui.borderColorLabel:setAlpha(0.4)
      end
    end
  end
  if ui.borderCheckListener == nil then
    ui.borderCheckListener = luajava.createProxy("android.widget.CompoundButton$OnCheckedChangeListener",{
      onCheckedChanged = function(buttonView, isChecked)
        syncBorderSwatchEnabled()
      end
    })
    ui.borderCheck:setOnCheckedChangeListener(ui.borderCheckListener)
  end
  syncBorderSwatchEnabled()
  
  if(ui.typeInLabel == nil) then
    ui.typeInLabel = fnew(TextView,context)
    typeInLabelParams = fnew(LinearLayoutParams,FILL_PARENT,WRAP_CONTENT)
    typeInLabelParams:setMargins(0,10,0,10)
    ui.typeInLabel:setLayoutParams(typeInLabelParams)
    ui.advancedPage:addView(ui.typeInLabel)
  end
  styleSectionBar(ui.typeInLabel, "SIZE & POSITION")
  
  if(ui.controlRowOne == nil) then
    ui.controlRowOne = fnew(LinearLayout,context)
    ui.controlRowOne:setLayoutParams(fillparams)
    ui.advancedPage:addView(ui.controlRowOne)
  end
  
  if(ui.controlHolderA == nil) then
    ui.controlHolderA = fnew(LinearLayout,context)
    ui.controlHolderA:setLayoutParams(fillparams)
    ui.controlHolderA:setGravity(GRAVITY_CENTER)
    ui.controlRowOne:addView(ui.controlHolderA)
  end
  
  numbereditorParams = fnew(LinearLayoutParams,120*density,WRAP_CONTENT)
  if(ui.labelSizeEdit == nil) then
    ui.labelSizeEdit = fnew(EditText,context)
    ui.labelSizeEdit:setInputType(TYPE_CLASS_NUMBER)
    ui.labelSizeEdit:setLayoutParams(numbereditorParams)
    ui.labelSizeEdit:setGravity(GRAVITY_CENTER)
    ui.labelSizeEdit:setTextSize(textSize)
    ui.controlHolderA:addView(ui.labelSizeEdit)
  end
  if(editorValues.labelSize == "MULTI") then
    ui.labelSizeEdit:setText("")
  else
    ui.labelSizeEdit:setText(tostring(math.floor(editorValues.labelSize)))
  end
  
  if(ui.controlHolderB == nil) then
    ui.controlHolderB = fnew(LinearLayout,context)
    ui.controlHolderB:setLayoutParams(fillparams)
    ui.controlHolderB:setGravity(GRAVITY_CENTER)
    ui.controlRowOne:addView(ui.controlHolderB)
  end
  --numbereditorParams = fnew(LinearLayoutParams,120,WRAP_CONTENT)
  if(ui.widthEdit == nil) then
    ui.widthEdit = fnew(EditText,context)
    ui.widthEdit:setLayoutParams(numbereditorParams)
    ui.widthEdit:setInputType(TYPE_CLASS_NUMBER)
    ui.widthEdit:setGravity(GRAVITY_CENTER)
    ui.widthEdit:setTextSize(textSize)
    ui.controlHolderB:addView(ui.widthEdit)
  end
  if(editorValues.width == "MULTI") then
    ui.widthEdit:setText("")
  else
    ui.widthEdit:setText(tostring(math.floor(editorValues.width)))
  end
  
  if(ui.controlHolderC == nil) then
    ui.controlHolderC = fnew(LinearLayout,context)
    ui.controlHolderC:setLayoutParams(fillparams)
    ui.controlHolderC:setGravity(GRAVITY_CENTER)
    ui.controlRowOne:addView(ui.controlHolderC)
  end
  --numbereditorParams = fnew(LinearLayoutParams,120,WRAP_CONTENT)
  if(ui.heightEdit == nil) then
    ui.heightEdit = fnew(EditText,context)
    ui.heightEdit:setLayoutParams(numbereditorParams)
    ui.heightEdit:setInputType(TYPE_CLASS_NUMBER)
    ui.heightEdit:setGravity(GRAVITY_CENTER)
    ui.heightEdit:setTextSize(textSize)
    ui.controlHolderC:addView(ui.heightEdit)
  end
  if(editorValues.height == "MULTI") then
    ui.heightEdit:setText("")
  else
    ui.heightEdit:setText(tostring(math.floor(editorValues.height)))
  end
  
  if(ui.labelRowThree == nil) then
    ui.labelRowThree = fnew(LinearLayout,context)
    ui.labelRowThree:setLayoutParams(fillparams)
    ui.advancedPage:addView(ui.labelRowThree)
  end
  
  if(ui.labelSizeCaptionColumn == nil) then
    ui.labelSizeCaptionColumn = fnew(LinearLayout,context)
    ui.labelSizeCaptionColumn:setOrientation(LinearLayout.VERTICAL)
    ui.labelSizeCaptionColumn:setLayoutParams(fillparams)
    ui.labelSizeCaptionColumn:setGravity(GRAVITY_CENTER)
    ui.labelRowThree:addView(ui.labelSizeCaptionColumn)
  end

  if(ui.labelSizeLabel == nil) then
    ui.labelSizeLabel = fnew(TextView,context)
    ui.labelSizeLabel:setLayoutParams(fillparams)
    ui.labelSizeLabel:setGravity(GRAVITY_CENTER)
    ui.labelSizeLabel:setText("Label Font Size")
    ui.labelSizeLabel:setTextSize(textSizeSmall)
  end
  safeAddView(ui.labelSizeCaptionColumn, ui.labelSizeLabel)

  if(ui.widthLabel == nil) then
    ui.widthLabel = fnew(TextView,context)
    ui.widthLabel:setLayoutParams(fillparams)
    ui.widthLabel:setGravity(GRAVITY_CENTER)
    ui.widthLabel:setText("Width")
    ui.widthLabel:setTextSize(textSizeSmall)
    ui.labelRowThree:addView(ui.widthLabel)
  end
  
  if(ui.heightLabel == nil) then
    ui.heightLabel = fnew(TextView,context)
    ui.heightLabel:setLayoutParams(fillparams)
    ui.heightLabel:setGravity(GRAVITY_CENTER)
    ui.heightLabel:setText("Height")
    ui.heightLabel:setTextSize(textSizeSmall)
    ui.labelRowThree:addView(ui.heightLabel)
  --ui.invisLabel:setVisibility(View.INVISIBLE)
  end
  
  if(ui.controlRowTwo == nil) then
    ui.controlRowTwo = fnew(LinearLayout,context)
    ui.controlRowTwo:setLayoutParams(fillparams)
    ui.advancedPage:addView(ui.controlRowTwo)
  end
  ui.controlRowTwo:setVisibility(View.VISIBLE)
  
  if(ui.controlHolderD == nil) then
    ui.controlHolderD = fnew(LinearLayout,context)
    ui.controlHolderD:setLayoutParams(fillparams)
    ui.controlHolderD:setGravity(GRAVITY_CENTER)
    ui.controlRowTwo:addView(ui.controlHolderD)
  end
  --numbereditorParams = fnew(LinearLayoutParams,120,WRAP_CONTENT)
  if(ui.xCoordEdit == nil) then
    ui.xCoordEdit = fnew(EditText,context)
    ui.xCoordEdit:setLayoutParams(numbereditorParams)
    ui.xCoordEdit:setInputType(TYPE_CLASS_NUMBER)
    ui.xCoordEdit:setGravity(GRAVITY_CENTER)
    ui.xCoordEdit:setTextSize(textSize)
    ui.controlHolderD:addView(ui.xCoordEdit)
  end
  if(editorValues.x == "MULTI") then
    --Note("setting x string:MULTI")
    ui.xCoordEdit:setText("")
  else
    --Note("setting x string:"..editorValues.x)
    ui.xCoordEdit:setText(tostring(math.floor(editorValues.x)))
  end
  
  if(ui.controlHolderE == nil) then
    ui.controlHolderE = fnew(LinearLayout,context)
    ui.controlHolderE:setLayoutParams(fillparams)
    ui.controlHolderE:setGravity(GRAVITY_CENTER)
    ui.controlRowTwo:addView(ui.controlHolderE)
  end
  --numbereditorParams = fnew(LinearLayoutParams,120,WRAP_CONTENT)
  if(ui.yCoordEdit == nil) then
    ui.yCoordEdit = fnew(EditText,context)
    ui.yCoordEdit:setLayoutParams(numbereditorParams)
    ui.yCoordEdit:setInputType(TYPE_CLASS_NUMBER)
    ui.yCoordEdit:setGravity(GRAVITY_CENTER)
    ui.yCoordEdit:setTextSize(textSize)
    ui.controlHolderE:addView(ui.yCoordEdit)
  end
  if(editorValues.y == "MULTI") then
    ui.yCoordEdit:setText("")
  else
    ui.yCoordEdit:setText(tostring(math.floor(editorValues.y)))
  end
  
  if(ui.controlHolderF == nil) then
    ui.controlHolderF = fnew(LinearLayout,context)
    ui.controlHolderF:setLayoutParams(fillparams)
    ui.controlHolderF:setGravity(GRAVITY_CENTER)
    ui.controlRowTwo:addView(ui.controlHolderF)
  end
  
  if(ui.invisibleControl == nil) then
    ui.invisibleControl = fnew(View,context)
    ui.invisibleControl:setVisibility(View.INVISIBLE)
    ui.invisibleControl:setLayoutParams(fillparams)
    ui.controlHolderF:addView(ui.invisibleControl)
  end
  
  if(ui.labelRowFour == nil) then
    ui.labelRowFour = fnew(LinearLayout,context)
    ui.labelRowFour:setLayoutParams(fillparams)
    ui.advancedPage:addView(ui.labelRowFour)
  end
  
  if(ui.xcoordLabel == nil) then
    ui.xcoordLabel = fnew(TextView,context)
    ui.xcoordLabel:setLayoutParams(fillparams)
    ui.xcoordLabel:setGravity(GRAVITY_CENTER)
    ui.xcoordLabel:setText("X Coord")
    ui.xcoordLabel:setTextSize(textSizeSmall)
    ui.labelRowFour:addView(ui.xcoordLabel)
  end
  
  if(ui.ycoordLabel == nil) then
    ui.ycoordLabel = fnew(TextView,context)
    ui.ycoordLabel:setLayoutParams(fillparams)
    ui.ycoordLabel:setGravity(GRAVITY_CENTER)
    ui.ycoordLabel:setText("Y Coord")
    ui.ycoordLabel:setTextSize(textSizeSmall)
    ui.labelRowFour:addView(ui.ycoordLabel)
  end
  
  if(ui.invisControlLabel == nil) then
    ui.invisControlLabel = fnew(TextView,context)
    ui.invisControlLabel:setLayoutParams(fillparams)
    ui.invisControlLabel:setGravity(GRAVITY_CENTER)
    ui.invisControlLabel:setText("FlipLabel")
    ui.invisControlLabel:setTextSize(textSizeSmall)
    ui.invisControlLabel:setVisibility(View.INVISIBLE)
    ui.labelRowFour:addView(ui.invisControlLabel)
  end

  buttoneditorlook.attachAvoid(ui.advancedPage, editorValues, numediting)

  -- Floating-button options (Advanced tab). Single-button edit only.
  if(ui.floatSectionLabel == nil) then
    ui.floatSectionLabel = fnew(TextView,context)
    local floatSectionParams = fnew(LinearLayoutParams,FILL_PARENT,WRAP_CONTENT)
    floatSectionParams:setMargins(0,10,0,10)
    ui.floatSectionLabel:setLayoutParams(floatSectionParams)
    ui.advancedPage:addView(ui.floatSectionLabel)
  end
  styleSectionBar(ui.floatSectionLabel, "FLOATING")

  if(ui.floatHelp == nil) then
    ui.floatHelp = fnew(TextView,context)
    ui.floatHelp:setLayoutParams(fillparams)
    ui.floatHelp:setTextSize(textSizeSmall)
    ui.floatHelp:setTextColor(DESC_TEXT)
    ui.floatHelp:setMaxLines(2)
    ui.floatHelp:setText("Puts a copy of this button on the screen, over the game.")
    ui.advancedPage:addView(ui.floatHelp)
  end
  ui.floatHelp:setPadding(othersSide, math.floor(4 * density), othersSide, math.floor(2 * density))

  if(ui.floatingCheck == nil) then
    ui.floatingCheck = fnew(CheckBox,context)
    ui.floatingCheck:setLayoutParams(fillparams)
    ui.floatingCheck:setText("Float over the game")
    ui.floatingCheck:setTextSize(textSize)
    ui.advancedPage:addView(ui.floatingCheck)
  end
  ui.floatingCheck:setChecked(editorValues.floating == true)

  if(ui.floatModeRow == nil) then
    ui.floatModeRow = fnew(LinearLayout,context)
    ui.floatModeRow:setLayoutParams(fillparams)
    ui.advancedPage:addView(ui.floatModeRow)

    ui.floatModeLabel = fnew(TextView,context)
    local floatModeLabelParams = fnew(LinearLayoutParams,80*density,WRAP_CONTENT)
    ui.floatModeLabel:setLayoutParams(floatModeLabelParams)
    ui.floatModeLabel:setText("When:")
    ui.floatModeLabel:setTextSize(textSize)
    ui.floatModeLabel:setGravity(Gravity.RIGHT)
    ui.floatModeRow:addView(ui.floatModeLabel)

    ui.floatModeSpinner = fnew(Spinner,context)
    local floatModeEditParams = fnew(LinearLayoutParams,FILL_PARENT,WRAP_CONTENT)
    ui.floatModeSpinner:setLayoutParams(floatModeEditParams)
    local pkg = context:getPackageName()
    local res = context:getResources()
    local spinnerItemLayout = res:getIdentifier("spinner_item_dark", "layout", pkg)
    local spinnerDropdownLayout = res:getIdentifier("spinner_dropdown_item_dark", "layout", pkg)
    local modeAdapter = luajava.new(ArrayAdapter,context,spinnerItemLayout)
    modeAdapter:add("Always visible")
    modeAdapter:add("Show with keyboard")
    modeAdapter:setDropDownViewResource(spinnerDropdownLayout)
    ui.floatModeSpinner:setAdapter(modeAdapter)
    local ColorDrawable = luajava.bindClass("android.graphics.drawable.ColorDrawable")
    ui.floatModeSpinner:setPopupBackgroundDrawable(luajava.new(ColorDrawable, Color:argb(255, 0, 0, 0)))
    ui.floatModeSpinner:setBackgroundColor(Color:argb(255, 0, 0, 0))
    ui.floatModeRow:addView(ui.floatModeSpinner)
  end
  hideEssay(ui.floatModeHelp)
  if editorValues.floatMode == "keyboard" then
    ui.floatModeSpinner:setSelection(1)
  else
    ui.floatModeSpinner:setSelection(0)
  end

  -- Shape is exclusive (square OR round). Outline is a separate thin border.
  if(ui.floatShapeRow == nil) then
    ui.floatShapeRow = fnew(LinearLayout,context)
    ui.floatShapeRow:setLayoutParams(fillparams)
    ui.advancedPage:addView(ui.floatShapeRow)

    ui.floatShapeLabel = fnew(TextView,context)
    local floatShapeLabelParams = fnew(LinearLayoutParams,80*density,WRAP_CONTENT)
    ui.floatShapeLabel:setLayoutParams(floatShapeLabelParams)
    ui.floatShapeLabel:setText("Shape:")
    ui.floatShapeLabel:setTextSize(textSize)
    ui.floatShapeLabel:setGravity(Gravity.RIGHT)
    ui.floatShapeRow:addView(ui.floatShapeLabel)

    ui.floatShapeSpinner = fnew(Spinner,context)
    local floatShapeEditParams = fnew(LinearLayoutParams,FILL_PARENT,WRAP_CONTENT)
    ui.floatShapeSpinner:setLayoutParams(floatShapeEditParams)
    local pkg2 = context:getPackageName()
    local res2 = context:getResources()
    local spinnerItemLayout2 = res2:getIdentifier("spinner_item_dark", "layout", pkg2)
    local spinnerDropdownLayout2 = res2:getIdentifier("spinner_dropdown_item_dark", "layout", pkg2)
    local shapeAdapter = luajava.new(ArrayAdapter,context,spinnerItemLayout2)
    shapeAdapter:add("Square")
    shapeAdapter:add("Round")
    shapeAdapter:setDropDownViewResource(spinnerDropdownLayout2)
    ui.floatShapeSpinner:setAdapter(shapeAdapter)
    local ColorDrawable2 = luajava.bindClass("android.graphics.drawable.ColorDrawable")
    ui.floatShapeSpinner:setPopupBackgroundDrawable(luajava.new(ColorDrawable2, Color:argb(255, 0, 0, 0)))
    ui.floatShapeSpinner:setBackgroundColor(Color:argb(255, 0, 0, 0))
    ui.floatShapeRow:addView(ui.floatShapeSpinner)
  end
  if editorValues.floatRound == true then
    ui.floatShapeSpinner:setSelection(1)
  else
    ui.floatShapeSpinner:setSelection(0)
  end

  if(ui.floatOutlineCheck == nil) then
    ui.floatOutlineCheck = fnew(CheckBox,context)
    ui.floatOutlineCheck:setLayoutParams(fillparams)
    ui.floatOutlineCheck:setText("Thin outline")
    ui.floatOutlineCheck:setTextSize(textSize)
    ui.advancedPage:addView(ui.floatOutlineCheck)
  end
  ui.floatOutlineCheck:setChecked(editorValues.floatFrame == true)
  hideEssay(ui.floatOutlineHelp)

  -- A floating copy is one button's, so the whole section goes away for a
  -- multi-button edit rather than sitting there greyed. Both branches set the
  -- visibility: ui outlives the dialog, so a section hidden for a multi-edit
  -- and never shown again would be missing from the next single-button edit.
  local floatEnabled = (numediting == 1)
  local floatVis = floatEnabled and View.VISIBLE or View.GONE
  local floatAlways = {
    ui.floatSectionLabel, ui.floatHelp, ui.floatingCheck,
  }
  for i = 1, #floatAlways do
    if floatAlways[i] ~= nil then
      floatAlways[i]:setVisibility(floatVis)
    end
  end
  ui.floatingCheck:setEnabled(floatEnabled)
  if ui.floatCheckListener == nil then
    ui.floatCheckListener = luajava.createProxy("android.widget.CompoundButton$OnCheckedChangeListener",{
      onCheckedChanged = function(buttonView, isChecked)
        applyFloatRowFold()
        local on = ui.floatingCheck:isEnabled() and isChecked
        -- The Accordion tab has to grey out with this tick, not on the next
        -- time the editor is opened: a super button's accordion is thrown away
        -- on save, and a player who typed sub-buttons first would lose them
        -- with nothing on screen having said so.
        if floatingChangedCallback ~= nil then
          floatingChangedCallback(on)
        end
      end
    })
    ui.floatingCheck:setOnCheckedChangeListener(ui.floatCheckListener)
  end
  applyFloatRowFold()
  if not floatEnabled then
    if ui.floatModeRow ~= nil then ui.floatModeRow:setVisibility(View.GONE) end
    if ui.floatShapeRow ~= nil then ui.floatShapeRow:setVisibility(View.GONE) end
    if ui.floatOutlineCheck ~= nil then ui.floatOutlineCheck:setVisibility(View.GONE) end
  end
  
  return ui.advancedPageScroller
  
end

function getEditorValues()
  local tmp = {}
  tmp.xCoord = tonumber(ui.xCoordEdit:getText():toString())
  tmp.yCoord = tonumber(ui.yCoordEdit:getText():toString())
  tmp.name = ui.nameEdit:getText():toString()
  tmp.target = ui.targetEdit:getText():toString()
  tmp.normalColor = ui.normalColor
  tmp.flipColor = ui.flipColor
  tmp.pressedColor = ui.pressedColor
  tmp.normalLabelColor = ui.normalLabelColor
  tmp.flipLabelColor = ui.flipLabelColor
  tmp.border = ui.borderCheck ~= nil and ui.borderCheck:isChecked()
  tmp.borderColor = ui.borderColor
  tmp.labelSize = tonumber(ui.labelSizeEdit:getText():toString())
  if ui.activeCheck ~= nil then
    tmp.active = ui.activeCheck:isChecked()
  else
    tmp.active = true
  end
  tmp.height = tonumber(ui.heightEdit:getText():toString())
  tmp.width = tonumber(ui.widthEdit:getText():toString())
  if ui.floatingCheck ~= nil and ui.floatingCheck:isEnabled() then
    tmp.floating = ui.floatingCheck:isChecked()
    local modeIndex = 0
    if ui.floatModeSpinner ~= nil then
      modeIndex = tonumber(ui.floatModeSpinner:getSelectedItemPosition()) or 0
    end
    if modeIndex == 1 then
      tmp.floatMode = "keyboard"
    else
      tmp.floatMode = "always"
    end
    local shapeIndex = 0
    if ui.floatShapeSpinner ~= nil then
      shapeIndex = tonumber(ui.floatShapeSpinner:getSelectedItemPosition()) or 0
    end
    tmp.floatRound = (shapeIndex == 1)
    tmp.floatFrame = ui.floatOutlineCheck ~= nil and ui.floatOutlineCheck:isChecked()
  end
  return tmp
end

makeLabel = function(text,textSize,gravity,params)
  local tmp = quicknew(TextView,context)
  tmp:setLayoutParams(params)
  tmp:setText(text)
  tmp:setTextSize(textSize)
  tmp:setGravity(gravity)
  return tmp
end

makeEdit = function(params,pTextSize)
  local tmp = quicknew(EditText,context)
  tmp:setLines(1)
  tmp:setLayoutParams(params)
  tmp:setTextSize(pTextSize)
  return tmp
end

swatchClickListener = luajava.createProxy("android.view.View$OnClickListener",{
  onClick = function(v)
    selectedColorField = v:getTag()
    local color = 0
    if(selectedColorField == "flip") then
      color = ui.flipColor
    elseif(selectedColorField == "normal") then
      color = ui.normalColor
    elseif(selectedColorField == "pressed") then
      color = ui.pressedColor
    elseif(selectedColorField == "label") then
      color = ui.normalLabelColor
    elseif(selectedColorField == "flipLabel") then
      color = ui.flipLabelColor
    elseif(selectedColorField == "border") then
      color = ui.borderColor
    end
    local shown = displayColor(color, labelColorField(selectedColorField) and "label" or "pad")
    if shown == nil then
      shown = color
    end
    colorpickerdialog = luajava.newInstance("com.resurrection.blowtorch2.lib.button.ColorPickerDialog",context,colorPickerDoneListener,shown)
    local sliderShown = buttoneditorlook.opacitySliderShown()
    if labelColorField(selectedColorField) then
      sliderShown = buttoneditorlook.labelOpacitySliderShown()
    end
    colorpickerdialog:setShowAlpha(ui.multiColorEdit ~= true and not sliderShown)
    colorpickerdialog:show()
  end
})

swatchLongClickListener = luajava.createProxy("android.view.View$OnLongClickListener",{
  onLongClick = function(v)
    applyDefaultColor(v:getTag())
    return true
  end
})

colorPickerDoneListener = luajava.createProxy("com.resurrection.blowtorch2.lib.button.ColorPickerDialog$OnColorChangedListener",{
  colorChanged = function(color)
    if(selectedColorField == "flip") then
      ui.flipColor = pickedColor(ui.flipColor, color, "flip")
      showSwatch(ui.flipColorPicker, ui.flipColor)
    elseif(selectedColorField == "normal") then
      ui.normalColor = pickedColor(ui.normalColor, color, "normal")
      showSwatch(ui.normalColorPicker, ui.normalColor)
    elseif(selectedColorField == "pressed") then
      ui.pressedColor = pickedColor(ui.pressedColor, color, "pressed")
      showSwatch(ui.pressedColorPicker, ui.pressedColor)
    elseif(selectedColorField == "label") then
      ui.normalLabelColor = pickedColor(ui.normalLabelColor, color, "label")
      showSwatch(ui.normalLabelColorPicker, ui.normalLabelColor)
    elseif(selectedColorField == "flipLabel") then
      ui.flipLabelColor = pickedColor(ui.flipLabelColor, color, "flipLabel")
      showSwatch(ui.flipLabelColorPicker, ui.flipLabelColor)
    elseif(selectedColorField == "border") then
      ui.borderColor = pickedColor(ui.borderColor, color, "border")
      showSwatch(ui.borderColorPicker, ui.borderColor)
    end
  end
})

showSetEditorControls = function()
  buttoneditorlook.hideForSetDefaults()
  paintColorNote("picker")
  ui.buttonTargetSetRow:setVisibility(View.GONE)
  hideEssay(ui.buttonTargetSetHint)
  hideEssay(ui.othersOneLiner)
  ui.buttonNameRow:setVisibility(View.VISIBLE)
  ui.controlRowTwo:setVisibility(View.GONE)
  ui.labelRowFour:setVisibility(View.GONE)
end

showButtonEditorControls = function()
  ui.controlRowTwo:setVisibility(View.VISIBLE)
  ui.labelRowFour:setVisibility(View.VISIBLE)
  ui.buttonNameRow:setVisibility(View.VISIBLE)
  ui.buttonTargetSetRow:setVisibility(View.VISIBLE)
  hideEssay(ui.buttonTargetSetHint)
  if ui.othersOneLiner ~= nil then
    ui.othersOneLiner:setVisibility(View.VISIBLE)
  end
end

