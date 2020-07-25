package net.pawet.pawgen.component;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.netlify.NetlifyDeployer;
import net.pawet.pawgen.component.render.ArticleHeaderQuery;
import net.pawet.pawgen.component.render.Renderer;
import net.pawet.pawgen.component.render.Templater;
import net.pawet.pawgen.component.resource.ResourceFactory;
import net.pawet.pawgen.component.resource.img.ImageFactory;
import net.pawet.pawgen.component.resource.img.WatermarkFilterFactory;
import net.pawet.pawgen.component.system.CliOptions;
import net.pawet.pawgen.component.system.ProcessingExecutorService;
import net.pawet.pawgen.component.system.storage.DigestAwareResource;
import net.pawet.pawgen.component.system.storage.Storage;
import net.pawet.pawgen.component.xml.ContentParser;

import java.util.function.Consumer;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public class Pawgen implements AutoCloseable {

	private final ProcessingExecutorService processingExecutor;
	private final ArticleHeaderQuery queryService;
	private final Renderer renderer;
	private final Storage storage;
	private final Consumer<Stream<DigestAwareResource>> deployer;

	public static Pawgen create(CliOptions opts) {
		var storage = Storage.builder()
			.contentUri(opts.getContentUri())
			.outputUri(opts.getOutputUri())
			.staticUris(opts.getStaticUris())
			.templatesUri(opts.getTemplatesUri())
			.dateFrom(opts.getDateFrom())
			.watermarkUri(opts.getWatermarkUri())
			.build();
		var imageFactory = ImageFactory.of(WatermarkFilterFactory.of(storage).create(opts.getWatermarkText()));
		var processingExecutor = new ProcessingExecutorService(Runtime.getRuntime().availableProcessors());
		var imageParser = ResourceFactory.of(storage, imageFactory, opts.getHosts());
		var contentReader = ContentParser.of(storage, imageParser);
		var templater = new Templater(storage);
		var queryService = new ArticleHeaderQuery(storage);
		var renderer = Renderer.of(templater, queryService, processingExecutor, contentReader::read, storage);
		return new Pawgen(processingExecutor, queryService, renderer, storage, deployerFactory(opts));
	}

	private static Consumer<Stream<DigestAwareResource>> deployerFactory(CliOptions opts) {
		if (opts.isNetlifyEnabled()) {
			return new NetlifyDeployer<DigestAwareResource>(opts.getNetlifyUrl(), opts.getAccessToken(), opts.getSiteId())::deploy;
		}
		log.info("Netlify deployment disabled");
		return __ -> {
		};
	}

	public void deploy() {
		try (var items = storage.readOutputDir()) {
			deployer.accept(items);
		}
	}

	public void render() {
		log.info("Finding articles to be processed.");
		try (var headers = queryService.get(Category.ROOT)) {
			headers.map(renderer::create).forEach(Renderer.ArticleContext::render);
		}
		processingExecutor.waitAllExecuted();
		assert storage.assertChecksums() : "Some checksum are inconsistent";
	}

	public void copyStaticResources() {
		log.info("Copying static resources");
		storage.copyStaticResources();
	}

	public void timestamp() {
		log.info("Timestamp build");
		storage.timestamp();
	}

	@Override
	@SneakyThrows
	public void close() {
		processingExecutor.close();
		storage.close();
	}

}

