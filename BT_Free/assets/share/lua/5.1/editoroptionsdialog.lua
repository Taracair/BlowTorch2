local LinearLayoutParams = _G["LinearLayoutParams"]
local LinearLayout = _G["LinearLayout"]
local ScrollView = _G["ScrollView"]
local density = _G["density"]
local luajava = _G["luajava"]
local Button = _G["Button"]
local Gravity = _G["Gravity"]
local pairs = _G["pairs"]
local math = _G["math"]
local require = _G["require"]
local Validator = _G["Validator"]
local Validator_Number_Not_Blank = _G["Validator_Number_Not_Blank"];
local Validator_Number_Or_Blank = _G["Validator_Number_Or_Blank"];
local Validator_Not_Blank = _G["Validator_Not_Blank"]
local luajava = _G["luajava"]
local TextView = _G["TextView"]
local FILL_PARENT = _G["FILL_PARENT"]
local WRAP_CONTENT = _G["WRAP_CONTENT"]
local GRAVITY_CENTER = _G["GRAVITY_CENTER"]
local Color = _G["Color"]
--needed for advaced page of the set propertieseditor dialog
local View = _G["View"]
local Configuration = _G["Configuration"]
local EditText = _G["EditText"]
local TYPE_CLASS_NUMBER = _G["TYPE_CLASS_NUMBER"]
local tostring = _G["tostring"]
local Note = _G["Note"]
local string = _G["string"]
local tonumber = _G["tonumber"]
local serialize = _G["serialize"]
-- module(...) sandboxes globals, so anything used here has to be pulled in.
local table = _G["table"]
local ipairs = _G["ipairs"]
module(...)

local context

--text size values (REFACTOR!)
local textSizeBig = (18) -- sp value
local textSize = (14)  
local textSizeSmall = (10) 

--callbacks to be set from the parent window (the main button manager/editor)
local setGridSnap
local setGridXSpacing
local setGridYSpacing
local setGridOpacity
local setGridSnapTest
local setAdvancedProperties
local setShowGestureHints
local setShowSwipePreview
local applySize
local tidyLayout
local setChromeGestures
local fitGrid
local editorDone
local editorCancel
setEditorDoneCallback = function(c) editorDone = c end
setEditorCancelCallback = function(c) editorCancel = c end

--setter methods for the above callbacks
setGridSnapCallback = function(c) setGridSnap = c end
setGridXSpacingCallback = function(c) setGridXSpacing = c end
setGridYSpacingCallback = function(c) setGridYSpacing = c end
setGridOpacityCallback = function(c) setGridOpacity = c end
setGridSnapTestCallback = function(c) setGridSnapTest = c end
setAdvancedPropertiesCallback = function(c) setAdvancedProperties = c end
setShowGestureHintsCallback = function(c) setShowGestureHints = c end
setShowSwipePreviewCallback = function(c) setShowSwipePreview = c end
setApplySizeCallback = function(c) applySize = c end
setTidyLayoutCallback = function(c) tidyLayout = c end
setChromeGesturesCallback = function(c) setChromeGestures = c end
setFitGridCallback = function(c) fitGrid = c end
--end callback handling variables

--local vairables to keep track of widget values
local gridSnap
local gridX
local gridY
local gridOpacity
local gridIntersectionTest
local showGestureHints
local showSwipePreview
local setEditorValues
local editorValues

--event handlers
local gridSnapCheckChangeListener
local gridXSeekBarChangeListener
local gridYSeekBarChangeListener
local gridOpacitySeekBarChangeListener
local gridIntersectionTestRadioChangedListener
local showGestureHintsCheckChangeListener
local showSwipePreviewCheckChangeListener
local applySizeListener
local tidyLayoutListener
local doneListener
local cancelListener
local setDefaultsEditorListener

--ui widgets
local dialog
local xSeekBarLabel
local ySeekBarLabel
local opacitySeekBarLabel
-- Set while the dialog is built; the click listeners live at module scope and
-- cannot reach showDialog's locals.
local chromeFields
local chromeGesturesListener
local fitSquareListener
local fitStretchListener
local gridFieldListener
local gridXField
local gridYField
local sizeWidthField
local sizeHeightField
local tidyColumnsField

