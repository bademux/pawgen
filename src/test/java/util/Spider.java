//package util;
//
//import lombok.SneakyThrows;
//import lombok.extern.slf4j.Slf4j;
//
//import java.io.IOException;
//import java.net.URI;
//import java.net.URISyntaxException;
//import java.net.http.HttpClient;
//import java.net.http.HttpRequest;
//import java.net.http.HttpResponse;
//import java.nio.file.Path;
//import java.time.Duration;
//import java.util.*;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.Executors;
//import java.util.function.Predicate;
//import java.util.regex.Pattern;
//import java.util.stream.Stream;
//import java.util.zip.ZipException;
//
//import static java.net.http.HttpResponse.BodyHandlers.ofString;
//import static java.util.stream.Collectors.toList;
//import static java.util.stream.Collectors.toSet;
//
//@Slf4j
//public class Spider {
//
//	private final static Set<URI> processed = new HashSet<>();
//	private final static ZipFileSaver zipFileSaver = ZipFileSaver.create(Path.of("./src/test/resources/pawet.net.zip"));
//	private final static HttpClient httpClient = HttpClient.newBuilder()
//		.followRedirects(HttpClient.Redirect.ALWAYS)
//		.connectTimeout(Duration.ofSeconds(30))
//		.build();
//
//	public static void main(String... args) {
//		Runtime.getRuntime().addShutdownHook(new Thread(zipFileSaver::close));
//		Collection<URI> uris = List.of(URI.create("http://pawet.net"));
//		while (!uris.isEmpty()) {
//			uris = uris.stream()
//				.filter(uri1 -> !uri1.getPath().startsWith("/zl/"))
//				.filter(Predicate.not(processed::contains))
//				.flatMap(Spider::httpRequest)
//				.collect(toSet());
//		}
//	}
//
//	@SneakyThrows
//	private static Stream<URI> httpRequest(URI uri) {
//		String body = null;
//		try {
//			body = handleResponse(httpClient.send(createRequestTemplate(uri), ofString()));
//		} catch (Exception e){
//			e.printStackTrace();
//		}
//		if (body == null) {
//			return Stream.empty();
//		}
//		System.out.printf("Processing '%s'\n", uri);
//		save(uri, body);
//		return parseForLinks(body)
//			.map(rawTarget -> parseUri(rawTarget, uri))
//			.filter(Objects::nonNull)
//			.filter(Spider::isHtml);
//	}
//
//	@SneakyThrows
//	private static void save(URI uri, String content) {
//		if (!processed.add(uri)) {
//			return;
//		}
//		try {
//			zipFileSaver.add(uri, content);
//		} catch (ZipException e) {
//			if (!e.getMessage().startsWith("duplicate entry:")) {
//				throw e;
//			}
//		}
//	}
//
//	private static String handleResponse(HttpResponse<String> httpResponse) {
//		int statusCode = httpResponse.statusCode();
//		if (statusCode / 100 != 2) { // redirects are handled on HttpClient level
//			throw new IllegalStateException("Request to returns bad statusCode '" + statusCode + "' for uri: " + httpResponse.uri());
//		}
//		if (httpResponse.headers().firstValue("Content-Type")
//			.filter(Spider::isHtmlContentType)
//			.isEmpty()) {
//			return null;
//		}
//		return httpResponse.body();
//	}
//
//	@SneakyThrows
//	private static URI parseUri(String rawTarget, URI base) {
//		URI target;
//		try {
//			target = new URI(rawTarget);
//		} catch (URISyntaxException e) {
//			log.debug("Can't create uri for '{}' founded on '{}'", rawTarget, base, e);
//			return null;
//		}
//		// ignore unknown protocols
//		String targetScheme = target.getScheme();
//		if (targetScheme != null && !targetScheme.equals("http") && !targetScheme.equals("https")) {
//			return null;
//		}
//		//handle relative to host links
//		String targetHost = target.getHost();
//		// ignore urls to external sites
//		if (targetHost != null && !targetHost.equals(base.getHost())) {
//			return null;
//		}
//		if (targetHost == null || targetHost.isEmpty()) {
//			//handle relative to root or relative to base path
//			String path = createRelativePath(target.getPath(), base.getPath());
//			return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), path, base.getQuery(), null);
//		}
//		return new URI(target.getScheme(), target.getUserInfo(), target.getHost(), target.getPort(), target.getPath(), base.getQuery(), null);
//	}
//
//	private static String createRelativePath(String targetPath, String basePath) {
//		if (targetPath.startsWith("/")) {
//			return targetPath;
//		}
//		if (basePath.endsWith("/")) {
//			return basePath + targetPath;
//		}
//		Path parent = Path.of(basePath).getParent();
//		if (parent != null) {
//			return parent.resolve(targetPath).toString();
//		}
//		return targetPath;
//	}
//
//	private static Stream<String> parseForLinks(String body) {
//		return Pattern.compile("href=\"(.*?)\"", Pattern.DOTALL).matcher(body).results()
//			.map(matchResult -> matchResult.group(1))
//			.filter(Objects::nonNull)
//			.map(String::trim)
//			.filter(Predicate.not(String::isBlank));
//	}
//
//	private static boolean isHtmlContentType(String contentTypeValue) {
//		String contentType = "text/html";
//		return contentType.regionMatches(true, 0, contentTypeValue, 0, contentType.length());
//	}
//
//	private static boolean isHtml(URI uri) {
//		String uriStr = uri.getPath();
//		String ext = ".html";
//		return ext.regionMatches(true, 0, uriStr, uriStr.length() - ext.length(), ext.length());
//	}
//
//	private static HttpRequest createRequestTemplate(URI uri) {
//		return HttpRequest.newBuilder()
//			.uri(uri)
//			.header("User-Agent", "PawSpider/1")
////			.timeout(Duration.ofSeconds(30))
//			.GET()
//			.build();
//	}
//}
//
