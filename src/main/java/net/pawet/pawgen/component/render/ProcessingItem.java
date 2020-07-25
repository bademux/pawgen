package net.pawet.pawgen.component.render;

import net.pawet.pawgen.component.ArticleHeader;

import java.io.Writer;
import java.util.function.Function;

public interface ProcessingItem {

	String getPrintableName();

	void writeWith(Function<ArticleHeader, Writer> writerFactory);

}
