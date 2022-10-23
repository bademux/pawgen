package net.pawet.pawgen.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public final class AppConsoleHandler extends ConsoleHandler {

	public AppConsoleHandler() {
		try {
			setEncoding(StandardCharsets.UTF_8.toString());
		} catch (UnsupportedEncodingException ignored) {
		}
		setOutputStream(System.out);
		setFormatter(new Formatter() {

			@Override
			public String format(LogRecord record) {
				var resul = new StringWriter()
					.append(DateTimeFormatter.ISO_INSTANT.format(record.getInstant()))
					.append(' ').append(formatMessage(record));
				Throwable thrown = record.getThrown();
				if (thrown != null) {
					resul.append('\n');
					try (PrintWriter pw = new PrintWriter(resul)) {
						thrown.printStackTrace(pw);
					}
				}
				return resul.append('\n').toString();
			}
		});
	}

}