-- Chrome gesture bindings arrive as target.gesture=command lines, the format
-- ChromeGestures.java reads. Kept as one setting because nineteen separate
-- options would be unreadable in the settings file.
function parseChromeGestures(stored)
  local out = {}
  if stored == nil or stored == "" then
    return out
  end
  for line in string.gmatch(stored, "[^\n]+") do
    local key, cmd = string.match(line, "^%s*([%w_]+%.[%w_]+)%s*=%s*(.-)%s*$")
    if key ~= nil and cmd ~= nil and cmd ~= "" then
      out[key] = cmd
    end
  end
  return out
end

function addChromeField(row, labelText, value)
  local label = luajava.newInstance("android.widget.TextView", context)
  label:setText(labelText)
  label:setTextSize(textSizeSmall)
  label:setGravity(Gravity.RIGHT)
  label:setLayoutParams(luajava.new(LinearLayoutParams,
      math.floor(34 * density), WRAP_CONTENT))
  local edit = luajava.newInstance("android.widget.EditText", context)
  edit:setTextSize(textSizeSmall)
  edit:setSingleLine(true)
  edit:setText(value ~= nil and value or "")
  edit:setLayoutParams(luajava.new(LinearLayoutParams,
      LinearLayoutParams.FILL_PARENT, WRAP_CONTENT, 1))
  row:addView(label)
  row:addView(edit)
  return edit
end

function init(pContext)
  context = pContext
end

