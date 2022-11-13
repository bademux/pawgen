package net.pawet.pawgen.utils;

import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import static java.time.ZoneId.systemDefault;
import static java.time.temporal.ChronoField.*;

public final class AppConsoleHandler extends ConsoleHandler {

	private final static DateTimeFormatter DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
		.appendValue(HOUR_OF_DAY, 2)
		.appendLiteral(':')
		.appendValue(MINUTE_OF_HOUR, 2)
		.optionalStart()
		.appendLiteral(':')
		.appendValue(SECOND_OF_MINUTE, 2)
		.optionalStart()
		.appendFraction(NANO_OF_SECOND, 9, 9, true)
		.toFormatter()
		.withZone(systemDefault());

	public AppConsoleHandler() {
		try {
			setEncoding(StandardCharsets.UTF_8.toString());
		} catch (UnsupportedEncodingException ignored) {
		}
		setOutputStream(System.out);
		setFormatter(new Formatter() {

			@Override
			public String format(LogRecord record) {
				var result = new StringBuilder();
				DATE_TIME_FORMATTER.formatTo(record.getInstant(), result);
				result.append(' ').append(formatMessage(record));
				Throwable thrown = record.getThrown();
				if (thrown != null) {
					result.append('\n');
					try (PrintWriter pw = new PrintWriter(new StringBuilderWriter(result))) {
						thrown.printStackTrace(pw);
					}
				}
				return result.append('\n').toString();
			}
		});
	}

}

