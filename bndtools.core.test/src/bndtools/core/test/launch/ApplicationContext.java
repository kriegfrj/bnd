package bndtools.core.test.launch;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.osgi.framework.Bundle;
import org.osgi.service.component.annotations.Component;

public class ApplicationContext implements IApplicationContext {

	static final Map<?,?> ARGS;
	
	static {
		Map<String, Object> args = new HashMap<>();
		args.put(IApplicationContext.APPLICATION_ARGS, new String[] {});
		ARGS = args;
	}
	
	@Override
	public Map<?,?> getArguments() {
		return ARGS;
	}

	@Override
	public void applicationRunning() {
	}

	@Override
	public String getBrandingApplication() {
		return "Bndtools Integration Test";
	}

	@Override
	public String getBrandingName() {
		return "Bndtools";
	}

	@Override
	public String getBrandingDescription() {
		return "Integration tests for Bndtools";
	}

	@Override
	public String getBrandingId() {
		return "org.bndtools";
	}

	@Override
	public String getBrandingProperty(String key) {
		return null;
	}

	@Override
	public Bundle getBrandingBundle() {
		return null;
	}

	@Override
	public void setResult(Object result, IApplication application) {
	}

}
