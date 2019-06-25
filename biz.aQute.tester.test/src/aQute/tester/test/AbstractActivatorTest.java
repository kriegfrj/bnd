package aQute.tester.test;

import static aQute.junit.constants.TesterConstants.TESTER_CONTINUOUS;
import static aQute.junit.constants.TesterConstants.TESTER_NAMES;
import static aQute.junit.constants.TesterConstants.TESTER_PORT;
import static aQute.junit.constants.TesterConstants.TESTER_SEPARATETHREAD;
import static aQute.junit.constants.TesterConstants.TESTER_TRACE;

import java.io.IOException;
import java.net.ServerSocket;
import java.security.Permission;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;
import org.eclipse.jdt.internal.junit.model.ITestRunListener2;
import org.eclipse.jdt.internal.junit.model.RemoteTestRunnerClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleException;

import aQute.launchpad.Launchpad;
import aQute.launchpad.LaunchpadBuilder;
import aQute.lib.exceptions.Exceptions;
import aQute.lib.io.IO;
import aQute.tester.utils.TestClassName;
import aQute.tester.utils.TestRunData;
import aQute.tester.utils.TestRunDataAssert;
import aQute.tester.utils.TestRunListener;
import aQute.tester.utils.TestSourceBundler;

// Because we're not in the same project as aQute.junit.TesterConstants and its bundle-private.
@SuppressWarnings("restriction")
public abstract class AbstractActivatorTest extends SoftAssertions {

	static final String								BND_TEST_THREAD		= "bnd Runtime Test Bundle";

	private final Class<? extends BundleActivator>	activatorClass;
	private final String							tester;

	protected static TestSourceBundler				testBundler;

	private boolean									DEBUG				= false;

	static AbstractActivatorTest					me;

	public static final String						TESTPACKAGE_NAME	= "aQute.tester.test";
	public static final TestClassName				JUnit3Test			= new TestClassName(TESTPACKAGE_NAME,
		"JUnit3Test");
	public static final TestClassName				JUnit4Test			= new TestClassName(TESTPACKAGE_NAME,
		"JUnit4Test");
	public static final TestClassName				With2Failures		= new TestClassName(TESTPACKAGE_NAME,
		"With2Failures");
	public static final TestClassName				With1Error1Failure	= new TestClassName(TESTPACKAGE_NAME,
		"With1Error1Failure");

	@After
	public void after() {
		assertAll();
	}

	protected AbstractActivatorTest(Class<? extends BundleActivator> clazz, String tester) {
		activatorClass = clazz;
		this.tester = tester;
	}

	// This extends Error rather than SecurityException so that it can traverse
	// the catch(Exception) statements in the code-under-test.
	protected class ExitCode extends Error {
		private static final long	serialVersionUID	= -1498037177123939551L;
		public final int			exitCode;
		final StackTraceElement[]	stack;

		public ExitCode(int exitCode, StackTraceElement[] stack) {
			this.exitCode = exitCode;
			this.stack = stack;
		}
	}

	// To catch calls to System.exit() calls within bnd.aQute.junit that
	// otherwise cause the entire test harness to exit.
	class ExitCheck extends SecurityManager {
		@Override
		public void checkPermission(Permission perm) {
		}

		@Override
		public void checkPermission(Permission perm, Object context) {
		}

		@Override
		public void checkExit(int status) {
			// Because the activator might have been loaded in a different
			// classloader, need to check names and not objects.
			if (Stream.of(getClassContext())
				.anyMatch(x -> x.getName()
					.equals(activatorClass.getName()))) {
				throw new ExitCode(status, Thread.currentThread()
					.getStackTrace());
			}
			super.checkExit(status);
		}
	}

	protected LaunchpadBuilder	builder;
	protected Launchpad			lp;
	SecurityManager				oldManager;

	protected TestRunDataAssert assertThat(TestRunData a) {
		return proxy(TestRunDataAssert.class, TestRunData.class, a);
	}

	@Before
	public void setUp() {

		builder = new LaunchpadBuilder();
		builder.bndrun(tester + ".bndrun")
			.excludeExport("aQute.junit.bundle.*");
		if (DEBUG) {
			builder.debug()
				.set(TESTER_TRACE, "true");
		}
		lp = null;
		oldManager = System.getSecurityManager();
		System.setSecurityManager(new ExitCheck());

		testBundler.reset();
	}

