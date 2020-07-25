package net.pawet.pawgen.component.render;

import com.github.mustachejava.*;
import com.github.mustachejava.reflect.BaseObjectHandler;
import com.github.mustachejava.util.Wrapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import net.pawet.pawgen.component.render.Renderer.ArticleContext;
import net.pawet.pawgen.component.system.storage.Storage;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class Templater {

	private final static Base64.Encoder BASE64_ENCODER = Base64.getEncoder();
	private static final String DEFAULT_EXT = ".mustache";
	private static final String TEMPLATE_NAME = "index.html";

	private final Mustache mustache;
	private final Storage storage;

	public Templater(Storage storage) {
		this.storage = storage;
		var mf = new DefaultMustacheFactory(storage::resolveTemplate);
		mf.setObjectHandler(new PawgenObjectHandler());
		this.mustache = mf.compile(TEMPLATE_NAME + DEFAULT_EXT);
	}

	public Writer render(Writer writer, Object context, Callable<CharSequence> contentProvider) {
		return mustache.execute(writer, new Object[]{context, contentProvider});
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
			switch (name) {
				case "author":
					return context.getAuthor();
				case "category":
					return context.getCategory();
				case "date":
					return context.getDate();
				case "file":
					return context.getFile();
				case "fileExt":
					return context.getFileExt();
				case "lang":
					return context.getLang();
				case "source":
					return context.getSource();
				case "title":
					return context.getTitle();
				case "type":
					return context.getType();
				case "url":
					return context.getUrl();
				case "children":
					return context.getChildren();
				case "otherLangArticle":
					return context.getOtherLangArticle();
				default:
					return null;
			}
		}

		private Function<String, CharSequence> getFuncByName(String name, ArticleContext context) {
			switch (name) {
				case "relativize":
					return context::relativize;
				case "embed":
					return this::embed;
				case "embedBase64":
					return this::embedBase64;
				default:
					return null;
			}
		}

		@SneakyThrows
		private CharSequence embedBase64(String src) {
			var bos = new ByteArrayOutputStream(1024);
			embed(src, BASE64_ENCODER.wrap(bos));
			return bos.toString();
		}

		@SneakyThrows
		private CharSequence embed(String src) {
			var os = new ByteArrayOutputStream(1024);
			embed(src, os);
			return os.toString(UTF_8);
		}

		@SneakyThrows
		private void embed(String src, OutputStream os) {
			try (var is = storage.read(src)) {
				is.transferTo(os);
			}
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
