package aQute.junit.platform;

import static aQute.junit.constants.TesterConstants.TESTER_CONTINUOUS;
import static aQute.junit.constants.TesterConstants.TESTER_CONTROLPORT;
import static aQute.junit.constants.TesterConstants.TESTER_DIR;
import static aQute.junit.constants.TesterConstants.TESTER_NAMES;
import static aQute.junit.constants.TesterConstants.TESTER_PORT;
import static aQute.junit.constants.TesterConstants.TESTER_SEPARATETHREAD;
import static aQute.junit.constants.TesterConstants.TESTER_TRACE;
import static aQute.junit.constants.TesterConstants.TESTER_UNRESOLVED;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.LoggingListener;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.Constants;
import org.osgi.framework.SynchronousBundleListener;

import aQute.junit.bundle.engine.BundleEngine;
import aQute.junit.bundle.engine.discovery.BundleSelector;
import aQute.junit.platform.utils.BundleUtils;

public class Activator implements BundleActivator, Runnable {
	String								unresolved;
	Launcher							launcher;
	BundleContext						context;
	volatile boolean					active;
	boolean								continuous	= false;
	boolean								trace		= false;
	PrintStream							out			= System.err;
	JUnitEclipseListener				jUnitEclipseReport;
	volatile Thread						thread;
	private File						reportDir;
	private LoggingListener				basicReport;
	private SummaryGeneratingListener	summary;
	private TestExecutionListener[]		listeners;

	public Activator() {}

	@Override
	public void start(BundleContext context) throws Exception {
		this.context = context;
		active = true;

		if (!Boolean.valueOf(context.getProperty(TESTER_SEPARATETHREAD))
			&& Boolean.valueOf(context.getProperty("launch.services"))) { // can't
																			// register
																			// services
																			// on
																			// mini
																			// framework
			Hashtable<String, String> ht = new Hashtable<>();
			ht.put("main.thread", "true");
			ht.put(Constants.SERVICE_DESCRIPTION, "JUnit tester");
			context.registerService(Runnable.class.getName(), this, ht);
		} else {
			thread = new Thread(this, "bnd Runtime Test Bundle");
			thread.start();
		}
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		active = false;
		if (jUnitEclipseReport != null)
			jUnitEclipseReport.close();

		if (thread != null) {
			thread.interrupt();
			thread.join(10000);
		}
	}

	public boolean active() {
		return active;
	}

	@Override
	public void run() {

		continuous = Boolean.valueOf(context.getProperty(TESTER_CONTINUOUS));
		trace = context.getProperty(TESTER_TRACE) != null;

		if (thread == null)
			trace("running in main thread");

		// We can be started on our own thread or from the main code
		thread = Thread.currentThread();

		// Make sure that this is loaded
		ClassLoader l = thread.getContextClassLoader();
		try {
			thread.setContextClassLoader(BundleEngine.class.getClassLoader());
			launcher = LauncherFactory.create();
		} catch (Throwable e) {
			error("Couldn't load the BundleEngine: %s", e);
			System.exit(254);
		} finally {
			thread.setContextClassLoader(l);
		}

		List<TestExecutionListener> listenerList = new ArrayList<>();

		String testcases = context.getProperty(TESTER_NAMES);
		trace("test cases %s", testcases);
		int port = -1;
		boolean rerunIDE = false;
		if (context.getProperty(TESTER_CONTROLPORT) != null) {
			port = Integer.parseInt(context.getProperty(TESTER_CONTROLPORT));
			rerunIDE = true;
		} else if (context.getProperty(TESTER_PORT) != null) {
			port = Integer.parseInt(context.getProperty(TESTER_PORT));
		}

		if (port > 0) {
			try {
				trace("using control port %s, rerun IDE?: %s", port, rerunIDE);
				jUnitEclipseReport = new JUnitEclipseListener(port, rerunIDE);
				listenerList.add(jUnitEclipseReport);
			} catch (Exception e) {
				System.err.println(
					"Cannot create link Eclipse JUnit control on port " + port + " (rerunIDE: " + rerunIDE + ')');
				System.exit(254);
			}
		}

		String testerDir = context.getProperty(TESTER_DIR);
		if (testerDir == null)
			testerDir = "testdir";

		reportDir = new File(testerDir);

		//
		// Jenkins does not detect test failures unless reported
		// by JUnit XML output. If we have an unresolved failure
		// we timeout. The following will test if there are any
		// unresolveds and report this as a JUnit failure. It can
		// be disabled with -testunresolved=false
		//
		unresolved = context.getProperty(TESTER_UNRESOLVED);

		trace("run unresolved %s", unresolved);

		if (!reportDir.exists() && !reportDir.mkdirs()) {
			System.err.printf("Could not create directory %s%n", reportDir);
		} else {
			trace("using %s, path: %s", reportDir, reportDir.toPath());
			try {
				listenerList.add(new JUnitXmlListener(reportDir.toPath(), new PrintWriter(System.err)));
			} catch (Exception e) {
				error("Error trying to create xml reporter: %s", e);
			}
		}

		basicReport = LoggingListener.forBiConsumer((t, msg) -> {
			if (t == null) {
				trace(msg.get());
			} else {
				trace(msg.get(), t);
			}
		});
		summary = new SummaryGeneratingListener();
		listenerList.add(basicReport);
		listenerList.add(summary);
		listeners = listenerList.stream()
			.toArray(TestExecutionListener[]::new);

		if (testcases == null) {
			trace("automatic testing of all bundles with " + aQute.bnd.osgi.Constants.TESTCASES + " header");
			try {
				automatic();
			} catch (IOException e) {
				// ignore
			}
		} else {
			trace("receivednames of classes to test %s", testcases);
			try {
				baseSelectors = Stream.of(testcases.split("\\s*,\\s*"))
					.map(x -> {
						if (x.contains(":")) {
							return selectMethod(x.replace(':', '#'));
						} else {
							return selectClass(x);
						}
					})
					.collect(Collectors.toList());
				automatic();
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(254);
			}
		}
	}

