package net.pawet.pawgen.component.deployer;

import lombok.experimental.Delegate;
import net.pawet.pawgen.component.system.storage.DigestAwareResource;
import net.pawet.pawgen.deployer.deployitem.Content;
import net.pawet.pawgen.deployer.deployitem.Digest;
import net.pawet.pawgen.deployer.deployitem.Path;
import net.pawet.pawgen.deployer.deployitem.Size;

import java.io.InputStream;
import java.nio.channels.Channels;

record DigestAwareResourceFile(@Delegate(types = {Path.class, Size.class}) DigestAwareResource resource, String digestName) implements Digest, Content, Path, Size {

	public static DigestAwareResourceFile netlify(DigestAwareResource resource) {
		return new DigestAwareResourceFile(resource, "sha1");
	}

	public static DigestAwareResourceFile cloudflare(DigestAwareResource resource) {
		return new DigestAwareResourceFile(resource, "cfdigest");
	}

	@Override
	public InputStream inputStream() {
		return Channels.newInputStream(resource.readable());
	}

	@Override
	public String getDigest() {
		return resource.getDigestBy(digestName).orElseThrow();
	}

}
