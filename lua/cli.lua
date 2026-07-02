#!/usr/bin/env lua
-- cli.lua - full JSONFold Lua command-line wrapper.
--
-- Mirrors the Python cli.py option surface.  The core module jsonfold.lua also
-- has a lightweight main(); this file exposes all pack/fold/grid/join override
-- flags.

local script = arg and arg[0] or ""
local dir = script:match("^(.*[/\\])") or "./"

package.path = dir .. "?.lua;" .. package.path


local jsonfold = require("jsonfold")

local function read_all(path)
  local fp
  if path then
    local err
    fp, err = io.open(path, "rb")
    if not fp then error("cannot open input file " .. tostring(path) .. ": " .. tostring(err)) end
  else
    fp = io.stdin
  end
  local s = fp:read("*a") or ""
  if path then fp:close() end
  return s
end

local function parse_int(v, name)
  if v == nil then error(name .. " requires value") end
  local n = tonumber(v)
  if n == nil or n ~= math.floor(n) then
    error(name .. " requires integer value, got " .. tostring(v))
  end
  return n
end

local function set_pair(overrides, a, b, value)
  overrides[a] = value
  overrides[b] = value
end

local function usage()
  io.stderr:write([[Usage: lua cli.lua [options]

Read JSON from stdin; write folded JSON to stdout.

Basic options:
  --demo                       use built-in demo data
  --compact NAME               preset: default, none, low, med, classic, high, max, pack, fold, grid, join, off
  --width N                    line width limit
  --verbose, -v                print config/stats to stderr
  --input FILE, -i FILE        read JSON input from file instead of stdin
  --indent N                   JSON indentation for encode (default 2)
  --sort-keys                  request sorted keys from encode
  --help, -h                   show this help

Pack phase:
  --pack-items N               set both array/object pack item limits
  --pack-array-items N
  --pack-obj-items N
  --pack-nesting N

Fold phase:
  --fold-items N               set both array/object fold item limits
  --fold-array-items N
  --fold-obj-items N
  --fold-nesting N

Grid phase:
  --grid-items N               set both array/object grid item limits
  --grid-array-items N
  --grid-obj-items N
  --grid-min-lines N
  --grid-max-lines N

Join phase:
  --join-items N               set both array/object join item limits
  --join-array-items N
  --join-obj-items N
  --join-nesting N
]])
end

local option_to_field = {
  ["--pack-array-items"] = "pack_array_items",
  ["--pack-obj-items"] = "pack_obj_items",
  ["--pack-nesting"] = "pack_nesting",

  ["--fold-array-items"] = "fold_array_items",
  ["--fold-obj-items"] = "fold_obj_items",
  ["--fold-nesting"] = "fold_nesting",

  ["--grid-array-items"] = "grid_array_items",
  ["--grid-obj-items"] = "grid_obj_items",
  ["--grid-min-lines"] = "grid_min_lines",
  ["--grid-max-lines"] = "grid_max_lines",

  ["--join-array-items"] = "join_array_items",
  ["--join-obj-items"] = "join_obj_items",
  ["--join-nesting"] = "join_nesting",
}

local function normalize_long_option(a)
  return a:gsub("_", "-")
end

local function parse_args(argv)
  local args = {
    demo = false,
    compact = "default",
    width = nil,
    verbose = false,
    input = nil,
    indent = 2,
    sort_keys = false,
    help = false,
    overrides = {},
  }

  local i = 1
  while i <= #argv do
    local raw = argv[i]
    local a = normalize_long_option(raw)
    local eq_name, eq_val = a:match("^(%-%-[^=]+)=(.*)$")
    if eq_name then
      a, raw = eq_name, eq_name
    end

    local function next_value(name)
      if eq_val ~= nil then return eq_val end
      i = i + 1
      if i > #argv then error(name .. " requires value") end
      return argv[i]
    end

    if a == "--help" or a == "-h" then
      args.help = true
    elseif a == "--demo" then
      args.demo = true
    elseif a == "--verbose" or a == "-v" then
      args.verbose = true
    elseif a == "--sort-keys" then
      args.sort_keys = true
    elseif a == "--compact" then
      args.compact = next_value("--compact")
    elseif a == "--width" then
      args.width = parse_int(next_value("--width"), "--width")
    elseif a == "--input" or a == "-i" then
      args.input = next_value(a)
    elseif a == "--indent" then
      args.indent = parse_int(next_value("--indent"), "--indent")

    elseif a == "--pack-items" then
      set_pair(args.overrides, "pack_array_items", "pack_obj_items", parse_int(next_value(a), a))
    elseif a == "--fold-items" then
      set_pair(args.overrides, "fold_array_items", "fold_obj_items", parse_int(next_value(a), a))
    elseif a == "--grid-items" then
      set_pair(args.overrides, "grid_array_items", "grid_obj_items", parse_int(next_value(a), a))
    elseif a == "--join-items" then
      set_pair(args.overrides, "join_array_items", "join_obj_items", parse_int(next_value(a), a))

    elseif option_to_field[a] then
      args.overrides[option_to_field[a]] = parse_int(next_value(a), a)
    else
      error("unknown option: " .. tostring(raw))
    end

    i = i + 1
  end

  return args
end

local function decode_json(text)
  if type(jsonfold.decode_json) ~= "function" then
    error("jsonfold.decode_json is required by cli.lua")
  end
  return jsonfold.decode_json(text)
end

local function main(argv)
  argv = argv or arg or {}
  local args = parse_args(argv)
  if args.help then usage(); return 0 end

  local cfg = jsonfold.jsonfold_config(args.compact, args.width, args.overrides)

  if args.verbose then
    io.stderr:write(tostring(cfg), "\n")
  end

  local data
  if args.demo then
    data = jsonfold.demo_data()
  else
    data = decode_json(read_all(args.input))
  end

  local stats = jsonfold.write_json(data, io.stdout, args.width, cfg, {
    indent = args.indent,
    sort_keys = args.sort_keys,
  })

  if args.verbose then
    io.stderr:write(tostring(stats), "\n")
  end
  return 0
end

local ok, err = pcall(function() os.exit(main(arg or {})) end)
if not ok then
  io.stderr:write("cli.lua: ", tostring(err), "\n")
  os.exit(1)
end
