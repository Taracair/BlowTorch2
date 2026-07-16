local M = {}

M.AUTO_TRACK = false
M.TILE_SIZE_DP = 36
M.MINIMAP_TILES = 5
M.MIN_TILE_DP = 18
M.STRIP_HEIGHT_DP = 220
M.HEADER_DP = 30
M.HEADER_BTN_DP = 26
M.MAX_QUICK = 4
M.MAX_GO_SLOTS = 4
M.COMPASS_DOCK_MAX_DP = 118
M.COMPASS_DOCK_MIN_DP = 90
M.COMPASS_DOCK_RATIO = 0.28
M.COMPASS_CELL_MIN_DP = 14
M.MAP_PANEL_RATIO = 0.62
M.PENDING_MOVE_MS = 4000

M.DIR_DELTA = {
	n = {0, -1, 0}, s = {0, 1, 0},
	e = {1, 0, 0}, w = {-1, 0, 0},
	u = {0, 0, 1}, d = {0, 0, -1},
	ne = {1, -1, 0}, nw = {-1, -1, 0},
	se = {1, 1, 0}, sw = {-1, 1, 0},
}

M.DEFAULT_UI = {
	tileSizeDp = 44,
	showLabels = true,
	showGrid = false,
	fogAlpha = 180,
	currentGlow = true,
	minimapHeightDp = 220,
	fullscreenOnOpen = false,
	accentColor = 0xFF55AAFF,
	exploredColor = 0xFF3D6A8C,
	currentColor = 0xFFFF9900,
	fogColor = 0xFF0E0E14,
	unmappedColor = 0xFF1A2230,
	adjacentBorderColor = 0xFF66BBFF,
	flagColors = {
		shop = 0xFF44AA66,
		danger = 0xFFCC3333,
		quest = 0xFFAA66CC,
		waypoint = 0xFFFFCC44,
		safe = 0xFF4488CC,
	},
}

-- Empty by default — never fire surprise MUD commands on first tap.
M.DEFAULT_GO_SLOTS = {
	{ label = "", cmd = "" },
	{ label = "", cmd = "" },
	{ label = "", cmd = "" },
	{ label = "", cmd = "" },
}

M.DEFAULT_GO_KEYS = {
	["in"] = { label = "in", cmd = "" },
	out = { label = "out", cmd = "" },
}

-- Compact header: place / note / (close drawn separately)
M.HEADER_BUTTONS = {
	{ id = "here", label = "+", action = "here" },
	{ id = "note", label = "N", action = "note" },
}

M.FLAG_ICONS = {
	shop = "$", danger = "!", quest = "?", waypoint = "*", safe = "+",
}

return M
