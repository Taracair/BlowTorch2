-- Which button .icon set names. No luajava: the Lua tests load this file.
-- One shown label wins. Otherwise one Name. Otherwise one id (b12).

local M = {}

local function hits(entries, token, field)
	local found = {}
	for i = 1, #entries do
		local value = entries[i][field]
		if value ~= nil and value ~= "" and value == token then
			found[#found + 1] = i
		end
	end
	return found
end

-- index, "label"|"name"|"id"   or   nil, "missing"|"labelled"|"named"|"called", count
function M.resolve(entries, token)
	if type(entries) ~= "table" or token == nil or token == "" then
		return nil, "missing", 0
	end
	token = tostring(token)
	local labelHits = hits(entries, token, "label")
	if #labelHits == 1 then
		return labelHits[1], "label"
	end
	local nameHits = hits(entries, token, "name")
	if #nameHits == 1 then
		return nameHits[1], "name"
	end
	local idHits = hits(entries, token, "id")
	if #idHits == 1 then
		return idHits[1], "id"
	end
	if #labelHits > 1 then
		return nil, "labelled", #labelHits
	end
	if #nameHits > 1 then
		return nil, "named", #nameHits
	end
	if #idHits > 1 then
		return nil, "called", #idHits
	end
	return nil, "missing", 0
end

return M
