package aQute.junit.platform.test;

import org.junit.Test;

public class JUnit4AbortTest {
	@Test
	public void abortedTest() {
		org.junit.Assume.assumeTrue("Let's get outta here", false);
		throw new AssertionError();
	}

	@Test
	public void completedTest() {
	}
}