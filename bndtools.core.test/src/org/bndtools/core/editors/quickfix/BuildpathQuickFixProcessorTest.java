package org.bndtools.core.editors.quickfix;

import org.eclipse.ui.wizards.datatransfer.FileSystemStructureProvider;
import org.eclipse.ui.wizards.datatransfer.ImportOperation;

import static org.eclipse.core.resources.IResource.FORCE; 
import static org.eclipse.core.resources.IResource.ALWAYS_DELETE_PROJECT_CONTENT; 
import static org.bndtools.core.testutils.ResourceLock.TEST_WORKSPACE;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bndtools.builder.BndProjectNature;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.ui.text.correction.AssistContext;
import org.eclipse.jdt.internal.ui.text.correction.ProblemLocation;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.ui.dialogs.IOverwriteQuery;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.test.junit5.context.BundleContextParameter;

import aQute.lib.io.IO;
import bndtools.Plugin;

// All of these tests manipulate the same workspace so they can't run in parallel.
@SuppressWarnings("restriction")
@Execution(SAME_THREAD)
@ResourceLock(TEST_WORKSPACE)
public class BuildpathQuickFixProcessorTest {
	static IPackageFragment pack;
	
	@BundleContextParameter
	BundleContext bc;
	
	BuildpathQuickFixProcessor sut;
	
	@BeforeAll
	static void beforeAll() throws Exception {
		Bundle b = FrameworkUtil.getBundle(BuildpathQuickFixProcessorTest.class);
		Path srcRoot = Paths.get(b.getBundleContext().getProperty("bndtools.core.test.workspaces"));
		Path ourRoot = srcRoot.resolve("org/bndtools/core/editors/quickfix");

		// Clean the workspace
		IWorkspaceRoot wsr = ResourcesPlugin.getWorkspace().getRoot();
		IProject[] projects = wsr.getProjects();
		CountDownLatch deleteFlag = new CountDownLatch(projects.length);
		for (IProject project : projects) {
			project.delete(true, true, new NullProgressMonitor() {
				@Override
				public void done() {
					deleteFlag.countDown();
				}
			});
		}
		deleteFlag.await(10000, TimeUnit.MILLISECONDS);
		File rootFile = wsr.getLocation().toFile();
//		for (File file : rootFile.listFiles()) {
//			if (file.getName().equals(".metadata")) {
//				continue;
//			}
//			IO.deleteWithException(file);
//		}
		
		IOverwriteQuery overwriteQuery = new IOverwriteQuery() {
	        public String queryOverwrite(String file) { return ALL; }
	};

//	String baseDir = ourRoot; // location of files to import
	IProject project = wsr.getProject("test");
	ImportOperation importOperation = new ImportOperation(project.getFullPath(),
	        ourRoot.toFile(), FileSystemStructureProvider.INSTANCE, overwriteQuery);
	importOperation.setCreateContainerStructure(false);
	CountDownLatch flag = new CountDownLatch(1);
	importOperation.run(new NullProgressMonitor() {
		@Override
		public void done() {
			flag.countDown();
		}
	});
	flag.await(10000, TimeUnit.MILLISECONDS);
	
		
//		Path wsrootAbs = rootFile.toPath();
//		IO.copy(ourRoot, wsrootAbs);
//
//		wsr.refreshLocal(IResource.DEPTH_INFINITE, null);
//		
		if (!project.exists()) {
			project.create(null);
		}
		project.open(null);
		IJavaProject javaProject = JavaCore.create(project); 

		// And bnd nature to the project so that the repos will be
		// downloaded and added.
//	    IProjectDescription desc = project.getDescription();
//	    String[] prevNatures = desc.getNatureIds();
//	    String[] newNatures = new String[prevNatures.length + 2];
//	    System.arraycopy(prevNatures, 0, newNatures, 0, prevNatures.length);
//	    newNatures[prevNatures.length] = JavaCore.NATURE_ID;
//	    newNatures[prevNatures.length + 1] = Plugin.BNDTOOLS_NATURE;
//	    desc.setNatureIds(newNatures);
//	    CountDownLatch natureFlag = new CountDownLatch(1);
//	    project.setDescription(desc, new NullProgressMonitor()
//	    {
//
//			@Override
//			public void done() {
//				System.err.println("Done adding nature!");
//				natureFlag.countDown();
//			}
//	    });
//	    
//		flag.await(10000, TimeUnit.MILLISECONDS);
//		System.err.println("Finished waitings for nature to be added");
	    
		IFolder sourceFolder = project.getFolder("src");
		if (!sourceFolder.exists()) {
			sourceFolder.create(true, true, null);
		}

		IPackageFragmentRoot root = javaProject.getPackageFragmentRoot(sourceFolder);
		pack = root.createPackageFragment("test", false, null);
		Job.getJobManager()
			.join(ResourcesPlugin.FAMILY_AUTO_BUILD, null);
	}
	
	@BeforeEach
	void before() throws Exception {
		sut = new BuildpathQuickFixProcessor();
	}

	@Test
	void testExample() throws Exception {
		setupAST("Something",
				"package test; import org.osgi.framework.Bundle; import java.util.ArrayList; public class Something { ArrayList<?> myList; "
				+ "public static void main(String[] args) {\n" + "		ArrayList<String> al = null;\n" + "		int j =0;\n"
				+ "		System.out.println(j);\n" + "		System.out.println(al);\r\n" + "	}}");
	}
	
	private void setupAST(String className, String source) throws Exception {

		ICompilationUnit icu = pack.createCompilationUnit(className + ".java", source, true, null);
		

		ASTParser parser = ASTParser.newParser(AST.JLS11);
		Map<String, String> options = JavaCore.getOptions();
		// Need to set 1.5 or higher for the "import static" syntax to work.
		// Need to set 1.8 or higher to test parameterized type usages.
		JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
		parser.setCompilerOptions(options);
		parser.setSource(icu);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setUnitName(className + ".java");
		parser.setEnvironment(new String[] {}, new String[] {}, new String[] {}, true);
		CompilationUnit cu = (CompilationUnit) parser.createAST(null);

		System.err.println("cu: " + cu);
		
		IProblemLocation[] locs = Stream.of(cu.getProblems())
			.map(ProblemLocation::new)
			.toArray(IProblemLocation[]::new);
		System.err.println("Problems: " + Stream.of(locs)
			.map(IProblemLocation::toString)
			.collect(Collectors.joining(",")));
		
		IInvocationContext context = new AssistContext(icu, 51, 1);
		IJavaCompletionProposal[] proposals = sut.getCorrections(context, locs);

		if (proposals != null) {
			System.err.println("Proposals: "
					+ Stream.of(proposals).map(IJavaCompletionProposal::toString).collect(Collectors.joining("\n")));
		} else {
			System.err.println("No proposals");
		}
	}
}
