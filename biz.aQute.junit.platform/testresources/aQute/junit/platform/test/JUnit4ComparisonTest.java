package aQute.junit.platform.test;

import org.junit.ComparisonFailure;
import org.junit.Test;

public class JUnit4ComparisonTest {
	@Test
	public void comparisonFailure() {
		throw new ComparisonFailure("message", "expected", "actual");
	}
}