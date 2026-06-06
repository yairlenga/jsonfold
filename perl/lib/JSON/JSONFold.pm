package JSON::JSONFold;

use strict;
use warnings;
use JSON::PP ();
use Exporter 'import';

our $VERSION = '0.002';
our @EXPORT_OK = qw(dump dumps dumpi fold_text preset config);

sub config {
    my ($preset) = @_ ;
    return JSON::JSONFold::Config::config(@_) ;
}

# Backward-compatible constructor: the public package acts as a writer/filter.
sub new {
    my ($class, %args) = @_;
    return JSON::JSONFold::Writer->new(%args);
}

sub _json_coder {
    my (%opt) = @_;
    my $indent = exists $opt{indent} ? $opt{indent} : 2;
    my $json = JSON::PP->new->allow_nonref->canonical($opt{sort_keys} ? 1 : 0);
    if ($indent && $indent > 0) {
        $json->pretty->indent_length($indent)->space_before(0)->space_after(1);
    }
    return $json;
}

sub _print_stream {
    my ($obj, $fh, $compact, %opt) = @_;
    my $text = _json_coder(%opt)->encode($obj);
    my $out = JSON::JSONFold::Writer->new($fh, $compact, %opt);
    $out->write($text);
    $out->finish;
    return $out->stats ;
}

sub dump {
    my ($obj, $fh, %opt) = @_;
    my $compact = delete $opt{compact} // '' ;
    my $info = _print_stream($obj, $fh, $compact, %opt) ;
    return;
}

sub dumpi {
    my ($obj, $fh, %opt) = @_;
    my $compact = delete $opt{compact} // '' ;
    my $info = _print_stream($obj, $fh, $compact, %opt) ;
    return $info ;
}

sub dumps {
    my ($obj, %opt) = @_;
    my $compact = delete $opt{compact} // '' ;
    my $output = '' ;
    open my $fh, '>', \$output or die "open output: $!" ;
    my $info = _print_stream($obj, $fh, $compact, %opt) ;
    close $fh or die "close output: $!" ;
    $output .= "\n" unless $output =~ /\n\z/;
    return $output ;
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

use constant MAX_ARRAY_ITEMS => 1000;
use constant MAX_OBJ_ITEMS   => 1000;
use constant MAX_NESTING     => 10;

our $NONE = bless {
    pack_array_items => 0,
    pack_obj_items   => 0,
    pack_nesting     => 0,

    fold_array_items => 0,
    fold_obj_items   => 0,
    fold_nesting     => 0,

    join_array_items => 0,
    join_obj_items   => 0,
    join_nesting     => 0,
}, __PACKAGE__;

our $DEFAULT = bless {
    width            => 80,

    pack_array_items => 8,
    pack_obj_items   => 4,
    pack_nesting     => 1,

    fold_array_items => 8,
    fold_obj_items   => 4,
    fold_nesting     => 1,

    join_array_items => 8,
    join_obj_items   => 4,
    join_nesting     => 1,
}, __PACKAGE__;

sub _replace {
    my ($base) = shift ;
    # Clone only if there are overrides
    return $base unless @_ ;
    # Overrides can be single HASH reference, or keyword=value, ...
    my $overrides = @_ == 1 && ref($_[0]) ? $_[0] : { @_ } ;
    return $base unless %$overrides ;
    return bless { %$base, %$overrides }, ref($base) ;
}

sub _config {
    my ($config) = @_ ;
    $config = _preset($config) unless ref($config) ;
    return $config ;
}

sub config {
    my ($preset, %overrides)  = @_ ;
    return _replace(_config($preset) , \%overrides) ;
}

sub new {
    my ($class, $config, @args) = @_;
    return _replace(_config($config), @args) ;
}

our %PRESETS = (
    off     => undef,
    ''      => $DEFAULT,
    default => $DEFAULT,
    none    => $NONE,
    low     => _replace($DEFAULT, fold_nesting => 0, join_nesting => 0),
    med     => _replace($DEFAULT, join_nesting => 0),
    high    => _replace($DEFAULT,
        pack_array_items => 16, pack_obj_items => 8, pack_nesting => 4,
        fold_array_items => 16, fold_obj_items => 8, fold_nesting => 4,
        join_array_items => 16, join_obj_items => 8, join_nesting => 2,
    ),
    max     => _replace($NONE,
        width => 255,
        pack_array_items => MAX_ARRAY_ITEMS, pack_obj_items => MAX_OBJ_ITEMS,
        pack_nesting => MAX_NESTING,
        fold_array_items => MAX_ARRAY_ITEMS, fold_obj_items => MAX_OBJ_ITEMS,
        fold_nesting => MAX_NESTING,
        join_array_items => MAX_ARRAY_ITEMS, join_obj_items => MAX_OBJ_ITEMS,
        join_nesting => MAX_NESTING,
    ),
    pack    => _replace($NONE,
        pack_array_items => MAX_ARRAY_ITEMS, pack_obj_items => MAX_OBJ_ITEMS,
        pack_nesting => MAX_NESTING,
    ),
    fold    => _replace($NONE,
        fold_array_items => MAX_ARRAY_ITEMS, fold_obj_items => MAX_OBJ_ITEMS,
        fold_nesting => MAX_NESTING,
    ),
    join    => _replace($NONE,
        fold_array_items => MAX_ARRAY_ITEMS, fold_obj_items => MAX_OBJ_ITEMS,
        fold_nesting => MAX_NESTING,
        join_array_items => MAX_ARRAY_ITEMS, join_obj_items => MAX_OBJ_ITEMS,
        join_nesting => MAX_NESTING,
    ),
);

sub _preset {
    my ($name) = @_;

    $name //= '';

    die "unknown JSON::JSONFold preset: $name\n"
        unless exists $PRESETS{$name};

    return $PRESETS{$name};
}


# -------------------------------------------------------------------------
# Internal package: one physical pretty-printed line
# -------------------------------------------------------------------------

package JSON::JSONFold::Line;
use strict;
use warnings;

use constant KIND_NONE => $JSON::JSONFold::Kind::KIND_NONE ;

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

    return bless {
        indent      => length($spaces),
        text        => $body,
        parent_kind => $parent_kind,
        items       => 1,
        leafs       => 1,
        child_nesting => -1,
        opener      => $opener,
        closer      => $closer,
        can_join    => $is_body,
        can_pack    => $is_body,
    }, $class ;
}

