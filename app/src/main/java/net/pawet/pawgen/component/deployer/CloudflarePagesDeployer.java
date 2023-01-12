package net.pawet.pawgen.component.deployer;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

@Slf4j
class CloudflarePagesDeployer {

	public static final int UPLOAD_CHUNK_SIZE = 5;

	private final CloudflarePagesClient.ProjectOperation projectOp;

	public CloudflarePagesDeployer(URI url, String token, String projectName, String accountId) {
		this.projectOp = new CloudflarePagesClient(url, token).project(accountId, projectName);
	}

	@SneakyThrows
	public final <T extends FileDigest & FileData> void deploy(Collection<T> files) {
		var unique = files.stream().collect(toMap(FileDigest::getDigest, Function.identity(), (t, __) -> t));
		log.debug("Deploying {} files", unique.size());
		retry(() -> uploadMissing(files, unique::get));
		retry(() -> createDeployment(files));
		upsertFiles(files);
	}

	private void upsertFiles(Collection<? extends FileDigest> files) {
		try {
			if (projectOp.asset().upsert(files)) {
				log.info("Deployed {} files", files.size());
				return;
			}
			log.info("Can't update file hashes, files have to be uploaded next time");
		} catch (Exception e) {
			log.info("Can't update file hashes, files have to be uploaded next time", e);
		}
	}

	private void createDeployment(Collection<? extends FileDigest> files) {
		var id = projectOp.deployment().create(files);
		log.info("Created deployment {}", id);
	}

	private <T extends FileDigest & FileData> void uploadMissing(Collection<T> files, Function<String, T> fileByHash) {
		var assetOp = projectOp.asset();
		var missing = assetOp.missing(files);
		var toBeDeployed = missing.stream().map(fileByHash).filter(Objects::nonNull).toList();

		for (int start = 0; start < toBeDeployed.size(); ) {
			int end = Math.min(start + UPLOAD_CHUNK_SIZE, toBeDeployed.size());
			if (!assetOp.upload(toBeDeployed.subList(start, end))) {
				log.warn("Can't upload file batch starting from {} to {}", start, end);
				return;
			}
			start = end;
			log.info("Uploaded files: {} ", end);
		}
	}

	void retry(Runnable operation) {
		for (int i = 1; ; i++) {
			try {
				operation.run();
				break;
			} catch (Exception e) {
				if ((e instanceof DeployerHttpException || e instanceof IOException) && i < 5) {
					log.error("Unexpected error, retrying {} times", i, e);
					continue;
				}
				throw e;
			}
		}
	}

}
