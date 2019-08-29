package aQute.lib.io;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import aQute.lib.exceptions.Exceptions;

public class LockerClient implements AutoCloseable {
	Process				process;
	DataOutputStream	outStr;
	ObjectInputStream	inStr;

	public LockerClient() throws Exception {
		String separator = System.getProperty("file.separator");
		String classpath = System.getProperty("java.class.path");
		String classPath = System.getProperty("java.home") + separator + "bin" + separator + "java";
		ProcessBuilder processBuilder = new ProcessBuilder(classPath, "-cp", classpath, Locker.class.getName());
		process = processBuilder.start();
		try {
			outStr = new DataOutputStream(process.getOutputStream());
			inStr = new ObjectInputStream(process.getInputStream());
		} catch (Exception e) {
			IO.close(outStr);
			IO.close(inStr);
			process.destroyForcibly();
			throw e;
		}
	}

	public void lockExclusive(Path p) {
		lockExclusive(p.toString());
	}

	public void lockExclusive(File f) {
		lockExclusive(f.toPath());
	}

	public void lockExclusive(String s) {
		try {
			outStr.writeByte(Locker.LOCK_EXCLUSIVE);
			outStr.writeUTF(s);
			outStr.flush();
			waitForNullWithTimeout("lockExclusive");
		} catch (Exception e) {
			throw Exceptions.duck(e);
		}
	}

	public boolean tryLockExclusive(Path p) {
		return tryLockExclusive(p.toString());
	}

	public boolean tryLockExclusive(File f) {
		return tryLockExclusive(f.toPath());
	}
	public boolean tryLockExclusive(String s) {
		try {
			outStr.writeByte(Locker.TRYLOCK_EXCLUSIVE);
			outStr.writeUTF(s);
			outStr.flush();
			Boolean retval = waitWithTimeout("tryLockExclusive");
			return retval.booleanValue();
		} catch (Exception e) {
			throw Exceptions.duck(e);
		}
	}

	public void lockShared(Path p) {
		lockShared(p.toString());
	}

	public void lockShared(File f) {
		lockShared(f.toPath());
	}

	public void lockShared(String s) {
		try {
			outStr.writeByte(Locker.LOCK_SHARED);
			outStr.writeUTF(s);
			outStr.flush();
			waitForNullWithTimeout("lockShared");
		} catch (Exception e) {
			throw Exceptions.duck(e);
		}
	}

	public boolean tryLockShared(Path p) {
		return tryLockShared(p.toString());
	}

	public boolean tryLockShared(File f) {
		return tryLockShared(f.toPath());
	}

	public boolean tryLockShared(String s) {
		try {
			outStr.writeByte(Locker.TRYLOCK_SHARED);
			outStr.writeUTF(s);
			outStr.flush();
			Boolean retval = waitWithTimeout("tryLockShared");
			return retval.booleanValue();
		} catch (Exception e) {
			throw Exceptions.duck(e);
		}
	}

	public void unlock(Path p) {
		unlock(p.toString());
	}

	public void unlock(File f) {
		unlock(f.toPath());
	}

	public void unlock(String s) {
		try {
			outStr.writeByte(Locker.UNLOCK);
			outStr.writeUTF(s);
			outStr.flush();
			waitForNullWithTimeout("unlock");
		} catch (Exception e) {
			throw Exceptions.duck(e);
		}
	}

	public void unlockAll() {
		try {
			outStr.writeByte(Locker.UNLOCK_ALL);
			outStr.flush();
			waitForNullWithTimeout("unlockAll");
		} catch (Exception e) {
			throw Exceptions.duck(e);
		}
	}

	private <T> T waitWithTimeout(String msg) {
		AtomicReference<T> retval = new AtomicReference<>();
		AtomicReference<Exception> ex = new AtomicReference<>();
		long start = System.currentTimeMillis();
		@SuppressWarnings("unchecked")
		Thread bg = new Thread(() -> {
			try {
				retval.set((T) inStr.readObject());
			} catch (IOException | ClassNotFoundException e) {
				ex.set(e);
			}
		});
		bg.start();
		try {
			bg.join(5000);
		} catch (InterruptedException e) {
			throw Exceptions.duck(e);
		}
		if (ex.get() != null) {
			throw new IllegalStateException("Exception while reading result: " + ex.get(), ex.get());
		}
		if (retval.get() instanceof Throwable) {
			Throwable remote = (Throwable) retval.get();
			throw new IllegalStateException("Child process threw an exception: " + remote, remote);
		}
		return retval.get();
	}

	private void waitForNullWithTimeout(String msg) throws InterruptedException, IOException {
		Object retval = waitWithTimeout(msg);
		if (retval != null) {
			throw new IllegalStateException("Remote process returned an object (" + retval + ");\n expecting null");
		}
	}

	@Override
	public void close() {
		try {
			outStr.writeByte(Locker.EXIT);
			outStr.flush();
			waitForNullWithTimeout("exit");
			if (!process.waitFor(5000, TimeUnit.MILLISECONDS)) {
				process.destroyForcibly();
			}
		} catch (Exception e) {
			process.destroyForcibly();
		}
	}
}