sub fold {
    my ($class, $lines, $leafs, $child_nesting) = @_;
    my $first_line = $lines->[0] ;
    return bless {
        indent        => $first_line->{indent},
        text          => join(' ', map { $_->{text} } @$lines),
        parent_kind   => $first_line->{parent_kind},
        items         => 1,
        leafs         => $leafs,
        child_nesting => $child_nesting,
        opener        => KIND_NONE,
        closer        => KIND_NONE,
        can_pack      => 0,
        can_join      => 1,
    }, $class ;
}

sub raw   { return (' ' x $_[0]{indent}) . $_[0]{text} . "\n" }
sub width { return $_[0]{indent} + length($_[0]{text}) }

sub join_line {
    my ($self, $other) = @_;
    $self->{text} .= ' ' . $other->{text};
    $self->{items} += $other->{items};
    $self->{leafs} += $other->{leafs};
    if ($other->{child_nesting} > $self->{child_nesting}) {
        $self->{child_nesting} = $other->{child_nesting};
        $self->{can_pack} = 0;
    }
    return $self;
}

# -------------------------------------------------------------------------
# Internal package: stack frame for a currently open JSON container
# -------------------------------------------------------------------------

package JSON::JSONFold::Frame;
use strict;
use warnings;

sub new {
    my ($class, %arg) = @_;
    return bless {
        kind          => $arg{kind},
        depth         => $arg{depth} // 0,
        lines         => $arg{lines} || [],
        pack_limit    => $arg{pack_limit} // 0,
        fold_limit    => $arg{fold_limit} // 0,
        join_limit    => $arg{join_limit} // 0,
        content_lines => 0,
        items         => 0,
        leafs         => 0,
        fold_ok       => 1,
        child_nesting => -1,
    }, $class;
}

# Update Frame information based on added line
sub update_add {
    my ($self, $line) = @_ ;
    $self->{items} += $line->{items};
    $self->{leafs} += $line->{leafs};
    if ($line->{child_nesting} >= $self->{child_nesting}) {
        $self->{child_nesting} = $line->{child_nesting} + 1;
    }
}

sub is_empty { return @{ $_[0]{lines} } == 0 }
sub last_line { return $_[0]{lines}[-1] }

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

sub new {
    my ($class, $fh, $compact) = @_;

    return bless {
        fh      => $fh,
        cfg     => JSON::JSONFold::config($compact),
        pending => '',
        stack   => [],
        stats   => JSON::JSONFold::Stats->new,
    }, $class;
}

sub stats  { return $_[0]->{stats} }

