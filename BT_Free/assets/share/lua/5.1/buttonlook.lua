-- Button picture, label font tag, alignment, and avoid-text margin.
-- No luajava: the Lua tests load this file. Keep {font:} in step with
-- ButtonLabelPrefix.java.

local M = {}

-- Options → Button list order. The saved value is the index. Do not reorder.
M.FONT_LABELS = {
	"Android default",
	"Serif",
	"Sans serif",
	"Monospace",
	"DejaVu Sans Mono",
	"Fairfax HD",
	"JetBrains Mono",
	"Ubuntu Sans Mono",
	"Atkinson Hyperlegible Mono",
	"Inconsolata",
	"Liberation Mono",
	"Noto Sans Mono",
}

M.FONT_PATHS = {
	"default",
	"serif",
	"sans serif",
	"monospace",
	"fonts/DejaVuSansMono.ttf",
	"fonts/FairfaxHD.ttf",
	"fonts/JetBrainsMonoNL-Regular.ttf",
	"fonts/UbuntuSansMono-Regular.ttf",
	"fonts/AtkinsonHyperlegibleMono-Regular.ttf",
	"fonts/Inconsolata-Regular.ttf",
	"fonts/LiberationMono-Regular.ttf",
	"fonts/NotoSansMono-Regular.ttf",
}

M.STYLE_LABELS = { "Regular", "Bold", "Italic", "Bold Italic" }
M.STYLE_IDS = { "regular", "bold", "italic", "bolditalic" }

M.ALIGN_H_LABELS = { "Left", "Center", "Right" }
M.ALIGN_V_LABELS = { "Top", "Center", "Bottom" }

