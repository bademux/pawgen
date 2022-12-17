package net.pawet.pawgen.deployer;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.deployer.CloudflarePagesClient.AssetOperation;
import net.pawet.pawgen.deployer.deployitem.Content;
import net.pawet.pawgen.deployer.deployitem.Digest;
import net.pawet.pawgen.deployer.deployitem.Path;
import net.pawet.pawgen.deployer.deployitem.Size;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.*;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.*;

/**
 * inspired with https://github.com/cloudflare/workers-sdk/blob/89d78c0/packages/wrangler/src/pages/upload.tsx
 */
@Slf4j
public class CloudflarePagesDeployer {

	//https://developers.cloudflare.com/pages/platform/limits/#file-size
	private final static int MAX_FILE_SIZE_IN_BYTES = 25 * 1024 * 1024;

	private final Retrier retrier = new Retrier(Duration.ofSeconds(3), 20);

	private final CloudflarePagesClient.ProjectOperation projectOp;

	public CloudflarePagesDeployer(URI url, String token, String projectName, String accountId) {
		this.projectOp = createClient(url, token).project(accountId, projectName);
	}

	private static CloudflarePagesClient createClient(URI url, String token) {
		return url == null ? new CloudflarePagesClient(token) : new CloudflarePagesClient(url, token);
	}

	public final <T extends Digest & Content & Path & Size> void deploy(Collection<T> files) {
		var grouped = files.stream().collect(groupingBy(CloudflarePagesDeployer::groupingFiles));
		var invalidFiles = grouped.getOrDefault("invalid", List.of());
		if (!invalidFiles.isEmpty()) {
			log.warn("{} files aren't match CloudFlare restrictions and will not be deployed", invalidFiles.size());
			for (var invalidFile : invalidFiles) {
				log.warn("File '{}' is bigger than {} MiB", invalidFile.getPath(), MAX_FILE_SIZE_IN_BYTES / 1024 / 1024);
			}
		}
		var fileRes = grouped.get("files");
		var redirectFile = grouped.getOrDefault("redirect", List.of()).stream().findAny().orElse(null);
		deploy(fileRes, redirectFile);
	}

	private static <T extends Path & Size> String groupingFiles(T file) {
		if (file.getPath().equals("/_redirects")) {
			return "redirect";
		}
		if (file.getSizeInBytes() > MAX_FILE_SIZE_IN_BYTES) {
			return "invalid";
		}
		return "files";
	}

	@SneakyThrows
	final <T extends Digest & Content & Path & Size> void deploy(Collection<T> files, T redirectsFile) {
		log.debug("Deploying {} files", files.size());

		var assetOp = projectOp.asset();
		var missing = missing(assetOp, files);
		uploadInBatches(assetOp, missing);

		retrier.exec(() -> createDeployment(files, redirectsFile));
		if (missing.size() != 0) {
			retrier.exec(() -> upsertFiles(missing));
		}
	}

	private <T extends Digest & Content & Path & Size> void uploadInBatches(AssetOperation assetOp, Collection<T> files) {
		long batchSize = 0;
		var batch = new ArrayList<T>();
		for (var file : files) {
			batchSize += file.getSizeInBytes();
			if (batchSize >= MAX_FILE_SIZE_IN_BYTES) {
				log.info("Uploading files: {}", batch.stream().map(Path::getPath).collect(joining(", ")));
				retrier.exec(() -> assetOp.upload(batch));
				batchSize = file.getSizeInBytes();
				batch.clear();
			}
			batch.add(file);
		}
	}

	private <T extends Digest & Path> void createDeployment(Collection<T> files, Content redirectsFile) {
		var id = projectOp.deployment().create(files, redirectsFile);
		log.info("Created deployment {}", id);
	}

	private void upsertFiles(Collection<? extends Digest> digests) {
		try {
			if (projectOp.asset().upsert(digests)) {
				log.info("Deployed {} files", digests.size());
				return;
			}
			log.info("Can't update file hashes, files have to be uploaded next time");
		} catch (Exception e) {
			log.error("Can't update file hashes, files have to be uploaded next time", e);
		}
		throw DeployerHttpException.rateLimit(-1, Clock.systemUTC().instant().plusSeconds(1));
	}

	private <T extends Digest> Collection<T> missing(AssetOperation assetOp, Collection<T> files) {
		var unique = files.stream().collect(toMap(Digest::getDigest, identity(), (t, __) -> t));
		return retrier.exec(() -> assetOp.missing(unique.values())).stream()
			.map(unique::get)
			.filter(Objects::nonNull)
			.toList();
	}

}
