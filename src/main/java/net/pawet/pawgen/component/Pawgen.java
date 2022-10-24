package net.pawet.pawgen.component;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.netlify.DeployerFactory;
import net.pawet.pawgen.component.netlify.FileDigestData;
import net.pawet.pawgen.component.render.ArticleHeaderQuery;
import net.pawet.pawgen.component.render.Renderer;
import net.pawet.pawgen.component.render.Templater;
import net.pawet.pawgen.component.resource.ResourceFactory;
import net.pawet.pawgen.component.resource.img.ProcessableImageFactory;
import net.pawet.pawgen.component.resource.img.WatermarkFilterFactory;
import net.pawet.pawgen.component.system.CliOptions;
import net.pawet.pawgen.component.system.ProcessingExecutorService;
import net.pawet.pawgen.component.system.storage.*;
import net.pawet.pawgen.component.xml.ArticleParser;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public class Pawgen implements AutoCloseable {

	private final ProcessingExecutorService processingExecutor;
	private final ArticleHeaderQuery queryService;
	private final Renderer renderer;
	private final FileSystemRegistry fsRegistry;
	private final Storage storage;
	private final StaticFileService staticFileService;
	private final Consumer<Stream<FileDigestData>> deployer;
	private final Supplier<Duration> imagesProcessingTime;
	private final Clock clock;
	private final boolean cleanupOutputDir;

	public static Pawgen create(CliOptions opts, Clock clock, int processingThreads) {
		var fsRegistry = new FileSystemRegistry();
		Path outputDir = fsRegistry.getPathFsRegistration(opts.getOutputUri());
		var staticFileService = new StaticFileService(opts.getStaticUris(), fsRegistry);
		var storage = Storage.create(staticFileService::resolve, fsRegistry.getPathFsRegistration(opts.getContentUri()), outputDir);
		var watermarkFilter = new WatermarkFilterFactory(fsRegistry).create(opts.getWatermarkText(), opts.getWatermarkUri());
		var imageFactory = ProcessableImageFactory.of(clock, watermarkFilter);
		var processingExecutor = new ProcessingExecutorService(processingThreads);
		var resourceFactory = new ResourceFactory(storage, imageFactory, opts.getHosts());
		var templater = new Templater(storage::readFromInput, fsRegistry.getPathFsRegistration(opts.getTemplatesUri()));
		var queryService = new ArticleHeaderQuery(storage, new ArticleParser(resourceFactory));
		var renderer = Renderer.of(templater, queryService, processingExecutor);
		var deployerFactory = new DeployerFactory(opts.getNetlifyUrl(), opts.getAccessToken(), opts.getSiteId());
		return new Pawgen(processingExecutor, queryService, renderer, fsRegistry, storage, staticFileService, deployerFactory.create(opts.isNetlifyEnabled()), imageFactory::getProcessingTime, clock, opts.isCleanupOutputDir());
	}

	public Duration deploy() {
		return measure(this::deployInternal);
	}

	public void deployInternal() {
		try (var items = storage.readOutputDir()) {
			deployer.accept(items.map(DigestAwareResourceFile::new));
		}
	}

	public Duration cleanupOutputDir() {
		return measure(this::cleanupOutputDirInternal);
	}

	public void cleanupOutputDirInternal() {
		if (cleanupOutputDir) {
			log.debug("Cleaning output dir");
			storage.cleanupOutputDir();
		}
	}

	private record DigestAwareResourceFile(@Delegate(types = FileDigestData.class) DigestAwareResource resource) implements FileDigestData {
	}

	public Duration render() {
		return measure(this::renderInternal);
	}

	public void renderInternal() {
		log.info("Finding articles to be processed.");
		try (var headers = queryService.get(Category.ROOT)) {
			headers.map(renderer::create).forEach(Renderer.ArticleContext::render);
		}
		processingExecutor.waitAllExecuted();
		assert storage.assertChecksums() : "Some checksum are inconsistent";
	}

	public Duration copyStaticResources() {
		return measure(this::copyStaticResourcesInternal);
	}

	public Duration getImageProcessingTime() {
		return imagesProcessingTime.get();
	}

	public void copyStaticResourcesInternal() {
		log.info("Copying static resources");
		staticFileService.copyStaticResources(storage::resource)
			.filter(SimpleResource::isNewOrChanged)
			.forEach(res -> {
				try {
					res.transfer();
				} catch (Exception e) {
					log.error("Can't copy resource '{}'", res, e);
				}
			});
	}

	@Override
	@SneakyThrows
	public void close() {
		processingExecutor.close();
		fsRegistry.close();
	}

	private Duration measure(Runnable run) {
		long start = clock.millis();
		run.run();
		return Duration.ofMillis(clock.millis() - start);
	}

}

