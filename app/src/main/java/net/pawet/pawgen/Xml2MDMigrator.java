package net.pawet.pawgen;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Article;
import net.pawet.pawgen.component.Category;
import net.pawet.pawgen.component.system.CliOptions;
import net.pawet.pawgen.component.system.storage.FileSystemRegistry;
import net.pawet.pawgen.component.system.storage.Storage;
import net.pawet.pawgen.component.xml.XmlArticleParser;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.Files.walk;
import static java.util.function.Predicate.not;

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
			var parser = XmlArticleParser.of((__, attrs) -> attrs, (__, attrs) -> attrs);
			Path contentDir = Path.of(opts.getContentUri());
			try (var files = walk(contentDir).filter(Files::isDirectory)) {
				files.map(contentDir::relativize)
					.map(Category::of)
					.flatMap(storage::readXml)
					.map(parser::parse)
					.forEach(article -> save(opts, article));
			}
		} catch (Exception e) {
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
			createList(writer, "authors", findAuthors(article));
			createItem(writer, "date", article.getDate().toString());
			createItem(writer, "source", article.getSource());
			createItem(writer, "type", article.getType());
			createList(writer, "attachments", article.getAttachmentUri());
			createList(writer, "aliases", article.getAliases().toArray(String[]::new));
			writer.write("---");
			writer.newLine();
			writer.append(convertMarkdownToHTML(article.readContent()));
		}
	}

	public static String convertMarkdownToHTML(String markdown) {
		Parser parser = Parser.builder().build();
		var document = parser.parse(markdown);
		var htmlRenderer = HtmlRenderer.builder().build();
		return htmlRenderer.render(document);
	}


	private static String[] findAuthors(Article article) {
		String author = article.getAuthor();
		if(author == null || author.isBlank()) {
			return new String[0];
		}
		String authorStr = author.trim();
		Map<String, String[]> perLang = AUTHORS.getOrDefault(authorStr, Map.of());
		String lang = article.getLang();
		return Stream.concat(Stream.of(lang), perLang.keySet().stream().filter(not(lang::equals)))
			.map(perLang::get)
			.filter(Objects::nonNull)
			.findFirst()
			.orElse(new String[] {authorStr});
	}

	private static void createItem(BufferedWriter writer, String name, String value) throws IOException {
		if (value == null) {
			return;
		}
		writer.write(name);
		writer.write(": ");
		writer.write(value);
		writer.newLine();
	}

	private static void createList(BufferedWriter writer, String name, String... values) throws IOException {
		if (values.length == 0 || Arrays.asList(values).contains(null)) {
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

	private static final Map<String, Map<String, String[]>> AUTHORS = readAuthorsMappings();

	private static Map<String, Map<String, String[]>> readAuthorsMappings() {
		try (var lines = Files.lines(Path.of("./authors - authors.tsv")).skip(1)) {
			return lines.map(s -> s.split("\t", 5))
				.map(row -> Map.entry(row[0], authorsByLang(Map.of("by", row[1], "pl", row[2], "en", row[3], "ru", row[4]))))
				.collect(Collectors.toMap(Entry::getKey, Entry::getValue));
		} catch (Exception e) {
			log.error("Can't read authors mappings", e);
			return Map.of();
		}
	}

	private static Map<String, String[]> authorsByLang(Map<String, String> authorsByLang) {
		return authorsByLang.entrySet().stream().map(entry -> {
				String value = entry.getValue();
				if (value.isBlank()) {
					return null;
				}
				return Map.entry(entry.getKey(), value.split(","));
			})
			.filter(Objects::nonNull)
			.collect(Collectors.toMap(Entry::getKey, Entry::getValue));
	}

}

