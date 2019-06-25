package aQute.junit.platform.test;

import java.util.concurrent.atomic.AtomicReference;

public class JUnit5Test {
	public static AtomicReference<Thread> currentThread = new AtomicReference<>();

	@org.junit.jupiter.api.Test
	public void somethingElseAgain() {
		currentThread.set(Thread.currentThread());
	}
}