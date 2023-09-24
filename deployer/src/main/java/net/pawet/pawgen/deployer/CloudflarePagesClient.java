package net.pawet.pawgen.deployer;

import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.deployer.deployitem.Content;
import net.pawet.pawgen.deployer.deployitem.Digest;
import net.pawet.pawgen.deployer.deployitem.Path;

import java.io.*;
import java.net.URI;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.net.http.HttpClient.Redirect.NORMAL;
import static java.net.http.HttpRequest.BodyPublishers.ofByteArray;
import static java.net.http.HttpRequest.newBuilder;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static net.pawet.pawgen.deployer.DeployerHttpException.errorResponse;
import static net.pawet.pawgen.deployer.JsonBodyHandler.jsonPublisher;
import static net.pawet.pawgen.deployer.JsonBodyHandler.toJson;


@Slf4j
public final class CloudflarePagesClient {

	public static final URI BASE_URL = URI.create("https://api.cloudflare.com/client/v4/");
	private final HttpClient client = HttpClient.newBuilder()
		.followRedirects(NORMAL)
		.connectTimeout(Duration.ofMinutes(30))
		.build();

	private final BodyHandler<Stream<JsonValue>> bodyHandler = JsonBodyHandler.withErrorHandling(CloudflarePagesClient::handleError);
	@NonNull
	private final URI baseUrl;
	@NonNull
	private final String token;

	/**
	 * Create custom token for <a href="https://dash.cloudflare.com/profile/api-tokens">Account\Cloudflare Pages\edit</a>
	 */
	public CloudflarePagesClient(String token) {
		this(BASE_URL, token);
	}

	public CloudflarePagesClient(@NonNull URI baseUrl, @NonNull String token) {
		if (!baseUrl.getPath().endsWith("/")) {
			throw new IllegalArgumentException("uri path have to end with '/' " + baseUrl);
		}
		this.baseUrl = baseUrl;
		this.token = token;
	}

	private Builder getApiAuthRequestBuilder() {
		return getRequestBuilder().header("Authorization", "bearer " + token);
	}

	private static Builder getRequestBuilder() {
		return newBuilder().setHeader("User-Agent", "url/7.68.0");
	}

	public ProjectOperation project(String accountId, String projectName) {
		return new ProjectOperation(accountId, projectName);
	}

	public class ProjectOperation {

		private final URI projectUri;

		ProjectOperation(@NonNull String accountId, @NonNull String projectName) {
			this.projectUri = baseUrl.resolve("accounts/" + accountId + "/pages/projects/" + projectName + "/");
		}

		public AssetOperation asset() {
			return new AssetOperation(this::fetchJwt);
		}

		public DeploymentOperation deployment() {
			return new DeploymentOperation(projectUri);
		}

		@SneakyThrows
		String fetchJwt() {
			log.debug("Fetch jwt token for '{}'", projectUri);
			var request = getApiAuthRequestBuilder().uri(projectUri.resolve("upload-token")).GET().build();
			try (var valueStream = client.send(request, bodyHandler).body()) {
				return valueStream.map(JsonValue::asJsonObject)
					.findAny()
					.map(json -> json.getJsonObject("result"))
					.map(json -> json.getString("jwt"))
					.orElseThrow(() -> new IllegalArgumentException("Can't login for account: " + projectUri));
			}
		}
	}

	public class DeploymentOperation {

		private final URI deploymentUri;

		DeploymentOperation(@NonNull URI projectUri) {
			this.deploymentUri = projectUri.resolve("deployments");
		}

		@SneakyThrows
		public <T extends Digest & Path> String create(Collection<T> files, Content redirectsFile) {
			var body = createFormDataBody(files, redirectsFile);
			var request = getApiAuthRequestBuilder()
				.uri(deploymentUri)
				.header("Content-Type", "multipart/form-data; boundary=" + body.getKey())
				.POST(ofByteArray(body.getValue()))
				.build();
			try (var valueStream = client.send(request, bodyHandler).body()) {
				return valueStream.map(JsonValue::asJsonObject).findAny()
					.filter(json -> json.getBoolean("success"))
					.map(json -> json.getJsonObject("result"))
					.map(json -> json.getString("id"))
					.orElseThrow(() -> new IllegalStateException("No deployment found for files: " + files.stream().map(Path::getPath).collect(joining("[", "]", ","))));
			}
		}

