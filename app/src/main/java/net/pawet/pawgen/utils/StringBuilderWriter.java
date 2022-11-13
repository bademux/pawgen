package net.pawet.pawgen.utils;

import lombok.RequiredArgsConstructor;

import java.io.Writer;

@RequiredArgsConstructor
public final class StringBuilderWriter extends Writer {

	private final StringBuilder buf;

	@Override
	public void write(int c) {
		buf.append((char) c);
	}

	@Override
	public void write(char[] cbuf, int off, int len) {
		buf.append(cbuf, off, len);
	}

	@Override
	public void write(String str) {
		buf.append(str);
	}

	@Override
	public void write(String str, int off, int len) {
		buf.append(str, off, off + len);
	}

	@Override
	public Writer append(CharSequence csq) {
		buf.append(csq);
		return this;
	}

	@Override
	public Writer append(CharSequence csq, int start, int end) {
		buf.append(csq, start, end);
		return this;
	}

	@Override
	public Writer append(char c) {
		buf.append(c);
		return this;
	}

	@Override
	public void flush() {
	}

	@Override
	public void close() {
	}
}
