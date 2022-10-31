package net.pawet.pawgen.component;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.system.storage.ArticleResourceWrapper;
import net.pawet.pawgen.component.system.storage.AttachmentResource;

import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.Channels;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static lombok.AccessLevel.PRIVATE;

@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor(access = PRIVATE)
@Slf4j
public final class Article implements Comparable<Article> {

	@ToString.Include
	@EqualsAndHashCode.Include
	@NonNull
	private final ArticleResourceWrapper articleResource;
	@Getter
	@ToString.Include
	@NonNull
	private final String type;
	@Getter
	@ToString.Include
	@EqualsAndHashCode.Include
	@NonNull
	private final String lang;
	@Getter
	@ToString.Include
	@EqualsAndHashCode.Include
	@NonNull
	private final String title;
	@Getter
	private final String author;
	@Getter
	private final ZonedDateTime date;
	@Getter
	private final String source;
	@Getter
	@NonNull
	private final String url;

	public static Article of(ArticleResourceWrapper resource, String type, String lang, String title, String author, ZonedDateTime date, String source) {
		if (date == null) {
			date = ZonedDateTime.ofInstant(resource.getModificationDate(), ZoneOffset.UTC);
		}
		return new Article(resource, type, lang, title, author, date, source, resource.getUrl());
	}

	public Category getCategory(){
		return articleResource.getCategory();
	}

	@Override
	public int compareTo(Article o) {
		int categoryRes = getCategory().compareTo(o.getCategory());
		if (categoryRes != 0) {
			return categoryRes;
		}
		int langRes = lang.compareTo(o.lang);
		if (langRes != 0) {
			return langRes;
		}
		return title.compareTo(o.title);
	}

	public boolean isSameCategory(Article article) {
		return getCategory().equals(article.getCategory());
	}

	public boolean isSameLang(Article article) {
		return lang.equals(article.lang);
	}

	public boolean isChildFor(Article parent) {
		return getCategory().isChildFor(parent.getCategory());
	}

	public String relativize(String url) {
		if (url == null) {
			return null;
		}
		return getCategory().relativize(normalize(url));
	}

	private String normalize(String url) {
		try {
			return new URI(url).normalize().toASCIIString();
		} catch (URISyntaxException e) {
			//TODO: fix url in articles and make throw exception if invalid article
			log.warn("bad url '{}' for article: '{}'", url, title);
		}
		return url;
	}

	@SneakyThrows
	public CharSequence readContent() {
		return articleResource.readContent();
	}

	public Writer writer() {
		return Channels.newWriter(articleResource.writable(), UTF_8);
	}

	private Optional<AttachmentResource> getAttachment() {
		var attachment = articleResource.getAttachment();
		attachment.ifPresent(AttachmentResource::transfer);//copy file whe it is used
		return attachment;
	}
	public Optional<String> getAttachmentUri() {
		return getAttachment().map(AttachmentResource::getUri);
	}

	public Optional<String> getAttachmentType() {
		return getAttachment().map(AttachmentResource::getType);
	}

}
