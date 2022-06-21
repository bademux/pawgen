package net.pawet.pawgen.component.render;

import com.github.mustachejava.*;
import com.github.mustachejava.reflect.BaseObjectHandler;
import com.github.mustachejava.util.Wrapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.render.Renderer.ArticleContext;

import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.newBufferedReader;

@Slf4j
public class Templater {

	private static final String DEFAULT_EXT = ".mustache";
	private static final String TEMPLATE_NAME = "index.html";

	private final Mustache mustache;
	private final Function<String, InputStream> resourceReader;

	public Templater(Function<String, InputStream> resourceReader, Path templateDir) {
		this.resourceReader = resourceReader;
		MustacheResolver mustacheResolver = ((Function<String, Path>) templateDir::resolve).andThen(Templater::resolveTemplate)::apply;
		var mf = new DefaultMustacheFactory(mustacheResolver);
		mf.setObjectHandler(new PawgenObjectHandler());
		this.mustache = mf.compile(TEMPLATE_NAME + DEFAULT_EXT);
	}

	@SneakyThrows
	private static Reader resolveTemplate(Path template) {
		return newBufferedReader(template, UTF_8);
	}

	public void render(Writer writer, Object... context) {
		mustache.execute(writer, context);
	}

	final class PawgenObjectHandler extends BaseObjectHandler {

		@Override
		public Wrapper find(String name, List<Object> scopes) {
			return s -> findInternal(name, s);
		}

		private static final String parentPrefix = "parent.";
		private static final String funcPrefix = "func.";
		private static final String prefix = "../";

		private Object findInternal(String name, List<Object> scopes) {
			int level = calcParentLevel(name, prefix);
			if (level > 0) {
				name = name.substring(level * prefix.length());
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
			if (name.startsWith(funcPrefix)) {
				return getFuncByName(name.substring(funcPrefix.length()), context);
			}
			if (name.startsWith(parentPrefix)) {
				return context.getParent().map(ctx -> getByName(name.substring(parentPrefix.length()), ctx)).orElse(null);
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
				case "url" -> context.getUrl();
				case "children" -> context.getChildren();
				case "hasChildren" -> context.getChildren().hasNext();
				case "otherLangArticle" -> context.getOtherLangArticle();
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

}
