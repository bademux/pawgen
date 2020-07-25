package net.pawet.pawgen.component.system;

import build.Build;
import lombok.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;
import static lombok.AccessLevel.PRIVATE;

@ToString
@Getter
@Builder(access = PRIVATE)
public final class CommandLineOptions {
    private static final String OUTPUT_DIR = "./public";
    private static final String TEMPLATES_DIR = "./templates";
    private static final String STATIC_DIR = "./static";

    @NonNull
    private final Path contentDir;
    @Builder.Default
    private final Path outputDir = Path.of(OUTPUT_DIR);
    @Builder.Default
    private final Path templatesDir = Path.of(TEMPLATES_DIR);
    @Builder.Default
    private final Path staticDir = Path.of(STATIC_DIR);
    @Builder.Default
    private final String watermarkText = "pawgen";
    @Builder.Default
    private final Instant dateFrom = Clock.systemUTC().instant();

    public static CommandLineOptions parse(String... args) {
        return parse(new LinkedHashSet<>(Arrays.asList(args)));
    }

    public static Stream<String> handleError(Throwable e) {
        return Stream.iterate(e, Objects::nonNull, Throwable::getCause)
                .map(Throwable::getMessage)
                .filter(Objects::nonNull);
    }

    @SneakyThrows
    private static CommandLineOptions parse(LinkedHashSet<String> args) {
        try {
            var optionsBuilder = CommandLineOptions.builder();
            parseHelp(args);
            parseVersion(args);
            parseConfigFile(args.stream(), optionsBuilder);
            parseDateFrom(args, optionsBuilder);
            parseDirOpts(args.stream(), optionsBuilder);
            return optionsBuilder.build();
        } catch (Throwable e) {
            String help = format(
                    "Usage: pawgen contentDir [outputDir:%s] [templatesDir:%s] [staticDir:./%s] [path_to/config.properties] [-f|--force] [-h|--help] [-v|--version]\n" +
                            "If path to config.properties is provided than config.properties in app dir is ignored\n" +
                            "Config example:\n%s\n", OUTPUT_DIR, TEMPLATES_DIR, STATIC_DIR, readConfig()
            );
            throw new Throwable(help, e);
        }
    }

    private static String readConfig() {
        try (var is = CommandLineOptions.class.getResourceAsStream("/config.properties")) {
            return new String(is.readAllBytes(), UTF_8);
        } catch (Exception ignore) {
            return "none";
        }
    }

    private static void parseConfigFile(Stream<String> args, CommandLineOptionsBuilder optionsBuilder) {
        Properties props = args.filter(s -> s.endsWith("config.properties"))
                .findAny()
                .or(() -> Optional.of("./config.properties"))
                .map(Path::of)
                .map(CommandLineOptions::readConfig)
                .orElseGet(Properties::new);
        if (props.isEmpty()) {
            return;
        }
        ofNullable(props.getProperty("site.watermarkText"))
                .filter(not(String::isBlank))
                .ifPresent(optionsBuilder::watermarkText);
        ofNullable(props.getProperty("site.contentDir"))
                .map(Path::of)
                .ifPresent(optionsBuilder::contentDir);
        ofNullable(props.getProperty("site.outputDir"))
                .map(Path::of)
                .ifPresent(optionsBuilder::outputDir);
        ofNullable(props.getProperty("site.templatesDir"))
                .map(Path::of)
                .ifPresent(optionsBuilder::templatesDir);
        ofNullable(props.getProperty("site.staticDir"))
                .map(Path::of)
                .ifPresent(optionsBuilder::staticDir);
    }

    @SneakyThrows
    private static Properties readConfig(Path path) {
        try (var io = Files.newInputStream(path)) {
            Properties props = new Properties();
            props.load(io);
            return props;
        } catch (Exception e) {
            throw new Throwable("Can't read properties file " + path, e);
        }
    }

    @SneakyThrows
    private static void parseVersion(Collection<String> args) {
        if (args.contains("-v") || args.contains("--version")) {
            throw new Throwable("Version: " + Build.VERSION);
        }
    }

    @SneakyThrows
    private static void parseHelp(Collection<String> args) {
        if (args.contains("-h") || args.contains("--help")) {
            throw new Throwable();
        }
    }

    private static void parseDateFrom(Collection<String> args, CommandLineOptionsBuilder optionsBuilder) {
        if (args.contains("-f") || args.contains("--force")) {
            optionsBuilder.dateFrom(Instant.MIN);
        }
    }

    private static void parseDirOpts(Stream<String> args, CommandLineOptionsBuilder optionsBuilder) {
        var dirs = args.map(Path::of)
                .map(CommandLineOptions::toRealPath)
                .filter(Objects::nonNull)
                .filter(Files::isDirectory)
                .toArray(Path[]::new);
        switch (dirs.length) {
            case 4:
                optionsBuilder.staticDir(dirs[3]);
            case 3:
                optionsBuilder.templatesDir(dirs[2]);
            case 2:
                optionsBuilder.outputDir(dirs[1]);
            case 1:
                optionsBuilder.contentDir(dirs[0]);
            default:
        }
    }

    private static Path toRealPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return null;
        }
    }
}
