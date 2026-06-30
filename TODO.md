# Internal improvements

1. Change emit_lines to transfer lines between frames - so it will be explicit lines are removed from the source. (2026-06-25)
2. Add file interface (fopencookie, funopen). (2026-06-01)
3. Cleanup the flush operation - should be NO-OP, and "finish" should call flush. Should not be exposed directly as public API - unless needed to implement stream interface interface. (2026-06-25)
4. Revise C code to extract signatures - and do strcmp, instead of compare signatures. (2026-06-25)
5. Improve error detection on C, return false on write errors to provide early detection.
6. Add print_folded to convenience API - print_folded(fp, text, width, config)
7. API Test for Perl
8. Change default for all implementation to generate Uncode and not \u.... sequences
9. Allow Perl code to take key=>value, or options hash ref for variable argument list.
