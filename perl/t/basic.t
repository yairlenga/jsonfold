use strict;
use warnings;
use Test::More;
use JSON::PP ();
use JSON::JSONFold qw(dumps fold_text preset);

my $data = {
    ids    => [ 1, 2, 3, 4 ],
    meta   => { version => 1, ok => JSON::PP::true },
    matrix => [ [1, 2], [3, 4] ],
};

my $s = dumps($data, compact => 'default', sort_keys => 1);
like($s, qr/"ids": \[ 1, 2, 3, 4 \]/, 'folds small array');
like($s, qr/"meta": \{ "ok": true, "version": 1 \}/, 'folds small object');
like($s, qr/\[ 1, 2 \], \[ 3, 4 \]/, 'joins folded child arrays');

my $pretty = JSON::PP->new->pretty->indent_length(2)->canonical->encode($data);
my $off = fold_text($pretty, compact => 'off');
is($off, $pretty, 'off preserves pretty JSON text');

my $cfg = preset('max', width => 120);
is($cfg->{width}, 120, 'preset override');

done_testing;
