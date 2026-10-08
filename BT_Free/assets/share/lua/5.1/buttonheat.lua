-- Button-use counts for one world. No Android.
-- File body is "v1" then one "key<TAB>count" line. Keys percent-encode
-- % \n \r \t so a label can hold those and the line split still works.
-- Linear in this tile's count over the hottest on the pad. 300 beside a
-- hottest of 500 is 0.6 of the dim-to-bright span. 0 uses stays at the dim end.

local M = {}

M.MIN_ALPHA = 32
M.MAX_ALPHA = 220
M.FILL_R = 255
M.FILL_G = 255
M.FILL_B = 255
-- Label flips to dark once the white fill is bright enough to hide white text.
M.LABEL_FLIP = 140

local function enc(s)
	s = tostring(s)
	s = string.gsub(s, "%%", "%%25")
	s = string.gsub(s, "\n", "%%0A")
	s = string.gsub(s, "\r", "%%0D")
	s = string.gsub(s, "\t", "%%09")
	return s
end

local function dec(s)
	s = string.gsub(s, "%%0A", "\n")
	s = string.gsub(s, "%%0D", "\r")
	s = string.gsub(s, "%%09", "\t")
	s = string.gsub(s, "%%25", "%%")
	return s
end

function M.total(counts, id)
	if type(counts) ~= "table" or id == nil then
		return 0
	end
	local prefix = tostring(id) .. "|"
	local n = #prefix
	local total = 0
	for key, v in pairs(counts) do
		if type(key) == "string" and string.sub(key, 1, n) == prefix then
			total = total + (tonumber(v) or 0)
		end
	end
	return total
end

function M.alpha(total, maxTotal)
	total = tonumber(total) or 0
	maxTotal = tonumber(maxTotal) or 0
	if total <= 0 or maxTotal <= 0 then
		return M.MIN_ALPHA
	end
	if total >= maxTotal then
		return M.MAX_ALPHA
	end
	local t = total / maxTotal
	local a = math.floor(M.MIN_ALPHA + t * (M.MAX_ALPHA - M.MIN_ALPHA) + 0.5)
	if a < M.MIN_ALPHA then
		return M.MIN_ALPHA
	end
	if a > M.MAX_ALPHA then
		return M.MAX_ALPHA
	end
	return a
end

function M.labelRgb(alpha)
	alpha = tonumber(alpha) or 0
	if alpha >= M.LABEL_FLIP then
		return 20, 20, 24
	end
	return 255, 255, 255
end

function M.encode(counts)
	local keys = {}
	if type(counts) == "table" then
		for k, n in pairs(counts) do
			n = tonumber(n)
			if type(k) == "string" and n ~= nil and n > 0 then
				keys[#keys + 1] = k
			end
		end
	end
	table.sort(keys)
	local lines = { "v1" }
	for i = 1, #keys do
		local n = math.floor(tonumber(counts[keys[i]]))
		lines[#lines + 1] = enc(keys[i]) .. "\t" .. tostring(n)
	end
	return table.concat(lines, "\n")
end

function M.decode(text)
	local out = {}
	if type(text) ~= "string" or text == "" then
		return out
	end
	local first = true
	for line in string.gmatch(text .. "\n", "(.-)\n") do
		if first then
			first = false
			if line ~= "v1" then
				return {}
			end
		elseif line ~= "" then
			local key, num = string.match(line, "^([^\t]+)\t(%d+)$")
			if key ~= nil then
				local n = tonumber(num)
				if n ~= nil and n > 0 then
					out[dec(key)] = n
				end
			end
		end
	end
	return out
end

-- world length, newline, world bytes, then the encode() body.
-- A world name can hold anything, including newlines.
function M.pack(world, body)
	world = tostring(world or "")
	if type(body) ~= "string" then
		body = ""
	end
	return string.format("%d\n", #world) .. world .. body
end

function M.unpack(payload)
	if type(payload) ~= "string" then
		return "", {}
	end
	local lenText, rest = string.match(payload, "^(%d+)\n(.*)$")
	if lenText == nil then
		return "", {}
	end
	local len = tonumber(lenText)
	if len == nil or len < 0 or len > #rest then
		return "", {}
	end
	local world = string.sub(rest, 1, len)
	local body = string.sub(rest, len + 1)
	return world, M.decode(body)
end

return M