	@After
	public void tearDown() {
		System.setSecurityManager(oldManager);
		IO.close(lp);
		IO.close(builder);
	}

	@Test
	public void start_withNoSeparateThreadProp_runsInMainThread() {
		runTests(0, JUnit3Test);
		assertThat(JUnit3Test.getCurrentThread()).as("thread")
			.isSameAs(Thread.currentThread());
	}

	@Test
	public void start_withSeparateThreadPropFalse_startsInMainThread() {
		builder.set(TESTER_SEPARATETHREAD, "false");
		runTests(0, JUnit3Test);
		assertThat(JUnit3Test.getCurrentThread()).as("thread")
			.isSameAs(Thread.currentThread());
	}

	// Gets a handle on the Bnd test thread, if it exists.
	// Try to build some resilience into this test to avoid
	// race conditions when the new test starts.
	private Thread getBndTestThread() throws InterruptedException {
		int threadCount = Thread.activeCount();
		Thread[] threads = new Thread[threadCount + 10];

		final long endTime = System.currentTimeMillis() + 10000;

		while (System.currentTimeMillis() < endTime) {
			threadCount = Thread.enumerate(threads);

			if (threadCount == threads.length) {
				threads = new Thread[threadCount + 10];
				continue;
			}

			for (Thread thread : threads) {
				if (thread.getName()
					.equals(BND_TEST_THREAD)) {
					return thread;
				}
			}
			Thread.sleep(10);
		}
		return null;
	}

	@Test
	public void start_withSeparateThreadProp_startsInNewThread() throws Exception {
		lp = builder.set(TESTER_SEPARATETHREAD, "true")
			.create();
		addTesterBundle();

		final CountDownLatch latch = new CountDownLatch(1);

		final Thread bndThread = getBndTestThread();

		// Don't assert softly, since if we can't find this thread we can't do
		// the other tests.
		Assertions.assertThat(bndThread)
			.as("thread started")
			.isNotNull()
			.isNotSameAs(Thread.currentThread());

		Runnable r = lp.getService(Runnable.class)
			.orElse(null);
		assertThat(r).as("runnable")
			.isNull();

		final AtomicReference<Throwable> exception = new AtomicReference<>();
		bndThread.setUncaughtExceptionHandler((thread, e) -> {
			exception.set(e);
			latch.countDown();
		});

		// Can't start the test bundle until after the exception handler
		// is in place to ensure we're ready to catch System.exit().
		Bundle tb = addTestBundle(JUnit3Test);

		final AtomicBoolean flag = new AtomicBoolean(false);
		// Wait for the exception handler to catch the exit.
		assertThatCode(() -> flag.set(latch.await(5000, TimeUnit.MILLISECONDS))).as("wait for exit")
			.doesNotThrowAnyException();

		assertThat(flag.get()).as("flag")
			.isTrue();

		assertThat(exception.get()).as("exited")
			.isInstanceOf(ExitCode.class);

		if (!(exception.get() instanceof ExitCode)) {
			return;
		}

		final ExitCode ee = (ExitCode) exception.get();

		assertThat(ee.exitCode).as("exitCode")
			.isZero();

		final Thread currentThread = JUnit3Test.getCurrentThread();
		assertThat(currentThread).as("exec thread")
			.isNotNull()
			.isNotSameAs(Thread.currentThread())
			.isSameAs(bndThread);
	}

	protected ExitCode runTests(int expectedExit, TestClassName... classes) {
		final ExitCode exitCode = runTests(classes);
		assertThat(exitCode.exitCode).as("exitCode")
			.isEqualTo(expectedExit);
		return exitCode;
	}

	protected Bundle		testBundle;
	protected List<Bundle>	testBundles	= new ArrayList<>(10);
	protected Bundle		testerBundle;

	protected interface Callback {
		public void run() throws Exception;
	}

	protected ExitCode runTests(TestClassName... classes) {
		return runTests((Callback) null, classes);
	}

	protected ExitCode runTests(TestClassName[]... bundles) {
		return runTests((Callback) null, bundles);
	}

	protected ExitCode runTests(Callback postCreateCallback, TestClassName... classes) {
		return runTests(postCreateCallback, new TestClassName[][] {
			classes
		});
	}

