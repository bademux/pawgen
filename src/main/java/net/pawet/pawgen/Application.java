package net.pawet.pawgen;

import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Pawgen;
import net.pawet.pawgen.component.system.CliOptions;
import org.slf4j.bridge.SLF4JBridgeHandler;

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
		log.info("Executed with config: {}", config);
		try (var app = Pawgen.create(config)) {
			Runtime.getRuntime().addShutdownHook(new Thread(app::close));
			app.render();
			app.copyStaticResources();
			log.info("Built in {} min", Duration.ofMillis(CLOCK.millis() - start).toMinutes());
			app.deploy();
		} catch (Throwable e) {
			log.error("Unrecoverable error: {}", e.getMessage(), e);
			CliOptions.handleError(e).forEach(log::error);
			return 1;
		} finally {
			log.info("Processed in {} min", Duration.ofMillis(CLOCK.millis() - start).toMinutes());
		}
		return 0;
	}


	private static List<String> handleDirectClassRun(List<String> args) {
		int pos = args.indexOf(Application.class.getName());
		if (pos != -1) {
			return args.subList(pos + 1, args.size());
		}
		return args;
	}

	static {
		System.setProperty("java.awt.headless", Boolean.TRUE.toString());
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();
	}

}

