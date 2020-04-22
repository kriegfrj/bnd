package org.bndtools.core.editors.quickfix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bndtools.core.testutils.ResourceLock.TEST_WORKSPACE;
import static org.eclipse.jdt.core.compiler.IProblem.ImportNotFound;
import static org.eclipse.jdt.core.compiler.IProblem.IsClassPathCorrect;
import static org.eclipse.jdt.core.compiler.IProblem.UndefinedType;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.assertj.core.api.Condition;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.assertj.core.presentation.Representation;
import org.assertj.core.presentation.StandardRepresentation;
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
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblem;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

import aQute.lib.exceptions.Exceptions;
import bndtools.central.Central;

// All of these tests manipulate the same workspace so they can't run in parallel.
@Execution(SAME_THREAD)
@ResourceLock(TEST_WORKSPACE)
@ExtendWith(SoftAssertionsExtension.class)
public class BuildpathQuickFixProcessorTest {
	static IPackageFragment pack;
	static Class<? extends IQuickFixProcessor> sutClass;

//	@BundleContextParameter
//	BundleContext bc;

	SoftAssertions softly;
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
		proposalsFor(51, 7, "Something",
				"package test; import org.osgi.framework.Bundle; import java.util.ArrayList; public class Something { ArrayList<?> myList; "
						+ "public static void main(String[] args) {\n" + "		ArrayList<String> al = null;\n"
						+ "		int j =0;\n" + "		System.out.println(j);\n" + "		System.out.println(al);\r\n"
						+ "	}}");
	}

	private static final String DEFAULT_CLASS_NAME = "Test";
	private static final String CLASS_HEADER = "class " + DEFAULT_CLASS_NAME + " {";
	private static final String CLASS_FOOTER = " var};";

	private IJavaCompletionProposal[] proposalsForImport(String imp) {
		return proposalsFor(8, 0, "import " + imp + ";");
	}

	private IJavaCompletionProposal[] proposalsForUndefType(String type) {
		return proposalsFor(CLASS_HEADER.length(), 0, CLASS_HEADER + type + CLASS_FOOTER);
	}

	private IJavaCompletionProposal[] proposalsFor(int offset, int length, String source) {
		return proposalsFor(offset, length, DEFAULT_CLASS_NAME, source);
	}

	/**
	 * Workhorse method for driving the quick fix processor and getting the results.
	 * 
	 * @param offset    the location in the source of the start of the current
	 *                  selection.
	 * @param length    the length of the current selection (0 = no selection).
	 * @param className the name of the class, to use as the filename of the
	 *                  compilation unit.
	 * @param source    the source code of the class to compile.
	 * @return The completion proposals that were generated by the quick fix
	 *         processor.
	 */
	private IJavaCompletionProposal[] proposalsFor(int offset, int length, String className, String source) {

		try {
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

			IInvocationContext context = new AssistContext(icu, offset, length);
			IJavaCompletionProposal[] proposals = sut.getCorrections(context, locs);

			if (proposals != null) {
				System.err.println("Proposals: " + Stream.of(proposals).map(x -> {
					return "toString: " + x.toString() + "\ndisplaystring: " + x.getDisplayString();
				}).collect(Collectors.joining("\n")));
			} else {
				System.err.println("No proposals");
			}
			return proposals;
		} catch (Exception e) {
			throw Exceptions.duck(e);
		}
	}

	@ParameterizedTest
	@MethodSource("supportedProblemTypes")
	public void hasCorrections_forSupportedProblemTypes_returnsTrue(IProblem problem, SoftAssertions softly) {
		softly.assertThat(sut.hasCorrections(null, problem.getID())).as(problem.getMessage()).isTrue();
	}

	static final Set<Integer> SUPPORTED = Stream.of(ImportNotFound, UndefinedType, IsClassPathCorrect)
			.collect(Collectors.toSet());

	// This is just to give nice error feedback
	static class DummyProblem extends DefaultProblem {
		public DummyProblem(int id, String message) {
			super(null, message, id, null, 0, 0, 0, 0, 0);
		}
	}

	static Stream<IProblem> supportedProblemTypes() {
		return allProblemTypes().filter(p -> SUPPORTED.contains(p.getID()));
	}

	@ParameterizedTest
	@MethodSource("unsupportedProblemTypes")
	public void hasCorrections_forUnsupportedProblemTypes_returnsFalse(IProblem problem, SoftAssertions softly) {
		softly.assertThat(sut.hasCorrections(null, problem.getID())).as(problem.getMessage()).isFalse();
	}

	static Stream<IProblem> unsupportedProblemTypes() {
		return allProblemTypes().filter(p -> !SUPPORTED.contains(p.getID())).limit(50);
	}

	static IProblem getProblem(Field f) {
		try {
			int problemId = f.getInt(null);
			return new DummyProblem(problemId, f.getName());
		} catch (Exception e) {
			throw Exceptions.duck(e);
		}
	}

	static Stream<IProblem> allProblemTypes() {
		return Stream.of(IProblem.class.getFields()).map(BuildpathQuickFixProcessorTest::getProblem);
	}

	@Test
	public void withNoMatches_forUndefType_returnsNull() {
		assertThat(proposalsForUndefType("my.unknown.type.MyClass")).isNull();
	}

	@Test
	public void withUnqualifiedType_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsBundleActivatorSuggestions(proposalsForUndefType("BundleActivator"));
	}

	@Test
	public void withAnnotatedUnqualifiedType_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsBundleActivatorSuggestions(proposalsForUndefType("@NonNull BundleActivator"));
	}

	@Disabled("Not yet implemented")
	@Test
	public void withInnerType_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsFrameworkBundles(proposalsForUndefType("ListenerHook.ListenerInfo"),
				"org.osgi.framework.hooks.service.ListenerHook.ListenerInfo");
	}

	@Test
	public void withFQType_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsBundleActivatorSuggestions(proposalsForUndefType("org.osgi.framework.BundleActivator"));
	}

	@Disabled("Not yet implemented")
	@Test
	public void withFQNestedType_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsListenerInfoSuggestions(
				proposalsForUndefType("org.osgi.framework.hooks.service.ListenerHook.ListenerInfo"));
	}

	@Disabled("Not yet implemented")
	@Test
	public void withAnnotatedFQNestedType_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsListenerInfoSuggestions(
				proposalsForUndefType("org.osgi.framework.hooks.service.ListenerHook.@NotNull ListenerInfo"));
	}

	@Disabled("Not yet implemented")
	@Test
	public void withAnnotatedFQType_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsBundleActivatorSuggestions(
				proposalsForUndefType("org.osgi.framework.@NotNull BundleActivator"));
	}

	@Test
	public void withAnnotatedSimpleType_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsBundleActivatorSuggestions(proposalsForUndefType("@NotNull BundleActivator"));
	}

	@Test
	public void withUnannotatedFQArrayType_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsBundleActivatorSuggestions(proposalsForUndefType("org.osgi.framework.BundleActivator[]"));
	}

	@Disabled("Not yet implemented")
	@Test
	public void withFQType_andOneLevelPackage_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsBundleActivatorSuggestions(proposalsForUndefType("test.BundleActivator"));
	}

	@Disabled("Not yet implemented")
	@Test
	public void withAnnotatedFQType_andOneLevelPackage_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsBundleActivatorSuggestions(proposalsForUndefType("`test'.@NotNull BundleActivator"));
	}

	@Test
	// Using a parameterized type as a qualifier forces Eclipse AST to generate
	// a QualifiedType
	public void withGenericType_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsPromiseSuggestions(proposalsForUndefType("org.osgi.util.promise.Promise<String>"));
	}