M.FACE_CATALOG = {
	{
		set = "marks",
		label = "Marks",
		icons = {
			{ id = "sword", label = "Sword" },
			{ id = "shield", label = "Shield" },
			{ id = "compass", label = "Compass" },
			{ id = "door", label = "Door" },
			{ id = "scroll", label = "Scroll" },
			{ id = "flask", label = "Flask" },
			{ id = "key", label = "Key" },
			{ id = "pack", label = "Pack" },
			{ id = "lantern", label = "Lantern" },
			{ id = "boot", label = "Boot" },
			{ id = "helm", label = "Helm" },
			{ id = "axe", label = "Axe" },
			{ id = "bow", label = "Bow" },
			{ id = "hammer", label = "Hammer" },
			{ id = "coin", label = "Coin" },
			{ id = "crown", label = "Crown" },
			{ id = "chest", label = "Chest" },
			{ id = "flag", label = "Flag" },
			{ id = "torch", label = "Torch" },
			{ id = "mug", label = "Mug" },
			{ id = "book", label = "Book" },
			{ id = "gem", label = "Gem" },
			{ id = "horn", label = "Horn" },
			{ id = "rope", label = "Rope" },
			{ id = "arrow", label = "Arrow" },
			{ id = "band", label = "Band" },
			{ id = "mapfold", label = "Map" },
			{ id = "anvil", label = "Anvil" },
		},
	},
	{
		set = "signals",
		label = "Signals",
		icons = {
			{ id = "star", label = "Star" },
			{ id = "sun", label = "Sun" },
			{ id = "moon", label = "Moon" },
			{ id = "bolt", label = "Bolt" },
			{ id = "drop", label = "Drop" },
			{ id = "gear", label = "Gear" },
			{ id = "eye", label = "Eye" },
			{ id = "cross", label = "Cross" },
			{ id = "heart", label = "Heart" },
			{ id = "flame", label = "Flame" },
			{ id = "target", label = "Target" },
			{ id = "bell", label = "Bell" },
			{ id = "mountain", label = "Mountain" },
			{ id = "tree", label = "Tree" },
			{ id = "snow", label = "Snow" },
			{ id = "hand", label = "Hand" },
			{ id = "lock", label = "Lock" },
			{ id = "wave", label = "Wave" },
			{ id = "diamond", label = "Diamond" },
			{ id = "spiral", label = "Spiral" },
			{ id = "comet", label = "Comet" },
			{ id = "leaf", label = "Leaf" },
			{ id = "anchor", label = "Anchor" },
			{ id = "fish", label = "Fish" },
			{ id = "bird", label = "Bird" },
			{ id = "house", label = "House" },
			{ id = "path", label = "Path" },
			{ id = "plus", label = "Plus" },
		},
	},
	{
		set = "relics",
		label = "Relics",
		icons = {
			{ id = "chalice", label = "Chalice" },
			{ id = "candle", label = "Candle" },
			{ id = "mask", label = "Mask" },
			{ id = "quill", label = "Quill" },
			{ id = "hourglass", label = "Hourglass" },
			{ id = "scales", label = "Scales" },
			{ id = "harp", label = "Harp" },
			{ id = "drum", label = "Drum" },
			{ id = "spear", label = "Spear" },
			{ id = "staff", label = "Staff" },
			{ id = "tablet", label = "Tablet" },
			{ id = "crystal", label = "Crystal" },
			{ id = "chain", label = "Chain" },
			{ id = "banner", label = "Banner" },
			{ id = "tent", label = "Tent" },
			{ id = "claw", label = "Claw" },
			{ id = "wing", label = "Wing" },
			{ id = "pipe", label = "Pipe" },
			{ id = "well", label = "Well" },
			{ id = "cart", label = "Cart" },
			{ id = "bell", label = "Bell" },
			{ id = "nest", label = "Nest" },
			{ id = "egg", label = "Egg" },
			{ id = "mushroom", label = "Mushroom" },
			{ id = "goblet", label = "Goblet" },
			{ id = "rune", label = "Rune" },
			{ id = "horseshoe", label = "Horseshoe" },
			{ id = "censer", label = "Censer" },
		},
	},
	{
		set = "wards",
		label = "Wards",
		icons = {
			{ id = "hex", label = "Hex" },
			{ id = "knot", label = "Knot" },
			{ id = "gate", label = "Gate" },
			{ id = "arch", label = "Arch" },
			{ id = "tower", label = "Tower" },
			{ id = "pillar", label = "Pillar" },
			{ id = "bridge", label = "Bridge" },
			{ id = "wall", label = "Wall" },
			{ id = "stair", label = "Stair" },
			{ id = "seal", label = "Seal" },
			{ id = "bars", label = "Bars" },
			{ id = "chevron", label = "Chevron" },
			{ id = "spiral", label = "Spiral" },
			{ id = "window", label = "Window" },
			{ id = "links", label = "Links" },
			{ id = "crest", label = "Crest" },
			{ id = "rune", label = "Rune" },
			{ id = "ring", label = "Ring" },
			{ id = "diamond", label = "Diamond" },
			{ id = "nova", label = "Nova" },
			{ id = "spark", label = "Spark" },
			{ id = "keyhole", label = "Keyhole" },
			{ id = "north", label = "North" },
			{ id = "dome", label = "Dome" },
			{ id = "fence", label = "Fence" },
			{ id = "post", label = "Post" },
			{ id = "portal", label = "Portal" },
			{ id = "maze", label = "Maze" },
		},
	},
	{
		set = "embers",
		label = "Embers",
		icons = {
			{ id = "brazier", label = "Brazier" },
			{ id = "coal", label = "Coal" },
			{ id = "forge", label = "Forge" },
			{ id = "tongs", label = "Tongs" },
			{ id = "bellows", label = "Bellows" },
			{ id = "cauldron", label = "Cauldron" },
			{ id = "smoke", label = "Smoke" },
			{ id = "hearth", label = "Hearth" },
			{ id = "poker", label = "Poker" },
			{ id = "kindling", label = "Kindling" },
			{ id = "skillet", label = "Skillet" },
			{ id = "chimney", label = "Chimney" },
			{ id = "bonfire", label = "Bonfire" },
			{ id = "wick", label = "Wick" },
			{ id = "spark", label = "Spark" },
			{ id = "ash", label = "Ash" },
			{ id = "log", label = "Log" },
			{ id = "kettle", label = "Kettle" },
			{ id = "spit", label = "Spit" },
			{ id = "grill", label = "Grill" },
			{ id = "pot", label = "Pot" },
			{ id = "match", label = "Match" },
			{ id = "flare", label = "Flare" },
			{ id = "cinder", label = "Cinder" },
			{ id = "oven", label = "Oven" },
			{ id = "brand", label = "Brand" },
			{ id = "pyre", label = "Pyre" },
			{ id = "glow", label = "Glow" },
		},
	},
	{
		set = "voyage",
		label = "Voyage",
		icons = {
			{ id = "sail", label = "Sail" },
			{ id = "hull", label = "Hull" },
			{ id = "wheel", label = "Wheel" },
			{ id = "sextant", label = "Sextant" },
			{ id = "oar", label = "Oar" },
			{ id = "dock", label = "Dock" },
			{ id = "buoy", label = "Buoy" },
			{ id = "net", label = "Net" },
			{ id = "gaff", label = "Gaff" },
			{ id = "island", label = "Island" },
			{ id = "lighthouse", label = "Lighthouse" },
			{ id = "porthole", label = "Porthole" },
			{ id = "barrel", label = "Barrel" },
			{ id = "crate", label = "Crate" },
			{ id = "spyglass", label = "Spyglass" },
			{ id = "chart", label = "Chart" },
			{ id = "pennant", label = "Pennant" },
			{ id = "mast", label = "Mast" },
			{ id = "rudder", label = "Rudder" },
			{ id = "cabin", label = "Cabin" },
			{ id = "tide", label = "Tide" },
			{ id = "gull", label = "Gull" },
			{ id = "shell", label = "Shell" },
			{ id = "pearl", label = "Pearl" },
			{ id = "bottle", label = "Bottle" },
			{ id = "pier", label = "Pier" },
			{ id = "beacon", label = "Beacon" },
			{ id = "tiller", label = "Tiller" },
		},
	},
}

