package net.pawet.pawgen.component.netlify;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Slf4j
public record DeployerFactory(URI url, String accessToken, String siteId, boolean isEnabled) {

	public Consumer<Stream<FileDigestData>> create() {
		if (isEnabled) {
			return new NetlifyDeployer(url, accessToken, siteId)::deploy;
		}
		log.info("Netlify deployment disabled");
		return __ -> {
		};
	}

}
