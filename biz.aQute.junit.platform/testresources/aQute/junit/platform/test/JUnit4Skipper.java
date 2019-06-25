package aQute.junit.platform.test;

import org.junit.Ignore;
import org.junit.Test;

public class JUnit4Skipper {
	@Test
	@Ignore("This is a test")
	public void disabledTest() {
	}

	@Test
	public void enabledTest() {
	}
}