
Color = luajava.bindClass("com.resurrection.blowtorch2.lib.ui.ColorCompat")
Path = luajava.bindClass("android.graphics.Path")
PathDirection = luajava.bindClass("android.graphics.Path$Direction")
statusoffset = 0

--typeface support for bolding text
local Typeface = luajava.bindClass("android.graphics.Typeface")
local DEFAULT_BOLD_TYPEFACE = Typeface.DEFAULT_BOLD

buttonRoundness = 16
buttonShowHints = true
-- Live arrow drawn under the finger during a swipe, showing which direction is
-- currently being aimed at. Off-putting for some, so it has its own switch.
buttonShowSwipePreview = true
-- Callout above the tile naming the command the current gesture would send, for
-- when a button has more bindings than anyone remembers. Per-button setting.
buttonShowGestureLabels = true
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
						swipeUpLeftCommand = "",
						swipeUpRightCommand = "",
						swipeDownLeftCommand = "",
						swipeDownRightCommand = "",
						showGestureLabel = true,
						-- Per button, on top of the profile-wide switch: the
						-- global one is a master off, this picks which buttons
						-- carry swipe letters, corner arrows, Hold and accordion
						-- badges while it is on. Lives on BUTTONSET_DATA so a
						-- button that has never been asked inherits "yes", which
						-- is how every profile behaved before it existed.
						showGestureHints = true,
						name = "",
						switchTo = "",
						accordionDirection = "",
						accordionChildren = {},
						accordionAutoClose = true,
						accordionTrigger = "tap",
						accordionHoldMs = 450,
						accordionChildLayout = "along",
						-- Floating copy over the game (Phase 0 schema). Same
						-- inheritance path as accordion: live on BUTTONSET_DATA
						-- so BUTTON_DATA:new lookups resolve defaults.
						floating = false,
						floatMode = "always",
						floatX = -1,
						floatY = -1,
						-- Landscape keeps its own pair; -1 = never placed there,
						-- so an existing profile seeds landscape from the grid
						-- and keeps the portrait position it already had.
						floatXLand = -1,
						floatYLand = -1,
						floatRound = false,
						floatFrame = false,
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

local function rectLeft(r)
	return r.left
end

local function rectTop(r)
	return r.top
end

local function rectRight(r)
	return r.right
end

local function rectBottom(r)
	return r.bottom
end

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
	-- posX/posY live in buttonwindow and pick the pair for the orientation the
	-- phone is in; they are absent in the plain-button unit context, where the
	-- portrait pair is the only one there is.
	if posX ~= nil and posY ~= nil then
		self:updateRectAt(posX(self.data), posY(self.data), statusoffset)
	else
		self:updateRectAt(self.data.x, self.data.y, statusoffset)
	end
end

-- Draw the button at x,y without touching data.x/data.y. Turning the phone
-- makes some buttons fall outside the narrower screen, and the old code fixed
-- that by writing the clamped position back -- which then came home with you
-- when you turned back, so one trip to landscape rearranged portrait for good.
-- Clamping is a drawing concern; only a finger may move a button for keeps.
function BUTTON:updateRectAt(x, y, statusoffset)
	local left = x - (self.data.width/2)*self.density
	local right = x + (self.data.width/2)*self.density
	local top = y - (self.data.height/2)*self.density + statusoffset
	local bottom = y + (self.data.height/2)*self.density + statusoffset
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

local function getAccordionTrigger(data)
	local trigger = data.accordionTrigger
	if trigger == nil or trigger == "" then
		return "tap"
	end
	return trigger
end

local function indicatorSize(rect, density)
	local w = rectRight(rect) - rectLeft(rect)
	local h = rectBottom(rect) - rectTop(rect)
	local minDim = math.min(w, h)
	return math.max(4 * density, math.min(7 * density, minDim * 0.14))
end

local function edgeInset(rect, density)
	return math.max(3 * density, math.min(6 * density, (rectRight(rect) - rectLeft(rect)) * 0.1))
end

