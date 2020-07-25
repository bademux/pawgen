package net.pawet.pawgen.component.render;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import net.pawet.pawgen.component.ArticleHeader;

import java.io.Writer;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.*;
import static lombok.AccessLevel.PACKAGE;
import static lombok.AccessLevel.PRIVATE;

@RequiredArgsConstructor(access = PACKAGE)
final class RenderContext {

	private static final List<String> SUPPORTED_LANGS = List.of("by", "en", "pl", "ru");

	private final BiConsumer<Writer, Map<String, ?>> templater;
	private final Collection<ArticleHeader> articleHeaders;
	private final Function<ArticleHeader, CharSequence> contentProvider;

	public Stream<ProcessingItem> handle() {
		return articleHeaders.stream().map(MapWrapper::new);
	}

	@RequiredArgsConstructor(access = PRIVATE)
	final class MapWrapper extends AbstractMap<String, Object> implements ProcessingItem {

		private final ArticleHeader currentHeader;
		private final ArticleHeader contextHeader;

		public MapWrapper(ArticleHeader header) {
			this(header, null);
		}

		public MapWrapper createMapWrapper(ArticleHeader header) {
			return new MapWrapper(header, currentHeader);
		}

		@Override
		public final Object get(Object key) {
			String propName = (String) key;
			switch (propName) {
				case "children":
					return getChildren().map(this::createMapWrapper).iterator();
				case "otherLangArticle":
					return getOtherLangArticle().map(this::createMapWrapper).iterator();
				case "func.relativize":
					return (Function<String, CharSequence>) (contextHeader == null ? currentHeader : contextHeader)::relativize;
				default:
			}
			String parentPrefix = "parent.";
			if (propName.startsWith(parentPrefix)) {
				return getParentItem().get(propName.substring(parentPrefix.length()));
			}

			return currentHeader.get(propName);
		}

		@Override
		public final boolean containsKey(Object key) {
			return get(key) != null;
		}

		@Override
		public String toString() {
			return contentProvider.apply(currentHeader).toString();
		}

		final Stream<ArticleHeader> getOtherLangArticle() {
			return articleHeaders.stream()
				.filter(currentHeader::isSameCategory)
				.filter(not(currentHeader::isSameLang));
		}

		final Stream<ArticleHeader> getChildren() {
			return articleHeaders.stream()
				.filter(currentHeader::isParentFor)
				.collect(groupingBy(ArticleHeader::getCategory))
				.values()
				.stream()
				.map(this::chooseTheBestSuitableLang)
				.sorted();
		}

		final ArticleHeader getParentItem() {
			return articleHeaders.stream()
				.filter(currentHeader::isChildFor)
				.collect(collectingAndThen(toList(), this::chooseTheBestSuitableLang));
		}

		private ArticleHeader chooseTheBestSuitableLang(List<ArticleHeader> articleHeaders) {
			if (articleHeaders.size() == 1) {
				return articleHeaders.get(0);
			}
			var articleByLang = articleHeaders.stream().collect(toMap(ArticleHeader::getLang, Function.identity()));
			ArticleHeader ah = articleByLang.get(currentHeader.getLang());
			if (ah != null) {
				return ah;
			}
			return SUPPORTED_LANGS.stream()
				.map(articleByLang::get)
				.filter(Objects::nonNull)
				.findAny()
				.orElseThrow(() -> new IllegalStateException("Article " + currentHeader + " have no parent"));
		}

		@Override
		public final Set<Entry<String, Object>> entrySet() {
			throw new UnsupportedOperationException("this map should me used as Mustache.java scope only");
		}

		@Override
		public String getPrintableName() {
			return currentHeader.toString();
		}

		@SneakyThrows
		@Override
		public void writeWith(Function<ArticleHeader, Writer> writerFactory) {
			try (var writer = writerFactory.apply(currentHeader)) {
				templater.accept(writer, this);
			}
		}
	}
}
