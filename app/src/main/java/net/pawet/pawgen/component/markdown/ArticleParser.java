package net.pawet.pawgen.component.markdown;

import com.vladsch.flexmark.ast.Image;
import com.vladsch.flexmark.ast.LinkNodeBase;
import com.vladsch.flexmark.ext.attributes.AttributesExtension;
import com.vladsch.flexmark.ext.yaml.front.matter.AbstractYamlFrontMatterVisitor;
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension;
import com.vladsch.flexmark.html.AttributeProvider;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.html.IndependentAttributeProviderFactory;
import com.vladsch.flexmark.html.renderer.AttributablePart;
import com.vladsch.flexmark.html.renderer.LinkResolverContext;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.DataKey;
import com.vladsch.flexmark.util.data.NullableDataKey;
import com.vladsch.flexmark.util.html.Attribute;
import com.vladsch.flexmark.util.html.MutableAttributes;
import com.vladsch.flexmark.util.misc.Extension;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Article;
import net.pawet.pawgen.component.Category;
import net.pawet.pawgen.component.system.storage.ArticleResource;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;

@Slf4j
@RequiredArgsConstructor
public class ArticleParser {

	private static final List<? extends Extension> EXTENSIONS = List.of(YamlFrontMatterExtension.create(), AttributesExtension.create());

	private final Parser parser = Parser.builder().extensions(EXTENSIONS).build();
	private final HtmlRenderer renderer = HtmlRenderer.builder().extensions(EXTENSIONS)
		.attributeProviderFactory(new IndependentAttributeProviderFactory() {
			@Override
			public AttributeProvider apply(LinkResolverContext __) {
				return ArticleParser.this::attributeProviderSetAttributes;
			}
		})
		.build();

	private final BiFunction<Category, Map<String, String>, Map<String, String>> imageResourceProcessor;
	private final BiFunction<Category, Map<String, String>, Map<String, String>> linkResourceProcessor;

	private void attributeProviderSetAttributes(Node node, AttributablePart __, MutableAttributes attributes) {
		if (node instanceof Image) {
			handle(imageResourceProcessor, node, attributes);
		} else if (node instanceof LinkNodeBase) {
			handle(linkResourceProcessor, node, attributes);
		}
	}

	private void handle(BiFunction<Category, Map<String, String>, Map<String, String>> resourceProcessor, Node node, MutableAttributes attributes) {
		var attrs = attributes.values().stream().collect(toMap(Attribute::getName, Attribute::getValue));
		Category category = CATEGORY_DATA_KEY.get(node.getDocument());
		resourceProcessor.apply(category, attrs).forEach(attributes::replaceValue);
	}

	private static final NullableDataKey<Category> CATEGORY_DATA_KEY = new NullableDataKey<>("category");
	private static final NullableDataKey<String> LANG_DATA_KEY = new NullableDataKey<>("language");
	private static final DataKey<String> TYPE_DATA_KEY = new DataKey<>("type", "article");
	private static final NullableDataKey<String> TITLE_DATA_KEY = new NullableDataKey<>("title");
	private static final NullableDataKey<String> AUTHOR_DATA_KEY = new NullableDataKey<>("author");
	private static final NullableDataKey<ZonedDateTime> DATE_DATA_KEY = new NullableDataKey<>("date");
	private static final NullableDataKey<String> SOURCE_DATA_KEY = new NullableDataKey<>("source");
	private static final NullableDataKey<String> FILE_DATA_KEY = new NullableDataKey<>("file");
	private static final DataKey<List<String>> ALIASES_DATA_KEY = new DataKey<>("aliases", List.of());

	@SneakyThrows
	public Article parse(ArticleResource resource) {
		var category = resource.getCategory();
		log.info("Parsing category '{}'", category);
		var document = parseToDocument(resource.readable(), category, resource.getLanguage());
		return Article.of(resource, () -> readContent(document),
			TYPE_DATA_KEY.get(document), LANG_DATA_KEY.get(document),
			requireNonNull(TITLE_DATA_KEY.get(document), "No title for article in category " + category),
			AUTHOR_DATA_KEY.get(document), DATE_DATA_KEY.get(document), SOURCE_DATA_KEY.get(document),
			FILE_DATA_KEY.get(document), ALIASES_DATA_KEY.get(document)
		);
	}

	private static Map<String, List<String>> readFrontMatter(Document node) {
		var visitor = new AbstractYamlFrontMatterVisitor();
		visitor.visit(node);
		return visitor.getData();
	}

	final Document parseToDocument(@NonNull ReadableByteChannel readable, @NonNull Category category, String lang) throws IOException {
		var document = parseToDocument(readable);
		CATEGORY_DATA_KEY.set(document, category);
		Map<String, List<String>> data = readFrontMatter(document);
		getFrom(data, LANG_DATA_KEY.getName())
			.findFirst()
			.or(() -> ofNullable(lang))
			.ifPresent(value -> document.set(LANG_DATA_KEY, value));
		getFrom(data, TYPE_DATA_KEY.getName()).findFirst().ifPresent(value -> document.set(TYPE_DATA_KEY, value));
		getFrom(data, TITLE_DATA_KEY.getName()).findFirst().ifPresent(value -> document.set(TITLE_DATA_KEY, value));
		getFrom(data, AUTHOR_DATA_KEY.getName()).findFirst().ifPresent(value -> document.set(AUTHOR_DATA_KEY, value));
		getFrom(data, DATE_DATA_KEY.getName()).findFirst()
			.filter(not(String::isBlank))
			.map(DateTimeFormatter.ISO_LOCAL_DATE::parse)
			.map(temporalAccessor -> temporalAccessor.query(ZonedDateTime::from))
			.ifPresent(value -> document.set(DATE_DATA_KEY, value));
		getFrom(data, SOURCE_DATA_KEY.getName()).findFirst().ifPresent(value -> document.set(SOURCE_DATA_KEY, value));
		getFrom(data, FILE_DATA_KEY.getName()).findFirst().ifPresent(value -> document.set(FILE_DATA_KEY, value));
		ALIASES_DATA_KEY.set(document, getFrom(data, "aliases")
			.filter(not(String::isBlank))
			.distinct()
			.toList());
		return document;
	}

	private Document parseToDocument(ReadableByteChannel readable) throws IOException {
		try (var reader = Channels.newReader(readable, UTF_8)) {
			return parser.parseReader(reader);
		}
	}

	private static Stream<String> getFrom(Map<String, List<String>> data, String name) {
		return data.getOrDefault(name, List.of()).stream();
	}

	final CharSequence readContent(Document doc) {
		StringBuilder sb = new StringBuilder();
		renderer.render(doc, sb);
		return sb;
	}

}