sub write {
    my ($self, $s) = @_;
    $s = '' unless defined $s;
    my $len = length($s);
    $self->{stats}{bytes_in} += $len;

    unless ($self->{cfg}) {
        $self->{stats}{lines_in} += _count_newlines($s);
        return $self->_write_str($s);
    }

    my $nl = index($s, "\n");
    if ($nl < 0) {
        $self->{pending} .= $s;
        return $len;
    }

    my $nl2 = index($s, "\n", $nl + 1);
    if ($nl2 < 0) {
        $self->{stats}{lines_in}++;
        my $line_text = $self->{pending} . substr($s, 0, $nl);
        $self->{pending} = substr($s, $nl + 1);
        $self->_feed(JSON::JSONFold::Line->parse($line_text, $self->_parent_kind));
        return $len;
    }

    $self->{pending} .= $s;
    while (1) {
        my $pos = index($self->{pending}, "\n");
        last if $pos < 0;
        my $part = substr($self->{pending}, 0, $pos, '');
        substr($self->{pending}, 0, 1, '');
        $self->{stats}{lines_in}++;
        $self->_feed(JSON::JSONFold::Line->parse($part, $self->_parent_kind));
    }

    return $len;
}

sub finish {
    my ($self) = @_;
    if (length $self->{pending}) {
        $self->_feed(JSON::JSONFold::Line->parse($self->{pending}, $self->_parent_kind));
        $self->{pending} = '';
    }

    for my $frame (@{ $self->{stack} }) {
        $self->_write_line($_) for @{ $frame->{lines} };
    }
    @{ $self->{stack} } = ();
    return $self;
}

sub flush {
    my ($self) = @_;
    $self->finish;
    my $fh = $self->{fh};
    $fh->flush if $fh && $fh->can('flush');
    return $self;
}

sub close { return $_[0]->finish }

sub _feed {
    my ($self, $line) = @_;
    # Opener
    if ($line->{opener}) {
        push @{ $self->{stack} }, JSON::JSONFold::Frame->new(
            kind       => $line->{opener},
            depth      => scalar(@{ $self->{stack} }),
            lines      => [ $line ],
            pack_limit => $self->_pack_limit($line->{opener}),
            fold_limit => $self->_fold_limit($line->{opener}),
            join_limit => $self->_join_limit($line->{opener}),
        );
        return;
    }

    # Closer
    if ($line->{closer}) {
        $self->_close_frame($line, $line->{closer});
        return;
    }

    # Regular Line
    if (@{ $self->{stack} }) {
        my $frame = $self->{stack}[-1];
        $line->{can_pack} = 0 if $line->{items} >= $frame->{pack_limit};
        $line->{can_join} = 0 if $line->{items} >= $frame->{join_limit};
        $self->_add_to_frame($frame, $line);
    } else {
        $self->_write_line($line);
    }
    return ;
}

sub _emit_lines {
    my ($self, $lines, $depth) = @_;
    return unless @$lines;
    $depth = @{ $self->{stack} } - 1 unless defined $depth;

    if ($depth < 0) {
        $self->_write_line($_) for @$lines;
        return
    }

    my $frame = $self->{stack}[$depth];
    $self->_add_to_frame($frame, $_) for @$lines;
    return
}



sub _add_to_frame {
    my ($self, $frame, $line) = @_;

    if (!$frame->is_empty) {
        return if $line->{can_pack} && $self->_try_pack($frame, $line);
        return if $line->{can_join} && $self->_try_join($frame, $line);

        # If frame is empty, may be it's in "streaming" mode, which
        # mean that lines that can not be packed/joined can be sent
        # directly to the output:
    } elsif (!$frame->{fold_ok} && !$line->{can_pack} && !$line->{can_join}) {
        $self->_write_line($line);
        return;
    }

    push @{ $frame->{lines} }, $line;

    if ( $frame->{fold_ok} && $line->width > $self->{cfg}{width} ) {
        $self->_mark_no_fold ;
    }

    unless ($line->{closer}) {
        $frame->{content_lines}++;
        $frame->update_add($line) ;
        if ( $frame->{fold_ok} ) {
            $self->_mark_no_fold unless $self->_check_fold_limits($frame) 
        }
    }

    $self->_stream_frame($frame) unless $frame->{fold_ok};
    return
}

sub _can_merge {
    my ($self, $prev, $line, $limit) = @_;
    return $prev->{indent} == $line->{indent}
        && $prev->{items} + $line->{items} <= $limit
        && $prev->{indent} + length($prev->{text}) + 1 + length($line->{text}) <= $self->{cfg}{width};
}

sub _merge_into_frame {
    my ($self, $frame, $prev, $line) = @_;
    $prev->join_line($line);
    $frame->update_add($line) ;

    $prev->{can_pack} = 0 if $prev->{items} >= $frame->{pack_limit};
    $prev->{can_join} = 0 if $prev->{items} >= $frame->{join_limit};
    if ( $frame->{fold_ok} ) {
        unless ( $self->_check_fold_limits($frame)) {
            $self->_mark_no_fold ;
            $self->_stream_frame($frame) ;
        }
    }
}

