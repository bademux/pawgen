package net.pawet.pawgen.component.render;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_TIME;

@Slf4j
@UtilityClass
class TemplateFunctions {

	private final static Base64.Encoder BASE64_ENCODER = Base64.getEncoder();
	private final static Map<String, DateTimeFormatter> FORMATTERS = new ConcurrentHashMap<>();
	private final static DateTimeFormatter PARSER = new DateTimeFormatterBuilder()
		.append(DateTimeFormatter.ISO_LOCAL_DATE)
		.optionalStart()
		.optionalStart().appendLiteral('T').optionalEnd()
		.optionalStart().appendLiteral(' ').optionalEnd()
		.append(ISO_LOCAL_TIME)
		.optionalStart().appendOffsetId().optionalEnd()
		.optionalStart(). appendZoneId().optionalEnd()
		.optionalEnd()
		.toFormatter();

	public static CharSequence format(String value) {
		int delimPos = value.lastIndexOf('|');
		int beginFormat = delimPos + 1;
		boolean hasArguments = !(delimPos == -1 || beginFormat == value.length());
		var formatter = getFormatter(value.substring(beginFormat).trim(), hasArguments);
		String data = (hasArguments ? value.substring(0, delimPos) : value).trim();
		try {
			var temporal = PARSER.parseBest(data, ZonedDateTime::from, OffsetDateTime::from, LocalDateTime::from);
			return formatter.format(temporal);
		} catch (Exception e) {
			log.error("Can't parse date '{}'", value, e);
		}
		return "";
	}

	private static DateTimeFormatter getFormatter(String value, boolean hasArguments) {
		if (hasArguments) {
			var formatter = FORMATTERS.computeIfAbsent(value, TemplateFunctions::createFormatterOrDefault);
			if (formatter != null) {
				return formatter;
			}
		}
		return DateTimeFormatter.ISO_DATE_TIME;
	}

	private static DateTimeFormatter createFormatterOrDefault(String format) {
		try {
			return DateTimeFormatter.ofPattern(format).withZone(ZoneId.of("UTC"));
		} catch (Exception e) {
			log.error("Cant parse date format '{}'", format, e);
		}
		return null;
	}

	public static CharSequence embed(Function<String, ReadableByteChannel> resourceReader, String value) {
		int delimPos = value.lastIndexOf('|');
		int beginFormat = delimPos + 1;
		boolean hasNoArguments = delimPos == -1 || beginFormat == value.length();
		String args = hasNoArguments ? null : value.substring(beginFormat);
		String data = hasNoArguments ? value : value.substring(0, delimPos);
		try (var in = resourceReader.apply(data)) {
			ByteBuffer buffer = ByteBuffer.allocate(1024);
			in.read(buffer);
			buffer.flip();
			return UTF_8.decode("base64".equals(args) ? BASE64_ENCODER.encode(buffer) : buffer);
		} catch (Exception e) {
			log.error("Can't embed '{}'", value, e);
		}
		return "";
	}

}
