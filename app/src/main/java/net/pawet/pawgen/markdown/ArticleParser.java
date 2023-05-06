package net.pawet.pawgen.markdown;

import com.vladsch.flexmark.ext.attributes.AttributesExtension;
import com.vladsch.flexmark.ext.yaml.front.matter.AbstractYamlFrontMatterVisitor;
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension;
import com.vladsch.flexmark.html.AttributeProvider;
import com.vladsch.flexmark.html.AttributeProviderFactory;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.html.renderer.LinkResolverContext;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.misc.Extension;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Article;
import net.pawet.pawgen.component.resource.ResourceProcessor;
import net.pawet.pawgen.component.system.storage.ArticleResource;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;

@Slf4j
@RequiredArgsConstructor
public class ArticleParser {

	private static final List<? extends Extension> EXTENSIONS = List.of(
		YamlFrontMatterExtension.create(),
		AttributesExtension.create()
	);

	private final Parser parser = Parser.builder().extensions(EXTENSIONS).build();
	private final HtmlRenderer renderer = HtmlRenderer.builder().extensions(EXTENSIONS)
		.attributeProviderFactory(new AttributeProviderFactory() {
			@Override
			public  Set<Class<?>> getAfterDependents() {
				return null;
			}

			@Override
			public  Set<Class<?>> getBeforeDependents() {
				return null;
			}

			@Override
			public boolean affectsGlobalScope() {
				return false;
			}

			@Override
			public  AttributeProvider apply( LinkResolverContext context) {
				return null;
			}
		})
		.build();

	private final Function<ResourceProcessor.ProcessingItem, Map<String, String>> resourceFactory;

	@SneakyThrows
	public Article parse(ArticleResource resource) {
		var category = resource.getCategory();
		log.info("Parsing category '{}'", category);
		var node = parseToDocument(resource);
		Map<String, List<String>> data = readFrontMatter(node);
		String type = getFrom(data, "type").findFirst().orElse("article");
		String lang = resource.getLanguage();
		String title = getFrom(data, "title").findFirst().orElseThrow();
		String author = getFrom(data, "author").findFirst().orElse(null);
		ZonedDateTime date = getFrom(data, "date").findFirst()
			.filter(not(String::isBlank))
			.map(DateTimeFormatter.ISO_LOCAL_DATE::parse)
			.map(temporalAccessor -> temporalAccessor.query(ZonedDateTime::from))
			.orElse(null);
		String source = getFrom(data, "source").findFirst()
			.filter(not(String::isBlank))
			.orElse(null);
		String file = getFrom(data, "file").findFirst()
			.filter(not(String::isBlank))
			.orElse(null);
		List<String> aliases = getFrom(data, "aliases")
			.filter(not(String::isBlank))
			.distinct()
			.toList();



		return Article.of(resource, () -> readContent(node), type, lang, title, author, date, source, file, aliases);
	}

	private static Map<String, List<String>> readFrontMatter(Document node) {
		var visitor = new AbstractYamlFrontMatterVisitor();
		visitor.visit(node);
		return visitor.getData();
	}

	private Document parseToDocument(ArticleResource resource) throws IOException {
		try (var reader = Channels.newReader(resource.readable(), StandardCharsets.UTF_8)) {
			return parser.parseReader(reader);
		}
	}

	private static Stream<String> getFrom(Map<String, List<String>> data, String name) {
		return data.getOrDefault(name, List.of()).stream();
	}

	private CharSequence readContent(Document doc) {
		StringBuilder sb = new StringBuilder();
		renderer.render(doc, sb);
		return sb;
	}

}
