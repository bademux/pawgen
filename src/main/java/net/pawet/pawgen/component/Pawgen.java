package net.pawet.pawgen.component;

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
import java.util.function.Consumer;
import java.util.stream.Stream;

@Slf4j
public record Pawgen(ProcessingExecutorService processingExecutor,
					 ArticleHeaderQuery queryService,
					 Renderer renderer,
					 FileSystemRegistry fsRegistry,
					 Storage storage,
					 StaticFileService staticFileService,
					 Consumer<Stream<FileDigestData>> deployer) implements AutoCloseable {

	public static Pawgen create(CliOptions opts) {
		var fsRegistry = new FileSystemRegistry();
		Path outputDir = fsRegistry.getPathFsRegistration(opts.getOutputUri());
		var staticFileService = new StaticFileService(opts.getStaticUris(), fsRegistry);
		var storage = Storage.create(staticFileService::resolve, fsRegistry.getPathFsRegistration(opts.getContentUri()), outputDir);
		var watermarkFilter = new WatermarkFilterFactory(fsRegistry).create(opts.getWatermarkText(), opts.getWatermarkUri());
		var imageFactory = new ProcessableImageFactory(watermarkFilter);
		var processingExecutor = new ProcessingExecutorService(Runtime.getRuntime().availableProcessors());
		var resourceFactory = new ResourceFactory(storage, imageFactory, opts.getHosts());
		var templater = new Templater(storage::readFromInput, fsRegistry.getPathFsRegistration(opts.getTemplatesUri()));
		var queryService = new ArticleHeaderQuery(storage, new ArticleParser(resourceFactory));
		var renderer = Renderer.of(templater, queryService, processingExecutor);
		var deployerFactory = new DeployerFactory(opts.getNetlifyUrl(), opts.getAccessToken(), opts.getSiteId());
		return new Pawgen(processingExecutor, queryService, renderer, fsRegistry, storage, staticFileService, deployerFactory.create(opts.isNetlifyEnabled()));
	}

	public void deploy() {
		try (var items = storage.readOutputDir()) {
			deployer.accept(items.map(DigestAwareResourceFile::new));
		}
	}

	private record DigestAwareResourceFile(@Delegate(types = FileDigestData.class) DigestAwareResource resource) implements FileDigestData {
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

}

