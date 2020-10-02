package aQute.tester.test.utils;

import static org.eclipse.jdt.internal.junit.model.ITestRunListener2.STATUS_ERROR;
import static org.eclipse.jdt.internal.junit.model.ITestRunListener2.STATUS_FAILURE;

public class TestRerun {
	public String	testId;
	public String	testClass;
	public String	testName;
	public int		status;
	public String	trace;
	public String	expected;
	public String	actual;

	public TestRerun(String testId, String testClass, String testName, int status, String trace, String expected,
		String actual) {
		this.testId = testId;
		this.testClass = testClass;
		this.testName = testName;
		this.status = status;
		this.trace = trace;
		this.expected = expected;
		this.actual = actual;
	}

	static public String statusToString(int statusCode) {
		switch (statusCode) {
			case STATUS_FAILURE :
				return "FAIL";
			case STATUS_ERROR :
				return "ERROR";
			default :
				return "???";
		}
	}

	@Override
	public String toString() {
		return "TestRerun [id=" + testId + ", class=" + testClass + ", name=" + testName + ", status="
			+ statusToString(status) + ", trace="
			+ trace + ", expected=" + expected + ", actual=" + actual + "]";
	}
}
