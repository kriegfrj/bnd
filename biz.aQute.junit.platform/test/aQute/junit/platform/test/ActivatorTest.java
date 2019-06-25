package aQute.junit.platform.test;

import static aQute.junit.constants.TesterConstants.TESTER_UNRESOLVED;
import static aQute.tester.utils.TestRunData.nameOf;
import static org.eclipse.jdt.internal.junit.model.ITestRunListener2.STATUS_FAILURE;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.AssumptionViolatedException;
import org.junit.BeforeClass;
import org.junit.ComparisonFailure;
import org.junit.Test;
import org.junit.platform.commons.JUnitException;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.MultipleFailuresError;
import org.opentest4j.TestAbortedException;
import org.osgi.framework.Bundle;
import org.osgi.framework.namespace.ExecutionEnvironmentNamespace;

import aQute.junit.platform.Activator;
import aQute.launchpad.LaunchpadBuilder;
import aQute.lib.io.IO;
import aQute.tester.test.AbstractActivatorTest;
import aQute.tester.utils.TestClassName;
import aQute.tester.utils.TestEntry;
import aQute.tester.utils.TestFailure;
import aQute.tester.utils.TestRunData;
import aQute.tester.utils.TestSourceBundler;

public class ActivatorTest extends AbstractActivatorTest {
	static final String			JUP_TESTPACKAGE_NAME						= "aQute.junit.platform.test";
	static final TestClassName	JUnit5Test									= new TestClassName(JUP_TESTPACKAGE_NAME,
		"JUnit5Test");
	static final TestClassName	Mixed35Test									= new TestClassName(JUP_TESTPACKAGE_NAME,
		"Mixed35Test");
	static final TestClassName	Mixed45Test									= new TestClassName(JUP_TESTPACKAGE_NAME,
		"Mixed45Test");
	static final TestClassName	JUnit3ComparisonTest						= new TestClassName(JUP_TESTPACKAGE_NAME,
		"JUnit3ComparisonTest");
	static final TestClassName	JUnit4ComparisonTest						= new TestClassName(JUP_TESTPACKAGE_NAME,
		"JUnit4ComparisonTest");
	static final TestClassName	JUnit5SimpleComparisonTest					= new TestClassName(JUP_TESTPACKAGE_NAME,
		"JUnit5SimpleComparisonTest");
	static final TestClassName	JUnitMixedComparisonTest					= new TestClassName(JUP_TESTPACKAGE_NAME,
		"JUnitMixedComparisonTest");
	static final TestClassName	JUnit5ParameterizedTest						= new TestClassName(JUP_TESTPACKAGE_NAME,
		"JUnit5ParameterizedTest");
	static final TestClassName	JUnit5DisplayNameTest						= new TestClassName(JUP_TESTPACKAGE_NAME,
		"JUnit5DisplayNameTest");
	static final TestClassName	JUnit4Skipper								= new TestClassName(JUP_TESTPACKAGE_NAME,
		"JUnit4Skipper");
	static final TestClassName	JUnit5Skipper								= new TestClassName(JUP_TESTPACKAGE_NAME,
		"JUnit5Skipper");
	static final TestClassName	JUnit5ContainerSkipped						= new TestClassName(JUP_TESTPACKAGE_NAME,
		"JUnit5ContainerSkipped");
	static final TestClassName	JUnit5ContainerSkippedWithCustomDisplayName	= new TestClassName(JUP_TESTPACKAGE_NAME,
		"JUnit5ContainerSkippedWithCustomDisplayName");
	static final TestClassName	JUnit4AbortTest								= new TestClassName(JUP_TESTPACKAGE_NAME,
		"JUnit4AbortTest");
	static final TestClassName	JUnit5AbortTest								= new TestClassName(JUP_TESTPACKAGE_NAME,
		"JUnit5AbortTest");

	public ActivatorTest() {
		super(Activator.class, "biz.aQute.junit.platform");
	}

	@BeforeClass
	public static void setupTestBundler() throws IOException {
		List<String> srcList = Arrays.asList("testresources", "../biz.aQute.tester.test/testresources");

		testBundler = new TestSourceBundler(srcList, JUnit3Test, JUnit4Test, With1Error1Failure, With2Failures,
			JUnit5Test, Mixed35Test, Mixed45Test, JUnit3ComparisonTest, JUnit4ComparisonTest,
			JUnit5SimpleComparisonTest, JUnitMixedComparisonTest, JUnit5ParameterizedTest, JUnit5DisplayNameTest,
			JUnit5Skipper, JUnit5ContainerSkipped, JUnit5ContainerSkippedWithCustomDisplayName, JUnit4Skipper,
			JUnit4AbortTest, JUnit5AbortTest);
	}

