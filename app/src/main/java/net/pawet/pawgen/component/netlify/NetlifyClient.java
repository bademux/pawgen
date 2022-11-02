package net.pawet.pawgen.component.netlify;

import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.ResponseInfo;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.net.http.HttpClient.Redirect.NORMAL;
import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.net.http.HttpRequest.BodyPublishers;
import static java.net.http.HttpRequest.BodyPublishers.noBody;
import static java.net.http.HttpRequest.BodyPublishers.ofInputStream;
import static java.net.http.HttpRequest.newBuilder;
import static java.net.http.HttpResponse.BodySubscribers;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.PRIVATE;
import static net.pawet.pawgen.component.netlify.NetlifyHttpException.errorResponse;

/**
 * https://github.com/netlify/open-api/blob/v0.18.1/swagger.yml
 */
@Slf4j
public final class NetlifyClient {

	public static final URI NETLIFY_BASE_URL = URI.create("https://api.netlify.com/api/v1");
	private final HttpClient client = HttpClient.newBuilder()
		.followRedirects(NORMAL)
		.version(HTTP_1_1)
		.connectTimeout(Duration.ofSeconds(31)) //netlify sets 30s per connection
		.build();

	private final URI baseUrl;
	private final BodyHandler<Stream<JsonValue>> bodyHandler = JsonBodyHandler.withErrorHandling();
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
				.POST(withObject(generator -> {
					generator.write("async", true);
					generator.writeStartObject("files");
					files.forEach(file -> generator.write(file.getRootRelativePath(), file.getDigest()));
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

	private static BodyPublisher withObject(Consumer<JsonGenerator> consumer) {
		var os = new ByteArrayOutputStream() {
			byte[] getRawBuf() {
				return buf;
			}

			int getLength() {
				return count;
			}
		};
		try (var generator = Json.createGenerator(os)) {
			generator.writeStartObject();
			consumer.accept(generator);
			generator.writeEnd();
		}
		return BodyPublishers.ofByteArray(os.getRawBuf(), 0, os.getLength());
	}

	private URI uriOf(String path, String queryParams) throws URISyntaxException {
		return new URI(baseUrl.getScheme(), null, baseUrl.getHost(), baseUrl.getPort(), baseUrl.getRawPath() + path, queryParams, null);
	}

}

/**
 * https://docs.oracle.com/en/java/javase/13/docs/api/java.net.http/java/net/http/HttpResponse.BodySubscribers.html#mapping(java.net.http.HttpResponse.BodySubscriber,java.util.function.Function)
 */
@Slf4j
@RequiredArgsConstructor
final class JsonBodyHandler implements BodyHandler<Stream<JsonValue>> {

	private static final JsonParserFactory PARSER_FACTORY = Json.createParserFactory(null);

	@Override
	public BodySubscriber<Stream<JsonValue>> apply(ResponseInfo resp) {
		return BodySubscribers.mapping(BodySubscribers.ofInputStream(), is -> parse(getCharset(resp), is));
	}

	private Charset getCharset(ResponseInfo resp) {
		return resp.headers().firstValue("Content-Type").map(this::parseCharset).orElse(UTF_8);
	}

	/**
	 * naive, yet good enough contentType.charset attribute header parse, example: "text/html;charset=windows-1252,"
	 */
	private Charset parseCharset(String contentType) {
		String prefix = "charset=";
		int start = contentType.indexOf(prefix);
		if (start != -1) {
			start += prefix.length();
			int end = contentType.indexOf(',', start);
			String csn = (end == -1 ? contentType.substring(start) : contentType.substring(start, end)).trim();
			if (Charset.isSupported(csn)) {
				return Charset.forName(csn);
			}
		}
		return UTF_8;
	}

	static Stream<JsonValue> parse(Charset charset, InputStream is) {
		JsonParser parser = PARSER_FACTORY.createParser(is, charset);
		return parser.getValueStream().onClose(parser::close);
	}

	static Optional<JsonValue> parseFirst(InputStream is) {
		try {
			return parse(UTF_8, is).findAny();
		} catch (Exception ignore) {
			return Optional.empty();
		}
	}

	public static BodyHandler<Stream<JsonValue>> withErrorHandling() {
		var jsonBodyHandler = new JsonBodyHandler();
		return resp -> handleError(jsonBodyHandler, resp);
	}

	@SuppressWarnings("unchecked")
	public static <T> BodySubscriber<T> handleError(BodyHandler<T> handler, ResponseInfo resp) {
		int statusCode = resp.statusCode();
		if (statusCode / 100 == 2) { //isSucceed
			return handler.apply(resp);
		} else if (statusCode == 404) {
			return BodySubscribers.replacing((T) Stream.empty());
		} else if (statusCode == 429) {
			return BodySubscribers.replacing((T) Stream.generate(() -> {
				throw NetlifyHttpException.rateLimit(statusCode, resp.headers());
			}));
		}
		return (BodySubscriber<T>) BodySubscribers.mapping(BodySubscribers.ofByteArray(), bytes -> Stream.generate(() -> {
			throw JsonBodyHandler.parseFirst(new ByteArrayInputStream(bytes))
				.map(JsonValue::asJsonObject)
				.map(json -> errorResponse(statusCode, json))
				.or(() -> Optional.of(NetlifyHttpException.generic(statusCode, new String(bytes, UTF_8))))
				.orElseGet(() -> NetlifyHttpException.generic(statusCode, "body: <no body>"));
		}));
	}

}
