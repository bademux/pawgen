package net.pawet.pawgen.component.system.storage;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import net.pawet.pawgen.component.Category;

import java.nio.channels.WritableByteChannel;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

import static lombok.AccessLevel.PROTECTED;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor(access = PROTECTED)
public final class ArticleResourceWrapper {

	@ToString.Include
	@EqualsAndHashCode.Include
	private final ArticleResource resource;
	private final Supplier<CharSequence> readable;
	@ToString.Include
	private final String file;
	@ToString.Include
	@EqualsAndHashCode.Include
	private final String title;

	public static ArticleResourceWrapper of(ArticleResource resource, String title, String file, Supplier<CharSequence> contentSupplier) {
		return new ArticleResourceWrapper(resource, contentSupplier, file, title);
	}


	public CharSequence readContent() {
		return readable.get();
	}

	public WritableByteChannel writable() {
		return resource.writableFor(title);
	}

	public Optional<AttachmentResource> getAttachment() {
		return resource.attachment(file);
	}

	public Instant getModificationDate() {
		return resource.getModificationDate();
	}

	public String getUrl() {
		return resource.urlFor(title);
	}

	public Category getCategory() {
		return resource.getCategory();
	}

}