		@SneakyThrows
		public Collection<String> list() {
			var request = getApiAuthRequestBuilder().uri(deploymentUri).GET().build();
			try (var valueStream = client.send(request, bodyHandler).body()) {
				return valueStream.map(JsonValue::asJsonObject).findAny()
					.map(json -> json.getJsonArray("result"))
					.stream()
					.flatMap(json -> json.getValuesAs(JsonObject.class).stream())
					.map(json -> json.getString("id"))
					.toList();
			}
		}

	}

	public class AssetOperation {

		//TODO https://github.com/projectlombok/lombok/issues/3506  @Synchronized with ReentrantLock
		private final ReentrantLock lock = new ReentrantLock();
		private final Supplier<String> jwtSupplier;
		private final URI pagesUri;
		private volatile String jwt;

		AssetOperation(Supplier<String> jwtSupplier) {
			this.jwtSupplier = jwtSupplier;
			pagesUri = baseUrl.resolve("pages/assets/");
		}

		@SneakyThrows
		public <T> T refreshToken(Callable<T> operation) {
			try {
				return operation.call();
			} catch (DeployerHttpException.DeployerErrorResponseHttpException e) {
				if (e.getCode() == 8000013) { //https://github.com/cloudflare/workers-sdk/blob/89d78c0/packages/wrangler/src/pages/upload.tsx#L225
					return resetTokenAndRetry(operation);
				}
				throw e;
			} catch (DeployerHttpException e) {
				if (e.getHttpStatusCode() == 403) {
					return resetTokenAndRetry(operation);
				}
				throw e;
			}
		}

		private <T> T resetTokenAndRetry(Callable<T> operation) throws Exception {
			jwt = null; //force refetch
			return operation.call();
		}

		@SneakyThrows
		public Collection<String> missing(Collection<? extends Digest> digests) {
			return refreshToken(() -> missingInternal(digests));
		}

		Collection<String> missingInternal(Collection<? extends Digest> digests) throws IOException, InterruptedException {
			var request = getJwtAuthRequestBuilder().uri(pagesUri.resolve("check-missing"))
				.header("Content-Type", "application/json; charset=utf-8")
				.POST(jsonPublisher(generator -> {
					generator.writeStartObject();
					{
						generator.writeStartArray("hashes");
						digests.stream().map(Digest::getDigest).forEach(generator::write);
						generator.writeEnd();
					}
					generator.writeEnd();
				}))
				.build();
			try (var valueStream = client.send(request, bodyHandler).body()) {
				return valueStream.map(JsonValue::asJsonObject).findAny()
					.map(json -> json.getJsonArray("result"))
					.stream()
					.flatMap(json -> json.getValuesAs(JsonString.class).stream())
					.map(JsonString::getString)
					.toList();
			}
		}

		@SneakyThrows
		public boolean upsert(Collection<? extends Digest> digests) {
			return refreshToken(() -> upsertInternal(digests));
		}

		boolean upsertInternal(Collection<? extends Digest> digests) throws IOException, InterruptedException {
			var request = getJwtAuthRequestBuilder().uri(pagesUri.resolve("upsert-hashes"))
				.header("Content-Type", "application/json; charset=utf-8")
				.POST(jsonPublisher(generator -> {
					generator.writeStartObject();
					{
						generator.writeStartArray("hashes");
						digests.stream().map(Digest::getDigest).forEach(generator::write);
						generator.writeEnd();
					}
					generator.writeEnd();
				}))
				.build();
			return client.send(request, bodyHandler).body()
				.map(JsonValue::asJsonObject)
				.findAny()
				.map(json -> json.getBoolean("success"))
				.orElse(false);
		}

		@SneakyThrows
		public <T extends Digest & Content & Path> boolean upload(Collection<T> files) {
			log.debug("Uploading {} files", files.size());
			return refreshToken(() -> uploadInternal(files));
		}

		<T extends Digest & Content & Path> boolean uploadInternal(Collection<T> files) throws IOException, InterruptedException {
			var request = getJwtAuthRequestBuilder()
				.uri(pagesUri.resolve("upload"))
				.header("Content-Type", "application/json; charset=utf-8")
				.POST(jsonPublisher(generator -> {
					generator.writeStartArray();
					for (var file : files) {
						generator.writeStartObject()
							.write("key", file.getDigest())
							.write("base64", true);
						try (var is = getMarkSupportedIS(file)) {
							//get mime first
							generator.writeStartObject("metadata").write("contentType", getContentType(file.getPath(), is)).writeEnd();
							//then handle content
							generator.write("value", asBase64(is));
						} catch (IOException e) {
							throw new UncheckedIOException(e);
						}
						generator.writeEnd();
					}
					generator.writeEnd();
				}))
				.build();
			return client.send(request, bodyHandler).body()
				.map(JsonValue::asJsonObject)
				.findAny()
				.map(json -> json.getBoolean("success"))
				.orElse(false);
		}


