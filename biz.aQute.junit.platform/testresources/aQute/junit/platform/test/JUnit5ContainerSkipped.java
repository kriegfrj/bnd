package aQute.junit.platform.test;

import org.junit.jupiter.api.Disabled;

@Disabled("with another message")
public class JUnit5ContainerSkipped {
	@org.junit.jupiter.api.Test
	public void disabledTest2() {
	}

	@org.junit.jupiter.api.Test
	public void disabledTest3() {
	}
}