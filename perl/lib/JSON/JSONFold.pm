package JSON::JSONFold;

use strict;
use warnings;
use 5.014 ;
use JSON::PP ();
use Exporter 'import';

our $VERSION = '0.002';
our @EXPORT_OK = qw(
    config format_json write_json filter_stream
    fold_text write_text preset run);

# Object Orient Interface

sub new {
    my ($class, $width, $config, %overrides) = @_ ;

    # User may provide his own pretty printer
    my $indent = delete $overrides{indent} || 2 ;
    my $gold = delete $overrides{gold} ;
    my $json = delete $overrides{json} ;
    my $do_close = delete $overrides{do_close} ;
    unless ( $json ) {
        $overrides{sort_keys} //= 1 ;
        $json = _json_coder($gold, $indent, %overrides) ;
    }
    return bless {
        json => $json,
        width => $width,
        config => _config($config, $width),
        do_close => $do_close,
    }, ref($class) || $class || __PACKAGE__ ;
}

sub format {
    my($self, $data) = @_ ;

    my $config = $self->{config} ;

    my $output = '' ;
    open my $out, '>', \$output or die "open output: $!" ;

    my $stream = _stream($out, $config, 0) ;
    my $json = $self->{json} ;
    my $text = $json->encode($data) ;

    $stream->write($text);
    $stream->finish;
    $stream->flush;

    close $out or die "close output: $!" ;
    $output .= "\n" unless $output =~ /\n\z/;
    return $output ;
}

sub write {
    my($self, $data, $fh) = @_ ;

    my $config = $self->{config} ;

    my $do_close = $self->{do_close} ;
    my $stream = _stream($fh, $config, $do_close) ;
    my $json = $self->{json} ;

    my $text = $json->encode($data) ;
    $stream->write($text);
    $stream->finish;
    $stream->flush;
    my $info = $stream->stats ;
    $stream->close() ;

    return $info ;
}

# Functional Interface

sub config {
    my($base_config, $width, %overrides) = @_ ;
    $width //= $overrides{width} ;
    return _config($base_config, $width, %overrides) ;
}

sub format_json {
    my($data, $width, $config, %overrides) = @_ ;

    my $fmt = __PACKAGE__->new($width, $config, %overrides) ;
    my $output = $fmt->format($data) ;
    return $output ;
}

sub write_json {
    my($data, $fh, $width, $config, %overrides) = @_ ;

    my $fmt = __PACKAGE__->new($width, $config, %overrides) ;
    my $info = $fmt->write($data, $fh) ;
    return $info ;
}

sub format_text {
    my($text, $width, $config) = @_ ;

    my $cfg = _config($config, $width) ;

    my $output = '' ;
    open my $out, '>', \$output or die "open output: $!" ;

    my $stream = _stream($out, $config, 0) ;
    $stream->write($text) ;
    $stream->close() ;

    $output .= "\n" unless $output =~ /\n\z/;
    return $output ;
}

sub write_text {
    my ($fh, $text, $width, $config) = @_ ;

    my $cfg = _config($config, $width) ;

    my $stream = _stream($fh, $config, 0) ;
    $stream->write($text) ;
    my $info = $stream->info ;
    $stream->close() ;

    return $info ;
}

sub filter_stream {
    my($fh, $width, $config, %overrides) = @_ ;
    my $do_close = delete $overrides{close_fp} ;
    return _stream($fh, _config($config, $width, %overrides), $do_close) ;
}

sub _config {
    my ($preset, $width, %overrides) = @_ ;
    $overrides{width} = $width if $width ;
    return JSON::JSONFold::Config::config($preset, %overrides) ;
}

sub _stream {
    my ($fp, $config, $close_fp) = @_ ;
    return JSON::JSONFold::Writer->new($fp, $config, $close_fp) ;
}

sub _json_coder {
    my ($gold, $indent, %opt) = @_;
    # Must have valid indent, otherwise cannot parse the data
    my $sort_keys = $opt{sort_keys} // 1 ;
    my $json = JSON::PP->new->allow_nonref->canonical($sort_keys);
    if ( $gold ) {
        $json->pretty->indent_length(2)->space_before(0)->space_after(1);
    } else {
        $json->pretty(1)->indent_length($indent || 2) ;
    }
    return $json;
}

# JSON compatible API - OO

sub encode {
    my $data = shift ;
    
}


# JSON compatiable API - Functional

sub encode_json {
    my ($data, $opts) = @_ ;
    my %overrides = %$opts if $opts ;
    my $width = delete $overrides{width} ;
    my $compact = delete $overrides{compact} // "" ;
    return format_json($data, $width, $compact, %overrides) ;
}

sub to_json {
    my ($data, $opts) = @_ ;
    my %overrides = %$opts if $opts ;
    my $width = delete $overrides{width} ;
    my $compact = delete $overrides{compact} // "" ;
    return format_json($data, $width, $compact, %overrides) ;
}

