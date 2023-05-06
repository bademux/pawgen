package net.pawet.pawgen.component.render;

import com.vladsch.flexmark.ext.attributes.AttributesExtension;
import com.vladsch.flexmark.ext.yaml.front.matter.YamlFrontMatterExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.misc.Extension;
import lombok.SneakyThrows;

import java.io.Reader;
import java.util.List;

public class Processor {
	private static final List<? extends Extension> EXTENSIONS = List.of(
		YamlFrontMatterExtension.create(),
		AttributesExtension.create()
	);
	private final Parser parser = Parser.builder().extensions(EXTENSIONS).build();
	private final HtmlRenderer renderer = HtmlRenderer.builder().extensions(EXTENSIONS).build();

	@SneakyThrows
	public final void render(Reader input, Appendable output) {
		renderer.render(parser.parseReader(input), output);
	}

}
