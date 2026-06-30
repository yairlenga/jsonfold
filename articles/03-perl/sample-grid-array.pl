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
