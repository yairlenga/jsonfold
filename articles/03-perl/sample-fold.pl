use JSON::JSONFold qw(encode_json jsonfold_config);
use JSON::PP ;

my $data = {
    name    => "Alice",
    age     => 42,
    address => {
        city    => "Boston",
        state   => "MA",
        country => "USA",
    },
    status => {
        active => JSON::PP::true,
    },
};
print encode_json($data, { config => jsonfold_config("max", undef, join_nesting => 0) }) ;
