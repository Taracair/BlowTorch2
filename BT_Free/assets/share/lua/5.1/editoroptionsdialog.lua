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
local Context = _G["Context"]
local R_drawable = _G["R_drawable"]
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

-- Grid spacing range, in dp. The sliders used to run on the default 0..100 max
-- with 32 added, so they could not reach a spacing the number field happily
-- accepted; both ends now come from here and typed values clamp to it.
local GRID_MIN = 8
local GRID_MAX = 160

--ui widgets
local dialog
local xSeekBarLabel
local ySeekBarLabel
local opacitySeekBarLabel
local xSeekBar
local ySeekBar
local opacitySeekBar
-- Set while a slider is being moved from code. setProgress() calls
-- onProgressChanged synchronously, which would push the clamped slider value
-- straight back into the grid and undo whatever we were displaying.
local suppressGridSync = false
-- Declared up here on purpose. module(...) makes every free name a lookup in
-- the module table, so a helper defined further down the file reads as nil
-- inside showDialog rather than as the local it looks like.
local clampGrid
local setSliderQuietly
local commitGridFields
local gridFieldFocusListener
local gridFieldActionListener
local saveChromeGestures
local serialiseChromeFields
-- What the gesture boxes held when the dialog opened, in the same form they
-- serialise to, so "did the user change anything" is a string compare and not
-- a guess about field ordering.
local chromeBaseline
-- Set while the dialog is built; the click listeners live at module scope and
-- cannot reach showDialog's locals.
local chromeFields
local fitSquareListener
local fitStretchListener
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

