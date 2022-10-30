package net.pawet.pawgen.component.system;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Slf4j
public class ProcessingExecutorService implements Executor, AutoCloseable {

	private final ExecutorService executor = new ThreadPoolExecutor(1, Runtime.getRuntime().availableProcessors() * 2, 100, MILLISECONDS, new LinkedBlockingQueue<>());
	private final Phaser phaser = new Phaser(1);

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


	@Override
	public void close() {
		executor.shutdown();
	}

}
