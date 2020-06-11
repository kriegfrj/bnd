package bndtools.jareditor.internal;

import org.bndtools.api.BndtoolsConstants;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.JavaRuntime;

public class TemporaryProject {

	public TemporaryProject() {}

	private void addNaturesToProject(IProject proj, String... natureIds)
		throws CoreException {
		IProjectDescription description = proj.getDescription();
		String[] prevNatures = description.getNatureIds();
		String[] newNatures = new String[prevNatures.length + natureIds.length];
		System.arraycopy(prevNatures, 0, newNatures, 0, prevNatures.length);

		for (int i = prevNatures.length; i < newNatures.length; i++) {
			newNatures[i] = natureIds[i - prevNatures.length];
		}

		description.setNatureIds(newNatures);
		proj.setDescription(description, new NullProgressMonitor());
	}

	private void checkForSupportProject() throws CoreException {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceRoot root = workspace.getRoot();
		IProject project = root.getProject(BndtoolsConstants.BNDTOOLS_JAREDITOR_TEMP_PROJECT_NAME);
		IProgressMonitor monitor = new NullProgressMonitor();

		if (!project.exists()) {
			createProject();
		}
		else if (!project.isOpen()) {
			try {
				project.open(monitor);
			} catch (Exception e) {
				// recreate project since there is something wrong with this one
				project.delete(true, monitor);
				createProject();
			}
		}

		makeFolders(project.getFolder("temp"));

		computeClasspath(JavaCore.create(project), new NullProgressMonitor());
	}

	private void computeClasspath(IJavaProject project, IProgressMonitor monitor) {
		IClasspathEntry[] classpath = new IClasspathEntry[2];
		classpath[0] = JavaCore.newContainerEntry(JavaRuntime.newDefaultJREContainerPath());
		IProject javaProject = project.getProject();
		IFolder folder = javaProject.getFolder("src");
		classpath[1] = JavaCore.newSourceEntry(folder.getFullPath());

		try {
			project.setRawClasspath(classpath, monitor);
		} catch (JavaModelException jme) {}
	}

	private IProject createProject() throws CoreException {
		IProgressMonitor monitor = new NullProgressMonitor();
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IProject project = workspace.getRoot()
			.getProject(BndtoolsConstants.BNDTOOLS_JAREDITOR_TEMP_PROJECT_NAME);

		if (project.exists()) {
			if (!project.isOpen()) {
				project.open(monitor);
			}

			return project;
		}

		IProjectDescription description = workspace
			.newProjectDescription(BndtoolsConstants.BNDTOOLS_JAREDITOR_TEMP_PROJECT_NAME);

		IPath stateLocation = Plugin.getInstance()
			.getStateLocation();

		description.setLocation(stateLocation.append(BndtoolsConstants.BNDTOOLS_JAREDITOR_TEMP_PROJECT_NAME));

		project.create(description, monitor);
		project.open(monitor);

		makeFolders(project.getFolder("src"));

		addNaturesToProject(project, JavaCore.NATURE_ID);

		IJavaProject jProject = JavaCore.create(project);
		IPath fullPath = project.getFullPath();
		jProject.setOutputLocation(fullPath.append("bin"), monitor);

		computeClasspath(jProject, monitor);

		return project;
	}

	public IJavaProject getJavaProject() throws CoreException {
		checkForSupportProject();
		IProject project = ResourcesPlugin.getWorkspace()
			.getRoot()
			.getProject(BndtoolsConstants.BNDTOOLS_JAREDITOR_TEMP_PROJECT_NAME);

		if (project.exists() && project.isOpen() && project.hasNature(JavaCore.NATURE_ID)) {
			return JavaCore.create(project);
		}
		return null;
	}

	private void makeFolders(IFolder folder) throws CoreException {
		if (folder == null) {
			return;
		}

		IContainer parent = folder.getParent();

		if (parent instanceof IFolder) {
			makeFolders((IFolder) parent);
		}

		if (!folder.exists()) {
			folder.create(true, true, null);
		}
	}
}
