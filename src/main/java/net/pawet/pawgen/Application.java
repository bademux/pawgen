package net.pawet.pawgen;

import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Pawgen;
import net.pawet.pawgen.component.system.CliOptions;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class Application {

	private final static Clock CLOCK = Clock.systemUTC();

	public static void main(String... args) {
		System.exit(run(handleDirectClassRun(Arrays.asList(args))));
	}

	public static int run(List<String> args) {
		long start = CLOCK.millis();
		var config = CliOptions.parse(args);
		log.info("Executed with config: {}", config);
		try (var app = Pawgen.create(config)) {
			Runtime.getRuntime().addShutdownHook(new Thread(app::close));
			if (config.isCleanupOutputDir()) {
				log.info("Cleaning output dir");
				app.cleanupOutputDir();
			}
			var copyStaticTask = CompletableFuture.supplyAsync(() -> measure(app::copyStaticResources));
			var renderIn = measure(app::render);
			var copyStaticResourcesIn = copyStaticTask.join();
			var deployIn = measure(app::deploy);
			log.info("Built in {}min: render {}min, copyStaticResources {}min, deploy {}min",
				Duration.ofMillis(CLOCK.millis() - start).toMinutes(),
				renderIn.toMinutes(), copyStaticResourcesIn.toMinutes(), deployIn.toMinutes()
			);
		} catch (Throwable e) {
			log.error("Unrecoverable error: {}", e.getMessage(), e);
			CliOptions.handleError(e).forEach(log::error);
			return 1;
		} finally {
			log.info("Processed in {} min", Duration.ofMillis(CLOCK.millis() - start).toMinutes());
		}
		return 0;
	}

	private static Duration measure(Runnable run) {
		long start = CLOCK.millis();
		run.run();
		return Duration.ofMillis(CLOCK.millis() - start);
	}

	private static List<String> handleDirectClassRun(List<String> args) {
		int pos = args.indexOf(Application.class.getName());
		if (pos != -1) {
			return args.subList(pos + 1, args.size());
		}
		return args;
	}

}

