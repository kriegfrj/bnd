package aQute.junit.bundle.engine.test;

import static aQute.junit.bundle.engine.BundleEngine.CHECK_UNRESOLVED;
import static org.assertj.core.api.Assertions.allOf;
import static org.junit.platform.commons.util.FunctionUtils.where;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;
import static org.junit.platform.testkit.engine.Event.byTestDescriptor;
import static org.junit.platform.testkit.engine.EventConditions.container;
import static org.junit.platform.testkit.engine.EventConditions.event;
import static org.junit.platform.testkit.engine.EventConditions.finishedSuccessfully;
import static org.junit.platform.testkit.engine.EventConditions.finishedWithFailure;
import static org.junit.platform.testkit.engine.EventConditions.skippedWithReason;
import static org.junit.platform.testkit.engine.EventConditions.started;
import static org.junit.platform.testkit.engine.EventConditions.test;
import static org.junit.platform.testkit.engine.EventConditions.type;
import static org.junit.platform.testkit.engine.EventType.SKIPPED;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.instanceOf;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.message;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.assertj.core.api.Condition;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.platform.commons.JUnitException;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.EngineExecutionListener;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.EngineTestKit.Builder;
import org.junit.platform.testkit.engine.Event;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

import aQute.junit.bundle.engine.BundleDescriptor;
import aQute.junit.bundle.engine.BundleEngine;
import aQute.junit.bundle.engine.BundleEngineDescriptor;
import aQute.junit.bundle.engine.StaticFailureDescriptor;
import aQute.junit.bundle.engine.discovery.BundleSelector;
import aQute.junit.bundle.engine.discovery.BundleSelectorResolver;
import aQute.launchpad.BundleBuilder;
import aQute.launchpad.BundleSpecBuilder;
import aQute.launchpad.Launchpad;
import aQute.launchpad.LaunchpadBuilder;
import aQute.launchpad.junit.LaunchpadRule;
import aQute.lib.exceptions.Exceptions;
import aQute.lib.io.IO;
import aQute.tester.utils.TestClassName;
import aQute.tester.utils.TestSourceBundler;

public class BundleEngineTest {
	private LaunchpadBuilder	builder				= new LaunchpadBuilder().bndrun("bnd.bnd")
		.excludeExport("aQute.junit.bundle.engine")
		.excludeExport("aQute.junit.bundle.engine.discovery");

	static final boolean		DEBUG				= true;

	@Rule
	public LaunchpadRule		lpRule				= new LaunchpadRule(builder);

	Bundle						engineBundle;

	private PrintWriter			debugStr;

	static final String			TESTCLASS_PACKAGE	= "aQute.junit.bundle.engine.test.classes";
	static final TestClassName	JUnit4Test			= new TestClassName(TESTCLASS_PACKAGE, "JUnit4Test");
	static final TestClassName	JUnit5Test			= new TestClassName(TESTCLASS_PACKAGE, "JUnit5Test");
	static final TestClassName	AnotherTestClass	= new TestClassName(TESTCLASS_PACKAGE, "AnotherTestClass");
	static final TestClassName	TestClass			= new TestClassName(TESTCLASS_PACKAGE, "TestClass");

	static TestSourceBundler	testBundler;

	@BeforeClass
	public static void setupBundler() throws IOException {
		Set<TestClassName> testClasses = new HashSet<>();
		Stream.of(JUnit4Test, JUnit5Test, AnotherTestClass, TestClass)
			.forEach(testClasses::add);
		testBundler = new TestSourceBundler(testClasses);
	}

	@AfterClass
	public static void closeBundler() {
		testBundler.close();
	}

	@Before
	public void setUp() {
		if (DEBUG) {
			builder.debug();
			debugStr = new PrintWriter(System.err);
		} else {
			debugStr = new PrintWriter(new Writer() {
				@Override
				public void write(int b) {
				}

				@Override
				public void write(char[] cbuf, int off, int len) throws IOException {
				}

				@Override
				public void flush() throws IOException {
				}

				@Override
				public void close() throws IOException {
				}
			});
		}
	}

	@After
	public void tearDown() {
		IO.close(builder);
	}

	public static class EngineStarter implements Supplier<TestEngine> {

		@Override
		public TestEngine get() {
			return new BundleEngine();
		}

	}

	static String descriptionOf(Bundle b) {
		return b.getSymbolicName() + ';' + b.getVersion();
	}

	public BundleSpecBuilder addTestClass(BundleSpecBuilder bb, TestClassName testClass) {
		return testBundler.addTestClass(bb, testClass);
	}

