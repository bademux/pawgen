package net.pawet.pawgen.component.system;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
public class ProcessingExecutorService implements ExecutorService {

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
	private final Phaser phaser = new Phaser(1);

	@SneakyThrows
	public void waitAllExecuted() {
		phaser.arriveAndAwaitAdvance();
		log.debug("Processed {}/{} tasks", phaser.getRegisteredParties() - 1,  phaser.getArrivedParties());
	}

	@Override
	public void shutdown() {
		executor.shutdown();
	}

	@Override
	public List<Runnable> shutdownNow() {
		return executor.shutdownNow();
	}

	@Override
	public boolean isShutdown() {
		return executor.isShutdown();
	}

	@Override
	public boolean isTerminated() {
		return executor.isTerminated();
	}

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		return executor.awaitTermination(timeout, unit);
	}

	@Override
	public void execute(@NonNull Runnable command) {
		phaser.register();
		executor.execute(() -> {
			try {
				command.run();
			} finally {
				phaser.arrive();
			}
		});
	}

	@Override
	public <T> Future<T> submit(@NonNull Callable<T> task) {
		phaser.register();
		return executor.submit(() -> {
			try {
				return task.call();
			} finally {
				phaser.arrive();
			}
		});
	}

	@Override
	public <T> Future<T> submit(@NonNull Runnable task, T result) {
		phaser.register();
		return executor.submit(() -> {
			try {
				task.run();
			} finally {
				phaser.arrive();
			}
		}, result);
	}

	@Override
	public Future<?> submit(@NonNull Runnable task) {
		phaser.register();
		return executor.submit(() -> {
			try {
				task.run();
			} finally {
				phaser.arrive();
			}
		});
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
		throw new UnsupportedOperationException("unimplemented");
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
		throw new UnsupportedOperationException("unimplemented");
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks) {
		throw new UnsupportedOperationException("unimplemented");
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
		throw new UnsupportedOperationException("unimplemented");
	}

}
