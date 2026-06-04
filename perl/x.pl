#!/usr/bin/perl

use strict;
use warnings;
use Benchmark qw(cmpthese);

my @lines = (
    '{',
    '  "a": 1,',
    '    "name": "hello world",',
    '      [',
    '      ],',
    '    },',
) x 1 ;

cmpthese(-5, {

    capture => sub {
        for my $s (@lines) {
            my ($spaces) = $s =~ /^(\s*)/;
            my $body = substr($s, length($spaces));
            $body =~ s/\s+\z//;
        }
    },

    capture_dollar1 => sub {
        for my $s (@lines) {
            $s =~ /^(\s*)/;
            my $indent = length($1);
            my $body = substr($s, $indent);
            $body =~ s/\s+\z//;
        }
    },

    offset => sub {
        for my $s (@lines) {
            $s =~ /^ */;
            my $indent = $+[0];
            my $body = substr($s, $indent);
            $body =~ s/\s+\z//;
        }
    },

    single_re => sub {
        for my $s (@lines) {
            my ($indent, $body) =
                $s =~ /^(\s*)(.*?)\s*\z/;
        }
    },

    single_re2 => sub {
        for my $s (@lines) {
            my ($indent, $body) =
                $s =~ /^(\s*)(.*)\z/;
            $body =~ s/\s+\z//;
        }
    },

});
