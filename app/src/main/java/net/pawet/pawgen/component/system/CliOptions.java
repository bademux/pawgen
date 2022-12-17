package net.pawet.pawgen.component.system;

import build.Build;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.*;

@Slf4j
@Getter
@ToString(onlyExplicitlyIncluded = true)
@Builder
public final class CliOptions {
	private static final String OUTPUT_DIR = "./public";
	private static final String TEMPLATES_DIR = "./templates";
	private static final String STATIC_DIR = "./static";
	public static final Pattern COMMA_SEPARATED = Pattern.compile(",");

	@ToString.Include
	@NonNull
	private final URI contentUri;
	@Builder.Default
	@ToString.Include
	private final URI outputUri = createUri(System.getProperty("user.dir") + '/' + OUTPUT_DIR);;
	@Builder.Default
	@ToString.Include
	private final URI templatesUri = createUri(System.getProperty("user.dir") + '/' + TEMPLATES_DIR);
	@ToString.Include
	@Singular("staticUri")
	private final Collection<URI> staticUris;
	@ToString.Include
	private final String watermarkText;
	@ToString.Include
	private final URI watermarkUri;
	@ToString.Include
	private final Instant dateFrom;
	@ToString.Include
	@Singular
	private final Set<String> hosts;
	@ToString.Include
	private final String deployerNames;
	@ToString.Include
	private final URI netlifyUrl;
	private final String netlifyAccessToken;
	@ToString.Include
	private final String netlifySiteId;
	@ToString.Include
	private final URI cloudflarePagesUrl;
	private final String cloudflarePagesToken;
	@ToString.Include
	private final String cloudflarePagesAccountId;
	@ToString.Include
	private final String cloudflarePagesProjectName;

	public static final String USER_HOME = System.getProperty("user.home");

	public static CliOptions parse(List<String> args) {
		return args.stream()
			.filter(Objects::nonNull)
			.filter(not(String::isBlank))
			.collect(collectingAndThen(toList(), CliOptions::parseArgs));
	}

	public static Stream<String> handleError(Throwable e) {
		return Stream.iterate(e, Objects::nonNull, Throwable::getCause)
			.map(Throwable::getMessage)
			.filter(Objects::nonNull);
	}

	@SneakyThrows
	private static CliOptions parseArgs(List<String> args) {
		if (Stream.of("-v", "--version").anyMatch(((Collection<String>) args)::contains)) {
			throw new Throwable("Version: " + Build.VERSION);
		}
		try {
			if (Stream.of("-h", "--help").noneMatch((((Collection<String>) args)::contains))) {
				var optionsBuilder = CliOptions.builder();
				var propertyProvider = getConfigFilePropertyProvider(args);
				parseConfigFile(propertyProvider, optionsBuilder);
				parseDirOpts(args.stream().filter(not(isConfigFileName())), optionsBuilder);
				return optionsBuilder.build();
			}
		} catch (Throwable e) {
			log.error("Error while setup config", e);
		}
		String help = """
			Usage: pawgen contentDir [outputDir:%s] [templatesDir:%s] [staticDir:%s] [path_to/config.properties] [-h|--help] [-v|--version]
			If path to config.properties is provided than config.properties in app dir is ignored
			Config example:
			%s""".formatted(OUTPUT_DIR, TEMPLATES_DIR, STATIC_DIR, serializeAsPropertyFile(getDefaultConfig()));
		throw new Throwable(help);
	}

	private static CharSequence serializeAsPropertyFile(Map<String, String> conf) throws IOException {
		var props = new Properties();
		props.putAll(conf);
		var writer = new StringWriter();
		props.store(writer, null);
		return writer.getBuffer();
	}

	private static Predicate<String> isConfigFileName() {
		return fileName -> fileName.endsWith(".properties");
	}

