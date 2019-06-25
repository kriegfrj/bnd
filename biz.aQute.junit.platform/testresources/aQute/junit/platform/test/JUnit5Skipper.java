package aQute.junit.platform.test;

import org.junit.jupiter.api.Disabled;

public class JUnit5Skipper {

	@org.junit.jupiter.api.Test
	@Disabled("with custom message")
	public void disabledTest() {
	}
}