local function drawAccordionTriggerBadge(canvas, paint, rect, direction, trigger, density)
	local badge = "T"
	local color = Color:argb(160, 0xAA, 0xAA, 0xAA)
	if trigger == "hold" then
		badge = "H"
		color = Color:argb(180, 0xFF, 0xCC, 0x66)
	elseif trigger == "swipe" then
		badge = "S"
		color = Color:argb(180, 0x99, 0xCC, 0xFF)
	end
	local inset = edgeInset(rect, density)
	local cx = (rectLeft(rect) + rectRight(rect)) * 0.5
	local cy = (rectTop(rect) + rectBottom(rect)) * 0.5
	if direction == "down" then
		cx = rectLeft(rect) + inset * 2
		cy = rectBottom(rect) - inset
	elseif direction == "up" then
		cx = rectLeft(rect) + inset * 2
		cy = rectTop(rect) + inset
	elseif direction == "left" then
		cx = rectLeft(rect) + inset
		cy = rectTop(rect) + inset * 2
	elseif direction == "right" then
		cx = rectRight(rect) - inset
		cy = rectTop(rect) + inset * 2
	end
	paint:setColor(color)
	paint:setTextSize(math.max(7 * density, 8))
	canvas:drawText(badge, cx, cy, paint)
end

PaintStyle = luajava.bindClass("android.graphics.Paint$Style")

-- Unit vector per gesture direction. Screen y grows downward, so "up" is -1.
local DIRECTION_VECTORS = {
	up        = {  0,       -1       },
	down      = {  0,        1       },
	left      = { -1,        0       },
	right     = {  1,        0       },
	upleft    = { -0.70711, -0.70711 },
	upright   = {  0.70711, -0.70711 },
	downleft  = { -0.70711,  0.70711 },
	downright = {  0.70711,  0.70711 },
}

-- Isoceles triangle pointing along `direction`, centred on (cx, cy).
--
-- Built from the direction vector rather than a branch per direction so the four
-- diagonals come for free. For up/down/left/right this produces exactly the same
-- three points the previous hand-written branches did, so accordion chevrons and
-- existing gesture hints are pixel-identical.
local function drawDirectionArrow(canvas, paint, cx, cy, direction, size, color)
	local vec = DIRECTION_VECTORS[direction]
	if vec == nil then
		return
	end
	local previousStyle = paint:getStyle()
	paint:setColor(color)
	paint:setStyle(PaintStyle.FILL)
	local path = luajava.newInstance("android.graphics.Path")
	local half = size * 0.5
	local ux, uy = vec[1], vec[2]
	local px, py = -uy, ux                       -- perpendicular, spreads the base
	local baseX, baseY = cx - ux * half * 0.6, cy - uy * half * 0.6
	path:moveTo(cx + ux * half, cy + uy * half)  -- tip
	path:lineTo(baseX - px * half, baseY - py * half)
	path:lineTo(baseX + px * half, baseY + py * half)
	path:close()
	canvas:drawPath(path, paint)
	paint:setStyle(previousStyle)
end

local function accordionChevronPosition(rect, direction, density, data)
	local inset = edgeInset(rect, density)
	local w = rectRight(rect) - rectLeft(rect)
	local h = rectBottom(rect) - rectTop(rect)
	local cx = (rectLeft(rect) + rectRight(rect)) * 0.5
	local cy = (rectTop(rect) + rectBottom(rect)) * 0.5
	if direction == "down" then
		cx = hasGestureCommand(data, "swipeDownCommand") and (rectLeft(rect) + w * 0.28) or cx
		cy = rectBottom(rect) - inset
	elseif direction == "up" then
		cx = hasGestureCommand(data, "swipeUpCommand") and (rectLeft(rect) + w * 0.28) or cx
		cy = rectTop(rect) + inset
	elseif direction == "left" then
		cx = rectLeft(rect) + inset
		cy = hasGestureCommand(data, "swipeLeftCommand") and (rectTop(rect) + h * 0.28) or cy
	elseif direction == "right" then
		cx = rectRight(rect) - inset
		cy = hasGestureCommand(data, "swipeRightCommand") and (rectTop(rect) + h * 0.28) or cy
	end
	return cx, cy
end

local function drawAccordionChevron(canvas, paint, rect, direction, expanded, density, data)
	local cx, cy = accordionChevronPosition(rect, direction, density, data)
	local size = indicatorSize(rect, density) + density
	local color = Color:argb(200, 0x66, 0xDD, 0xFF)
	if expanded then
		color = Color:argb(200, 0xFF, 0xAA, 0x44)
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

