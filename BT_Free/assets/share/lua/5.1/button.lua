
Color = luajava.bindClass("com.offsetnull.bt.ui.ColorCompat")
Path = luajava.bindClass("android.graphics.Path")
PathDirection = luajava.bindClass("android.graphics.Path$Direction")
statusoffset = 0

--typeface support for bolding text
local Typeface = luajava.bindClass("android.graphics.Typeface")
local DEFAULT_BOLD_TYPEFACE = Typeface.DEFAULT_BOLD

buttonRoundness = 16
BUTTONSET_DATA = {
						height 			= 48,
						width 			= 48,
						labelSize 		= 16,
						primaryColor 	= Color:argb(0x88,0x00,0x00,0xFF),
						labelColor		= Color:argb(0xAA,0xAA,0xAA,0xAA),
						selectedColor 	= Color:argb(0x88,0x00,0xFF,0x00),
						flipColor 		= Color:argb(0x88,0xFF,0x00,0x00),
						flipLabelColor 	= Color:argb(0x88,0x00,0x00,0xFF),
						command = "",
						label = "LABEL",
						flipLabel = "",
						flipCommand = "",
						holdCommand = "",
						swipeUpCommand = "",
						swipeDownCommand = "",
						swipeLeftCommand = "",
						swipeRightCommand = "",
						name = "",
						switchTo = "",
						accordionDirection = "",
						accordionChildren = {},
						accordionAutoClose = true,
						gridXwidth = 50,
						gridYwidth = 50			
			  		}
function BUTTONSET_DATA:new(o)
	o = o or {}
	setmetatable(o,self)
	return o
end

BUTTONSET_DATA.__index = BUTTONSET_DATA

BUTTON_DATA = 	 { 	
						x				= 100,
						y				= 100,
						--height 			= 80,
						--width 			= 80,
						--command 		= "",
						--label 			= "LABEL",
						--labelSize 		= 23,
						--flipLabel		= "",
						--flipCommand 	= "",
						--primaryColor 	= Color:argb(0x88,0x00,0x00,0xFF),
						--labelColor		= Color:argb(0xAA,0xAA,0xAA,0xAA),
						--selectedColor 	= Color:argb(0x88,0x00,0xFF,0x00),
						--flipColor 		= Color:argb(0x88,0xFF,0x00,0x00),
						--flipLabelColor 	= Color:argb(0x88,0x00,0x00,0xFF)				
			  	 }
function BUTTON_DATA:new(o)
	o = o or {}
	setmetatable(o,self)
	return o
end

BUTTON_DATA.__index = BUTTONSET_DATA

BUTTON = {} -- this class is purley a factory. these represent "in use" buttons
function BUTTON:new(data,density)
	local o = {}
	o.paintOpts = luajava.newInstance("android.graphics.Paint")
	o.paintOpts:setAntiAlias(true)
	o.paintOpts:setXfermode(xferModeSRC)
	o.paintOpts:setTypeface(DEFAULT_BOLD_TYPEFACE)
	o.rect = luajava.newInstance("android.graphics.RectF")
	o.inset = luajava.newInstance("android.graphics.RectF")
	o.data = BUTTON_DATA:new(data)
	o.selected = false
	o.expanded = false
	o.isAccordionChild = false
	o.isAccordionClose = false
	o.accordionParent = nil
	o.accordionOverlay = nil
	setmetatable(o,self)
	self.__index = self
	o.density = density
	o:updateRect(statusoffset)
	
	return o
end

function BUTTON:updateRect(statusoffset)
	local left = self.data.x - (self.data.width/2)*self.density
	local right = self.data.x + (self.data.width/2)*self.density
	local top = self.data.y - (self.data.height/2)*self.density + statusoffset
	local bottom = self.data.y + (self.data.height/2)*self.density + statusoffset
	local tmp = self.rect

	tmp:set(left,top,right,bottom)
	self.inset:set(left+1.0,top+1.0,right-1.0,bottom-1.0)
end

local function hasGestureCommand(data, field)
	return data ~= nil and data[field] ~= nil and data[field] ~= ""
end

local function hasAccordionConfig(data)
	if data == nil or data.accordionDirection == nil or data.accordionDirection == "" then
		return false
	end
	return data.accordionChildren ~= nil and #data.accordionChildren > 0
end

PaintStyle = luajava.bindClass("android.graphics.Paint$Style")

local function drawDirectionArrow(canvas, paint, cx, cy, direction, size, color)
	local previousStyle = paint:getStyle()
	paint:setColor(color)
	paint:setStyle(PaintStyle.FILL)
	local path = luajava.newInstance("android.graphics.Path")
	local half = size * 0.5
	if direction == "up" then
		path:moveTo(cx, cy - half)
		path:lineTo(cx - half, cy + half * 0.6)
		path:lineTo(cx + half, cy + half * 0.6)
	elseif direction == "down" then
		path:moveTo(cx, cy + half)
		path:lineTo(cx - half, cy - half * 0.6)
		path:lineTo(cx + half, cy - half * 0.6)
	elseif direction == "left" then
		path:moveTo(cx - half, cy)
		path:lineTo(cx + half * 0.6, cy - half)
		path:lineTo(cx + half * 0.6, cy + half)
	elseif direction == "right" then
		path:moveTo(cx + half, cy)
		path:lineTo(cx - half * 0.6, cy - half)
		path:lineTo(cx - half * 0.6, cy + half)
	end
	path:close()
	canvas:drawPath(path, paint)
	paint:setStyle(previousStyle)
