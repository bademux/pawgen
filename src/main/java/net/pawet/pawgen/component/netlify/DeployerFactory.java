package net.pawet.pawgen.component.netlify;

import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.system.storage.DigestAwareResource;

import java.net.URI;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Slf4j
public record DeployerFactory(URI url, String accessToken, String siteId) {

	public Consumer<Stream<DigestAwareResource>> create(boolean isEnabled) {
		if (isEnabled) {
			return new NetlifyDeployer<DigestAwareResource>(url, accessToken, siteId)::deploy;
		}
		log.info("Netlify deployment disabled");
		return __ -> {
		};
	}

}