	protected ExitCode runTests(Callback postCreateCallback, TestClassName[]... bundles) {
		lp = builder.set("launch.services", "true")
			.set(TESTER_TRACE, "true")
			.create();

		Stream.of(bundles)
			.forEach(this::addTestBundle);

		addTesterBundle();

		final Optional<Runnable> oR = lp.getService(Runnable.class);

		// Use a hard assertion here to short-circuit the test if no runnable
		// found.
		Assertions.assertThat(oR)
			.as("runnable")
			.isPresent();

		final Runnable r = oR.get();

		assertThat(r.getClass()
			.getName()).as("runnable")
				.isEqualTo(activatorClass.getName());

		if (postCreateCallback != null) {
			try {
				postCreateCallback.run();
			} catch (Exception e) {
				Exceptions.duck(e);
			}
		}

		try {
			r.run();
			throw new AssertionError("Expecting run() to call System.exit(), but it didn't");
		} catch (ExitCode e) {
			return e;
		}
	}

	@Test
	public void multipleMixedTests_areAllRun() {
		final ExitCode exitCode = runTests(JUnit3Test, JUnit4Test);

		assertThat(exitCode.exitCode).as("exit code")
			.isZero();
		assertThat(JUnit3Test.getCurrentThread()).as("junit3")
			.isSameAs(Thread.currentThread());
		assertThat(JUnit4Test.getCurrentThread()).as("junit4")
			.isSameAs(Thread.currentThread());
	}

	@Test
	public void multipleTests_acrossMultipleBundles_areAllRun() {
		final ExitCode exitCode = runTests(new TestClassName[] {
			JUnit3Test
		}, new TestClassName[] {
			JUnit4Test
		});

		assertThat(exitCode.exitCode).as("exit code")
			.isZero();
		assertThat(JUnit3Test.getCurrentThread()).as("junit3")
			.isSameAs(Thread.currentThread());
		assertThat(JUnit4Test.getCurrentThread()).as("junit4")
			.isSameAs(Thread.currentThread());
	}

	@Test
	public void testerNames_isHonouredByTester() {
		builder.set(TESTER_NAMES, With2Failures.fqName() + "," + JUnit4Test.fqName() + ":theOther");
		runTests(2, JUnit3Test, JUnit4Test, With2Failures);
		assertThat(JUnit3Test.getCurrentThread()).as("JUnit3 thread")
			.isNull();
		assertThat(JUnit4Test.getFlag("theOtherFlag")).as("theOther")
			.isTrue();
		assertThat(JUnit4Test.getCurrentThread()).as("JUnit4 thread")
			.isNull();
	}

	public void runTesterAndWait() {
		runTester();
		// This is to avoid race conditions and make sure that the
		// tester thread has actually gotten to the point where it is
		// waiting for new bundles.
		waitForTesterToWait();
	}

	public void runTester() {
		if (lp == null) {
			lp = builder.set("launch.services", "true")
				.create();
		}

		addTesterBundle();

		final Optional<Runnable> oR = lp.getService(Runnable.class);
		Assertions.assertThat(oR)
			.as("runnable")
			.isPresent();
		final Runnable r = oR.get();

		runThread = new Thread(r);
		runThread.setUncaughtExceptionHandler((t, x) -> uncaught.set(x));
		runThread.start();
	}

	@Test
	public void whenNoTestBundles_waitForTestBundle_thenRunAndExit() throws Exception {
		runTesterAndWait();
		addTestBundle(JUnit4Test, With2Failures);
		runThread.join(10000);
		assertThat(JUnit4Test.getCurrentThread()).as("thread:after")
			.isSameAs(runThread);
		assertThat(JUnit4Test.getStatic(AtomicBoolean.class, "theOtherFlag")).as("otherFlag:after")
			.isTrue();
		assertExitCode(2);
	}

	public void assertExitCode(int exitCode) {
		if (uncaught.get() instanceof ExitCode) {
			assertThat(((ExitCode) uncaught.get()).exitCode).as("exitCode")
				.isEqualTo(exitCode);
		} else {
			failBecauseExceptionWasNotThrown(ExitCode.class);
		}
	}

