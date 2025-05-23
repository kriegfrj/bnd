package test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import aQute.bnd.header.Parameters;
import aQute.bnd.maven.PomParser;
import aQute.bnd.maven.support.CachedPom;
import aQute.bnd.maven.support.Maven;
import aQute.bnd.maven.support.MavenEntry;
import aQute.bnd.maven.support.MavenRemoteRepository;
import aQute.bnd.maven.support.Pom;
import aQute.bnd.maven.support.Pom.Dependency;
import aQute.bnd.maven.support.Pom.Scope;
import aQute.bnd.maven.support.ProjectPom;
import aQute.bnd.osgi.Builder;
import aQute.bnd.osgi.Constants;
import aQute.bnd.osgi.Jar;
import aQute.bnd.osgi.Processor;
import aQute.bnd.osgi.Resource;
import aQute.bnd.service.Strategy;
import aQute.lib.io.IO;
import aQute.lib.xml.XML;

@SuppressWarnings("resource")
public class MavenTest {
	Processor				processor	= new Processor();
	final static File		cwd			= new File("").getAbsoluteFile();
	static ExecutorService	executor	= Executors.newCachedThreadPool();
	Maven					maven		= new Maven(executor);

	/**
	 * A test against maven 2
	 *
	 * @throws Exception
	 * @throws URISyntaxException
	 */
	@Test
	public void testRemote() throws URISyntaxException, Exception {
		URI repo = new URI("https://repo.maven.apache.org/maven2");
		MavenEntry entry = maven.getEntry("org.springframework", "spring-aspects", "3.0.5.RELEASE");
		entry.remove();
		CachedPom pom = maven.getPom("org.springframework", "spring-aspects", "3.0.5.RELEASE", repo);
		Set<Pom> dependencies = pom.getDependencies(Scope.compile, repo);
		for (Pom dep : dependencies) {
			System.err.printf("%20s %-20s %10s%n", dep.getGroupId(), dep.getArtifactId(), dep.getVersion());
		}

	}

	/**
	 * Test parsing a project pom
	 *
	 * @throws Exception
	 */

	@Test
	public void testProjectPom() throws Exception {
		Maven maven = new Maven(null);
		ProjectPom pom = maven.createProjectModel(IO.getFile(cwd, "testresources/ws/maven1/testpom.xml"));
		assertEquals("artifact", pom.getArtifactId());
		assertEquals("group-parent", pom.getGroupId());
		assertEquals("1.0.0", pom.getVersion());
		assertEquals("Artifact", pom.getName());
		assertEquals("Parent Description\n\nDescription artifact", pom.getDescription());

		List<Dependency> dependencies = pom.getDependencies();
		boolean dep1 = false; // dep1
		boolean dep2 = false; // artifact (after macro)
		boolean dep3 = false; // junit
		boolean dep4 = false; // easymock

		for (Dependency dep : dependencies) {
			String artifactId = dep.getArtifactId();
			if ("dep1".equals(artifactId)) {
				assertFalse(dep1);
				dep1 = true;
				assertEquals("xyz", dep.getGroupId());
				assertEquals("1.0.1", dep.getVersion());
				assertEquals(Pom.Scope.valueOf("compile"), dep.getScope());

			} else if ("artifact".equals(artifactId)) {
				assertFalse(dep2);
				dep2 = true;
				assertEquals("group-parent", dep.getGroupId());
				assertEquals("1.0.2", dep.getVersion());
				assertEquals(Pom.Scope.valueOf("compile"), dep.getScope());
			} else if ("junit".equals(artifactId)) {
				assertFalse(dep3);
				dep3 = true;
				assertEquals("junit", dep.getGroupId());
				assertEquals("4.0", dep.getVersion());
				assertEquals(Pom.Scope.valueOf("test"), dep.getScope());
			} else if ("easymock".equals(artifactId)) {
				assertFalse(dep4);
				dep4 = true;
				assertEquals("org.easymock", dep.getGroupId());
				assertEquals("2.4", dep.getVersion());
				assertEquals(Pom.Scope.valueOf("compile"), dep.getScope());
			} else
				fail("'" + artifactId + "'");
		}
		assertTrue(dep1 && dep2 && dep3 && dep4);

		assertEquals("aa", pom.getProperty("a"));
		assertEquals("b from parent", pom.getProperty("b"));
		assertEquals("aab from parentartifact", pom.getProperty("c"));
	}

	/**
	 * Test the maven remote repository
	 */

