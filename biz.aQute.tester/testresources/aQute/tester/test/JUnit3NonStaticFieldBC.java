package aQute.tester.test;

import java.util.concurrent.atomic.AtomicReference;

import org.osgi.framework.BundleContext;

import junit.framework.TestCase;

public class JUnit3NonStaticFieldBC extends TestCase {
	public BundleContext							context;
	public static AtomicReference<BundleContext>	actualBundleContext	= new AtomicReference<>();

	public void testSomething() {
		actualBundleContext.set(context);
	}
}
