package aQute.junit.platform.test;

import org.junit.jupiter.api.DisplayName;

@DisplayName("JUnit 5 Display test")
public class JUnit5DisplayNameTest {
	@DisplayName("Test 1")
	@org.junit.jupiter.api.Test
	public void test1() {
	}

	@DisplayName("Prüfung 2")
	@org.junit.jupiter.api.Test
	public void testWithNonASCII() {
	}

	@DisplayName("Δοκιμή 3")
	@org.junit.jupiter.api.Test
	public void testWithNonLatin() {
	}
}