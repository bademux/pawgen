package net.pawet.pawgen.component.netlify;

import jakarta.json.JsonString;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.netlify.NetlifyHttpException.NetlifyRateLimitHttpException;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;
import static lombok.AccessLevel.PRIVATE;

@Slf4j
class NetlifyDeployer {

	private static final String DEPLOYMENT_TITLE = "pawgen_deployer";
	private final NetlifyClient netlifyClient;
	private final String siteId;
	private final Retrier retrier = new Retrier(Clock.systemUTC(), Duration.ofSeconds(2), 20);

	public NetlifyDeployer(URI url, String accessToken, String siteId) {
		this.netlifyClient = url == null ? new NetlifyClient(accessToken) : new NetlifyClient(url, accessToken);
		this.siteId = siteId;
	}

	@SneakyThrows
	public final <T extends FileDigest & FileData> void deploy(Collection<T> toBeDeployed) {
		retrier.deployWithRetry(new Deployer<>(this, toBeDeployed)::deploy);
		log.debug("Deployed {} files", toBeDeployed.size());
	}

	Optional<String> getDeployState(String deployId) {
		return netlifyClient.deploy(deployId).find()
			.filter(d -> DEPLOYMENT_TITLE.equals(d.getString("title")))
			.map(d -> d.getString("state"));
	}

	Optional<String> createDeploy(Collection<? extends FileDigest> values) {
		return netlifyClient.siteDeploy(siteId).createAsync(DEPLOYMENT_TITLE, values).map(obj -> obj.getString("id"));
	}

	Stream<String> getRequiredFilesFor(String deployId) {
		return netlifyClient.deploy(deployId).find().orElseThrow(() -> new IllegalStateException("Can't find deploy with id: " + deployId))
			.getJsonArray("required").getValuesAs(JsonString.class).stream()
			.map(JsonString::getString);
	}

	/**
	 * @return true if everything is uploaded successfully
	 */
	@SneakyThrows
	long uploadFiles(String deployId, Collection<? extends FileData> files) {
		var deployOp = netlifyClient.deploy(deployId);
		return files.stream()
			.mapToLong(value -> upload(deployOp, value))
			.filter(value -> value != Long.MIN_VALUE)
			.count();
	}

	private long upload(NetlifyClient.DeployOperation deployOp, FileData value) {
		long size = deployOp.upload(value);
		log.debug("Uploaded {}Kb", size / 1024);
		return size;
	}

	Optional<String> getLastDeployWithStatus(String state) {
		try (var arrayStream = netlifyClient.siteDeploy(siteId).list(state, 1)) {
			return arrayStream.findAny().map(json -> json.getString("id"));
		}
	}

	public String cancelDeploy(String deployId) {
		log.debug("Canceling deploy: {}", deployId);
		return netlifyClient.deploy(deployId).cancel().getString("id");
	}

	public List<String> cancelAll() {
		try (var arrayStream = netlifyClient.siteDeploy(siteId).list(null, 1000)) {
			List<String> canceledIds = arrayStream.filter(d -> !"error".equals(d.getString("state")))
				.map(d -> d.getString("id"))
				.map(this::cancelDeploy)
				.collect(Collectors.toList());
			log.debug("Canceled deploys: {}", String.join(",", canceledIds));
			return canceledIds;
		}
	}
}

@Slf4j
@AllArgsConstructor(access = PRIVATE)
final class Deployer<T extends FileDigest & FileData> {

	private final NetlifyDeployer client;
	private final Collection<T> files;
	private final Map<String, T> unique;

	private String deployId;

	private static <T extends FileDigest & FileData> Map<String, T> createUnique(Collection<T> files) {
		return files.stream().collect(toMap(FileDigest::getDigest, Function.identity(), (t, __) -> t));
	}

	Deployer(NetlifyDeployer client, Collection<T> files) {
		this(client, files, createUnique(files), null);
	}

	Deployer(NetlifyDeployer client, Collection<T> files, String deployId) {
		this(client, files, createUnique(files), deployId);
	}


	/**
	 * @return true if everything is uploaded successfully, false if retry
	 */
	boolean deploy() {
		if (files.isEmpty()) {
			return true;
		}
		if (deployId != null) {
			return deployWithState(client.getDeployState(deployId).orElse("unknown"));
		}
		var deployId = client.getLastDeployWithStatus("prepared");
		if (deployId.isPresent()) { // try to resume
			this.deployId = deployId.get();
			return deployWithState("prepared");
		}
		return deployWithState("--init--");
	}

	@SneakyThrows
	boolean deployWithState(String state) {
		switch (state) {
			case "--init--":
			case "error":
				log.info("Creating new deployment and upload {} files", files.size());
				deployId = client.createDeploy(files).orElseThrow();
				return false; //retry later deploy might be in 'processing' state
			case "new": // files need to be added to deploy
			case "uploading":
				if (deployId == null) {
					// previous upload in progress, nothing we can do here  https://community.netlify.com/t/bug-netlify-deployment-api-deployments-in-uploading-state-forces-client-to-be-stateful/32030
					return true;
				}
			case "prepared": // upload nor started yet
				log.debug("Got deployId '{}'", deployId);
				var requiredFilesFor = client.getRequiredFilesFor(deployId).toList();
				var files = requiredFilesFor.stream().map(unique::get).filter(Objects::nonNull).collect(toList());
				log.info("Uploading {} files", files.size());
				log.atTrace().setMessage("Uploading files: {}").addArgument(() -> files.stream().map(Object::toString).collect(joining())).log();
				try {
					long filesUploaded = client.uploadFiles(deployId, files);
					log.info("Uploaded {} files, left: {}", filesUploaded, files.size() - filesUploaded);
					return filesUploaded == files.size();
				} catch (NetlifyHttpException e) {
					log.error("Error while uploading: '{}': attempt to upload: {}", e.getMessage(), files.stream().map(Object::toString).collect(joining(",")));
					if (e.getHttpStatusCode() == 422) {
						client.cancelDeploy(deployId);
					}
					throw e;
				}
			case "processing":
			case "preparing": // wait a bit more and retry
				log.info("wait until deploy with id '{}' prepared", deployId);
				return false;
			case "ready": // already deployed
				log.info("'{}' already deployed", deployId);
				return true;
			case "uploaded":
				log.info("'{}' uploaded, wait for a while", deployId);
				return false;
			default:
		}
		throw new IllegalArgumentException(String.format("Unknown state '%s' for deploy with Id '%s'", state, deployId));
	}

}

@Slf4j
@RequiredArgsConstructor
final class Retrier {

	private final Clock clock;
	private final Duration initialTimeout;
	private final int maxRetries;

	@SneakyThrows
	void deployWithRetry(BooleanSupplier deployer) {
		for (long i = 1, t = initialTimeout.toMillis(); !safeOperation(deployer); i++, t *= 2) {
			if (i > maxRetries) {
				throw new IllegalStateException(i + " operations in a row was done, limit reached.");
			}
			Thread.sleep(t);
		}
	}

	@SneakyThrows
	private boolean safeOperation(BooleanSupplier deployer) {
		try {
			return deployer.getAsBoolean();
		} catch (NetlifyRateLimitHttpException e) {
			long delay = clock.millis() - e.getReset();
			if (delay > 0) {
				log.info("Requests are rate limited. Waiting {} seconds", delay / 1000);
				Thread.sleep(delay);
			}
			return false;
		}
	}

}

