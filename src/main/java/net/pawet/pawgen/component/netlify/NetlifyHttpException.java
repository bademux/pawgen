package net.pawet.pawgen.component.netlify;

import jakarta.json.JsonObject;
import lombok.Getter;
import lombok.ToString;

import java.net.http.HttpHeaders;
import java.time.format.DateTimeFormatter;

import static java.lang.String.format;

@ToString
@Getter
public class NetlifyHttpException extends RuntimeException {

	private final int httpStatusCode;

	static NetlifyHttpException rateLimit(int statusCode, HttpHeaders headers) {
		return new NetlifyRateLimitHttpException( statusCode,
			headers.firstValueAsLong("X-RateLimit-Remaining").orElse(-1),
			headers.firstValueAsLong("X-RateLimit-Limit").orElse(-1),
			headers.firstValueAsLong("X-RateLimit-Reset").orElse(-1));
	}

	static NetlifyHttpException generic(int statusCode, String responseBody) {
		return new NetlifyHttpException(statusCode, responseBody, null);
	}
	static NetlifyHttpException errorResponse(int httpStatusCode, JsonObject response) {
		return new NetlifyErrorResponseHttpException(httpStatusCode, response.getInt("code", -1), response.getString("message", "<empty>"));
	}

	static NetlifyHttpException wtf(int statusCode, String message, Throwable cause) {
		return new NetlifyHttpException(statusCode, message, cause);
	}

	private NetlifyHttpException(int httpStatusCode, String message, Throwable cause) {
		super(message, cause);
		this.httpStatusCode = httpStatusCode;
	}


	@ToString
	@Getter
	public static class NetlifyRateLimitHttpException extends NetlifyHttpException {

		private final static DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[]");
		private final long remaining;
		private final long limit;
		private final long reset; //delta-seconds

		public NetlifyRateLimitHttpException(int httpStatusCode, long remaining, long limit, long reset) {
			super(httpStatusCode, format("Remained %d and the limit is %d. Wait till %s to reset", remaining, limit, reset), null);
			this.remaining = remaining;
			this.limit = limit;
			this.reset = reset;
		}
	}

	@ToString
	@Getter
	public static class NetlifyErrorResponseHttpException extends NetlifyHttpException {

		private final int code;

		private NetlifyErrorResponseHttpException(int httpStatusCode, int code, String message) {
			super(httpStatusCode, message, null);
			this.code = code;
		}

	}

}
