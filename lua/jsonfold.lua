#!/usr/bin/env lua
-- jsonfold.lua - hybrid pretty/compact JSON output for Lua.
--
-- Single-file Lua port of the Python jsonfold core.  It folds ordinary
-- pretty-printed JSON text by packing scalar runs, folding small containers,
-- optionally gridding repeated folded rows, then joining folded/scalar lines.
--
-- Public API:
--   local jsonfold = require("jsonfold")
--   jsonfold.fold_text(text, width, config)
--   jsonfold.format_json(data, width, config, opts)
--   jsonfold.write_json(data, fp, width, config, opts)
--   jsonfold.create_writer(fp, width, config, opts)
--   jsonfold.jsonfold_config(config, width, overrides)
--   jsonfold.demo_data()
--
-- OO API:
--   local fmt = jsonfold.JSONFold:new(width, config, opts)
--   fmt:fold(text)
--   fmt:format(data)
--   fmt:write(data, fp)
--
-- This module uses ljson.encode/ljson.decode by default for JSON I/O.
-- Folding already-pretty text works without ljson.

local M = {}


-- ---------------------------------------------------------------------------
-- Constants / helpers
-- ---------------------------------------------------------------------------

M.DEFAULT_WIDTH = 100
M.MAX_ARRAY_ITEMS = 1000
M.MAX_OBJ_ITEMS = 1000
M.MAX_NESTING = 10
M.MAX_GRID_LINES = 1000
M.MAX_WIDTH = 255

local DEFAULT_WIDTH = M.DEFAULT_WIDTH
local MAX_ARRAY_ITEMS = M.MAX_ARRAY_ITEMS
local MAX_OBJ_ITEMS = M.MAX_OBJ_ITEMS
local MAX_NESTING = M.MAX_NESTING
local MAX_GRID_LINES = M.MAX_GRID_LINES
local MAX_WIDTH = M.MAX_WIDTH

local function shallow_copy(t)
  if t == nil then return nil end
  local out = {}
  for k, v in pairs(t) do out[k] = v end
  return out
end

local function replace(base, overrides)
  if base == nil then return nil end
  local out = shallow_copy(base)
  if overrides then
    for k, v in pairs(overrides) do out[k] = v end
  end
  return out
end

local function count_newlines(s)
  local _, n = tostring(s):gsub("\n", "")
  return n
end

local function starts_numeric(s)
  local c = s:sub(1, 1)
  return c == "-" or (c >= "0" and c <= "9")
end

local function ljust(s, width)
  local n = width - #s
  if n <= 0 then return s end
  return s .. string.rep(" ", n)
end

local function rjust(s, width)
  local n = width - #s
  if n <= 0 then return s end
  return string.rep(" ", n) .. s
end

local function write_any(fp, s)
  if fp == nil then return #s end

  if type(fp) == "function" then
    fp(s)
    return #s
  end

  local ok, ret = pcall(function()
    return fp:write(s)
  end)

  if ok then
    return type(ret) == "number" and ret or #s
  end

  error("writer must be a function or an object with write(s): " .. tostring(ret))
end

local function flush_any(fp)
  if fp ~= nil then
    pcall(function() fp:flush() end)
  end
end

local function close_any(fp)
  if fp ~= nil then
    pcall(function() fp:close() end)
  end
end

local function read_all(path)
  local fp
  if path then
    local err
    fp, err = io.open(path, "r")
    if not fp then error(err) end
  else
    fp = io.stdin
  end
  local text = fp:read("*a")
  if path then fp:close() end
  return text
end

local function parse_bool_flag(args, i)
  return true, i
end

local function parse_int(v, name)
  local n = tonumber(v)
  if n == nil then error("invalid integer for " .. name .. ": " .. tostring(v)) end
  return math.floor(n)
end

