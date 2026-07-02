#!/usr/bin/env lua
-- benchmark.lua - JSONFold Lua benchmark.
--
-- Port of benchmark.py.  Uses ljson.encode through jsonfold for JSON output.
-- Memory is estimated from collectgarbage("count") because Lua has no
-- tracemalloc-equivalent in the standard runtime.
--
local script = arg and arg[0] or ""
local dir = script:match("^(.*[/\\])") or "./"

package.path = dir .. "?.lua;" .. package.path

local jsonfold = require("jsonfold")

local REPEATS = 3

local NullWriter = {}
NullWriter.__index = NullWriter

function NullWriter:new(t0)
  return setmetatable({
    t0 = t0 or os.clock(),
    first_write = nil,
    bytes = 0,
    writes = 0,
  }, self)
end

function NullWriter:write(s)
  s = tostring(s or "")
  if self.first_write == nil then
    self.first_write = os.clock()
  end
  self.bytes = self.bytes + #s
  self.writes = self.writes + 1
  return #s
end

function NullWriter:ttfb_ms()
  if self.first_write == nil then return "" end
  return math.floor(((self.first_write - self.t0) * 1000) * 10 + 0.5) / 10
end

local function range_array(start_n, end_n)
  local t = {}
  for i = start_n, end_n do t[#t + 1] = i end
  return t
end

local function make_data(rows)
  local long_obj = {}
  for i = 0, 49 do long_obj["k" .. tostring(i)] = i end

  local row_list = {}
  for i = 0, rows - 1 do
    row_list[#row_list + 1] = {
      id = i,
      name = "name_" .. tostring(i),
      active = (i % 3 == 0),
      score = i * 1.25,
      tags = {"alpha", "beta", "gamma", "delta"},
      pos = {x = i, y = i + 1, z = i + 2},
      values = {i, i + 1, i + 2, i + 3, i + 4},
      pairs = { { i, i + 1, {i + 2, i + 3}, {i + 4, i + 5} } },
    }
  end

  return {
    meta = {version = 1, ok = true, name = "jsonfold benchmark"},
    long_ids = range_array(0, 99),
    long_obj = long_obj,
    rows = row_list,
  }
end

local function mem_label()
  return "kb"
end

local function mem_units(n)
  return math.floor(n * 10 + 0.5) / 10
end

local function encode_plain(data)
  return jsonfold.encode_json(data, {indent = nil})
end

local function encode_pretty(data)
  return jsonfold.encode_json(data, {indent = 2})
end

local function write_string(t0, s)
  local w = NullWriter:new(t0)
  w:write(s)
  return w
end

local function run_json_dump(data, t0)
  local w = NullWriter:new(t0)
  w:write(encode_pretty(data))
  return w
end

local function run_json_dump_plain(data, t0)
  local w = NullWriter:new(t0)
  w:write(encode_plain(data))
  return w
end

local function run_jsonfold_dump(data, t0, compact)
  local w = NullWriter:new(t0)
  jsonfold.write_json(data, w, nil, compact, {indent = 2})
  return w
end

local function run_case(data, name)
  if name == "base.dumps.plain" then
    return function(t0) return write_string(t0, encode_plain(data)) end
  end
  if name == "base.dumps.pretty" then
    return function(t0) return write_string(t0, encode_pretty(data)) end
  end
  if name == "base.dump.pretty" then
    return function(t0) return run_json_dump(data, t0) end
  end
  if name == "base.dump.plain" then
    return function(t0) return run_json_dump_plain(data, t0) end
  end

  local kind, func, compact = name:match("^([^.]+)%.([^.]+)%.([^.]+)$")
  if kind == "jf" then
    if func == "dumps" then
      return function(t0)
        return write_string(t0, jsonfold.format_json(data, nil, compact, {indent = 2}))
      end
    end
    if func == "dump" then
      return function(t0) return run_jsonfold_dump(data, t0, compact) end
    end
  end

  error("unknown benchmark case: " .. tostring(name))
end

local function time_one(name, data)
  local best = nil
  local best_dt = 0

  for _ = 1, REPEATS do
    collectgarbage("collect")
    local t0 = os.clock()
    local w = run_case(data, name)(t0)
    local t1 = os.clock()
    local dt = t1 - t0

    local row = {
      ["time(ms)"] = math.floor(dt * 1000 * 10 + 0.5) / 10,
      ["CPU(ms)"] = math.floor(dt * 1000 * 10 + 0.5) / 10,
      ["ttfb(ms)"] = w:ttfb_ms(),
      ["out(" .. mem_label() .. ")"] = mem_units(w.bytes / 1024),
      writes = w.writes,
    }

    if best == nil or dt < best_dt then
      best = row
      best_dt = dt
    end
  end

  return best_dt, best
end

local function memory_one(name, data)
  collectgarbage("collect")
  local before = collectgarbage("count")
  local t0 = os.clock()
  run_case(data, name)(t0)
  local after = collectgarbage("count")
  collectgarbage("collect")
  local delta = after - before
  if delta < 0 then delta = 0 end
  return mem_units(delta)
end

local function is_num(v)
  return type(v) == "number"
end

local function print_table(rows)
  if #rows == 0 then return end

  local cols = {"rows", "name", "time(ms)", "CPU(ms)", "ttfb(ms)", "out(" .. mem_label() .. ")", "writes", "peak(" .. mem_label() .. ")"}
  local widths = {}
  local numeric = {}

  for _, c in ipairs(cols) do
    widths[c] = #c
    numeric[c] = true
    for _, r in ipairs(rows) do
      local v = r[c]
      local s = tostring(v == nil and "" or v)
      if #s > widths[c] then widths[c] = #s end
      if not (is_num(v) or v == "" or v == nil) then numeric[c] = false end
    end
  end

  local function cell(c, v)
    local s = tostring(v == nil and "" or v)
    local pad = widths[c] - #s
    if numeric[c] then
      return " " .. string.rep(" ", pad) .. s .. " "
    end
    return " " .. s .. string.rep(" ", pad) .. " "
  end

  local parts = {}
  for _, c in ipairs(cols) do parts[#parts + 1] = string.rep("-", widths[c] + 2) end
  local line = "+" .. table.concat(parts, "+") .. "+"

  print(line)
  local head = {}
  for _, c in ipairs(cols) do head[#head + 1] = cell(c, c) end
  print("|" .. table.concat(head, "|") .. "|")
  print(line)
  for _, r in ipairs(rows) do
    local out = {}
    for _, c in ipairs(cols) do out[#out + 1] = cell(c, r[c]) end
    print("|" .. table.concat(out, "|") .. "|")
  end
  print(line)
end

local default_tests = {
  "base.dump.plain",
  "base.dump.pretty",
  "jf.dump.off",
  "jf.dump.none",
  "jf.dump.default",
  "jf.dump.low",
  "jf.dump.med",
  "jf.dump.classic",
  "jf.dump.high",
  "jf.dump.max",
  "jf.dump.pack",
  "jf.dump.fold",
  "jf.dump.grid",
  "jf.dump.join",
  "base.dumps.plain",
  "base.dumps.pretty",
  "jf.dumps.none",
  "jf.dumps.default",
  "jf.dumps.high",
  "jf.dumps.max",
}

local function run_one_size(rows, tests)
  local data = make_data(rows)
  if not tests or #tests == 0 then tests = default_tests end
  local results = {}

  for _, name in ipairs(tests) do
    io.stderr:write(string.format("%s (%d)... ", name, rows))
    io.stderr:flush()

    local t0 = os.clock()
    local _, speed = time_one(name, data)
    local peak = memory_one(name, data)
    local t1 = os.clock()

    io.stderr:write(tostring(math.floor((t1 - t0) * 1000 + 0.5)), " ms\n")

    local row = {
      rows = rows,
      name = name,
      ["peak(" .. mem_label() .. ")"] = peak,
    }
    for k, v in pairs(speed) do row[k] = v end
    results[#results + 1] = row
  end

  return results
end

local function show_data(rows)
  io.stdout:write(encode_pretty(make_data(rows)))
  io.stdout:write("\n")
end

local function usage()
  io.stderr:write([[Usage: lua benchmark.lua [--show N] [TEST ...] [ROWS ...]

Examples:
  lua benchmark.lua
  lua benchmark.lua 1000
  lua benchmark.lua jf.dump.default jf.dump.max 1000 10000
  lua benchmark.lua --show 10

Use '-' to clear the accumulated test filter, matching the Python benchmark.
]])
end

local function parse_args(argv)
  local args = {show = nil, rest = {}, help = false}
  local i = 1
  while i <= #argv do
    local a = argv[i]
    if a == "--help" or a == "-h" then
      args.help = true
    elseif a == "--show" then
      i = i + 1
      args.show = tonumber(argv[i]) or error("--show requires integer")
    elseif a:match("^%-%-show=") then
      args.show = tonumber(a:match("^%-%-show=(.*)$")) or error("--show requires integer")
    else
      args.rest[#args.rest + 1] = a
    end
    i = i + 1
  end
  return args
end

local function append_all(dst, src)
  for _, v in ipairs(src) do dst[#dst + 1] = v end
end

local function main(argv)
  argv = argv or arg or {}
  local args = parse_args(argv)
  if args.help then usage(); return 0 end

  if args.show then
    show_data(args.show)
    return 0
  end

  local t0 = os.clock()
  local filter = {}
  local last_sz = nil
  local results = {}

  for _, item in ipairs(args.rest) do
    if item == "-" then
      filter = {}
    else
      local n = tonumber(item)
      if n and n == math.floor(n) then
        last_sz = n
        append_all(results, run_one_size(last_sz, filter))
      else
        filter[#filter + 1] = item
      end
    end
  end

  if last_sz == nil then
    append_all(results, run_one_size(1000, filter))
  end

  local t1 = os.clock()
  print_table(results)
  io.stderr:write("completed in: ", tostring(math.floor((t1 - t0) * 10 + 0.5) / 10), "\n")
  return 0
end

local ok, err = pcall(function() os.exit(main(arg or {})) end)
if not ok then
  io.stderr:write("benchmark.lua: ", tostring(err), "\n")
  os.exit(1)
end
