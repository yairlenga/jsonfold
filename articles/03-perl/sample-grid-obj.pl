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
