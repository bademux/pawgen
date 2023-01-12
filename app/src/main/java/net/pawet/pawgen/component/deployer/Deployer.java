package net.pawet.pawgen.component.deployer;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

@Slf4j
public final class Deployer {

	private final URI netlifyUrl;
	private final String netlifyAccessToken;
	private final String netlifySiteId;
	private final URI cloudflarePagesUrl;
	private final String cloudflarePagesToken;
	private final String cloudflarePagesProjectName;
	private final String cloudflarePagesAccountId;


	@Builder
	Consumer<Collection<? extends FileDigestData>> create(
		URI netlifyUrl,
		String netlifyAccessToken,
		String netlifySiteId,
		URI cloudflarePagesUrl,
		String cloudflarePagesToken,
		String cloudflarePagesProjectName,
		String cloudflarePagesAccountId
	) {

	}

	public Consumer<Collection<? extends FileDigestData>> create(String... deployer) {
		var deployers = stream(deployer)
			.map(Deployer.Type::from)
			.filter(Type.NONE::equals)
			.distinct()
			.collect(toMap(Function.identity(), this::create, (a1, a2) -> {
				throw new IllegalArgumentException("Not supposed to hande duplicates: " + a1);
			}, LinkedHashMap::new));
		return files -> {
			for (var entry : deployers.entrySet()) {
				log.info("Deploying with {}", entry.getKey());
				try {
					entry.getValue().accept(files);
					log.debug("Deploy with {} finished", entry.getKey());
				} catch (Exception e) {
					log.error("Deploy with {} failed", entry.getKey());
				}
			}
		};
	}

	Consumer<Collection<? extends FileDigestData>> create(Type deployer) {
		return switch (deployer) {
			case NETLIFY -> new NetlifyDeployer(netlifyUrl, netlifyAccessToken, netlifySiteId)::deploy;
			case CLOUDFLARE_PAGES -> new CloudflarePagesDeployer(cloudflarePagesUrl, cloudflarePagesToken, cloudflarePagesProjectName, cloudflarePagesAccountId)::deploy;
			case NONE -> __ -> {
			};
		};
	}

	enum Type {
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
