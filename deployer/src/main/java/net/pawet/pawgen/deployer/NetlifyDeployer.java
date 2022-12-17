package net.pawet.pawgen.deployer;

import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.deployer.deployitem.Content;
import net.pawet.pawgen.deployer.deployitem.Digest;
import net.pawet.pawgen.deployer.deployitem.Path;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Thread.sleep;
import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.*;
import static lombok.AccessLevel.PRIVATE;

@Slf4j
public class NetlifyDeployer {

	private static final String DEPLOYMENT_TITLE = "pawgen_deployer";
	private final NetlifyClient netlifyClient;
	private final String siteId;
	private final Retrier retrier = new Retrier(Duration.ofSeconds(2), 20);

	public NetlifyDeployer(URI url, String accessToken, String siteId) {
		this.netlifyClient = url == null ? new NetlifyClient(accessToken) : new NetlifyClient(url, accessToken);
		this.siteId = siteId;
	}

	@SneakyThrows
	public final <T extends Digest & Content & Path> void deploy(Collection<T> toBeDeployed) {
		retrier.exec(new Operation<>(this, toBeDeployed)::deploy);
		log.debug("Deployed {} files", toBeDeployed.size());
	}

	Optional<String> getDeployState(String deployId) {
		return netlifyClient.deploy(deployId).find()
			.filter(d -> DEPLOYMENT_TITLE.equals(d.getString("title")))
			.map(d -> d.getString("state"));
	}

	<T extends Digest & Path> Optional<String> createDeploy(Collection<T> values) {
		return netlifyClient.siteDeploy(siteId).createAsync(DEPLOYMENT_TITLE, values).map(obj -> obj.getString("id"));
	}

	Stream<String> getRequiredFilesFor(String deployId) {
		return netlifyClient.deploy(deployId).find()
			.map(json -> json.get("required"))
			.filter(not(JsonValue.NULL::equals))
			.map(JsonValue::asJsonArray)
			.stream()
			.flatMap(Collection::stream)
			.map(JsonString.class::cast)
			.map(JsonString::getString);
	}

	/**
	 * @return true if everything is uploaded successfully
	 */
	@SneakyThrows
	<T extends Content & Path> long uploadFiles(String deployId, Collection<T> files) {
		var deployOp = netlifyClient.deploy(deployId);
		return files.stream()
			.mapToLong(value -> upload(deployOp, value))
			.filter(value -> value != Long.MIN_VALUE)
			.count();
	}

	private <T extends Content & Path> long upload(NetlifyClient.DeployOperation deployOp, T value) {
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
final class Operation<T extends Digest & Content & Path> {

	private final NetlifyDeployer client;
	private final Collection<T> files;
	private final Map<String, T> unique;

	private String deployId;

	private static <T extends Digest & Content> Map<String, T> createUnique(Collection<T> files) {
		return files.stream().collect(toMap(Digest::getDigest, identity(), (t, __) -> t));
	}

	Operation(NetlifyDeployer client, Collection<T> files) {
		this(client, files, null);
	}

	Operation(NetlifyDeployer client, Collection<T> files, String deployId) {
		this(client, files, createUnique(files), deployId);
	}

	/**
	 * @throws DeployerHttpException if retry
	 */
	@SneakyThrows
	void deploy() throws DeployerHttpException {
		while (true) {
			if (files.isEmpty()) {
				return;
			}
			if (deployId == null) {
				var deployId = client.getLastDeployWithStatus("prepared");
				if (deployId.isPresent()) { // try to resume
					this.deployId = deployId.get();
					if (deployWithState("prepared")) {
						return;
					}
				}
				if (deployWithState("--init--")) {
					return;
				}
			} else if (client.getDeployState(deployId).map(this::deployWithState).orElseThrow()) {
				return;
			}
			sleep(Duration.ofSeconds(2));
		}
	}

	/**
	 * @return true if deployment process is done, false if needs to be continued
	 */
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
				if (files.isEmpty()) {
					log.info("Closing previous deploy deployId {}, as it was nothing to upload", deployId);
					client.cancelDeploy(deployId);
					return false; //we are done here
				}
				log.info("Uploading {} files", files.size());
				log.atTrace().setMessage("Uploading files: {}").addArgument(() -> files.stream().map(Object::toString).collect(joining())).log();
				try {
					long filesUploaded = client.uploadFiles(deployId, files);
					log.info("Uploaded {} files, left: {}", filesUploaded, files.size() - filesUploaded);
					return filesUploaded == files.size();
				} catch (DeployerHttpException e) {
					log.error("Error while uploading: '{}': attempt to upload: {}", e.getMessage(), files.stream().map(Object::toString).collect(joining(",")));
					if (e.getHttpStatusCode() == 422) {
						client.cancelDeploy(deployId);
					}
					throw e;
				}
			case "processing":
			case "preparing": // wait a bit more
				log.info("wait until deploy with id '{}' prepared", deployId);
				return false;
			case "ready": // already deployed
				log.info("'{}' already deployed", deployId);
				return true;
			case "uploaded":
				log.info("'{}' uploaded, please wait", deployId);
				return false;
			default:
		}
		throw new IllegalArgumentException(String.format("Unknown state '%s' for deploy with Id '%s'", state, deployId));
	}

}

