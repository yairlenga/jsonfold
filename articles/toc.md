# Problem
* standard JSON output is binary:
  * either compact unreadable
  * or fully pretty-printed
* real data often needs mixed formatting
# Key ideas
* do not replace the serializer
* wrap the output stream
* operate on pretty-printed token stream
# The three phases
* pack
* fold
* join
# Why streaming matters
* avoids building second giant string
* can sit on top of existing json.dump()
* works incrementally
# Cross-language portability
* same algorithm in Python + JavaScript
* relies only on pretty-print structure
# Examples
* before/after examples are critical here
# Limitations
* assumes normal serializer layout
* not a validating parser
* depends on indentation semantics
* 