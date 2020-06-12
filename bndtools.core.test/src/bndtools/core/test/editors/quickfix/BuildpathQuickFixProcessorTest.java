package bndtools.core.test.editors.quickfix;

import static bndtools.core.test.utils.ResourceLock.TEST_WORKSPACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.jdt.core.compiler.IProblem.ImportNotFound;
import static org.eclipse.jdt.core.compiler.IProblem.IsClassPathCorrect;
import static org.eclipse.jdt.core.compiler.IProblem.UndefinedType;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;
import static org.osgi.test.common.exceptions.RunnableWithException.asRunnable;

import org.eclipse.jdt.internal.launching.LaunchingPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.commands.Category;
import org.assertj.core.api.Condition;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.assertj.core.presentation.Representation;
import org.assertj.core.presentation.StandardRepresentation;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IncrementalProjectBuilder;
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
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.eclipse.ui.dialogs.IOverwriteQuery;
import org.eclipse.ui.internal.Workbench;
import org.eclipse.ui.internal.WorkbenchPlugin;
import org.eclipse.ui.internal.WorkingSetManager;
import org.eclipse.ui.wizards.datatransfer.FileSystemStructureProvider;
import org.eclipse.ui.wizards.datatransfer.ImportOperation;
import org.junit.jupiter.api.AfterAll;
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
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;

import aQute.bnd.build.Project;
import aQute.bnd.osgi.Jar;
import aQute.bnd.service.RepositoryListenerPlugin;
import aQute.bnd.service.RepositoryPlugin;
import aQute.lib.exceptions.Exceptions;
import aQute.lib.io.IO;
import bndtools.central.Central;
import bndtools.core.test.Activator;

import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.services.contributions.IContributionFactory;
import org.eclipse.e4.ui.internal.workbench.E4Workbench;
import org.eclipse.e4.ui.internal.workbench.ModelServiceImpl;
import org.eclipse.e4.ui.internal.workbench.UIEventPublisher;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.MApplicationElement;
import org.eclipse.e4.ui.model.application.commands.MCommand;
import org.eclipse.e4.ui.model.application.impl.ApplicationPackageImpl;
import org.eclipse.e4.ui.model.application.ui.MUIElement;
import org.eclipse.e4.ui.model.application.ui.basic.MWindow;
import org.eclipse.e4.ui.tests.extensions.HeadlessApplicationExtension;
import org.eclipse.e4.ui.workbench.IPresentationEngine;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.m2e.model.edit.pom.Notifier;
import org.eclipse.e4.core.commands.ECommandService;
import aQute.bnd.deployer.repository.LocalIndexedRepo;
import org.osgi.test.junit5.context.BundleContextExtension;
import org.osgi.test.common.annotation.InjectBundleContext;

// All of these tests manipulate the same workspace so they can't run in parallel.
@Execution(SAME_THREAD)
@ResourceLock(TEST_WORKSPACE)
@ExtendWith(SoftAssertionsExtension.class)
@ExtendWith(BundleContextExtension.class)
//@ExtendWith(HeadlessApplicationExtension.class)
public class BuildpathQuickFixProcessorTest {
	static IPackageFragment pack;
	static Class<? extends IQuickFixProcessor> sutClass;

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

	static interface MonitoredTask {
		void run(IProgressMonitor monitor) throws Exception;
	}

	static void synchronously(String msg, MonitoredTask task) {
		try {
			String suffix = msg == null ? "" : ": " + msg;
			CountDownLatch flag = new CountDownLatch(1);
			log("Synchronously executing" + suffix);
			task.run(countDownMonitor(flag));
			log("Waiting for flag" + suffix);
			flag.await(10000, TimeUnit.MILLISECONDS);
			log("Finished waiting for flag" + suffix);
		} catch (Exception e) {
			throw Exceptions.duck(e);
		}
	}

	static void synchronously(MonitoredTask task) {
		synchronously(null, task);
	}

