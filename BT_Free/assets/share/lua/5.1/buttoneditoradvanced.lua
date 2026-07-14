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
module(...)

local context = nil
local quicknew = luajava.new
local fillparams = luajava.new(LinearLayoutParams,FILL_PARENT,WRAP_CONTENT,1)
local textSizeBig = (18)
local textSize = (14)
local textSizeSmall = (10)
local bgGrey = Color:argb(255,0x99,0x99,0x99)

local ui = {}

local selectedColorField

local defaultColors = {}

local function applyDefaultColor(field)
  local color = defaultColors[field]
  if color == nil then
    return
  end
  if field == "flip" then
    ui.flipColorPicker:setBackgroundColor(color)
    ui.flipColorPicker:invalidate()
    ui.flipColor = color
  elseif field == "normal" then
    ui.normalColorPicker:setBackgroundColor(color)
    ui.normalColorPicker:invalidate()
    ui.normalColor = color
  elseif field == "pressed" then
    ui.pressedColorPicker:setBackgroundColor(color)
    ui.pressedColorPicker:invalidate()
    ui.pressedColor = color
  elseif field == "label" then
    ui.normalLabelColorPicker:setBackgroundColor(color)
    ui.normalLabelColorPicker:invalidate()
    ui.normalLabelColor = color
  elseif field == "flipLabel" then
    ui.flipLabelColorPicker:setBackgroundColor(color)
    ui.flipLabelColorPicker:invalidate()
    ui.flipLabelColor = color
  end
end

local function bindColorSwatch(view)
  view:setOnClickListener(swatchClickListener)
  view:setOnLongClickListener(swatchLongClickListener)
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

function init(pContext)
  context = pContext
end

