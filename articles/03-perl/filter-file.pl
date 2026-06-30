use JSON::JSONFold qw(fold_text);

# Read text from input.json
open my $fp, "<", "input.json" or die "Error opening input.json: $!";
my $text = do { local $/ ; <$fp> } ;
close $fp ;

# Print formatted text, using default config/default width (100 columns)
print fold_text($text) ;

# Print formatted text, using high compaction config, fit to 120 columns
print fold_text($text, 50, "high") ;
