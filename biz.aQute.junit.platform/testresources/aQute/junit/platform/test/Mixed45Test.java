package aQute.junit.platform.test;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class Mixed45Test {
	public static Set<String> methods = new HashSet<>();

	@Test
	public void junit4Test() {
		methods.add("junit4Test");
	}

	@org.junit.jupiter.api.Test
	public void junit5Test() {
		methods.add("junit5Test");
	}
}