-- Kept out of showDialog so Lua 5.1's 60-upvalue limit is not exceeded.
local function presentEditorOptionsSheet(scroller, footer, screenH)
  -- Header/footer must stay weight 0. Weight 1 on them (via shared fillparams)
  -- made fullscreen share leftover height equally with the scroller, so the
  -- title and Done/Cancel floated in the middle with black bands above/below.
  local chromeParams = luajava.new(LinearLayoutParams,
      LinearLayoutParams.FILL_PARENT, LinearLayoutParams.WRAP_CONTENT, 0)
  local rowParams = luajava.new(LinearLayoutParams,
      LinearLayoutParams.FILL_PARENT, LinearLayoutParams.WRAP_CONTENT)
  -- Scroll area only; header and Done/Cancel sit below the ~37% band.
  local maxPanelScrollH = math.floor(screenH * 0.37)

  local root = luajava.newInstance("android.widget.LinearLayout", context)
  root:setOrientation(LinearLayout.VERTICAL)
  root:setLayoutParams(luajava.new(LinearLayoutParams,
      LinearLayoutParams.FILL_PARENT, LinearLayoutParams.FILL_PARENT))

  local spacer = luajava.newInstance("android.view.View", context)
  spacer:setLayoutParams(luajava.new(LinearLayoutParams,
      LinearLayoutParams.FILL_PARENT, 0, 1))
  spacer:setClickable(true)

  local panel = luajava.newInstance("android.widget.LinearLayout", context)
  panel:setOrientation(LinearLayout.VERTICAL)
  panel:setLayoutParams(luajava.new(LinearLayoutParams,
      LinearLayoutParams.FILL_PARENT, LinearLayoutParams.WRAP_CONTENT, 0))
  panel:setBackgroundResource(R_drawable.dialog_window_crawler1)

  local header = luajava.newInstance("android.widget.LinearLayout", context)
  header:setOrientation(LinearLayout.VERTICAL)
  header:setLayoutParams(chromeParams)
  header:setPadding(math.floor(10 * density), math.floor(8 * density),
      math.floor(10 * density), math.floor(4 * density))

  local headerTitle = luajava.newInstance("android.widget.TextView", context)
  headerTitle:setText("Button set options")
  headerTitle:setTextSize(textSize)
  headerTitle:setLayoutParams(rowParams)

  local modeRow = luajava.newInstance("android.widget.LinearLayout", context)
  modeRow:setOrientation(LinearLayout.HORIZONTAL)
  modeRow:setLayoutParams(rowParams)
  modeRow:setGravity(Gravity.CENTER_VERTICAL)

  local function makeModeButton(label)
    local btn = luajava.new(Button, context)
    btn:setText(label)
    btn:setTextSize(textSizeSmall)
    btn:setLayoutParams(luajava.new(LinearLayoutParams,
        0, LinearLayoutParams.WRAP_CONTENT, 1))
    modeRow:addView(btn)
    return btn
  end

  local panelModeButton = makeModeButton("Panel")
  local fullscreenModeButton = makeModeButton("Fullscreen")
  local hideModeButton = makeModeButton("Hide")

  local panelMode = "panel"
  local sheetDialog = nil
  local spacerAttached = true

  local function attachSpacer()
    if not spacerAttached then
      root:addView(spacer, 0)
      spacerAttached = true
    end
  end

  local function detachSpacer()
    if spacerAttached then
      root:removeView(spacer)
      spacerAttached = false
    end
  end

  local function setScrollContentHeight(matchParent)
    local content = scroller:getChildAt(0)
    if content == nil then
      return
    end
    local h = LinearLayoutParams.WRAP_CONTENT
    if matchParent then
      h = LinearLayoutParams.FILL_PARENT
    end
    content:setLayoutParams(luajava.new(LinearLayoutParams,
        LinearLayoutParams.FILL_PARENT, h))
  end

  local function applyPanelMode()
    if panelMode == "fullscreen" then
      if sheetDialog ~= nil then
        sheetDialog:setPresentationOverGrid(false)
      end
      detachSpacer()
      panel:setBackgroundResource(0)
      scroller:setFillViewport(true)
      setScrollContentHeight(true)
      scroller:setVisibility(View.VISIBLE)
      header:setLayoutParams(chromeParams)
      footer:setLayoutParams(chromeParams)
      panel:setLayoutParams(luajava.new(LinearLayoutParams,
          LinearLayoutParams.FILL_PARENT, LinearLayoutParams.FILL_PARENT, 0))
      scroller:setLayoutParams(luajava.new(LinearLayoutParams,
          LinearLayoutParams.FILL_PARENT, 0, 1))
    elseif panelMode == "hidden" then
      if sheetDialog ~= nil then
        sheetDialog:setPresentationOverGrid(true)
      end
      attachSpacer()
      spacer:setVisibility(View.VISIBLE)
      panel:setBackgroundResource(R_drawable.dialog_window_crawler1)
      scroller:setFillViewport(false)
      setScrollContentHeight(false)
      scroller:setVisibility(View.GONE)
      header:setLayoutParams(chromeParams)
      footer:setLayoutParams(chromeParams)
      spacer:setLayoutParams(luajava.new(LinearLayoutParams,
          LinearLayoutParams.FILL_PARENT, 0, 1))
      scroller:setLayoutParams(luajava.new(LinearLayoutParams,
          LinearLayoutParams.FILL_PARENT, maxPanelScrollH, 0))
      panel:setLayoutParams(luajava.new(LinearLayoutParams,
          LinearLayoutParams.FILL_PARENT, LinearLayoutParams.WRAP_CONTENT, 0))
    else
      if sheetDialog ~= nil then
        sheetDialog:setPresentationOverGrid(true)
      end
      attachSpacer()
      spacer:setVisibility(View.VISIBLE)
      panel:setBackgroundResource(R_drawable.dialog_window_crawler1)
      scroller:setFillViewport(false)
      setScrollContentHeight(false)
      scroller:setVisibility(View.VISIBLE)
      header:setLayoutParams(chromeParams)
      footer:setLayoutParams(chromeParams)
      spacer:setLayoutParams(luajava.new(LinearLayoutParams,
          LinearLayoutParams.FILL_PARENT, 0, 1))
      scroller:setLayoutParams(luajava.new(LinearLayoutParams,
          LinearLayoutParams.FILL_PARENT, maxPanelScrollH, 0))
      panel:setLayoutParams(luajava.new(LinearLayoutParams,
          LinearLayoutParams.FILL_PARENT, LinearLayoutParams.WRAP_CONTENT, 0))
    end
    root:requestLayout()
    panelModeButton:setEnabled(panelMode ~= "panel")
    fullscreenModeButton:setEnabled(panelMode ~= "fullscreen")
    hideModeButton:setEnabled(panelMode ~= "hidden")
  end

  panelModeButton:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
    onClick = function()
      panelMode = "panel"
      applyPanelMode()
    end
  }))
  fullscreenModeButton:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
    onClick = function()
      panelMode = "fullscreen"
      applyPanelMode()
    end
  }))
  hideModeButton:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
    onClick = function()
      panelMode = "hidden"
      applyPanelMode()
    end
  }))
  spacer:setOnClickListener(luajava.createProxy("android.view.View$OnClickListener", {
    onClick = function()
      if panelMode == "panel" then
        panelMode = "hidden"
        applyPanelMode()
      end
    end
  }))

  header:addView(headerTitle)
  header:addView(modeRow)
  footer:setLayoutParams(chromeParams)
  panel:addView(header)
  panel:addView(scroller)
  panel:addView(footer)
  root:addView(spacer)
  root:addView(panel)

  local LuaDialog = luajava.bindClass("com.resurrection.blowtorch2.lib.window.LuaDialog")
  sheetDialog = luajava.new(LuaDialog, context, root, false, nil, LuaDialog.LAYOUT_BOTTOM_SHEET)
  dialog = sheetDialog
  applyPanelMode()
  sheetDialog:show()
