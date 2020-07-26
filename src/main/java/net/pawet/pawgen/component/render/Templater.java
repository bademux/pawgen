package net.pawet.pawgen.component.render;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.MustacheFactory;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import net.pawet.pawgen.component.ArticleHeader;

import java.io.Writer;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static lombok.AccessLevel.PRIVATE;

@RequiredArgsConstructor(access = PRIVATE)
@Log
public class Templater {

	private static final String DEFAULT_EXT = ".mustache";
	private static final String TEMPLATE_NAME = "index.html";

	private final MustacheFactory mf;

	public static Templater of(Path baseDir) {
		return new Templater(new DefaultMustacheFactory(new PathFileSystemResolver(baseDir, null)));
	}

	final BiConsumer<Writer, Map<String, ?>> create(@NonNull String templateName) {
		return mf.compile(templateName + DEFAULT_EXT)::execute;
	}

	public Stream<ProcessingItem> create(Collection<ArticleHeader> articleHeaders, Function<ArticleHeader, CharSequence> contentProvider) {
		return new RenderContext(create(TEMPLATE_NAME), articleHeaders, contentProvider).handle();
	}
}
