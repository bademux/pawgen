package net.pawet.pawgen.component.deployer;

import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.net.http.HttpClient.Redirect.NORMAL;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpRequest.BodyPublishers.noBody;
import static java.net.http.HttpRequest.BodyPublishers.ofInputStream;
import static java.net.http.HttpRequest.newBuilder;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.PRIVATE;
import static net.pawet.pawgen.component.deployer.DeployerHttpException.errorResponse;
import static net.pawet.pawgen.component.deployer.JsonBodyHandler.jsonPublisher;

/**
 * https://github.com/netlify/open-api/blob/v0.18.1/swagger.yml
 */
@Slf4j
public final class NetlifyClient {

	public static final URI NETLIFY_BASE_URL = URI.create("https://api.netlify.com/api/v1");
	private final HttpClient client = HttpClient.newBuilder()
		.executor(Executors.newVirtualThreadPerTaskExecutor())
		.followRedirects(NORMAL)
		.version(HTTP_1_1)
		.connectTimeout(Duration.ofSeconds(31)) //netlify sets 30s per connection
		.build();

	private final URI baseUrl;
	private final BodyHandler<Stream<JsonValue>> bodyHandler = JsonBodyHandler.withErrorHandling(NetlifyClient::handleError);
	private final Supplier<Builder> requestFactory;

	public NetlifyClient(String accessToken) {
		this(NETLIFY_BASE_URL, accessToken);
	}

	public NetlifyClient(URI baseUrl, String accessToken) {
		this.baseUrl = baseUrl;
		this.requestFactory = newBuilder().header("Authorization", "Bearer " + accessToken).setHeader("User-Agent", "url/7.68.0")::copy;
	}

	public DeployOperation deploy(String deployId) {
		return new DeployOperation(requireNonNull(deployId));
	}

	public SiteDeployOperation siteDeploy(String siteId) {
		return new SiteDeployOperation(requireNonNull(siteId));
	}

	@RequiredArgsConstructor(access = PRIVATE)
	public final class DeployOperation {

		private final String deployId;

		@SneakyThrows
		public Optional<JsonObject> find() {
			var request = requestFactory.get().uri(uriOf("/deploys/" + deployId, null)).GET().build();
			try (var valueStream = client.send(request, bodyHandler).body()) {
				return valueStream.map(JsonValue::asJsonObject).findAny();
			}
		}

		@SneakyThrows
		public JsonObject cancel() {
			var request = requestFactory.get().uri(uriOf("/deploys/" + deployId + "/cancel", null)).POST(noBody()).build();
			try (var valueStream = client.send(request, bodyHandler).body()) {
				return valueStream.map(JsonValue::asJsonObject).findAny().orElseThrow();
			}
		}

		@SneakyThrows
		public long upload(FileData file) {
			log.info("Uploading file: {}", file);
			HttpRequest request = requestFactory.get()
				.uri(uriOf("/deploys/" + deployId + "/files/" + requireNonNull(file).getRootRelativePath(), null))
				.header("Content-Type", "application/octet-stream")
				.PUT(ofInputStream(file::inputStream))
				.build();
			return client.send(request, bodyHandler).body().findAny()
				.map(JsonValue::asJsonObject)
				.map(json -> json.getJsonNumber("size"))
				.map(JsonNumber::longValue)
				.orElse(0L);
		}
	}

	@RequiredArgsConstructor(access = PRIVATE)
	public final class SiteDeployOperation {

		private final String siteId;

		@SneakyThrows
		public Optional<JsonObject> createAsync(String title, Collection<? extends FileDigest> files) {
			var request = requestFactory.get()
				.uri(uriOf("/sites/" + siteId + "/deploys", title == null ? null : "title=" + title))
				.header("Content-Type", "application/json; charset=utf-8")
				.POST(jsonPublisher(generator -> {
					generator.writeStartObject();
					{
						generator.write("async", true);
						generator.writeStartObject("files");
						files.forEach(file -> generator.write(file.getRootRelativePath(), file.getDigest()));
						generator.writeEnd();
					}
					generator.writeEnd();
				}))
				.build();
			try (var valueStream = client.send(request, bodyHandler).body()) {
				return valueStream.map(JsonValue::asJsonObject).findAny();
			}
		}

		@SneakyThrows
		public Stream<JsonObject> list(String state, int resultPerPage) {
			var request = requestFactory.get()
				.uri(uriOf("/sites/" + siteId + "/deploys", "per_page=" + resultPerPage + (state == null ? "" : "&state=" + state)))
				.GET()
				.build();
			return client.send(request, bodyHandler).body()
				.map(JsonValue::asJsonArray)
				.flatMap(Collection::stream)
				.map(JsonValue::asJsonObject);
		}

		@SneakyThrows
		public Stream<JsonObject> files() {
			var request = requestFactory.get()
				.uri(uriOf("/sites/" + siteId + "/files", null))
				.GET()
				.build();
			return client.send(request, bodyHandler).body()
				.map(JsonValue::asJsonArray)
				.flatMap(Collection::stream)
				.map(JsonValue::asJsonObject);
		}

		@SneakyThrows
		public Optional<JsonObject> find() {
			var request = requestFactory.get().uri(uriOf("/sites/" + siteId, null)).GET().build();
			try (var valueStream = client.send(request, bodyHandler).body()) {
				return valueStream.map(JsonValue::asJsonObject).findAny();
			}
		}

	}

	private URI uriOf(String path, String queryParams) throws URISyntaxException {
		return new URI(baseUrl.getScheme(), null, baseUrl.getHost(), baseUrl.getPort(), baseUrl.getRawPath() + path, queryParams, null);
	}

	@SuppressWarnings("unchecked")
	public static <T> HttpResponse.BodySubscriber<T> handleError(HttpResponse.BodyHandler<T> handler, HttpResponse.ResponseInfo resp) {
		int statusCode = resp.statusCode();
		if (statusCode / 100 == 2) { //isSucceed
			return handler.apply(resp);
		} else if (statusCode == 404) {
			return HttpResponse.BodySubscribers.replacing((T) Stream.empty());
		} else if (statusCode == 429) {
			return HttpResponse.BodySubscribers.replacing((T) Stream.generate(() -> {
				throw DeployerHttpException.rateLimit(statusCode, resp.headers());
			}));
		}
		return (HttpResponse.BodySubscriber<T>) HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofByteArray(), bytes -> Stream.generate(() -> {
			throw JsonBodyHandler.parseFirst(new ByteArrayInputStream(bytes))
				.map(JsonValue::asJsonObject)
				.map(json -> errorResponse(statusCode, json.getInt("code", -1), json.getString("message", "<empty>")))
				.or(() -> Optional.of(DeployerHttpException.generic(statusCode, new String(bytes, UTF_8))))
				.orElseGet(() -> DeployerHttpException.generic(statusCode, "body: <no body>"));
		}));
	}

}

