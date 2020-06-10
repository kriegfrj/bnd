package bndtools.core.test.launch;

import java.util.Dictionary;
import java.util.Hashtable;

import org.eclipse.e4.ui.internal.workbench.swt.E4Application;
//import org.eclipse.e4.ui.internal.workbench.swt.E4Application;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.eclipse.ui.internal.ide.application.DelayedEventsProcessor;
import org.eclipse.ui.internal.ide.application.IDEApplication;
import org.eclipse.ui.internal.ide.application.IDEWorkbenchAdvisor;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator, Runnable {

	ServiceRegistration<Runnable> reg;

	@Override
	public void start(BundleContext context) throws Exception {
		System.err.println("Registering service!!!");
		Dictionary<String, Object> dictionary = new Hashtable<>();
		dictionary.put("main.thread", "true");
		reg = context.registerService(Runnable.class, this, dictionary);
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		System.err.println("UnRegistering service!!!");
		reg.unregister();
	}

	@Override
	public void run() {
		Display display = PlatformUI.createDisplay();
        DelayedEventsProcessor processor = new DelayedEventsProcessor(display);

		WorkbenchAdvisor advisor = new IDEWorkbenchAdvisor(processor);

		System.err.println("Running!");
		try {
			int retval = PlatformUI.createAndRunWorkbench(display, advisor);
			System.err.println("platform exited with retval: " + retval);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