		@Synchronized
		private Builder getJwtAuthRequestBuilder() {
			lock.lock();
			try {
				if (jwt == null) {
					jwt = jwtSupplier.get();
				}
				return getRequestBuilder().header("Authorization", "Bearer " + jwt);
			} finally {
				lock.unlock();
			}
		}
	}

	// BufferedInputStream supports marks, used in java.net.URLConnection#guessContentTypeFromStream()
	private static InputStream getMarkSupportedIS(Content file) {
		var is = file.inputStream();
		return is.markSupported() ? is : new BufferedInputStream(is);
	}

	@SneakyThrows
	private static String asBase64(InputStream is) {
		var bos = new ByteArrayOutputStream();
		try (var base64os = BASE64_ENCODER.wrap(bos)) {
			is.transferTo(base64os);
		}
		return bos.toString();
	}

	private static <T extends Digest & Path> Entry<String, byte[]> createFormDataBody(Collection<T> files, Content redirectsFile) throws IOException {
		var boundary = "---------------------------" + UUID.randomUUID();
		byte[] rnDelim = "\r\n".getBytes();
		byte[] dashDelim = "--".getBytes();
		byte[] boundaryDelim = boundary.getBytes();
		var os = new ByteArrayOutputStream();
		{ //manifest
			os.write(dashDelim);
			os.write(boundaryDelim);
			os.write(rnDelim);
			os.write("Content-Disposition: form-data; name=\"manifest\"".getBytes());
			os.write(rnDelim);
			os.write(rnDelim);
			toJson(generator -> {
				generator.writeStartObject();
				for (var file : files) {
					generator.write(file.getPath(), file.getDigest());
				}
				generator.writeEnd();
			}, os);
			os.write(rnDelim);
		}
		if (redirectsFile != null) { //_redirects
			os.write(dashDelim);
			os.write(boundaryDelim);
			os.write(rnDelim);
			os.write("Content-Disposition: form-data; name=\"_redirects\"; filename=\"_redirects\"".getBytes());
			os.write(rnDelim);
			os.write("Content-Type: application/octet-stream".getBytes());
			os.write(rnDelim);
			os.write(rnDelim);
			try (var is = redirectsFile.inputStream()) {
				is.transferTo(os);
			}
			os.write(rnDelim);
		}
		{ // end
			os.write(dashDelim);
			os.write(boundaryDelim);
			os.write(dashDelim);
		}
		return Map.entry(boundary, os.toByteArray());
	}

	private static String getContentType(String path, InputStream is) {
		String filename = path.substring(path.lastIndexOf('/') + 1);
		String mime = URLConnection.guessContentTypeFromName(filename);
		if (mime != null) {
			return mime;
		}
		try {
			mime = URLConnection.guessContentTypeFromStream(is);
			if (mime != null) {
				return mime;
			}
		} catch (IOException e) {
			log.info("Unable to detect ime type for {}", path, e);
		}
		return "application/octet-stream";
	}

	public static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();

	@SuppressWarnings("unchecked")
	public static <T> HttpResponse.BodySubscriber<T> handleError(HttpResponse.BodyHandler<T> handler, HttpResponse.ResponseInfo resp) {
		int statusCode = resp.statusCode();
		if (statusCode / 100 == 2) { //isSucceed
			return handler.apply(resp);
		} else if (statusCode == 429) {
			return HttpResponse.BodySubscribers.replacing((T) Stream.generate(() -> {
				throw DeployerHttpException.rateLimit(statusCode, Clock.systemUTC().instant().plusSeconds(60));
			}));
		}
		return (HttpResponse.BodySubscriber<T>) HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofByteArray(), bytes -> Stream.generate(() -> {
			throw JsonBodyHandler.parseFirst(new ByteArrayInputStream(bytes))
				.map(JsonValue::asJsonObject)
				.map(json -> json.getJsonString("error"))
				.filter(JsonValue.NULL::equals)
				.map(Object::toString)
				.map(error -> errorResponse(statusCode, statusCode, error))
				.or(() -> Optional.of(DeployerHttpException.generic(statusCode, new String(bytes, UTF_8))))
				.orElseGet(() -> DeployerHttpException.generic(statusCode, "body: <no body>"));
		}));
	}
}
