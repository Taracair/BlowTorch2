--encapulsation of the button list editor
local luajava = _G["luajava"]
local R_id = _G["R_id"]
local R_layout = _G["R_layout"]
local RelativeLayout = _G["RelativeLayout"]
local RelativeLayoutParams = _G["RelativeLayoutParams"]
local TranslateAnimation = _G["TranslateAnimation"]
local R_drawable = _G["R_drawable"]
local ImageButton = luajava.bindClass("android.widget.ImageButton")
local LinearLayoutParams = _G["LinearLayoutParams"]
local Context = luajava.bindClass("android.content.Context")
local EditText = _G["EditText"]
local LinearLayout = _G["LinearLayout"]
local Button = _G["Button"]
local pairs = _G["pairs"]
local ipairs = _G["ipairs"]
local table = _G["table"]
local density = _G["density"]
local ViewGroup = _G["ViewGroup"]
local tonumber = _G["tonumber"]
local View = _G["View"]
local Color= _G["Color"]
local Note = _G["Note"]
local KeyEvent = _G["KeyEvent"]
local modifyButtonSetCallback = _G["modifyButtonSet"]
local DialogInterface = _G["DialogInterface"]
local PluginXCallS = _G["PluginXCallS"]
-- module() swaps the environment for the module table, so anything not pulled in
-- above is simply nil in here. showList() guards its dismiss with pcall, which was
-- never on this list: reopening the set list threw "attempt to call global 'pcall'
-- (a nil value)" instead of quietly dropping a stale dialog.
local pcall = _G["pcall"]
module(...)

local lastSelectedIndex = -1
local selectedIndex = -1
local selectedSet = nil
local sortedList = nil
local list = nil
local itemClicked = nil
local adapter = nil
local dialog = nil
-- Row waiting on a delete confirmation.
local pendingDeleteIndex = -1
local dpadupdownlistener = nil
local dpadselectionlistener = nil
local layoutInflater = nil
local context = nil
local rowModifyListener = nil
local rowLoadListener = nil
local rowDeleteListener = nil
local makeRowButton = nil
local newButtonListener = nil
local newSetDoneListener = nil
local newSetCancelListener = nil
local newSetEdit = nil
local newButtonSetDialog = nil
local deleteConfirmListener = nil
local deleteCancelListener = nil
local doneListener = nil

local reclick_ = {}
local scrollListener = {}
local focusListener = nil

function init(pContext)	
	context = pContext
	layoutInflater = context:getSystemService(Context.LAYOUT_INFLATER_SERVICE)
	
end

-- Which row a control belongs to travels on the control itself.
local function rowIndexOf(v)
  local tag = v:getTag()
  if tag == nil then
    return -1
  end
  return tag:intValue()
end

local function entryFor(v)
  local index = rowIndexOf(v)
  if index < 0 then
    return nil
  end
  return sortedList[index+1]
end

makeRowButton = function(icon,listener,pos)
  local button = luajava.new(ImageButton,context)
  local pad = math.floor(6 * (context:getResources()):getDisplayMetrics().density + 0.5)
  button:setPadding(pad,pad,pad,pad)
  button:setLayoutParams(luajava.new(LinearLayoutParams,LinearLayoutParams.WRAP_CONTENT,LinearLayoutParams.WRAP_CONTENT))
  button:setBackgroundColor(0)
  button:setImageResource(icon)
  button:setTag(luajava.newInstance("java.lang.Integer",pos))
  button:setOnClickListener(listener)
  button:setFocusable(false)
  button:setFocusableInTouchMode(false)
  return button
end

rowModifyListener = luajava.createProxy("android.view.View$OnClickListener",{
  onClick = function(v)
    local item = entryFor(v)
    if item == nil then
      return
    end
    modifyButtonSetCallback(item)
    dialog:dismiss()
  end
})

