package JSON::JSONFold;

use strict;
use warnings;
use JSON::PP ();
use Exporter 'import';

our $VERSION = '0.001';
our @EXPORT_OK = qw(dump dumps dumpi fold_text preset config);

use constant {
    KIND_NONE => 0,
    KIND_DICT => 1,
    KIND_LIST => 2,
};

use constant MAX_ARRAY_ITEMS => 1000;
use constant MAX_OBJ_ITEMS   => 1000;
use constant MAX_NESTING     => 10;

sub _cfg {
    return {
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
        @_,
    };
}

my $NONE = _cfg(
    pack_array_items => 0, pack_obj_items => 0, pack_nesting => 0,
    fold_array_items => 0, fold_obj_items => 0, fold_nesting => 0,
    join_array_items => 0, join_obj_items => 0, join_nesting => 0,
);

my $DEFAULT = _cfg();

my %PRESETS = (
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

sub _replace {
    my ($base, %overrides) = @_;
    return undef if !defined $base;
    return { %$base, %overrides };
}

sub config { return _cfg(@_) }

sub preset {
    my ($name, %overrides) = @_;
    $name = '' unless defined $name;
    die "unknown JSON::JSONFold preset: $name" unless exists $PRESETS{$name};
    return _replace($PRESETS{$name}, %overrides);
}

sub new {
    my ($class, %args) = @_;
    my $fh = delete $args{fh};
    my $compact = exists $args{compact} ? delete $args{compact} : '';
    my $cfg = _as_config($compact, %args);

    return bless {
        fh      => $fh,
        cfg     => $cfg,
        pending => '',
        stack   => [],
        stats   => { bytes_in => 0, bytes_out => 0, lines_in => 0, lines_out => 0 },
        out     => '',
    }, $class;
}

sub _as_config {
    my ($compact, %overrides) = @_;
    return undef if !defined($compact) || !$compact;

    my $cfg;
    if (ref($compact) eq 'HASH') {
        $cfg = { %$compact };
    } else {
        die "unknown JSON::JSONFold preset: $compact" unless exists $PRESETS{$compact};
        return undef if !defined $PRESETS{$compact};
        $cfg = { %{ $PRESETS{$compact} } };
    }

    return { %$cfg, %overrides };
}

sub stats { return $_[0]->{stats} }

sub output { return $_[0]->{out} }

sub write {
    my ($self, $s) = @_;
    $s = '' unless defined $s;
    my $len = length($s);
    $self->{stats}{bytes_in} += $len;

    if (!$self->{cfg}) {
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
        $self->_feed(_line_parse($line_text, $self->_parent_kind));
        $self->_mark_no_fold if length($self->{pending}) > $self->{cfg}{width};
        return $len;
    }

    $self->{pending} .= $s;
    while (1) {
        my $pos = index($self->{pending}, "\n");
        last if $pos < 0;
        my $part = substr($self->{pending}, 0, $pos, '');
        substr($self->{pending}, 0, 1, '');
        $self->{stats}{lines_in}++;
        $self->_feed(_line_parse($part, $self->_parent_kind));
    }

    $self->_mark_no_fold if length($self->{pending}) > $self->{cfg}{width};
    return $len;
}

sub finish {
    my ($self) = @_;
    if (length $self->{pending}) {
        $self->_feed(_line_parse($self->{pending}, $self->_parent_kind));
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

sub _line_parse {
    my ($s, $parent_kind) = @_;
    $parent_kind //= KIND_NONE;

    my ($spaces) = $s =~ /^(\s*)/;
    my $body = substr($s, length($spaces));
    $body =~ s/\s+\z//;

    my $opener = $body =~ /\{\z/ ? KIND_DICT : $body =~ /\[\z/ ? KIND_LIST : KIND_NONE;
    my $closer =
        ($body eq '}'  || $body eq '},') ? KIND_DICT :
        ($body eq ']'  || $body eq '],') ? KIND_LIST :
        KIND_NONE;

    my $is_body = $parent_kind && !$opener && !$closer ? 1 : 0;

    return {
        indent        => length($spaces),
        text          => $body,
        parent_kind   => $parent_kind,
        items         => 1,
        leafs         => 1,
        child_nesting => -1,
        opener        => $opener,
        closer        => $closer,
        can_join      => $is_body,
        can_pack      => $is_body,
    };
}

sub _line_raw { return (' ' x $_[0]{indent}) . $_[0]{text} . "\n" }
sub _line_width { return $_[0]{indent} + length($_[0]{text}) }

sub _line_join {
    my ($line, $other) = @_;
    $line->{text} .= ' ' . $other->{text};
    $line->{items} += $other->{items};
    $line->{leafs} += $other->{leafs};
    if ($other->{child_nesting} > $line->{child_nesting}) {
        $line->{child_nesting} = $other->{child_nesting};
        $line->{can_pack} = 0;
    }
}

sub _frame_new {
    my ($kind, $depth, $pack, $fold, $join, $line) = @_;
    return {
        kind => $kind, depth => $depth, lines => [ $line ],
        pack_limit => $pack, fold_limit => $fold, join_limit => $join,
        content_lines => 0, items => 0, leafs => 0,
        fold_ok => 1, child_nesting => -1,
    };
}

sub _feed {
    my ($self, $line) = @_;
    if ($line->{opener}) {
        push @{ $self->{stack} }, _frame_new(
            $line->{opener}, scalar(@{ $self->{stack} }),
            $self->_pack_limit($line->{opener}),
            $self->_fold_limit($line->{opener}),
            $self->_join_limit($line->{opener}),
            $line,
        );
        $self->_mark_no_fold if _line_width($line) > $self->{cfg}{width};
        return;
    }

    if ($line->{closer}) {
        $self->_close_frame($line, $line->{closer});
        return;
    }

    if (@{ $self->{stack} }) {
        my $frame = $self->{stack}[-1];
        $line->{can_pack} = 0 if $line->{items} >= $frame->{pack_limit};
        $line->{can_join} = 0 if $line->{items} >= $frame->{join_limit};
        $self->_add_to_frame($frame, $line);
    } else {
        $self->_write_line($line);
    }
}

sub _emit_lines {
    my ($self, $lines, $depth) = @_;
    return if !@$lines;
    $depth = @{ $self->{stack} } - 1 unless defined $depth;

    if ($depth < 0) {
        $self->_write_line($_) for @$lines;
        return;
    }

    my $frame = $self->{stack}[$depth];
    $self->_add_to_frame($frame, $_) for @$lines;
}

sub _add_to_frame {
    my ($self, $frame, $line) = @_;

    if (@{ $frame->{lines} }) {
        return if $line->{can_pack} && $self->_try_pack($frame, $line);
        return if $line->{can_join} && $self->_try_join($frame, $line);
    }

    push @{ $frame->{lines} }, $line;

    if (!$line->{closer}) {
        $frame->{content_lines}++;
        $frame->{items} += $line->{items};
        $frame->{leafs} += $line->{leafs};
        if ($line->{child_nesting} >= $frame->{child_nesting}) {
            $frame->{child_nesting} = $line->{child_nesting} + 1;
        }
        $self->_check_fold_limits($frame) if $frame->{fold_ok};
    }

    $self->_mark_no_fold if $frame->{fold_ok} && _line_width($line) > $self->{cfg}{width};
    $self->_stream_frame($frame) if !$frame->{fold_ok};
}

sub _can_merge {
    my ($self, $prev, $line, $limit) = @_;
    return $prev->{indent} == $line->{indent}
        && $prev->{items} + $line->{items} <= $limit
        && $prev->{indent} + length($prev->{text}) + 1 + length($line->{text}) <= $self->{cfg}{width};
}

sub _merge_into_frame {
    my ($self, $frame, $prev, $line) = @_;
    _line_join($prev, $line);
    $frame->{items} += $line->{items};
    $frame->{leafs} += $line->{leafs};
    $prev->{can_pack} = 0 if $prev->{items} >= $frame->{pack_limit};
    $prev->{can_join} = 0 if $prev->{items} >= $frame->{join_limit};
    $self->_check_fold_limits($frame) if $frame->{fold_ok};
}

sub _try_pack {
    my ($self, $frame, $line) = @_;
    return 0 if $frame->{pack_limit} <= 1 || !$line->{can_pack} || !@{ $frame->{lines} };
    my $prev = $frame->{lines}[-1];
    return 0 unless $prev->{can_pack}
        && $prev->{child_nesting} < $self->{cfg}{pack_nesting}
        && $self->_can_merge($prev, $line, $frame->{pack_limit});
    $self->_merge_into_frame($frame, $prev, $line);
    return 1;
}

sub _try_join {
    my ($self, $frame, $line) = @_;
    return 0 if $frame->{join_limit} <= 1 || !@{ $frame->{lines} }
        || !$line->{can_join} || $line->{child_nesting} >= $self->{cfg}{join_nesting};
    my $prev = $frame->{lines}[-1];
    return 0 unless $prev->{can_join}
        && $prev->{child_nesting} < $self->{cfg}{join_nesting}
        && $self->_can_merge($prev, $line, $frame->{join_limit});
    $self->_merge_into_frame($frame, $prev, $line);
    return 1;
}

sub _check_fold_limits {
    my ($self, $frame) = @_;
    return if !$frame->{fold_ok};
    if ($frame->{content_lines} > 1 || $frame->{items} > $frame->{fold_limit}
        || $frame->{child_nesting} >= $self->{cfg}{fold_nesting}) {
        $frame->{fold_ok} = 0;
    }
}

sub _close_frame {
    my ($self, $closer, $closing_kind) = @_;
    if (!@{ $self->{stack} }) {
        $self->_write_line($closer);
        return;
    }

    my $frame = pop @{ $self->{stack} };
    push @{ $frame->{lines} }, $closer;
    $frame->{fold_ok} = 0 if $frame->{kind} != $closing_kind;

    my $folded = $self->_try_fold($frame);
    $frame->{lines} = [ $folded ] if $folded;

    $self->_emit_lines($frame->{lines});
    @{ $frame->{lines} } = ();
}

sub _try_fold {
    my ($self, $frame) = @_;
    return undef if !$frame->{fold_ok} || $frame->{content_lines} != 1 || @{ $frame->{lines} } != 3;

    my $folded_len = -1;
    $folded_len += 1 + length($_->{text}) for @{ $frame->{lines} };
    return undef if $frame->{lines}[0]{indent} + $folded_len > $self->{cfg}{width};

    return {
        indent        => $frame->{lines}[0]{indent},
        text          => join(' ', map { $_->{text} } @{ $frame->{lines} }),
        parent_kind   => $self->_parent_kind,
        items         => 1,
        leafs         => $frame->{leafs},
        child_nesting => $frame->{child_nesting} > 0 ? $frame->{child_nesting} : 0,
        opener        => KIND_NONE,
        closer        => KIND_NONE,
        can_pack      => 0,
        can_join      => 1,
    };
}

sub _stream_frame {
    my ($self, $frame) = @_;
    my $lines = $frame->{lines};
    return if !@$lines;

    my $keep;
    if ($lines->[-1]{can_pack} || $lines->[-1]{can_join}) {
        $keep = pop @$lines;
    }

    $self->_emit_lines($lines, $frame->{depth} - 1);
    @$lines = ();
    push @$lines, $keep if $keep;
}

sub _mark_no_fold {
    my ($self) = @_;
    $_->{fold_ok} = 0 for @{ $self->{stack} };
    $self->_stream_frame($self->{stack}[-1]) if @{ $self->{stack} };
}

sub _write_line { $_[0]->_write_str(_line_raw($_[1])) }

sub _write_str {
    my ($self, $s) = @_;
    if (my $fh = $self->{fh}) {
        print {$fh} $s or die "write failed: $!";
    } else {
        $self->{out} .= $s;
    }
    $self->{stats}{bytes_out} += length($s);
    $self->{stats}{lines_out} += _count_newlines($s);
    return length($s);
}

sub _parent_kind { return @{ $_[0]->{stack} } ? $_[0]->{stack}[-1]{kind} : KIND_NONE }

sub _choose_limit {
    my ($kind, $list, $dict) = @_;
    return $kind == KIND_LIST ? $list : $kind == KIND_DICT ? $dict : 0;
}
sub _pack_limit { _choose_limit($_[1], $_[0]->{cfg}{pack_array_items}, $_[0]->{cfg}{pack_obj_items}) }
sub _fold_limit { _choose_limit($_[1], $_[0]->{cfg}{fold_array_items}, $_[0]->{cfg}{fold_obj_items}) }
sub _join_limit { _choose_limit($_[1], $_[0]->{cfg}{join_array_items}, $_[0]->{cfg}{join_obj_items}) }

sub _count_newlines { return ($_[0] =~ tr/\n//) }

sub _json_coder {
    my (%opt) = @_;
    my $indent = exists $opt{indent} ? $opt{indent} : 2;
    my $json = JSON::PP->new->allow_nonref->canonical($opt{sort_keys} ? 1 : 0);
    if ($indent && $indent > 0) {
        $json->pretty->indent_length($indent)->space_before(0)->space_after(1);
    }
    return $json;
}

sub dump {
    my ($obj, $fh, %opt) = @_;
    my $compact = exists $opt{compact} ? delete $opt{compact} : '';
    my $out = __PACKAGE__->new(fh => $fh, compact => $compact);
    my $text = _json_coder(%opt)->encode($obj);
    $out->write($text);
    $out->write("\n") unless $text =~ /\n\z/;
    $out->finish;
    return;
}

sub dumpi {
    my ($obj, $fh, %opt) = @_;
    my $compact = exists $opt{compact} ? delete $opt{compact} : '';
    my $out = __PACKAGE__->new(fh => $fh, compact => $compact);
    my $text = _json_coder(%opt)->encode($obj);
    $out->write($text);
    $out->write("\n") unless $text =~ /\n\z/;
    $out->finish;
    return $out->stats;
}

sub dumps {
    my ($obj, %opt) = @_;
    my $compact = exists $opt{compact} ? delete $opt{compact} : '';
    my $out = __PACKAGE__->new(compact => $compact);
    my $text = _json_coder(%opt)->encode($obj);
    $out->write($text);
    $out->write("\n") unless $text =~ /\n\z/;
    $out->finish;
    return $out->output;
}

sub fold_text {
    my ($text, %opt) = @_;
    my $compact = exists $opt{compact} ? delete $opt{compact} : '';
    my $out = __PACKAGE__->new(compact => $compact, %opt);
    $out->write($text);
    $out->finish;
    return $out->output;
}

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

=head1 PRESETS

C<default>, C<none>, C<low>, C<med>, C<high>, C<max>, C<pack>, C<fold>, C<join>,
and C<off>.

=cut