sub run {
    JSON::JSONFold::CLI::run() ;
}

package JSON::JSONFold::Kind;

use strict ;
use warnings ;
use Exporter 'import';

our @EXPORT_OK = qw(
    KIND_NONE
    KIND_DICT
    KIND_LIST
    %OPENING_KIND
    %CLOSING_KIND
);

use constant KIND_NONE => 0;
use constant KIND_DICT => 1;
use constant KIND_LIST => 2;

our %OPENING_KIND = (
    '{' => KIND_DICT,
    '[' => KIND_LIST,
);

our %CLOSING_KIND = (
    '}'  => KIND_DICT,
    '},' => KIND_DICT,
    ']'  => KIND_LIST,
    '],' => KIND_LIST,
);

# -------------------------------------------------------------------------
# Internal package: immutable-ish configuration record
# -------------------------------------------------------------------------

package JSON::JSONFold::Config;

use strict;
use warnings;
use Exporter 'import';

use constant MAX_ARRAY_ITEMS => 1000;
use constant MAX_OBJ_ITEMS   => 1000;
use constant MAX_NESTING     => 10;
use constant DEFAULT_WIDTH   => 100;

our $SEQ;
use constant {
    C_UNUSED_FIRST      => $SEQ++,
    C_WIDTH             => $SEQ++,

    C_PACK_ARRAY_ITEMS  => $SEQ++,
    C_PACK_OBJ_ITEMS    => $SEQ++,
    C_PACK_NESTING      => $SEQ++,

    C_FOLD_ARRAY_ITEMS  => $SEQ++,
    C_FOLD_OBJ_ITEMS    => $SEQ++,
    C_FOLD_NESTING      => $SEQ++,

    C_JOIN_ARRAY_ITEMS  => $SEQ++,
    C_JOIN_OBJ_ITEMS    => $SEQ++,
    C_JOIN_NESTING      => $SEQ++,
    C_UNUSED_LAST       => $SEQ++,
};

our %PRESETS ;        # values defined later

BEGIN {

    our @EXPORT = qw(
        C_WIDTH
        C_PACK_ARRAY_ITEMS
        C_PACK_OBJ_ITEMS
        C_PACK_NESTING
        C_FOLD_ARRAY_ITEMS
        C_FOLD_OBJ_ITEMS
        C_FOLD_NESTING
        C_JOIN_ARRAY_ITEMS
        C_JOIN_OBJ_ITEMS
        C_JOIN_NESTING
    );
}

our @FIELDS = (
    [ 'width',            C_WIDTH ],
    [ 'pack_array_items', C_PACK_ARRAY_ITEMS ],
    [ 'pack_obj_items',   C_PACK_OBJ_ITEMS ],
    [ 'pack_nesting',     C_PACK_NESTING ],
    [ 'fold_array_items', C_FOLD_ARRAY_ITEMS ],
    [ 'fold_obj_items',   C_FOLD_OBJ_ITEMS ],
    [ 'fold_nesting',     C_FOLD_NESTING ],
    [ 'join_array_items', C_JOIN_ARRAY_ITEMS ],
    [ 'join_obj_items',   C_JOIN_OBJ_ITEMS ],
    [ 'join_nesting',     C_JOIN_NESTING ],
);

sub as_hash {
    my ($self) = @_ ;
    map { my ($name, $idx) = @$_ ; ($name => $self->[$idx]) ; } @FIELDS ;
}

sub _make {
    my ($class, %arg) = @_;
    my @d;
    $#d = $SEQ;
    $d[C_WIDTH] = $arg{width};

    $d[C_PACK_ARRAY_ITEMS] = $arg{pack_array_items};
    $d[C_PACK_OBJ_ITEMS]   = $arg{pack_obj_items};
    $d[C_PACK_NESTING]     = $arg{pack_nesting};

    $d[C_FOLD_ARRAY_ITEMS] = $arg{fold_array_items};
    $d[C_FOLD_OBJ_ITEMS]   = $arg{fold_obj_items};
    $d[C_FOLD_NESTING]     = $arg{fold_nesting};

    $d[C_JOIN_ARRAY_ITEMS] = $arg{join_array_items};
    $d[C_JOIN_OBJ_ITEMS]   = $arg{join_obj_items};
    $d[C_JOIN_NESTING]     = $arg{join_nesting};
    return bless \@d, $class;
}

our $NONE = __PACKAGE__->_make(
    width            => DEFAULT_WIDTH,

    pack_array_items => 0,
    pack_obj_items   => 0,
    pack_nesting     => 0,

    fold_array_items => 0,
    fold_obj_items   => 0,
    fold_nesting     => 0,

    join_array_items => 0,
    join_obj_items   => 0,
    join_nesting     => 0,
);