end

function showDialog(initialValues)

  -- Module state outlives the dialog. Left over from a previous visit to the
  -- defaults editor, this would re-apply those values on a later plain Done.
  setEditorValues = nil

  -- Clamped so the slider, the box and the grid cannot open disagreeing. A
  -- spacing outside this range means two columns on a phone, and normalising it
  -- on the way in beats showing a number the controls cannot represent.
  gridX = clampGrid(math.floor(initialValues.gridX or 40))
  gridY = clampGrid(math.floor(initialValues.gridY or 40))
  gridOpacity = initialValues.gridOpacity
  gridIntersectionTest = initialValues.gridIntersectionTest
  gridSnap = initialValues.gridSnap
  showGestureHints = initialValues.showGestureHints ~= false
  showSwipePreview = initialValues.showSwipePreview ~= false
  
  editorValues = initialValues

  local ll = luajava.newInstance("android.widget.LinearLayout",context)
  ll:setOrientation(1)
  local llparams = luajava.new(LinearLayoutParams,
      LinearLayoutParams.FILL_PARENT,LinearLayoutParams.WRAP_CONTENT)
  ll:setLayoutParams(llparams)
  ll:setPadding(math.floor(10*density),0,math.floor(10*density),0)

  local wm = context:getSystemService(Context.WINDOW_SERVICE)
  local display = wm:getDefaultDisplay()
  local screenH = display:getHeight()
  local maxScrollH = math.floor(screenH * 0.37)

  local scroller = luajava.new(ScrollView,context)
  scroller:setLayoutParams(luajava.new(LinearLayoutParams,
      LinearLayoutParams.FILL_PARENT, maxScrollH))
  
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

  --Note("seekbar creation")
  xSeekBar = luajava.newInstance("android.widget.SeekBar",context)
  xSeekBar:setLayoutParams(fillparams)
  xSeekBar:setMax(GRID_MAX - GRID_MIN)
  xSeekBar:setProgress(gridX - GRID_MIN)
  xSeekBar:setOnSeekBarChangeListener(gridXSeekBarChangeListener)
  xSeekBarLabel = luajava.newInstance("android.widget.TextView",context)
  xSeekBarLabel:setLayoutParams(wrapparams)
  xSeekBarLabel:setTextSize(textSizeSmall)
  xSeekBarLabel:setText("Grid X spacing")

  ySeekBar = luajava.newInstance("android.widget.SeekBar",context)
  ySeekBar:setLayoutParams(fillparams)
  ySeekBar:setMax(GRID_MAX - GRID_MIN)
  ySeekBar:setProgress(gridY - GRID_MIN)
  ySeekBar:setOnSeekBarChangeListener(gridYSeekBarChangeListener)
  ySeekBarLabel = luajava.newInstance("android.widget.TextView",context)
  ySeekBarLabel:setLayoutParams(wrapparams)
  ySeekBarLabel:setTextSize(textSizeSmall)
  ySeekBarLabel:setText("Grid Y spacing")

  opacitySeekBar = luajava.newInstance("android.widget.SeekBar",context)

  opacitySeekBar:setLayoutParams(fillparams)
  opacitySeekBar:setMax(255)
  ----Note("settings opacity slider to:"..manageropacity)
  opacitySeekBar:setProgress(gridOpacity)
  opacitySeekBar:setOnSeekBarChangeListener(gridOpacitySeekBarChangeListener)

  opacitySeekBarLabel = luajava.newInstance("android.widget.TextView",context)
  opacitySeekBarLabel:setLayoutParams(wrapparams)
  opacitySeekBarLabel:setTextSize(textSizeSmall)
  -- The slider reports a percentage, so start with one rather than the raw
  -- 0..255 alpha the label used to open with.
  opacitySeekBarLabel:setText("Grid opacity: "..math.floor((gridOpacity / 255) * 100).."%")

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
  setSettingsButton:setText("Edit set defaults…")
  setSettingsButton:setOnClickListener(setDefaultsEditorListener)

  local hintsCb = luajava.newInstance("android.widget.CheckBox",context)
  hintsCb:setChecked(showGestureHints)
  hintsCb:setText("Show swipe letters, corner arrows, Hold and accordion badges on buttons")
  hintsCb:setTextSize(textSizeSmall)
  hintsCb:setOnCheckedChangeListener(showGestureHintsCheckChangeListener)
  hintsCb:setLayoutParams(fillparams)

  local swipePreviewCb = luajava.newInstance("android.widget.CheckBox",context)
  swipePreviewCb:setChecked(showSwipePreview)
  swipePreviewCb:setText("Show swipe direction arrow while dragging (command callouts always show)")
  swipePreviewCb:setTextSize(textSizeSmall)
  swipePreviewCb:setOnCheckedChangeListener(showSwipePreviewCheckChangeListener)
  swipePreviewCb:setLayoutParams(fillparams)

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

  -- The slider and the number are one control now. They used to be a labelled
  -- slider up here and a separate X/Y/Set row further down, which read as two
  -- unrelated ways to set the same thing. The slider is quick, the box is
  -- exact, and each keeps the other honest.
  local function addSliderRow(labelView, seekBar, value)
    local row = luajava.newInstance("android.widget.LinearLayout", context)
    row:setLayoutParams(fillparams)
    labelView:setLayoutParams(luajava.new(LinearLayoutParams,
        WRAP_CONTENT, WRAP_CONTENT, 1))
    labelView:setGravity(Gravity.CENTER_VERTICAL)
    local edit = luajava.newInstance("android.widget.EditText", context)
    edit:setTextSize(textSize)
    edit:setInputType(TYPE_CLASS_NUMBER)
    edit:setSingleLine(true)
    edit:setText(tostring(value))
    edit:setGravity(Gravity.RIGHT)
    edit:setLayoutParams(luajava.new(LinearLayoutParams,
        math.floor(56 * density), WRAP_CONTENT))
    -- Applied when the box loses focus or the keyboard's action key is
    -- pressed, so a half-typed "1" of "120" never reaches the grid.
    edit:setOnFocusChangeListener(gridFieldFocusListener)
    edit:setOnEditorActionListener(gridFieldActionListener)
    row:addView(labelView)
    row:addView(edit)
    ll:addView(row)
    ll:addView(seekBar)
    return edit
  end

  addSectionHeader("Grid")
  ll:addView(cb)
  gridXField = addSliderRow(xSeekBarLabel, xSeekBar, gridX)
  gridYField = addSliderRow(ySeekBarLabel, ySeekBar, gridY)
  addHint("Spacing in dp, between " .. GRID_MIN .. " and " .. GRID_MAX .. ". Drag for a rough size or type an exact one — the two stay in step.")
  ll:addView(opacitySeekBarLabel)
  ll:addView(opacitySeekBar)

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
  addHint("Square keeps buttons square and leaves any spare width at the right edge. Fill window uses the whole screen, so cells stop being square. Both resize the buttons to match, and the boxes above follow.")

  ll:addView(selectionTextLabel)
  ll:addView(rg)
  addHint("How a drag rectangle decides what it picks up. Intersect takes every button the rectangle touches, even a corner. Contains takes only the buttons that fit inside it whole.")

  addSectionHeader("Automatic arrange buttons")
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

  addSectionHeader("Markings on buttons")
  ll:addView(hintsCb)
  ll:addView(swipePreviewCb)

  -- Not the same ground as the tools above, though it is easy to read it that
  -- way: those change the buttons that exist, this sets what a set starts from.
  addSectionHeader("Set defaults")
  addHint("Colours, label size and the size a newly added button starts at. Apply size above changes buttons you already have; this decides what the next one looks like.")
  ll:addView(setSettingsButton)

  addSectionHeader("Additional gestures")
  addHint("Swipe or hold these buttons. Taps keep working; these only fire on a gesture. Dot commands suit this well. Done saves them.")

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

  -- "Save additional gestures" is gone: Done saves them now. A second save
  -- button next to a Done that saved everything else was a trap -- typing a
  -- gesture and pressing Done lost it. Taken from the fields rather than from
  -- initialValues so it is comparable with what Done will serialise.
  chromeBaseline = serialiseChromeFields()

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

  scroller:addView(ll)
  presentEditorOptionsSheet(scroller, boptHolder, screenH)
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