	public Bundle startTestBundle(TestClassName... testClasses) {
		return testBundler.startTestBundle(lpRule.getLaunchpad(), testClasses);
	}

	public Bundle startTestBundle(Launchpad lp, TestClassName... testClasses) {
		return testBundler.startTestBundle(lp, testClasses);
	}

	public Bundle installTestBundle(TestClassName... testClasses) throws Exception {
		return testBundler.installTestBundle(lpRule.getLaunchpad(), testClasses);
	}

	public Bundle installTestBundle(Launchpad lp, TestClassName... testClasses) throws Exception {
		return testBundler.installTestBundle(lp, testClasses);
	}

	private BundleSpecBuilder buildTestBundle(TestClassName... testClasses) {
		return testBundler.buildTestBundle(lpRule.getLaunchpad(), testClasses);
	}

	private BundleSpecBuilder buildTestBundle(Launchpad lp, TestClassName... testClasses) {
		return testBundler.buildTestBundle(lp, testClasses);
	}

	@Test
	public void outsideOfFramework_hasInitializationError() throws Exception {
		EngineTestKit.engine(new BundleEngine())
			.execute()
			.tests()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(1, event(test("noFramework"), finishedWithFailure(instanceOf(JUnitException.class),
				message(x -> x.contains("inside an OSGi framework")))));
	}

	@Test
	public void withNoEngines_reportsMissingEngines_andSkipsMainTests() throws Exception {
		builder = new LaunchpadBuilder();
		builder = builder.bndrun("no-engines.bndrun")
			.excludeExport("aQute.junit.bundle.engine")
			.excludeExport("aQute.junit.bundle.engine.discovery");

		Launchpad lp = builder.create();

		Bundle testBundle = startTestBundle(lp, JUnit4Test);

		engineInFramework(lp).execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(1,
				event(test("noEngines"),
					finishedWithFailure(instanceOf(JUnitException.class),
						message(x -> x.contains("Couldn't find any registered TestEngines")))))
			.haveExactly(1, event(bundle(testBundle), skippedWithReason("Couldn't find any registered TestEngines")));
	}

	public class NonEngine {
	}

	@Test
	public void withEngineWithBadServiceSpec_andTesterUnresolvedTrue_reportsMisconfiguredEngines_andSkipsMainTests()
		throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		Bundle engineBundle = lp.bundle()
			.includeResource("META-INF/services/" + TestEngine.class.getName())
			.literal("some.unknown.Engine # Include a comment\n" + NonEngine.class.getName())
			.addResourceWithCopy(NonEngine.class)
			.start();
		Bundle testBundle = startTestBundle(JUnit4Test);

