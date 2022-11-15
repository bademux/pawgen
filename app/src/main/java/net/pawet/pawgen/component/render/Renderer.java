package net.pawet.pawgen.component.render;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Synchronized;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Article;
import net.pawet.pawgen.component.Category;

import java.nio.file.FileAlreadyExistsException;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;

import static java.time.ZoneOffset.UTC;
import static java.util.Comparator.comparing;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.*;
import static lombok.AccessLevel.PRIVATE;

@Slf4j
@RequiredArgsConstructor(staticName = "of")
public class Renderer {

	private final Set<Article> processedFiles = ConcurrentHashMap.newKeySet();
	private final Templater templater;
	private final Clock clock;
	private final ArticleQuery queryService;
	private final Executor executor;

	public ArticleContext create(Article header) {
		return new ArticleContext(header);
	}

	void render(ArticleContext context) {
		try (var writer = context.article.writer()) {
			templater.render(writer, context, context.article.readContent());
			log.debug("Rendering: {}", context);
		} catch (FileAlreadyExistsException e) {
			log.debug("Error while generating article {}.", context, e);
		}catch (Exception e) {
			log.error("Error while generating article {}.", context, e);
		}
	}

	@ToString(onlyExplicitlyIncluded = true)
	@RequiredArgsConstructor(access = PRIVATE)
	public final class ArticleContext {

		@ToString.Include
		@Getter
		private final Article article;

		@Synchronized
		public void render() {
			if (isLegacy()) {
				log.debug("legacy article, skipping {}", article);
				return;
			}
			if (processedFiles.contains(article)) {
				log.trace("Already processed, skipping {}", article);
				return;
			}
			executor.execute(() -> Renderer.this.render(this));
			processedFiles.add(article);
		}

		Iterator<ArticleContext> getOtherLangArticle() {
			return queryService.getArticles(article.getCategory())
				.filter(not(article::isSameLang))
				.map(Renderer.this::create)
				.peek(ArticleContext::render)
				.iterator();

		}

		Iterator<ArticleContext> getChildren() {
			return queryService.getChildren(article.getCategory())
				.map(Renderer.this::create)
				.peek(ArticleContext::render)
				.collect(groupingBy(ArticleContext::getCategory, LinkedHashMap::new, toList()))
				.values().stream()
				.map(this::chooseTheBestSuitableLang)
				.flatMap(Optional::stream)
				.iterator();
		}

		Iterator<ArticleContext> getLatest() {
			return queryService.getLast(article.getCategory(), clock.instant().atZone(UTC), 10)
				.map(Renderer.this::create)
				.peek(ArticleContext::render)
				.collect(groupingBy(ArticleContext::getCategory, LinkedHashMap::new, toList()))
				.values().stream()
				.map(this::chooseTheBestSuitableLang)
				.flatMap(Optional::stream)
				.limit(6)
				.iterator();
		}

		Optional<ArticleContext> getParent() {
			return queryService.getParents(article.getCategory())
				.map(Renderer.this::create)
				.peek(ArticleContext::render)
				.collect(collectingAndThen(toList(), this::chooseTheBestSuitableLang));
		}

		String relativize(String value) {
			return article.relativize(value);
		}

		String getUrl() {
			return article.getUrl();
		}

		String getAuthor() {
			return article.getAuthor();
		}

		Category getCategory() {
			return article.getCategory();
		}

		ZonedDateTime getDate() {
			return article.getDate();
		}

		String getFile() {
			return article.getAttachmentUri();
		}

		String getFileExt() {
			return article.getAttachmentType();
		}

		String getLang() {
			return article.getLang();
		}

		String getSource() {
			return article.getSource();
		}

		String getTitle() {
			return article.getTitle();
		}

		String getType() {
			return article.getType();
		}

		boolean isLegacy() {
			var title = article.getTitle();
			var category = article.getCategory();
			return title.isBlank() || ".".equals(title) || category.endsWith("_space") || category.endsWith("_aspace");
		}

		private Optional<ArticleContext> chooseTheBestSuitableLang(List<ArticleContext> articleHeaders) {
			if (articleHeaders.size() < 2) {
				return articleHeaders.stream().findAny();
			}

			var articleByLang = articleHeaders.stream().collect(toMap(ArticleContext::getLang, Function.identity()));
			var ah = articleByLang.get(article.getLang());
			if (ah != null) {
				return Optional.of(ah);
			}
			return SUPPORTED_LANGS.stream().map(articleByLang::get).filter(Objects::nonNull).findAny();
		}

	}

	private static final List<String> SUPPORTED_LANGS = List.of("by", "pl", "ru", "en");
}