	@SuppressWarnings("unchecked")
	static void initSUTClass() throws Exception {
		sutClass = (Class<? extends IQuickFixProcessor>) Central.class.getClassLoader()
				.loadClass("org.bndtools.core.editors.quickfix.BuildpathQuickFixProcessor");
	}

	static CountDownLatch simpleJar;

	static ServiceRegistration<RepositoryListenerPlugin> service;

	protected static void createGUI(MUIElement uiRoot) {
		renderer.createGui(uiRoot);
	}

	protected static MApplicationElement createApplicationElement(IEclipseContext appContext) throws Exception {
		return createApplication(appContext, getURI());
	}

	protected static String getURI() {
		return "org.eclipse.ui.workbench/LegacyIDE.e4xmi";
	}

	static IEclipseContext applicationContext;

	protected static IPresentationEngine createPresentationEngine(String renderingEngineURI) throws Exception {
		IContributionFactory contributionFactory = applicationContext.get(IContributionFactory.class);
		Object newEngine = contributionFactory.create(renderingEngineURI, applicationContext);
		return (IPresentationEngine) newEngine;
	}

	private static MApplication createApplication(IEclipseContext appContext, String appURI) throws Exception {
		URI initialWorkbenchDefinitionInstance = URI.createPlatformPluginURI(appURI, true);

		ResourceSet set = new ResourceSetImpl();
		set.getPackageRegistry().put("http://MApplicationPackage/", ApplicationPackageImpl.eINSTANCE);

		Resource resource = set.getResource(initialWorkbenchDefinitionInstance, true);

		MApplication application = (MApplication) resource.getContents().get(0);
		application.setContext(appContext);
		appContext.set(MApplication.class, application); // XXX
		appContext.set(EModelService.class, new ModelServiceImpl(appContext));

//		ECommandService cs = appContext.get(ECommandService.class);
//		Category cat = cs.defineCategory(MApplication.class.getName(),
//				"Application Category", null); //$NON-NLS-1$
//		List<MCommand> commands = application.getCommands();
//		for (MCommand cmd : commands) {
//			String id = cmd.getElementId();
//			String name = cmd.getCommandName();
//			cs.defineCommand(id, name, null, cat, null);
//		}
//
		// take care of generating the contexts.
		List<MWindow> windows = application.getChildren();
		for (MWindow window : windows) {
			E4Workbench.initializeContext(appContext, window);
		}

		// processPartContributions(application.getContext(), resource);

		renderer = createPresentationEngine(getEngineURI());

		return application;
	}

	static IPresentationEngine renderer;

	protected static String getEngineURI() {
		return "bundleclass://org.eclipse.e4.ui.tests/org.eclipse.e4.ui.tests.application.HeadlessContextPresentationEngine"; //$NON-NLS-1$
	}

	protected static MApplicationElement applicationElement;
	protected static EModelService ems;

	static void setupApp(IEclipseContext applicationContext) throws Exception {

		BuildpathQuickFixProcessorTest.applicationContext = applicationContext;
		applicationElement = createApplicationElement(applicationContext);
		ems = applicationContext.get(EModelService.class);

		// Hook the global notifications
//			final UIEventPublisher ep = new UIEventPublisher(applicationContext);
//			((Notifier) applicationElement).eAdapters().add(ep);
//			applicationContext.set(UIEventPublisher.class, ep);

	}

