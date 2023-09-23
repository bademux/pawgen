package net.pawet.pawgen;

import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Pawgen;
import net.pawet.pawgen.component.Xml2MDMigrator;
import net.pawet.pawgen.component.deployer.DeployerFactory;
import net.pawet.pawgen.component.system.CliOptions;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class Application {

	private final static Clock CLOCK = Clock.systemUTC();

	public static void main(String... args) {
		System.exit(run(handleDirectClassRun(Arrays.asList(args))));
	}

	public static int run(List<String> args) {
		long start = CLOCK.millis();
		var config = CliOptions.parse(args);
		if(true) {
			Xml2MDMigrator.migrate(config);
			return 0;
		}
		log.info("Executed with config: {}", config);
		try (var app = setupShutdownHook(Pawgen.create(CLOCK, config))) {
			var cleanupIn = app.cleanupOutputDir();
			var renderIn = app.render();
			long startDeploy = CLOCK.millis();
			try (var files = app.readOutputDir()) {
				DeployerFactory.create(config)
					.deployer(config.getDeployerNames())
					.accept(files.toList());
			}
			log.info("Cleanup {}min, render {}min, img processing {}min, copy resources {}min, deploy {}min",
				cleanupIn.toMinutes(), renderIn.toMinutes(), app.getImageProcessingTime().toMinutes(), app.getCopyResourcesTime().toMinutes(), Duration.ofMillis(CLOCK.millis() - startDeploy).toMinutes()
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
