package net.pawet.pawgen.deployer;

import jakarta.json.Json;
import jakarta.json.JsonValue;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.ResponseInfo;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * https://docs.oracle.com/en/java/javase/13/docs/api/java.net.http/java/net/http/HttpResponse.BodySubscribers.html#mapping(java.net.http.HttpResponse.BodySubscriber,java.util.function.Function)
 */
@Slf4j
@RequiredArgsConstructor
final class JsonBodyHandler implements BodyHandler<Stream<JsonValue>> {

	private static final JsonProvider JSON_PROVIDER = JsonProvider.provider();

	@Override
	public BodySubscriber<Stream<JsonValue>> apply(ResponseInfo resp) {
		return HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofInputStream(), is -> parse(getCharset(resp), is));
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

	public static <T> BodyHandler<T> withErrorHandling(BiFunction<BodyHandler<Stream<JsonValue>>, ResponseInfo, BodySubscriber<T>> errorHandler) {
		var jsonBodyHandler = new JsonBodyHandler();
		return resp -> errorHandler.apply(jsonBodyHandler, resp);
	}

	private static final JsonParserFactory PARSER_FACTORY = Json.createParserFactory(null);

	public static BodyPublisher jsonPublisher(Consumer<JsonGenerator> consumer) {
		var os = new ByteArrayOutputStream();
		toJson(consumer, os);
		return BodyPublishers.ofByteArray(os.toByteArray());
	}

	public static void toJson(Consumer<JsonGenerator> consumer, ByteArrayOutputStream os) {
		try (var generator = JSON_PROVIDER.createGenerator(os)) {
			consumer.accept(generator);
		}
	}

}