function showDialog(initialValues)

  gridX = initialValues.gridX
  gridY = initialValues.gridY
  gridOpacity = initialValues.gridOpacity
  gridIntersectionTest = initialValues.gridIntersectionTest
  gridSnap = initialValues.gridSnap
  showGestureHints = initialValues.showGestureHints ~= false
  showSwipePreview = initialValues.showSwipePreview ~= false
  
  editorValues = initialValues

  local ll = luajava.newInstance("android.widget.LinearLayout",context)
  ll:setOrientation(1)
  -- Fill the dialog rather than sitting at a fixed 350dp inside it, which left
  -- every row hugging the left edge with dead space down the right. The button
  -- editor already sizes itself this way.
  local utils = require("buttonutils")
  local dialogWidth = utils.getDialogDimensions(context)
  local llparams = luajava.new(LinearLayoutParams,
      LinearLayoutParams.FILL_PARENT,LinearLayoutParams.WRAP_CONTENT)
  ll:setLayoutParams(llparams)
  ll:setPadding(math.floor(10*density),0,math.floor(10*density),0)

  local scroller = luajava.new(ScrollView,context)
  -- Its own params object: sharing one between two views in different parents
  -- is how the widths ended up disagreeing.
  scroller:setLayoutParams(luajava.new(LinearLayoutParams,
      dialogWidth,LinearLayoutParams.WRAP_CONTENT,1))
  
  local fillparams = luajava.new(LinearLayoutParams,LinearLayoutParams.FILL_PARENT,LinearLayoutParams.WRAP_CONTENT,1)
  local wrapparams = luajava.new(LinearLayoutParams,LinearLayoutParams.WRAP_CONTENT,LinearLayoutParams.WRAP_CONTENT,1)
  wrapparams:setMargins(0,15,0,0)
  local wrapparamsNoWeight = luajava.new(LinearLayoutParams,LinearLayoutParams.WRAP_CONTENT,LinearLayoutParams.WRAP_CONTENT)
  
  --lp = luajava.newInstance("android.view.ViewGroup$LayoutParams",-1,-2)

  local cb = luajava.newInstance("android.widget.CheckBox",context)
  cb:setChecked(gridSnap)
  cb:setText("Snap To Grid")
  cb:setTextSize(textSizeSmall)
  cb:setOnCheckedChangeListener(gridSnapCheckChangeListener)
  cb:setLayoutParams(fillparams)
  
  local subrow = luajava.new(LinearLayout,context)
  subrow:setLayoutParams(fillparams)
  
  --gridSizeRow = luajava.newInstance("android.widget.LinearLayout",context)
  --gridSizeRow:setOrientation(1)
  
  --Note("seekbar creation")
  local xSeekBar = luajava.newInstance("android.widget.SeekBar",context)
  xSeekBar:setOnSeekBarChangeListener(gridXSeekBarChangeListener)
  xSeekBar:setLayoutParams(fillparams)
  xSeekBarLabel = luajava.newInstance("android.widget.TextView",context)
  xSeekBarLabel:setLayoutParams(wrapparams)
  xSeekBarLabel:setTextSize(textSizeSmall)
  xSeekBarLabel:setText("Grid X Spacing: "..gridX)
  xSeekBar:setProgress((gridX)-32)
  
  local ySeekBar = luajava.newInstance("android.widget.SeekBar",context)
  ySeekBar:setOnSeekBarChangeListener(gridYSeekBarChangeListener)
  ySeekBar:setLayoutParams(fillparams)
  ySeekBarLabel = luajava.newInstance("android.widget.TextView",context)
  ySeekBarLabel:setLayoutParams(wrapparams)
  ySeekBarLabel:setTextSize(textSizeSmall)
  ySeekBarLabel:setText("Grid Y Spacing: "..gridY)
  ySeekBar:setProgress((gridY)-32)
  
  local opacitySeekBar = luajava.newInstance("android.widget.SeekBar",context)
  
  opacitySeekBar:setLayoutParams(fillparams)
  opacitySeekBar:setMax(255)
  ----Note("settings opacity slider to:"..manageropacity)
  opacitySeekBar:setProgress(gridOpacity)
  opacitySeekBar:setOnSeekBarChangeListener(gridOpacitySeekBarChangeListener)
  
  opacitySeekBarLabel = luajava.newInstance("android.widget.TextView",context)
  opacitySeekBarLabel:setLayoutParams(wrapparams)
  opacitySeekBarLabel:setTextSize(textSizeSmall)
  opacitySeekBarLabel:setText("Grid Opacity: "..gridOpacity)
  
  local rg_static = luajava.bindClass("android.widget.RadioGroup")
  
  --local subrow2 = luajava.new(LinearLayout,context)
  --subrow2:setLayoutParams(fillparams)
  
  local rg = luajava.newInstance("android.widget.RadioGroup",context)
  local rgLayoutParams = luajava.newInstance("android.widget.LinearLayout$LayoutParams",-2,-2)
  rg:setLayoutParams(rgLayoutParams)
  rg:setOnCheckedChangeListener(gridIntersectionTestRadioChangedListener)
  rg:setOrientation(0)
  
  local contain = luajava.newInstance("android.widget.RadioButton",context)
  contain:setText("Contains")
  contain:setTextSize(textSizeSmall)
  contain:setId(1)
  
  local intersect = luajava.newInstance("android.widget.RadioButton",context)
  intersect:setText("Intersect")
  intersect:setTextSize(textSizeSmall)
  intersect:setId(0)
  
  
  
  local rg_lp = luajava.bindClass("android.widget.RadioGroup$LayoutParams")
  
  local rg_lp_gen = luajava.new(rg_lp,fillparams)
  local rg_lp_gen2 = luajava.new(rg_lp,fillparams)
  rg_lp_gen2:setMargins(25,0,0,0)
  
  rg:addView(intersect,0,rg_lp_gen)
  rg:addView(contain,1,rg_lp_gen2)
  rg:check(gridIntersectionTest)
  
  local selectionTextLabel = luajava.newInstance("android.widget.TextView",context)
  selectionTextLabel:setLayoutParams(wrapparams)
  selectionTextLabel:setTextSize(textSizeSmall)
  selectionTextLabel:setText("Drag rectangle selection test:")
  
  --subrow2:addView(selectionTextLabel)
  --subrow2:addView(rg)
  
  local setSettingsButton = luajava.new(Button,context)
  setSettingsButton:setLayoutParams(fillparams)
  setSettingsButton:setText("Edit Defaults")
  setSettingsButton:setOnClickListener(setDefaultsEditorListener)
  --Note("adding views")
  
  subrow:addView(cb)
  subrow:addView(setSettingsButton)
  ll:addView(subrow)

  local hintsCb = luajava.newInstance("android.widget.CheckBox",context)
  hintsCb:setChecked(showGestureHints)
  hintsCb:setText("Show swipe letters, corner arrows, Hold and accordion badges on buttons")
  hintsCb:setTextSize(textSizeSmall)
  hintsCb:setOnCheckedChangeListener(showGestureHintsCheckChangeListener)
  hintsCb:setLayoutParams(fillparams)
  ll:addView(hintsCb)

  local swipePreviewCb = luajava.newInstance("android.widget.CheckBox",context)
  swipePreviewCb:setChecked(showSwipePreview)
  swipePreviewCb:setText("Show a live arrow while swiping (which direction you are aiming at)")
  swipePreviewCb:setTextSize(textSizeSmall)
  swipePreviewCb:setOnCheckedChangeListener(showSwipePreviewCheckChangeListener)
  swipePreviewCb:setLayoutParams(fillparams)
  ll:addView(swipePreviewCb)

  -- Section headers: the dialog was one flat run of checkboxes, sliders and
  -- radio buttons with no grouping, which made it hard to scan.
  local function addSectionHeader(text)
    local header = luajava.newInstance("android.widget.TextView", context)
    header:setText(text)
    header:setTextSize(textSize)
    header:setTextColor(Color:argb(255, 0x88, 0xCC, 0xFF))
    header:setPadding(math.floor(6 * density), math.floor(14 * density),
        math.floor(6 * density), math.floor(4 * density))
    header:setLayoutParams(fillparams)
    ll:addView(header)
  end

  local function addHint(text)
    local hint = luajava.newInstance("android.widget.TextView", context)
    hint:setText(text)
    hint:setTextSize(textSizeSmall)
    hint:setPadding(math.floor(6 * density), 0, math.floor(6 * density),
        math.floor(4 * density))
    hint:setLayoutParams(fillparams)
    ll:addView(hint)
  end

  local function addNumberField(row, labelText, value, widthDp)
    local label = luajava.newInstance("android.widget.TextView", context)
    label:setText(labelText)
    label:setTextSize(textSizeSmall)
    label:setGravity(Gravity.RIGHT)
    label:setLayoutParams(luajava.new(LinearLayoutParams,
        math.floor(58 * density), WRAP_CONTENT))
    local edit = luajava.newInstance("android.widget.EditText", context)
    edit:setTextSize(textSize)
    edit:setInputType(TYPE_CLASS_NUMBER)
    edit:setText(tostring(value))
    edit:setLayoutParams(luajava.new(LinearLayoutParams,
        math.floor(widthDp * density), WRAP_CONTENT))
    row:addView(label)
    row:addView(edit)
    return edit
  end

  addSectionHeader("Grid")
  ll:addView(xSeekBarLabel)
  ll:addView(xSeekBar)
  ll:addView(ySeekBarLabel)
  ll:addView(ySeekBar)
  ll:addView(opacitySeekBarLabel)
  ll:addView(opacitySeekBar)
  ll:addView(selectionTextLabel)
  ll:addView(rg)

  -- Exact spacing, next to the sliders rather than instead of them: the sliders
  -- are quick, typing is precise.
  addHint("Drag the sliders for a rough size, or type an exact one here — the sliders keep these boxes up to date.")
  local gridExactRow = luajava.newInstance("android.widget.LinearLayout", context)
  gridExactRow:setLayoutParams(fillparams)
  gridXField = addNumberField(gridExactRow, "X:", gridX, 50)
  gridYField = addNumberField(gridExactRow, "Y:", gridY, 50)
  local gridSetButton = luajava.new(Button, context)
  gridSetButton:setText("Set")
  gridSetButton:setTextSize(textSizeSmall)
  gridSetButton:setLayoutParams(fillparams)
  gridSetButton:setOnClickListener(gridFieldListener)
  gridExactRow:addView(gridSetButton)
  ll:addView(gridExactRow)

  local fitRow = luajava.newInstance("android.widget.LinearLayout", context)
  fitRow:setLayoutParams(fillparams)
  local fitSquare = luajava.new(Button, context)
  fitSquare:setText("Fit — square")
  fitSquare:setTextSize(textSizeSmall)
  fitSquare:setLayoutParams(fillparams)
  fitSquare:setOnClickListener(fitSquareListener)
  local fitStretch = luajava.new(Button, context)
  fitStretch:setText("Fit — fill window")
  fitStretch:setTextSize(textSizeSmall)
  fitStretch:setLayoutParams(fillparams)
  fitStretch:setOnClickListener(fitStretchListener)
  fitRow:addView(fitSquare)
  fitRow:addView(fitStretch)
  ll:addView(fitRow)
  addHint("Square keeps buttons square and leaves any spare width at the right edge. Fill window uses the whole screen, so cells stop being square.")

  addSectionHeader("Arrange buttons")
  addHint("Applies to the selected buttons, or to every button when nothing is selected.")

  local sizeRow = luajava.newInstance("android.widget.LinearLayout", context)
  sizeRow:setLayoutParams(fillparams)
  local sizeWEdit = addNumberField(sizeRow, "Size  W:", initialValues.width or 42, 54)
  local sizeHEdit = addNumberField(sizeRow, "H:", initialValues.height or 42, 54)
  local applySizeButton = luajava.new(Button, context)
  applySizeButton:setText("Apply size")
  applySizeButton:setTextSize(textSizeSmall)
  applySizeButton:setLayoutParams(fillparams)
  applySizeButton:setOnClickListener(applySizeListener)
  sizeRow:addView(applySizeButton)
  ll:addView(sizeRow)

  local tidyRow = luajava.newInstance("android.widget.LinearLayout", context)
  tidyRow:setLayoutParams(fillparams)
  local tidyColsEdit = addNumberField(tidyRow, "Columns:", 0, 54)
  local tidyButton = luajava.new(Button, context)
  tidyButton:setText("Line up on grid")
  tidyButton:setTextSize(textSizeSmall)
  tidyButton:setLayoutParams(fillparams)
  tidyButton:setOnClickListener(tidyLayoutListener)
  tidyRow:addView(tidyButton)
  ll:addView(tidyRow)
  addHint("Columns 0 picks a square-ish shape. Buttons keep their reading order and top-left corner; spacing comes from the grid above.")

  -- The listeners live at module scope and cannot see these fields, so hand the
  -- widgets over for them to read on click.
  sizeWidthField = sizeWEdit
  sizeHeightField = sizeHEdit
  tidyColumnsField = tidyColsEdit

  addSectionHeader("Just when you thought you were out of room for gestures")
  addHint("Swipe or hold these buttons. Taps keep working; these only fire on a gesture. Dot commands suit this well. The input bar is left out — dragging there selects text.")

  chromeFields = {}
  local chromeStored = parseChromeGestures(initialValues.chromeGestures)
  local chromeGroups = {
    { key = "edit",     label = "Edit button",   hold = true },
    { key = "send",     label = "Send button",   hold = true },
    { key = "overflow", label = "Overflow  ⋮",   hold = false },
  }
  local chromeGestureRows = {
    { g = "up",    label = "↑" },
    { g = "down",  label = "↓" },
    { g = "left",  label = "←" },
    { g = "right", label = "→" },
  }
  for i = 1, #chromeGroups do
    local group = chromeGroups[i]
    local groupLabel = luajava.newInstance("android.widget.TextView", context)
    groupLabel:setText(group.label)
    groupLabel:setTextSize(textSizeSmall)
    groupLabel:setPadding(math.floor(6 * density), math.floor(8 * density), 0, 0)
    groupLabel:setLayoutParams(fillparams)
    ll:addView(groupLabel)
    local rows = chromeGestureRows
    for r = 1, #rows do
      local row = luajava.newInstance("android.widget.LinearLayout", context)
      row:setLayoutParams(fillparams)
      local edit = addChromeField(row, rows[r].label,
          chromeStored[group.key .. "." .. rows[r].g])
      chromeFields[#chromeFields + 1] =
          { key = group.key .. "." .. rows[r].g, edit = edit }
      ll:addView(row)
    end
    if group.hold then
      local row = luajava.newInstance("android.widget.LinearLayout", context)
      row:setLayoutParams(fillparams)
      local edit = addChromeField(row, "hold", chromeStored[group.key .. ".hold"])
      chromeFields[#chromeFields + 1] = { key = group.key .. ".hold", edit = edit }
      ll:addView(row)
    else
      local note = luajava.newInstance("android.widget.TextView", context)
      note:setText("   hold stays as Edit buttons")
      note:setTextSize(textSizeSmall)
      note:setLayoutParams(fillparams)
      ll:addView(note)
    end
  end

  local chromeApply = luajava.new(Button, context)
  chromeApply:setText("Save chrome gestures")
  chromeApply:setTextSize(textSizeSmall)
  chromeApply:setLayoutParams(fillparams)
  chromeApply:setOnClickListener(chromeGesturesListener)
  ll:addView(chromeApply)
  
  local boptHolder = luajava.new(LinearLayout,context)
  boptHolder:setLayoutParams(fillparams)
  boptHolder:setOrientation(0)
  boptHolder:setGravity(Gravity.CENTER)
  local boptDoneButton = luajava.newInstance("android.widget.Button",context)
  boptDoneButton:setText("Done")
  boptDoneButton:setLayoutParams(wrapparamsNoWeight)
  boptDoneButton:setOnClickListener(doneListener)
  local boptCancelButton = luajava.newInstance("android.widget.Button",context)
  boptCancelButton:setText("Cancel")
  boptCancelButton:setLayoutParams(wrapparamsNoWeight)
  boptCancelButton:setOnClickListener(cancelListener)
  boptHolder:addView(boptCancelButton)
  boptHolder:addView(boptDoneButton)
  
  ll:addView(boptHolder)
  
  scroller:addView(ll)
  --ll:addView(rg)
  
  --ll:addView(setSettingsButton)
  --ll:addView(subrow)
  --set up the show editor settings button.
  --Note("builder alert creation")
  --builder = luajava.newInstance("android.app.AlertDialog$Builder",context)
  dialog = luajava.newInstance("com.resurrection.blowtorch2.lib.window.LuaDialog",context,scroller,false,nil)
  dialog:show()
  --local hiddenview = luajava.new(TextView,view:getContext())
  --hiddenview:setVisibility(View.GONE)
  --builder:setCustomTitle(hiddenview)
  --builder:setTitle("")
  --builder:setMessage("")
  --builder:setView(ll)
  --alert = builder:create()
  --local titleview = alert:findViewById(android_R_id.title)
  --titleview:setVisibility(View.GONE)
  --alert:show()
end

gridSnapCheckChangeListener = luajava.createProxy("android.widget.CompoundButton$OnCheckedChangeListener",{
  onCheckedChanged = function(v,isChecked)
    gridSnap = isChecked
    if(setGridSnap ~= nil) then
      setGridSnap(isChecked)
    end
  end
})

showGestureHintsCheckChangeListener = luajava.createProxy("android.widget.CompoundButton$OnCheckedChangeListener",{
  onCheckedChanged = function(v,isChecked)
    showGestureHints = isChecked
    if(setShowGestureHints ~= nil) then
      setShowGestureHints(isChecked)
    end
  end
})

showSwipePreviewCheckChangeListener = luajava.createProxy("android.widget.CompoundButton$OnCheckedChangeListener",{
  onCheckedChanged = function(v,isChecked)
    showSwipePreview = isChecked
    if(setShowSwipePreview ~= nil) then
      setShowSwipePreview(isChecked)
    end
  end
})

applySizeListener = luajava.createProxy("android.view.View$OnClickListener",{
  onClick = function(v)
    if applySize == nil or sizeWidthField == nil or sizeHeightField == nil then
      return
    end
    local w = tonumber(sizeWidthField:getText():toString())
    local h = tonumber(sizeHeightField:getText():toString())
    if w == nil or h == nil then
      Note("\nEnter a width and a height first.\n")
      return
    end
    if w < 16 or h < 16 then
      Note("\nButtons smaller than 16dp are hard to hit; ignoring.\n")
      return
    end
    applySize(w, h)
  end
})

fitSquareListener = luajava.createProxy("android.view.View$OnClickListener",{
  onClick = function(v)
    if fitGrid ~= nil then fitGrid(true) end
  end
})

fitStretchListener = luajava.createProxy("android.view.View$OnClickListener",{
  onClick = function(v)
    if fitGrid ~= nil then fitGrid(false) end
  end
})

-- Typing an exact spacing. The sliders stay; they are quick but cannot hit a
-- specific number, which matters when lining a pad up to a size.
gridFieldListener = luajava.createProxy("android.view.View$OnClickListener",{
  onClick = function(v)
    if gridXField == nil or gridYField == nil then
      return
    end
    local gx = tonumber(gridXField:getText():toString())
    local gy = tonumber(gridYField:getText():toString())
    if gx == nil or gy == nil or gx < 8 or gy < 8 then
      Note("\nGrid spacing needs two numbers of 8 or more.\n")
      return
    end
    if setGridXSpacing ~= nil then setGridXSpacing(gx) end
    if setGridYSpacing ~= nil then setGridYSpacing(gy) end
    if xSeekBarLabel ~= nil then xSeekBarLabel:setText("Grid X Spacing: "..gx) end
    if ySeekBarLabel ~= nil then ySeekBarLabel:setText("Grid Y Spacing: "..gy) end
  end
})

chromeGesturesListener = luajava.createProxy("android.view.View$OnClickListener",{
  onClick = function(v)
    if chromeFields == nil or setChromeGestures == nil then
      return
    end
    local parts = {}
    for i = 1, #chromeFields do
      local entry = chromeFields[i]
      local cmd = entry.edit:getText():toString()
      cmd = string.gsub(cmd, "^%s*(.-)%s*$", "%1")
      if cmd ~= "" then
        parts[#parts + 1] = entry.key .. "=" .. cmd
      end
    end
    setChromeGestures(table.concat(parts, "\n"))
    Note("\nChrome gestures saved (" .. #parts .. " bound).\n")
  end
})

tidyLayoutListener = luajava.createProxy("android.view.View$OnClickListener",{
  onClick = function(v)
    if tidyLayout == nil then
      return
    end
    local cols = 0
    if tidyColumnsField ~= nil then
      cols = tonumber(tidyColumnsField:getText():toString()) or 0
    end
    tidyLayout(cols)
  end
})

gridXSeekBarChangeListener = luajava.createProxy("android.widget.SeekBar$OnSeekBarChangeListener",{
  onProgressChanged = function(v,progress,state)
    gridX = (progress + 32)
    xSeekBarLabel:setText("Grid X Spacing: "..gridX)
    if gridXField ~= nil then gridXField:setText(tostring(gridX)) end
    if(setGridXSpacing ~= nil) then
      setGridXSpacing(gridX)
    end
  end
})

gridYSeekBarChangeListener = luajava.createProxy("android.widget.SeekBar$OnSeekBarChangeListener",{
  onProgressChanged = function(v,progress,state)
    gridY = (progress + 32)
    ySeekBarLabel:setText("Grid Y Spacing: "..gridY)
    if gridYField ~= nil then gridYField:setText(tostring(gridY)) end
    if(setGridYSpacing ~= nil) then
      setGridYSpacing(gridY)
    end
  end
})

gridOpacitySeekBarChangeListener = luajava.createProxy("android.widget.SeekBar$OnSeekBarChangeListener",{
  onProgressChanged = function(v,progress,state)
    gridOpacity = progress
    local opacitypct = math.floor((gridOpacity / 255)*100)
    opacitySeekBarLabel:setText("Grid Opacity: "..opacitypct.."%")
    if(setGridOpacity ~= nil) then
      setGridOpacity(progress)
    end
  end 
})

gridIntersectionTestRadioChangedListener = luajava.createProxy("android.widget.RadioGroup$OnCheckedChangeListener",{
  onCheckedChanged = function(group,id)
    gridIntersectionTest = id
    if(setGridSnapTest ~= nil) then
      setGridSnapTest(gridIntersectionTest)
    end
  end
})

doneListener = luajava.createProxy("android.view.View$OnClickListener",{
  onClick = function(v)
    --collect editor values
    if(setEditorValues ~= nil) then
      for i,k in pairs(setEditorValues) do
        editorValues[i] = k
      end
    end
    if(editorDone ~= nil) then
      editorDone(editorValues)
    end
  
    dialog:dismiss()
  end
})

cancelListener = luajava.createProxy("android.view.View$OnClickListener",{
  onClick = function(v)
    if(editorCancel ~= nil) then
      editorCancel()
    end
    dialog:dismiss()
  end
})

setDefaultsEditorListener = luajava.createProxy("android.view.View$OnClickListener",{
  onClick = function(v)
    local callback = function(values)
      --this is called when the defaults editor is done.
      setEditorValues = values;
    end
    
    local setEditor = require("setpropertieseditor")
    setEditor.init(context)
    setEditor.setEditorDoneCallback(callback)
    setEditor.showDialog(editorValues)
  end
})

