package net.pawet.pawgen.component.system;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
public final class ProcessingExecutorService implements ExecutorService {

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
	private final LinkedBlockingQueue<Future<?>> queue = new LinkedBlockingQueue<>();

	@SneakyThrows
	public void waitAllExecuted() {
		do {
			var future = queue.poll();
			if (future == null) {
				break;
			}
			future.get();
		} while (true);
		log.debug("Processed tasks");
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

	@SneakyThrows
	private <T> FutureTask<T> createFuture(Callable<T> task) {
		var f = new FutureTask<>(task);
		queue.put(f);
		return f;
	}

	@Override
	public void execute(@NonNull Runnable command) {
		submit(Executors.callable(command));
	}

	@Override
	public <T> Future<T> submit(@NonNull Callable<T> task) {
		var f = createFuture(task);
		executor.execute(f);
		return f;
	}

	@Override
	public <T> Future<T> submit(@NonNull Runnable task, T result) {
		return submit(Executors.callable(task, result));
	}

	@Override
	public Future<?> submit(@NonNull Runnable task) {
		return submit(Executors.callable(task));
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
		return (List) executor.invokeAll(tasks.stream().map(this::createFuture).map(Executors::callable).toList());
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
		return (List) executor.invokeAll(tasks.stream().map(this::createFuture).map(Executors::callable).toList(), timeout, unit);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws ExecutionException, InterruptedException {
		return (T) executor.invokeAny(tasks.stream().map(this::createFuture).map(Executors::callable).toList());
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
		return (T) executor.invokeAny(tasks.stream().map(this::createFuture).map(Executors::callable).toList(), timeout, unit);
	}

}
