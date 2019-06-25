package aQute.tester.utils;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleRevision;

import aQute.lib.exceptions.Exceptions;

public class TestClassName {
	private String	pkg;
	private String	className;

	Bundle			bundle;
	Class<?>		me;

	public TestClassName(String pkg, String className) {
		this.pkg = pkg;
		this.className = className;
	}

	public String getPkg() {
		return pkg;
	}

	public String getClassName() {
		return className;
	}

	public String fqName() {
		return pkg + '.' + className;
	}

	@Override
	public String toString() {
		return fqName();
	}

	public void setStatic(String field, Object value) {
		checkMyself();
		try {
			Field f = getOurClass().getField(field);
			f.set(null, value);
		} catch (Exception e) {
			Exceptions.duck(e);
		}
	}

	@SuppressWarnings("unchecked")
	public <T> T getStatic(String field) {
		checkMyself();
		try {
			Field f = getOurClass().getField(field);
			return (T) f.get(null);
		} catch (Exception e) {
			throw Exceptions.duck(e);
		}
	}

	// Convenience wrappers for common fields in the test classes
	@SuppressWarnings("unchecked")
	public BundleContext getBundleContext() {
		return ((AtomicReference<BundleContext>) getStatic("bundleContext")).get();
	}

	@SuppressWarnings("unchecked")
	public BundleContext getActualBundleContext() {
		return ((AtomicReference<BundleContext>) getStatic("actualBundleContext")).get();
	}

	@SuppressWarnings("unchecked")
	public Thread getCurrentThread() {
		return ((AtomicReference<Thread>) getStatic("currentThread")).get();
	}

	public boolean getFlag(String flag) {
		return ((AtomicBoolean) getStatic(flag)).get();
	}

	private void checkMyself() {
		if (bundle == null) {
			throw new IllegalStateException("TestClassName " + fqName() + " hasn't been installed in a bundle yet");
		}
		if (me == null) {
			Bundle host = getHost(bundle).orElseThrow(() -> new IllegalStateException(
				"TestClassName " + fqName() + " is in a fragment that has not yet attached: " + bundle));
			try {
				me = bundle.loadClass(fqName());
			} catch (ClassNotFoundException e) {
				throw new IllegalStateException("TestClassName " + fqName() + " failed to load from bundle " + host
					+ (bundle == host ? "" : " (fragment " + bundle + ")"), e);
			}
		}
	}

	/**
	 * Convenience version that allows you to avoid explicit cast (neater for
	 * chaining).
	 */
	@SuppressWarnings("unchecked")
	public <T> T getStatic(Class<T> fieldType, String field) {
		checkMyself();
		try {
			Field f = me.getField(field);
			return (T) f.get(null);
		} catch (Exception e) {
			throw Exceptions.duck(e);
		}
	}

	public Class<?> getOurClass() {
		return me;
	}

	public Bundle getBundle() {
		return bundle;
	}

	void setBundle(Bundle bundle) {
		this.bundle = bundle;
		// Clear cached class object now that we've been assigned to a new
		// bundle.
		this.me = null;
	}

	static Optional<Bundle> getHost(Bundle bundle) {
		if (bundle.getHeaders()
			.get(aQute.bnd.osgi.Constants.FRAGMENT_HOST) == null) {
			return Optional.of(bundle);
		}
		return Optional.ofNullable(bundle.adapt(BundleRevision.class))
			.filter(revision -> revision.getWiring() != null)
			.map(revision -> revision.getWiring()
				.getRequiredWires(BundleRevision.HOST_NAMESPACE))
			.flatMap(wires -> wires.stream()
				.map(wire -> wire.getProviderWiring()
					.getBundle())
				.findFirst());
	}

	void reset() {
		bundle = null;
		me = null;
	}
}