package freenet.support.io;

import java.io.IOException;

/**
 *
 * @author unknown
 */
public class ByteArrayRandomAccessThing implements RandomAccessThing {

	private final byte[] data;
	private boolean readOnly;

	/**
	 *
	 * @param padded
	 */
	public ByteArrayRandomAccessThing(byte[] padded) {
		this.data = padded;
	}

	public void close() {
		// Do nothing
	}

	/**
	 *
	 * @param fileOffset
	 * @param buf
	 * @param bufOffset
	 * @param length
	 * @throws IOException
	 */
	public void pread(long fileOffset, byte[] buf, int bufOffset, int length)
			throws IOException {
		if(fileOffset < 0) {
			throw new IOException("Cannot read before zero");
		}
		if(fileOffset + length > data.length) {
			throw new IOException("Cannot read after end: trying to read from " + fileOffset + " to " + (fileOffset + length) + " on block length " + data.length);
		}
		System.arraycopy(data, (int) fileOffset, buf, bufOffset, length);
	}

	/**
	 *
	 * @param fileOffset
	 * @param buf
	 * @param bufOffset
	 * @param length
	 * @throws IOException
	 */
	public void pwrite(long fileOffset, byte[] buf, int bufOffset, int length)
			throws IOException {
		if(fileOffset < 0) {
			throw new IOException("Cannot write before zero");
		}
		if(fileOffset + length > data.length) {
			throw new IOException("Cannot write after end: trying to write from " + fileOffset + " to " + (fileOffset + length) + " on block length " + data.length);
		}
		if(readOnly) {
			throw new IOException("Read-only");
		}
		System.arraycopy(buf, bufOffset, data, (int) fileOffset, length);
	}

	/**
	 *
	 * @return
	 * @throws IOException
	 */
	public long size() throws IOException {
		return data.length;
	}

	/**
	 *
	 */
	public void setReadOnly() {
		readOnly = true;
	}

}
