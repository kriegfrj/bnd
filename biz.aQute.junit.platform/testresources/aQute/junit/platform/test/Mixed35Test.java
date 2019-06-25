package aQute.junit.platform.test;

import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;

public class Mixed35Test extends TestCase {
	public static Set<String> methods = new HashSet<>();

	public void testJUnit3() {
		methods.add("testJUnit3");
	}

	@org.junit.jupiter.api.Test
	public void junit5Test() {
		methods.add("junit5Test");
	}
}