package net.pawet.pawgen.component.system;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

@Slf4j
public class ProcessingExecutorService implements Executor, AutoCloseable {

	private final ThreadPoolExecutor executor;
	private final Phaser phaser = new Phaser(1);

	public ProcessingExecutorService(int availableProcessors) {
		int maximumPoolSize = availableProcessors > 0 ? availableProcessors : 1;
		executor = new ThreadPoolExecutor(1, maximumPoolSize, 100, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
		executor.prestartCoreThread();
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

	@SneakyThrows
	public void waitAllExecuted() {
		phaser.arriveAndAwaitAdvance();
		log.debug("Processed {}/{} tasks", phaser.getRegisteredParties() - 1,  phaser.getArrivedParties());
	}

	/**
	 * TODO: remove when updated to JDK19 impl
	 */
	@Override
	public void close() {
		boolean terminated = executor.isTerminated();
		if (!terminated) {
			executor.shutdown();
			boolean interrupted = false;
			while (!terminated) {
				try {
					terminated = executor.awaitTermination(1L, TimeUnit.DAYS);
				} catch (InterruptedException e) {
					if (!interrupted) {
						executor.shutdownNow();
						interrupted = true;
					}
				}
			}
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

}
