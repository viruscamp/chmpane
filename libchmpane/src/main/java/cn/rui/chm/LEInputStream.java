/**
 * Copyright (C) 2007 Rui Shen (rui.shen@gmail.com) All Right Reserved
 * File     : LEInputStream.java
 * Created	: 2007-3-1
 * 
 * ****************************************************************************
 * Copyright (C) 2007 Rui Shen (rui.shen@gmail.com)
 * http://chmpane.sourceforge.net, All Right Reserved. 
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  [1] Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *  [2] Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in
 *      the documentation and/or other materials provided with the
 *      distribution.
 *  [3] Neither the name "CHMPane" nor the names of its contributors may
 *      be used to endorse or promote products derived from this software
 *      without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * *****************************************************************************
 */
package cn.rui.chm;

import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Little Endian Input Stream
 * @author Shufeng
 *
 */
class LEInputStream extends FilterInputStream {

	public LEInputStream(InputStream in) {
		super(in);
	}

	/**
	 * 16-bit little endian unsinged integer
	 */
	public int read16() throws IOException {
		byte[] buf = new byte[Short.SIZE / Byte.SIZE];
		readFully(buf);
		int b1 = buf[0] & 0xff;
		int b2 = buf[1] & 0xff;
		return (b1 << 0) + (b2 << 8);
	}

	/**
	 * 32-bit little endian integer
	 */
	public int read32() throws IOException {
		byte[] buf = new byte[Integer.SIZE / Byte.SIZE];
		readFully(buf);
		return bytesToInt(buf);
	}

	public static int bytesToInt(byte... b) {
		int b1 = b[0] & 0xff;
		int b2 = b[1] & 0xff;
		int b3 = b[2] & 0xff;
		int b4 = b[3] & 0xff;
		return (b1 << 0) + (b2 << 8) + (b3 << 16) + (b4 << 24);
	}

	public static int bytesToInt(byte[] b, int offset) {
		int b1 = b[offset + 0] & 0xff;
		int b2 = b[offset + 1] & 0xff;
		int b3 = b[offset + 2] & 0xff;
		int b4 = b[offset + 3] & 0xff;
		return (b1 << 0) + (b2 << 8) + (b3 << 16) + (b4 << 24);
	}

	/**
	 * Encoded little endian integer
	 */
	public int readENC() throws IOException {
		int r = 0;
		for (;;) {
			int b = read();
			if (b < 0) throw new EOFException();
			r = (r << 7) + (b & 0x7f);
			if ((b & 0x80) == 0)
				return r;
		}
	}

	/**
	 * 64-bit little endian integer
	 */
	public long read64() throws IOException {
		byte[] buf = new byte[Long.SIZE / Byte.SIZE];
		readFully(buf);
		return bytesToLong(buf);
	}

	public static long bytesToLong(byte[] buf) {
		long b1 = buf[0] & 0xff;
		long b2 = buf[1] & 0xff;
		long b3 = buf[2] & 0xff;
		long b4 = buf[3] & 0xff;
		long b5 = buf[4] & 0xff;
		long b6 = buf[5] & 0xff;
		long b7 = buf[6] & 0xff;
		long b8 = buf[7] & 0xff;
		return ((b1 << 0) + (b2 << 8) + (b3 << 16) + (b4 << 24)
				+ (b5 << 32) + (b6 << 40) + (b7 << 48) + (b8 << 56));
	}

	public static long bytesToLong(byte[] buf, int offset) {
		long b1 = buf[offset + 0] & 0xff;
		long b2 = buf[offset + 1] & 0xff;
		long b3 = buf[offset + 2] & 0xff;
		long b4 = buf[offset + 3] & 0xff;
		long b5 = buf[offset + 4] & 0xff;
		long b6 = buf[offset + 5] & 0xff;
		long b7 = buf[offset + 6] & 0xff;
		long b8 = buf[offset + 7] & 0xff;
		return ((b1 << 0) + (b2 << 8) + (b3 << 16) + (b4 << 24)
				+ (b5 << 32) + (b6 << 40) + (b7 << 48) + (b8 << 56));
	}

	public long read64BE() throws IOException {
		byte[] buf = new byte[Long.SIZE / Byte.SIZE];
		readFully(buf);
		return bytesToLongBE(buf);
	}

	public static long bytesToLongBE(byte... b) {
		long result = 0;
		for (int i = 0; i < 8; i++) {
			result <<= 8;
			result |= (b[i] & 0xFF);
		}
		return result;
	}

	public String readUTF8(int len) throws IOException {
		byte[] buf = new byte[len];
		readFully(buf);
		return new String(buf, 0, len, "UTF-8");
	}

	public String readUTF16(int len) throws IOException {
		byte[] buf = new byte[len];
		readFully(buf);
		return new String(buf, 0, len, "UTF-16LE");
	}

	public String readGUID() throws IOException {
		return toHexString(read32(), 8) + 
			"-" + toHexString(read16(), 4) + "-" + toHexString(read16(), 4) +
			"-" + toHexString(read(), 2) + toHexString(read(), 2) +
			"-" + toHexString(read(), 2) + toHexString(read(), 2) +
			"-" + toHexString(read(), 2) + toHexString(read(), 2) +
			"-" + toHexString(read(), 2) + toHexString(read(), 2);
	}

	public UUID readUUID() throws IOException {
		int p1 = read32();
		int p2 = read16();
		int p3 = read16();
		long p4 = read64BE();
		long mostSigBits = ((long)p1 << 32) + ((long)p2 << 16) + (long)p3;
		long leastSigBits = p4;
		return new UUID(mostSigBits, leastSigBits);
	}

	private String toHexString(int v, int len) {
		StringBuffer b = new StringBuffer();
		for (int i = 0; i < len; i++)
			b.append('0');
		b.append(Long.toHexString(v));
		return b.substring(b.length() - len).toUpperCase();
	}

	public void readFully(byte b[]) throws IOException {
		readFully(b, 0, b.length);
	}

	public void readFully(byte b[], int off, int len)
			throws IOException {
		for (int n = 0; n < len; ) {
			int count = read(b, off + n, len - n);
			if (count < 0)
				throw new EOFException();
			n += count;
		}
	}
}
