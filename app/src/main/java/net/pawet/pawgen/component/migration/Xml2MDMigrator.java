package net.pawet.pawgen.component.migration;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Article;
import net.pawet.pawgen.component.Category;
import net.pawet.pawgen.component.system.CliOptions;
import net.pawet.pawgen.component.system.storage.FileSystemRegistry;
import net.pawet.pawgen.component.system.storage.Storage;
import net.pawet.pawgen.component.xml.XmlArticleParser;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static java.nio.file.Files.walk;

@Slf4j
@RequiredArgsConstructor
public class Xml2MDMigrator {

	@SneakyThrows
	public static void migrate(CliOptions opts) {
		log.info("run migrator");
		try (var fsRegistry = new FileSystemRegistry()) {
			var storage = Storage.create(
				opts.getStaticUris().stream().flatMap(fsRegistry::parseCopyDir),
				fsRegistry.getPathFsRegistration(opts.getContentUri()),
				fsRegistry.getPathFsRegistration(opts.getOutputUri())
			);
			var parser = XmlArticleParser.of((category, attrs) -> attrs, (category, attrs) -> attrs);
			Path contentDir = Path.of(opts.getContentUri());
			try (var files = walk(contentDir).filter(Files::isDirectory)) {
				files.map(contentDir::relativize)
					.map(Category::of)
					.flatMap(storage::read)
					.map(parser::parse)
					.forEach(article -> save(opts, article));
			}
		} catch (Exception e){
			log.error("error", e);
		}
	}

	@SneakyThrows
	private static void save(CliOptions opts, Article article) {
		Path dest = setupDestFile(opts.getOutputUri(), article);
		log.info("migrating '{}', from {} to {}", article.getTitle(), article.getCategory(), dest);
		try (var writer = Files.newBufferedWriter(dest)) {
			writer.write("---");
			writer.newLine();
			createItem(writer, "title", article.getTitle());
			createItem(writer, "author", article.getAuthor());
			createItem(writer, "date", article.getDate().toString());
			createItem(writer, "source", article.getSource());
			createItem(writer, "type", article.getType());
			createList(writer, "attachments", article.getAttachmentUri());
			createList(writer, "aliases", article.getAliases().toArray(String[]::new));
			writer.write("---");
			writer.newLine();
			writer.append(article.readContent());
		}
	}

	private static void createItem(BufferedWriter writer, String name, String value) throws IOException {
		if(value == null) {
			return;
		}
		writer.write(name);
		writer.write(": ");
		writer.write(value);
		writer.newLine();
	}

	private static void createList(BufferedWriter writer, String name, String... values) throws IOException {
		if(values.length == 0 || Arrays.asList(values).contains(null)) {
			return;
		}
		writer.write(name);
		writer.write(": ");
		writer.newLine();
		for (String value : values) {
			writer.write("   - ");
			writer.write(value);
			writer.newLine();
		}
	}

	private static Path setupDestFile(URI outputUri, Article article) throws IOException {
		return Files.createDirectories(Path.of(outputUri).resolve(article.getCategory().toString()))
			.resolve("index." + article.getLang() + ".md");
	}

}

