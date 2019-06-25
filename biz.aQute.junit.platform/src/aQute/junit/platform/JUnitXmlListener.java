package aQute.junit.platform;

import static java.lang.invoke.MethodHandles.publicLookup;

import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.UniqueId.Segment;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.reporting.legacy.xml.LegacyXmlReportGeneratingListener;

public class JUnitXmlListener extends LegacyXmlReportGeneratingListener {

	final MethodHandle	writeXmlReportSafely;
	final MethodHandle	reportData;
	final MethodHandle	markFinished;
	final PrintWriter	out;

	public JUnitXmlListener(Path reportsDir, PrintWriter out) throws NoSuchMethodException, SecurityException,
		IllegalAccessException, NoSuchFieldException, ClassNotFoundException {
		super(reportsDir, out);
		this.out = out;

		Method m = LegacyXmlReportGeneratingListener.class.getDeclaredMethod("writeXmlReportSafely",
			TestIdentifier.class, String.class);

		m.setAccessible(true);
		writeXmlReportSafely = publicLookup().unreflect(m)
			.bindTo(this);

		Field f = LegacyXmlReportGeneratingListener.class.getDeclaredField("reportData");
		f.setAccessible(true);
		reportData = publicLookup().unreflectGetter(f)
			.bindTo(this);

		Class<?> xmlReportData = LegacyXmlReportGeneratingListener.class.getClassLoader()
			.loadClass(LegacyXmlReportGeneratingListener.class.getPackage()
				.getName() + ".XmlReportData");
		m = xmlReportData.getDeclaredMethod("markFinished", TestIdentifier.class, TestExecutionResult.class);
		m.setAccessible(true);
		markFinished = publicLookup().unreflect(m);
	}

	@Override
	public void executionFinished(TestIdentifier identifier, TestExecutionResult result) {
		try {
			markFinished.invoke(reportData.invoke(), identifier, result);
			List<Segment> segments = UniqueId.parse(identifier.getUniqueId())
				.getSegments();
			Segment last = segments.get(segments.size() - 1);
			if (last.getType()
				.equals("bundle")) {
				writeXmlReportSafely.invoke(identifier, last.getValue());
			}
		} catch (Throwable t) {
			out.println("JUnitXmlReport: " + t);
			t.printStackTrace(out);
		}
	}

}