our $DEFAULT = __PACKAGE__->_make(
    width            => DEFAULT_WIDTH,

    pack_array_items => 8,
    pack_obj_items   => 4,
    pack_nesting     => 1,

    fold_array_items => 8,
    fold_obj_items   => 4,
    fold_nesting     => 1,

    join_array_items => 8,
    join_obj_items   => 4,
    join_nesting     => 1,
);

our %NAME_TO_INDEX = (
    width            => C_WIDTH,

    pack_array_items => C_PACK_ARRAY_ITEMS,
    pack_obj_items   => C_PACK_OBJ_ITEMS,
    pack_nesting     => C_PACK_NESTING,

    fold_array_items => C_FOLD_ARRAY_ITEMS,
    fold_obj_items   => C_FOLD_OBJ_ITEMS,
    fold_nesting     => C_FOLD_NESTING,

    join_array_items => C_JOIN_ARRAY_ITEMS,
    join_obj_items   => C_JOIN_OBJ_ITEMS,
    join_nesting     => C_JOIN_NESTING,
);

sub _replace {
    my ($base, $validate) = (shift, shift) ;
    return $base unless @_;
    my $overrides = @_ == 1 && ref($_[0]) ? $_[0] : { @_ };
    return $base unless %$overrides;

    # Make  acopy of the original object.
    my @d = @$base;
    for my $key (keys %$overrides) {
        unless ( exists $NAME_TO_INDEX{$key} ) {
            die "unknown JSON::JSONFold config key: $key\n" if $validate ;
            next ;
        } ;
        $d[$NAME_TO_INDEX{$key}] = $overrides->{$key};
    }
    return bless \@d, ref($base) || __PACKAGE__;
}

    # Resolve named config into actual config object.
sub _resolve_config {
    my ($config) = @_;

    unless ( ref($config)) {
        my $name //= '';
        die "unknown JSON::JSONFold preset: $name\n"
            unless exists $PRESETS{$name};

    return $PRESETS{$name};

    }
    my ($name) = @_;


    $config = _preset($config) unless ref($config);
    return $config;
}


sub config {
    my ($preset, %overrides)  = @_;
    return _replace(_resolve_config($preset), 0, \%overrides);
}

sub new {
    my ($class, $config, @args) = @_;
    return config($config, @args);
}

    # Create preset - force validation.
sub _new_preset {
    my $base = shift ;
    return _replace($base, 1, @_) ;
}

%PRESETS = (
    off     => undef,
    ''      => $DEFAULT,
    default => $DEFAULT,
    none    => $NONE,
    low     => _new_preset($DEFAULT, fold_nesting => 0, join_nesting => 0),
    med     => _new_preset($DEFAULT, join_nesting => 0),
    high    => _new_preset($DEFAULT,
        pack_array_items => 16, pack_obj_items => 8, pack_nesting => 4,
        fold_array_items => 16, fold_obj_items => 8, fold_nesting => 4,
        join_array_items => 16, join_obj_items => 8, join_nesting => 2,
    ),
    max     => _new_preset($NONE,
        width => 255,
        pack_array_items => MAX_ARRAY_ITEMS, pack_obj_items => MAX_OBJ_ITEMS,
        pack_nesting => MAX_NESTING,
        fold_array_items => MAX_ARRAY_ITEMS, fold_obj_items => MAX_OBJ_ITEMS,
        fold_nesting => MAX_NESTING,
        join_array_items => MAX_ARRAY_ITEMS, join_obj_items => MAX_OBJ_ITEMS,
        join_nesting => MAX_NESTING,
    ),
    pack    => _new_preset($NONE,
        pack_array_items => MAX_ARRAY_ITEMS, pack_obj_items => MAX_OBJ_ITEMS,
        pack_nesting => MAX_NESTING,
    ),
    fold    => _new_preset($NONE,
        fold_array_items => MAX_ARRAY_ITEMS, fold_obj_items => MAX_OBJ_ITEMS,
        fold_nesting => MAX_NESTING,
    ),
    join    => _new_preset($NONE,
        fold_array_items => MAX_ARRAY_ITEMS, fold_obj_items => MAX_OBJ_ITEMS,
        fold_nesting => MAX_NESTING,
        join_array_items => MAX_ARRAY_ITEMS, join_obj_items => MAX_OBJ_ITEMS,
        join_nesting => MAX_NESTING,
    ),
);


# -------------------------------------------------------------------------
# Internal package: one physical pretty-printed line
# -------------------------------------------------------------------------

package JSON::JSONFold::Line;
use strict;
use warnings;
use Exporter 'import' ;

use constant KIND_NONE => $JSON::JSONFold::Kind::KIND_NONE ;