rowLoadListener = luajava.createProxy("android.view.View$OnClickListener",{
  onClick = function(v)
    local entry = entryFor(v)
    if entry == nil then
      return
    end
    if(entry.name ~= selectedSet) then
      PluginXCallS("loadButtonSet",entry.name)
    end
    dialog:dismiss()
  end
})

rowDeleteListener = luajava.createProxy("android.view.View$OnClickListener",{
  onClick = function(v)
    local index = rowIndexOf(v)
    if index < 0 or sortedList[index+1] == nil then
      return
    end
    pendingDeleteIndex = index
    local builder = luajava.newInstance("android.app.AlertDialog$Builder",v:getContext())
    builder:setTitle("Delete Button Set")
    builder:setMessage("Confirm delete?")
    builder:setPositiveButton("Yes",deleteConfirmListener)
    builder:setNegativeButton("No",deleteCancelListener)
    local confirm = builder:create()
    confirm:show()
  end
})


adapter = luajava.createProxy("android.widget.ListAdapter",{
	getView = function(pos,v,parent)
		local newview = nil
		if(v ~= nil) then
			newview = v
			
		else
			--Note("inflating view")
			newview = layoutInflater:inflate(R_layout.editor_selection_list_row,nil)
		
			local root = newview:findViewById(R_id.root)
			--root:setOnClickListener(rowClicker_cb)
			
		end
		
		newview:setId(157*pos)
		
		local holder = newview:findViewById(R_id.toolbarholder)
		holder:setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS)
		--holder:setFocusableInTouchMode(false)
		
		if(holder:getChildCount() > 0) then
			holder:removeAllViews()
			-- Do not reset lastSelectedIndex here; recycling would clear a valid selection.
		end
		
		-- Load, edit and delete live in the row. They used to be one shared toolbar
		-- that slid in over whichever row you tapped, which is unlike every other
		-- list in the app and hid the actions until you went looking for them. Each
		-- button carries its own row index, so no selection has to be remembered.
		holder:addView(makeRowButton(R_drawable.ic_row_load, rowLoadListener, pos))
		holder:addView(makeRowButton(R_drawable.ic_row_edit, rowModifyListener, pos))
		holder:addView(makeRowButton(R_drawable.ic_row_delete, rowDeleteListener, pos))
		
		item = sortedList[tonumber(pos)+1]
		
		if(item ~= nil) then
	
			label = newview:findViewById(R_id.infoTitle)
			extra = newview:findViewById(R_id.infoExtended)
			
			icon = newview:findViewById(R_id.icon)
			icon:setVisibility(View.GONE)
			label:setText(item.name)
			extra:setText("Contains: "..item.count.." buttons")
			
			if(selectedIndex == (pos+1)) then
				label:setBackgroundColor(Color:argb(55,255,255,255))
				extra:setBackgroundColor(Color:argb(55,255,255,255))
			else
				label:setBackgroundColor(Color:argb(0,0,0,0))
				extra:setBackgroundColor(Color:argb(0,0,0,0))
			end
			--newview:setId(pos)
		end
		return newview
	end,
	getCount = function() return #sortedList end,
	areAllItemsEnabled = function() return true end,
	isEnabled = function(position) return true end,
	getItem = function(position) return sortedList[position+1] end,
	getItemId = function(position) return 1 end,
	isEmpty = function() return false end,
	hasStableIds = function() return true end,
	getViewTypeCount = function() return 1 end,
	getItemViewType = function(pos) return 1 end
})

function dismissList()
  if(dialog ~= nil) then
    dialog:dismiss()
  end
end

function sortList(unsortedList)
  sortedList = {}
  for k,v in pairs(sortedList) do
    sortedList[v] = nil;
  end
  sortedList = nil
  sortedList = {}
  
  local counter = 1;
  selectedIndex = -1;
  for i,k in pairs(unsortedList) do
    local tmp = {}
    tmp.name = i
    tmp.count = k
    table.insert(sortedList,tmp)
  end

  table.sort(sortedList,function(a,b) if(a.name < b.name) then return true else return false end end)

  --find the selectedindex
  for i,k in ipairs(sortedList) do
    if(k.name == selectedSet) then
      selectedIndex = counter
    end
    counter = counter + 1
  end