		engineInFramework().execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(1, event(test("misconfiguredEngines"), unresolvedBundle(engineBundle),
				finishedWithFailure(instanceOf(java.util.ServiceConfigurationError.class))));
	}

	@Test
	public void withEngineWithBadServiceSpec_andTesterUnresolvedFalse_doesntReportMisconfiguredEngines_andRunsMainTests()
		throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		Bundle engineBundle = lp.bundle()
			.includeResource("META-INF/services/" + TestEngine.class.getName())
			.literal("some.unknown.Engine # Include a comment\n" + NonEngine.class.getName())
			.addResource(NonEngine.class)
			.start();
		Bundle testBundle = startTestBundle(JUnit4Test);

		engineInFramework().configurationParameter(CHECK_UNRESOLVED, "false")
			.execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(0, event(container("misconfiguredEngines"), bundle(engineBundle)))
			.haveExactly(0, event(testClass(JUnit4Test), finishedWithFailure(instanceOf(ClassCastException.class))))
			.haveExactly(1, event(bundle(testBundle), finishedSuccessfully()))
			.haveExactly(1, event(test("aTest"), finishedSuccessfully()))
			.haveExactly(1, event(test("bTest"), finishedSuccessfully()));
	}

	public static class CustomEngine implements TestEngine {

		static final String ENGINE_ID = "custom.engine";

		@Override
		public String getId() {
			return ENGINE_ID;
		}

		@Override
		public TestDescriptor discover(EngineDiscoveryRequest discoveryRequest, UniqueId uniqueId) {
			EngineDescriptor root = new EngineDescriptor(uniqueId, "Custom Engine");
			root.addChild(
				new StaticFailureDescriptor(uniqueId.append("test", "customTest"), "A Test", new Exception()));
			return root;
		}

		@Override
		public void execute(ExecutionRequest request) {
			TestDescriptor t = request.getRootTestDescriptor();
			EngineExecutionListener l = request.getEngineExecutionListener();
			l.executionStarted(t);
			for (TestDescriptor td : t.getChildren()) {
				StaticFailureDescriptor s = (StaticFailureDescriptor) td;
				s.execute(l);
			}
			request.getEngineExecutionListener()
				.executionFinished(t, TestExecutionResult.successful());
		}
	}

	@Test
	public void withEngineWithServiceSpecCommentsAndWhitespace_loadsEngine() throws Exception {
		builder = new LaunchpadBuilder();
		builder = builder.bndrun("no-engines.bndrun")
			.excludeExport("aQute.junit.bundle.engine")
			.excludeExport("aQute.junit.bundle.engine.discovery");
		if (DEBUG) {
			builder.debug();
		}

		Launchpad lp = builder.create();

		Bundle engineBundle = lp.bundle()
			.includeResource("META-INF/services/" + TestEngine.class.getName())
			.literal("# Include a comment\n \t " + CustomEngine.class.getName()
				+ " # another comment\n\n#The above was a blank line")
			.addResource(CustomEngine.class)
			.start();
		Bundle testBundle = startTestBundle(lp, JUnit4Test);

		engineInFramework(lp).execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(0, event(container("misconfiguredEngines"), bundle(engineBundle), finishedSuccessfully()))
			.haveExactly(1, event(bundle(testBundle), finishedSuccessfully()))
			.haveExactly(1, event(container(CustomEngine.ENGINE_ID), finishedSuccessfully()))
			.haveExactly(1, event(test("customTest"), finishedWithFailure()));
	}

	public static boolean lastSegmentMatches(UniqueId uId, String type, String contains) {
		final List<UniqueId.Segment> segments = uId.getSegments();
		UniqueId.Segment last = segments.get(segments.size() - 1);
		return last.getType()
			.equals(type)
			&& last.getValue()
				.contains(contains);
	}

	public static Condition<Event> lastSegment(String type, String contains) {
		return new Condition<>(
			byTestDescriptor(
				where(TestDescriptor::getUniqueId, uniqueId -> lastSegmentMatches(uniqueId, type, contains))),
			"last segment of type '%s' with value '%s'", type, contains);
	}

	public static Condition<Event> testClass(TestClassName testClass) {
		return test(testClass.fqName());
	}

	public static Condition<Event> containerClass(TestClassName testClass) {
		return container(testClass.fqName());
	}

	public static Condition<Event> bundle(Bundle bundle) {
		return container(lastSegment("bundle", descriptionOf(bundle)));
	}

	public static Condition<? super Event> inBundle(Bundle resolvedTestBundle) {
		return container(descriptionOf(resolvedTestBundle));
	}

	public static Condition<? super Event> testInBundle(Bundle resolvedTestBundle) {
		return test(descriptionOf(resolvedTestBundle));
	}

	public static Condition<Event> fragment(Bundle bundle) {
		return container(lastSegment("fragment", descriptionOf(bundle)));
	}

	public static Condition<Event> unresolvedBundle(Bundle bundle) {
		return allOf(test(), lastSegment("bundle", descriptionOf(bundle)));
	}

	public static Condition<Event> displayNameContaining(String substring) {
		return new Condition<>(byTestDescriptor(where(TestDescriptor::getDisplayName, x -> x.contains(substring))),
			"descriptor with display name containing '%s'", substring);
	}

	public static Condition<Event> withParentLastSegment(String type, String contains) {
		return new Condition<>(byTestDescriptor(x -> x.getParent()
			.map(parent -> lastSegmentMatches(parent.getUniqueId(), type, contains))
			.orElse(false)), "parent with last segment of type '%s' and value '%s'", type, contains);

	}

	public Builder engineInFramework(Launchpad lp) {
		try {
			engineBundle = lp.bundle()
				.addResourceWithCopy(BundleEngine.class)
				.addResourceWithCopy(BundleEngineDescriptor.class)
				.addResourceWithCopy(BundleDescriptor.class)
				.addResourceWithCopy(StaticFailureDescriptor.class)
				.addResourceWithCopy(BundleSelector.class)
				.addResourceWithCopy(BundleSelectorResolver.class)
				.addResourceWithCopy(BundleSelectorResolver.SubDiscoveryRequest.class)
				.exportPackage(BundleEngine.class.getPackage()
					.getName())
				.exportPackage(BundleSelector.class.getPackage()
					.getName())
				.start();
			debugStr.println("Engine bundle: " + engineBundle.getHeaders()
				.get("Import-Package"));
			@SuppressWarnings("unchecked")
			Class<? extends TestEngine> clazz = (Class<? extends TestEngine>) engineBundle
				.loadClass(BundleEngine.class.getName());

			return EngineTestKit.engine(clazz.getConstructor()
				.newInstance());
		} catch (Exception e) {
			throw Exceptions.duck(e);
		}
	}

	public Builder engineInFramework() {
		return engineInFramework(lpRule.getLaunchpad());
	}

	@Test
	public void bundleEngine_executesRootDescriptor() throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		Bundle testBundle = startTestBundle(JUnit4Test);

		engineInFramework().execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(1, event(lastSegment("engine", BundleEngine.ENGINE_ID), started()))
			.haveExactly(1, event(lastSegment("engine", BundleEngine.ENGINE_ID), finishedSuccessfully()));
	}

	@Test
	public void withUnresolvedBundles_reportsUnresolved_andSkipsMainTests() throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		String unresolved1 = lp.bundle()
			.importPackage("some.unknown.pkg")
			.install()
			.getSymbolicName();
		String unresolved2 = lp.bundle()
			.importPackage("some.other.package")
			.install()
			.getSymbolicName();

		Bundle testBundle = startTestBundle(JUnit4Test);

		engineInFramework().execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(1, event(container("unresolvedBundles"), finishedSuccessfully()))
			.haveExactly(1,
				event(test(unresolved1),
					finishedWithFailure(instanceOf(BundleException.class),
						message(x -> x.contains("Unable to resolve")))))
			.haveExactly(1,
				event(test(unresolved2),
					finishedWithFailure(instanceOf(BundleException.class),
						message(x -> x.contains("Unable to resolve")))))
			.haveExactly(1, event(bundle(testBundle), skippedWithReason("Unresolved bundles")));
	}

	@Test
	public void withUnresolvedBundles_andTesterUnresolvedFalse_doesntReportsUnresolved_andRunsMainTests()
		throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		Bundle unresolved = lp.bundle()
			.importPackage("some.unknown.pgk")
			.install();

		Bundle testBundle = startTestBundle(JUnit4Test);

		engineInFramework().configurationParameter(CHECK_UNRESOLVED, "false")
			.execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(0, event(container("unresolvedBundles")))
			.haveExactly(1, event(bundle(testBundle), finishedSuccessfully()))
			.haveExactly(1, event(test("aTest"), finishedSuccessfully()))
			.haveExactly(1, event(test("bTest"), finishedSuccessfully()));
	}

	@Test
	public void withUnresolvedTestBundle_andTesterUnresolvedFalse_reportsError_andSkipsBundle() throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		Bundle unResolvedTestBundle = buildTestBundle(lp, JUnit4Test).importPackage("some.unresolved.package")
			.install();
		Bundle resolvedTestBundle = startTestBundle(lp, TestClass);

		engineInFramework().configurationParameter(CHECK_UNRESOLVED, "false")
			.execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(1,
				event(unresolvedBundle(unResolvedTestBundle), finishedWithFailure(instanceOf(BundleException.class))))
			.haveExactly(1, event(bundle(resolvedTestBundle), finishedSuccessfully()));
	}

	// Only generate the "Unresolved Tests" hierarchy for non-test bundles that
	// fail to resolve.
	@Test
	public void withOnlyTestBundleUnresolved_andTesterUnresolvedTrue_reportsError_andSkipsBundle() throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		Bundle unResolvedTestBundle = buildTestBundle(lp, JUnit4Test).importPackage("some.unresolved.package")
			.install();
		Bundle resolvedTestBundle = startTestBundle(lp, TestClass);

		engineInFramework().execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(0, event(container("unresolvedBundles")))
			.haveExactly(1,
				event(unresolvedBundle(unResolvedTestBundle), finishedWithFailure(instanceOf(BundleException.class))))
			.haveExactly(1, event(bundle(resolvedTestBundle), finishedSuccessfully()));
	}

	@Test
	public void withMethodSelectors_andTestClassesHeader_runsOnlySelectedMethods() throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		Bundle resolvedTestBundle = startTestBundle(lp, TestClass, JUnit5Test);

		engineInFramework().selectors(selectMethod(JUnit5Test.fqName(), "thisIsBTest"))
			.execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(1, event(inBundle(resolvedTestBundle), containerClass(JUnit5Test), finishedSuccessfully()))
			.haveExactly(1, event(testInBundle(resolvedTestBundle), test("thisIsBTest"), finishedSuccessfully()))
			.haveExactly(0, event(test("thisIsATest")))
			.haveExactly(0, event(containerClass(TestClass), finishedSuccessfully()));
	}

	@Test
	public void withClassNameSelectors_andTestClassesHeader_runsOnlySelectedClasses() throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		Bundle resolvedTestBundle = startTestBundle(lp, TestClass, JUnit4Test);

		engineInFramework().selectors(selectClass(TestClass.fqName()))
			.execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(0, event(containerClass(JUnit4Test)))
			.haveExactly(1, event(inBundle(resolvedTestBundle), containerClass(TestClass), finishedSuccessfully()));
	}

	@Test
	public void withClassNameSelectors_andNoTestClassHeader_stillRunsSelectedClasses() throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		BundleBuilder bb = lp.bundle();
		addTestClass(bb, TestClass);
		addTestClass(bb, JUnit4Test);
		Bundle resolvedTestBundle = bb.start();

		engineInFramework().selectors(selectClass(TestClass.fqName()))
			.execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(0, event(containerClass(JUnit4Test)))
			.haveExactly(1, event(inBundle(resolvedTestBundle), containerClass(TestClass), finishedSuccessfully()));
	}

	@Test
	public void withUnresolvedClassSelectors_andTesterUnresolvedFalse_doesntReportError_andRunsOtherTests()
		throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		Bundle resolvedTestBundle = startTestBundle(lp, TestClass);

		engineInFramework().configurationParameter(CHECK_UNRESOLVED, "false")
			.selectors(selectClass("some.unknown.Clazz"), selectClass(TestClass.fqName()))
			.execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(0, event(container("unresolvedClasses")))
			// .haveExactly(0,
			// event(unresolvedClass("some.unknown.Clazz"),
			// finishedWithFailure(instanceOf(ClassNotFoundException.class))))
			.haveExactly(1, event(inBundle(resolvedTestBundle), containerClass(TestClass), finishedSuccessfully()));
	}

	@Test
	public void withUnresolvedClassSelectors_andTesterUnresolvedTrue_reportsError_andRunsOtherTests() throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		Bundle resolvedTestBundle = startTestBundle(lp, TestClass);

		engineInFramework().selectors(selectClass("some.unknown.Clazz"), selectClass(TestClass.fqName()))
			.execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(1, event(container("unresolvedClasses"), finishedSuccessfully()))
			.haveExactly(1, event(test("some.unknown.Clazz"), finishedWithFailure(instanceOf(JUnitException.class))))
			.haveExactly(1, event(bundle(resolvedTestBundle), skippedWithReason("Unresolved classes")));
	}

	@Test
	public void withClassSelector_forUnresolvedTestBundle_andTesterUnresolvedTrue_reportsUnresolvedBundle_butNotUnresolvedClass()
		throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		BundleSpecBuilder bb = buildTestBundle(TestClass);
		Bundle unResolvedTestBundle = bb.importPackage("some.unknown.pkg")
			.install();

		engineInFramework().selectors(selectClass(TestClass.fqName()))
			.execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(0, event(container("unresolvedClasses")))
			.haveExactly(1,
				event(unresolvedBundle(unResolvedTestBundle), finishedWithFailure(instanceOf(BundleException.class))));
	}

	@Test
	public void withClassSelector_forUnattachedTestFragment_andTesterUnresolvedTrue_reportsUnattachedFragment_butNotUnresolvedClass()
		throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		BundleSpecBuilder bb = buildTestBundle(TestClass);
		Bundle unAttachedTestFragment = bb.fragmentHost("some.unknown.bundle")
			.install();

		engineInFramework().selectors(selectClass(TestClass.fqName()))
			.execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(0, event(container("unresolvedClasses")))
			.haveExactly(1,
				event(unresolvedBundle(unAttachedTestFragment), finishedWithFailure(instanceOf(BundleException.class),
					message("Test fragment was not attached to a host bundle"))));
	}

	// Only generate the "Unresolved Tests" hierarchy for classes specified in
	// tester.testcases
	@Test
	public void withTestClassHeaderUnresolved_andTesterUnresolvedFalse_reportsError_andRunsOtherClasses()
		throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		BundleBuilder bb = lp.bundle();
		addTestClass(bb, JUnit4Test);
		Bundle unResolvedTestBundle = bb.header("Test-Cases", JUnit4Test.fqName() + ", some.other.Clazz")
			.start();
		Bundle resolvedTestBundle = startTestBundle(lp, TestClass);

		engineInFramework().configurationParameter(CHECK_UNRESOLVED, "false")
			.execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(0, event(container("unresolvedClasses")))
			.haveExactly(1,
				event(test("some.other.Clazz"), withParentLastSegment("bundle", unResolvedTestBundle.getSymbolicName()),
					finishedWithFailure(instanceOf(ClassNotFoundException.class))))
			.haveExactly(1, event(test("aTest"), finishedSuccessfully()))
			.haveExactly(1, event(test("bTest"), finishedSuccessfully()))
			.haveExactly(1, event(bundle(resolvedTestBundle), finishedSuccessfully()));
	}

	// Only generate the "Unresolved Tests" hierarchy for classes specified in
	// tester.testcases
	@Test
	public void withTestClassHeaderUnresolved_andTesterUnresolvedTrue_reportsError_andRunsOtherClasses()
		throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		BundleBuilder bb = lp.bundle();
		addTestClass(bb, JUnit4Test);
		Bundle unResolvedTestBundle = bb.header("Test-Cases", JUnit4Test.fqName() + ", some.other.Clazz")
			.start();
		Bundle resolvedTestBundle = startTestBundle(lp, TestClass);

		engineInFramework().execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(0, event(container("unresolvedClasses")))
			.haveExactly(1,
				event(test("some.other.Clazz"), withParentLastSegment("bundle", unResolvedTestBundle.getSymbolicName()),
					finishedWithFailure(instanceOf(ClassNotFoundException.class))))
			.haveExactly(1, event(test("aTest"), finishedSuccessfully()))
			.haveExactly(1, event(test("bTest"), finishedSuccessfully()))
			.haveExactly(1, event(bundle(resolvedTestBundle), finishedSuccessfully()));
	}

	@Test
	public void withUnresolvedClass_andUnresolvedBundle_andUnattachedFragment_reportsAll() throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		Bundle unresolved = lp.bundle()
			.importPackage("some.unknown.pkg")
			.install();

		Bundle testBundle = startTestBundle(JUnit4Test);

		Bundle unattached = lp.bundle()
			.fragmentHost("some.unknown.bundle")
			.install();

		engineInFramework().selectors(selectClass("some.unknown.class"))
			.execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(1, event(container("unresolvedBundles"), finishedSuccessfully()))
			.haveExactly(1,
				event(test(unresolved.getSymbolicName()),
					finishedWithFailure(instanceOf(BundleException.class),
						message(x -> x.contains("Unable to resolve")))))
			.haveExactly(1, event(container("unattachedFragments"), finishedSuccessfully()))
			.haveExactly(1, event(container("unresolvedClasses"), finishedSuccessfully()));

		engineInFramework().execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(1, event(container("unresolvedBundles"), finishedSuccessfully()))
			.haveExactly(1,
				event(test(unresolved.getSymbolicName()),
					finishedWithFailure(instanceOf(BundleException.class),
						message(x -> x.contains("Unable to resolve")))))
			.haveExactly(1, event(container("unattachedFragments"), finishedSuccessfully()))
			.haveExactly(1, event(bundle(testBundle), type(SKIPPED)));
	}

	@Test
	public void withUnattachedFragments_reportsUnattached_andSkipsMainTests() throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		Bundle fragment = lp.bundle()
			.addResourceWithCopy(NonEngine.class)
			.fragmentHost("some.missing.bundle")
			.install();

		Bundle testBundle = installTestBundle(JUnit4Test);
		Bundle hostedFragment = lp.bundle()
			.addResourceWithCopy(NonEngine.class)
			.fragmentHost(testBundle.getSymbolicName())
			.install();
		testBundle.start();

		engineInFramework().execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(1, event(container("unattachedFragments"), finishedSuccessfully()))
			.haveExactly(1,
				event(unresolvedBundle(fragment), withParentLastSegment("test", "unattachedFragments"),
					finishedWithFailure(instanceOf(JUnitException.class),
						message(x -> x.contains("Fragment was not attached to a host bundle")))))
			.haveExactly(1, event(bundle(testBundle), skippedWithReason("Unattached fragments")));
	}

	@Test
	public void withUnattachedNonTestFragments_andTesterUnresolvedFalse_doesntReportsUnattached_andRunsMainTests()
		throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		Bundle unattachedFragment = lp.bundle()
			.addResourceWithCopy(NonEngine.class)
			.fragmentHost("some.missing.bundle")
			.install();

		Bundle testBundle = startTestBundle(JUnit4Test);

		engineInFramework().configurationParameter(CHECK_UNRESOLVED, "false")
			.execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(0, event(container("unattachedFragments")))
			.haveExactly(1, event(bundle(testBundle), finishedSuccessfully()))
			.haveExactly(1, event(test("aTest"), finishedSuccessfully()))
			.haveExactly(1, event(test("bTest"), finishedSuccessfully()));
	}

	@Test
	public void withUnattachedTestFragment_andTesterUnresolvedFalse_reportsError_andSkipsBundle() throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		Bundle unattachedTestFragment = buildTestBundle(lp, JUnit4Test).fragmentHost("some.unresolved.package")
			.install();

		Bundle testFragmentHostWithoutItsOwnTests = lp.bundle()
			.install();

		Bundle attachedTestFragment = buildTestBundle(lp, TestClass)
			.fragmentHost(testFragmentHostWithoutItsOwnTests.getSymbolicName())
			.install();

		testFragmentHostWithoutItsOwnTests.start();

		engineInFramework().configurationParameter(CHECK_UNRESOLVED, "false")
			.execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(1,
				event(unresolvedBundle(unattachedTestFragment),
					finishedWithFailure(instanceOf(BundleException.class),
						message("Test fragment was not attached to a host bundle"))))
			.haveExactly(0, event(withParentLastSegment("bundle", descriptionOf(unattachedTestFragment))))
			.haveExactly(1,
				event(container(), lastSegment("fragment", descriptionOf(attachedTestFragment)),
					withParentLastSegment("bundle", descriptionOf(testFragmentHostWithoutItsOwnTests)),
					finishedSuccessfully()))
			.haveExactly(1,
				event(container("junit-vintage"),
					withParentLastSegment("fragment", descriptionOf(attachedTestFragment)), finishedSuccessfully()))
			.haveExactly(1, event(test("thisIsATest"), finishedSuccessfully()));
	}

	@Test
	public void withTestFragment_attachedToTestBundle_runsBothSetsOfTests() throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		Bundle testFragmentHostWithItsOwnTests = installTestBundle(lp, JUnit4Test);

		final String fragmentHost = testFragmentHostWithItsOwnTests.getSymbolicName();

		Bundle attachedTestFragment = buildTestBundle(lp, TestClass).fragmentHost(fragmentHost)
			.install();

		testFragmentHostWithItsOwnTests.start();

		engineInFramework().configurationParameter(CHECK_UNRESOLVED, "false")
			.execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(1,
				event(container(), lastSegment("fragment", descriptionOf(attachedTestFragment)),
					withParentLastSegment("bundle", descriptionOf(testFragmentHostWithItsOwnTests)),
					finishedSuccessfully()))
			.haveExactly(1,
				event(container("junit-vintage"),
					withParentLastSegment("fragment", descriptionOf(attachedTestFragment)), finishedSuccessfully()))
			.haveExactly(1,
				event(container("junit-vintage"),
					withParentLastSegment("bundle", descriptionOf(testFragmentHostWithItsOwnTests)),
					finishedSuccessfully()))
			.haveExactly(1, event(test("thisIsATest"), finishedSuccessfully()))
			.haveExactly(1, event(test("aTest"), finishedSuccessfully()))
			.haveExactly(1, event(test("bTest"), finishedSuccessfully()));
	}

	// Only generate the "Unattached fragments" hierarchy for non-test fragment
	// that fail to attach.
	@Test
	public void withOnlyTestFragmentUnattached_andTesterUnresolvedTrue_reportsError_andSkipsBundle() throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		Bundle unAttachedTestFragment = buildTestBundle(lp, JUnit4Test).fragmentHost("some.unresolved.bundle")
			.install();
		Bundle testHost = lp.bundle()
			.install();
		Bundle attachedTestFragment = buildTestBundle(lp, TestClass).fragmentHost(testHost.getSymbolicName())
			.start();

		engineInFramework().execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(0, event(container("unattachedFragments")))
			.haveExactly(1,
				event(unresolvedBundle(unAttachedTestFragment), finishedWithFailure(instanceOf(BundleException.class))))
			.haveExactly(1, event(fragment(attachedTestFragment), finishedSuccessfully()));
	}

	// Helper methods to call the bundle selector in the engine bundle's class.
	private DiscoverySelector selectBundle(String bsn, String version) {
		try {
			Class<?> bundleSelector = engineBundle.loadClass(BundleSelector.class.getName());
			Method m = bundleSelector.getMethod("selectBundle", String.class, String.class);
			return (DiscoverySelector) m.invoke(null, bsn, version);
		} catch (Exception e) {
			throw Exceptions.duck(e);
		}
	}

	private DiscoverySelector selectBundle(String bsn) {
		try {
			Class<?> bundleSelector = engineBundle.loadClass(BundleSelector.class.getName());
			Method m = bundleSelector.getMethod("selectBundle", String.class);
			return (DiscoverySelector) m.invoke(null, bsn);
		} catch (Exception e) {
			throw Exceptions.duck(e);
		}
	}

	private DiscoverySelector selectBundle(Bundle b) {
		try {
			Class<?> bundleSelector = engineBundle.loadClass(BundleSelector.class.getName());
			Method m = bundleSelector.getMethod("selectBundle", Bundle.class);
			return (DiscoverySelector) m.invoke(null, b);
		} catch (Exception e) {
			throw Exceptions.duck(e);
		}
	}

	@Test
	public void withBundleSelectors_onlyRunsTestsInSelectedBundles() throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		Bundle tb1 = startTestBundle(lp, JUnit4Test);
		Bundle tb2 = startTestBundle(lp, JUnit5Test);
		Bundle tb3 = buildTestBundle(lp, AnotherTestClass).bundleSymbolicName("test.bundle")
			.bundleVersion("2.3.4")
			.start();
		Bundle tb4 = buildTestBundle(lp, TestClass).bundleSymbolicName("test.bundle")
			.bundleVersion("1.2.3")
			.start();

		engineInFramework()
			.selectors(selectBundle(tb1), selectBundle(tb2.getSymbolicName()), selectBundle("test.bundle", "[1,2)"))
			.execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(1, event(containerClass(JUnit4Test), finishedSuccessfully()))
			.haveExactly(1, event(containerClass(JUnit5Test), finishedSuccessfully()))
			.haveExactly(1, event(containerClass(TestClass), finishedSuccessfully()))
			.haveExactly(0, event(containerClass(AnotherTestClass)));

		engineInFramework().selectors(selectBundle("test.bundle", "[2,3)"))
			.execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(1, event(containerClass(AnotherTestClass), finishedSuccessfully()))
			.haveExactly(0, event(containerClass(TestClass)));
	}

	@Test
	public void withBundleSelector_alsoRunsBundleFragments_ofSelectedBundle() throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		Bundle tb1 = buildTestBundle(lp, JUnit4Test).install();
		Bundle tb2 = buildTestBundle(lp, JUnit5Test).header("Fragment-Host", tb1.getSymbolicName())
			.install();

		engineInFramework().selectors(selectBundle(tb1))
			.execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(1, event(containerClass(JUnit4Test), finishedSuccessfully()))
			.haveExactly(1, event(containerClass(JUnit5Test), finishedSuccessfully()))
			.haveExactly(1, event(bundle(tb1), finishedSuccessfully()))
			.haveExactly(1, event(fragment(tb2), finishedSuccessfully()));

		engineInFramework().selectors(selectBundle(tb2))
			.execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(1, event(fragment(tb2), finishedSuccessfully()))
			.haveExactly(0, event(containerClass(JUnit4Test)));
	}

	@Test
	public void withSameTestClass_exportedByMultipleBundles_onlyRunsOnce() throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		// First bundle contains the class & exports it; second imports it from
		// first.
		BundleSpecBuilder bb = buildTestBundle(JUnit4Test);
		Bundle tb1 = bb.exportPackage(JUnit4Test.getPkg())
			.start();
		Bundle tb2 = lp.bundle()
			.importPackage(JUnit4Test.getPkg())
			.header("Test-Cases", JUnit4Test.getPkg())
			.start();

		engineInFramework().execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(1, event(containerClass(JUnit4Test), finishedSuccessfully()));
	}

	@Test
	public void withSameTestClass_exportedByMultipleBundles_andClassSelector_onlyRunsOnce() throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		// First bundle contains the class & exports it; second imports it from
		// first.
		BundleSpecBuilder bb = buildTestBundle(JUnit4Test);
		Bundle tb1 = bb.exportPackage(JUnit4Test.getPkg())
			.start();

		Bundle tb2 = lp.bundle()
			.importPackage(JUnit4Test.getPkg())
			.header("Test-Cases", JUnit4Test.fqName())
			.start();

		engineInFramework().selectors(selectClass(JUnit4Test.fqName()))
			.execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(1, event(containerClass(JUnit4Test), finishedSuccessfully()));
	}

	@Test
	public void usesBundleDisplayName() throws Exception {
		Launchpad lp = lpRule.getLaunchpad();

		Bundle tb1 = buildTestBundle(lp, JUnit4Test).header("Bundle-Name", "This is my name")
			.start();

		engineInFramework().execute()
			.all()
			.debug(debugStr)
			.assertThatEvents()
			.haveExactly(1, event(bundle(tb1), displayNameContaining("This is my name"), finishedSuccessfully()));
	}
}
