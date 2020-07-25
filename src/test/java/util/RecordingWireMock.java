package util;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.common.TextFile;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.recording.RecordingStatus;
import lombok.Getter;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;

import static com.github.tomakehurst.wiremock.core.WireMockApp.FILES_ROOT;
import static com.github.tomakehurst.wiremock.core.WireMockApp.MAPPINGS_ROOT;

public final class RecordingWireMock implements AutoCloseable {

	@Getter
	private final WireMockServer wireMock;
	private final String serverUrl;
	private final boolean forceRecord;
	private final FileSource mappingsDir;

	public RecordingWireMock(WireMockConfiguration conf, URI targetServerUrl, boolean forceRecord) {
		this.wireMock = new WireMockServer(conf);
		this.serverUrl = targetServerUrl.toASCIIString();
		this.forceRecord = forceRecord;
		this.mappingsDir = wireMock.getOptions().filesRoot().child(MAPPINGS_ROOT);
	}

	public RecordingWireMock(WireMockConfiguration conf, URI targetServerUrl) {
		this(conf, targetServerUrl, Boolean.getBoolean("--force-record"));
	}

	public int getPort() {
		return wireMock.port();
	}

	public RecordingWireMock start() {
		wireMock.start();
		handleForceRecord();
		if (isRecordingMode()) {
			prepareFolders();
			startRecordingForDefinedServer();
		}
		return this;
	}

	public void stop() {
		stopRecordingIfNeeded();
		wireMock.stop();
		wireMock.resetAll();
	}


	private void stopRecordingIfNeeded() {
		if (RecordingStatus.Recording.equals(wireMock.getRecordingStatus().getStatus())) {
			mappingsDir.createIfNecessary();
			wireMock.getOptions().filesRoot().child(FILES_ROOT).createIfNecessary();
			wireMock.snapshotRecord();
		}
	}

	private void handleForceRecord() {
		if (forceRecord) {
			try {
				cleanupMappings();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private void prepareFolders() {
		mappingsDir.createIfNecessary();
	}

	private boolean isRecordingMode() {
		return !mappingsDir.exists() || mappingsDir.listFilesRecursively().isEmpty();
	}

	private void startRecordingForDefinedServer() {
		wireMock.startRecording(serverUrl);
	}

	private void cleanupMappings() throws IOException {
		if (!mappingsDir.exists()) {
			return;
		}
		for (TextFile textFile : mappingsDir.listFilesRecursively()) {
			Files.delete(Paths.get(textFile.getPath()));
		}
	}

	@Override
	public void close() {
		stop();
	}
}