	@Test
	public void multipleMixedTests_areAllRun_withJupiterTest() {
		final ExitCode exitCode = runTests(JUnit3Test, JUnit4Test, JUnit5Test);

		assertThat(exitCode.exitCode).as("exit code")
			.isZero();
		assertThat(JUnit3Test.getCurrentThread()).as("junit3")
			.isSameAs(Thread.currentThread());
		assertThat(JUnit4Test.getCurrentThread()).as("junit4")
			.isSameAs(Thread.currentThread());
		assertThat(JUnit5Test.getCurrentThread()).as("junit5")
			.isSameAs(Thread.currentThread());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void multipleMixedTests_inASingleTestCase_areAllRun() {
		final ExitCode exitCode = runTests(Mixed35Test, Mixed45Test);

		assertThat(exitCode.exitCode).as("exit code")
			.isZero();
		assertThat(Mixed35Test.getStatic(Set.class, "methods")).as("Mixed JUnit 3 & 5")
			.containsExactlyInAnyOrder("testJUnit3", "junit5Test");
		assertThat(Mixed45Test.getStatic(Set.class, "methods")).as("Mixed JUnit 4 & 5")
			.containsExactlyInAnyOrder("junit4Test", "junit5Test");
	}

	@Test
	public void eclipseListener_reportsResults_acrossMultipleBundles() throws InterruptedException {
		TestClassName[][] tests = {
			{
				With2Failures, JUnit4Test
			}, {
				With1Error1Failure, JUnit5Test
			}
		};

		TestRunData result = runTestsEclipse(tests);

		assertThat(result.getTestCount()).as("testCount")
			.isEqualTo(9);

		// @formatter:off
		assertThat(result.getNameMap()
			.keySet()).as("executed")
			.contains(
					nameOf(With2Failures),
					nameOf(With2Failures, "test1"),
					nameOf(With2Failures, "test2"),
					nameOf(With2Failures, "test3"),
					nameOf(JUnit4Test),
					nameOf(JUnit4Test, "somethingElse"),
					nameOf(With1Error1Failure),
					nameOf(With1Error1Failure, "test1"),
					nameOf(With1Error1Failure, "test2"),
					nameOf(With1Error1Failure, "test3"),
					nameOf(JUnit5Test, "somethingElseAgain"),
					nameOf(testBundles.get(0)),
					nameOf(testBundles.get(1))
					);
		
		assertThat(result).as("result")
			.hasFailedTest(With2Failures, "test1", AssertionError.class)
			.hasSuccessfulTest(With2Failures, "test2")
			.hasFailedTest(With2Failures, "test3", CustomAssertionError.class)
			.hasSuccessfulTest(JUnit4Test, "somethingElse")
			.hasErroredTest(With1Error1Failure, "test1", RuntimeException.class)
			.hasSuccessfulTest(With1Error1Failure, "test2")
			.hasFailedTest(With1Error1Failure, "test3", AssertionError.class)
			.hasSuccessfulTest(JUnit5Test, "somethingElseAgain")
			;
		// @formatter:on
	}

	@Test
	public void eclipseListener_reportsComparisonFailures() throws InterruptedException {
		TestClassName[] tests = {
			JUnit3ComparisonTest, JUnit4ComparisonTest, JUnit5SimpleComparisonTest, JUnit5Test, JUnitMixedComparisonTest
		};

		TestRunData result = runTestsEclipse(tests);

		final String[] order = {
			"1", "2", "3.1", "3.2", "3.4", "4"
		};

		// @formatter:off
		assertThat(result).as("result")
			.hasFailedTest(JUnit3ComparisonTest, "testComparisonFailure", junit.framework.ComparisonFailure.class, "expected", "actual")
			.hasFailedTest(JUnit4ComparisonTest, "comparisonFailure", ComparisonFailure.class, "expected", "actual")
			.hasFailedTest(JUnit5SimpleComparisonTest, "somethingElseThatFailed", AssertionFailedError.class, "expected", "actual")
			.hasFailedTest(JUnitMixedComparisonTest, "multipleComparisonFailure", MultipleFailuresError.class,
				Stream.of(order).map(x -> "expected" + x).collect(Collectors.joining("\n\n")),
				Stream.of(order).map(x -> "actual" + x).collect(Collectors.joining("\n\n"))
				)
			;
		// @formatter:on
		TestFailure f = result.getFailureByName(nameOf(JUnit5SimpleComparisonTest, "emptyComparisonFailure"));
		assertThat(f).as("emptyComparisonFailure")
			.isNotNull();
		if (f != null) {
			assertThat(f.status).as("emptyComparisonFailure:status")
				.isEqualTo(STATUS_FAILURE);
			assertThat(f.expected).as("emptyComparisonFailure:expected")
				.isNull();
			assertThat(f.actual).as("emptyComparisonFailure:actual")
				.isNull();
		}
	}

	@Test
	public void eclipseListener_reportsParameterizedTests() throws InterruptedException {
		TestRunData result = runTestsEclipse(JUnit5ParameterizedTest);

		TestEntry methodTest = result.getTest(JUnit5ParameterizedTest, "parameterizedMethod");

		if (methodTest == null) {
			fail("Couldn't find method test entry for " + nameOf(JUnit5ParameterizedTest, "parameterizedMethod"));
			return;
		}

		assertThat(methodTest.parameterTypes).as("parameterTypes")
			.containsExactly("java.lang.String", "float");

		List<TestEntry> parameterTests = result.getChildrenOf(methodTest.testId)
			.stream()
			.map(x -> result.getById(x))
			.collect(Collectors.toList());

		assertThat(parameterTests.stream()
			.map(x -> x.testName)).as("testNames")
				.allMatch(x -> x.equals(nameOf(JUnit5ParameterizedTest, "parameterizedMethod")));
		assertThat(parameterTests.stream()).as("dynamic")
			.allMatch(x -> x.isDynamicTest);
		assertThat(parameterTests.stream()
			.map(x -> x.displayName)).as("displayNames")
				.containsExactlyInAnyOrder("1 ==> param: 'one', param2: 1.0", "2 ==> param: 'two', param2: 2.0",
					"3 ==> param: 'three', param2: 3.0", "4 ==> param: 'four', param2: 4.0",
					"5 ==> param: 'five', param2: 5.0");

		Optional<TestEntry> test4 = parameterTests.stream()
			.filter(x -> x.displayName.startsWith("4 ==>"))
			.findFirst();
		if (!test4.isPresent()) {
			fail("Couldn't find test result for parameter 4");
		} else {
			assertThat(parameterTests.stream()
				.filter(x -> result.getFailure(x.testId) != null)).as("failures")
					.containsExactly(test4.get());
		}
	}

	@Test
	public void eclipseListener_reportsMisconfiguredParameterizedTests() throws InterruptedException {
		TestRunData result = runTestsEclipse(JUnit5ParameterizedTest);

		TestEntry methodTest = result.getTest(JUnit5ParameterizedTest, "misconfiguredMethod");

		if (methodTest == null) {
			fail("Couldn't find method test entry for " + nameOf(JUnit5ParameterizedTest, "misconfiguredMethod"));
			return;
		}

		TestFailure failure = result.getFailure(methodTest.testId);
		if (failure == null) {
			fail("Expecting method:\n%s\nto have failed", methodTest);
		} else {
			assertThat(failure.trace).as("trace")
				.startsWith("org.junit.platform.commons.JUnitException: Could not find method: unknownMethod");
		}
	}

	@Test
	public void eclipseListener_reportsCustomNames_withOddCharacters() throws InterruptedException {
		TestRunData result = runTestsEclipse(JUnit5DisplayNameTest);

		final String[] methodList = {
			"test1", "testWithNonASCII", "testWithNonLatin"
		};
		final String[] displayList = {
			"Test 1", "Prüfung 2", "Δοκιμή 3"
		};

		for (int i = 0; i < methodList.length; i++) {
			final String method = methodList[i];
			final String display = displayList[i];
			TestEntry methodTest = result.getTest(JUnit5DisplayNameTest, method);
			if (methodTest == null) {
				fail("Couldn't find method test entry for " + nameOf(JUnit5ParameterizedTest, method));
				continue;
			}
			assertThat(methodTest.displayName).as(String.format("[%d] %s", i, method))
				.isEqualTo(display);
		}
	}

	@Test
	public void eclipseListener_reportsSkippedTests() throws InterruptedException {
		TestRunData result = runTestsEclipse(JUnit5Skipper, JUnit5ContainerSkipped,
			JUnit5ContainerSkippedWithCustomDisplayName, JUnit4Skipper);

		assertThat(result).as("result")
			.hasSkippedTest(JUnit5Skipper, "disabledTest", "with custom message")
			.hasSkippedTest(JUnit5ContainerSkipped, "with another message")
			.hasSkippedTest(JUnit5ContainerSkipped, "disabledTest2", "ancestor \"JUnit5ContainerSkipped\" was skipped")
			.hasSkippedTest(JUnit5ContainerSkipped, "disabledTest3", "ancestor \"JUnit5ContainerSkipped\" was skipped")
			.hasSkippedTest(JUnit5ContainerSkippedWithCustomDisplayName, "with a third message")
			.hasSkippedTest(JUnit5ContainerSkippedWithCustomDisplayName, "disabledTest2",
				"ancestor \"Skipper Class\" was skipped")
			.hasSkippedTest(JUnit5ContainerSkippedWithCustomDisplayName, "disabledTest3",
				"ancestor \"Skipper Class\" was skipped")
			.hasSkippedTest(JUnit4Skipper, "disabledTest", "This is a test");
	}

	@Test
	public void eclipseListener_reportsAbortedTests() throws InterruptedException {
		TestRunData result = runTestsEclipse(JUnit5AbortTest, JUnit4AbortTest);

		assertThat(result).as("result")
			.hasAbortedTest(JUnit5AbortTest, "abortedTest",
				new TestAbortedException("Assumption failed: I just can't go on"))
			.hasAbortedTest(JUnit4AbortTest, "abortedTest", new AssumptionViolatedException("Let's get outta here"));
	}

	@Test
	public void eclipseListener_handlesNoEnginesGracefully() throws Exception {
		try (LaunchpadBuilder builder = new LaunchpadBuilder()) {
			IO.close(this.builder);
			// builder.debug();
			builder.bndrun("no-engines.bndrun")
					.excludeExport("aQute.junit.bundle.*");
			this.builder = builder;
			TestRunData result = runTestsEclipse(JUnit5AbortTest, JUnit4AbortTest);
			assertThat(result).hasErroredTest("Initialization Error",
				new JUnitException("Couldn't find any registered TestEngines"));
		}
	}

	@Test
	public void eclipseListener_handlesNoJUnit3Gracefully() throws Exception {
		builder.excludeExport("junit.framework");
		TestClassName[] tests = {
			JUnit4ComparisonTest, JUnit5Test, JUnit5SimpleComparisonTest
		};

		TestRunData result = runTestsEclipse(tests);

		assertThat(result).as("result")
			.hasFailedTest(JUnit5SimpleComparisonTest, "somethingElseThatFailed", AssertionFailedError.class,
				"expected", "actual");
		TestFailure f = result.getFailureByName(nameOf(JUnit5SimpleComparisonTest, "emptyComparisonFailure"));
		assertThat(f).as("emptyComparisonFailure")
			.isNotNull();
		if (f != null) {
			assertThat(f.status).as("emptyComparisonFailure:status")
				.isEqualTo(STATUS_FAILURE);
			assertThat(f.expected).as("emptyComparisonFailure:expected")
				.isNull();
			assertThat(f.actual).as("emptyComparisonFailure:actual")
				.isNull();
		}
	}

	@Test
	public void eclipseListener_handlesNoJUnit4Gracefully() throws Exception {
		try (LaunchpadBuilder builder = new LaunchpadBuilder()) {
			IO.close(this.builder);
			builder.debug();
			builder.bndrun("no-vintage-engine.bndrun").excludeExport("aQute.junit.bundle.*").excludeExport("org.junit");
			this.builder = builder;
			TestClassName[] tests = {
				JUnit3ComparisonTest, JUnit5Test, JUnit5SimpleComparisonTest
			};

			TestRunData result = runTestsEclipse(tests);
			assertThat(result).as("result")
				.hasFailedTest(JUnit5SimpleComparisonTest, "somethingElseThatFailed", AssertionFailedError.class,
					"expected", "actual");
			TestFailure f = result.getFailureByName(nameOf(JUnit5SimpleComparisonTest, "emptyComparisonFailure"));
			assertThat(f).as("emptyComparisonFailure")
				.isNotNull();
			if (f != null) {
				assertThat(f.status).as("emptyComparisonFailure:status")
					.isEqualTo(STATUS_FAILURE);
				assertThat(f.expected).as("emptyComparisonFailure:expected")
					.isNull();
				assertThat(f.actual).as("emptyComparisonFailure:actual")
					.isNull();
			}
		}
	}

	@Test
	public void testerUnresolvedTrue_isPassedThroughToBundleEngine() {
		builder.set(TESTER_UNRESOLVED, "true");
		AtomicReference<Bundle> bundleRef = new AtomicReference<>();
		TestRunData result = runTestsEclipse(() -> {
			bundleRef.set(lp.bundle().importPackage("some.unknown.package")
					.requireCapability(ExecutionEnvironmentNamespace.EXECUTION_ENVIRONMENT_NAMESPACE)
					.filter("(&(osgi.ee=JavaSE)(version=1.8))")
					.install());
		}, JUnit3Test, JUnit4Test);

		assertThat(result).hasSuccessfulTest("Unresolved bundles");
	}

	@Test
	public void testerUnresolvedFalse_isPassedThroughToBundleEngine() {
		builder.set(TESTER_UNRESOLVED, "false");
		AtomicReference<Bundle> bundleRef = new AtomicReference<>();
		TestRunData result = runTestsEclipse(() -> {
			bundleRef.set(lp.bundle().importPackage("some.unknown.package")
					.requireCapability(ExecutionEnvironmentNamespace.EXECUTION_ENVIRONMENT_NAMESPACE)
					.filter("(&(osgi.ee=JavaSE)(version=1.8))")
					.install());
		}, JUnit3Test, JUnit4Test);

		assertThat(result.getNameMap()
			.get("Unresolved bundles")).isNull();
	}
}
