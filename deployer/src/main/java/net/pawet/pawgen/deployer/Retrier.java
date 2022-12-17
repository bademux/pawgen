package net.pawet.pawgen.deployer;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Callable;

import static java.lang.Thread.sleep;

@Slf4j
@RequiredArgsConstructor
final class Retrier {

	private final Clock clock;
	private final Duration initialTimeout;
	private final int maxRetries;

	public Retrier(Duration initialTimeout, int maxRetries) {
		this(Clock.systemUTC(), initialTimeout, maxRetries);
	}

	public Void exec(Runnable operation) {
		return exec(() -> {
			operation.run();
			return null;
		});
	}

	@SneakyThrows
	public <T> T exec(Callable<T> operation) {
		for (long i = 1, t = initialTimeout.toMillis(); ; i++, t *= 2) {
			if (i > maxRetries) {
				throw new IllegalStateException(maxRetries + " operations in a row was done, limit reached.");
			}
			try {
				return operation.call();
			} catch (DeployerHttpException.DeployerRateLimitHttpException e) {
				long delay = clock.millis() - e.getReset().toEpochMilli();
				if (delay > 0) {
					log.info("Requests are rate limited. Waiting {} seconds", delay / 1000);
					sleep(delay);
					continue;
				}
			} catch (DeployerHttpException e) {
				if (e.getHttpStatusCode() / 100 == 4) {
					log.debug("Hit client error", e);
					throw e; // don't trigger retry
				}
				log.debug("Something bad happens", e);
			}
			log.info("Retrying {} times, waiting {}ms", i, t);
			sleep(t);
		}
	}

}
