package bndtools.core.test;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

	public static BundleContext bc;
	
	@Override
	public void start(BundleContext context) throws Exception {
		bc = context;
		System.err.println("Starting bundle: " + context);
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		// TODO Auto-generated method stub

	}

}