our $SEQ ;
use constant {
    L_INDENT => $SEQ++,
    L_TEXT => $SEQ++,
    L_PARENT_KIND => $SEQ++,
    L_ITEMS => $SEQ++,
    L_LEAFS => $SEQ++,
    L_CHILD_NESTING => $SEQ++,
    L_OPENER => $SEQ++,
    L_CLOSER => $SEQ++,
    L_CAN_JOIN => $SEQ++,
    L_CAN_PACK => $SEQ++,
} ;

BEGIN {
    our @EXPORT = qw( L_INDENT
        L_TEXT
        L_PARENT_KIND
        L_ITEMS
        L_LEAFS
        L_CHILD_NESTING
        L_OPENER
        L_CLOSER
        L_CAN_JOIN
        L_CAN_PACK
        ) ;
}

sub parse {
    my ($class, $s, $parent_kind) = @_;
    $parent_kind //= KIND_NONE;

    my ($spaces) = $s =~ /^(\s*)/;
    my $body = substr($s, length($spaces));
    $body =~ s/\s+\z//;

    my $last = substr($body, -1, 1);

    my $opener = $OPENING_KIND{$last} // KIND_NONE ;
    my $closer = $CLOSING_KIND{$body} // KIND_NONE;

    my $is_body = $parent_kind && !$opener && !$closer ? 1 : 0;

    my @d ;
    $#d = $SEQ ;
    $d[L_INDENT]      = length($spaces);
    $d[L_TEXT]        = $body;
    $d[L_PARENT_KIND] = $parent_kind;
    $d[L_ITEMS]       = 1;
    $d[L_LEAFS]       = 1;
    $d[L_CHILD_NESTING] = -1;
    $d[L_OPENER]      = $opener;
    $d[L_CLOSER]      = $closer;
    $d[L_CAN_JOIN]    = $is_body;
    $d[L_CAN_PACK]    = $is_body;

    return bless \@d, $class ;
}

sub fold {
    my ($class, $lines, $leafs, $child_nesting) = @_;
    my $first_line = $lines->[0] ;
    my @d ;
    $#d = $SEQ ;
    $d[L_INDENT]        = $first_line->[L_INDENT] ;
    $d[L_TEXT]          = join(' ', map { $_->[L_TEXT] } @$lines) ;
    $d[L_PARENT_KIND]   = $first_line->[L_PARENT_KIND] ;
    $d[L_ITEMS]         = 1;
    $d[L_LEAFS]         = $leafs;
    $d[L_CHILD_NESTING] = $child_nesting;
    $d[L_OPENER]        = KIND_NONE;
    $d[L_CLOSER]        = KIND_NONE;
    $d[L_CAN_PACK]      = 0;
    $d[L_CAN_JOIN]      = 1,

    return bless \@d, $class ;
}

sub raw   { return (' ' x $_[0][L_INDENT]) . $_[0][L_TEXT] . "\n" }
sub width { return $_[0][L_INDENT] + length($_[0][L_TEXT]) }

sub join_line {
    my ($self, $other) = @_;
    $self->[L_TEXT] .= ' ' . $other->[L_TEXT];
    $self->[L_ITEMS] += $other->[L_ITEMS];
    $self->[L_LEAFS] += $other->[L_LEAFS];
    if ($other->[L_CHILD_NESTING] > $self->[L_CHILD_NESTING]) {
        $self->[L_CHILD_NESTING] = $other->[L_CHILD_NESTING];
        $self->[L_CAN_PACK] = 0;
    }
    return $self;
}

# -------------------------------------------------------------------------
# Internal package: stack frame for a currently open JSON container
# -------------------------------------------------------------------------



package JSON::JSONFold::Frame;
use strict;
use warnings;
use Exporter 'import' ;

BEGIN {
    JSON::JSONFold::Line->import() ;
}

our $SEQ ;
use constant {
    F_UNUSED_FIRST  => $SEQ++,
    F_KIND          => $SEQ++,
    F_DEPTH         => $SEQ++,
    F_LINES         => $SEQ++,
    F_PACK_LIMIT    => $SEQ++,
    F_FOLD_LIMIT    => $SEQ++,
    F_JOIN_LIMIT    => $SEQ++,
    F_CONTENT_LINES => $SEQ++,
    F_ITEMS         => $SEQ++,
    F_LEAFS         => $SEQ++,
    F_FOLD_OK       => $SEQ++,
    F_CHILD_NESTING => $SEQ++,
    F_UNUSED_LAST   => $SEQ++,
} ;

BEGIN {
    our @EXPORT = qw(
        F_UNUSED_FIRST
        F_KIND
        F_DEPTH
        F_LINES
        F_PACK_LIMIT
        F_FOLD_LIMIT
        F_JOIN_LIMIT
        F_CONTENT_LINES
        F_ITEMS
        F_LEAFS
        F_FOLD_OK
        F_CHILD_NESTING
        F_UNUSED_LAST
    ) ;
}

