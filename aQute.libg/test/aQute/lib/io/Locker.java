package aQute.lib.io;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

public class Locker {

	public static final int		LOCK_SHARED			= 1;
	public static final int		TRYLOCK_SHARED		= 2;
	public static final int		LOCK_EXCLUSIVE		= 3;
	public static final int		TRYLOCK_EXCLUSIVE	= 4;
	public static final int		UNLOCK				= 5;
	public static final int		UNLOCK_ALL			= 6;
	public static final int		EXIT				= 7;

	static Map<Path, FileChannel>	locks				= new HashMap<>();

	public static void main(String[] args) {
		try {
			DataInputStream dis = new DataInputStream(System.in);
			ObjectOutputStream oos = new ObjectOutputStream(System.out);
			try {
				WHILE: while (true) {
					final byte command = dis.readByte();
					Path path = null;
					switch (command) {
						case LOCK_EXCLUSIVE :
						case LOCK_SHARED :
						case TRYLOCK_SHARED :
						case TRYLOCK_EXCLUSIVE :
						case UNLOCK :
							path = Paths.get(dis.readUTF());
							break;
						case UNLOCK_ALL :
							synchronized (locks) {
								locks.values()
									.forEach(IO::close);
								locks.clear();
							}
						case EXIT :
							break WHILE;
						default :
							oos.writeObject(new IllegalStateException("Unknown command: " + command));
							System.exit(-1);
					}
					if (command == UNLOCK) {
						FileChannel fch = locks.remove(path);
						IO.close(fch);
						oos.writeObject(null);
						oos.flush();
						continue;
					}
					FileChannel fc = null;
					FileLock lock = null;
					switch (command) {
						case LOCK_EXCLUSIVE :
							fc = FileChannel.open(path, StandardOpenOption.WRITE);
							lock = fc.lock(0, Long.MAX_VALUE, false);
							break;
						case TRYLOCK_EXCLUSIVE :
							fc = FileChannel.open(path, StandardOpenOption.WRITE);
							lock = fc.tryLock(0, Long.MAX_VALUE, false);
							break;
						case LOCK_SHARED :
							fc = FileChannel.open(path, StandardOpenOption.READ);
							lock = fc.lock(0, Long.MAX_VALUE, true);
							break;
						case TRYLOCK_SHARED :
							fc = FileChannel.open(path, StandardOpenOption.READ);
							lock = fc.tryLock(0, Long.MAX_VALUE, true);
							break;
					}
					if (lock != null) {
						locks.put(path, fc);
					} else {
						IO.close(fc);
					}
					switch (command) {
						case LOCK_EXCLUSIVE :
						case LOCK_SHARED :
							oos.writeObject(null);
							break;
						case TRYLOCK_EXCLUSIVE :
						case TRYLOCK_SHARED :
							oos.writeObject(Boolean.valueOf(lock != null));
							break;
					}
					oos.flush();
				}
			} catch (Throwable e) {
				oos.writeObject(e);
				oos.flush();
			}
		} catch (IOException e) {}
	}

}