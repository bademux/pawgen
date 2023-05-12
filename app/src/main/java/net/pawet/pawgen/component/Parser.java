package net.pawet.pawgen.component;

import lombok.RequiredArgsConstructor;
import net.pawet.pawgen.component.system.storage.ArticleResource;

@RequiredArgsConstructor
public final class Parser {

	private final net.pawet.pawgen.component.xml.ArticleParser xmlParser;
	private final net.pawet.pawgen.component.markdown.ArticleParser mdParser;

	public Article parse(ArticleResource readable) {
		String format = readable.getFormat();
		return switch (format){
			case "xml" -> xmlParser.parse(readable);
			case "md" -> mdParser.parse(readable);
			default -> throw new IllegalStateException("Unexpected article type: " + format);
		};
	}

}
