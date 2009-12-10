/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.support.io;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.util.Random;

import freenet.client.DefaultMIMETypes;
import freenet.support.Logger;
import freenet.support.SizeUtil;

/**
 *
 * @author unknown
 */
final public class FileUtil {

	private static final int BUFFER_SIZE = 4096;

	/** Round up a value to the next multiple of a power of 2 */
	private static final long roundup_2n(long val, int blocksize) {
		int mask = blocksize - 1;
		return (val + mask) & ~mask;
	}

	/**
	 * Guesstimate real disk usage for a file with a given filename, of a given length.
	 *
	 * @param file
	 * @param flen
	 * @return
	 */
	public static long estimateUsage(File file, long flen) {
		/**
		 * It's possible that none of these assumptions are accurate for any filesystem;
		 * this is intended to be a plausible worst case.
		 */
		// Assume 4kB clusters for calculating block usage (NTFS)
		long blockUsage = roundup_2n(flen, 4096);
		// Assume 512 byte filename entries, with 100 bytes overhead, for filename overhead (NTFS)
		String filename = file.getName();
		int nameLength = filename.getBytes().length + 100;
		long filenameUsage = roundup_2n(nameLength, 512);
		// Assume 50 bytes per block tree overhead with 1kB blocks (reiser3 worst case)
		long extra = (roundup_2n(flen, 1024) / 1024) * 50;
		return blockUsage + filenameUsage + extra;
	}

	/**
	 *  Is possParent a parent of filename?
	 * Why doesn't java provide this? :(
	 *
	 * @param poss
	 * @param filename
	 * @return
	 */
	public static boolean isParent(File poss, File filename) {
		File canon = FileUtil.getCanonicalFile(poss);
		File canonFile = FileUtil.getCanonicalFile(filename);

		if(isParentInner(poss, filename)) {
			return true;
		}
		if(isParentInner(poss, canonFile)) {
			return true;
		}
		if(isParentInner(canon, filename)) {
			return true;
		}
		if(isParentInner(canon, canonFile)) {
			return true;
		}
		return false;
	}

	private static boolean isParentInner(File possParent, File filename) {
		while (true) {
			if(filename.equals(possParent)) {
				return true;
			}
			filename = filename.getParentFile();
			if(filename == null) {
				return false;
			}
		}
	}

	/**
	 *
	 * @param file
	 * @return
	 */
	public static File getCanonicalFile(File file) {
		// Having some problems storing File's in db4o ...
		// It would start up, and canonicalise a file with path "/var/lib/freenet-experimental/persistent-temp-24374"
		// to /var/lib/freenet-experimental/var/lib/freenet-experimental/persistent-temp-24374 
		// (where /var/lib/freenet-experimental is the current working dir)
		// Regenerating from path worked. So do that here.
		// And yes, it's voodoo.
		file = new File(file.getPath());
		File result;
		try {
			result = file.getAbsoluteFile().getCanonicalFile();
		} catch (IOException e) {
			result = file.getAbsoluteFile();
		}
		return result;
	}

	/**
	 *
	 * @param file
	 * @return
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static String readUTF(File file) throws FileNotFoundException, IOException {
		return readUTF(file, 0);
	}

	/**
	 *
	 * @param file
	 * @param offset
	 * @return
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static String readUTF(File file, long offset) throws FileNotFoundException, IOException {
		StringBuilder result = new StringBuilder();
		FileInputStream fis = null;
		BufferedInputStream bis = null;
		InputStreamReader isr = null;

		try {
			fis = new FileInputStream(file);
			skipFully(fis, offset);
			bis = new BufferedInputStream(fis);
			isr = new InputStreamReader(bis, "UTF-8");

			char[] buf = new char[4096];
			int length = 0;

			while ((length = isr.read(buf)) > 0) {
				result.append(buf, 0, length);
			}

		} finally {
			Closer.close(isr);
			Closer.close(bis);
			Closer.close(fis);
		}
		return result.toString();
	}

	/**
	 * Reliably skip a number of bytes or throw.
	 *
	 * @param is
	 * @param skip
	 * @throws IOException
	 */
	public static void skipFully(InputStream is, long skip) throws IOException {
		long skipped = 0;
		while (skipped < skip) {
			long x = is.skip(skip - skipped);
			if(x <= 0) {
				throw new IOException("Unable to skip " + (skip - skipped) + " bytes");
			}
			skipped += x;
		}
	}

