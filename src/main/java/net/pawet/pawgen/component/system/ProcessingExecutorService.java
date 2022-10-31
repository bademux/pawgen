package net.pawet.pawgen.component.system;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;

@Slf4j
public class ProcessingExecutorService implements Executor, AutoCloseable {

	@Delegate(types = AutoCloseable.class)
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
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

}
