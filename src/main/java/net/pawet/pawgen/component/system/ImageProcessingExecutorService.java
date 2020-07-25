package net.pawet.pawgen.component.system;

import lombok.experimental.Delegate;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ImageProcessingExecutorService implements ExecutorService {

	@Delegate(types = ExecutorService.class)
	private final ExecutorService executorService;
	private final LinkedBlockingQueue<Runnable> queue;

	public ImageProcessingExecutorService() {
		int nThreads = Runtime.getRuntime().availableProcessors();
		queue = new LinkedBlockingQueue<>();
		executorService = new ThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, queue);
	}

	public int size() {
		return queue.size();
	}

}
