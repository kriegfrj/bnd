package aQute.junit.platform;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.ComparisonFailure;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestExecutionResult.Status;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.MultipleFailuresError;
import org.opentest4j.ValueWrapper;

public class JUnitEclipseListener implements TestExecutionListener, Closeable {
	@Override
	public void dynamicTestRegistered(TestIdentifier testIdentifier) {
		visitEntry(testIdentifier, true);
	}

	// This is called if the execution is skipped without it having begun.
	// This contrasts to an assumption failure, which happens during a test
	// execution that has already started.
	@Override
	public void executionSkipped(TestIdentifier test, String reason) {
		info("JUnitEclipseReporter: testPlanSkipped: " + test + ", reason: " + reason);
		if (test.isContainer() && testPlan != null) {
			testPlan.getChildren(test)
				.forEach(x -> executionSkipped(x, "ancestor \"" + test.getDisplayName() + "\" was skipped"));
		}
		// This is a departure from the Eclipse built-in JUnit 5 tester in two
		// ways (hopefully both improvements):
		// 1. Eclipse's version doesn't send skip notifications for containers.
		// I found that doing so causes Eclipse's JUnit GUI to show the
		// container as skipped - Eclipse's version doesn't show the container
		// as skipped, only the children.
		// 2. Eclipse handles "Ignore/Skip" and "AssumptionFailure" differently.
		// Reporting them all as assumption failures triggers the GUI to display
		// the skip reason in the failure trace, which the Eclipse
		// implementation doesn't do.
		if (!test.isContainer()) {
			message("%TESTS  ", test);
		}
		message("%FAILED ", test, "@AssumptionFailure: ");
		message("%TRACES ");
		out.println("Skipped: " + reason);
		message("%TRACEE ");
		if (!test.isContainer()) {
			message("%TESTE  ", test);
		}
	}

	private final BufferedReader	in;
	private final PrintWriter		out;
	private long					startTime;
	private TestPlan				testPlan;
	private boolean					verbose	= false;