function makeUI(editorValues,numediting)
  
  local fnew = luajava.new
  defaultColors.normal = editorValues.defaultPrimaryColor or Color:argb(0x88,0x00,0x00,0xFF)
  defaultColors.pressed = editorValues.defaultSelectedColor or Color:argb(0x88,0x00,0xFF,0x00)
  defaultColors.flip = editorValues.defaultFlipColor or Color:argb(0x88,0xFF,0x00,0x00)
  defaultColors.label = editorValues.defaultLabelColor or Color:argb(0xAA,0xAA,0xAA,0xAA)
  defaultColors.flipLabel = editorValues.defaultFlipLabelColor or Color:argb(0x88,0x00,0x00,0xFF)
  --local context = view:getContext()
  local LabelWidth = nil -- margin size for different screen layouts
  if(ui.advancedPageScroller == nil) then
    ui.advancedPageScroller = fnew(ScrollView,context)
    ui.advancedPageScroller:setId(5)
  end
  
  if(ui.advancedPage == nil) then
    ui.advancedPage = fnew(LinearLayout,context)
    ui.advancedPage:setOrientation(LinearLayout.VERTICAL)
    ui.advancedPageScroller:addView(ui.advancedPage)
  end
  
  --ui.buttonNameRow
  if(ui.buttonNameRow == nil) then
    ui.buttonNameRow = fnew(LinearLayout,context)
    ui.buttonNameRow:setLayoutParams(fillparams)
    ui.advancedPage:addView(ui.buttonNameRow)
  end
  
  if(numediting > 1) then
    ui.buttonNameRow:setVisibility(View.GONE)
  else
    ui.buttonNameRow:setVisibility(View.VISIBLE)
  end
  -- Adjust margins for larger screen sizes
  if(test == Configuration.SCREENLAYOUT_SIZE_XLARGE) then
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
    ui.buttonNameLabel:setGravity(Gravity.RIGHT)
    ui.buttonNameRow:addView(ui.buttonNameLabel)
  end
  
  buttonNameEditParams = fnew(LinearLayoutParams,FILL_PARENT,WRAP_CONTENT)
    
  --local buttonNameEdit = makeEdit(buttonNameEditParams)
  if(ui.nameEdit == nil) then
    ui.nameEdit = fnew(EditText,context) 
    ui.nameEdit:setTextSize(textSize)
    ui.nameEdit:setLines(1)
    ui.nameEdit:setLayoutParams(buttonNameEditParams)
    ui.buttonNameRow:addView(ui.nameEdit)
  end
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
  
  if(ui.buttonTargetSetRow == nil) then
    ui.buttonTargetSetRow = fnew(LinearLayout,context)
    ui.buttonTargetSetRow:setLayoutParams(fillparams)
    ui.advancedPage:addView(ui.buttonTargetSetRow)
  end
  if(numediting > 1) then
    ui.buttonTargetSetRow:setVisibility(View.GONE)
  else
    ui.buttonTargetSetRow:setVisibility(View.VISIBLE)
  end
  
  buttonTargetSetLabelParams = fnew(LinearLayoutParams,LabelWidth,WRAP_CONTENT)
  if(ui.buttonTargetSetLabel == nil) then
    ui.buttonTargetSetLabel = fnew(TextView,context)
    
    ui.buttonTargetSetLabel:setLayoutParams(buttonNameLabelParams)
    ui.buttonTargetSetLabel:setText("Target Set:")
    ui.buttonTargetSetLabel:setTextSize(textSize)
    ui.buttonTargetSetLabel:setGravity(Gravity.RIGHT)
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
    ui.colortopLabel:setTextSize(textSize)
    ui.colortopLabel:setText("COLORS (hold swatch = default)")
    ui.colortopLabel:setGravity(GRAVITY_CENTER)
    ui.colortopLabel:setTextColor(Color:argb(255,0x33,0x33,0x33))
    ui.colortopLabel:setBackgroundColor(bgGrey)
    ui.advancedPage:addView(ui.colortopLabel)
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
    ui.normalColorPicker:setBackgroundColor(ui.normalColor)
    ui.normalColorPicker:setTag("normal")
    bindColorSwatch(ui.normalColorPicker)
    --Note("addiing normal color")
    ui.colorHolderA:addView(ui.normalColorPicker)
  else 
    ui.normalColor = editorValues.primaryColor
    ui.normalColorPicker:setBackgroundColor(ui.normalColor)
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
    ui.pressedColorPicker:setBackgroundColor(ui.pressedColor)
    --Note("addiing pressed color")
    ui.colorHolderB:addView(ui.pressedColorPicker)
  else
    ui.pressedColor = editorValues.selectedColor
    ui.pressedColorPicker:setBackgroundColor(ui.pressedColor)
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
    ui.flipColorPicker:setBackgroundColor(ui.flipColor)
    ui.colorHolderC:addView(ui.flipColorPicker)
  else
    ui.flipColor = editorValues.flipColor
    ui.flipColorPicker:setBackgroundColor(ui.flipColor)
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
    ui.normalLabelColorPicker:setBackgroundColor(ui.normalLabelColor)
    ui.normalLabelColorPicker:setTag("label")
    bindColorSwatch(ui.normalLabelColorPicker)
    safeAddView(ui.colorHolderD, ui.normalLabelColorPicker)
  else
    ui.normalLabelColor = editorValues.labelColor
    ui.normalLabelColorPicker:setBackgroundColor(ui.normalLabelColor)
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
    ui.flipLabelColorPicker:setBackgroundColor(ui.flipLabelColor)
    ui.colorHolderE:addView(ui.flipLabelColorPicker)
  else
    ui.flipLabelColor = editorValues.flipLabelColor
    ui.flipLabelColorPicker:setBackgroundColor(ui.flipLabelColor)
  end
  
  if(ui.colorHolderF == nil) then
    ui.colorHolderF = fnew(LinearLayout,context)
    ui.colorHolderF:setLayoutParams(fillparams)
    ui.colorHolderF:setGravity(GRAVITY_CENTER)
    ui.colorRowTwo:addView(ui.colorHolderF)
  end
  
  if(ui.invisible == nil) then
    ui.invisible = fnew(View,context)
    ui.invisible:setVisibility(View.INVISIBLE)
    ui.invisible:setLayoutParams(fillparams)
    ui.colorHolderF:addView(ui.invisible)
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
    ui.invisLabel:setText("FlipLabel")
    ui.invisLabel:setTextSize(textSizeSmall)
    ui.invisLabel:setVisibility(View.INVISIBLE)
    ui.labelRowTwo:addView(ui.invisLabel)
  end
  
  if(ui.typeInLabel == nil) then
    ui.typeInLabel = fnew(TextView,context)
    typeInLabelParams = fnew(LinearLayoutParams,FILL_PARENT,WRAP_CONTENT)
    typeInLabelParams:setMargins(0,10,0,10)
    ui.typeInLabel:setLayoutParams(typeInLabelParams)
    ui.typeInLabel:setTextSize(textSize)
    ui.typeInLabel:setText("TYPE-IN CONTROLS")
    ui.typeInLabel:setGravity(GRAVITY_CENTER)
    ui.typeInLabel:setTextColor(Color:argb(255,0x33,0x33,0x33))
    ui.typeInLabel:setBackgroundColor(bgGrey)
    ui.advancedPage:addView(ui.typeInLabel)
  end
  
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
  
  if(ui.labelSizeLabel == nil) then
    ui.labelSizeLabel = fnew(TextView,context)
    ui.labelSizeLabel:setLayoutParams(fillparams)
    ui.labelSizeLabel:setGravity(GRAVITY_CENTER)
    ui.labelSizeLabel:setText("Label Font Size")
    ui.labelSizeLabel:setTextSize(textSizeSmall)
    ui.labelRowThree:addView(ui.labelSizeLabel)
  end
  
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
  tmp.labelSize = tonumber(ui.labelSizeEdit:getText():toString())
  tmp.height = tonumber(ui.heightEdit:getText():toString())
  tmp.width = tonumber(ui.widthEdit:getText():toString())
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
    end
    colorpickerdialog = luajava.newInstance("com.resurrection.blowtorch2.lib.button.ColorPickerDialog",context,colorPickerDoneListener,color)
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
      ui.flipColorPicker:setBackgroundColor(color)
      ui.flipColorPicker:invalidate()
      ui.flipColor = color
    elseif(selectedColorField == "normal") then
      ui.normalColorPicker:setBackgroundColor(color)
      ui.normalColorPicker:invalidate()
      ui.normalColor = color;
    elseif(selectedColorField == "pressed") then
      ui.pressedColorPicker:setBackgroundColor(color)
      ui.pressedColorPicker:invalidate()
      ui.pressedColor = color
    elseif(selectedColorField == "label") then
      ui.normalLabelColorPicker:setBackgroundColor(color)
      ui.normalLabelColorPicker:invalidate()
      ui.normalLabelColor = color
    elseif(selectedColorField == "flipLabel") then
      ui.flipLabelColorPicker:setBackgroundColor(color)
      ui.flipLabelColorPicker:invalidate()
      ui.flipLabelColor = color
    end
  end
})

showSetEditorControls = function()
  ui.buttonTargetSetRow:setVisibility(View.GONE)
  ui.buttonNameRow:setVisibility(View.VISIBLE)
  ui.controlRowTwo:setVisibility(View.GONE)
  ui.labelRowFour:setVisibility(View.GONE)
end

showButtonEditorControls = function()
  ui.controlRowTwo:setVisibility(View.VISIBLE)
  ui.labelRowFour:setVisibility(View.VISIBLE)
  ui.buttonNameRow:setVisibility(View.VISIBLE)
  ui.buttonTargetSetRow:setVisibility(View.VISIBLE)
end

