package net.pawet.pawgen.component.deployer;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.system.CliOptions;
import net.pawet.pawgen.component.system.storage.DigestAwareResource;
import net.pawet.pawgen.deployer.CloudflarePagesDeployer;
import net.pawet.pawgen.deployer.NetlifyDeployer;
import net.pawet.pawgen.deployer.deployitem.Content;
import net.pawet.pawgen.deployer.deployitem.Digest;
import net.pawet.pawgen.deployer.deployitem.Path;
import net.pawet.pawgen.deployer.deployitem.Size;

import java.io.InputStream;
import java.net.URI;
import java.nio.channels.Channels;
import java.util.Collection;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
public final class DeployerFactory {

	private final URI netlifyUrl;
	private final String netlifyAccessToken;
	private final String netlifySiteId;
	private final URI cloudflarePagesUrl;
	private final String cloudflarePagesToken;
	private final String cloudflarePagesProjectName;
	private final String cloudflarePagesAccountId;

	public static DeployerFactory create(CliOptions config) {
		return new DeployerFactory(
			config.getNetlifyUrl(),
			config.getNetlifyAccessToken(),
			config.getNetlifySiteId(),
			config.getCloudflarePagesUrl(),
			config.getCloudflarePagesToken(),
			config.getCloudflarePagesProjectName(),
			config.getCloudflarePagesAccountId()
		);
	}

	public Consumer<Collection<DigestAwareResource>> deployer(String... deployerNames) {
		assert deployerNames != null : "can't be null";
		return files -> deploy(files, deployerNames);
	}

	private void deploy(Collection<DigestAwareResource> files, String[] deployerNames) {
		for (String deployerName : deployerNames) {
			log.info("Deploying with {}", deployerName);
			try {
				deploy(files, Type.from(deployerName));
			} catch (Exception e) {
				log.error("Deploy with {} failed", deployerName, e);
			}
		}
	}

	private void deploy(Collection<DigestAwareResource> files, Type type) {
		switch (type) {
			case NETLIFY:
				new NetlifyDeployer(netlifyUrl, netlifyAccessToken, netlifySiteId)
					.deploy(files.stream().map(DigestAwareResourceFile::netlify).toList());
				break;
			case CLOUDFLARE_PAGES:
				new CloudflarePagesDeployer(cloudflarePagesUrl, cloudflarePagesToken, cloudflarePagesProjectName, cloudflarePagesAccountId)
					.deploy(files.stream().map(DigestAwareResourceFile::cloudflare).toList());
				break;
			case NONE:
		}
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

