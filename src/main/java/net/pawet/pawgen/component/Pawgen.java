package net.pawet.pawgen.component;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.netlify.FileDigestData;
import net.pawet.pawgen.component.render.ArticleHeaderQuery;
import net.pawet.pawgen.component.render.Renderer;
import net.pawet.pawgen.component.render.Templater;
import net.pawet.pawgen.component.resource.ResourceFactory;
import net.pawet.pawgen.component.resource.img.ProcessableImageFactory;
import net.pawet.pawgen.component.resource.img.WatermarkFilterFactory;
import net.pawet.pawgen.component.system.CliOptions;
import net.pawet.pawgen.component.system.ProcessingExecutorService;
import net.pawet.pawgen.component.system.storage.DigestAwareResource;
import net.pawet.pawgen.component.system.storage.FileSystemRegistry;
import net.pawet.pawgen.component.system.storage.Resource;
import net.pawet.pawgen.component.system.storage.Storage;
import net.pawet.pawgen.component.xml.ArticleParser;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toUnmodifiableSet;

@Slf4j
@RequiredArgsConstructor
public class Pawgen implements AutoCloseable {

	private final Clock clock = Clock.systemUTC();
	private final ProcessingExecutorService processingExecutor;
	private final ArticleHeaderQuery queryService;
	private final Renderer renderer;
	private final FileSystemRegistry fsRegistry;
	private final Storage storage;
	private final ResourceFactory resourceFactory;
	private final boolean cleanupOutputDir;

	public static Pawgen create(CliOptions opts) {
		var fsRegistry = new FileSystemRegistry();
		var staticDirs = opts.getStaticUris().stream()
			.flatMap(fsRegistry::parseCopyDir)
			.collect(toUnmodifiableSet());
		Path contentDir = fsRegistry.getPathFsRegistration(opts.getContentUri());
		Path outputDir = fsRegistry.getPathFsRegistration(opts.getOutputUri());
		var storage = Storage.create(staticDirs, contentDir, outputDir);
		var watermarkFilter = new WatermarkFilterFactory(fsRegistry).create(opts.getWatermarkText(), opts.getWatermarkUri());
		var imageFactory = ProcessableImageFactory.of(watermarkFilter, 250);
		var processingExecutor = new ProcessingExecutorService();
		var resourceFactory = new ResourceFactory(storage, imageFactory, opts.getHosts());
		var templater = new Templater(storage::readFromInput, fsRegistry.getPathFsRegistration(opts.getTemplatesUri()));
		var queryService = new ArticleHeaderQuery(storage, new ArticleParser(resourceFactory));
		var renderer = Renderer.of(templater, queryService, processingExecutor);
		return new Pawgen(processingExecutor, queryService, renderer, fsRegistry, storage, resourceFactory, opts.isCleanupOutputDir());
	}

	public Stream<DigestAwareResource> readOutputDir() {
		return storage.readOutputDir();
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

	private void copyFiles() {
		try (var files = storage.copyFiles()) {
			files.forEach(Resource::transfer);
		}
	}

	public Duration render() {
		return measure(this::renderInternal);
	}

	@SneakyThrows
	public void renderInternal() {
		processingExecutor.execute(this::copyFiles);
		log.info("Finding articles to be processed.");
		try (var headers = queryService.get(Category.ROOT)) {
			headers.map(renderer::create).forEach(Renderer.ArticleContext::render);
		}
		processingExecutor.waitAllExecuted();
		assert storage.assertChecksums() : "Some checksum are inconsistent";
	}

	public Duration getImageProcessingTime() {
		return resourceFactory.getImageProcessingTime();
	}

	public Duration getCopyResourcesTime() {
		return resourceFactory.getResourceProcessingTime();
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

