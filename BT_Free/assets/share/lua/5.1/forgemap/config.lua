local M = {}

M.TILE_SIZE_DP = 44
M.MINIMAP_TILES = 7
M.MAX_QUICK = 4
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
	showGrid = true,
	fogAlpha = 180,
	currentGlow = true,
	minimapHeightDp = 148,
	fullscreenOnOpen = false,
	accentColor = 0xFF2B6CB0,
	exploredColor = 0xFF3D5A73,
	currentColor = 0xFFFFAA33,
	fogColor = 0xFF1A1A22,
	flagColors = {
		shop = 0xFF44AA66,
		danger = 0xFFCC3333,
		quest = 0xFFAA66CC,
		waypoint = 0xFFFFCC44,
		safe = 0xFF4488CC,
	},
}

M.FLAG_ICONS = {
	shop = "$", danger = "!", quest = "?", waypoint = "*", safe = "+",
}

return M
