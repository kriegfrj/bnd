package bndtools.launch.bnd;

import org.bndtools.api.launch.LaunchConstants;
import org.eclipse.debug.ui.ILaunchShortcut2;
import org.osgi.service.component.annotations.Component;

import bndtools.launch.api.AbstractLaunchShortcut;

@Component(property = {
	"eclipse.id=bnd.launch.runShortcut"
}, service = ILaunchShortcut2.class)
public class RunShortcut extends AbstractLaunchShortcut {
	public RunShortcut() {
		super(LaunchConstants.LAUNCH_ID_OSGI_NATIVE);
	}
}
