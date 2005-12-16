package freenet.keys;

import java.io.IOException;

import freenet.support.Bucket;
import freenet.support.BucketFactory;

public interface ClientKeyBlock {

	/** Decode with the key
	 * @param factory The BucketFactory to use to create the Bucket to return the data in.
	 * @param maxLength The maximum size of the returned data in bytes.
	 */
	Bucket decode(BucketFactory factory, int maxLength) throws KeyDecodeException, IOException;

	/**
	 * Does the block contain metadata? If not, it contains real data.
	 */
	boolean isMetadata();

}