	@BeforeAll
//	static void beforeAll(IEclipseContext context) throws Exception {
	static void beforeAll(@InjectBundleContext BundleContext bc) throws Exception {

		while (Workbench.getInstance() == null || !Workbench.getInstance().isRunning()) {
			System.err.println("Waiting for workbench");
			Thread.sleep(100);
		}

//		Display d = PlatformUI.createDisplay();
//		WorkbenchAdvisor advisor = new WorkbenchAdvisor() {
//			@Override
//			public String getInitialWindowPerspectiveId() {
//				return null;
//			}
//		};
//		setupApp(context);
//
//		Constructor<Workbench> m = Workbench.class.getDeclaredConstructor(Display.class, WorkbenchAdvisor.class, MApplication.class, IEclipseContext.class);
//		m.setAccessible(true);
//		WorkbenchPlugin.getDefault().initializeContext(context);
//		System.err.println("Checking to see if workbench is ready");
//		if (Workbench.getInstance() == null) {
//			System.err.println("Creating workbench");
//			Workbench wb = m.newInstance(d, advisor, (MApplication)applicationElement, context);
//			System.err.println("Created workbench: " + wb);
//			assertThat(Workbench.getInstance()).isSameAs(wb);
//		}
//		System.err.println("Finished workbench setyp");
//		
//		applicationContext.set(IWorkingSetManager.class,
//				new WorkingSetManager(FrameworkUtil.getBundle(HeadlessApplicationExtension.class).getBundleContext()));

		simpleJar = new CountDownLatch(1);

		// System.err.println("............Eclipse context: " + context);

		Path srcRoot = Paths.get("./resources/");
//		Path srcRoot = Paths.get(b.getBundleContext().getProperty("bndtools.core.test.workspaces"));
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

		IOverwriteQuery overwriteQuery = file -> IOverwriteQuery.ALL;

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

		// This is a hack to make sure that Central is running before we continue.
		// Central must be initialized on the UI thread.
		Display.getDefault()
		.syncExec(() -> Central.getInstance());
//		Instant endTime = Instant.now().plusMillis(10000);
//		NoClassDefFoundError ncdfe = null;
//		while (Instant.now().isBefore(endTime)) {
//			try {
//				log("Trying to get Central: " + endTime);
//				Central.getInstance();
//				break;
//			} catch (NoClassDefFoundError e) {
//				ncdfe = e;
//				log("Errroy trying to get Central: " + e);
//				Thread.sleep(100);
//			}
//		}
//		log("Finished trying to get Central");
//		if (Instant.now().isAfter(endTime)) {
//			log("endTime: " + endTime);
//			throw ncdfe;
//		}
		// Copy bundles from the parent project into our test workspace repo
		LocalIndexedRepo localRepo = (LocalIndexedRepo) Central.getWorkspace().getRepository("Local Index");
		Path bundleRoot = Paths.get("./generated/");
		System.err.println("localRepo: " + localRepo);
//		System.err.println("localRepo.getName(): " + localRepo.getName());
		System.err.println("bundleRoot: " + bundleRoot);
		Files.walk(bundleRoot, 1).map(Object::toString).forEach(BuildpathQuickFixProcessorTest::log);
		Files.walk(bundleRoot, 1).filter(x -> x.getFileName().toString().endsWith("simple.jar")).forEach(bundle -> {
			System.err.println("====>" + bundle);
			try {
				localRepo.put(IO.stream(bundle), null);
			} catch (Exception e) {
				System.err.println("--->" + e);
				e.printStackTrace();
				throw Exceptions.duck(e);
			}
		});

		log("About to wait for imports to complete " + sourceProjects.size());
		flag.await(10000, TimeUnit.MILLISECONDS);
		log("done waiting for import to completerater");

		IProject project = wsr.getProject("test");
		if (!project.exists()) {
			synchronously("create project", project::create);
		}
		synchronously("open project", project::open);
		IJavaProject javaProject = JavaCore.create(project);

		IFolder sourceFolder = project.getFolder("src");
		if (!sourceFolder.exists()) {
			synchronously("create src", monitor -> sourceFolder.create(true, true, monitor));
		}

		Job.getJobManager().join(ResourcesPlugin.FAMILY_AUTO_BUILD, null);

		IPackageFragmentRoot root = javaProject.getPackageFragmentRoot(sourceFolder);
		synchronously("createPackageFragment", monitor -> pack = root.createPackageFragment("test", false, monitor));

		for (IProject current : wsr.getProjects()) {
			log("Attempting to build " + current.getName());
			Project bndProject = Central.getProject(current);
			if (bndProject == null) {
				continue;
			}
			bndProject.build();
			if (current.getName().equals("cnf")) {
				bndProject.getWorkspace().refresh();
			}
		}

		initSUTClass();
	}