end

function showList(unsortedList,lastLoadedSet)
	
	if(dialog ~= nil) then
		pcall(function() dialog:dismiss() end)
		dialog = nil
	end
	
	selectedSet = lastLoadedSet
	
	--sort the list
	sortList(unsortedList)
	

	--actually make the dialog
	local layout = luajava.newInstance("android.widget.RelativeLayout",context)
	layout = layoutInflater:inflate(R_layout.editor_selection_dialog,layout)
	
	list = layout:findViewById(R_id.list)
	--keep the list
	
	
	list:setScrollbarFadingEnabled(false)
	list:setOnItemClickListener(itemClicked)
	list:setSelector(R_drawable.filter_selection_selector)
	list:setAdapter(adapter)
	list:setOnScrollListener(scrollListener)
	list:setOnFocusChangeListener(focusListener)
	list:setFocusable(true)
	list:bringToFront()
	list:setFocusableInTouchMode(false)
	
	local emptyView = layout:findViewById(R_id.empty)
	list:setEmptyView(emptyView)
	list:setSelectionFromTop(selectedIndex -1,10*density)

	local title = layout:findViewById(R_id.titlebar)
	title:setText("SELECT BUTTON SET")
	
	local newbutton = layout:findViewById(R_id.add)
	newbutton:setText("New Set")
	newbutton:setOnClickListener(newButtonListener)
	
	local donebutton = layout:findViewById(R_id.done)
	donebutton:setOnClickListener(doneListener)

	-- Button Sets has no plugin filter; hide the unused "=" control.
	local optionsbutton = layout:findViewById(R_id.optionsbutton)
	if optionsbutton ~= nil then
		optionsbutton:setVisibility(View.GONE)
	end
	local searchField = layout:findViewById(R_id.search_field)
	if searchField ~= nil then
		searchField:setVisibility(View.GONE)
	end

	dialog = luajava.newInstance("com.resurrection.blowtorch2.lib.window.LuaDialog",context,layout,false,nil)

	--end
	dialog:show()
end

function updateButtonListDialog()
	list:setAdapter(adapter)
	dialog:dismiss()
end

function updateButtonListDialogNoItems()
	list:setAdapter(adapter)
	emptyButtons()
	mSelectorDialog:dismiss()
end




--function buttonListAdapter.




dpadupdownlistener = luajava.createProxy("android.view.View$OnKeyListener",{
	onKey = function(v,keyCode,event)
		return false
	end
})


dpadselectionlistener = luajava.createProxy("android.widget.AdapterView$OnItemSelectedListener",{
	onItemSelected = function(adapter,view,position,rowid)
		if(view:getTop() < 0 or view:getBottom() > list:getHeight()) then
			list:smoothScrollToPosition(position,100)
		end
	end
})

local function makeSelectionRunnerForRow(pos,target)
	local scrollselectionrunner_ = {}
	function scrollselectionrunner.run()
		list:performItemClick((list:getAdapter()):getView(target,1,1))
	end
	local scrollselectionrunner = luajava.createProxy("java.lang.Runnable",scrollselectionrunner_)
	target:postDelayed(scrollselectionrunner)
end


itemClicked = luajava.createProxy("android.widget.AdapterView$OnItemClickListener",{
	onItemClick = function(arg0,view,position,arg3)
		local entry = sortedList[position+1]
		if entry == nil then
			return
		end
		lastSelectedIndex = position
		if(entry.name ~= selectedSet) then
			PluginXCallS("loadButtonSet",entry.name)
		end
		dialog:dismiss()
	end,
	onNothingSelected = function(arg0) end --don't care
})

reclick_.target = -1
function reclick_.run()
	list:performItemClick(adapter:getView(reclick_.target,nil,nil),reclick_.target,reclick_.target)
end

