package net.pawet.pawgen.component.render;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.system.CountingLatch;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

@Slf4j
@RequiredArgsConstructor
public class TrackingExecutor implements Executor {

	private final CountingLatch countingLatch = new CountingLatch(0);
	private final ThreadFactory threadFactory;

	@SneakyThrows
	public void waitRendered(Duration duration) {
		countingLatch.await(duration);
		log.debug("Processed tasks");
	}

	@Override
	public void execute(@NonNull Runnable command) {
		countingLatch.increment();
		threadFactory.newThread(() -> {
			try {
				command.run();
			} finally {
				countingLatch.decrement();
			}
		}).start();
	}

}