	public JUnitEclipseListener(int port) throws Exception {
		Socket socket = null;
		ConnectException e = null;
		for (int i = 0; socket == null && i < 30; i++) {
			try {
				socket = new Socket(InetAddress.getByName(null), port);
			} catch (ConnectException ce) {
				e = ce;
				Thread.sleep(i * 100);
			}
		}
		if (socket == null) {
			info("JUnitEclipseReporter: Cannot open the JUnit Port: " + port + " " + e);
			System.exit(254);
			throw new AssertionError("unreachable");
		}

		in = new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
		out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), UTF_8));
	}

	@Override
	public void testPlanExecutionStarted(TestPlan testPlan) {
		final long realCount = testPlan.countTestIdentifiers(x -> x.isTest());
		info("JUnitEclipseReporter: testPlanExecutionStarted: " + testPlan + ", realCount: " + realCount);
		message("%TESTC  ", realCount + " v2");
		this.testPlan = testPlan;
		for (TestIdentifier root : testPlan.getRoots()) {
			for (TestIdentifier child : testPlan.getChildren(root)) {
				visitEntry(child);
			}
		}
		startTime = System.currentTimeMillis();
	}

	@Override
	public void testPlanExecutionFinished(TestPlan testPlan) {
		message("%RUNTIME", "" + (System.currentTimeMillis() - startTime));
		out.flush();
	}

	private void sendTrace(Throwable t) {
		message("%TRACES ");
		t.printStackTrace(out);
		if (verbose) {
			t.printStackTrace(System.err);
		}
		out.println();
		message("%TRACEE ");
	}

	private void sendExpectedAndActual(CharSequence expected, CharSequence actual) {
		message("%EXPECTS");
		out.println(expected);
		info(expected);
		message("%EXPECTE");

		message("%ACTUALS");
		out.println(actual);
		info(actual);
		message("%ACTUALE");
	}

	@Override
	public void executionStarted(TestIdentifier test) {
		info("JUnitEclipseReporter: Execution started: " + test);
		if (test.isTest()) {
			message("%TESTS  ", test);
		}
	}

	@Override
	public void executionFinished(TestIdentifier test, TestExecutionResult testExecutionResult) {
		info("JUnitEclipseReporter: Execution finished: " + test);
		Status result = testExecutionResult.getStatus();
		if (test.isTest()) {
			if (result != Status.SUCCESSFUL) {
				final boolean assumptionFailed = result == Status.ABORTED;
				info("JUnitEclipseReporter: assumption failed: " + assumptionFailed);
				Optional<Throwable> throwableOp = testExecutionResult.getThrowable();
				if (throwableOp.isPresent()) {
					Throwable exception = throwableOp.get();
					info("JUnitEclipseReporter: throwable: " + exception);

					if (assumptionFailed || exception instanceof AssertionError) {
						info("JUnitEclipseReporter: failed: " + exception + " assumptionFailed: " + assumptionFailed);
						message("%FAILED ", test, (assumptionFailed ? "@AssumptionFailure: " : ""));

						sendExpectedAndActual(exception);

					} else {
						info("JUnitEclipseReporter: error");
						message("%ERROR  ", test);
					}
					sendTrace(exception);
				}
			}
			message("%TESTE  ", test);
		} else { // container
			if (result != Status.SUCCESSFUL) {
				message("%ERROR  ", test);
				Optional<Throwable> throwableOp = testExecutionResult.getThrowable();
				if (throwableOp.isPresent()) {
					sendTrace(throwableOp.get());
				}
			}
		}
	}

	private static void appendString(StringBuilder b, String s) {
		if (b.length() > 0) {
			b.append("\n\n");
		}
		b.append(s);
	}

	private boolean sendExpectedAndActual(Throwable exception, StringBuilder expectedBuilder,
		StringBuilder actualBuilder) {
		BooleanSupplier b;
		// switch is based on the class name rather than using instanceof
		// to avoid hard dependency on the assertion types.
		switch (exception.getClass()
			.getName()) {
			case "org.opentest4j.AssertionFailedError" :
				b = () -> {
					AssertionFailedError assertionFailedError = (AssertionFailedError) exception;
					ValueWrapper expected = assertionFailedError.getExpected();
					ValueWrapper actual = assertionFailedError.getActual();
					if (expected == null || actual == null) {
						return false;
					}
					appendString(expectedBuilder, expected.getStringRepresentation());
					appendString(actualBuilder, actual.getStringRepresentation());
					return true;
				};
				break;
			case "org.junit.ComparisonFailure" :
				b = () -> {
					ComparisonFailure comparisonFailure = (ComparisonFailure) exception;
					String expected = comparisonFailure.getExpected();
					String actual = comparisonFailure.getActual();
					if (expected == null || actual == null) {
						return false;
					}
					appendString(expectedBuilder, expected);
					appendString(actualBuilder, actual);
					return true;
				};
				break;
			case "junit.framework.ComparisonFailure" :
				b = () -> {
					junit.framework.ComparisonFailure comparisonFailure = (junit.framework.ComparisonFailure) exception;
					String expected = comparisonFailure.getExpected();
					String actual = comparisonFailure.getActual();
					if (expected == null || actual == null) {
						return false;
					}
					appendString(expectedBuilder, expected);
					appendString(actualBuilder, actual);
					return true;
				};
				break;
			case "org.opentest4j.MultipleFailuresError" :
				b = () -> ((MultipleFailuresError) exception).getFailures()
					.stream()
					.filter(x -> sendExpectedAndActual(x, expectedBuilder, actualBuilder))
					.count() > 0;
				break;
			default :
				return false;
		}
		return b.getAsBoolean();
	}

	private void sendExpectedAndActual(Throwable exception) {
		final StringBuilder expected = new StringBuilder();
		final StringBuilder actual = new StringBuilder();
		if (sendExpectedAndActual(exception, expected, actual)) {
			sendExpectedAndActual(expected, actual);
		}
	}

	private void message(String key) {
		message(key, "");
	}

	private void message(String key, CharSequence payload) {
		if (key.length() != 8)
			throw new IllegalArgumentException(key + " is not 8 characters");

		out.print(key);
		out.println(payload);
		out.flush();
		info("JUnitEclipseReporter: " + key + payload);
	}

	private AtomicInteger		counter	= new AtomicInteger(1);
	private Map<String, String>	idMap	= new HashMap<>();

	private String getTestId(String junitId) {
		String id = idMap.get(junitId);
		if (id == null) {
			id = Integer.toString(counter.getAndIncrement());
			idMap.put(junitId, id);
		}
		return id;
	}

	private String getTestId(TestIdentifier test) {
		return getTestId(test.getUniqueId());
	}

	private String getTestName(TestIdentifier test) {
		return test.getSource()
			.map(this::getTestName)
			.orElse(test.getDisplayName());
	}

	private String getTestName(TestSource testSource) {
		if (testSource instanceof ClassSource) {
			return ((ClassSource) testSource).getJavaClass()
				.getName();
		}
		if (testSource instanceof MethodSource) {
			MethodSource methodSource = (MethodSource) testSource;
			return MessageFormat.format("{0}({1})", methodSource.getMethodName(), methodSource.getClassName());
		}
		return null;
	}

	private String getTestParameterTypes(TestSource testSource) {
		if (testSource instanceof MethodSource) {
			MethodSource methodSource = (MethodSource) testSource;
			return methodSource.getMethodParameterTypes();
		}
		return "";
	}

	private void message(String key, TestIdentifier test) {
		message(key, test, "");
	}

	// namePrefix is used as a special case to signal ignored and aborted tests.
	private void message(String key, TestIdentifier test, String namePrefix) {
		final StringBuilder sb = new StringBuilder(100);
		sb.append(getTestId(test))
			.append(',');
		copyAndEscapeText(namePrefix + getTestName(test), sb);
		message(key, sb);
	}

	// This is mostly copied from
	// org.eclipse.jdt.internal.junit.runner.RemoteTestRunner except that
	// StringBuffer local has been replaced with StringBuilder parameter.
	public static void copyAndEscapeText(String s, StringBuilder sb) {
		if ((s.indexOf(',') < 0) && (s.indexOf('\\') < 0) && (s.indexOf('\r') < 0) && (s.indexOf('\n') < 0)) {
			sb.append(s);
			return;
		}
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == ',') {
				sb.append("\\,"); //$NON-NLS-1$
			} else if (c == '\\') {
				sb.append("\\\\"); //$NON-NLS-1$
			} else if (c == '\r') {
				if (i + 1 < s.length() && s.charAt(i + 1) == '\n') {
					i++;
				}
				sb.append(' ');
			} else if (c == '\n') {
				sb.append(' ');
			} else {
				sb.append(c);
			}
		}
	}

	private void visitEntry(TestIdentifier test) {
		visitEntry(test, false);
	}

	private void visitEntry(TestIdentifier test, boolean isDynamic) {
		StringBuilder treeEntry = new StringBuilder();
		treeEntry.append(getTestId(test))
			.append(',');
		copyAndEscapeText(getTestName(test), treeEntry);
		if (test.isTest()) {
			treeEntry.append(",false,1,")
				.append(isDynamic)
				.append(',')
				.append(test.getParentId()
					.map(this::getTestId)
					.orElse("-1"))
				.append(',');
			copyAndEscapeText(test.getDisplayName(), treeEntry);
			treeEntry.append(',');
			copyAndEscapeText(test.getSource()
				.map(this::getTestParameterTypes)
				.orElse(""), treeEntry);
			treeEntry.append(',');
			copyAndEscapeText(test.getUniqueId(), treeEntry);
			message("%TSTTREE", treeEntry);
		} else {
			final Set<TestIdentifier> children = testPlan.getChildren(test);
			treeEntry.append(",true,")
				.append(children.size())
				.append(',')
				.append(isDynamic)
				.append(',')
				.append(test.getParentId()
					.map(this::getTestId)
					.orElse("-1"))
				.append(',');
			copyAndEscapeText(test.getDisplayName(), treeEntry);
			treeEntry.append(',');
			copyAndEscapeText(test.getSource()
				.map(this::getTestParameterTypes)
				.orElse(""), treeEntry); //$NON-NLS-1$
			treeEntry.append(',');
			copyAndEscapeText(test.getUniqueId(), treeEntry);
			message("%TSTTREE", treeEntry);
			for (TestIdentifier child : children) {
				visitEntry(child, isDynamic);
			}
		}
	}

	@Override
	public void close() {
		info(() -> idMap.entrySet()
			.stream()
			.map(x -> x.getKey() + " => " + x.getValue())
			.collect(Collectors.joining(",\n")));
		try {
			in.close();
		} catch (Exception ioe) {
			// ignore
		}
		try {
			out.close();
		} catch (Exception ioe) {
			// ignore
		}
	}

	private void info(Supplier<CharSequence> message) {
		if (verbose) {
			info(message.get());
		}
	}

	private void info(CharSequence message) {
		if (verbose) {
			System.err.println(message);
		}
	}
}
