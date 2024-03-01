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
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.AttributeProviderContext;
import org.commonmark.renderer.html.AttributeProviderFactory;
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
import static lombok.AccessLevel.PROTECTED;

@Slf4j
@RequiredArgsConstructor
public final class MDArticleParser {

	private final Parser parser;
	private final HtmlRenderer renderer;

	public static MDArticleParser of(BiFunction<Category, Map<String, String>, Map<String, String>> imageProcessor,
									 BiFunction<Category, Map<String, String>, Map<String, String>> linkProcessor) {
		var extensions = List.of(YamlFrontMatterExtension.create(), ImageAttributesExtension.create(), TablesExtension.create());
		return new MDArticleParser(
			Parser.builder().extensions(extensions).build(),
			HtmlRenderer.builder().extensions(extensions).attributeProviderFactory(new ResourceAttributeProviderFactory(imageProcessor, linkProcessor)).build()
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
		switch (node.getKey()) {
			case "language" -> articleBuilder.lang(node.getValues().get(0));
			case "type" -> articleBuilder.type(node.getValues().get(0));
			case "title" -> articleBuilder.title(node.getValues().get(0));
			case "author" -> articleBuilder.author(node.getValues().get(0));
			case "date" -> articleBuilder.date(ISO_DATE_TIME.parse(node.getValues().get(0), ZonedDateTime::from));
			case "source" -> articleBuilder.source(node.getValues().get(0));
			case "file" -> articleBuilder.file(node.getValues().get(0));
			case "aliases" -> articleBuilder.aliases(node.getValues());
			default -> throw new IllegalArgumentException("Unknown article Frontmatter property: " + node.getKey());
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
final class ResourceAttributeProviderFactory implements AttributeProviderFactory {

	private final BiFunction<Category, Map<String, String>, Map<String, String>> imageResourceProcessor;
	private final BiFunction<Category, Map<String, String>, Map<String, String>> linkResourceProcessor;

	private void attributeProviderSetAttributes(Node node, String tagName, Map<String, String> attrs) {
		switch (node) {
			case Image image -> attrs.putAll(imageResourceProcessor.apply(AttachedData.findCategory(image), attrs));
			case Link link -> attrs.putAll(linkResourceProcessor.apply(AttachedData.findCategory(link), attrs));
			default -> {
			}
		}
	}

	@Override
	public AttributeProvider create(AttributeProviderContext __) {
		return this::attributeProviderSetAttributes;
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

