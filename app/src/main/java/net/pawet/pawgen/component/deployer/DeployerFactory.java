package net.pawet.pawgen.component.deployer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.Collection;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
public final class DeployerFactory {

	private final URI url;
	private final String accessToken;
	private final String siteId;

	public Consumer<Collection<? extends FileDigestData>> create(Type deployer) {
		return switch (deployer) {
			case NETLIFY -> new NetlifyDeployer(url, accessToken, siteId)::deploy;
			case CLOUDFLARE_PAGES -> new CloudflarePagesDeployer(url, accessToken, siteId, null)::deploy;
			case NONE -> __ -> {
			};
		};
	}

	public enum Type {
		NETLIFY,
		CLOUDFLARE_PAGES,
		NONE;

		public static Type from(String deployerType) {
			for (Type value : Type.values()) {
				if (value.name().equalsIgnoreCase(deployerType)) {
					return value;
				}
			}
			throw new IllegalArgumentException("Unknown deployer: " + deployerType);
		}
	}
}
