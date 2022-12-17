package net.pawet.pawgen.component.deployer;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.net.http.HttpClient.Redirect.NORMAL;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpRequest.BodyPublishers.ofByteArray;
import static java.net.http.HttpRequest.newBuilder;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static net.pawet.pawgen.component.deployer.DeployerHttpException.errorResponse;


@Slf4j
@RequiredArgsConstructor
public final class CloudflarePagesClient {

	public static final URI BASE_URL = URI.create("https://api.cloudflare.com/client/v4");
	private final HttpClient client = HttpClient.newBuilder()
		.executor(Executors.newVirtualThreadPerTaskExecutor())
		.followRedirects(NORMAL)
		.version(HTTP_1_1)
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


	private Builder getApiAuthRequestBuilder() {
		return getRequestBuilder().header("Authorization", "bearer " + token);
	}

	private static Builder getRequestBuilder() {
		return newBuilder().setHeader("User-Agent", "url/7.68.0");
	}

	public ProjectOperation project(String accountId, String projectName) {
		return new ProjectOperation(accountId, projectName);
	}

	@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
	public class ProjectOperation {

		@NonNull
		private final String accountId;
		@NonNull
		private final String projectName;

		public AssetOperation asset() {
			return new AssetOperation(this::fetchJwt);
		}

		public DeploymentOperation deployment() {
			return new DeploymentOperation(accountId, projectName);
		}

		@SneakyThrows
		String fetchJwt() {
			var request = getApiAuthRequestBuilder()
				.uri(uriOf("/accounts/" + accountId + "/pages/projects/" + projectName + "/upload-token"))
				.GET()
				.build();
			try (var valueStream = client.send(request, bodyHandler).body()) {
				return valueStream.map(JsonValue::asJsonObject)
					.findAny()
					.map(json -> json.getJsonObject("result"))
					.map(json -> json.getString("jwt"))
					.orElseThrow(() -> new IllegalArgumentException("Can't login for account: " + accountId + " project: " + projectName));
			}
		}
	}

	@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
	public class DeploymentOperation {
		@NonNull
		private final String accountId;
		@NonNull
		private final String projectName;

		@SneakyThrows
		public String create(Collection<? extends FileDigest> files) {
			var boundary = "---------------------------" + UUID.randomUUID();
			var request = getApiAuthRequestBuilder()
				.uri(uriOf("/accounts/" + accountId + "/pages/projects/" + projectName + "/deployments"))
				.header("Content-Type", "multipart/form-data; boundary=" + boundary)
				.POST(ofByteArray(createFormDataBody(files, boundary)))
				.build();
			try (var valueStream = client.send(request, bodyHandler).body()) {
				return valueStream.map(JsonValue::asJsonObject).findAny()
					.map(json -> json.getJsonObject("result"))
					.map(json -> json.getString("id"))
					.orElseThrow(() -> new IllegalStateException("No deployment found for files: " + files.stream().map(FileDigest::getRootRelativePath).collect(joining("[", "]", ","))));
			}
		}