	/**
	 *
	 * @param input
	 * @param target
	 * @return
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static boolean writeTo(InputStream input, File target) throws FileNotFoundException, IOException {
		DataInputStream dis = null;
		FileOutputStream fos = null;
		File file = File.createTempFile("temp", ".tmp", target.getParentFile());
		if(Logger.shouldLog(Logger.MINOR, FileUtil.class)) {
			Logger.minor(FileUtil.class, "Writing to " + file + " to be renamed to " + target);
		}

		try {
			dis = new DataInputStream(input);
			fos = new FileOutputStream(file);

			int len = 0;
			byte[] buffer = new byte[4096];
			while ((len = dis.read(buffer)) > 0) {
				fos.write(buffer, 0, len);
			}
		} catch (IOException e) {
			throw e;
		} finally {
			if(dis != null) {
				dis.close();
			}
			if(fos != null) {
				fos.close();
			}
		}

		if(FileUtil.renameTo(file, target)) {
			return true;
		} else {
			file.delete();
			return false;
		}
	}

	/**
	 *
	 * @param orig
	 * @param dest
	 * @return
	 */
	public static boolean renameTo(File orig, File dest) {
		// Try an atomic rename
		// Shall we prevent symlink-race-conditions here ?
		if(orig.equals(dest)) {
			throw new IllegalArgumentException("Huh? the two file descriptors are the same!");
		}
		if(!orig.exists()) {
			throw new IllegalArgumentException("Original doesn't exist!");
		}
		if(!orig.renameTo(dest)) {
			// Not supported on some systems (Windows)
			if(!dest.delete()) {
				if(dest.exists()) {
					Logger.error("FileUtil", "Could not delete " + dest + " - check permissions");
				}
			}
			if(!orig.renameTo(dest)) {
				Logger.error("FileUtil", "Could not rename " + orig + " to " + dest +
						(dest.exists() ? " (target exists)" : "") +
						(orig.exists() ? " (source exists)" : "") +
						" - check permissions");
				return false;
			}
		}
		return true;
	}

	/**
	 * Like renameTo(), but can move across filesystems, by copying the data.
	 * @param orig
	 * @param dest
	 * @param overwrite
	 * @return
	 */
	public static boolean moveTo(File orig, File dest, boolean overwrite) {
		if(orig.equals(dest)) {
			throw new IllegalArgumentException("Huh? the two file descriptors are the same!");
		}
		if(!orig.exists()) {
			throw new IllegalArgumentException("Original doesn't exist!");
		}
		if(dest.exists()) {
			if(overwrite) {
				dest.delete();
			} else {
				System.err.println("Not overwriting " + dest + " - already exists moving " + orig);
				return false;
			}
		}
		if(!orig.renameTo(dest)) {
			// Copy the data
			InputStream is = null;
			OutputStream os = null;
			try {
				is = new FileInputStream(orig);
				os = new FileOutputStream(dest);
				copy(is, os, orig.length());
				is.close();
				is = null;
				os.close();
				os = null;
				orig.delete();
				return true;
			} catch (IOException e) {
				dest.delete();
				Logger.error(FileUtil.class, "Move failed from " + orig + " to " + dest + " : " + e, e);
				System.err.println("Move failed from " + orig + " to " + dest + " : " + e);
				e.printStackTrace();
				return false;
			} finally {
				Closer.close(is);
				Closer.close(os);
			}
		} else {
			return true;
		}
	}

