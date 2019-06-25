package aQute.tester.test;

import java.util.concurrent.atomic.AtomicReference;

import org.osgi.framework.BundleContext;

import junit.framework.TestCase;

public class JUnit3Test extends TestCase {
	public static AtomicReference<Thread>			currentThread		= new AtomicReference<>();
	public static AtomicReference<BundleContext>	bundleContext		= new AtomicReference<>();
	public static AtomicReference<BundleContext>	actualBundleContext	= new AtomicReference<>();

	public void testSomething() {
		currentThread.set(Thread.currentThread());
		actualBundleContext.set(bundleContext.get());
	}

	public void notATest() {
	}

	static public void setBundleContext(BundleContext bc) {
		bundleContext.set(bc);
	}
}