local function lower(s)
	return string.lower(tostring(s or ""))
end

local function styleName(raw)
	local t = lower(raw):match("^%s*(.-)%s*$")
	if t == "regular" or t == "bold" or t == "italic" or t == "bolditalic" then
		return t
	end
	if t == "bold-italic" or t == "bold italic" then
		return "bolditalic"
	end
	return nil
end

local function faceName(raw)
	local t = lower(raw):match("^%s*(.-)%s*$")
	if t == "serif" then
		return "serif"
	end
	if t == "sans" or t == "sans serif" or t == "sans-serif" or t == "sansserif" then
		return "sans serif"
	end
	if t == "monospace" or t == "mono" then
		return "monospace"
	end
	if t == "default" then
		return "default"
	end
	return nil
end

local function isPath(raw)
	local t = tostring(raw or ""):match("^%s*(.-)%s*$")
	if t == "" then
		return false
	end
	if string.find(t, "/", 1, true) or string.find(t, "\\", 1, true) then
		return true
	end
	local tail = lower(t)
	return string.sub(tail, -4) == ".ttf" or string.sub(tail, -4) == ".otf"
end

function M.parseFontTag(label)
	if label == nil then
		return { display = "", path = nil, style = nil }
	end
	label = tostring(label)
	if string.sub(label, 1, 6) ~= "{font:" then
		return { display = label, path = nil, style = nil }
	end
	local endAt = string.find(label, "}", 7, true)
	if endAt == nil then
		return { display = label, path = nil, style = nil }
	end
	local token = string.sub(label, 7, endAt - 1):match("^%s*(.-)%s*$")
	if token == nil or token == "" then
		return { display = label, path = nil, style = nil }
	end
	local display = string.sub(label, endAt + 1)
	local style = nil
	local faceToken = token
	local colon = string.find(token, ":", 1, true)
	if colon ~= nil and colon > 1 then
		local headStyle = styleName(string.sub(token, 1, colon - 1))
		local rest = string.sub(token, colon + 1):match("^%s*(.-)%s*$")
		if headStyle ~= nil and rest ~= nil and rest ~= "" then
			style = headStyle
			faceToken = rest
		end
	end
	if style == nil and colon == nil then
		local asStyle = styleName(faceToken)
		if asStyle ~= nil then
			return { display = display, path = nil, style = asStyle }
		end
	end
	local asFace = faceName(faceToken)
	if asFace ~= nil then
		return { display = display, path = asFace, style = style }
	end
	if isPath(faceToken) then
		return { display = display, path = faceToken, style = style }
	end
	return { display = label, path = nil, style = nil }
end

function M.fontPathForIndex(index)
	local i = (tonumber(index) or 0) + 1
	return M.FONT_PATHS[i] or "default"
end

function M.fontStyleForIndex(index)
	local i = (tonumber(index) or 1) + 1
	return M.STYLE_IDS[i] or "bold"
end

function M.alignHForIndex(index)
	local i = tonumber(index) or 1
	if i == 0 then
		return "left"
	end
	if i == 2 then
		return "right"
	end
	return "center"
end

function M.alignVForIndex(index)
	local i = tonumber(index) or 1
	if i == 0 then
		return "top"
	end
	if i == 2 then
		return "bottom"
	end
	return "center"
end

function M.resolveFont(ownPath, ownStyle, tagPath, tagStyle, globalPath, globalStyle)
	local path = globalPath
	if path == nil or path == "" then
		path = "default"
	end
	if ownPath ~= nil and ownPath ~= "" then
		path = ownPath
	end
	if tagPath ~= nil and tagPath ~= "" then
		path = tagPath
	end
	local style = globalStyle
	if style == nil or style == "" then
		style = "bold"
	end
	if ownStyle ~= nil and ownStyle ~= "" then
		style = ownStyle
	end
	if tagStyle ~= nil and tagStyle ~= "" then
		style = tagStyle
	end
	return path, style
