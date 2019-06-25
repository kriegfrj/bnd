package aQute.tester.test;

import org.junit.Test;

public class With1Error1Failure {
	@Test
	public void test1() {
		throw new RuntimeException();
	}

	@Test
	public void test2() {
	}

	@Test
	public void test3() {
		throw new AssertionError();
	}
}