	private static void parseConfigFile(Function<String, Optional<String>> propertyProvider, CliOptionsBuilder optionsBuilder) {
		propertyProvider.apply("watermark.text")
			.ifPresent(optionsBuilder::watermarkText);
		propertyProvider.apply("watermark.file")
			.flatMap(CliOptions::createUriOpt)
			.ifPresent(optionsBuilder::watermarkUri);
		propertyProvider.apply("contentDir")
			.map(CliOptions::createUri)
			.ifPresent(optionsBuilder::contentUri);
		propertyProvider.apply("outputDir")
			.map(CliOptions::createUri)
			.ifPresent(optionsBuilder::outputUri);
		propertyProvider.apply("templatesDir")
			.map(CliOptions::createUri)
			.ifPresent(optionsBuilder::templatesUri);
		propertyProvider.apply("staticDirs").stream()
			.flatMap(COMMA_SEPARATED::splitAsStream)
			.filter(Objects::nonNull)
			.filter(not(String::isBlank))
			.map(CliOptions::createUri)
			.forEach(optionsBuilder::staticUri);
		propertyProvider.apply("dateFrom")
			.map(Instant::parse)
			.ifPresent(optionsBuilder::dateFrom);
		propertyProvider.apply("hosts").stream()
			.flatMap(COMMA_SEPARATED::splitAsStream)
			.filter(Objects::nonNull)
			.filter(not(String::isBlank))
			.forEach(optionsBuilder::host);
		propertyProvider.apply("deployers")
			.ifPresent(optionsBuilder::deployerNames);
		propertyProvider.apply("netlify.url")
			.map(URI::create)
			.ifPresent(optionsBuilder::netlifyUrl);
		propertyProvider.apply("netlify.accessToken")
			.ifPresent(optionsBuilder::netlifyAccessToken);
		propertyProvider.apply("netlify.siteAppId")
			.ifPresent(optionsBuilder::netlifySiteId);
		propertyProvider.apply("cloudflarepages.url")
			.map(URI::create)
			.ifPresent(optionsBuilder::cloudflarePagesUrl);
		propertyProvider.apply("cloudflarepages.token")
			.ifPresent(optionsBuilder::cloudflarePagesToken);
		propertyProvider.apply("cloudflarepages.accountId")
			.ifPresent(optionsBuilder::cloudflarePagesAccountId);
		propertyProvider.apply("cloudflarepages.projectName")
			.ifPresent(optionsBuilder::cloudflarePagesProjectName);
	}

	private static Function<String, Optional<String>> getConfigFilePropertyProvider(Collection<String> args) {
		Function<String, String> f = args.stream().filter(isConfigFileName()).findAny()
			.map(s -> {
				try {
					return new URI(s);
				} catch (URISyntaxException e) {
					return null;
				}
			})
			.map(Path::of)
			.or(() -> Optional.of("./config.properties").map(Path::of))
			.map(CliOptions::readConfig)
			.orElseGet(Map::of)::get;
		return f.andThen(Optional::ofNullable);
	}

	private static Map<String, String> getDefaultConfig() {
		var config = new LinkedHashMap<String, String>();
		config.put("watermark.file", "zip:///site.zip!/path_inside_zip/watermark.png");
		config.put("watermark.text", "pawgen");
		config.put("staticDirs", "./sitedir/**,../somedir");
		//#commaseparated list of files dirs or globed patterns https://docs.oracle.com/javase/tutorial/essential/io/find.html
		config.put("copyDirs", "./sitedir/**,../somedir");
		config.put("contentDir", "/content");
		config.put("outputDir", "zip:/%USER_HOME%/Desktop/pawgen/site.zip?create,true&useTempFile,true&noCompression,false");
		config.put("templatesDir", "./templates");
		config.put("hosts", "pawgen.mydomain,test.pawgen.mydomain");
		config.put("deployer", "NETLIFY");
//#https://app.netlify.com/user/applications#personal-access-tokens
		config.put("netlify.accessToken", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
//#https://app.netlify.com/sites/pawet/settings/general#site-details
		config.put("netlify.siteAppId", "11111111-1111-1111-1111-111111111111");
		return config;
	}

	@SneakyThrows
	private static URI createUri(String uriStr) {
		return createUriOpt(uriStr).orElseThrow();
	}

	private static Optional<URI> createUriOpt(String uriStr) {
		return Optional.ofNullable(uriStr)
			.map(u -> u.replace("%USER_HOME%", USER_HOME))
			.map(URI::create)
			.map(CliOptions::createInternalUri);
	}

	@SneakyThrows
	private static URI createInternalUri(URI uri) {
		if (uri.getScheme() == null) {
			return new URI("file", uri.getSchemeSpecificPart(), uri.getFragment());
		} else if ("zip".equals(uri.getScheme())) {
			String spec = uri.getRawSchemeSpecificPart();
			if (spec.contains("!/")) {
				return new URI("jar:file", uri.getRawSchemeSpecificPart(), null);
			}
			return new URI("jar:file", uri.getPath() + "!/", uri.getRawQuery());
		}
		return uri;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	@SneakyThrows
	private static Map<String, String> readConfig(Path path) {
		try (var io = Files.newInputStream(path)) {
			Properties props = new Properties();
			props.load(io);
			return (Map) props;
		} catch (Exception e) {
			log.info("Can't read properties file " + path, e);
		}
		return null;
	}

	private static void parseDirOpts(Stream<String> args, CliOptionsBuilder optionsBuilder) {
		var dirs = args.limit(4).toArray(String[]::new);
		switch (dirs.length) {
			case 4:
				Stream.of(dirs[3])
					.flatMap(COMMA_SEPARATED::splitAsStream)
					.map(CliOptions::createUri)
					.forEach(optionsBuilder::staticUri);
			case 3:
				optionsBuilder.templatesUri(createUri(dirs[2]));
			case 2:
				optionsBuilder.outputUri(createUri(dirs[1]));
			case 1:
				optionsBuilder.contentUri(createUri(dirs[0]));
			default:
		}
	}

}