sub new {
    my ($class, %arg) = @_;
    my @d ;
    $#d = $SEQ ;
    $d[F_KIND]          = $arg{kind};
    $d[F_DEPTH]         = $arg{depth} // 0;
    $d[F_LINES]         = $arg{lines} || [];
    $d[F_PACK_LIMIT]    = $arg{pack_limit} // 0;
    $d[F_FOLD_LIMIT]    = $arg{fold_limit} // 0;
    $d[F_JOIN_LIMIT]    = $arg{join_limit} // 0;
    $d[F_CONTENT_LINES] = 0;
    $d[F_ITEMS]         = 0;
    $d[F_LEAFS]         = 0;
    $d[F_FOLD_OK]       = 1;
    $d[F_CHILD_NESTING] = -1;
    return bless \@d, $class;
}

# Update Frame information based on added line
sub update_add {
    my ($self, $line) = @_ ;
    $self->[F_ITEMS] += $line->[L_ITEMS];
    $self->[F_LEAFS] += $line->[L_LEAFS];
    if ($line->[L_CHILD_NESTING] >= $self->[F_CHILD_NESTING]) {
        $self->[F_CHILD_NESTING] = $line->[L_CHILD_NESTING] + 1;
    }
}

sub is_empty { return @{ $_[0][F_LINES] } == 0 }
sub last_line { return $_[0][F_LINES][-1] }

# -------------------------------------------------------------------------
# Internal package: counters
# -------------------------------------------------------------------------

package JSON::JSONFold::Stats;
use strict;
use warnings;

sub new {
    my ($class) = @_;
    return bless {
        bytes_in  => 0,
        bytes_out => 0,
        lines_in  => 0,
        lines_out => 0,
    }, $class;
}

# -------------------------------------------------------------------------
# Internal package: streaming folding filter/writer
# -------------------------------------------------------------------------

package JSON::JSONFold::Writer;
use strict;
use warnings;

BEGIN {
    JSON::JSONFold::Line->import() ;
    JSON::JSONFold::Frame->import() ;
    JSON::JSONFold::Config->import() ;
}

our $SEQ ;
use constant {
    W_UNUSED_FIRST => $SEQ++,
    W_FH           => $SEQ++,
    W_CFG          => $SEQ++,
    W_PENDING      => $SEQ++,
    W_STACK        => $SEQ++,
    W_STATS        => $SEQ++,
    W_UNUSED_LAST  => $SEQ++,
} ;

BEGIN {
    our @EXPORT = qw(
        W_UNUSED_FIRST
        W_FH
        W_CFG
        W_PENDING
        W_STACK
        W_STATS
        W_UNUSED_LAST
    ) ;
}

sub new {
    my ($class, $fh, $compact) = @_;

    my @d ;
    $#d = $SEQ ;
    $d[W_FH]      = $fh;
    $d[W_CFG]     = JSON::JSONFold::Config::config($compact);
    $d[W_PENDING] = '';
    $d[W_STACK]   = [];
    $d[W_STATS]   = JSON::JSONFold::Stats->new;
    return bless \@d, $class;
}

sub stats  { return $_[0][W_STATS] }

sub write {
    my ($self, $s) = @_;
    $s = '' unless defined $s;
    my $len = length($s);
    $self->[W_STATS]{bytes_in} += $len;

    unless ($self->[W_CFG]) {
        $self->[W_STATS]{lines_in} += _count_newlines($s);
        return $self->_write_str($s);
    }

    my $nl = index($s, "\n");
    if ($nl < 0) {
        $self->[W_PENDING] .= $s;
        return $len;
    }

    my $nl2 = index($s, "\n", $nl + 1);
    if ($nl2 < 0) {
        $self->[W_STATS]{lines_in}++;
        my $line_text = $self->[W_PENDING] . substr($s, 0, $nl);
        $self->[W_PENDING] = substr($s, $nl + 1);
        $self->_feed(JSON::JSONFold::Line->parse($line_text, $self->_parent_kind));
        return $len;
    }

    # We have multiple lines - at least 2 new lines in the new buffer
    my @lines = split("\n", $s, -1) ;
    $lines[0] = $self->[W_PENDING] . $lines[0] ;
    $self->[W_PENDING] = pop @lines ;
    for my $part ( @lines ) {
        $self->_feed(JSON::JSONFold::Line->parse($part, $self->_parent_kind));

    }    
    $self->[W_STATS]{lines_in} += @lines;

    return $len;
}

