package net.pawet.pawgen.component;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.netlify.DeployerFactory;
import net.pawet.pawgen.component.render.ArticleHeaderQuery;
import net.pawet.pawgen.component.render.Renderer;
import net.pawet.pawgen.component.render.Templater;
import net.pawet.pawgen.component.resource.ResourceFactory;
import net.pawet.pawgen.component.resource.img.ImageFactory;
import net.pawet.pawgen.component.resource.img.WatermarkFilterFactory;
import net.pawet.pawgen.component.system.CliOptions;
import net.pawet.pawgen.component.system.ProcessingExecutorService;
import net.pawet.pawgen.component.system.storage.*;
import net.pawet.pawgen.component.xml.ContentParser;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Slf4j
public record Pawgen(ProcessingExecutorService processingExecutor,
					 ArticleHeaderQuery queryService,
					 Renderer renderer,
					 FileSystemRegistry fsRegistry,
					 Storage storage,
					 StaticFileService staticFileService,
					 Consumer<Stream<DigestAwareResource>> deployer) implements AutoCloseable {

	public static Pawgen create(CliOptions opts) {
		var fsRegistry = new FileSystemRegistry();
		Path outputDir = fsRegistry.getPathFsRegistration(opts.getOutputUri());
		var lastBuildService = LastBuildService.create(opts.getDateFrom(), outputDir);
		var staticFileService = new StaticFileService(opts.getStaticUris(), outputDir, fsRegistry, lastBuildService::isNewOrUpdated);
		var storage = Storage.create(staticFileService::resolve, lastBuildService, fsRegistry.getPathFsRegistration(opts.getContentUri()), outputDir);
		var imageFactory = new ImageFactory(new WatermarkFilterFactory(fsRegistry).create(opts.getWatermarkText(), opts.getWatermarkUri()));
		var processingExecutor = new ProcessingExecutorService(Runtime.getRuntime().availableProcessors());
		var imageParser = new ResourceFactory(storage, imageFactory, opts.getHosts());
		var contentReader = new ContentParser(storage, imageParser);
		var templater = new Templater(storage::read, fsRegistry.getPathFsRegistration(opts.getTemplatesUri()));
		var queryService = new ArticleHeaderQuery(storage);
		var renderer = Renderer.of(templater, queryService, processingExecutor, contentReader::read, storage);
		var deployerFactory = new DeployerFactory(opts.getNetlifyUrl(), opts.getAccessToken(), opts.getSiteId());
		return new Pawgen(processingExecutor, queryService, renderer, fsRegistry, storage, staticFileService, deployerFactory.create(opts.isNetlifyEnabled()));
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
		staticFileService.copyStaticResources(storage::write);
	}

	public void timestamp() {
		log.info("Timestamp build");
		storage.timestamp();
	}

	@Override
	@SneakyThrows
	public void close() {
		processingExecutor.close();
		fsRegistry.close();
	}

}