end

function M.effectiveAlign(own, globalValue)
	if own ~= nil and own ~= "" then
		return own
	end
	if globalValue ~= nil and globalValue ~= "" then
		return globalValue
	end
	return "center"
end

function M.labelLeft(left, right, textWidth, alignH, pad)
	if alignH == "left" then
		return left + pad
	end
	if alignH == "right" then
		return right - pad - textWidth
	end
	return (left + right) * 0.5 - textWidth * 0.5
end

function M.labelBaseline(top, bottom, ascent, descent, alignV, pad)
	if alignV == "top" then
		return top + pad - ascent
	end
	if alignV == "bottom" then
		return bottom - pad - descent
	end
	local mid = (top + bottom) * 0.5
	return mid - (ascent + descent) * 0.5
end

function M.labelBlockTop(top, bottom, blockHeight, alignV, pad)
	if alignV == "top" then
		return top + pad
	end
	if alignV == "bottom" then
		return bottom - pad - blockHeight
	end
	return (top + bottom) * 0.5 - blockHeight * 0.5
end

function M.participatesInAvoid(value)
	if value == nil then
		return true
	end
	if value == false or value == "false" or value == 0 or value == "0"
			or value == "no" or value == "off" then
		return false
	end
	return true
end

function M.clampMarginPx(value)
	local n = tonumber(value) or 0
	if n < 0 then
		n = 0
	end
	if n > 200 then
		n = 200
	end
	return math.floor(n)
end

-- force: a hole even when the window switch is off. skip: never a hole.
-- follow: key absent, the window switch decides.
function M.avoidMode(data)
	if M.avoidForced(data) then
		return "force"
	end
	if data ~= nil then
		local own = rawget(data, "avoidText")
		if own ~= nil and not M.participatesInAvoid(own) then
			return "skip"
		end
	end
	return "follow"
end

function M.avoidValueForMode(mode)
	if mode == "force" then
		return true
	end
	if mode == "skip" then
		return false
	end
	return nil
end

function M.avoidForced(data)
	if data == nil then
		return false
	end
	local own = rawget(data, "avoidText")
	return own == true or own == "true" or own == 1 or own == "1"
		or own == "yes" or own == "on"
end

function M.buttonParticipates(data)
	if data == nil then
		return true
	end
	local own = rawget(data, "avoidText")
	if own == nil then
		return true
	end
	return M.participatesInAvoid(own)
end

function M.buttonMarginPx(data)
	if data == nil then
		return 0
	end
	return M.clampMarginPx(rawget(data, "avoidMargin"))
end

-- 0–100. Missing means fully solid so an old button is unchanged.
function M.opacityPercent(raw)
	local p = tonumber(raw)
	if p == nil then
		return 100
	end
	if p < 0 then
		return 0
	end
	if p > 100 then
		return 100
	end
	return math.floor(p + 0.5)
end

-- Scales a colour's own alpha. 100 leaves it alone.
function M.mergedAlpha(colorAlpha, opacityPercent)
	local a = tonumber(colorAlpha) or 255
	if a < 0 then
		a = 0
	end
	if a > 255 then
		a = 255
	end
	local p = M.opacityPercent(opacityPercent)
	return math.floor(a * p / 100 + 0.5)
end

-- 0–255 as a percent, so the Opacity slider can show a pad that has no stored percent.
function M.alphaPercent(colorAlpha)
	local a = tonumber(colorAlpha) or 255
	if a < 0 then
		a = 0
	end
	if a > 255 then
		a = 255
	end
	return math.floor(a * 100 / 255 + 0.5)
end

function M.alphaPercentFromArgb(color)
	local n = tonumber(color)
	if n == nil then
		return 100
	end
	if n < 0 then
		n = n + 4294967296
	end
	return M.alphaPercent(math.floor(n / 16777216) % 256)
end

-- Button Opacity is the alpha. 100 is fully solid, not a second pass on the pad.
function M.opacityAlpha(percent)
	return M.mergedAlpha(255, percent)
end

-- While a swipe is aimed, the held and flip pictures stay off. The arrow is
-- the swipe mark. 0 idle, 1 held, 2 flip. Same numbers as ButtonFaces.
function M.pictureState(touchState, swipePreview)
	if swipePreview == true then
		return 0
	end
	local s = tonumber(touchState) or 0
	if s == 1 or s == 2 then
		return s
	end
	return 0
end

return M