sub finish {
    my ($self) = @_;
    if (length $self->[W_PENDING]) {
        $self->_feed(JSON::JSONFold::Line->parse($self->[W_PENDING], $self->_parent_kind));
        $self->[W_PENDING] = '';
    }

    for my $frame (@{ $self->[W_STACK] }) {
        $self->_write_line($_) for @{ $frame->[F_LINES] };
    }
    @{ $self->[W_STACK] } = ();
}

sub flush {
    my ($self) = @_;
    my $fh = $self->[W_FH];
    $fh->flush if $fh && $fh->can('flush');
}

sub close { return $_[0]->finish }

sub _feed {
    my ($self, $line) = @_;
    # Opener
    if ($line->[L_OPENER]) {
        push @{ $self->[W_STACK] }, JSON::JSONFold::Frame->new(
            kind       => $line->[L_OPENER],
            depth      => scalar(@{ $self->[W_STACK] }),
            lines      => [ $line ],
            pack_limit => $self->_pack_limit($line->[L_OPENER]),
            fold_limit => $self->_fold_limit($line->[L_OPENER]),
            join_limit => $self->_join_limit($line->[L_OPENER]),
        );
        return;
    }

    # Closer
    if ($line->[L_CLOSER]) {
        $self->_close_frame($line, $line->[L_CLOSER]);
        return;
    }

    # Regular Line
    if (@{ $self->[W_STACK] }) {
        my $frame = $self->[W_STACK][-1];
        $line->[L_CAN_PACK] = 0 if $line->[L_ITEMS] >= $frame->[F_PACK_LIMIT];
        $line->[L_CAN_JOIN] = 0 if $line->[L_ITEMS] >= $frame->[F_JOIN_LIMIT];
        $self->_add_to_frame($frame, $line);
    } else {
        $self->_write_line($line);
    }
    return ;
}

sub _emit_lines {
    my ($self, $lines, $depth) = @_;
    return unless @$lines;
    $depth = @{ $self->[W_STACK] } - 1 unless defined $depth;

    if ($depth < 0) {
        $self->_write_line($_) for @$lines;
        return
    }

    my $frame = $self->[W_STACK][$depth];
    $self->_add_to_frame($frame, $_) for @$lines;
    return
}



sub _add_to_frame {
    my ($self, $frame, $line) = @_;

    if (!$frame->is_empty) {
        return if $line->[L_CAN_PACK] && $self->_try_pack($frame, $line);
        return if $line->[L_CAN_JOIN] && $self->_try_join($frame, $line);

        # If frame is empty, may be it's in "streaming" mode, which
        # mean that lines that can not be packed/joined can be sent
        # directly to the output:
    } elsif (!$frame->[F_FOLD_OK] && !$line->[L_CAN_PACK] && !$line->[L_CAN_JOIN]) {
        $self->_write_line($line);
        return;
    }

    push @{ $frame->[F_LINES] }, $line;

    if ( $frame->[F_FOLD_OK] && $line->width > $self->[W_CFG][C_WIDTH] ) {
        $self->_mark_no_fold ;
    }

    unless ($line->[L_CLOSER]) {
        $frame->[F_CONTENT_LINES]++;
        $frame->update_add($line) ;
        if ( $frame->[F_FOLD_OK] ) {
            $self->_mark_no_fold unless $self->_check_fold_limits($frame) 
        }
    }

    $self->_stream_frame($frame) unless $frame->[F_FOLD_OK];
    return
}

sub _can_merge {
    my ($self, $prev, $line, $limit) = @_;
    return $prev->[L_INDENT] == $line->[L_INDENT]
        && $prev->[L_ITEMS] + $line->[L_ITEMS] <= $limit
        && $prev->[L_INDENT] + length($prev->[L_TEXT]) + 1 + length($line->[L_TEXT]) <= $self->[W_CFG][C_WIDTH];
}

sub _merge_into_frame {
    my ($self, $frame, $prev, $line) = @_;
    $prev->join_line($line);
    $frame->update_add($line) ;

    $prev->[L_CAN_PACK] = 0 if $prev->[L_ITEMS] >= $frame->[F_PACK_LIMIT];
    $prev->[L_CAN_JOIN] = 0 if $prev->[L_ITEMS] >= $frame->[F_JOIN_LIMIT];
    if ( $frame->[F_FOLD_OK] ) {
        unless ( $self->_check_fold_limits($frame)) {
            $self->_mark_no_fold ;
            $self->_stream_frame($frame) ;
        }
    }
}

sub _try_pack {
    my ($self, $frame, $line) = @_;

    return 0 if $frame->[F_PACK_LIMIT] <= 1 || !$line->[L_CAN_PACK] ||
        $frame->is_empty;

    my $prev = $frame->last_line;
    return 0 unless $prev->[L_CAN_PACK]
        && $prev->[L_CHILD_NESTING] < $self->[W_CFG][C_PACK_NESTING]
        && $self->_can_merge($prev, $line, $frame->[F_PACK_LIMIT]);
    $self->_merge_into_frame($frame, $prev, $line);
    # Disable join, or pack limits reached
    $prev->[L_CAN_JOIN] = 0 unless $prev->[L_CAN_PACK] ;
    return 1;
}

