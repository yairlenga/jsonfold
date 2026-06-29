using System;
using System.IO;

namespace JsonFold.Format;

internal sealed class FormatterStream  : Stream
{
    private readonly JsonFoldWriter _writer;

    public FormatterStream (JsonFoldWriter writer)
    {
        _writer = writer;
    }

    public override bool CanRead => false;
    public override bool CanSeek => false;
    public override bool CanWrite => true;

    public override long Length =>
        throw new NotSupportedException();

    public override long Position
    {
        get => throw new NotSupportedException();
        set => throw new NotSupportedException();
    }

    public override int Read(byte[] buffer, int offset, int count) =>
        throw new NotSupportedException();

    public override long Seek(long offset, SeekOrigin origin) =>
        throw new NotSupportedException();

    public override void SetLength(long value) =>
        throw new NotSupportedException();

 
    private readonly System.Text.Decoder _decoder = System.Text.Encoding.UTF8.GetDecoder();
    private readonly char[] _chars = new char[8192];

    public override void Write(byte[] buffer, int offset, int count)
    {
        Write(buffer.AsSpan(offset, count));
    }

    public override void Write(ReadOnlySpan<byte> buffer)
    {
        while (!buffer.IsEmpty)
        {
            _decoder.Convert(
                buffer,
                _chars,
                flush: false,
                out int bytesUsed,
                out int charsUsed,
                out _);

            if (charsUsed > 0)
                _writer.Write(new string(_chars, 0, charsUsed));

            buffer = buffer[bytesUsed..];
        }
    }

    public override void Flush()
    {
        while (true)
        {
            _decoder.Convert(
                ReadOnlySpan<byte>.Empty,
                _chars,
                flush: true,
                out _,
                out int charsUsed,
                out bool completed);

            if (charsUsed > 0)
                _writer.Write(new string(_chars, 0, charsUsed));

            if (completed)
                break;
        }

        _writer.Flush();
    }

    protected override void Dispose(bool disposing)
    {
        if (disposing)
        {
            Flush();
        }

        base.Dispose(disposing);
    }
}