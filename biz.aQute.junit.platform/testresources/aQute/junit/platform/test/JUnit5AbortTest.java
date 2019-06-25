package aQute.junit.platform.test;

public class JUnit5AbortTest {

	@org.junit.jupiter.api.Test
	public void abortedTest() {
		org.junit.jupiter.api.Assumptions.assumeFalse(true, "I just can't go on");
		throw new AssertionError();
	}
}