	/**
	 *
	 * @param s
	 * @return
	 */
	public static String sanitize(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		for(int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if((c == '/') || (c == '\\') || (c == '%') || (c == '>') || (c == '<') || (c == ':') || (c == '\'') || (c == '\"') || (c == '|')) {
				continue;
			}
			if(Character.isDigit(c)) {
				sb.append(c);
			} else if(Character.isLetter(c)) {
				sb.append(c);
			} else if(Character.isWhitespace(c)) {
				sb.append(' ');
			} else if((c == '-') || (c == '_') || (c == '.')) {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	/**
	 *
	 * @param filename
	 * @param mimeType
	 * @return
	 */
	public static String sanitize(String filename, String mimeType) {
		filename = sanitize(filename);
		if(mimeType == null) {
			return filename;
		}
		if(filename.indexOf('.') >= 0) {
			String oldExt = filename.substring(filename.lastIndexOf('.'));
			if(DefaultMIMETypes.isValidExt(mimeType, oldExt)) {
				return filename;
			}
		}
		String defaultExt = DefaultMIMETypes.getExtension(filename);
		if(defaultExt == null) {
			return filename;
		} else {
			return filename + '.' + defaultExt;
		}
	}

	/**
	 * Find the length of an input stream. This method will consume the complete
	 * input stream until its {@link InputStream#read(byte[])} method returns
	 * <code>-1</code>, thus signalling the end of the stream.
	 * 
	 * @param source
	 *            The input stream to find the length of
	 * @return The numbe of bytes that can be read from the stream
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public static long findLength(InputStream source) throws IOException {
		long length = 0;
		byte[] buffer = new byte[BUFFER_SIZE];
		int read = 0;
		while (read > -1) {
			read = source.read(buffer);
			if(read != -1) {
				length += read;
			}
		}
		return length;
	}

	/**
	 * Copies <code>length</code> bytes from the source input stream to the
	 * destination output stream. If <code>length</code> is <code>-1</code>
	 * as much bytes as possible will be copied (i.e. until
	 * {@link InputStream#read()} returns <code>-1</code> to signal the end of
	 * the stream).
	 * 
	 * @param source
	 *            The input stream to read from
	 * @param destination
	 *            The output stream to write to
	 * @param length
	 *            The number of bytes to copy
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public static void copy(InputStream source, OutputStream destination, long length) throws IOException {
		long remaining = length;
		byte[] buffer = new byte[BUFFER_SIZE];
		int read = 0;
		while ((remaining == -1) || (remaining > 0)) {
			read = source.read(buffer, 0, ((remaining > BUFFER_SIZE) || (remaining == -1)) ? BUFFER_SIZE : (int) remaining);
			if(read == -1) {
				if(length == -1) {
					return;
				}
				throw new EOFException("stream reached eof");
			}
			destination.write(buffer, 0, read);
			remaining -= read;
		}
	}

	/** Delete everything in a directory. Only use this when we are *very sure* there is no
	 * important data below it!
	 * @param wd
	 * @return
	 */
	public static boolean removeAll(File wd) {
		if(!wd.isDirectory()) {
			System.err.println("DELETING FILE " + wd);
			if(!wd.delete() && wd.exists()) {
				Logger.error(FileUtil.class, "Could not delete file: " + wd);
				return false;
			}
		} else {
			File[] subfiles = wd.listFiles();
			for(int i = 0; i < subfiles.length; i++) {
				if(!removeAll(subfiles[i])) {
					return false;
				}
			}
			if(!wd.delete()) {
				Logger.error(FileUtil.class, "Could not delete directory: " + wd);
			}
		}
		return true;
	}

	/**
	 *
	 * @param file
	 * @param random
	 * @throws IOException
	 */
	public static void secureDelete(File file, Random random) throws IOException {
		// FIXME somebody who understands these things should have a look at this...
		if(!file.exists()) {
			return;
		}
		long size = file.length();
		if(size > 0) {
			RandomAccessFile raf = null;
			try {
				raf = new RandomAccessFile(file, "rw");
				raf.seek(0);
				long count;
				byte[] buf = new byte[4096];
				// First zero it out
				count = 0;
				while (count < size) {
					int written = (int) Math.min(buf.length, size - count);
					raf.write(buf, 0, written);
					count += written;
				}
				raf.getFD().sync();
				// Then ffffff it out
				for(int i = 0; i < buf.length; i++) {
					buf[i] = (byte) 0xFF;
				}
				raf.seek(0);
				count = 0;
				while (count < size) {
					int written = (int) Math.min(buf.length, size - count);
					raf.write(buf, 0, written);
					count += written;
				}
				raf.getFD().sync();
				// Then random data
				random.nextBytes(buf);
				raf.seek(0);
				count = 0;
				while (count < size) {
					int written = (int) Math.min(buf.length, size - count);
					raf.write(buf, 0, written);
					count += written;
				}
				raf.getFD().sync();
				raf.seek(0);
				// Then 0's again
				for(int i = 0; i < buf.length; i++) {
					buf[i] = 0;
				}
				count = 0;
				while (count < size) {
					int written = (int) Math.min(buf.length, size - count);
					raf.write(buf, 0, written);
					count += written;
				}
				raf.getFD().sync();
			} finally {
				Closer.close(raf);
			}
		}
		if((!file.delete()) && file.exists()) {
			throw new IOException("Unable to delete file");
		}
	}

	/**
	 *
	 * @param dir
	 * @return
	 */
	public static final long getFreeSpace(File dir) {
		// Use JNI to find out the free space on this partition.
		long freeSpace = -1;
		try {
			Class<? extends File> c = dir.getClass();
			Method m = c.getDeclaredMethod("getFreeSpace", new Class<?>[0]);
			if(m != null) {
				Long lFreeSpace = (Long) m.invoke(dir, new Object[0]);
				if(lFreeSpace != null) {
					freeSpace = lFreeSpace.longValue();
					System.err.println("Found free space on node's partition: on " + dir + " = " + SizeUtil.formatSize(freeSpace));
				}
			}
		} catch (NoSuchMethodException e) {
			// Ignore
			freeSpace = -1;
		} catch (Throwable t) {
			System.err.println("Trying to access 1.6 getFreeSpace(), caught " + t);
			freeSpace = -1;
		}
		return freeSpace;
	}

}