	public void waitForTesterToWait() {
		final long waitTime = 10000;
		final long endTime = System.currentTimeMillis() + waitTime;
		int waitCount = 0;
		try {
			OUTER: while (true) {
				Thread.sleep(10);
				final Thread.State state = runThread.getState();
				switch (state) {
					case TERMINATED :
					case TIMED_WAITING :
					case WAITING :
						if (waitCount++ > 5) {
							break OUTER;
						}
						break;
					default :
						waitCount = 0;
						break;
				}
				if (System.currentTimeMillis() > endTime) {
					throw new InterruptedException("Thread still hasn't entered wait state after " + waitTime + "ms");
				}
			}
		} catch (InterruptedException e) {
			throw Exceptions.duck(e);
		}
		// Check that it hasn't terminated.
		assertThat(runThread.getState()).as("runThread")
			.isIn(Thread.State.WAITING, Thread.State.TIMED_WAITING);
	}

	Thread						runThread;
	AtomicReference<Throwable>	uncaught	= new AtomicReference<>();

	@Test
	public void testerContinuous_runsTestsContinuously() {
		builder.set(TESTER_CONTINUOUS, "true");
		runTesterAndWait();
		addTestBundle(JUnit4Test);
		waitForTesterToWait();
		assertThat(JUnit4Test.getCurrentThread()).as("junit4")
			.isSameAs(runThread);
		addTestBundle(JUnit3Test);
		waitForTesterToWait();
		assertThat(JUnit3Test.getCurrentThread()).as("junit3")
			.isSameAs(runThread);
		Bundle old4Bundle = JUnit4Test.getBundle();
		addTestBundle(JUnit4Test);
		waitForTesterToWait();
		assertThat(JUnit4Test.getCurrentThread()).as("junit4 take 2")
			.isSameAs(runThread);
		assertThat(JUnit4Test.getBundle()).as("different bundle")
			.isNotSameAs(old4Bundle);
	}

	public static class CustomAssertionError extends AssertionError {
		private static final long serialVersionUID = 1L;
	}

	@Test
	public void exitCode_countsErrorsAndFailures() {
		final ExitCode exitCode = runTests(JUnit4Test, With2Failures, With1Error1Failure);
		assertThat(exitCode.exitCode).isEqualTo(4);
	}

	protected TestRunData runTestsEclipse(Callback postCreateCallback, TestClassName... tests) {
		return runTestsEclipse(postCreateCallback, new TestClassName[][] {
			tests
		});
	}

	RemoteTestRunnerClient client;

	protected TestRunListener startEclipseListener() {
		if (lp != null) {
			throw new IllegalStateException("Framework already started");
		}
		int port = findFreePort();
		client = new RemoteTestRunnerClient();
		TestRunListener listener = new TestRunListener(this, DEBUG);
		client.startListening(new ITestRunListener2[] {
			listener
		}, port);

		builder.set(TESTER_PORT, Integer.toString(port));
		return listener;
	}

	protected TestRunData runTestsEclipse(Callback postCreateCallback, TestClassName[]... testBundles) {
		TestRunListener listener = startEclipseListener();
		final long startTime = System.currentTimeMillis();
		try {
			runTests(postCreateCallback, testBundles);
			if (listener.getLatestRunData() != null) {
				listener.getLatestRunData()
					.setActualRunTime(System.currentTimeMillis() - startTime);
			}
			listener.waitForClientToFinish(10000);
			listener.checkRunTime();
			return listener.getLatestRunData();
		} catch (InterruptedException e) {
			throw Exceptions.duck(e);
		} finally {
			client.stopWaiting();
		}
	}

	protected TestRunData runTestsEclipse(TestClassName... tests) throws InterruptedException {
		return runTestsEclipse(null, tests);
	}

	protected TestRunData runTestsEclipse(TestClassName[]... testBundles) throws InterruptedException {
		return runTestsEclipse(null, testBundles);
	}

	// Copied from org.eclipse.jdt.launching.SocketUtil
	public static int findFreePort() {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		} catch (IOException e) {
			throw new RuntimeException("Couldn't get port for test", e);
		}
	}

	protected Bundle addTestBundle(TestClassName... testClasses) {
		testBundle = testBundler.buildTestBundle(lp, testClasses)
			.start();
		testBundles.add(testBundle);
		return testBundle;
	}

	protected void addTesterBundle() {
		lp.bundles(tester)
			.forEach(t -> {
				if (testerBundle != null) {
					throw new IllegalStateException("Attempted to load tester bundle twice");
				}
				testerBundle = t;
				try {
					t.start();
				} catch (BundleException e) {
					Assertions.fail("Couldn't start tester bundle", e);
				}
			});
	}
}