-- Compass layout for the gesture hints: the four straight swipes are lettered on
-- the edge midpoints, the four diagonals are small arrows in the corners.
--
-- The letters used to sit at 72% of the width/height, which is a corner. That was
-- fine while only four directions existed; it collides with the diagonals now, so
-- they moved to true midpoints.
local STRAIGHT_HINTS = {
	{ field = "swipeUpCommand",    text = "U", edge = "top"    },
	{ field = "swipeDownCommand",  text = "D", edge = "bottom" },
	{ field = "swipeLeftCommand",  text = "L", edge = "left"   },
	{ field = "swipeRightCommand", text = "R", edge = "right"  },
}

local DIAGONAL_HINTS = {
	{ field = "swipeUpLeftCommand",    dir = "upleft"    },
	{ field = "swipeUpRightCommand",   dir = "upright"   },
	{ field = "swipeDownLeftCommand",  dir = "downleft"  },
	{ field = "swipeDownRightCommand", dir = "downright" },
}

function BUTTON:drawGestureIndicators(canvas, paint)
	local rect = self.rect
	local inset = edgeInset(rect, self.density)
	local arrow = indicatorSize(rect, self.density)
	local color = Color:argb(150, 0xFF, 0xFF, 0xFF)
	local left, right = rectLeft(rect), rectRight(rect)
	local top, bottom = rectTop(rect), rectBottom(rect)
	local midX = (left + right) * 0.5
	local midY = (top + bottom) * 0.5

	paint:setTextSize(math.max(7 * self.density, arrow * 1.35))
	for _, hint in ipairs(STRAIGHT_HINTS) do
		if hasGestureCommand(self.data, hint.field) then
			local hx, hy
			if hint.edge == "top" then
				hx, hy = midX - arrow * 0.4, top + inset + arrow * 0.35
			elseif hint.edge == "bottom" then
				hx, hy = midX - arrow * 0.4, bottom - inset + arrow * 0.15
			elseif hint.edge == "left" then
				hx, hy = left + inset, midY + arrow * 0.35
			else
				hx, hy = right - inset - arrow * 0.6, midY + arrow * 0.35
			end
			paint:setColor(color)
			canvas:drawText(hint.text, hx, hy, paint)
		end
	end

	local cornerInset = inset + arrow * 0.55
	for _, hint in ipairs(DIAGONAL_HINTS) do
		if hasGestureCommand(self.data, hint.field) then
			local isLeft = (hint.dir == "upleft" or hint.dir == "downleft")
			local isTop = (hint.dir == "upleft" or hint.dir == "upright")
			local hx = isLeft and (left + cornerInset) or (right - cornerInset)
			local hy = isTop and (top + cornerInset) or (bottom - cornerInset)
			drawDirectionArrow(canvas, paint, hx, hy, hint.dir, arrow * 0.95, color)
		end
	end

	if hasGestureCommand(self.data, "holdCommand") then
		paint:setColor(Color:argb(170, 0xFF, 0xFF, 0x66))
		paint:setTextSize(math.max(7 * self.density, arrow * 1.2))
		canvas:drawText("Hold", right - 16 * self.density, bottom - 4 * self.density, paint)
	end
end

