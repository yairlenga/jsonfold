use JSON::JSONFold qw(encode_json jsonfold_config);

my $data = {
    activity => [
        { _id => 101, event => "Order",    amount => 1250 },
        { _id => 101, event => "Payment",  invoice => 1045 },
        { _id => 205, note => "Called support" },
        { _id => 318, address => { city => "Boston", state => "MA" } },
        { _id => 318, contact => "Alice", phone => "555-1234" },
        { _id => 205, shipment => "UPS", tracking => "1Z84723" },
        { _id => 101, status => "Preferred", credit => 50000 },
        { _id => 205, invoice => 2048, due => "2026-07-15" },
        { _id => 318, email => "sales@example.com" },
        { _id => 101, note => "Renewal due next month" },
    ],
};
print encode_json($data, { compact => "join" } ) ;
