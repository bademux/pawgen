package net.pawet.pawgen.component;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.markdown.MDArticleParser;
import net.pawet.pawgen.component.render.ArticleQuery;
import net.pawet.pawgen.component.render.TrackingExecutor;
import net.pawet.pawgen.component.render.Renderer;
import net.pawet.pawgen.component.render.Templater;
import net.pawet.pawgen.component.resource.ResourceProcessor;
import net.pawet.pawgen.component.resource.img.ProcessableImageFactory;
import net.pawet.pawgen.component.resource.img.WatermarkFilterFactory;
import net.pawet.pawgen.component.system.CliOptions;
import net.pawet.pawgen.component.system.storage.DigestAwareResource;
import net.pawet.pawgen.component.system.storage.FileSystemRegistry;
import net.pawet.pawgen.component.system.storage.Resource;
import net.pawet.pawgen.component.system.storage.Storage;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;

import static java.time.temporal.ChronoUnit.MINUTES;

@Slf4j
@RequiredArgsConstructor
public class Pawgen implements AutoCloseable {

	private final Clock clock;
	private final TrackingExecutor trackingExecutor;
	private final ArticleQuery queryService;
	private final Renderer renderer;
	private final FileSystemRegistry fsRegistry;
	private final Storage storage;
	private final ResourceProcessor resourceProcessor;

	public static Pawgen create(Clock clock, CliOptions opts) {
		var fsRegistry = new FileSystemRegistry();
		var storage = Storage.create(
			opts.getStaticUris().stream().flatMap(fsRegistry::parseCopyDir),
			fsRegistry.getPathFsRegistration(opts.getContentUri()),
			fsRegistry.getPathFsRegistration(opts.getOutputUri())
		);
		var watermarkFilter = new WatermarkFilterFactory(fsRegistry::getPathFsRegistration)
			.create(opts.getWatermarkText(), opts.getWatermarkUri());
		var imageFactory = ProcessableImageFactory.of(watermarkFilter, 250);
		var threadFactory = Thread.ofVirtual().factory();
		var resourceFactory = new ResourceProcessor(storage, imageFactory, opts.getHosts());
		var executor = new TrackingExecutor(threadFactory);
		var templater = Templater.of(storage::readFromInput, fsRegistry.getPathFsRegistration(opts.getTemplatesUri()), executor);
		var parser = MDArticleParser.of(resourceFactory::attributes);
//		var parser = new FormatAwareArticleParser(
//			XmlArticleParser.of(resourceFactory::image, resourceFactory::link),
//			MDArticleParser.of(resourceFactory::image, resourceFactory::link)
//		);
		var queryService = new ArticleQuery(storage, parser::parse);
		var renderer = Renderer.of(executor, templater, clock, queryService);
		return new Pawgen(clock, executor, queryService, renderer, fsRegistry, storage, resourceFactory);
	}

	public Stream<DigestAwareResource> readOutputDir() {
		return storage.readOutputDir();
	}

	public Duration cleanupOutputDir() {
		return measure(this::cleanupOutputDirInternal);
	}

	void cleanupOutputDirInternal() {
		log.debug("Cleaning output dir");
		storage.cleanupOutputDir();
	}

	private void copyFiles() {
		try (var files = storage.staticFiles()) {
			files.forEach(Resource::transfer);
		}
	}

	public Duration render() {
		return measure(this::renderInternal);
	}

	@SneakyThrows
	void renderInternal() {
		trackingExecutor.execute(this::copyFiles);
		log.info("Finding articles to be processed.");
		try (var headers = queryService.getArticles(Category.ROOT)) {
			headers.map(renderer::create).forEach(Renderer.ArticleContext::renderAsync);
		}
		trackingExecutor.waitRendered(Duration.of(30, MINUTES));
		assert storage.assertChecksums() : "Some checksum are inconsistent";
		storage.writeAliases(
			renderer.getProcessed()
				.flatMap(article -> article.getAliases().map(alias -> Map.entry(alias, article.getUrl())))
				.distinct()::iterator
		);
	}

	public Duration getResourceProcessingTime() {
		return resourceProcessor.getProcessingTime();
	}

	@Override
	@SneakyThrows
	public void close() {
		fsRegistry.close();
	}

	private Duration measure(Runnable run) {
		long start = clock.millis();
		run.run();
		return Duration.ofMillis(clock.millis() - start);
	}

}