	void automatic() throws IOException {
		final List<LauncherDiscoveryRequest> queue = new Vector<>();

		trace("adding Bundle Listener for getting test bundle events");
		context.addBundleListener(new SynchronousBundleListener() {
			@Override
			public void bundleChanged(BundleEvent event) {
				switch (event.getType()) {
					case BundleEvent.STARTED :
					case BundleEvent.RESOLVED :
						checkBundle(queue, event.getBundle());
						break;
				}
			}
		});

		long result = 0;
		LauncherDiscoveryRequest request = buildRequest();
		TestPlan plan = launcher.discover(request);
		if (plan.containsTests()) {
			result = test(request);
			if (!continuous) {
				System.exit((int) result);
			}
		}

		trace("starting queue");
		outer: while (active) {
			LauncherDiscoveryRequest testRequest;
			synchronized (queue) {
				while (queue.isEmpty() && active) {
					try {
						queue.wait();
					} catch (InterruptedException e) {
						trace("tests bundle queue interrupted");
						// thread.interrupt();
						break outer;
					}
				}
			}
			try {
				testRequest = queue.remove(0);
				trace("test will run");
				result += test(testRequest);
				trace("test ran");
				if (queue.isEmpty() && !continuous) {
					trace("queue " + queue);
					System.exit((int) result);
				}
			} catch (Exception e) {
				error("Not sure what happened anymore %s", e);
				System.exit(254);
			}
		}
	}

	List<DiscoverySelector> baseSelectors = Collections.emptyList();

	LauncherDiscoveryRequest buildRequest() {
		return LauncherDiscoveryRequestBuilder.request()
			.configurationParameter(BundleEngine.CHECK_UNRESOLVED, unresolved)
			.selectors(baseSelectors)
			.build();
	}

	LauncherDiscoveryRequest buildRequest(Bundle bundle) {
		List<DiscoverySelector> selectors = new ArrayList<>(baseSelectors.size() + 1);
		selectors.add(BundleSelector.selectBundle(bundle));
		return LauncherDiscoveryRequestBuilder.request()
			.configurationParameter(BundleEngine.CHECK_UNRESOLVED, unresolved)
			.selectors(selectors)
			.build();
	}

	void checkBundle(List<LauncherDiscoveryRequest> queue, Bundle bundle) {
		Bundle host = BundleUtils.getHost(bundle)
			.orElse(bundle);
		if (host.getState() == Bundle.ACTIVE || host.getState() == Bundle.STARTING) {
			String testcases = bundle.getHeaders()
				.get(aQute.bnd.osgi.Constants.TESTCASES);
			if (testcases != null) {
				trace("found active bundle with test cases %s : %s", bundle, testcases);
				synchronized (queue) {
					queue.add(buildRequest(bundle));
					queue.notifyAll();
				}
			}
		}
	}

	/**
	 * The main test routine.
	 *
	 * @param bundle The bundle under test or null
	 * @param testnames The names to test
	 * @return # of errors
	 */
	long test(LauncherDiscoveryRequest testRequest) {
		trace("testing request %s", testRequest);
		try {
			try {
				launcher.execute(testRequest, listeners);
			} catch (Throwable t) {
				trace("%s", t);
			} finally {
				summary.getSummary()
					.printTo(new PrintWriter(out));
			}

			return summary.getSummary()
				.getTestsFailedCount();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return -1;
	}

	boolean isTrace() {
		return trace;
	}

	public void trace(String msg, Object... objects) {
		if (isTrace()) {
			message("# ", msg, objects);
		}
	}

	void message(String prefix, String string, Object... objects) {
		Throwable e = null;

		StringBuilder sb = new StringBuilder();
		int n = 0;
		sb.append(prefix);
		for (int i = 0; i < string.length(); i++) {
			char c = string.charAt(i);
			if (c == '%') {
				c = string.charAt(++i);
				switch (c) {
					case 's' :
						if (n < objects.length) {
							Object o = objects[n++];
							if (o instanceof Throwable) {
								Throwable t = e = (Throwable) o;
								for (Throwable cause; (t instanceof InvocationTargetException)
									&& ((cause = t.getCause()) != null);) {
									t = cause;
								}
								sb.append(t.getMessage());
							} else {
								sb.append(o);
							}
						} else
							sb.append("<no more arguments>");
						break;

					default :
						sb.append(c);
				}
			} else {
				sb.append(c);
			}
		}
		out.println(sb);
		if (e != null)
			e.printStackTrace(out);
	}

	public void error(String msg, Object... objects) {
		message("! ", msg, objects);
	}
}