		@SneakyThrows
		public Collection<String> list() {
			var request = getApiAuthRequestBuilder()
				.uri(uriOf("/accounts/" + accountId + "/pages/projects/" + projectName + "/deployments"))
				.GET()
				.build();
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

	@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
	public class AssetOperation {

		private final Supplier<String> jwtSupplier;
		private volatile String jwt;


		@SneakyThrows
		public <T> T refreshToken(Callable<T> operation) {
			try {
				return operation.call();
			} catch (DeployerHttpException.DeployerErrorResponseHttpException e) {
				if (e.getCode() == 8000013) {
					jwt = null; //force refetch
					return operation.call();
				}
				throw e;
			}
		}

		@SneakyThrows
		public Collection<String> missing(Collection<? extends FileDigest> files) {
			return refreshToken(() -> missingInternal(files));
		}

		Collection<String> missingInternal(Collection<? extends FileDigest> files) throws IOException, InterruptedException, URISyntaxException {
			var request = getJwtAuthRequestBuilder().uri(uriOf("/pages/assets/check-missing"))
				.header("Content-Type", "application/json; charset=utf-8")
				.POST(JsonBodyHandler.jsonPublisher(generator -> {
					generator.writeStartObject();
					{
						generator.writeStartArray("hashes");
						files.stream().map(FileDigest::getDigest).forEach(generator::write);
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
		public boolean upsert(Collection<? extends FileDigest> files) {
			return refreshToken(() -> upsertInternal(files));
		}

		boolean upsertInternal(Collection<? extends FileDigest> files) throws URISyntaxException, IOException, InterruptedException {
			var request = getJwtAuthRequestBuilder().uri(uriOf("/pages/assets/upsert-hashes"))
				.header("Content-Type", "application/json; charset=utf-8")
				.POST(JsonBodyHandler.jsonPublisher(generator -> {
					generator.writeStartObject();
					{
						generator.writeStartArray("hashes");
						files.stream().map(FileDigest::getDigest).forEach(generator::write);
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
		public <T extends FileDigest & FileData> boolean upload(Collection<T> files) {
			return refreshToken(() -> uploadInternal(files));
		}

		<T extends FileDigest & FileData> boolean uploadInternal(Collection<T> files) throws URISyntaxException, IOException, InterruptedException {
			log.info("Uploading file: {}", files.size());
			HttpRequest request = getJwtAuthRequestBuilder()
				.uri(uriOf("/pages/assets/upload"))
				.header("Content-Type", "application/json; charset=utf-8")
				.POST(JsonBodyHandler.jsonPublisher(generator -> {
					generator.writeStartArray();
					for (T fileData : files) {
						generator.writeStartObject();
						generator.write("key", fileData.getDigest());
						var mimeAndBase64 = getMimeAndBase64Content(fileData);
						{
							generator.writeStartObject("metadata");
							generator.write("contentType", mimeAndBase64.getKey());
							generator.writeEnd();
						}
						generator.write("base64", true);
						generator.write("value", mimeAndBase64.getValue());
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
			if (jwt == null) {
				jwt = jwtSupplier.get();
			}
			return getRequestBuilder().header("Authorization", "Bearer " + jwt);
		}
	}

	@SneakyThrows
	private static Map.Entry<String, String> getMimeAndBase64Content(FileData fileData) {
		// BufferedInputStream supports marks, used in java.net.URLConnection#guessContentTypeFromStream()
		try (var is = new BufferedInputStream(fileData.inputStream())) {
			String mime = getContentType(fileData.getRootRelativePath(), is);
			var bos = new ByteArrayOutputStream();
			try (var base64os = BASE64_ENCODER.wrap(bos)) {
				is.transferTo(base64os);
				return Map.entry(mime, bos.toString());
			}
		}
	}

	private static byte[] createFormDataBody(Collection<? extends FileDigest> files, String boundary) throws IOException {
		var os = new ByteArrayOutputStream();
		os.writeBytes("--".getBytes());
		os.write(boundary.getBytes());
		os.write("\r\n".getBytes());
		os.writeBytes("Content-Disposition: form-data; name=\"manifest\"".getBytes());
		os.write("\r\n".getBytes());
		os.write("\r\n".getBytes());
		try (var generator = Json.createGenerator(os)) {
			generator.writeStartObject();
			for (FileDigest file : files) {
				generator.write(file.getRootRelativePath(), file.getDigest());
			}
			generator.writeEnd();
		}
		os.write("\r\n".getBytes());
		os.writeBytes("--".getBytes());
		os.write(boundary.getBytes());
		os.writeBytes("--".getBytes());
		return os.toByteArray();
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

	private URI uriOf(String path) throws URISyntaxException {
		return new URI(baseUrl.getScheme(), null, baseUrl.getHost(), baseUrl.getPort(), baseUrl.getRawPath() + path, null, null);
	}

	@SuppressWarnings("unchecked")
	public static <T> HttpResponse.BodySubscriber<T> handleError(HttpResponse.BodyHandler<T> handler, HttpResponse.ResponseInfo resp) {
		int statusCode = resp.statusCode();
		if (statusCode / 100 == 2) { //isSucceed
			return handler.apply(resp);
		}
		return (HttpResponse.BodySubscriber<T>) HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofByteArray(), bytes -> Stream.generate(() -> {
			throw JsonBodyHandler.parseFirst(new ByteArrayInputStream(bytes))
				.map(JsonValue::asJsonObject)
				.map(json -> json.getJsonArray("errors"))
				.filter(json -> json.size() != 1)
				.stream()
				.flatMap(json -> json.getValuesAs(JsonObject.class).stream())
				.findAny()
				.map(json -> errorResponse(statusCode, json.getInt("code", -1), json.getString("message", "<empty>")))
				.or(() -> Optional.of(DeployerHttpException.generic(statusCode, new String(bytes, UTF_8))))
				.orElseGet(() -> DeployerHttpException.generic(statusCode, "body: <no body>"));
		}));
	}
}
