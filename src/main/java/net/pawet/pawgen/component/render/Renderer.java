package net.pawet.pawgen.component.render;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Synchronized;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Article;
import net.pawet.pawgen.component.Category;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.*;
import static lombok.AccessLevel.PRIVATE;

@Slf4j
@RequiredArgsConstructor(staticName = "of")
public class Renderer {

	private final Set<Article> processedFiles = ConcurrentHashMap.newKeySet();
	private final Templater templater;
	private final ArticleHeaderQuery queryService;
	private final Executor executor;

	public ArticleContext create(Article header) {
		return new ArticleContext(header);
	}

	void render(ArticleContext context) {
		try (var writer = context.header.writer()) {
			templater.render(writer, context, context.header.readContent());
			log.debug("Rendering: {}", context);
		} catch (Exception e) {
			log.error("Error while generating article {}.", context, e);
		}
	}

	@ToString(onlyExplicitlyIncluded = true)
	@RequiredArgsConstructor(access = PRIVATE)
	public final class ArticleContext {

		@ToString.Include
		@Getter
		private final Article header;

		@Synchronized
		public void render() {
			if (isLegacy()) {
				log.debug("legacy article, skipping {}", header);
				return;
			}
			if (processedFiles.contains(header)) {
				log.trace("Already processed, skipping {}", header);
				return;
			}
			executor.execute(() -> Renderer.this.render(this));
			processedFiles.add(header);
		}

		Iterator<ArticleContext> getOtherLangArticle() {
			return queryService.get(header.getCategory()).filter(not(header::isSameLang)).map(Renderer.this::create).peek(ArticleContext::render).iterator();

		}

		Iterator<ArticleContext> getChildren() {
			return queryService.getChildren(header.getCategory()).map(Renderer.this::create).peek(ArticleContext::render).collect(groupingBy(ArticleContext::getCategory, TreeMap::new, toList())).values().stream().map(this::chooseTheBestSuitableLang).flatMap(Optional::stream).iterator();
		}

		Optional<ArticleContext> getParent() {
			var parentCategory = header.getCategory().getParent();
			if (parentCategory == null) {
				return Optional.empty();
			}
			return queryService.get(parentCategory).map(Renderer.this::create).peek(ArticleContext::render).collect(collectingAndThen(toList(), this::chooseTheBestSuitableLang));
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

		Category getCategory() {
			return header.getCategory();
		}

		ZonedDateTime getDate() {
			return header.getDate();
		}

		String getFile() {
			return header.getAttachmentUri().orElse(null);
		}

		String getFileExt() {
			return header.getAttachmentType().orElse(null);
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
			return SUPPORTED_LANGS.stream().map(articleByLang::get).filter(Objects::nonNull).findAny();
		}

	}

	private static final List<String> SUPPORTED_LANGS = List.of("by", "en", "pl", "ru");
}