	@AfterAll
	static void afterAll() throws Exception {
		Method close = Workbench.class.getDeclaredMethod("close", int.class, boolean.class);
		close.setAccessible(true);
		Display.getDefault()
				.syncExec(asRunnable(() -> close.invoke(Workbench.getInstance(), PlatformUI.RETURN_OK, true)));
	}

	@BeforeEach
	void before() throws Exception {
		sut = sutClass.newInstance();
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
	public void withSimpleInnerType_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsListenerInfoSuggestions(proposalsForUndefType("ListenerInfo"));
	}

	@Disabled("Not yet implemented")
	@Test
	public void withPartlyQualifiedInnerType_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsListenerInfoSuggestions(proposalsForUndefType("ListenerHook.ListenerInfo"));
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

	@Test
	public void withFQType_andOneLevelPackage_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsMyClassSuggestions(proposalsForUndefType("simple.MyClass"));
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
//	public void withParameterisedOuterType_andMarkerOnInner_suggestsBundles() {
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

	@Disabled("Not yet implemented")
	@Test
	public void withAnnotatedFQArrayType_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsBundleActivatorSuggestions(
				proposalsForUndefType("org.osgi.framework.@NotNull BundleActivator[]"));
	}

	@Disabled("Not yet implemented")
	@Test
	public void withAnnotatedFQDoubleArrayType_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsBundleActivatorSuggestions(
				proposalsForUndefType("org.osgi.framework.@NotNull BundleActivator[][]"));
	}

	@Test
	public void withSimpleUnannotatedParameter_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsBundleActivatorSuggestions(proposalsForUndefType("List<BundleActivator>"));
	}

	@Test
	public void withSimpleAnnotatedParameter_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsBundleActivatorSuggestions(proposalsForUndefType("List<@NotNull BundleActivator>"));
	}

	@Test
	public void withFQUnannotatedParameter_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsBundleActivatorSuggestions(proposalsForUndefType("List<org.osgi.framework.BundleActivator>"));
	}

	@Disabled("Not yet implemented")
	@Test
	public void withFQAnnotatedParameter_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsBundleActivatorSuggestions(
				proposalsForUndefType("List<org.osgi.framework.@NotNull BundleActivator>"));
	}

	@Test
	public void withFQWildcardBound_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsBundleActivatorSuggestions(
				proposalsForUndefType("List<? extends org.osgi.framework.BundleActivator>"));
	}

	@Disabled("Not yet implemented")
	@Test
	public void withFQAnnotatedWildcardBound_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsBundleActivatorSuggestions(
				proposalsForUndefType("List<? extends org.osgi.framework.@NotNull BundleActivator>"));
	}

	@Test
	public void withUnqualifiedNameType_returnsNull(SoftAssertions softly) {
		// If the type is a simple name, it must refer to a type and not a
		// package; therefore don't provide package
		// import suggestions even if we have a package with the matching name.
		softly.assertThat(proposalsForUndefType("Test")).as("capitalized").isNull();
		softly.assertThat(proposalsForUndefType("test")).as("uncapitalized").isNull();
	}

	@Disabled("Not yet implemented")
	@Test
	public void withParameterizedType_thatLooksLikePackage_returnsNull() {
		// FIXME: need an uncapitalised class definition with an inner class to properly
		// exercise this test.
		assertThat(proposalsForUndefType("org.osgi.framework<String>.BundleActivator")).isNull();
	}

	@Test
	public void withNoMatches_returnsNull() {
		assertThat(proposalsForImport("my.unknown.package.*")).isNull();
	}

	@Disabled("Need an alternate package to import from")
	@Test
	public void withOnDemandImport_altPackage_suggestsBundles() {
//		AddBundleCompletionProposal[] props = proposalsForImport("`org.eclipse'.ui.*");
//		assertThat(props).hasSize(eclipseBundles.size())
//			.haveExactly(1, allOf(suggestsBundle("my.test.bundle", "Repo 1"), withRelevance(ADD_BUNDLE)))
//			.haveExactly(1,
//				allOf(suggestsBundle("my.eclipse.bundle", WORKSPACE_REPO), withRelevance(ADD_BUNDLE_WORKSPACE)));
	}

