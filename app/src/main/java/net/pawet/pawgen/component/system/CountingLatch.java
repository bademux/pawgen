package net.pawet.pawgen.component.system;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

public class CountingLatch {

	private final Sync sync;

	public CountingLatch(int count) {
		this.sync = new Sync(count);
	}

	public void await() throws InterruptedException {
		sync.acquireSharedInterruptibly(1);
	}

	public boolean await(Duration duration) throws InterruptedException {
		return sync.tryAcquireSharedNanos(1, duration.toNanos());
	}

	public void decrement() {
		sync.releaseShared(-1);
	}

	public void increment() {
		sync.releaseShared(1);
	}

	public long getCount() {
		return sync.getCount();
	}

	private static final class Sync extends AbstractQueuedSynchronizer {

		private Sync(final int count) {
			setState(count);
		}

		@Override
		public boolean tryReleaseShared(final int releases) {
			for (;;) {
				int count = getState();
				int next = count + releases;
				if (next < 0) {
					return false;
				}
				if (compareAndSetState(count, next)) {
					return next == 0;
				}
			}
		}

		@Override
		public int tryAcquireShared(final int acquires) {
			return getState() == 0 ? 1 : -1;
		}

		int getCount() {
			return getState();
		}
	}
}
