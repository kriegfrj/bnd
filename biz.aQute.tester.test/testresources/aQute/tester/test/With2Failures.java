package aQute.tester.test;

import org.junit.Test;

import aQute.tester.test.AbstractActivatorTest.CustomAssertionError;

public class With2Failures {
	@Test
	public void test1() {
		throw new AssertionError();
	}

	@Test
	public void test2() {
	}

	@Test
	public void test3() {
		throw new CustomAssertionError();
	}
}