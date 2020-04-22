package org.bndtools.core.editors.quickfix;

import static org.bndtools.core.testutils.ResourceLock.TEST_WORKSPACE;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;
import static org.eclipse.jdt.core.compiler.IProblem.AmbiguousField;
import static org.eclipse.jdt.core.compiler.IProblem.AmbiguousType;
import static org.eclipse.jdt.core.compiler.IProblem.ImportNotFound;
import static org.eclipse.jdt.core.compiler.IProblem.IsClassPathCorrect;
import static org.eclipse.jdt.core.compiler.IProblem.UndefinedType;
import static org.eclipse.jdt.core.compiler.IProblem.UnusedImport;


import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
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
import org.eclipse.jdt.ui.text.java.IQuickFixProcessor;
import org.eclipse.ui.dialogs.IOverwriteQuery;
import org.eclipse.ui.wizards.datatransfer.FileSystemStructureProvider;
import org.eclipse.ui.wizards.datatransfer.ImportOperation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.test.junit5.context.BundleContextParameter;

import bndtools.central.Central;

// All of these tests manipulate the same workspace so they can't run in parallel.
@Execution(SAME_THREAD)
@ResourceLock(TEST_WORKSPACE)
@ExtendWith(SoftAssertionsExtension.class)
public class BuildpathQuickFixProcessorTest {
	static IPackageFragment pack;
	static Class<? extends IQuickFixProcessor> sutClass;

	@BundleContextParameter
	BundleContext bc;

	IQuickFixProcessor sut;

	static void log(String msg) {
		System.err.println(System.currentTimeMillis() + ": " + msg);
	}

	static IProgressMonitor countDownMonitor(CountDownLatch flag) {
		return new NullProgressMonitor() {
			@Override
			public void done() {
				flag.countDown();
			}
		};
	}
	
	@SuppressWarnings("unchecked")
	@BeforeAll
	static void beforeAll() throws Exception {
		Bundle b = FrameworkUtil.getBundle(BuildpathQuickFixProcessorTest.class);
		sutClass = (Class<? extends IQuickFixProcessor>) Central.class.getClassLoader()
				.loadClass("org.bndtools.core.editors.quickfix.BuildpathQuickFixProcessor");
		Path srcRoot = Paths.get(b.getBundleContext().getProperty("bndtools.core.test.workspaces"));
		Path ourRoot = srcRoot.resolve("org/bndtools/core/editors/quickfix");

		// Clean the workspace
		IWorkspaceRoot wsr = ResourcesPlugin.getWorkspace().getRoot();
		IProject[] projects = wsr.getProjects();
		log("deleting projects");
		for (IProject project : projects) {
			log("deleting project: " + project.getName());
			// I tried to use the IProgressMonitor instance to 
			project.delete(true, true, null);
		}

		IOverwriteQuery overwriteQuery = new IOverwriteQuery() {
			public String queryOverwrite(String file) {
				return ALL;
			}
		};

//	String baseDir = ourRoot; // location of files to import
		List<Path> sourceProjects = Files.walk(ourRoot, 1).filter(x -> !x.equals(ourRoot)).collect(Collectors.toList());
		CountDownLatch flag = new CountDownLatch(sourceProjects.size());

		for (Path sourceProject : sourceProjects) {
			String projectName = sourceProject.getFileName().toString();
			IProject project = wsr.getProject(projectName);
			ImportOperation importOperation = new ImportOperation(project.getFullPath(), sourceProject.toFile(),
					FileSystemStructureProvider.INSTANCE, overwriteQuery);
			importOperation.setCreateContainerStructure(false);
			importOperation.run(countDownMonitor(flag));
		}
		log("About to wait for imports to complete " + sourceProjects.size());
		flag.await(10000, TimeUnit.MILLISECONDS);
		log("done waiting for import to complete");

		IProject project = wsr.getProject("test");
		if (!project.exists()) {
			project.create(null);
		}
		project.open(null);
		IJavaProject javaProject = JavaCore.create(project);

		IFolder sourceFolder = project.getFolder("src");
		if (!sourceFolder.exists()) {
			sourceFolder.create(true, true, null);
		}

		IPackageFragmentRoot root = javaProject.getPackageFragmentRoot(sourceFolder);
		pack = root.createPackageFragment("test", false, null);
		Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, null);
	}

	@BeforeEach
	void before() throws Exception {
		sut = sutClass.newInstance();
	}

	@Test
	void testExample() throws Exception {
		setupAST("Something",
				"package test; import org.osgi.framework.Bundle; import java.util.ArrayList; public class Something { ArrayList<?> myList; "
						+ "public static void main(String[] args) {\n" + "		ArrayList<String> al = null;\n"
						+ "		int j =0;\n" + "		System.out.println(j);\n" + "		System.out.println(al);\r\n"
						+ "	}}");
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

		IProblemLocation[] locs = Stream.of(cu.getProblems()).map(ProblemLocation::new)
				.toArray(IProblemLocation[]::new);
		System.err.println(
				"Problems: " + Stream.of(locs).map(IProblemLocation::toString).collect(Collectors.joining(",")));

		IInvocationContext context = new AssistContext(icu, 51, 1);
		IJavaCompletionProposal[] proposals = sut.getCorrections(context, locs);

		if (proposals != null) {
			System.err.println("Proposals: "
					+ Stream.of(proposals).map(IJavaCompletionProposal::toString).collect(Collectors.joining("\n")));
		} else {
			System.err.println("No proposals");
		}
	}
	
	@ParameterizedTest
	@ValueSource(ints = { ImportNotFound, UndefinedType, IsClassPathCorrect })
	public void hasCorrections_forSupportedProblemTypes_returnsTrue(int problemId, SoftAssertions softly) {
		softly.assertThat(sut.hasCorrections(null, problemId)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(ints = { UnusedImport })
	public void hasCorrections_forUnsupportedProblemTypes_returnsFalse(int problemId, SoftAssertions softly) {
		softly.assertThat(sut.hasCorrections(null, problemId)).isFalse();
	}
}