	@Test
	public void testMavenRepo1() throws Exception {
		Maven maven = new Maven(null);
		MavenRemoteRepository mr = new MavenRemoteRepository();
		mr.setMaven(maven);

		MavenEntry me = maven.getEntry("org.apache.commons", "com.springsource.org.apache.commons.beanutils", "1.6.1");
		me.remove();

		me = maven.getEntry("org.apache.commons", "com.springsource.org.apache.commons.collections", "2.1.1");
		me.remove();

		me = maven.getEntry("org.apache.commons", "com.springsource.org.apache.commons.logging", "1.0.4");
		me.remove();

		mr.setRepositories(IO.getFile(new File("").getAbsoluteFile(), "testresources/ws/maven1/m2")
			.toURI());

		Map<String, String> map = new HashMap<>();
		map.put("scope", "compile");
		File file = mr.get("org.apache.commons+com.springsource.org.apache.commons.beanutils", "1.6.1", Strategy.LOWEST,
			map);

		assertNotNull(file);
		assertTrue(file.isFile());

		assertEquals(
			"org.apache.commons+com.springsource.org.apache.commons.beanutils;version=\"1.6.1\"\n"
				+ "org.apache.commons+com.springsource.org.apache.commons.collections;version=\"2.1.1\"\n"
				+ "org.apache.commons+com.springsource.org.apache.commons.logging;version=\"1.0.4\"\n",
			IO.collect(file));

		file = mr.get("org.apache.commons+com.springsource.org.apache.commons.beanutils", "1.6.1", Strategy.LOWEST,
			null);
		assertEquals("com.springsource.org.apache.commons.beanutils-1.6.1.jar", file.getName());
	}

	/**
	 * Test the pom parser which will turn the pom into a set of properties,
	 * which will make it actually readable according to some.
	 *
	 * @throws Exception
	 */

	@SuppressWarnings("restriction")
	@Test
	public void testPomParser() throws Exception {
		PomParser parser = new PomParser();
		Properties p = parser.getProperties(IO.getFile("testresources/ws/maven1/pom.xml"));
		p.store(System.err, "testing");
		assertEquals("Apache Felix Metatype Service", p.get("pom.name"));
		assertEquals("org.apache.felix", p.get("pom.groupId")); // is from
																// parent
		assertEquals("org.apache.felix.metatype", p.get("pom.artifactId"));
		assertEquals("bundle", p.get("pom.packaging"));

		Parameters map = parser.parseHeader(p.getProperty("pom.scope.test"));
		Map<String, String> junit = map.get("junit.junit");
		assertNotNull(junit);
		assertEquals("4.0", junit.get("version"));
		Map<String, String> easymock = map.get("org.easymock.easymock");
		assertNotNull(easymock);
		assertEquals("2.4", easymock.get("version"));
	}

	// @Test public void testDependencies() throws Exception {
	// MavenDependencyGraph graph;
	//
	// graph = new MavenDependencyGraph();
	// File home = new File( System.getProperty("user.home"));
	// File m2 = new File( home, ".m2");
	// File m2Repo = new File( m2, "repository");
	// if ( m2Repo.isDirectory())
	// graph.addRepository( m2Repo.toURI().toURL());
	//
	// graph.addRepository( new URL("https://repo.maven.apache.org/maven2/"));
	// graph.addRepository( new
	// URL("https://repository.springsource.com/maven/bundles/external"));
	// // graph.root.add(
	// IO.getFile("testresources/poms/pom-1.xml").toURI().toURL());
	//
	// }

	// @Test public void testMaven() throws Exception {
	// MavenRepository maven = new MavenRepository();
	// maven.setReporter(processor);
	// maven.setProperties(new HashMap<String, String>());
	// maven.setRoot(processor.getFile("testresources/maven-repo"));
	//
	// File files[] = maven.get("activation.activation", null);
	// assertNotNull(files);
	// assertEquals("activation-1.0.2.jar", files[0].getName());
	//
	// files = maven.get("biz.aQute.bndlib", null);
	// assertNotNull(files);
	// assertEquals(5, files.length);
	// assertEquals("bndlib-0.0.145.jar", files[0].getName());
	// assertEquals("bndlib-0.0.255.jar", files[4].getName());
	//
	// List<String> names = maven.list(null);
	// System.err.println(names);
	// assertEquals(13, names.size());
	// assertTrue(names.contains("biz.aQute.bndlib"));
	// assertTrue(names.contains("org.apache.felix.javax.servlet"));
	// assertTrue(names.contains("org.apache.felix.org.osgi.core"));
	//
	// List<Version> versions =
	// maven.versions("org.apache.felix.javax.servlet");
	// assertEquals(1, versions.size());
	// versions.contains(new Version("1.0.0"));
	//
	// versions = maven.versions("biz.aQute.bndlib");
	// assertEquals(5, versions.size());
	// versions.contains(new Version("0.0.148"));
	// versions.contains(new Version("0.0.255"));
	// }

	// @Test public void testMavenBsnMapping() throws Exception {
	// Processor processor = new Processor();
	// processor
	// .setProperty("-plugin",
	// "aQute.bnd.maven.MavenGroup; groupId=org.apache.felix,
	// aQute.bnd.maven.MavenRepository");
	// MavenRepository maven = new MavenRepository();
	// maven.setReporter(processor);
	// Map<String, String> map = new HashMap<String, String>();
	// map.put("root",
	// IO.getFile(cwd,"testresources/maven-repo").getAbsolutePath());
	// maven.setProperties(map);
	//
	// File files[] = maven.get("org.apache.felix.framework", null);
	// assertNotNull(files);
	// ;
	// assertEquals(1, files.length);
	// }