sub _try_pack {
    my ($self, $frame, $line) = @_;
    return 0 if $frame->{pack_limit} <= 1 || !$line->{can_pack} || $frame->is_empty;
    my $prev = $frame->last_line;
    return 0 unless $prev->{can_pack}
        && $prev->{child_nesting} < $self->{cfg}{pack_nesting}
        && $self->_can_merge($prev, $line, $frame->{pack_limit});
    $self->_merge_into_frame($frame, $prev, $line);
    # Disable join, or pack limits reached
    $prev->{can_join} = 0 unless $prev->{can_pack} ;
    return 1;
}

sub _try_join {
    my ($self, $frame, $line) = @_;
    return 0 if $frame->{join_limit} <= 1 || $frame->is_empty
        || !$line->{can_join} || $line->{child_nesting} >= $self->{cfg}{join_nesting};
#    return 0 if $frame->{join_limit} <= 1 ||
#        $line->{child_nesting} >= $self->{cfg}{join_nesting} ||
        $frame->is_empty ;
    my $prev = $frame->last_line;
    return 0 unless $prev->{can_join}
        && $prev->{child_nesting} < $self->{cfg}{join_nesting}
        && $self->_can_merge($prev, $line, $frame->{join_limit});
    $self->_merge_into_frame($frame, $prev, $line);
    return 1;
}

sub _check_fold_limits {
    my ($self, $frame) = @_;

    return if $frame->{content_lines} > 1 ;
    return if $frame->{items} > $frame->{fold_limit} ;
    return if $frame->{child_nesting} >= $self->{cfg}{fold_nesting} ;
    return 1 ;
}

sub _close_frame {
    my ($self, $closer, $closing_kind) = @_;
    unless (@{ $self->{stack} }) {
        $self->_write_line($closer);
        return;
    }

    # Frame is removed/destroyed
    my $frame = pop @{ $self->{stack} };
    my $lines = $frame->{lines} ;
    push @$lines, $closer ;
#   MAYBE: $frame->{fold_ok} = 0 if $frame->{kind} != $closing_kind;

    if ( my $folded = $self->_try_fold($frame)) {
        $lines = [ $folded ] ;
    }
    $self->_emit_lines($lines);
}

sub _try_fold {
    my ($self, $frame) = @_;
    return undef if !$frame->{fold_ok} || $frame->{content_lines} != 1 || @{ $frame->{lines} } != 3;

    my $folded_len = -1;
    $folded_len += 1 + length($_->{text}) for @{ $frame->{lines} };
    return undef if $frame->{lines}[0]{indent} + $folded_len > $self->{cfg}{width};

    return JSON::JSONFold::Line->fold(
        $frame->{lines},
        $frame->{leafs},
        $frame->{child_nesting},
    );
}

sub _stream_frame {
    my ($self, $frame) = @_;
    my $lines = $frame->{lines};
    return unless @$lines ;

    my $last = $lines->[-1] ;
    my $keep_last = $last->{can_pack} || $last->{can_join} ;
    pop @$lines if $keep_last ;
    $self->_emit_lines($lines, $frame->{depth} - 1);
    @$lines = ();
    push @$lines, $last if $keep_last ;
    return
}

sub _mark_no_fold {
    my ($self) = @_;
    $_->{fold_ok} = 0 for @{ $self->{stack} };
}

sub _write_line {
    my ($self, $line) = @_ ;
    return $self->_write_str($line->raw) ;
}

sub _write_str {
    my ($self, $s) = @_;

    $self->{fh}->print($s) ;
    $self->{stats}{bytes_out} += length($s);
    $self->{stats}{lines_out} += _count_newlines($s);
    return length($s);
}

sub _parent_kind {
    my ($self) = @_ ;
    my $stack = $self->{stack} ;
    return unless @$stack ;
    return @$stack ? $stack->[-1]{kind} : JSON::JSONFold::Kind::KIND_NONE ;
}

sub _choose_limit {
    my ($kind, $list, $dict) = @_;
    return $kind == JSON::JSONFold::Kind::KIND_LIST() ? $list
         : $kind == JSON::JSONFold::Kind::KIND_DICT() ? $dict
         : 0;
}
sub _pack_limit { _choose_limit($_[1], $_[0]->{cfg}{pack_array_items}, $_[0]->{cfg}{pack_obj_items}) }
sub _fold_limit { _choose_limit($_[1], $_[0]->{cfg}{fold_array_items}, $_[0]->{cfg}{fold_obj_items}) }
sub _join_limit { _choose_limit($_[1], $_[0]->{cfg}{join_array_items}, $_[0]->{cfg}{join_obj_items}) }
sub _count_newlines { return ($_[0] =~ tr/\n//) }

package JSON::JSONFold;

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