sub _try_join {
    my ($self, $frame, $line) = @_;

    return 0 if $frame->[F_JOIN_LIMIT] <= 1 ||
        !$line->[L_CAN_JOIN] || $line->[L_CHILD_NESTING] >= $self->[W_CFG][C_JOIN_NESTING] ||
        $frame->is_empty;

    my $prev = $frame->last_line;
    return 0 unless $prev->[L_CAN_JOIN]
        && $prev->[L_CHILD_NESTING] < $self->[W_CFG][C_JOIN_NESTING]
        && $self->_can_merge($prev, $line, $frame->[F_JOIN_LIMIT]);
    $self->_merge_into_frame($frame, $prev, $line);
    return 1;
}

sub _check_fold_limits {
    my ($self, $frame) = @_;

    return if $frame->[F_CONTENT_LINES] > 1 ;
    return if $frame->[F_ITEMS] > $frame->[F_FOLD_LIMIT] ;
    return if $frame->[F_CHILD_NESTING] >= $self->[W_CFG][C_FOLD_NESTING] ;
    return 1 ;
}

sub _close_frame {
    my ($self, $closer, $closing_kind) = @_;
    unless (@{ $self->[W_STACK] }) {
        $self->_write_line($closer);
        return;
    }

    # Frame is removed/destroyed
    my $frame = pop @{ $self->[W_STACK] };
    my $lines = $frame->[F_LINES] ;
    push @$lines, $closer ;
#   MAYBE: $frame->[F_FOLD_OK] = 0 if $frame->[F_KIND] != $closing_kind;

    if ( my $folded = $self->_try_fold($frame)) {
        $lines = [ $folded ] ;
    }
    $self->_emit_lines($lines);
}

sub _try_fold {
    my ($self, $frame) = @_;
    return undef if !$frame->[F_FOLD_OK] || $frame->[F_CONTENT_LINES] != 1 || @{ $frame->[F_LINES] } != 3;

    my $folded_len = -1;
    $folded_len += 1 + length($_->[L_TEXT]) for @{ $frame->[F_LINES] };
    return undef if $frame->[F_LINES][0][L_INDENT] + $folded_len > $self->[W_CFG][C_WIDTH];

    return JSON::JSONFold::Line->fold(
        $frame->[F_LINES],
        $frame->[F_LEAFS],
        $frame->[F_CHILD_NESTING],
    );
}

sub _stream_frame {
    my ($self, $frame) = @_;
    my $lines = $frame->[F_LINES];
    return unless @$lines ;

    my $last = $lines->[-1] ;
    my $keep_last = $last->[L_CAN_PACK] || $last->[L_CAN_JOIN] ;
    pop @$lines if $keep_last ;
    if ( @$lines ) {
        $self->_emit_lines($lines, $frame->[F_DEPTH] - 1) ;
        @$lines = ();
    }
    push @$lines, $last if $keep_last ;
    return
}

sub _mark_no_fold {
    my ($self) = @_;
    $_->[F_FOLD_OK] = 0 for @{ $self->[W_STACK] };
}

sub _write_line {
    my ($self, $line) = @_ ;
    return $self->_write_str($line->raw) ;
}

sub _write_str {
    my ($self, $s) = @_;

    $self->[W_FH]->print($s) ;
    $self->[W_STATS]{bytes_out} += length($s);
    $self->[W_STATS]{lines_out} += _count_newlines($s);
    return length($s);
}

sub _parent_kind {
    my ($self) = @_ ;
    my $stack = $self->[W_STACK] ;
    return unless @$stack ;
    return @$stack ? $stack->[-1][F_KIND] : JSON::JSONFold::Kind::KIND_NONE ;
}

