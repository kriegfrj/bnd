package bndtools.core.test.utils;

import static bndtools.core.test.utils.TaskUtils.log;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.ui.dialogs.IOverwriteQuery;
import org.eclipse.ui.wizards.datatransfer.FileSystemStructureProvider;
import org.eclipse.ui.wizards.datatransfer.ImportOperation;

import aQute.bnd.build.Workspace;
import aQute.bnd.exceptions.Exceptions;
import bndtools.central.Central;

public class WorkspaceImporter {
	private static final IOverwriteQuery	overwriteQuery	= file -> IOverwriteQuery.ALL;

	private final Path						root;

	/**
	 * @param root the root path to the resources directory where the template
	 *            projects are stored.
	 */
	public WorkspaceImporter(Path root) {
		this.root = root;
	}

	public void reimportProject(String projectName) {
		try {
			IWorkspaceRoot wsr = ResourcesPlugin.getWorkspace()
				.getRoot();

			IProject project = wsr.getProject(projectName);
			if (project.exists()) {
				project.delete(true, true, null);
			}
			importProject(root.resolve(projectName));
		} catch (Exception e) {
			throw Exceptions.duck(e);
		}
	}

	public void importProject(String project) {
		importProject(root.resolve(project));
	}

	public static void cleanWorkspace() {
		IWorkspace ws = ResourcesPlugin.getWorkspace();
		IWorkspaceRoot wsr = ResourcesPlugin.getWorkspace()
			.getRoot();

		final CountDownLatch flag = new CountDownLatch(1);
		TaskUtils.log("Clean workspace");
		AtomicBoolean shouldWait = new AtomicBoolean(false);
		try {
			ws.run(monitor -> {
				IProject cnfProject = wsr.getProject(Workspace.CNFDIR);
				if (cnfProject.exists() && cnfProject.isOpen() && cnfProject.getFile(Workspace.BUILDFILE)
					.exists()) {
					// Wait for Workspace object to be complete.
					Central.onAnyWorkspace(bndWS -> {
						flag.countDown();
					});
					shouldWait.set(true);
				}
				// Clean the workspace
				IProject[] existingProjects = wsr.getProjects();
				for (IProject project : existingProjects) {
					project.delete(true, true, new NullProgressMonitor());
				}
			}, new NullProgressMonitor());
		} catch (CoreException e) {
			throw Exceptions.duck(e);
		}
		if (shouldWait.get()) {
			// Once the exclusive access rule has finished, we wait for the
			// onAnyWorkspace() event.
			TaskUtils.waitForFlag(flag, "cleanWorkspace()");
		}
	}

	public static void importProject(Path sourceProject) {
		IWorkspaceRoot wsr = ResourcesPlugin.getWorkspace()
			.getRoot();

		log("importing sourceProject: " + sourceProject);
		String projectName = sourceProject.getFileName()
			.toString();
		IProject project = wsr.getProject(projectName);
		ImportOperation importOperation = new ImportOperation(project.getFullPath(), sourceProject.toFile(),
			FileSystemStructureProvider.INSTANCE, overwriteQuery);
		importOperation.setCreateContainerStructure(false);
		try {
			importOperation.run(new NullProgressMonitor());
		} catch (InterruptedException e) {
			throw Exceptions.duck(e);
		} catch (InvocationTargetException e) {
			throw Exceptions.duck(e.getTargetException());
		}
	}

	public void importWorkspace() throws InterruptedException {
		importWorkspace(root);
	}

	public static void importWorkspace(Path ourRoot) throws InterruptedException {
		IWorkspace ws = ResourcesPlugin.getWorkspace();
		IWorkspaceRoot wsr = ResourcesPlugin.getWorkspace()
			.getRoot();

		final CountDownLatch flag = new CountDownLatch(1);
		TaskUtils.log("import workspace: " + ourRoot);
		try {
			List<Path> sourceProjects = Files.walk(ourRoot, 1)
				.filter(x -> !x.equals(ourRoot))
				.collect(Collectors.toList());

			ws.run(monitor -> {
				// Wait for Workspace object to be complete.
				// Central.onCnfWorkspace(bndWS -> {
				// flag.countDown();
				// });

				log("importing " + sourceProjects.size() + " projects");
				sourceProjects.forEach(path -> importProject(path));
				log("done importing");
			}, new NullProgressMonitor());
		} catch (Exception e) {
			throw Exceptions.duck(e);
		}
		// Once the exclusive access rule has finished, we wait for the
		// onCfWorkspace() event.
		// TaskUtils.waitForFlag(flag, "importWorkspace()");
		Job.getJobManager()
			.join(ResourcesPlugin.FAMILY_MANUAL_BUILD, null);
		Job.getJobManager()
			.join(ResourcesPlugin.FAMILY_AUTO_BUILD, null);
	}
}
