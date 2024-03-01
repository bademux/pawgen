package net.pawet.pawgen.component.render;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.Article;
import net.pawet.pawgen.component.Category;

import java.nio.file.FileAlreadyExistsException;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.Boolean.TRUE;
import static java.time.ZoneOffset.UTC;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.*;
import static lombok.AccessLevel.PRIVATE;

@Slf4j
@RequiredArgsConstructor(staticName = "of")
public class Renderer {

	private final Map<Article, Boolean> processed = new ConcurrentHashMap<>();
	private final Executor executor;
	private final Templater templater;
	private final Clock clock;
	private final ArticleQuery queryService;


	public ArticleContext create(Article header) {
		return new ArticleContext(header);
	}

	void renderAsync(ArticleContext context) {
		log.trace("Rendering {}", context.article);
		processed.computeIfAbsent(context.article, __ -> {
			executor.execute(() -> render(context));
			return TRUE;
		});
	}

	public Stream<Article> getProcessed() {
		return processed.keySet().stream();
	}

	@SneakyThrows
	private void render(ArticleContext context) {
		try (var writer = context.article.writer()) {
			templater.render(writer, context, (Callable<CharSequence>) context.article::readContent);
			log.debug("Rendering: {}", context.article);
		} catch (FileAlreadyExistsException e) {
			log.debug("Error while generating article {}.", context.article, e);
		} catch (Exception e) {
			log.error("Error while generating article {}.", context.article, e);
			throw e;
		}
	}

	@ToString(onlyExplicitlyIncluded = true)
	@EqualsAndHashCode(onlyExplicitlyIncluded = true)
	@RequiredArgsConstructor(access = PRIVATE)
	public final class ArticleContext {

		@ToString.Include
		@EqualsAndHashCode.Include
		@Getter
		private final Article article;

		public void renderAsync() {
			if (isLegacy()) {
				log.debug("legacy article, skipping {}", article);
				return;
			}
			log.trace("Rendering {}", article);
			Renderer.this.renderAsync(this);
		}

		Stream<ArticleContext> getOtherLangArticle() {
			return queryService.getArticles(article.getCategory())
				.filter(not(article::isSameLang))
				.map(Renderer.this::create)
				.peek(ArticleContext::renderAsync);

		}

		Stream<ArticleContext> getChildren() {
			return queryService.getChildren(article.getCategory())
				.map(Renderer.this::create)
				.peek(ArticleContext::renderAsync);
		}

		Stream<ArticleContext> getLatest() {
			return queryService.getLast(article.getCategory(), clock.instant().atZone(UTC), 6)
				.limit(6)
				.map(Renderer.this::create)
				.peek(ArticleContext::renderAsync);
		}

		Optional<ArticleContext> getParent() {
			return queryService.getParents(article.getCategory())
				.map(Renderer.this::create)
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

		Stream<String> getAliases() {
			return article.getAliases();
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