local function split_lines_keepends(s)
  local lines = {}
  local pos = 1
  while true do
    local nl = s:find("\n", pos, true)
    if not nl then break end
    lines[#lines + 1] = s:sub(pos, nl)
    pos = nl + 1
  end
  if pos <= #s then lines[#lines + 1] = s:sub(pos) end
  if #s == 0 then return {} end
  return lines
end

local function add_luarocks_path()
  local home = os.getenv("HOME")
  if not home then return end

  package.path =
    home .. "/.luarocks/share/lua/5.1/?.lua;" ..
    home .. "/.luarocks/share/lua/5.1/?/init.lua;" ..
    package.path

  package.cpath =
    home .. "/.luarocks/lib/lua/5.1/?.so;" ..
    package.cpath
end

add_luarocks_path()

local function load_json()
  local ok, json = pcall(require, "dkjson")
  if ok then return json end
  error("JSON encoder not found. Install dkjson or pass opts.json.")
end

local function json_encode(data, opts)
    opts = opts or {}

    local json = opts.json or load_json()

    local text = json.encode(data, {
        indent = true,
        keyorder = opts.sort_keys and {} or nil,
    })

    assert(type(text) == "string",
        "dkjson.encode() did not return a string")

    return text
end
M.encode_json = json_encode

local function json_decode(text, opts)
  opts = opts or {}
  local json = opts.json or load_json()

  local obj, pos, err = json.decode(text)
  if err then
    error(err)
  end
  return obj
end

M.decode_json = json_decode

-- ---------------------------------------------------------------------------
-- Configuration
-- ---------------------------------------------------------------------------

local JSONFoldConfig = {}
JSONFoldConfig.__index = JSONFoldConfig
M.JSONFoldConfig = JSONFoldConfig

function JSONFoldConfig:new(opts)
  opts = opts or {}
  local o = {
    width = opts.width or DEFAULT_WIDTH,

    pack_array_items = opts.pack_array_items or 10,
    pack_obj_items   = opts.pack_obj_items   or 5,
    pack_nesting     = opts.pack_nesting     or 1,

    fold_array_items = opts.fold_array_items or 10,
    fold_obj_items   = opts.fold_obj_items   or 5,
    fold_nesting     = opts.fold_nesting     or 2,

    grid_array_items = opts.grid_array_items or MAX_ARRAY_ITEMS,
    grid_obj_items   = opts.grid_obj_items   or MAX_OBJ_ITEMS,
    grid_min_lines   = opts.grid_min_lines   or 3,
    grid_max_lines   = opts.grid_max_lines   or 100,
    grid_array_min   = opts.grid_array_min   or 3,
    grid_obj_min     = opts.grid_obj_min     or 3,

    join_array_items = opts.join_array_items or 8,
    join_obj_items   = opts.join_obj_items   or 4,
    join_nesting     = opts.join_nesting     or 1,
  }
  return setmetatable(o, self)
end

function JSONFoldConfig:replace(overrides)
  return JSONFoldConfig:new(replace(self, overrides or {}))
end

function JSONFoldConfig.preset(name)
  name = name or ""
  if JSONFoldConfig.PRESETS[name] == nil and name ~= "off" then
    error("unknown JSONFold preset: " .. tostring(name))
  end
  return JSONFoldConfig.PRESETS[name]
end

function JSONFoldConfig.resolve(base_config, width, overrides)
  local cfg = base_config
  if cfg == nil then cfg = "" end
  if type(cfg) == "string" then
    cfg = JSONFoldConfig.preset(cfg)
  elseif type(cfg) == "table" and getmetatable(cfg) ~= JSONFoldConfig then
    cfg = JSONFoldConfig:new(cfg)
  end
  if cfg == nil then return nil end
  local ov = shallow_copy(overrides or {})
  if width ~= nil then ov.width = width end
  if next(ov) ~= nil then cfg = cfg:replace(ov) end
  return cfg
end

function JSONFoldConfig:__tostring()
  local fields = {
    "width", "pack_array_items", "pack_obj_items", "pack_nesting",
    "fold_array_items", "fold_obj_items", "fold_nesting",
    "grid_array_items", "grid_obj_items", "grid_min_lines", "grid_max_lines",
    "grid_array_min", "grid_obj_min",
    "join_array_items", "join_obj_items", "join_nesting",
  }
  local parts = {}
  for _, k in ipairs(fields) do parts[#parts + 1] = k .. "=" .. tostring(self[k]) end
  return "JSONFoldConfig(" .. table.concat(parts, ", ") .. ")"
end

local base_cfg = JSONFoldConfig:new()
local none_cfg = JSONFoldConfig:new({
  pack_array_items = 0, pack_obj_items = 0, pack_nesting = 0,
  fold_array_items = 0, fold_obj_items = 0, fold_nesting = 0,
  grid_array_items = 0, grid_obj_items = 0, grid_min_lines = 0,
  grid_max_lines = 0, grid_array_min = 0, grid_obj_min = 0,
  join_array_items = 0, join_obj_items = 0, join_nesting = 0,
})
JSONFoldConfig.DEFAULT = base_cfg
JSONFoldConfig.NONE = none_cfg
JSONFoldConfig.PRESETS = {
  ["off"] = nil,
  [""] = base_cfg,
  ["default"] = base_cfg,
  ["none"] = none_cfg,
  ["low"] = base_cfg:replace({ fold_nesting = 0, join_nesting = 0, grid_max_lines = 0 }),
  ["med"] = base_cfg:replace({ join_nesting = 0, grid_max_lines = 0 }),
  ["classic"] = base_cfg:replace({ grid_max_lines = 0 }),
  ["high"] = base_cfg:replace({
    pack_array_items = 20, pack_obj_items = 10, pack_nesting = 4,
    fold_array_items = 20, fold_obj_items = 10, fold_nesting = 4,
    grid_array_min = 4, grid_obj_min = 4,
    join_array_items = 16, join_obj_items = 8, join_nesting = 2,
  }),
  ["max"] = base_cfg:replace({
    width = MAX_WIDTH,
    pack_array_items = MAX_ARRAY_ITEMS, pack_obj_items = MAX_OBJ_ITEMS, pack_nesting = MAX_NESTING,
    fold_array_items = MAX_ARRAY_ITEMS, fold_obj_items = MAX_OBJ_ITEMS, fold_nesting = MAX_NESTING,
    grid_array_items = MAX_ARRAY_ITEMS, grid_obj_items = MAX_OBJ_ITEMS,
    grid_min_lines = 3, grid_max_lines = MAX_GRID_LINES, grid_array_min = 4, grid_obj_min = 4,
    join_array_items = MAX_ARRAY_ITEMS, join_obj_items = MAX_OBJ_ITEMS, join_nesting = MAX_NESTING,
  }),
  ["pack"] = none_cfg:replace({
    pack_array_items = MAX_ARRAY_ITEMS, pack_obj_items = MAX_OBJ_ITEMS, pack_nesting = MAX_NESTING,
  }),
  ["fold"] = none_cfg:replace({
    fold_array_items = MAX_ARRAY_ITEMS, fold_obj_items = MAX_OBJ_ITEMS, fold_nesting = MAX_NESTING,
  }),
  ["grid"] = none_cfg:replace({
    pack_array_items = MAX_ARRAY_ITEMS, pack_obj_items = MAX_OBJ_ITEMS, pack_nesting = MAX_NESTING,
    fold_array_items = MAX_ARRAY_ITEMS, fold_obj_items = MAX_OBJ_ITEMS, fold_nesting = MAX_NESTING,
    grid_array_items = MAX_ARRAY_ITEMS, grid_obj_items = MAX_OBJ_ITEMS,
    grid_min_lines = 3, grid_max_lines = MAX_GRID_LINES,
  }),
  ["join"] = none_cfg:replace({
    fold_array_items = MAX_ARRAY_ITEMS, fold_obj_items = MAX_OBJ_ITEMS, fold_nesting = MAX_NESTING,
    join_array_items = MAX_ARRAY_ITEMS, join_obj_items = MAX_OBJ_ITEMS, join_nesting = MAX_NESTING,
  }),
}

-- ---------------------------------------------------------------------------
-- Internal data structures
-- ---------------------------------------------------------------------------

local Kind = { NONE = 0, DICT = 1, LIST = 2 }
M.Kind = Kind

local CLOSING_KIND = {
  ["}"] = Kind.DICT, ["},"] = Kind.DICT,
  ["]"] = Kind.LIST, ["],"] = Kind.LIST,
}

local Line = {}
Line.__index = Line
M.Line = Line

function Line:new(opts)
  opts = opts or {}
  local parts = opts.parts or {}
  local parts_length = opts.parts_length
  if parts_length == nil then parts_length = Line._calc_parts_length(parts) end
  local o = {
    indent = opts.indent or 0,
    parts = parts,
    parts_length = parts_length,
    kind = opts.kind or Kind.NONE,
    items = opts.items == nil and 1 or opts.items,
    leafs = opts.leafs == nil and 1 or opts.leafs,
    child_nesting = opts.child_nesting == nil and -1 or opts.child_nesting,
    opener = opts.opener or Kind.NONE,
    closer = opts.closer or Kind.NONE,
    can_pack = opts.can_pack ~= false,
    can_join = opts.can_join ~= false,
    can_grid = opts.can_grid == true,
  }
  return setmetatable(o, self)
end

function Line._calc_parts_length(parts)
  if not parts or #parts == 0 then return 0 end
  local total = 0
  for _, part in ipairs(parts) do total = total + #part + 1 end
  return total - 1
end

function Line.parse(s)
  s = s or ""
  local stripped = s:match("^%s*(.*)$") or ""
  local body = stripped:gsub("%s+$", "")
  local indent = #s - #stripped
  local opener = Kind.NONE
  if body:sub(-1) == "{" then opener = Kind.DICT
  elseif body:sub(-1) == "[" then opener = Kind.LIST end
  local closer = CLOSING_KIND[body] or Kind.NONE
  local is_body_line = opener == Kind.NONE and closer == Kind.NONE
  return Line:new({
    indent = indent,
    parts = { body },
    parts_length = #body,
    opener = opener,
    closer = closer,
    can_join = is_body_line,
    can_pack = is_body_line,
    items = is_body_line and 1 or 0,
    leafs = is_body_line and 1 or 0,
  })
end

function Line:raw()
  return string.rep(" ", self.indent) .. table.concat(self.parts, " ") .. "\n"
end

function Line:width()
  return self.indent + self.parts_length
end

function Line:can_merge(other, item_limit, width_limit)
  return self.indent == other.indent
     and self.items + other.items <= item_limit
     and self.indent + self.parts_length + 1 + other.parts_length <= width_limit
end

function Line:merge_line(other)
  for _, part in ipairs(other.parts) do self.parts[#self.parts + 1] = part end
  if #other.parts > 0 then self.parts_length = self.parts_length + 1 + other.parts_length end
  self.items = self.items + other.items
  self.leafs = self.leafs + other.leafs
  if other.child_nesting > self.child_nesting then
    self.child_nesting = other.child_nesting
    self.can_pack = false
  end
end

function Line:set_parts(parts)
  self.parts = parts
  self.parts_length = Line._calc_parts_length(parts)
end

local function key_prefix(part)
  -- Good-enough equivalent of Python KEY_RE for JSON object rows:
  -- leading whitespace + quoted/simple key + colon + following whitespace.
  local p = part:match('^%s*"[^"\\]*"%s*:')
  if p then return p end
  p = part:match("^%s*'[^'\\]*'%s*:")
  if p then return p end
  p = part:match("^%s*[A-Za-z_$][A-Za-z0-9_$]*%s*:")
  return p
end

function Line:dict_signature()
  local sig = {}
  for i = 2, #self.parts - 1 do
    local p = key_prefix(self.parts[i])
    if not p then return nil end
    sig[#sig + 1] = p
  end
  return table.concat(sig, "\0")
end

function Line._format_parts(parts, widths)
  local out = {}
  local last = #widths
  for i, part in ipairs(parts) do
    if starts_numeric(part) then
      out[i] = rjust(part, widths[i])
    elseif i < last then
      out[i] = ljust(part, widths[i])
    else
      out[i] = part
    end
  end
  return out
end

function Line:apply_grid(widths)
  self:set_parts(Line._format_parts(self.parts, widths))
end

local Frame = {}
Frame.__index = Frame
M.Frame = Frame

function Frame:new(opts)
  opts = opts or {}
  local o = {
    kind = opts.kind or Kind.NONE,
    indent = opts.indent or 0,
    depth = opts.depth or 0,
    lines = opts.lines or {},
    parts_length = opts.parts_length or 0,
    pack_limit = opts.pack_limit or 0,
    fold_limit = opts.fold_limit or 0,
    join_limit = opts.join_limit or 0,
    grid_limit = opts.grid_limit or 0,
    grid_min_items = opts.grid_min_items or 0,
    content_lines = opts.content_lines or 0,
    items = opts.items or 0,
    leafs = opts.leafs or 0,
    fold_ok = opts.fold_ok ~= false,
    grid_ok = opts.grid_ok == true,
    child_nesting = opts.child_nesting == nil and -1 or opts.child_nesting,
  }
  return setmetatable(o, self)
end

function Frame:update_stats(line)
  self.leafs = self.leafs + line.leafs
  self.items = self.items + line.items
  self.parts_length = self.parts_length + line.parts_length + (self.parts_length ~= 0 and 1 or 0)
  if line.child_nesting >= self.child_nesting then
    self.child_nesting = line.child_nesting + 1
  end
end

function Frame:add_line(line)
  self.lines[#self.lines + 1] = line
  if line.opener == Kind.NONE and line.closer == Kind.NONE then
    self.content_lines = self.content_lines + 1
  end
  self:update_stats(line)
end

function Frame:check_fold_limits(config)
  if self.parts_length > config.width then return false end
  if self.items > self.fold_limit then return false end
  if self.child_nesting >= config.fold_nesting then return false end
  return true
end

function Frame:fold_lines(cfg)
  local parts = {}
  for _, line in ipairs(self.lines) do
    for _, part in ipairs(line.parts) do parts[#parts + 1] = part end
  end
  local line = Line:new({
    indent = self.indent,
    parts = parts,
    parts_length = self.parts_length,
    kind = self.kind,
    items = 1,
    leafs = self.leafs,
    child_nesting = self.child_nesting,
    can_pack = false,
    can_join = self.child_nesting < cfg.join_nesting,
    can_grid = cfg.grid_max_lines > 0 and self.items <= self.grid_limit,
  })
  self.lines = { line }
end

function Frame:join_lines(cfg)
  local lines = self.lines
  local n = #lines
  if n < 2 then return end
  local prev = lines[1]
  local out = { prev }
  local removed = 0
  for i = 2, n do
    local line = lines[i]
    if prev.can_join and line.can_join and prev:can_merge(line, self.join_limit, cfg.width) then
      prev:merge_line(line)
      prev.can_pack = false
      removed = removed + 1
    else
      out[#out + 1] = line
      prev = line
    end
  end
  self.lines = out
  self.content_lines = self.content_lines - removed
end

local JSONFoldStats = {}
JSONFoldStats.__index = JSONFoldStats
M.JSONFoldStats = JSONFoldStats

function JSONFoldStats:new(opts)
  opts = opts or {}
  return setmetatable({
    bytes_in = opts.bytes_in or 0,
    bytes_out = opts.bytes_out or 0,
    lines_in = opts.lines_in or 0,
    lines_out = opts.lines_out or 0,
  }, self)
end

function JSONFoldStats:__tostring()
  return string.format("JSONFoldStats(bytes_in=%d, bytes_out=%d, lines_in=%d, lines_out=%d)",
    self.bytes_in, self.bytes_out, self.lines_in, self.lines_out)
end

-- ---------------------------------------------------------------------------
-- JSONFoldWriter
-- ---------------------------------------------------------------------------

local JSONFoldWriter = {}
JSONFoldWriter.__index = JSONFoldWriter
M.JSONFoldWriter = JSONFoldWriter

function JSONFoldWriter:new(fp, opts)
  opts = opts or {}
  local config = opts.config
  if config == nil then config = "" end
  if type(config) == "string" then config = JSONFoldConfig.PRESETS[config] end
  local o = {
    fp = fp,
    stats = JSONFoldStats:new(),
    cfg = config,
    pending = "",
    stack = {},
    do_close = opts.do_close or opts.doClose or false,
  }
  return setmetatable(o, self)
end

function JSONFoldWriter:_write_str(s)
  local n = write_any(self.fp, s)
  self.stats.bytes_out = self.stats.bytes_out + n
  self.stats.lines_out = self.stats.lines_out + count_newlines(s)
  return n
end

function JSONFoldWriter:_write_line(line)
  return self:_write_str(line:raw())
end

function JSONFoldWriter:_choose_limit(kind, list_limit, dict_limit, default)
  if kind == Kind.LIST then return list_limit end
  if kind == Kind.DICT then return dict_limit end
  return default or 0
end

function JSONFoldWriter:_pack_limit(kind)
  return self:_choose_limit(kind, self.cfg.pack_array_items, self.cfg.pack_obj_items)
end

function JSONFoldWriter:_fold_limit(kind)
  return self:_choose_limit(kind, self.cfg.fold_array_items, self.cfg.fold_obj_items)
end

function JSONFoldWriter:_join_limit(kind)
  return self:_choose_limit(kind, self.cfg.join_array_items, self.cfg.join_obj_items)
end

function JSONFoldWriter:_grid_limit(kind)
  return self:_choose_limit(kind, self.cfg.grid_array_items, self.cfg.grid_obj_items)
end

function JSONFoldWriter:_grid_min_items(kind)
  return self:_choose_limit(kind, self.cfg.grid_array_min, self.cfg.grid_obj_min)
end

function JSONFoldWriter:write(s)
  s = tostring(s or "")
  local s_len = #s
  self.stats.bytes_in = self.stats.bytes_in + s_len

  if not self.cfg then
    self.stats.lines_in = self.stats.lines_in + count_newlines(s)
    return self:_write_str(s)
  end

  local nl_pos = s:find("\n", 1, true)
  if not nl_pos then
    self.pending = self.pending .. s
    return s_len
  end

  local nl2_pos = s:find("\n", nl_pos + 1, true)
  if not nl2_pos then
    self.stats.lines_in = self.stats.lines_in + 1
    local s2 = self.pending .. s:sub(1, nl_pos - 1)
    self.pending = s:sub(nl_pos + 1)
    self:_feed(Line.parse(s2))
    return s_len
  end

  local parts = split_lines_keepends(s)
  self.stats.lines_in = self.stats.lines_in + count_newlines(s)

  if self.pending ~= "" and #parts > 0 then
    parts[1] = self.pending .. parts[1]
    self.pending = ""
  end

  if #parts > 0 and parts[#parts]:sub(-1) ~= "\n" then
    self.pending = table.remove(parts)
  end

  for _, part in ipairs(parts) do
    self:_feed(Line.parse(part:sub(1, -2)))
  end

  return s_len
end

function JSONFoldWriter:finish()
  if self.pending ~= "" then
    self:_feed(Line.parse(self.pending))
    self.pending = ""
  end
  for _, frame in ipairs(self.stack) do
    for _, line in ipairs(frame.lines) do self:_write_line(line) end
  end
  self.stack = {}
end

function JSONFoldWriter:flush()
  self:finish()
  flush_any(self.fp)
end

function JSONFoldWriter:close()
  self:finish()
  flush_any(self.fp)
  if self.do_close then close_any(self.fp) end
end

function JSONFoldWriter:_feed(line)
  local opener = line.opener
  if opener ~= Kind.NONE then
    local frame = Frame:new({
      kind = opener,
      indent = line.indent,
      depth = #self.stack,
      pack_limit = self:_pack_limit(opener),
      fold_limit = self:_fold_limit(opener),
      join_limit = self:_join_limit(opener),
      grid_limit = self:_grid_limit(opener),
      grid_min_items = self:_grid_min_items(opener),
    })
    frame:add_line(line)
    self.stack[#self.stack + 1] = frame
    return
  end

  if #self.stack == 0 then
    self:_write_line(line)
    return
  end

  local frame = self.stack[#self.stack]
  local closer = line.closer
  if closer ~= Kind.NONE then
    if frame.kind ~= closer then
      frame.fold_ok = false
      frame.grid_ok = false
    end
    frame:add_line(line)
    self:_close_frame()
    return
  end

  if line.items >= frame.pack_limit then line.can_pack = false end
  if line.items >= frame.join_limit then line.can_join = false end
  self:_add_to_frame(frame, line)
end

function JSONFoldWriter:_emit_lines(lines, depth)
  if not lines or #lines == 0 then return end
  if depth == nil then depth = #self.stack - 1 end
  if depth < 0 then
    for _, line in ipairs(lines) do self:_write_line(line) end
    return
  end
  local frame = self.stack[depth + 1]
  for _, line in ipairs(lines) do self:_add_to_frame(frame, line) end
end

function JSONFoldWriter:_add_to_frame(frame, line)
  if #frame.lines > 0 then
    if not frame.grid_ok then
      local prev = frame.lines[#frame.lines]
      if line.can_pack and prev.can_pack and self:_try_pack(frame, prev, line) then return end
      if line.can_join and prev.can_join and self:_try_join(frame, prev, line) then return end
    end
  elseif not frame.fold_ok and not line.can_pack and not line.can_join then
    self:_write_line(line)
    return
  end

  frame:add_line(line)

  if frame.fold_ok and line:width() > self.cfg.width then self:_mark_no_fold() end

  if line.closer == Kind.NONE then
    if frame.fold_ok and not frame:check_fold_limits(self.cfg) then
      self:_mark_no_fold()
    end
    if frame.grid_ok and not line.can_grid then
      self:_mark_no_grid()
      frame:join_lines(self.cfg)
    end
  end

  if not frame.fold_ok and not frame.grid_ok then self:_stream_frame(frame) end
end

function JSONFoldWriter:_merge_into_frame(frame, prev, line)
  prev:merge_line(line)
  if prev.items >= frame.pack_limit or prev.child_nesting >= self.cfg.pack_nesting then
    prev.can_pack = false
  end
  if prev.items >= frame.join_limit or prev.child_nesting >= self.cfg.join_nesting then
    prev.can_join = false
  end
  frame:update_stats(line)
  if frame.fold_ok and not frame:check_fold_limits(self.cfg) then
    self:_mark_no_fold()
    self:_stream_frame(frame)
  end
end

function JSONFoldWriter:_try_pack(frame, prev, line)
  if frame.pack_limit <= 1 or not prev:can_merge(line, frame.pack_limit, self.cfg.width) then
    return false
  end
  self:_merge_into_frame(frame, prev, line)
  if not prev.can_pack then prev.can_join = false end
  return true
end

function JSONFoldWriter:_try_join(frame, prev, line)
  if frame.join_limit <= 1 or not prev:can_merge(line, frame.join_limit, self.cfg.width) then
    return false
  end
  self:_merge_into_frame(frame, prev, line)
  return true
end

function JSONFoldWriter:_close_frame()
  local frame = table.remove(self.stack)

  if frame.grid_ok then
    if self:_try_grid(frame) then
      self:_mark_no_grid()
    else
      self:_mark_no_grid()
      frame:join_lines(self.cfg)
      frame.fold_ok = frame:check_fold_limits(self.cfg)
    end
  end

  if frame.fold_ok and self:_try_fold(frame) then
    if #self.stack > 0 and frame.lines[1].can_grid then
      local parent_frame = self.stack[#self.stack]
      if parent_frame.content_lines == 0 then parent_frame.grid_ok = true end
    end
  end

  self:_emit_lines(frame.lines)
end

function JSONFoldWriter:_try_fold(frame)
  if not frame.fold_ok
      or frame.content_lines ~= 1
      or #frame.lines ~= 3
      or frame.indent + frame.parts_length > self.cfg.width then
    return false
  end
  frame:fold_lines(self.cfg)
  return true
end

function JSONFoldWriter:_try_grid(frame)
  if frame.kind ~= Kind.LIST then return false end
  local line_count = #frame.lines - 2
  if line_count < 2
      or line_count < self.cfg.grid_min_lines
      or line_count > self.cfg.grid_max_lines then
    return false
  end

  local lines = {}
  for i = 2, #frame.lines - 1 do lines[#lines + 1] = frame.lines[i] end
  local first_line = lines[1]
  local part_count = #first_line.parts
  if part_count < 4 or part_count - 2 < frame.grid_min_items then return false end

  for _, line in ipairs(lines) do
    if #line.parts ~= part_count then return false end
  end

  if first_line.kind == Kind.DICT then
    local sig = first_line:dict_signature()
    if not sig then return false end
    for _, line in ipairs(lines) do
      if line:dict_signature() ~= sig then return false end
    end
  end

  local widths = {}
  for i = 1, part_count do
    local maxw = 0
    for _, line in ipairs(lines) do
      if #line.parts[i] > maxw then maxw = #line.parts[i] end
    end
    widths[i] = maxw
  end

  local grided_length = -1
  for _, w in ipairs(widths) do grided_length = grided_length + 1 + w end
  if frame.lines[1].indent + grided_length > self.cfg.width then return false end

  for _, line in ipairs(lines) do
    line:apply_grid(widths)
    line.can_pack = false
    line.can_join = false
    line.can_grid = false
  end
  return true
end

function JSONFoldWriter:_stream_frame(frame)
  local lines = frame.lines
  if #lines == 0 then return end
  local last = lines[#lines]
  local keep_last = last.can_pack or last.can_join
  if keep_last then table.remove(lines) end
  self:_emit_lines(lines, frame.depth - 1)
  frame.lines = {}
  if keep_last then frame.lines[#frame.lines + 1] = last end
end

function JSONFoldWriter:_mark_no_fold()
  for _, frame in ipairs(self.stack) do frame.fold_ok = false end
end

function JSONFoldWriter:_mark_no_grid()
  for _, frame in ipairs(self.stack) do frame.grid_ok = false end
end

-- ---------------------------------------------------------------------------
-- Public OO API
-- ---------------------------------------------------------------------------

local JSONFold = {}
JSONFold.__index = JSONFold
M.JSONFold = JSONFold

function JSONFold:new(width, config, opts)
  opts = opts or {}
  local o = {
    width = width,
    config = JSONFoldConfig.resolve(config == nil and "" or config, width),
    do_close = opts.do_close or opts.doClose or false,
    indent = opts.indent or 2,
    gold = opts.gold ~= false,
    json = opts.json,
    sort_keys = opts.sort_keys or opts.sortKeys or false,
  }
  return setmetatable(o, self)
end

function JSONFold:fold(text)
  local buff = {}
  local fp = function(s) buff[#buff + 1] = s end
  local out = JSONFoldWriter:new(fp, { config = self.config })
  out:write(text)
  out:close()
  return table.concat(buff)
end

function JSONFold:format(data)
  local text = json_encode(data, {
    json = self.json,
    indent = self.indent,
    sort_keys = self.sort_keys,
  })
  if type(text) ~= "string" then return text end
  if text:sub(-1) ~= "\n" then text = text .. "\n" end
  return self:fold(text)
end

function JSONFold:write(data, fp)
  local text = json_encode(data, {
    json = self.json,
    indent = self.indent,
    sort_keys = self.sort_keys,
  })
  if type(text) ~= "string" then return text end
  if text:sub(-1) ~= "\n" then text = text .. "\n" end
  local out_text = self:fold(text)
  local stats = JSONFoldStats:new({
    bytes_in = #text,
    lines_in = count_newlines(text),
    bytes_out = #out_text,
    lines_out = count_newlines(out_text),
  })
  write_any(fp, out_text)
  return stats
end

-- ---------------------------------------------------------------------------
-- Functional API
-- ---------------------------------------------------------------------------

function M.jsonfold_config(base_config, width, overrides)
  return JSONFoldConfig.resolve(base_config == nil and "" or base_config, width, overrides)
end

M.config = M.jsonfold_config

function M.create_writer(fp, width, config, opts)
  opts = opts or {}
  return JSONFoldWriter:new(fp, {
    config = JSONFoldConfig.resolve(config == nil and "" or config, width),
    do_close = opts.do_close or opts.doClose or false,
  })
end

function M.fold_text(text, width, config)
  local fmt = JSONFold:new(width, config == nil and "" or config)
  return fmt:fold(text)
end

function M.format_json(data, width, config, opts)
  local fmt = JSONFold:new(width, config == nil and "" or config, opts or {})
  return fmt:format(data)
end

function M.write_json(data, fp, width, config, opts)
  local fmt = JSONFold:new(width, config == nil and "" or config, opts or {})
  return fmt:write(data, fp)
end

function M.demo_data()
  local long_array, wide_array, wide_object = {}, {}, {}
  for i = 1, 50 do long_array[i] = "a" .. i end
  for i = 1, 9 do wide_array[i] = "abcdefghijklmnopqrstuvwxyz" .. i end
  for i = 1, 9 do wide_object["abcdefghijk" .. i] = "lmnopqrstuvwxyz" .. i end
  return {
    meta = { version = 1, ok = true, name = "jsonfold demo" },
    ids = { 1, 2, 3, 4, 5, 6 },
    matrix = { {1, 20, "Red", 300}, {4000, 50, "Yellow", 6}, {70, 800, "Green", 9000} },
    items = {
      { id = 1, name = "alpha", qty = 12, size = "Medium" },
      { id = 20, name = "beta", qty = 3000, size = "Large" },
      { id = 300, name = "Charlie", qty = 4, size = "Tiny" },
    },
    long = {
      "this is a long message that may force the block to stay expanded",
      "second", "third", "fourth",
    },
    single_array = { 1 },
    single_object = { x = 2 },
    long_array = long_array,
    wide_array = wide_array,
    wide_object = wide_object,
  }
end

-- ---------------------------------------------------------------------------
-- Lightweight main, similar to Python jsonfold.py. Full option CLI belongs in cli.lua.
-- ---------------------------------------------------------------------------

local function usage()
  io.stderr:write([[Usage: lua jsonfold.lua [options]

Read JSON from stdin; write folded JSON to stdout.

Options:
  --demo                 use built-in demo data
  --compact NAME         preset: default, none, low, med, classic, high, max, pack, fold, grid, join, off
  --width N              line width limit
  --verbose, -v          print config/stats to stderr
  --input FILE, -i FILE  read JSON input from file
  --indent N             JSON indentation for ljson.encode (default 2)
  --sort-keys            request sorted keys from ljson.encode
  --help, -h             show this help
]])
end

local function parse_main_args(argv)
  local args = {
    demo = false,
    compact = "default",
    width = nil,
    verbose = false,
    input = nil,
    indent = 2,
    sort_keys = false,
  }
  local i = 1
  while i <= #argv do
    local a = argv[i]
    if a == "--help" or a == "-h" then
      args.help = true
    elseif a == "--demo" then
      args.demo = true
    elseif a == "--verbose" or a == "-v" then
      args.verbose = true
    elseif a == "--sort-keys" then
      args.sort_keys = true
    elseif a == "--compact" then
      i = i + 1; args.compact = argv[i] or error("--compact requires value")
    elseif a:match("^%-%-compact=") then
      args.compact = a:match("^%-%-compact=(.*)$")
    elseif a == "--width" then
      i = i + 1; args.width = parse_int(argv[i], "--width")
    elseif a:match("^%-%-width=") then
      args.width = parse_int(a:match("^%-%-width=(.*)$"), "--width")
    elseif a == "--input" or a == "-i" then
      i = i + 1; args.input = argv[i] or error(a .. " requires value")
    elseif a:match("^%-%-input=") then
      args.input = a:match("^%-%-input=(.*)$")
    elseif a == "--indent" then
      i = i + 1; args.indent = parse_int(argv[i], "--indent")
    elseif a:match("^%-%-indent=") then
      args.indent = parse_int(a:match("^%-%-indent=(.*)$"), "--indent")
    else
      error("unknown option: " .. tostring(a))
    end
    i = i + 1
  end
  return args
end

function M.main(argv)
  argv = argv or arg or {}
  local args = parse_main_args(argv)
  if args.help then usage(); return 0 end

  local cfg = M.jsonfold_config(args.compact)
  if args.verbose then io.stderr:write(tostring(cfg), "\n") end

  local data
  if args.demo then
    data = M.demo_data()
  else
    data = json_decode(read_all(args.input))
  end

  local stats = M.write_json(data, io.stdout, args.width, cfg, {
    indent = args.indent,
    sort_keys = args.sort_keys,
  })
  if args.verbose then io.stderr:write(tostring(stats), "\n") end
  return 0
end

-- If loaded through require(), return module. If executed directly, run main().
-- This conventional check works for `lua jsonfold.lua ...`; when required,
-- `...` is normally the module name and arg[0] belongs to the parent program.
local _is_direct = type(arg) == "table" and type(arg[0]) == "string"
  and (arg[0]:match("jsonfold%.lua$") or arg[0]:match("[/\\]jsonfold%.lua$"))

if _is_direct then
  local ok, err = pcall(function() os.exit(M.main(arg or {})) end)
  if not ok then
    io.stderr:write("jsonfold.lua: ", tostring(err), "\n")
    os.exit(1)
  end
end

return M