sub _choose_limit {
    my ($kind, $list, $dict) = @_;
    return $kind == JSON::JSONFold::Kind::KIND_LIST() ? $list
         : $kind == JSON::JSONFold::Kind::KIND_DICT() ? $dict
         : 0;
}
sub _pack_limit { _choose_limit($_[1], $_[0][W_CFG][C_PACK_ARRAY_ITEMS], $_[0][W_CFG][C_PACK_OBJ_ITEMS]) }
sub _fold_limit { _choose_limit($_[1], $_[0][W_CFG][C_FOLD_ARRAY_ITEMS], $_[0][W_CFG][C_FOLD_OBJ_ITEMS]) }
sub _join_limit { _choose_limit($_[1], $_[0][W_CFG][C_JOIN_ARRAY_ITEMS], $_[0][W_CFG][C_JOIN_OBJ_ITEMS]) }
sub _count_newlines { return ($_[0] =~ tr/\n//) }

package JSON::JSONFold::CLI ;

use 5.014 ;
use strict;
use warnings;

sub setup {
    require Carp ;

    $SIG{__DIE__} = sub {
        return if $^S;
        local $SIG{__DIE__};
        Carp::confess(@_);
    };

    $SIG{__WARN__} = sub {
        local $SIG{__WARN__};
        Carp::cluck(@_);
    };

    require Getopt::Long ;

    require JSON ;

}

sub demo_data {
    return {
        meta  => { version => 1, ok => JSON::PP::true },
        items => [ { id => 1, name => "alpha" }, { id => 2, name => "beta" }, ],
        matrix => [ [ 1, 2 ], [ 3, 4 ] ],
        long   => [
            "this is a long message that may force the block to stay expanded",
            "second",
            "third",
            "fourth",
        ],
        "single-array" => [1],
        "single-obj"   => [2],
        wide_array     => [ map { "abcdefghijklmnopqrstuvwxyz$_" } 1 .. 9 ],
        wide_object    => { map { ; "abcdefghijk$_" => "lmnopqrstuvwxyz$_" } 1 .. 9 },

    };
}

sub parse_options {
    my %opt = (
        compact   => 'default',
        indent    => 2,
        sort_keys => 1,
    );

    Getopt::Long::GetOptions(
        'demo'       => \$opt{demo},
        'verbose|v'  => \$opt{verbose},
        'help|h'     => \$opt{help},
        'input|i=s'  => \$opt{input},
        'compact=s'  => \$opt{compact},
        'indent=i'   => \$opt{indent},
        'sort-keys!' => \$opt{sort_keys},

        'width=i'            => \$opt{width},
    ) or die "Try --help\n";

    return \%opt;
}

sub usage {
    my $out = shift ;
    $out->print(<<___
Usage: json-jsonfold [options] < input.json

  --demo
  --compact=default|none|low|med|high|max|pack|fold|join|off
  --width=N
  --indent=N
  --sort-keys
  --input=FILE
___
    ) ;
}

sub read_input {
    my ($input) = @_ ;

    my $json_text;
    if (defined $input) {
        open my $fh, '<', $input or die "$input: $!\n";
        local $/;
        $json_text = <$fh>;
        close $fh or die "$input: $!\n";
    } else {
        local $/;
        $json_text = <STDIN>;
    }

    return JSON::PP->new->allow_nonref->decode($json_text);
}

sub show_verbose {
    require Data::Dumper ;

    my ($label) = shift ;
    my $dumper = new Data::Dumper([])->Terse(1)->Indent(1)->Sortkeys(1)->Pair('=')->Quotekeys(0) ;

    my $s = $dumper->Values( \@_)->Dump ;
    $s =~ s/\s+/ /gsm ;

    print STDERR "$label: $s\n" ;

}

sub run {
    setup() ;
    my $opt = parse_options();

    if ($opt->{help}) {
        usage();
        return 0;
    }

    my $data = $opt->{demo} ? demo_data() : read_input($opt->{input});
    my %cfg ;
    my $config = JSON::JSONFold::config($opt->{compact}, $opt->{width}, %cfg);
    my $verbose = $opt->{verbose} ;

    show_verbose("config", { $config->as_hash } ) if $verbose ;
 
    my $info = JSON::JSONFold::write_json($data, \*STDOUT, $opt->{width}, $config, sort_keys => $opt->{sort_keys});

    show_verbose("stats", { % $info }) if $verbose ;
    return 0;
}

run() unless caller() ;

1;

__END__

=head1 NAME

JSON::JSONFold - hybrid pretty/compact JSON output

=head1 SYNOPSIS

    use JSON::JSONFold qw(dumps dump);

    my $text = dumps($data, compact => 'default', indent => 2);
    dump($data, \*STDOUT, compact => 'max', sort_keys => 1);

    my $filter = JSON::JSONFold->new(compact => 'join');
    $filter->write($pretty_json_chunk);
    $filter->finish;
    print $filter->output;

=head1 DESCRIPTION

C<JSON::JSONFold> formats JSON using a normal pretty-printer first, then folds
small containers and adjacent scalar lines when they fit within a target width.
It is a line-oriented streaming filter, not a validating JSON parser.

=head1 INTERNAL PACKAGES

The implementation keeps internal records as lightweight packages in this same
file: C<JSON::JSONFold::Config>, C<JSON::JSONFold::Line>,
C<JSON::JSONFold::Frame>, C<JSON::JSONFold::Stats>, and
C<JSON::JSONFold::Writer>.

=head1 PRESETS

C<default>, C<none>, C<low>, C<med>, C<high>, C<max>, C<pack>, C<fold>, C<join>,
and C<off>.

=cut


