local M = {}

M.AUTO_TRACK = false
M.TILE_SIZE_DP = 36
M.MINIMAP_TILES = 5
M.MIN_TILE_DP = 18
M.STRIP_HEIGHT_DP = 210
M.HEADER_DP = 28
M.HEADER_BTN_DP = 24
M.MAX_QUICK = 4
M.MAX_GO_SLOTS = 4
M.COMPASS_DOCK_MAX_DP = 128
M.COMPASS_DOCK_MIN_DP = 96
M.COMPASS_DOCK_RATIO = 0.34
M.COMPASS_CELL_MIN_DP = 14
M.MAP_PANEL_RATIO = 0.58
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
	minimapHeightDp = 210,
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

M.DEFAULT_GO_SLOTS = {
	{ label = "ent", cmd = "enter" },
	{ label = "door", cmd = "open door" },
	{ label = "look", cmd = "look" },
	{ label = "", cmd = "" },
}

M.DEFAULT_GO_KEYS = {
	["in"] = { label = "in", cmd = "in" },
	out = { label = "out", cmd = "out" },
}

M.HEADER_BUTTONS = {
	{ id = "here", label = "+", action = "here" },
	{ id = "walk", label = "GO", action = "mode_walk" },
	{ id = "map", label = "MAP", action = "mode_map" },
}

M.FLAG_ICONS = {
	shop = "$", danger = "!", quest = "?", waypoint = "*", safe = "+",
}

return M