//	@Test
	// Force Eclipse AST to generate a QualifiedType
//	public void getCorrections_withParameterisedOuterType_andMarkerOnInner_suggestsBundles() {
//		// Due to the structure of QualifiedType (the qualifier is a Type rather
//		// than simply a Name), it becomes
//		// a little more complicated to recurse back through the type definition
//		// to get the package name.
//		// Moreover, the JDT doesn't seem to mark the inner type if it can't
//		// find the package - it will mark
//		// the package instead. So this kind of construction is unlikely to
//		// occur in practice. If there becomes
//		// a need, this test can be re-enabled and implemented.
//		assertThatContainsFrameworkBundles(
//			proposalsForUndefType("org.osgi.framework.BundleActivator<String>.`Inner'.@NotNull BundleActivator"));
//	}

	@Test
	public void withAnnotatedFQArrayType_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsBundleActivatorSuggestions(proposalsForUndefType("org.osgi.framework.@NotNull BundleActivator[]"));
	}

	@Test
	public void withAnnotatedFQDoubleArrayType_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsBundleActivatorSuggestions(proposalsForUndefType("org.osgi.framework.@NotNull BundleActivator[][]"));
	}

	@Test
	public void withParameter_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsBundleActivatorSuggestions(
			proposalsForUndefType("List<org.osgi.framework.@NotNull BundleActivator>"));
	}


	private void assertThatContainsPromiseSuggestions(IJavaCompletionProposal[] proposals) {
		if (proposals == null) {
			softly.fail("no proposals returned");
		} else {
			softly.assertThat(proposals).withRepresentation(PROPOSAL).hasSize(1).haveExactly(1, suggestsBundle(
					"org.osgi.util.promise", "1.1.1", "org.osgi.util.promise.Promise"));
		}
	}

	private void assertThatContainsBundleActivatorSuggestions(IJavaCompletionProposal[] proposals) {
		assertThatContainsFrameworkBundles(proposals, "org.osgi.framework.BundleActivator");
	}

	private void assertThatContainsListenerInfoSuggestions(IJavaCompletionProposal[] proposals) {
		assertThatContainsFrameworkBundles(proposals, "org.osgi.framework.hooks.service.ListenerHook.ListenerInfo");
	}

	static Representation PROPOSAL = new StandardRepresentation() {
		@Override
		public String toStringOf(Object object) {
			if (object instanceof IJavaCompletionProposal) {
				return ((IJavaCompletionProposal) object).getDisplayString();
			}
			return super.toStringOf(object);
		}
	};

	private void assertThatContainsFrameworkBundles(IJavaCompletionProposal[] proposals, String fqName) {
		if (proposals == null) {
			softly.fail("no proposals returned");
		} else {
			softly.assertThat(proposals).withRepresentation(PROPOSAL).hasSize(2)
					.haveExactly(1, suggestsBundle("org.osgi.framework", "1.8.0", fqName))
					.haveExactly(1, suggestsBundle("org.osgi.framework", "1.9.0", fqName));
		}
	}

	static class MatchDisplayString extends Condition<IJavaCompletionProposal> {
		private final Pattern p;

		public MatchDisplayString(String bundle, String version, String fqName, boolean test) {
			super(String.format("Suggestion to add '%s %s' to -%spath for class %s", bundle, version,
					test ? "test" : "build", fqName));
			String re = String.format("^Add \\Q%s\\E \\Q%s\\E to -\\Q%s\\Epath [(]found \\Q%s\\E[)]", bundle, version,
					test ? "test" : "build", fqName);
			p = Pattern.compile(re);
			System.err.println("regular expression: " + p);
		}

		@Override
		public boolean matches(IJavaCompletionProposal value) {
			if (value == null || value.getDisplayString() == null) {
				return false;
			}
			final Matcher m = p.matcher(value.getDisplayString());
			System.err.println("Testing " + m + ", " + value.getDisplayString());
			return m.find();
		}
	}

	static Condition<IJavaCompletionProposal> suggestsBundle(String bundle, String version, String fqName) {
		return new MatchDisplayString(bundle, version, fqName, false);
	}

	static Condition<IJavaCompletionProposal> withRelevance(final int relevance) {
		return new Condition<IJavaCompletionProposal>("Suggestion has relevance " + relevance) {
			@Override
			public boolean matches(IJavaCompletionProposal value) {
				return value != null && value.getRelevance() == relevance;
			}
		};
	}
}
