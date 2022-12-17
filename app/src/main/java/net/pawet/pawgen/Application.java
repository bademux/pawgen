package net.pawet.pawgen;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Pawgen;
import net.pawet.pawgen.component.deployer.DeployerFactory;
import net.pawet.pawgen.component.deployer.FileDigestData;
import net.pawet.pawgen.component.system.CliOptions;
import net.pawet.pawgen.component.system.storage.DigestAwareResource;

import java.io.InputStream;
import java.nio.channels.Channels;
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
		try (var app = setupShutdownHook(Pawgen.create(CLOCK, config))) {
			var cleanupIn = app.cleanupOutputDir();
			var renderIn = app.render();
			long startDeploy = CLOCK.millis();
			try (var files = app.readOutputDir()) {
				new DeployerFactory(config.getNetlifyUrl(), config.getAccessToken(), config.getSiteId())
					.create(DeployerFactory.Type.from(config.getDeployerType()))
					.accept(files.map(DigestAwareResourceFile::new).toList());
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

@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor
final class DigestAwareResourceFile implements FileDigestData {
	private final DigestAwareResource resource;

	@ToString.Include
	@EqualsAndHashCode.Include
	public String getRootRelativePath() {
		return this.resource.getRootRelativePath();
	}

	@ToString.Include
	@EqualsAndHashCode.Include
	public String getDigest() {
		return this.resource.getDigest();
	}

	public InputStream inputStream() {
		return Channels.newInputStream(this.resource.readable());
	}

}
