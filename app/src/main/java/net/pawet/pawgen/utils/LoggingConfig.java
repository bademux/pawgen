package net.pawet.pawgen.utils;

import lombok.SneakyThrows;

import java.io.BufferedInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.LogManager;

public class LoggingConfig {

	static {
		readLoggingConfiguration();
	}

	static void readLoggingConfiguration() {
		var logManager = LogManager.getLogManager();
		try (var in = new BufferedInputStream(Files.newInputStream(getLoggingConfPath()))) {
			logManager.readConfiguration(in);
			return;
		} catch (Exception ignored) {
		}
		System.err.println("fallback to default JDK logging config");
		System.setProperty("java.util.logging.config.class", ""); // reset config class
		try {
			logManager.readConfiguration();
		} catch (Exception ignored) {
			System.err.println("No logging config loaded");
		}
	}

	@SneakyThrows
	static Path getLoggingConfPath() {
		String loggingPropFile = "logging.properties";
		//read from config for URI
		var loggingConfPath = parseLoggingConfPath(System.getProperty("java.util.logging.config.file", loggingPropFile))
			.toAbsolutePath()
			.normalize();
		if (Files.exists(loggingConfPath)) {
			return loggingConfPath;
		}
		URL resource = LoggingConfig.class.getClassLoader().getResource(loggingPropFile);
		return resource == null ? Path.of(loggingPropFile) : Path.of(resource.toURI());
	}

	private static Path parseLoggingConfPath(String property) {
		try {
			URI loggingPropUri = new URI(property);
			if (loggingPropUri.getScheme() != null) {
				return Path.of(loggingPropUri);
			}
		} catch (URISyntaxException ignored) {
		}
		return Path.of(property);
	}

}

