package aQute.junit.bundle.engine.discovery;

import org.junit.platform.engine.DiscoverySelector;
import org.osgi.framework.Bundle;
import org.osgi.framework.VersionRange;

public class BundleSelector implements DiscoverySelector {

	private String			symbolicName;
	private VersionRange	version;

	public String getSymbolicName() {
		return symbolicName;
	}

	public VersionRange getVersion() {
		return version;
	}

	public static BundleSelector selectBundle(String bsn) {
		return selectBundle(bsn, "0");
	}

	public static BundleSelector selectBundle(String bsn, String versionRange) {
		BundleSelector s = new BundleSelector();
		s.symbolicName = bsn;
		s.version = VersionRange.valueOf(versionRange);
		return s;
	}

	public static BundleSelector selectBundle(Bundle bundle) {
		BundleSelector s = new BundleSelector();
		s.symbolicName = bundle.getSymbolicName();
		s.version = new VersionRange('[', bundle.getVersion(), bundle.getVersion(), ']');
		return s;
	}
}
