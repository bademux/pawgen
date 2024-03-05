package net.pawet.pawgen.component.render;

import com.github.mustachejava.*;
import com.github.mustachejava.reflect.BaseObjectHandler;
import com.github.mustachejava.util.Wrapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.render.Renderer.ArticleContext;

import java.io.Reader;
import java.io.Writer;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.newBufferedReader;
import static lombok.AccessLevel.PRIVATE;

@Slf4j
@RequiredArgsConstructor(access = PRIVATE)
public class Templater {

	private static final String DEFAULT_EXT = ".mustache";
	private static final String TEMPLATE_NAME = "index.html";

	private final Mustache mustache;

	public static Templater of(@NonNull Function<String, ReadableByteChannel> resourceReader, @NonNull Path templateDir, @NonNull Executor executor) {
		MustacheResolver mustacheResolver = ((Function<String, Path>) templateDir::resolve).andThen(Templater::resolveTemplate)::apply;
		var mf = new DefaultMustacheFactory(mustacheResolver);
		mf.setObjectHandler(new PawgenObjectHandler(resourceReader, executor));
		mf.setExecutorService(new ExecutorServiceAdapter(executor));
		return new Templater(mf.compile(TEMPLATE_NAME + DEFAULT_EXT));
	}

	@SneakyThrows
	private static Reader resolveTemplate(Path template) {
		return newBufferedReader(template, UTF_8);
	}

	public void render(Writer writer, Object... context) {
		mustache.execute(writer, context);
	}

}

@RequiredArgsConstructor
final class PawgenObjectHandler extends BaseObjectHandler {

	private final Function<String, ReadableByteChannel> resourceReader;
	private final Executor executor;

	@Override
	public Object coerce(Object object) {
		if (object instanceof ArticleContext c) {
			executor.execute(c::render);
			return c;
		}
		return super.coerce(object);
	}

	@Override
	public Wrapper find(String name, List<Object> scopes) {
		return s -> findInternal(name, s);
	}

	private static final String PARENT_PREFIX = "parent.";
	private static final String FUNC_PREFIX = "func.";
	private static final String PREFIX = "../";

	private Object findInternal(String name, List<Object> scopes) {
		int level = calcParentLevel(name, PREFIX);
		if (level > 0) {
			name = name.substring(level * PREFIX.length());
		}
		for (int i = scopes.size() - 1; i >= 0; i--) {
			Object scope = scopes.get(i);
			if (scope instanceof ArticleContext) {
				if (level == 0) { // skip parent stack entries
					return findInternal(name, (ArticleContext) scope);
				}
				level--;
			}
		}
		return null;
	}

	private int calcParentLevel(String name, String prefix) {
		int count = 0, index = 0;
		while ((index = name.indexOf(prefix, index)) != -1) {
			index += prefix.length() - 1;
			count++;
		}
		return count;
	}

	private Object findInternal(String name, ArticleContext context) {
		if (name.startsWith(FUNC_PREFIX)) {
			return getFuncByName(name.substring(FUNC_PREFIX.length()), context);
		}
		if (name.startsWith(PARENT_PREFIX)) {
			return context.getParent().map(ctx -> getByName(name.substring(PARENT_PREFIX.length()), ctx)).orElse(null);
		}
		return getByName(name, context);
	}

	private Object getByName(String name, ArticleContext context) {
		return switch (name) {
			case "author" -> context.getAuthor();
			case "category" -> context.getCategory();
			case "date" -> context.getDate();
			case "file" -> context.getFile();
			case "fileExt" -> context.getFileExt();
			case "lang" -> context.getLang();
			case "source" -> context.getSource();
			case "title" -> context.getTitle();
			case "type" -> context.getType();
			case "aliases" -> context.getAliases().iterator();
			case "url" -> context.getUrl();
			case "children" -> context.getChildren().iterator();
			case "hasChildren" -> context.getChildren().findAny().isPresent();
			case "otherLangArticle" -> context.getOtherLangArticle().iterator();
			case "latest" -> context.getLatest().iterator();
			default -> null;
		};
	}

	private Function<String, CharSequence> getFuncByName(String name, ArticleContext context) {
		return switch (name) {
			case "relativize" -> context::relativize;
			case "embed" -> s -> TemplateFunctions.embed(resourceReader, s);
			case "format" -> TemplateFunctions::format;
			default -> throw new UnsupportedOperationException("unknown function: " + name + " in " + context);
		};
	}

	@Override
	public Binding createBinding(final String name, TemplateContext tc, Code code) {
		return new Binding() {
			// We find the wrapper just once since only the name is needed
			private final Wrapper wrapper = find(name, null);

			@Override
			public Object get(List<Object> scopes) {
				return wrapper.call(scopes);
			}
		};
	}
}

@RequiredArgsConstructor
final class ExecutorServiceAdapter implements ExecutorService {

	@Delegate(types = Executor.class)
	private final Executor executor;

	@Override
	public void shutdown() {
		throw new UnsupportedOperationException("unrelated");
	}

	@Override
	public List<Runnable> shutdownNow() {
		throw new UnsupportedOperationException("unrelated");
	}

	@Override
	public boolean isShutdown() {
		throw new UnsupportedOperationException("unrelated");
	}

	@Override
	public boolean isTerminated() {
		throw new UnsupportedOperationException("unrelated");
	}

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) {
		throw new UnsupportedOperationException("unrelated");
	}

	@Override
	public <T> Future<T> submit(Callable<T> task) {
		throw new UnsupportedOperationException("unrelated");
	}

	@Override
	public <T> Future<T> submit(Runnable task, T result) {
		throw new UnsupportedOperationException("unrelated");
	}

	@Override
	public Future<?> submit(Runnable task) {
		throw new UnsupportedOperationException("unrelated");
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
		throw new UnsupportedOperationException("unrelated");
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
		throw new UnsupportedOperationException("unrelated");
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks) {
		throw new UnsupportedOperationException("unrelated");
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
		throw new UnsupportedOperationException("unrelated");
	}

}
