package bndtools.core.test;

import org.eclipse.e4.ui.internal.workbench.swt.E4Application;
import org.eclipse.equinox.app.IApplicationContext;

public class Application extends E4Application {

	@Override
	public Object start(IApplicationContext applicationContext) throws Exception {
		System.err.println("=========>We are starting!");
		return super.start(applicationContext);
	}

}