-- Translucent arrow across the middle of the tile showing which swipe direction
-- the finger is currently aimed at, so a corner gesture can be told apart from a
-- straight one before letting go. `direction` is the direction that would
-- actually fire, so the arrow never promises something the release will not do.
-- Callout above the tile naming what the current gesture would send.
--
-- Sized from the text, with a floor of a bit wider and taller than the tile, so
-- it reads as a label about the button rather than part of it. Flips below the
-- tile when there is no room above, and is nudged sideways to stay on screen —
-- a hint that runs off the edge helps nobody.
function BUTTON:drawGestureLabel(canvas, text, screenWidth)
	if text == nil or text == "" then
		return
	end
	if self.data.showGestureLabel == false or buttonShowGestureLabels == false then
		return
	end
	local d = self.density
	local rect = self.rect
	local paint = self.paintOpts
	local previousStyle = paint:getStyle()
	local label = text
	if #label > 48 then
		label = string.sub(label, 1, 47) .. "…"
	end

	paint:setStyle(PaintStyle.FILL)
	paint:setTextSize(math.max(11 * d, 12))
	local textW = paint:measureText(label)
	local padX, padY = 8 * d, 6 * d
	local tileW = rectRight(rect) - rectLeft(rect)
	local tileH = rectBottom(rect) - rectTop(rect)
	local boxW = math.max(textW + padX * 2, tileW * 1.25)
	local boxH = math.max(paint:getTextSize() + padY * 2, tileH * 0.55)

	local cx = (rectLeft(rect) + rectRight(rect)) * 0.5
	local left = cx - boxW * 0.5
	local gap = 6 * d
	local top = rectTop(rect) - gap - boxH
	if top < gap then
		-- No room above: sit below the tile instead.
		top = rectBottom(rect) + gap
	end
	if screenWidth ~= nil and screenWidth > 0 then
		if left < gap then
			left = gap
		end
		if left + boxW > screenWidth - gap then
			left = screenWidth - gap - boxW
		end
	end

	local box = luajava.newInstance("android.graphics.RectF")
	box:set(left, top, left + boxW, top + boxH)
	paint:setColor(Color:argb(232, 0x14, 0x14, 0x18))
	canvas:drawRoundRect(box, 6 * d, 6 * d, paint)
	paint:setColor(Color:argb(150, 0x88, 0xCC, 0xFF))
	paint:setStyle(PaintStyle.STROKE)
	canvas:drawRoundRect(box, 6 * d, 6 * d, paint)

	paint:setStyle(PaintStyle.FILL)
	paint:setColor(Color:argb(255, 0xEE, 0xEE, 0xEE))
	canvas:drawText(label,
			left + (boxW - textW) * 0.5,
			top + boxH * 0.5 + paint:getTextSize() * 0.36,
			paint)
	paint:setStyle(previousStyle)
end

function BUTTON:drawSwipePreview(canvas, direction)
	if direction == nil then
		return
	end
	local rect = self.rect
	local cx = (rectLeft(rect) + rectRight(rect)) * 0.5
	local cy = (rectTop(rect) + rectBottom(rect)) * 0.5
	local size = math.min(rectRight(rect) - rectLeft(rect),
			rectBottom(rect) - rectTop(rect)) * 0.55
	drawDirectionArrow(canvas, self.paintOpts, cx, cy, direction, size,
			Color:argb(105, 0xFF, 0xFF, 0xFF))
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
	-- Centre the label on the rect, not on data.x/data.y. The rect is built by
	-- updateRectAt from whichever position this orientation is in force (see
	-- buttonwindow.posX), and it already carries statusoffset. Reading the
	-- portrait pair here meant that in landscape -- and for the whole of a move
	-- in the manager -- the tile moved and its label stayed behind.
	local cx = (rectLeft(rect) + rectRight(rect)) * 0.5
	local cy = (rectTop(rect) + rectBottom(rect)) * 0.5
	local tX = cx - (p:measureText(label)/2)
	local tY = cy + (p:getTextSize()/2)
	p:setTypeface(DEFAULT_BOLD_TYPEFACE)
	canvas:drawText(label,tX,tY,p)
	-- nil defaults to on; only explicit false/"false"/0 hides U/D/L/R, Hold, accordion badges.
	local showHints = true
	if buttonShowHints == false or buttonShowHints == "false"
			or buttonShowHints == 0 or buttonShowHints == "0" then
		showHints = false
	elseif buttonShowHints ~= nil
			and buttonShowHints ~= true
			and buttonShowHints ~= "true"
			and buttonShowHints ~= "1" then
		showHints = false
	end
	-- The profile-wide switch is a master: with it off nothing is drawn anywhere.
	-- With it on, each button decides for itself, so a pad can carry hints on the
	-- tiles that need them and stay clean everywhere else.
	if showHints and self.data.showGestureHints == false then
		showHints = false
	end
	if showHints and not self.isAccordionChild then
		self:drawGestureIndicators(canvas, p)
		if hasAccordionConfig(self.data) then
			drawAccordionChevron(canvas, p, rect, self.data.accordionDirection, self.expanded, self.density, self.data)
			drawAccordionTriggerBadge(canvas, p, rect, self.data.accordionDirection, getAccordionTrigger(self.data), self.density)
		end
		if self.expanded and self.data.accordionAutoClose == false then
			p:setColor(Color:argb(200, 0xFF, 0x66, 0x66))
			p:setTextSize(math.max(9 * self.density, indicatorSize(rect, self.density) * 1.5))
			canvas:drawText("x", rectLeft(rect) + 8 * self.density, rectTop(rect) + 11 * self.density, p)
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