-- Move a slider from code without it answering back. setProgress() runs
-- onProgressChanged there and then, which would push the clamped slider value
-- into the grid and quietly undo a Fit that landed outside the slider's range.
setSliderQuietly = function(bar, progress)
  if bar == nil then
    return
  end
  suppressGridSync = true
  bar:setProgress(progress)
  suppressGridSync = false
end

clampGrid = function(v)
  if v < GRID_MIN then return GRID_MIN end
  if v > GRID_MAX then return GRID_MAX end
  return v
end

-- One axis of the grid. Returns the spacing that ended up in force.
local function commitAxis(field, current, bar, setter)
  local v = tonumber(field:getText():toString())
  if v == nil then
    -- Put back what the grid actually is rather than nagging: an empty box on
    -- the way to typing a number is not a mistake worth a message.
    field:setText(tostring(current))
    return current
  end
  v = math.floor(v)
  if v == current then
    -- Nothing was typed. Leave it completely alone -- clamping here would let
    -- a plain Done quietly shrink a spacing that a Fit had legitimately set
    -- above the slider's range.
    return current
  end
  v = clampGrid(v)
  setSliderQuietly(bar, v - GRID_MIN)
  if setter ~= nil then setter(v) end
  -- The typed text may have been out of range, so show what was applied.
  field:setText(tostring(v))
  return v
