package net.pawet.pawgen.deployer;

import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.deployer.deployitem.Content;
import net.pawet.pawgen.deployer.deployitem.Digest;
import net.pawet.pawgen.deployer.deployitem.Path;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.net.http.HttpClient.Redirect.NORMAL;
import static java.net.http.HttpRequest.BodyPublishers.noBody;
import static java.net.http.HttpRequest.BodyPublishers.ofInputStream;
import static java.net.http.HttpRequest.newBuilder;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static net.pawet.pawgen.deployer.DeployerHttpException.errorResponse;
import static net.pawet.pawgen.deployer.JsonBodyHandler.jsonPublisher;

/**
 * https://github.com/netlify/open-api/blob/v0.18.1/swagger.yml
 */
@Slf4j
public final class NetlifyClient {

	public static final URI NETLIFY_BASE_URL = URI.create("https://api.netlify.com/api/v1/");
	private final HttpClient client = HttpClient.newBuilder()
		.followRedirects(NORMAL)
		.connectTimeout(Duration.ofSeconds(31)) //netlify sets 30s per connection
		.build();

	private final URI baseUrl;
	private final BodyHandler<Stream<JsonValue>> bodyHandler = JsonBodyHandler.withErrorHandling(NetlifyClient::handleError);
	private final Supplier<Builder> requestFactory;

	public NetlifyClient(String accessToken) {
		this(NETLIFY_BASE_URL, accessToken);
	}

	public NetlifyClient(URI baseUrl, String accessToken) {
		if (!baseUrl.getPath().endsWith("/")) {
			throw new IllegalArgumentException("uri path have to end with '/' " + baseUrl);
		}
		this.baseUrl = baseUrl;
		this.requestFactory = newBuilder().header("Authorization", "Bearer " + accessToken).setHeader("User-Agent", "url/7.68.0")::copy;
	}

	public DeployOperation deploy(String deployId) {
		return new DeployOperation(deployId);
	}

	public SiteDeployOperation siteDeploy(String siteId) {
		return new SiteDeployOperation(siteId);
	}

	public final class DeployOperation {

		private final URI deployUri;

		private DeployOperation(@NonNull String deployId) {
			this.deployUri = baseUrl.resolve("deploys/" + deployId + "/");
		}

		@SneakyThrows
		public Optional<JsonObject> find() {
			var request = requestFactory.get().uri(deployUri).GET().build();
			try (var valueStream = client.send(request, bodyHandler).body()) {
				return valueStream.map(JsonValue::asJsonObject).findAny();
			}
		}

		@SneakyThrows
		public JsonObject cancel() {
			var request = requestFactory.get().uri(deployUri.resolve("cancel")).POST(noBody()).build();
			try (var valueStream = client.send(request, bodyHandler).body()) {
				return valueStream.map(JsonValue::asJsonObject).findAny().orElseThrow();
			}
		}

		@SneakyThrows
		public <T extends Content & Path> long upload(T file) {
			log.info("Uploading file: {}", file);
			HttpRequest request = requestFactory.get()
				.uri(deployUri.resolve("files" + requireNonNull(file).getPath()))
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

	public final class SiteDeployOperation {

		private final URI siteUri;

		private SiteDeployOperation(@NonNull String siteId) {
			this.siteUri = baseUrl.resolve("sites/" + siteId + "/");
		}

		@SneakyThrows
		public <T extends Digest & Path> Optional<JsonObject> createAsync(String title, Collection<T> files) {
			var request = requestFactory.get()
				.uri(siteUri.resolve("deploys?title=" + title))
				.header("Content-Type", "application/json; charset=utf-8")
				.POST(jsonPublisher(generator -> {
					generator.writeStartObject();
					{
						generator.write("async", true);
						generator.writeStartObject("files");
						for (var file : files) {
							generator.write(file.getPath(), file.getDigest());
						}
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
				.uri(siteUri.resolve("deploys?per_page=" + resultPerPage + (state == null ? "" : "&state=" + state)))
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
				.uri(siteUri.resolve("files"))
				.GET()
				.build();
			return client.send(request, bodyHandler).body()
				.map(JsonValue::asJsonArray)
				.flatMap(Collection::stream)
				.map(JsonValue::asJsonObject);
		}

		@SneakyThrows
		public Optional<JsonObject> find() {
			var request = requestFactory.get().uri(siteUri).GET().build();
			try (var valueStream = client.send(request, bodyHandler).body()) {
				return valueStream.map(JsonValue::asJsonObject).findAny();
			}
		}

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

