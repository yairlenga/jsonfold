# JSONFold for Perl – Compact JSON Without Sacrificing Readability

## Table of Contents

1. [Introduction](#introduction)
2. [The Problem with Pretty-Printed JSON](#the-problem-with-pretty-printed-json)
3. [Packing – Reducing Vertical Space](#packing--reducing-vertical-space)
4. [Folding – Keeping Small Containers Together](#folding--keeping-small-containers-together)
5. [Joining – Combining Folded Structures](#joining--combining-folded-structures)
6. [Grid Formatting – Aligning Tabular Data](#grid-formatting--aligning-tabular-data)
7. [Using JSONFold in Perl](#using-jsonfold-in-perl)
8. [Configuration and Presets](#configuration-and-presets)
9. [Conclusion](#conclusion)

---

## Introduction

Built-in JSON serializers give us two choices:

The default output is built for machines and optimized for efficiency. It is compact, without any extra whitespace. While technically "text", it feels "binary" - a dense wall of brackets, quotes, commas, and braces that is painful to inspect.

```json
{"request_id":"8f2c1a44-91e2-4f52-8e11-7d2d1d9d52d1","timestamp":"2026-05-19T14:32:11Z","user":{"id":10421,"name":"John Smith","roles":["admin","reviewer","ops"],"preferences":{"theme":"dark","notifications":{"email":true,"sms":false,"push":true}}},"jobs":[{"id":901,"status":"running","targets":["srv-a01","srv-a02","srv-a03"],"metrics":{"cpu":72.4,"mem":68.1,"latency_ms":[12,15,11,18,14]}},{"id":902,"status":"queued","targets":["srv-b17"],"metrics":{"cpu":0,"mem":0,"latency_ms":[]}}],"audit":{"created_by":"system","created_at":"2026-05-19T14:00:00Z","tags":["prod","finance","daily-run","priority-high"]}}
```

To solve this problem JSON serializers provide a "Pretty-print" mode, which adds indentation, spacing around tokens and line breaks - making it readable for humans. The problem is that for large documents it often goes too far: A small array of numbers becomes ten lines. A tiny metadata object becomes a block. Deep structures become readable only by making the file much longer.

That extra formatting is not free. It makes logs larger, diffs noisier, terminal output harder to scan, and requires "speed-scrolling" to review the data sets.

### Why it exists

What I wanted was a middle ground: JSON that keeps the shape of pretty-printed output, but folds small, simple structures back onto one line when they fit. I wrote a Perl module `JSON::JSONFold` for "compacting" pretty-print JSON data to make it more readable for humans. The level of "compactness" is controlled by parameters, and there are a few preset configurations that can be used to get output with minimal effort.

### How it differs from traditional pretty printers

JSONFold Does not have logic to serialize perl data structures into JSON. There are already many good serializers. JSONFold post process that serialized JSON from other packages - so that all existing custom logic to produce the JSON (e.g., key sorting, tag filtering, blessed objects with `TO_JSON`, ...) can be reused. As a result, it's possible add the JSONFold formatting on top of existing serializers (including XS, etc.)

### How to use:

It's possible to use JSONFold in two ways: sending (pretty-printed) JSON for formatting, or using compatibility functions that work the same way as the existing `JSON` and `JSON::PP` formatters.
```perl
$data = { map { ("key$_", $_) } 0..10 } ;

use JSON qw();

# Compact JSON
print "// Compact:\n", JSON::encode_json($data), "\n" ;

# Pretty-Printed JSON
print "// Pretty-Printed:\n", JSON->new->pretty->encode($data), "\n" ;

# With JSONFold
use JSON::JSONFold qw();
print "// JSONFold:\n", JSON::JSONFold::encode_json($data), "\n" ;
```

Output:
```json
// Compact:
{"key2":"2","key7":"7","key6":"6","key4":"4","key9":"9","key5":"5","key0":"0","key10":"10","key3":"3","key1":"1","key8":"8"}
// Pretty-Printed:
{
   "key2" : "2",
   "key7" : "7",
   // ... Lines removed for brevity ...
   "key1" : "1",
   "key8" : "8"
}

// JSONFold:
{
  "key0": 0, "key1": 1, "key10": 10, "key2": 2, "key3": 3,
  "key4": 4, "key5": 5, "key6": 6, "key7": 7, "key8": 8,
  "key9": 9
}
```

### How does it work

JSONFold uses multiple transformation to make the data more readable: "pack", "fold", "grid" and "join". They are applied in sequence. Each transformation process the output of the previous step. The following describe each action.

> While it's possible to configure JSONFold for each JSON document, this is usually not necessary: JSONFold comes with few predefined "presets", including a default one, which can handle most documents without having to fine tune the formatting configuration. It's important to remember that every invocation of JSONFold will use a specific line-width and configuration parameters. The "default" setting is configured to produce output no wider than 100 columns.

> The following section includes example that describe the details of each step - using custom configuration to show the impact of each transformation.

## Packing – Reducing Vertical Space

### Problem

Pretty-printed JSON places every scalar value on its own line. While this makes deeply nested structures easy to follow, it also wastes a significant amount of vertical space for simple arrays and objects. For large document - it means a lot of vertical scrolling.

### Example Data
```json
{
  "states": [
    "Alabama",
    "Alaska",
    "Arizona",
    "Arkansas",
    "California",
    // ... Lines removed for brevity ...
    "Virginia",
    "Washington",
    "West_Virginia",
    "Wisconsin",
    "Wyoming"
  ]
}
```

### Perl Code

```perl
use JSON::JSONFold ;
my $data = {
    states => [
        qw(
            Alabama Alaska Arizona Arkansas California Colorado Connecticut
            Delaware Florida Georgia Hawaii Idaho Illinois Indiana Iowa
            Kansas Kentucky Louisiana Maine Maryland Massachusetts Michigan
            Minnesota Mississippi Missouri Montana Nebraska Nevada
            New_Hampshire New_Jersey New_Mexico New_York North_Carolina
            North_Dakota Ohio Oklahoma Oregon Pennsylvania Rhode_Island
            South_Carolina South_Dakota Tennessee Texas Utah Vermont
            Virginia Washington West_Virginia Wisconsin Wyoming
        )
    ],
};

print encode_json($data, { compact => "pack" })
```

### Output

```json
{
  "states": [
    "Alabama", "Alaska", "Arizona", "Arkansas", "California", "Colorado", "Connecticut", "Delaware",
    "Florida", "Georgia", "Hawaii", "Idaho", "Illinois", "Indiana", "Iowa", "Kansas", "Kentucky",
    "Louisiana", "Maine", "Maryland", "Massachusetts", "Michigan", "Minnesota", "Mississippi",
    "Missouri", "Montana", "Nebraska", "Nevada", "New_Hampshire", "New_Jersey", "New_Mexico",
    "New_York", "North_Carolina", "North_Dakota", "Ohio", "Oklahoma", "Oregon", "Pennsylvania",
    "Rhode_Island", "South_Carolina", "South_Dakota", "Tennessee", "Texas", "Utah", "Vermont",
    "Virginia", "Washington", "West_Virginia", "Wisconsin", "Wyoming"
  ]
}...
```

### Discussion

The "Pack" works on list or hashes where the values are "simple" (including scalars, null, empty list `[]`, or empty object `{}`). It will try to put as many as possible into the same line, respecting the stated line width.

---

## Folding – Keeping Small Containers Together

### The Problem

Even after scalar values have been packed, many JSON objects and arrays still occupy three lines simply because they are enclosed by opening and closing brackets.

For small containers, these extra lines add visual noise without improving readability. A single property object or a short array is often easier to understand when the entire container appears on one line.

JSONFold detects containers whose contents fit comfortably within the configured line width and folds them into a single logical line, while leaving larger or more complex structures expanded.

### Example Data

```json
{
  "name": "Alice",
  "age": 42,
  "address": {
    "city": "Boston",
    "state": "MA",
    "country": "USA"
  },
  "status": {
    "active": true
  }
}
```

### Perl Code

```perl
use JSON::JSONFold qw(encode_json jsonfold_config);

my $data = {
    name    => "Alice",
    age     => 42,
    address => {
        city    => "Boston",
        state   => "MA",
        country => "USA",
    },
    status => {
        active => JSON::true,
    },
};
print encode_json($data, { config => jsonfold_config("max", undef, join_nesting => 0) }) ;
```

### Output

```json
{
  "address": { "city": "Boston", "country": "USA", "state": "MA" },
  "age": 42, "name": "Alice",
  "status": { "active": "JSON::true" }
}
```

### Discussion

With "Fold" Small arrays and objects become single-line containers. Larger structures, that do not fit into a single line remain expanded - spread over multiple lines, with additional indentation for the container content vs. the sounding opener and closer lines.

---

## Grid Formatting – Aligning Tabular Data

### The Problem

Arrays often contain repeated structures, such as rows of numbers or objects with the same set of properties. While traditional pretty-printed JSON preserves the structure, it can be difficult to compare corresponding values across rows because the fields are not visually aligned.

JSONFold detects collections with a consistent layout and automatically aligns their values into columns, making the data easier to scan without changing the underlying JSON.

### Example: Array of Arrays

This example uses array of array data sets - which is common output for `DBI` `$ary_ref  = $dbh->selectall_arrayref($statement)`

```json
{
  "quarterly_sales": [
    [
      2023,
      1,
      "North",
      "Laptop",
      1250,
      18
    ],
    [
      2023,
      2,
      "Southwest",
      "Monitor",
      1345,
      21
    ],
    // ... Lines removed for brevity ...
    [
      2024,
      1,
      "Northeast",
      "Desk",
      1510,
      26
    ],
    [
      2024,
      2,
      "South",
      "Headphones",
      1635,
      29
    ]
  ]
}
```

### Perl Code

```perl
use JSON::JSONFold qw(encode_json jsonfold_config);

my $data = {
    quarterly_sales => [
        [2023, 1, "North", "Laptop", 1250, 18],
        [2023, 2, "Southwest", "Monitor", 1345, 21],
        [2023, 3, "West",  "Keyboard", 13198, 17],
        [2023, 4, "East",  "Mouse", 422, 24],
        [2024, 1, "Northeast", "Desk", 1510, 26],
        [2024, 2, "South", "Headphones", 1635, 29],
    ],
};
print encode_json($data, { compact => "grid" } ) ;
```

### Output

```json
{
  "quarterly_sales": [
    [ 2023, 1, "North",     "Laptop",      1250, 18 ],
    [ 2023, 2, "Southwest", "Monitor",     1345, 21 ],
    [ 2023, 3, "West",      "Keyboard",   13198, 17 ],
    [ 2023, 4, "East",      "Mouse",        422, 24 ],
    [ 2024, 1, "Northeast", "Desk",        1510, 26 ],
    [ 2024, 2, "South",     "Headphones",  1635, 29 ]
  ]
}
```

### Example: Array of Objects

This example uses array of array data sets - which is common output for `DBI` `$ary_ref  = $dbh->selectall_hashref($statement)`

```json
{
  "quarterly_sales": [
    {
      "year": 2023,
      "region": "North",
      "product": "Laptop",
      "sales": 1250,
      "orders": 18
    },
    // ... Lines removed for brevity ...
    {
      "year": 2023,
      "region": "Southwest",
      "product": "Monitor",
      "sales": 1345,
      "orders": 21
    },
  ]
}
```

### Perl Code

```perl
use JSON::JSONFold qw(encode_json jsonfold_config);

my $data = {
    quarterly_sales => [
        { yr => 2023, region => "North", product => "Laptop", sales => 1250, orders => 18 },
        { yr => 2023, region => "Southwest", product => "Monitor", sales => 1345, orders => 21 },
        { yr => 2023, region => "West", product => "Keyboard", sales => 1198, orders => 17 },
        { yr => 2023, region => "East", product => "Mouse", sales => 1422, orders => 24 },
        { yr => 2024, region => "Northeast", product => "Desk", sales => 1510, orders => 26 },
    ],
};
print encode_json($data, { compact => "grid" } ) ;
```


### Output

```json
{
  "quarterly_sales": [
    { "orders": 18, "product": "Laptop",   "region": "North",     "sales": 1250, "yr": 2023 },
    { "orders": 21, "product": "Monitor",  "region": "Southwest", "sales": 1345, "yr": 2023 },
    { "orders": 17, "product": "Keyboard", "region": "West",      "sales": 1198, "yr": 2023 },
    { "orders": 24, "product": "Mouse",    "region": "East",      "sales": 1422, "yr": 2023 },
    { "orders": 26, "product": "Desk",     "region": "Northeast", "sales": 1510, "yr": 2024 }
  ]
}
```

### Discussion

The grid transformation is different from the other transformations. It does not try to pick a better tradeoff between space and readability. Instead, it will add spaces to the regular pretty-printed output to make data easier to scan. This can result in small increase to the output size. The generated lines will still meet the line-width limit.

The "default" preset enabled grid processing. If the data is known not to have lines with repeated structure, its possible to choose the "classic" preset, which works like the "default" (pack, fold, join), and does not try to identify and align similar lines.

Deciding if a structure will benefit from "grid" layout require buffering few lines before making the decision. JSONFold presets limit the buffering to reasonable size (which can be customized if needed).

---

## Joining – Combining Folded Structures

### Problem

### Example Data

### Perl Code

```perl
...
```

### Output

```json
...
```

### Discussion

- Adjacent folded structures share a line.
- Further reduces vertical space while remaining readable.

---


## Using JSONFold in Perl

### Formatting Perl Data

```perl
...
```

### Filtering Existing Pretty-Printed JSON

```perl
...
```

### Writing to a File or Stream

```perl
...
```

---

## Configuration and Presets

### Default

### High

### Maximum

### Custom Configuration

```perl
...
```

---

## Conclusion

- JSONFold is a post-processing formatter.
- It works with existing Perl JSON serializers.
- The output is significantly more compact while preserving readability.
- Suitable for logs, configuration files, APIs, and debugging.