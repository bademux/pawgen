package net.pawet.pawgen.component.system.storage;

import lombok.*;
import lombok.experimental.Delegate;
import net.pawet.pawgen.component.netlify.FileData;
import net.pawet.pawgen.component.netlify.FileDigest;

import static net.pawet.pawgen.component.system.storage.Sha1DigestService.encode;

@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class DigestAwareResource implements FileData, FileDigest {

	@Getter
	private final String digest;
	@Delegate(types = FileData.class)
	private final Resource resource;

	static DigestAwareResource of(byte[] digest, Resource resource) {
		return new DigestAwareResource(encode(digest), resource);
	}

}
