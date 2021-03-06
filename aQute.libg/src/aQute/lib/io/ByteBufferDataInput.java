package aQute.lib.io;

import java.io.DataInput;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class ByteBufferDataInput implements DataInput {
	private final ByteBuffer bb;

	public static DataInput wrap(ByteBuffer bb) {
		return new ByteBufferDataInput(bb);
	}

	public static DataInput wrap(byte[] b) {
		return wrap(b, 0, b.length);
	}

	public static DataInput wrap(byte[] b, int off, int len) {
		return wrap(ByteBuffer.wrap(b, off, len));
	}

	private ByteBufferDataInput(ByteBuffer bb) {
		this.bb = Objects.requireNonNull(bb);
	}

	private int ranged(int n) {
		if (n <= 0) {
			return 0;
		}
		return Math.min(n, bb.remaining());
	}

	public ByteBuffer slice(int n) {
		int limit = ranged(n);
		ByteBuffer slice = bb.slice();
		slice.limit(limit);
		bb.position(bb.position() + limit);
		return slice;
	}

	@Override
	public void readFully(byte[] b) {
		bb.get(b, 0, b.length);
	}

	@Override
	public void readFully(byte[] b, int off, int len) {
		bb.get(b, off, len);
	}

	@Override
	public int skipBytes(int n) {
		int skipped = ranged(n);
		bb.position(bb.position() + skipped);
		return skipped;
	}

	@Override
	public boolean readBoolean() {
		return bb.get() != 0;
	}

	@Override
	public byte readByte() {
		return bb.get();
	}

	@Override
	public int readUnsignedByte() {
		return Byte.toUnsignedInt(bb.get());
	}

	@Override
	public short readShort() {
		return bb.getShort();
	}

	@Override
	public int readUnsignedShort() {
		return Short.toUnsignedInt(bb.getShort());
	}

	@Override
	public char readChar() {
		return bb.getChar();
	}

	@Override
	public int readInt() {
		return bb.getInt();
	}

	@Override
	public long readLong() {
		return bb.getLong();
	}

	@Override
	public float readFloat() {
		return bb.getFloat();
	}

	@Override
	public double readDouble() {
		return bb.getDouble();
	}

	@Override
	@Deprecated
	public String readLine() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String readUTF() throws IOException {
		int size = readUnsignedShort();
		char[] string = new char[size];
		int len = 0;
		for (int i = 0; i < size; i++, len++) {
			int b = readUnsignedByte();
			if ((b > 0x00) && (b < 0x80)) {
				string[len] = (char) b;
			} else {
				switch (b >> 4) {
					// 2 byte encoding
					case 0b1100 :
					case 0b1101 : {
						if (bb.remaining() < 1) {
							throw new UTFDataFormatException("partial multi byte charater at end");
						}
						int b2 = readUnsignedByte();
						if ((b2 & 0b1100_0000) != 0b1000_0000) {
							throw new UTFDataFormatException("bad encoding at byte: " + i);
						}
						string[len] = (char) (((b & 0x1F) << 6) | (b2 & 0x3F));
						i++;
						break;
					}
					// 3 byte encoding
					case 0b1110 : {
						if (bb.remaining() < 2) {
							throw new UTFDataFormatException("partial multi byte charater at end");
						}
						int b2 = readUnsignedByte();
						int b3 = readUnsignedByte();
						if (((b2 & 0b1100_0000) != 0b1000_0000) || ((b3 & 0b1100_0000) != 0b1000_0000)) {
							throw new UTFDataFormatException("bad encoding at byte: " + i);
						}
						string[len] = (char) (((b & 0x0F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F));
						i += 2;
						break;
					}
					// invalid encoding
					default : {
						throw new UTFDataFormatException("bad encoding at byte: " + i);
					}
				}
			}
		}
		return new String(string, 0, len);
	}
}
