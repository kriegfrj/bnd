package aQute.tester.utils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.osgi.framework.Bundle;
import org.osgi.framework.namespace.ExecutionEnvironmentNamespace;

import aQute.launchpad.BundleBuilder;
import aQute.launchpad.BundleSpecBuilder;
import aQute.launchpad.Launchpad;
import aQute.lib.exceptions.Exceptions;
import aQute.lib.io.IO;

/**
 * Utility class for compiling sources from the testresources folder and
 * building test bundles.
 * <p>
 * Testing the tester requires test classes to feed it. Unfortunately, if these
 * are present in regular test source, they are also found (and executed) by the
 * build tools. To get around this, this utility class allows you to store the
 * test sources in the testresources folder. At startup, it will compile them
 * all into generated/bin_testresources. The rest of the methods facilitate
 * copying the classes into synthetic bundles using Launchpad.
 * 
 * @author Fr Jeremy Krieg (fr.jkrieg@greekwelfaresa.org.au)
 */
public class TestSourceBundler implements Closeable {

	JavaCompiler				compiler;
	StandardJavaFileManager		fm;
	File						outDir;
	final Set<TestClassName>	testClasses;

	public TestSourceBundler(TestClassName... classNames) throws IOException {
		this(Arrays.asList("testresources"), classNames);
	}

	public TestSourceBundler(List<String> srcList, TestClassName... classNames) throws IOException {
		testClasses = new HashSet<>();
		Stream.of(classNames)
			.forEach(testClasses::add);
		setUpCompiler(srcList);
	}

	public TestSourceBundler(Set<TestClassName> testClasses) throws IOException {
		this.testClasses = testClasses;
		setUpCompiler(Arrays.asList("testresources"));
	}

	public void reset() {
		testClasses.forEach(TestClassName::reset);
	}

	void setUpCompiler(List<String> srcList) throws IOException {
		compiler = ToolProvider.getSystemJavaCompiler();
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
		fm = compiler.getStandardFileManager(diagnostics, null, null);
		outDir = Paths.get("generated")
			.resolve("bin_testresources")
			.toFile();
		if (!outDir.exists()) {
			outDir.mkdirs();
		}
		Stream<File> srcFiles = srcList.stream()
			.map(IO::getFile);
		fm.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(outDir));
		fm.setLocation(StandardLocation.SOURCE_PATH, srcFiles::iterator);

		Stream<JavaFileObject> compilationUnits = testClasses.stream()
			.map(f -> {
				JavaFileObject fo;
				try {
					fo = (JavaFileObject) fm.getFileForInput(StandardLocation.SOURCE_PATH, f.getPkg(),
						f.getClassName() + ".java");
				} catch (IOException e) {
					throw Exceptions.duck(e);
				}
				if (fo == null) {
					throw new RuntimeException("Couldn't find source for test: " + f);
				}
				return fo;
			});

		CompilationTask task = compiler.getTask(null, fm, diagnostics, null, null, compilationUnits::iterator);

		if (!task.call()) {
			StringBuilder message = new StringBuilder("Compiler error in source: " + diagnostics.getDiagnostics()
				.size());
			for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
				message.append("\n");
				message.append(diagnostic.getCode())
					.append("\n");
				message.append(diagnostic.getKind())
					.append("\n");
				message.append(diagnostic.getPosition())
					.append("\n");
				message.append(diagnostic.getStartPosition())
					.append("\n");
				message.append(diagnostic.getEndPosition())
					.append("\n");
				message.append(diagnostic.getSource())
					.append("\n");
				message.append(diagnostic.getMessage(null))
					.append("\n");
			}
			throw new IllegalStateException(message.toString());
		}
	}

	@Override
	public void close() {
		outDir.delete();
	}

	public Bundle startTestBundle(Launchpad lp, TestClassName... testClasses) {
		return buildTestBundle(lp, testClasses).start();
	}

	public Bundle installTestBundle(Launchpad lp, TestClassName... testClasses) throws Exception {
		return buildTestBundle(lp, testClasses).install();
	}

	public BundleSpecBuilder addTestClass(BundleSpecBuilder bb, TestClassName testClass) {
		try {
			FileObject classFile = fm.getFileForInput(StandardLocation.CLASS_OUTPUT, testClass.getPkg(),
				testClass.getClassName() + ".class");
			String className = testClass.getPkg()
				.replace('.', '/') + '/'
				+ testClass.getClassName()
					.replace('.', '$')
				+ ".class";
			bb.addResource(className, classFile.toUri()
				.toURL());
		} catch (Exception e) {
			throw Exceptions.duck(e);
		}
		return bb;
	}

	public BundleSpecBuilder buildTestBundle(Launchpad lp, TestClassName... testClasses) {
		BundleBuilder underlying = lp.bundle();
		BundleSpecBuilder bb = new BundleSpecBuilder() {
			@Override
			public BundleBuilder x() {
				return underlying;
			}

			@Override
			public Bundle install() throws Exception {
				Bundle retval = BundleSpecBuilder.super.install();
				Stream.of(testClasses)
					.forEach(x -> x.setBundle(retval));
				return retval;
			}
		};
		final StringBuilder sb = new StringBuilder(128);
		boolean first = true;
		for (TestClassName testClass : testClasses) {
			if (!this.testClasses.contains(testClass)) {
				throw new IllegalStateException(
					"Test class: " + testClass + " was not loaded when TestSourceBundler was constructed");
			}
			if (first) {
				first = false;
			} else {
				sb.append(',');
			}
			sb.append(testClass);
			addTestClass(bb, testClass);
		}
		bb.header("Test-Cases", sb.toString())
			.requireCapability(ExecutionEnvironmentNamespace.EXECUTION_ENVIRONMENT_NAMESPACE)
			.filter("(&(osgi.ee=JavaSE)(version=1.8))");
		return bb;
	}
}
