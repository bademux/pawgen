package net.pawet.pawgen.deployer;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.teavm.jso.JSBody;
import org.teavm.jso.browser.Location;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.typedarrays.Uint8Array;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import static java.util.concurrent.TimeUnit.SECONDS;
import static lombok.AccessLevel.PRIVATE;

public final class Downloader {

	@SneakyThrows
	public static void main(String... __) {
		var document = HTMLDocument.current();
		var page = Page.create(document);
		page.status("Preparing");
		var uris = parsePartUrls(Location.current().getSearch());
		page.status("[%s] parts will be downloaded".formatted(uris.size()));
		var parts = new ArrayList<byte[]>(uris.size());
		for (URI uri : uris) {
			parts.add(download(uri));
		}
		String fileName = parseFilename(Location.current().getPathName());
		String mediaType = HttpURLConnection.guessContentTypeFromName(fileName);
		byte[] data = joinParts(parts);
		page.status("Downloaded");
		page.download(fileName, mediaType, data);

	}

	private static byte[] joinParts(List<byte[]> results) {
		int resultingSize = results.stream().mapToInt(value -> value.length).sum();
		var bb = ByteBuffer.allocate(resultingSize);
		results.forEach(bb::put);
		return bb.array();
	}

	private static String parseFilename(String pathName) {
		return pathName.substring(pathName.lastIndexOf('/') + 1);
	}

	private static byte[] download(URI uri) throws IOException {
		var con = (HttpURLConnection) uri.toURL().openConnection();
		con.setConnectTimeout((int) SECONDS.toMillis(10));
		con.setReadTimeout((int) SECONDS.toMillis(10));
		con.setInstanceFollowRedirects(true);
		con.setRequestMethod("GET");
		con.setRequestProperty("User-Agent", "pawgen-downloader/1.0");
		if (con.getResponseCode() % 100 == 2) {
			try (var is = con.getInputStream()) {
				return is.readAllBytes();
			}
		}
		return null;
	}

	private static List<URI> parsePartUrls(String queryStr) {
		var tokenizer = new StringTokenizer(queryStr, "=&");
		var urls = new ArrayList<URI>();
		while (tokenizer.hasMoreTokens()) {
			if ("url".equals(tokenizer.nextToken())) {
				urls.add(URI.create(tokenizer.nextToken()));
			}
		}
		return urls;
	}
}


@RequiredArgsConstructor(access = PRIVATE)
final class Page {

	private final HTMLElement status;
	private final HTMLElement downloadLink;

	public static Page create(HTMLDocument document) {
		var status = document.createElement("div");
		document.getBody().appendChild(status);
		var downloadLink = document.createElement("a");
		downloadLink.setHidden(true);
		document.getBody().appendChild(status);
		return new Page(status, downloadLink);
	}


	public void status(String msg) {
		status.setTextContent(msg);
	}

	public void download(String filename, String mediaType, byte[] data) {
		var uint8Array = Uint8Array.create(data.length);
		uint8Array.set(data);
		downloadLink.setAttribute("href", toBlobUrl(uint8Array, mediaType));
		downloadLink.setAttribute("download", filename);
		downloadLink.click();
		downloadLink.setHidden(false);
	}


	@JSBody(params = {"array", "mediaType"}, script = "return window.URL.createObjectURL(new Blob([array], {type: mediaType}));")
	private static native String toBlobUrl(Uint8Array array, String mediaType);
}