//	@Test
//	public void withOnlyIrrelvantProblems_skipsAll_andReturnsNull() {
//		List<IProblemLocation> locs = getProblems("import `org.`osgi'.framework'.*", UnusedImport, AmbiguousType);
//
//		assertThat(proposalsFor(locs)).isNull();
//	}
//
//	@Test
//	public void skipsIrrelevantProblems_andReturnsBundles() {
//		List<IProblemLocation> locs = getProblems("import `org.osgi'`.framework'.*", ImportNotFound, UnusedImport);
//
//		assertThatContainsFrameworkBundles(proposalsFor(locs));
//	}
//
//	@Test
//	public void withMarkerInMiddle_suggestsBundles() {
//		assertThatContainsFrameworkBundles(proposalsForImport("org.`osgi.'framework.*"));
//	}
//
//	@Test
//	public void withMarkerAroundWildcard_suggestsBundles() {
//		assertThatContainsFrameworkBundles(proposalsForImport("org.`osgi.framework.*'"));
//	}
//
//	@Test
//	public void withMarkerAroundClass_suggestsBundles() {
//		assertThatContainsFrameworkBundles(proposalsForImport("org.`osgi.framework.BundleActivator'"));
//	}
//
//	@Test
//	public void withMarkerAroundStatic_suggestsBundles() {
//		assertThatContainsFrameworkBundles(proposalsForImport("`static org.osgi.framework'.Clazz.member"));
//	}
//
	@Test
	public void withClassImport_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsBundleActivatorSuggestions(proposalsForImport("org.osgi.framework.BundleActivator"));
	}

	@Disabled("Not yet implemented")
	@Test
	public void withInnerClassImport_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsListenerInfoSuggestions(
				proposalsForImport("org.osgi.framework.hooks.service.ListenerHook.ListenerInfo"));
	}

	@Test
	public void withOnDemandImport_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsFrameworkBundles(proposalsForImport("org.osgi.framework.*"), "org.osgi.framework");
	}

	@Test
	public void withOnDemandStaticImport_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsBundleSuggestions(proposalsForImport("static org.osgi.framework.Bundle.*"));
	}

	@Disabled("Not yet implemented")
	@Test
	public void withStaticImport_suggestsBundles(SoftAssertions softly) {
		this.softly = softly;
		assertThatContainsBundleSuggestions(proposalsForImport("static org.osgi.framework.Bundle.INSTALLED"));
	}

	private void assertThatContainsPromiseSuggestions(IJavaCompletionProposal[] proposals) {
		if (proposals == null) {
			softly.fail("no proposals returned");
		} else {
			softly.assertThat(proposals).withRepresentation(PROPOSAL).hasSize(1).haveExactly(1,
					suggestsBundle("org.osgi.util.promise", "1.1.1", "org.osgi.util.promise.Promise"));
		}
	}

	private void assertThatContainsMyClassSuggestions(IJavaCompletionProposal[] proposals) {
		if (proposals == null) {
			softly.fail("no proposals returned");
		} else {
			softly.assertThat(proposals).withRepresentation(PROPOSAL).hasSize(1).haveExactly(1,
					suggestsBundle("bndtools.core.test.simple", "1.0.0", "simple.MyClass"));
		}
	}

	private void assertThatContainsBundleSuggestions(IJavaCompletionProposal[] proposals) {
		assertThatContainsFrameworkBundles(proposals, "org.osgi.framework.Bundle");
	}

	private void assertThatContainsBundleActivatorSuggestions(IJavaCompletionProposal[] proposals) {
		assertThatContainsFrameworkBundles(proposals, "org.osgi.framework.BundleActivator");
	}

	private void assertThatContainsListenerInfoSuggestions(IJavaCompletionProposal[] proposals) {
		assertThatContainsFrameworkBundles(proposals, "org.osgi.framework.hooks.service.ListenerHook.ListenerInfo");
	}

	// This gives us a more complete display when tests fail
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