scrollListener = luajava.createProxy("android.widget.AbsListView$OnScrollListener",{
	onScrollStateChanged = function(view,scrollstate)
		-- Nothing to dismiss on scroll now that the controls ride in the rows.
	end,
	onScroll = function(view,first,visCount,totalCount)
		--don't care
	end
})

focusListener = luajava.createProxy("android.view.View$OnFocusChangeListener",{
	onFocusChange = function(view,hasfocus)
		if(hasfocus) then
			list:setSelector(R_drawable.filter_selection_selector)
		else
			list:setSelector(R_drawable.transparent)
		end
	end
})

newButtonListener = luajava.createProxy("android.view.View$OnClickListener",{
  onClick = function(v)
    --Note("new button pressed")
    dialog:dismiss()
    --local context = view:getContext()
    --make the new button set text input dialog and show it.
    local linear = luajava.new(LinearLayout,context)
    
    local llparams = luajava.new(LinearLayoutParams,350*density,LinearLayoutParams.WRAP_CONTENT)
    
    local fillparams = luajava.new(LinearLayoutParams,LinearLayoutParams.FILL_PARENT,LinearLayoutParams.WRAP_CONTENT,1)
    
    local buttonholder = luajava.new(LinearLayout,context)
    buttonholder:setLayoutParams(llparams)
    buttonholder:setOrientation(LinearLayout.HORIZONTAL)
    linear:setLayoutParams(llparams)
    linear:setOrientation(LinearLayout.VERTICAL)
    
    newSetEdit = luajava.new(EditText,context)
    newSetEdit:setHint("New Button Set Name")
    
    local done = luajava.new(Button,context)
    done:setText("Done")
    done:setLayoutParams(fillparams)
    done:setOnClickListener(newSetDoneListener)
    
    local cancel = luajava.new(Button,context)
    cancel:setText("Cancel")
    cancel:setLayoutParams(fillparams)
    cancel:setOnClickListener(newSetCancelListener)
    
    buttonholder:addView(cancel)
    buttonholder:addView(done)
    
    linear:addView(newSetEdit)
    linear:addView(buttonholder)
    
    newButtonSetDialog = luajava.newInstance("com.resurrection.blowtorch2.lib.window.LuaDialog",context,linear,false,nil)
    newButtonSetDialog:show()
  end
})

newSetDoneListener = luajava.createProxy("android.view.View$OnClickListener",{
  onClick = function(view)
    newButtonSetDialog:dismiss()
    local text = newSetEdit:getText():toString()
    PluginXCallS("makeNewButtonSet",text)
  end
})

newSetCancelListener = luajava.createProxy("android.view.View$OnClickListener",{ 
  onClick = function(v)
    newButtonSetDialog:dismiss()
  end
})

deleteConfirmListener = luajava.createProxy("android.content.DialogInterface$OnClickListener",{
  onClick = function(confirmDialog,which)
    if(which == DialogInterface.BUTTON_POSITIVE) then
      local index = pendingDeleteIndex
      local entry = sortedList[index+1]
      if entry ~= nil then
        table.remove(sortedList,index+1)
        PluginXCallS("deleteButtonSet",entry.name)
        list:setAdapter(adapter)
      end
    end
    pendingDeleteIndex = -1
  end
})

deleteCancelListener = luajava.createProxy("android.content.DialogInterface$OnClickListener",{
  onClick = function(dialog,which)
    dialog:dismiss()
  end
})

function updateButtonListDialog(data)
  
  --buttonSetListDialog.updateButtonListDialog()
  selectedSet = data.setname
  --unsortedList = data.setlist
  sortList(data.setlist)
  --Note("\nConfirmingDelete: " .. data.setname)
  list:setAdapter(adapter)
  --dialog:dismiss()
end

function updateButtonListDialogNoItems()
  sortedList = {}
  list:setAdapter(adapter)
  --emptyButtons()
  --dialog:dismiss()
end

doneListener = luajava.createProxy("android.view.View$OnClickListener",{
  onClick = function(v)
    --local foo = nil
    --pcall(foo)
    dialog:dismiss()
  end
})

