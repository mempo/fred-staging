package freenet.node;

import freenet.io.comm.PeerParseException;
import freenet.io.comm.ReferenceSignatureVerificationException;
import freenet.support.SimpleFieldSet;

public class SeedServerPeerNode extends PeerNode {

	public SeedServerPeerNode(SimpleFieldSet fs, Node node2, NodeCrypto crypto, PeerManager peers, boolean fromLocal, OutgoingPacketMangler mangler) throws FSParseException, PeerParseException, ReferenceSignatureVerificationException {
		super(fs, node2, crypto, peers, fromLocal, mangler, true);
	}

	public PeerNodeStatus getStatus() {
		return new PeerNodeStatus(this);
	}

	public boolean isOpennet() {
		return false;
	}

	public boolean isSearchable() {
		return false;
	}

	public boolean equals(Object o) {
		// Only equal to seednode of its own type.
		// Different to an OpennetPeerNode with the same identity!
		if(o instanceof SeedClientPeerNode) {
			return super.equals(o);
		} else return false;
	}
	
	public void onSuccess(boolean insert, boolean ssk) {
		// Ignore
	}

	public boolean isRoutingCompatible() {
		return false;
	}

	public boolean recordStatus() {
		return false;
	}

}
