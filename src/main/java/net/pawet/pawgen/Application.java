package net.pawet.pawgen;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import net.pawet.pawgen.component.ArticleHeader;
import net.pawet.pawgen.component.Storage;
import net.pawet.pawgen.component.img.ImageParser;
import net.pawet.pawgen.component.img.WatermarkFilter;
import net.pawet.pawgen.component.render.ProcessingItem;
import net.pawet.pawgen.component.render.Templater;
import net.pawet.pawgen.component.system.CommandLineOptions;
import net.pawet.pawgen.component.system.ImageProcessingExecutorService;
import net.pawet.pawgen.component.xml.ContentParser;
import net.pawet.pawgen.component.xml.HeaderParser;

import java.io.IOException;
import java.time.Clock;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static java.util.logging.Level.FINE;
import static java.util.stream.Collectors.toUnmodifiableList;

@Log
@RequiredArgsConstructor
public class Application implements Runnable, AutoCloseable {

	private final ImageProcessingExecutorService imageProcessingExecutor;
	private final HeaderParser headerParser;
	private final ContentParser contentParser;
	private final Templater templater;
	private final Storage storage;

	public static void main(String[] args) {
		Clock clock = Clock.systemUTC();
		try {
			long start = clock.millis();
			CommandLineOptions config = CommandLineOptions.parse(args);
			System.out.println("Executed with config:\n" + config);
			Application app = Application.create(config);
			try (app) {
				app.run();
			}
			app.printImageProcessingStatus();
			System.out.printf("Processed in %ds\n", (clock.millis() - start) / 1000);
		} catch (Throwable e) {
			log.log(FINE, "Error: ", e);
			CommandLineOptions.handleError(e).forEach(System.out::println);
			e.printStackTrace();
			System.exit(1);
		}
	}

	static Application create(CommandLineOptions opts) {
		var imageProcessingExecutor = new ImageProcessingExecutorService();
		var storage = Storage.of(opts.getContentDir(), opts.getOutputDir(), opts.getStaticDir(), opts.getDateFrom());
		var imageParser = ImageParser.of(imageProcessingExecutor, storage, createWatermarkFilter(opts));
		var contentReader = ContentParser.of(storage, imageParser);
		var templater = Templater.of(opts.getTemplatesDir());
		Application application = new Application(imageProcessingExecutor, new HeaderParser(), contentReader, templater, storage);
		Runtime.getRuntime().addShutdownHook(new Thread(application::close));
		return application;
	}

	private static WatermarkFilter createWatermarkFilter(CommandLineOptions opts) {
		if (opts.getWatermarkFile() != null) {
			return WatermarkFilter.of(opts.getWatermarkFile(), 0.7f);
		}
		return WatermarkFilter.of(opts.getWatermarkText(), 0.7f);
	}

	@Override
	public void run() {
		System.out.println("Finding articles to be processed.");
		Collection<ArticleHeader> index = readAllArticles();
		System.out.println("Found " + index.size() + " articles.");
		templater.create(index, contentParser::read)
			.peek(this::printProgress)
			.forEach(item -> item.writeWith(storage::writer));
		System.out.println("Processing static resources.");
		storage.copyStaticResources();
		System.out.println("Waiting for images to be  processed.");
	}

	private void printProgress(ProcessingItem processingItem) {
		System.out.printf("Processing %s.\n", processingItem.getPrintableName());
	}

	private Collection<ArticleHeader> readAllArticles() {
		try (var articles = storage.readArticles()) {
			return articles.flatMap(headerParser::parse).collect(toUnmodifiableList());
		}
	}

	public void printImageProcessingStatus() throws InterruptedException {
		do {
			System.out.printf("%d images left.\n", imageProcessingExecutor.size());
		} while (!imageProcessingExecutor.awaitTermination(5, TimeUnit.SECONDS));
	}

	@Override
	public void close() {
		imageProcessingExecutor.shutdown();
		storage.close();
	}

	static {
		System.setProperty("java.awt.headless", Boolean.TRUE.toString());
		Logger.getLogger("").addHandler(createFileHandler());
	}

	@SneakyThrows
	private static FileHandler createFileHandler() {
		FileHandler handler = new FileHandler("pawgen.%g.log", 1024 * 1024, 3, true);
		handler.setFormatter(new SimpleFormatter());
		return handler;
	}
}

