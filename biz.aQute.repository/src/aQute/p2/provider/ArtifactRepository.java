package aQute.p2.provider;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.osgi.framework.Version;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import aQute.lib.converter.Converter;
import aQute.lib.converter.TypeReference;
import aQute.lib.filter.Filter;
import aQute.libg.sed.Domain;
import aQute.libg.sed.ReplacerAdapter;
import aQute.p2.api.Artifact;

/**
 * @formatter:off
 * <pre>
 * <?xml version='1.0' encoding='UTF-8'?>
 * <?artifactRepository version='1.1.0'?>
 * <repository name='Bndtools' type='org.eclipse.equinox.p2.artifact.repository.simpleRepository' version='1'>
 *   <properties size='2'>
 *     <property name='p2.timestamp' value='1463781466748'/>
 *     <property name='p2.compressed' value='true'/>
 *   </properties>
 *   <mappings size='3'>
 *     <rule filter='(&amp; (classifier=osgi.bundle))' output='${repoUrl}/plugins/${id}_${version}.jar'/>
 *     <rule filter='(&amp; (classifier=binary))' output='${repoUrl}/binary/${id}_${version}'/>
 *     <rule filter='(&amp; (classifier=org.eclipse.update.feature))' output='${repoUrl}/features/${id}_${version}.jar'/>
 *   </mappings>
 *   <artifacts size='22'>
 *     <artifact classifier='osgi.bundle' id='org.bndtools.versioncontrol.ignores.plugin.git' version='3.3.0.201605202157'>
 *       <properties size='3'>
 *         <property name='artifact.size' value='9356'/>
 *         <property name='download.size' value='9356'/>
 *         <property name='download.md5' value='745f389a49189112a785848ad466097b'/>
 *       </properties>
 *     </artifact>
 *   </artifacts>
 * </pre>
 * @formatter:on
 */

class ArtifactRepository extends XML {

	static class Rule {
		final Filter	filter;
		String			output;

		Rule(String filter, String output) {
			this.filter = new Filter(filter);
			this.output = output;
		}

		public boolean matches(Map<String,String> map) throws Exception {
			return filter.matchMap(map);
		}

	}

	public static class XMLArtifact {
		public String	classifier;
		public String	id;
		public String	version;
		public String	format;
	}

	List<Rule>		rules;
	List<Artifact>	artifacts	= new ArrayList<>();
	private URI		base;

	ArtifactRepository(InputStream in, URI base) throws Exception {
		super(getDocument(in));
		this.base = base;
		parse();
	}

	private Rule createRule(Node ruleNode) {
		String filter = getAttribute(ruleNode, "filter");
		String output = getAttribute(ruleNode, "output");
		return new Rule(filter, output);
	}

	void parse() throws Exception {

		final Map<String,String> properties = getProperties("repository/properties/property");
		properties.put("repoUrl", base.resolve("").toString());
		final Domain parent = new Domain() {

			@Override
			public Map<String,String> getMap() {
				return properties;
			}

			@Override
			public Domain getParent() {
				return null;
			}

		};

		rules = getRules();

		NodeList artifactNodes = getNodes("repository/artifacts/artifact");
		for (int i = 0; i < artifactNodes.getLength(); i++) {
			final XMLArtifact xmlArtifact = getFromType(artifactNodes.item(i), XMLArtifact.class);
			final Map<String,String> map = Converter.cnv(new TypeReference<Map<String,String>>() {}, xmlArtifact);

			if ("osgi.bundle".equals(xmlArtifact.classifier)) {
				Domain domain = new Domain() {

					@Override
					public Map<String,String> getMap() {
						return map;
					}

					@Override
					public Domain getParent() {
						return parent;
					}

				};
				ReplacerAdapter ra = new ReplacerAdapter(domain);

				for (Rule r : rules) {
					if (r.matches(map)) {
						String s = ra.process(r.output);
						URI uri = new URI(s).normalize();

						Artifact artifact = new Artifact();
						artifact.uri = uri;
						artifact.id = xmlArtifact.id;
						artifact.version = new Version(xmlArtifact.version);
						artifacts.add(artifact);
						break;
					}
				}
			}
		}
	}

	/**
	 * * <artifact classifier='osgi.bundle'
	 * id='org.bndtools.versioncontrol.ignores.plugin.git'
	 * version='3.3.0.201605202157'>
	 * 
	 * @param item
	 * @return
	 * @throws Exception
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 */
	List<Rule> getRules() throws Exception {
		List<Rule> rules = new ArrayList<>();
		NodeList ruleNodes = getNodes("repository/mappings/rule");
		for (int i = 0; i < ruleNodes.getLength(); i++) {
			Node ruleNode = ruleNodes.item(i);
			Rule rule = createRule(ruleNode);
			rules.add(rule);
		}
		return rules;
	}

	public List<Artifact> getArtifacts() {
		return artifacts;
	}

}