package aQute.tester.test;

import static aQute.junit.constants.TesterConstants.TESTER_UNRESOLVED;
import static aQute.tester.utils.TestRunData.nameOf;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.namespace.ExecutionEnvironmentNamespace;

import aQute.junit.Activator;
import aQute.junit.UnresolvedTester;
import aQute.tester.utils.TestClassName;
import aQute.tester.utils.TestRunData;
import aQute.tester.utils.TestSourceBundler;
import junit.framework.AssertionFailedError;

// This suppression is because we're not building in the same project.
public class ActivatorTest extends AbstractActivatorTest {

	static final TestClassName	JUnit3NonStaticBC		= new TestClassName(TESTPACKAGE_NAME, "JUnit3NonStaticBC");
	static final TestClassName	JUnit3StaticFieldBC		= new TestClassName(TESTPACKAGE_NAME, "JUnit3StaticFieldBC");
	static final TestClassName	JUnit3NonStaticFieldBC	= new TestClassName(TESTPACKAGE_NAME, "JUnit3NonStaticFieldBC");

	public ActivatorTest() {
		super(Activator.class, "biz.aQute.tester");
	}

	@BeforeClass
	public static void setupTestBundler() throws IOException {
		List<String> srcList = Arrays.asList("testresources", "../biz.aQute.tester.test/testresources");

		testBundler = new TestSourceBundler(srcList, JUnit3Test, JUnit4Test, With1Error1Failure, With2Failures,
			JUnit3NonStaticBC, JUnit3StaticFieldBC, JUnit3NonStaticFieldBC);
	}

	@AfterClass
	public static void closeBundler() {
		testBundler.close();
	}

	@Test
	public void eclipseReporter_reportsResults() throws InterruptedException {
		TestRunData result = runTestsEclipse(With2Failures, JUnit4Test, With1Error1Failure);

		assertThat(result.getTestCount()).as("testCount")
			.isEqualTo(8);

		// @formatter:off
		assertThat(result.getNameMap()
			.keySet()).as("executed")
			.containsExactlyInAnyOrder(
					nameOf(With2Failures),
					nameOf(With2Failures, "test1"),
					nameOf(With2Failures, "test2"),
					nameOf(With2Failures, "test3"),
					nameOf(JUnit4Test),
					nameOf(JUnit4Test, "somethingElse"),
					nameOf(JUnit4Test, "theOther"),
					nameOf(With1Error1Failure),
					nameOf(With1Error1Failure, "test1"),
					nameOf(With1Error1Failure, "test2"),
					nameOf(With1Error1Failure, "test3"));

		// Note: in the old Tester, all failures were (incorrectly) reported as errors.
		// This test verifies the actual behaviour, rather than the desired behaviour.
		assertThat(result)
			.hasErroredTest(With2Failures, "test1", AssertionError.class)
			.hasSuccessfulTest(With2Failures, "test2")
			.hasErroredTest(With2Failures, "test3", CustomAssertionError.class)
			.hasSuccessfulTest(JUnit4Test, "somethingElse")
			.hasErroredTest(With1Error1Failure, "test1", RuntimeException.class)
			.hasSuccessfulTest(With1Error1Failure, "test2")
			.hasErroredTest(With1Error1Failure, "test3", AssertionError.class)
			;
		// @formatter:on
	}

	// This functionality doesn't work under JUnit 4 for the old tester.
	@Test
	public void run_setsBundleContext_forJUnit3() {
		runTests(0, JUnit3Test, JUnit3NonStaticBC, JUnit3StaticFieldBC, JUnit3NonStaticFieldBC);
		assertThat(JUnit3Test.getStatic(AtomicReference.class, "bundleContext")
			.get()).as("static setBundleContext()")
				.isSameAs(testBundle.getBundleContext());
		assertThat(JUnit3Test.getStatic(AtomicReference.class, "actualBundleContext")
			.get()).as("static setBundleContext() - in method")
				.isSameAs(testBundle.getBundleContext());

		assertThat(JUnit3NonStaticBC.getBundleContext()).as("nonstatic setBundleContext()")
			.isSameAs(testBundle.getBundleContext());
		assertThat(JUnit3NonStaticBC.getActualBundleContext()).as("nonstatic setBundleContext() - in method")
			.isSameAs(testBundle.getBundleContext());

		assertThat(JUnit3StaticFieldBC.getActualBundleContext()).as("static context field")
			.isSameAs(testBundle.getBundleContext());
		assertThat(JUnit3NonStaticFieldBC.getActualBundleContext()).as("nonstatic context field")
			.isSameAs(testBundle.getBundleContext());
	}

	@Test
	public void testerUnresolvedTrue_withUnresolvedBundle_fails() {
		builder.set(TESTER_UNRESOLVED, "true");
		AtomicReference<Bundle> bundleRef = new AtomicReference<>();
		TestRunData result = runTestsEclipse(() -> {
			bundleRef.set(lp.bundle()
				.importPackage("some.unknown.package")
				.requireCapability(ExecutionEnvironmentNamespace.EXECUTION_ENVIRONMENT_NAMESPACE)
				.filter("(&(osgi.ee=JavaSE)(version=1.8))")
				.install());
		}, JUnit3Test, JUnit4Test);

		assertThat(result).hasFailedTest(UnresolvedTester.class, "testAllResolved", AssertionFailedError.class);

	}

	@Test
	public void testerUnresolvedFalse_withUnresolvedBundle_doesntFail() {
		builder.set(TESTER_UNRESOLVED, "false");
		AtomicReference<Bundle> bundleRef = new AtomicReference<>();
		TestRunData result = runTestsEclipse(() -> {
			bundleRef.set(lp.bundle()
				.importPackage("some.unknown.package")
				.requireCapability(ExecutionEnvironmentNamespace.EXECUTION_ENVIRONMENT_NAMESPACE)
				.filter("(&(osgi.ee=JavaSE)(version=1.8))")
				.install());
		}, JUnit3Test, JUnit4Test);

		assertThat(result.getNameMap()
			.get(UnresolvedTester.class.getName())).isNull();
	}
}
