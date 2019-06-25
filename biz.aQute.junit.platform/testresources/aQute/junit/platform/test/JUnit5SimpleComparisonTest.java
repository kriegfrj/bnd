package aQute.junit.platform.test;

import java.util.ArrayList;
import java.util.List;

import org.opentest4j.AssertionFailedError;
import org.opentest4j.MultipleFailuresError;

public class JUnit5SimpleComparisonTest {
	@org.junit.jupiter.api.Test
	public void somethingElseThatFailed() {
		throw new AssertionFailedError("Hi there", "expected", "actual");
	}

	@org.junit.jupiter.api.Test
	public void emptyComparisonFailure() {
		List<Throwable> l = new ArrayList<>();
		l.add(new RuntimeException());
		l.add(new IllegalArgumentException());

		throw new MultipleFailuresError("message", l);
	}
}