	@Test
	public void testPomResource() throws Exception {

		testPom("pom.xml", "true", "com.example.foo", "1.2.3.qualifier", "com.example", "foo", "1.2.3.qualifier",
			"url=http://github.com/bndtools,connection=scm:git:https://github.com/bndtools/bnd,developerConnection=scm:git:git@github.com/bndtools/bnd",
			"Peter.Kriens@aQute.biz;name=\"Peter Kriens\";organization=aQute;roles=\"programmer,gopher\"", null);
		testPom("pom.xml", "true", "com.example.foo", "1.2.3.qualifier", "com.example", "foo", "1.2.3.qualifier", null,
			null, null);
		testPom("pom.xml", "true", "uvw.xyz", "1.2.3", "uvw", "xyz", "1.2.3", null, null, null);
		testPom("META-INF/maven/abc.def.ghi/jkl/pom.xml", "groupid=abc.def.ghi,artifactid=jkl", "uvw.xyz", "1.2.3",
			"abc.def.ghi", "jkl", "1.2.3", null, null, "<<EXTERNAL>>");
		testPom("META-INF/maven/abc.def.ghi/uvw.xyz/pom.xml", "groupid=abc.def.ghi", "uvw.xyz", "1.2.3", "abc.def.ghi",
			"uvw.xyz", "1.2.3", null, null, "http://www.apache.org/licenses/LICENSE-2.0");
		testPom("META-INF/maven/abc.def.ghi/uvw.xyz/pom.xml", "groupid=abc.def.ghi,version=2.6.8", "uvw.xyz", "1.2.3",
			"abc.def.ghi", "uvw.xyz", "2.6.8", null, null,
			"http://www.apache.org/licenses/LICENSE-2.0;description=\"Apache License, Version 2.0\"");
		testPom("META-INF/maven/pom.xml", "groupid=abc.def.ghi,version=2.6.8,where=META-INF/maven/pom.xml", "uvw.xyz",
			"1.2.3", "abc.def.ghi", "uvw.xyz", "2.6.8", null, null,
			"Apache-2.0;description=\"Apache License, Version 2.0\";link=\"http://www.apache.org/licenses/LICENSE-2.0\"");
	}

	void testPom(String where, String pom, String bsn, String version, String groupId, String artifactId,
		String mversion, String scm, String developers, String license)
		throws IOException, SAXException, ParserConfigurationException, Exception {
		Builder b = new Builder();
		b.setProperty("-pom", pom);
		b.setBundleSymbolicName(bsn);
		b.setBundleVersion(version);
		b.setProperty("-resourceonly", "true");
		b.setProperty("-maven-dependencies",
			"group1:artifact1:1.0.0-SNAPSHOT;groupId=group1;artifactId=artifact1;version=1.0.0-SNAPSHOT,group2:artifact2:2.0.0;groupId=group2;artifactId=artifact2;version=2.0.0");
		b.setProperty("-maven-dependencies.fix",
			"group1:artifact1:1.0.0-SNAPSHOT;groupId=group1;artifactId=artifact1;version=1.0.0");
		if (developers != null)
			b.setProperty(Constants.BUNDLE_DEVELOPERS, developers);

		if (scm != null)
			b.setProperty(Constants.BUNDLE_SCM, scm);

		if (license != null)
			b.setProperty(Constants.BUNDLE_LICENSE, license);

		Jar jar = b.build();
		assertTrue(b.check());
		Resource r = jar.getResource(where);
		IO.copy(r.openInputStream(), System.out);
		Document d = XML.newDocumentBuilderFactory()
			.newDocumentBuilder()
			.parse(r.openInputStream());
		XPath xpath = XPathFactory.newInstance()
			.newXPath();
		assertEquals(groupId, xpath.evaluate("/project/groupId", d));
		assertEquals(artifactId, xpath.evaluate("/project/artifactId", d));
		assertEquals(mversion, xpath.evaluate("/project/version", d));

		assertEquals((developers == null) ? "0" : "1", xpath.evaluate("count(/project/developers)", d));
		assertEquals((scm == null) ? "0" : "1", xpath.evaluate("count(/project/scm)", d));
		assertEquals(((license == null) || license.trim()
			.equals("<<EXTERNAL>>")) ? "0" : "1", xpath.evaluate("count(/project/licenses)", d));

		assertEquals("2", xpath.evaluate("count(/project/dependencies/dependency)", d));
		assertEquals("group1", xpath.evaluate("/project/dependencies/dependency[1]/groupId", d));
		assertEquals("artifact1", xpath.evaluate("/project/dependencies/dependency[1]/artifactId", d));
		assertEquals("1.0.0", xpath.evaluate("/project/dependencies/dependency[1]/version", d));
		assertEquals("group2", xpath.evaluate("/project/dependencies/dependency[2]/groupId", d));
		assertEquals("artifact2", xpath.evaluate("/project/dependencies/dependency[2]/artifactId", d));
		assertEquals("2.0.0", xpath.evaluate("/project/dependencies/dependency[2]/version", d));
	}
}
