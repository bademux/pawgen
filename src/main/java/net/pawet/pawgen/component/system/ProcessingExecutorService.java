package net.pawet.pawgen.component.system;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ProcessingExecutorService implements Executor, AutoCloseable {

	private final ThreadPoolExecutor executor;

	public ProcessingExecutorService(int availableProcessors) {
		int maximumPoolSize = availableProcessors > 0 ? availableProcessors : 1;
		executor = new ThreadPoolExecutor(1, maximumPoolSize, 100, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
		executor.prestartCoreThread();
	}

	@SneakyThrows
	public void waitAllExecuted() {
		//TODO: calculate not completed in two different ways to be sure that everything is done
		while (
			executor.getTaskCount() - executor.getCompletedTaskCount() != 0 ||
				executor.getQueue().size() + executor.getActiveCount() != 0
		) ;
	}

	public void execute(@NonNull Runnable command) {
		executor.execute(command);
	}

	@Override
	public void close() {
		executor.shutdown();
	}
}
