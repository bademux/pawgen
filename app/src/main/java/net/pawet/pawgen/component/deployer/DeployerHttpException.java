package net.pawet.pawgen.component.deployer;

import lombok.Getter;
import lombok.ToString;

import java.net.http.HttpHeaders;
import java.time.format.DateTimeFormatter;

import static java.lang.String.format;

@ToString
@Getter
public class DeployerHttpException extends RuntimeException {

	private final int httpStatusCode;

	static DeployerHttpException rateLimit(int statusCode, HttpHeaders headers) {
		return new DeployerRateLimitHttpException( statusCode,
			headers.firstValueAsLong("X-RateLimit-Remaining").orElse(-1),
			headers.firstValueAsLong("X-RateLimit-Limit").orElse(-1),
			headers.firstValueAsLong("X-RateLimit-Reset").orElse(-1));
	}

	static DeployerHttpException generic(int statusCode, String responseBody) {
		return new DeployerHttpException(statusCode, responseBody, null);
	}
	static DeployerHttpException errorResponse(int httpStatusCode, int code, String message) {
		return new DeployerErrorResponseHttpException(httpStatusCode, code, message);
	}

	static DeployerHttpException wtf(int statusCode, String message, Throwable cause) {
		return new DeployerHttpException(statusCode, message, cause);
	}

	private DeployerHttpException(int httpStatusCode, String message, Throwable cause) {
		super(message, cause);
		this.httpStatusCode = httpStatusCode;
	}

	@ToString
	@Getter
	public static class DeployerRateLimitHttpException extends DeployerHttpException {

		private final static DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[]");
		private final long remaining;
		private final long limit;
		private final long reset; //delta-seconds

		public DeployerRateLimitHttpException(int httpStatusCode, long remaining, long limit, long reset) {
			super(httpStatusCode, format("Remained %d and the limit is %d. Wait till %s to reset", remaining, limit, reset), null);
			this.remaining = remaining;
			this.limit = limit;
			this.reset = reset;
		}
	}

	@ToString
	@Getter
	public static class DeployerErrorResponseHttpException extends DeployerHttpException {

		private final int code;

		private DeployerErrorResponseHttpException(int httpStatusCode, int code, String message) {
			super(httpStatusCode, message, null);
			this.code = code;
		}

	}

}