end

local function drawAccordionChevron(canvas, paint, rect, direction, expanded, density)
	local inset = 6 * density
	local cx = rect:left() + inset
	local cy = rect:top() + inset
	local size = 8 * density
	local color = Color:argb(220, 0x66, 0xDD, 0xFF)
	if expanded then
		color = Color:argb(220, 0xFF, 0xAA, 0x44)
	end
	if direction == "down" or direction == "up" then
		cx = (rect:left() + rect:right()) * 0.5
		if direction == "down" then
			cy = rect:bottom() - inset
		else
			cy = rect:top() + inset
		end
	elseif direction == "left" or direction == "right" then
		cy = (rect:top() + rect:bottom()) * 0.5
		if direction == "right" then
			cx = rect:right() - inset
		else
			cx = rect:left() + inset
		end
	end
	local drawDir = direction
	if expanded then
		if direction == "down" then drawDir = "up"
		elseif direction == "up" then drawDir = "down"
		elseif direction == "left" then drawDir = "right"
		elseif direction == "right" then drawDir = "left"
		end
	end
	drawDirectionArrow(canvas, paint, cx, cy, drawDir, size, color)
end

function BUTTON:drawGestureIndicators(canvas, paint)
	local rect = self.rect
	local edge = 7 * self.density
	local arrow = 5 * self.density
	local color = Color:argb(170, 0xFF, 0xFF, 0xFF)
	if hasGestureCommand(self.data, "swipeUpCommand") then
		drawDirectionArrow(canvas, paint, (rect:left() + rect:right()) * 0.5, rect:top() + edge, "up", arrow, color)
	end
	if hasGestureCommand(self.data, "swipeDownCommand") then
		drawDirectionArrow(canvas, paint, (rect:left() + rect:right()) * 0.5, rect:bottom() - edge, "down", arrow, color)
	end
	if hasGestureCommand(self.data, "swipeLeftCommand") then
		drawDirectionArrow(canvas, paint, rect:left() + edge, (rect:top() + rect:bottom()) * 0.5, "left", arrow, color)
	end
	if hasGestureCommand(self.data, "swipeRightCommand") then
		drawDirectionArrow(canvas, paint, rect:right() - edge, (rect:top() + rect:bottom()) * 0.5, "right", arrow, color)
	end
	if hasGestureCommand(self.data, "holdCommand") then
		paint:setColor(Color:argb(180, 0xFF, 0xFF, 0x66))
		paint:setTextSize(9 * self.density)
		canvas:drawText("H", rect:right() - 11 * self.density, rect:top() + 11 * self.density, paint)
	end
end

function BUTTON:draw(state,canvas)
	if(canvas == nil) then
		error("canvas parameter must not be null")
	end
	
	local usestate = 0
	local p = self.paintOpts
	if(state ~= nil) then
		usestate = state
	end
	
	local rect = self.rect
	--Note("drawing button, roundness is"..buttonRoundness)
	--buttonRoundness = 30.0
	if(usestate == 0) then
		p:setColor(self.data.primaryColor)
		canvas:drawRoundRect(rect,buttonRoundness,buttonRoundness,p)
	elseif(usestate == 1) then
		p:setColor(self.data.selectedColor)
		canvas:drawRoundRect(self.inset,buttonRoundness,buttonRoundness,p)
	elseif(usestate == 2) then
		p:setColor(self.data.flipColor)
		canvas:drawRoundRect(self.inset,buttonRoundness,buttonRoundness,p)
	end
	
	local label = nil
	if(usestate == 0 or usestate == 1) then
		p:setColor(self.data.labelColor)
		p:setTextSize(tonumber(self.data.labelSize)*self.density)
		--p:setTypeface(DEFAULT_BOLD_TYPEFACE)
		label = self.data.label
	elseif(usestate == 2) then
		p:setColor(self.data.flipLabelColor)
		--p:setTypeface(DEFAULT_BOLD_TYPEFACE)
		if(self.data.flipLabel == "" or self.data.flipLabel == nil) then
			label = self.data.label
		else
			label = self.data.flipLabel
		end
		
	end
	local tX = self.data.x - (p:measureText(label)/2)
	local tY = self.data.y + (p:getTextSize()/2) + statusoffset
	p:setTypeface(DEFAULT_BOLD_TYPEFACE)
	canvas:drawText(label,tX,tY,p)
	if not self.isAccordionChild then
		self:drawGestureIndicators(canvas, p)
		if hasAccordionConfig(self.data) then
			drawAccordionChevron(canvas, p, rect, self.data.accordionDirection, self.expanded, self.density)
		end
		if self.expanded and self.data.accordionAutoClose == false then
			p:setColor(Color:argb(220, 0xFF, 0x66, 0x66))
			p:setTextSize(10 * self.density)
			canvas:drawText("x", rect:right() - 10 * self.density, rect:top() + 12 * self.density, p)
		end
	end
end

PorterDuffMode = luajava.bindClass("android.graphics.PorterDuff$Mode")
xferModeClear = luajava.newInstance("android.graphics.PorterDuffXfermode",PorterDuffMode.CLEAR)
xferModeSRC = luajava.newInstance("android.graphics.PorterDuffXfermode",PorterDuffMode.SRC)

function BUTTON:clearButton(canvas)
	local p = self.paintOpts
	p:setXfermode(xferModeClear)
	canvas:drawRoundRect(self.rect,5,5,p)
	p:setXfermode(nil)
end
