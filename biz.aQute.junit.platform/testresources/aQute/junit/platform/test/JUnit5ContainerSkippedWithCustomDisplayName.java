package aQute.junit.platform.test;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;

@DisplayName("Skipper Class")
@Disabled("with a third message")
public class JUnit5ContainerSkippedWithCustomDisplayName {
	@org.junit.jupiter.api.Test
	public void disabledTest2() {
	}

	@org.junit.jupiter.api.Test
	public void disabledTest3() {
	}
}