package = "jsonfold"
version = "0.2.2-1"

source = {
    url = "git+https://github.com/ylenga/jsonfold"
}

description = {
    summary = "Hybrid pretty/compact JSON formatter",
    detailed = [[
JSONFold compacts pretty-printed JSON while preserving readability.
]],
    homepage = "https://github.com/ylenga/jsonfold",
    license = "MIT",
}

dependencies = {
    "lua >= 5.3",
    "dkjson",
}

build = {
    type = "builtin",
    modules = {
        ["jsonfold"] = "jsonfold.lua",
    },
    install = {
        bin = {
            jsonfold = "cli.lua",
        }
    }
}
