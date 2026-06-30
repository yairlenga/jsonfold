use JSON::JSONFold qw(encode_json);

my $data = {
    _id       => 123,
    locations => [
      { city => "Boston",    state => "MA", country => "USA" },
      { city => "Seattle",   state => "WA", country => "USA" },
      { city => "Montreal",  state => "QC", country => "Canada" },
    ],
    info      => {
      roles     => [ "foo", "bar", "baz" ],
    },
    name      => "Alice",
};

print encode_json($data) ;
