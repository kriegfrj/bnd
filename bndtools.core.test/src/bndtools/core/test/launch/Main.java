package bndtools.core.test.launch;

import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.eclipse.ui.internal.ide.application.DelayedEventsProcessor;
import org.eclipse.ui.internal.ide.application.IDEWorkbenchAdvisor;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Component;

public class Main implements Runnable {

	ServiceRegistration<Runnable> reg;

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
