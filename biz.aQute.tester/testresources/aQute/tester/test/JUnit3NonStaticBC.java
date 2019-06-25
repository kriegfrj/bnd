package aQute.tester.test;

import java.util.concurrent.atomic.AtomicReference;

import org.osgi.framework.BundleContext;

import junit.framework.TestCase;

public class JUnit3NonStaticBC extends TestCase {
	public static AtomicReference<BundleContext>	bundleContext		= new AtomicReference<>();
	public static AtomicReference<BundleContext>	actualBundleContext	= new AtomicReference<>();

	public void testSomething() {
		actualBundleContext.set(bundleContext.get());
	}

	public void setBundleContext(BundleContext bc) {
		bundleContext.set(bc);
	}
}
