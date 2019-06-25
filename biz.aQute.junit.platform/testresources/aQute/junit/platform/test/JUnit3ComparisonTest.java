package aQute.junit.platform.test;

import junit.framework.TestCase;

public class JUnit3ComparisonTest extends TestCase {
	public void testComparisonFailure() {
		throw new junit.framework.ComparisonFailure("message", "expected", "actual");
	}
}