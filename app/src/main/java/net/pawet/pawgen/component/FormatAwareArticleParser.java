package net.pawet.pawgen.component;

import lombok.RequiredArgsConstructor;
import net.pawet.pawgen.component.markdown.MDArticleParser;
import net.pawet.pawgen.component.system.storage.ArticleResource;
import net.pawet.pawgen.component.xml.XmlArticleParser;

@RequiredArgsConstructor
public final class FormatAwareArticleParser {

	private final XmlArticleParser xmlParser;
	private final MDArticleParser mdParser;

	public Article parse(ArticleResource readable) {
		String format = readable.getFormat();
		return switch (format) {
			case "xml" -> xmlParser.parse(readable);
			case "md" -> mdParser.parse(readable);
			default -> throw new IllegalStateException("Unexpected article type: " + format);
		};
	}

}
