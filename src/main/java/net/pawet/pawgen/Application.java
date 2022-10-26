package net.pawet.pawgen;

import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Pawgen;
import net.pawet.pawgen.component.netlify.DeployerFactory;
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
		try (var app = setupShutdownHook(Pawgen.create(config, Runtime.getRuntime().availableProcessors() * 2))) {
			var cleanupIn = app.cleanupOutputDir();
			var copyTask = CompletableFuture.supplyAsync(app::copyFiles);
			var renderIn = app.render();
			var copyIn = copyTask.join();
			long startDeploy = CLOCK.millis();
			try (var files = app.readOutputDir()) {
				new DeployerFactory(config.getNetlifyUrl(), config.getAccessToken(), config.getSiteId(), config.isNetlifyEnabled())
					.create()
					.accept(files);
			}
			log.info("Cleanup {}min, render {}min, img processing {}min, copy resources {}min, deploy {}min",
				cleanupIn.toMinutes(), renderIn.toMinutes(), app.getImageProcessingTime().toMinutes(), app.getCopyResourcesTime().plus(copyIn).toMinutes(), Duration.ofMillis(CLOCK.millis() - startDeploy).toMinutes()
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

	private static Pawgen setupShutdownHook(Pawgen app) {
		Runtime.getRuntime().addShutdownHook(new Thread(app::close));
		return app;
	}

	private static List<String> handleDirectClassRun(List<String> args) {
		int pos = args.indexOf(Application.class.getName());
		if (pos != -1) {
			return args.subList(pos + 1, args.size());
		}
		return args;
	}

}