end

-- Make the grid, the sliders and the boxes agree. Called when a box loses focus,
-- when the keyboard action key is pressed, and on Done.
commitGridFields = function()
  if gridXField == nil or gridYField == nil then
    return
  end
  gridX = commitAxis(gridXField, gridX, xSeekBar, setGridXSpacing)
  gridY = commitAxis(gridYField, gridY, ySeekBar, setGridYSpacing)
end

gridFieldFocusListener = luajava.createProxy("android.view.View$OnFocusChangeListener",{
  onFocusChange = function(v,hasFocus)
    if not hasFocus then
      commitGridFields()
    end
  end
})

gridFieldActionListener = luajava.createProxy("android.widget.TextView$OnEditorActionListener",{
  onEditorAction = function(v,actionId,event)
    commitGridFields()
    -- false: let the keyboard do its usual thing on top of this.
    return false
  end
})

serialiseChromeFields = function()
  local parts = {}
  if chromeFields == nil then
    return "", 0
  end
  for i = 1, #chromeFields do
    local entry = chromeFields[i]
    local cmd = entry.edit:getText():toString()
    cmd = string.gsub(cmd, "^%s*(.-)%s*$", "%1")
    if cmd ~= "" then
      parts[#parts + 1] = entry.key .. "=" .. cmd
    end
  end
  return table.concat(parts, "\n"), #parts
end

-- Shared by Done. The gestures used to need their own save button, which meant
-- typing one and pressing Done threw it away. Returns how many are bound and
-- whether anything actually changed, so Done stays quiet when it has nothing
-- to report.
saveChromeGestures = function()
  if chromeFields == nil or setChromeGestures == nil then
    return 0, false
  end
  local joined, count = serialiseChromeFields()
  if joined == chromeBaseline then
    return count, false
  end
  setChromeGestures(joined)
  chromeBaseline = joined
  return count, true
end

-- Called back after a tool has changed the grid or the button size behind the
-- dialog. The dialog is full screen, so without this the only way to see what
-- a Fit did was to close settings and open them again.
function refreshValues(values)
  if values == nil then
    return
  end
  -- Reported as it is, not clamped: this is what the grid actually became, and
  -- a box quietly showing 160 when the grid is 200 is the kind of small lie
  -- that costs an afternoon. Only the slider position gets clamped, because
  -- that is all a slider can express.
  if values.gridX ~= nil then
    gridX = math.floor(values.gridX)
    setSliderQuietly(xSeekBar, clampGrid(gridX) - GRID_MIN)
    if gridXField ~= nil then gridXField:setText(tostring(gridX)) end
    if editorValues ~= nil then editorValues.gridX = gridX end
  end
  if values.gridY ~= nil then
    gridY = math.floor(values.gridY)
    setSliderQuietly(ySeekBar, clampGrid(gridY) - GRID_MIN)
    if gridYField ~= nil then gridYField:setText(tostring(gridY)) end
    if editorValues ~= nil then editorValues.gridY = gridY end
  end
  -- The box always shows the size that was applied. editorValues only follows
  -- when the change was set-wide, because Done writes editorValues.width to the
  -- set default -- and a size meant for two selected buttons must not become
  -- what every new button starts at. Leaving it alone is equally deliberate:
  -- Done would otherwise write the stale open-time size back over the new one.
  if values.width ~= nil then
    if sizeWidthField ~= nil then sizeWidthField:setText(tostring(values.width)) end
    if editorValues ~= nil and values.becameDefault then
      editorValues.width = values.width
    end
  end
  if values.height ~= nil then
    if sizeHeightField ~= nil then sizeHeightField:setText(tostring(values.height)) end
    if editorValues ~= nil and values.becameDefault then
      editorValues.height = values.height
    end
  end
end

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
    if suppressGridSync then
      return
    end
    gridX = (progress + GRID_MIN)
    if gridXField ~= nil then gridXField:setText(tostring(gridX)) end
    if(setGridXSpacing ~= nil) then
      setGridXSpacing(gridX)
    end
  end
})

gridYSeekBarChangeListener = luajava.createProxy("android.widget.SeekBar$OnSeekBarChangeListener",{
  onProgressChanged = function(v,progress,state)
    if suppressGridSync then
      return
    end
    gridY = (progress + GRID_MIN)
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
    opacitySeekBarLabel:setText("Grid opacity: "..opacitypct.."%")
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
    -- A grid box the user typed into and never left still holds uncommitted
    -- text; Done is a commit.
    commitGridFields()
    local bound, changed = saveChromeGestures()

    --collect editor values
    if(setEditorValues ~= nil) then
      for i,k in pairs(setEditorValues) do
        editorValues[i] = k
      end
    end
    if(editorDone ~= nil) then
      editorDone(editorValues)
    end

    if changed then
      Note("\nSaved, including " .. bound .. " additional gesture(s).\n")
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

