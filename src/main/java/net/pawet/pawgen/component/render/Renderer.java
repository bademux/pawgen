package net.pawet.pawgen.component.render;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.ArticleHeader;
import net.pawet.pawgen.component.system.storage.Storage;

import java.io.OutputStreamWriter;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.*;
import static lombok.AccessLevel.PACKAGE;
import static lombok.AccessLevel.PRIVATE;

@Slf4j
@RequiredArgsConstructor(access = PACKAGE)
public class Renderer {

	private final Set<ArticleHeader> processedFiles = ConcurrentHashMap.newKeySet();
	private final Templater templater;
	private final ArticleHeaderQuery queryService;
	private final Executor executor;
	private final Function<ArticleHeader, CharSequence> contentProvider;
	private final Storage storage;

	public static Renderer of(Templater templater, ArticleHeaderQuery queryService, Executor executor, Function<ArticleHeader, CharSequence> contentProvider, Storage storage) {
		return new Renderer(templater, queryService, executor, contentProvider, storage);
	}

	public ArticleContext create(ArticleHeader header) {
		return new ArticleContext(header);
	}

	public void render(ArticleContext context) {
		boolean isNewOrChanged = storage.isNewOrChanged(context.header.getCategory().toString());
		Callable<CharSequence> contentProvider = isNewOrChanged? createContentProvider(context) : ""::toString;
		try (var writer = isNewOrChanged ? new OutputStreamWriter(storage.write(context.getUrl()), UTF_8) : OutputStreamWriter.nullWriter()) {
			@Cleanup var ignore = templater.render(writer, context, contentProvider);
			log.debug("Rendering: {}", context);
		} catch (Exception e) {
			log.error("Error while generating article {}.", context, e);
		}
	}

	private Callable<CharSequence> createContentProvider(ArticleContext context) {
		return () -> contentProvider.apply(context.header);
	}

	@ToString(onlyExplicitlyIncluded = true)
	@RequiredArgsConstructor(access = PRIVATE)
	public final class ArticleContext {

		@ToString.Include
		@Getter
		private final ArticleHeader header;

		@Synchronized
		public void render() {
			if (processedFiles.contains(header)) {
				log.trace("Already processed, skipping {}", header);
				return;
			}
			Renderer.this.render(this);
			processedFiles.add(header);
		}

		private void renderInternal() {
			if (isLegacy()) {
				log.debug("legacy article, skipping {}", header);
				return;
			}
			if (processedFiles.contains(header)) {
				log.trace("Already processed, skipping {}", header);
				return;
			}
			executor.execute(this::render);
		}

		Iterator<ArticleContext> getOtherLangArticle() {
			return queryService.get(header.getCategory())
				.filter(not(header::isSameLang))
				.map(Renderer.this::create)
				.peek(ArticleContext::renderInternal)
				.iterator();

		}

		Iterator<ArticleContext> getChildren() {
			return queryService.getChildren(header.getCategory())
				.map(Renderer.this::create)
				.peek(ArticleContext::renderInternal)
				.collect(groupingBy(ArticleContext::getCategory, TreeMap::new, toList())).values().stream()
				.map(this::chooseTheBestSuitableLang)
				.flatMap(Optional::stream)
				.iterator();
		}

		Optional<ArticleContext> getParent() {
			var parentCategory = header.getCategory().getParent();
			if (parentCategory == null) {
				return Optional.empty();
			}
			return queryService.get(parentCategory)
				.map(Renderer.this::create)
				.peek(ArticleContext::renderInternal)
				.collect(collectingAndThen(toList(), this::chooseTheBestSuitableLang));
		}

		String relativize(String value) {
			return header.relativize(value);
		}

		String getUrl() {
			return header.getUrl();
		}

		String getAuthor() {
			return header.getAuthor();
		}

		String getCategory() {
			return header.getCategory().toString();
		}

		String getDate() {
			return header.getDate();
		}

		String getFile() {
			return header.getFile();
		}

		String getFileExt() {
			return header.getFileExt();
		}

		String getLang() {
			return header.getLang();
		}

		String getSource() {
			return header.getSource();
		}

		String getTitle() {
			return header.getTitle();
		}

		String getType() {
			return header.getType();
		}

		boolean isLegacy() {
			var title = header.getTitle();
			var category = header.getCategory();
			return title.isBlank() || ".".equals(title) || category.endsWith("_space") || category.endsWith("_aspace");
		}

		private Optional<ArticleContext> chooseTheBestSuitableLang(List<ArticleContext> articleHeaders) {
			if (articleHeaders.size() == 0) {
				return Optional.empty();
			}
			if (articleHeaders.size() == 1) {
				return Optional.of(articleHeaders.get(0));
			}
			var articleByLang = articleHeaders.stream().collect(toMap(ArticleContext::getLang, Function.identity()));
			var ah = articleByLang.get(header.getLang());
			if (ah != null) {
				return Optional.of(ah);
			}
			return SUPPORTED_LANGS.stream()
				.map(articleByLang::get)
				.filter(Objects::nonNull)
				.findAny();
		}
	}

	private static final List<String> SUPPORTED_LANGS = List.of("by", "en", "pl", "ru");
}
