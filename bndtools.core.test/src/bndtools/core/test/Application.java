package bndtools.core.test;

//import org.eclipse.e4.ui.internal.workbench.swt.E4Application;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.ui.internal.ide.application.IDEApplication;

public class Application extends IDEApplication {

	@Override
	public Object start(IApplicationContext applicationContext) throws Exception {
		System.err.println("=========>We are starting!");
		return super.start(applicationContext);
	}

}
