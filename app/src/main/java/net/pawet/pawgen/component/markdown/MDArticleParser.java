package net.pawet.pawgen.component.markdown;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Article;
import net.pawet.pawgen.component.Category;
import net.pawet.pawgen.component.system.storage.ArticleResource;
import org.commonmark.ext.front.matter.YamlFrontMatterBlock;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterNode;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.image.attributes.ImageAttributesExtension;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.joining;
import static lombok.AccessLevel.PROTECTED;

@Slf4j
@RequiredArgsConstructor
public final class MDArticleParser {

	private final Parser parser;
	private final HtmlRenderer renderer;

	public static MDArticleParser of(BiFunction<Category, Map<String, String>, Map<String, String>> attrsProcessor) {
		var extensions = List.of(YamlFrontMatterExtension.create(), ImageAttributesExtension.create(), TablesExtension.create());
		return new MDArticleParser(
			Parser.builder().extensions(extensions).build(),
			HtmlRenderer.builder().extensions(extensions)
				.attributeProviderFactory(context -> (node, tagName, attrs) -> {
					if (node instanceof Image || node instanceof Link) {
						attrs.putAll(attrsProcessor.apply(AttachedData.findCategory(node), attrs));
					}
				})
				.build()
		);
	}

	@SneakyThrows
	public Article parse(ArticleResource resource) {
		var category = resource.getCategory();
		log.info("Parsing category '{}'", category);
		var readable = resource.readable();
		String extLang = resource.getLanguage();
		var articleBuilder = parse(readable, category, extLang);
		var article = articleBuilder.resource(resource).build();
		if (!article.getLang().equals(extLang)) {
			throw new IllegalStateException("Externally provided language hint '%s' is not matching article '%s'".formatted(article.getLang(), article));
		}
		return article;
	}

	Article.ArticleBuilder parse(ReadableByteChannel readable, Category category, String extLang) throws IOException {
		var document = parseToDocument(readable);
		var articleBuilder = Article.builder().lang(extLang);
		if (!(document.getFirstChild() instanceof YamlFrontMatterBlock yfmb)) {
			throw new IllegalStateException("Frontmatter is mandatory");
		}

		populateArticleProperties(yfmb, articleBuilder);

		AttachedData.attach(category, document);
		articleBuilder.contentSupplier(() -> render(document));

		return articleBuilder;
	}

	private static void populateArticleProperties(YamlFrontMatterBlock block, Article.ArticleBuilder articleBuilder) {
		var node = block.getFirstChild();
		while (node instanceof YamlFrontMatterNode yfn) {
			populateArticleProperties(yfn, articleBuilder);
			node = node.getNext();
		}
	}

	private static void populateArticleProperties(YamlFrontMatterNode node, Article.ArticleBuilder articleBuilder) {
        var stream = node.getValues().stream();
		switch (node.getKey()) {
			case "language" -> stream.findAny().ifPresent(articleBuilder::lang);
			case "type" -> stream.findAny().ifPresent(articleBuilder::type);
			case "title" -> articleBuilder.title(stream.findAny().orElse(""));
			case "authors" ->  stream.collect(collectingAndThen(joining(","), articleBuilder::author));
			case "date" -> stream.findAny().map(date -> ISO_DATE_TIME.parse(date, ZonedDateTime::from)).ifPresent(articleBuilder::date);
			case "source" -> stream.findAny().ifPresent(articleBuilder::source);
			case "file" -> stream.findAny().ifPresent(articleBuilder::file);
			case "aliases" -> stream.forEachOrdered(articleBuilder::alias);
			default -> throw new IllegalArgumentException("Unknown article FrontMatter property: " + node.getKey());
		}
	}

	private Document parseToDocument(ReadableByteChannel readable) throws IOException {
		try (var reader = Channels.newReader(readable, UTF_8)) {
			return (Document) parser.parseReader(reader);
		}
	}

	CharSequence render(Document doc) {
		var sb = new StringBuilder();
		renderer.render(doc, sb);
		return sb;
	}
}

@RequiredArgsConstructor(access = PROTECTED)
final class AttachedData extends CustomNode {

	private final Category category;

	public static void attach(Category category, Document document) {
		document.appendChild(new AttachedData(category));
	}

	@Override
	public void accept(Visitor visitor) {
	}

	public static Category findCategory(Node node) {
		var parent = node.getParent();
		while (parent != null) {
			node = parent;
			parent = node.getParent();
		}
		var dataNode = node.getFirstChild();
		while (!(dataNode instanceof AttachedData data)) {
			if (dataNode == null) {
				throw new IllegalStateException("No AttachedData found for article");
			}
			dataNode = dataNode.getNext();
		}
		return data.category;
	}

}

