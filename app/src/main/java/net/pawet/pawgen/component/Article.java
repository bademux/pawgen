package net.pawet.pawgen.component;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.system.storage.ArticleResource;
import net.pawet.pawgen.component.system.storage.AttachmentResource;

import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.Channels;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

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
	private final ArticleResource resource;
	@NonNull
	private final Supplier<CharSequence> readable;
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
	@ToString.Include
	@EqualsAndHashCode.Include
	private final String author;
	@ToString.Include
	@EqualsAndHashCode.Include
	private final ZonedDateTime date;
	@Getter
	@ToString.Include
	@EqualsAndHashCode.Include
	private final String source;
	@Getter(value = PRIVATE, lazy = true)
	private final AttachmentResource attachment = initAttachment();
	@Getter
	@ToString.Include
	@EqualsAndHashCode.Include
	private final String file;
	private final Collection<String> aliases;

	public static Article of(ArticleResource resource, Supplier<CharSequence> contentSupplier, String type, String lang, String title, String author, ZonedDateTime date, String source, String file, Collection<String> aliases) {
		return new Article(resource, contentSupplier, type, lang, title, author, date, source, file, aliases);
	}

	public Category getCategory(){
		return resource.getCategory();
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
		return readable.get();
	}

	public Writer writer() {
		return Channels.newWriter(resource.writableFor(title), UTF_8);
	}

	private AttachmentResource initAttachment() {
		var attachment = resource.attachment(file);
		if (attachment.isEmpty()) {
			return null;
		}
		var attRes = attachment.get();
		attRes.transfer();
		return attRes;
	}

	public String getAttachmentUri() {
		var attachment = getAttachment();
		return attachment == null ? null : attachment.getUri();
	}

	public String getAttachmentType() {
		var attachment = getAttachment();
		return attachment == null ? null : attachment.getType();
	}

	public ZonedDateTime getDate() {
		if (date == null) {
			return ZonedDateTime.ofInstant(resource.getModificationDate(), ZoneOffset.UTC);
		}
		return date;
	}

	public String getUrl() {
		return resource.urlFor(title);
	}

	public Stream<String> getAliases() {
		return aliases.stream();
	}

}
