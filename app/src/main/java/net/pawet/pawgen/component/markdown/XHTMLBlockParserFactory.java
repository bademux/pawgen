package net.pawet.pawgen.component.markdown;

import org.commonmark.parser.block.BlockParserFactory;
import org.commonmark.parser.block.BlockStart;
import org.commonmark.parser.block.MatchedBlockParser;
import org.commonmark.parser.block.ParserState;

public class XHTMLBlockParserFactory implements BlockParserFactory {
	@Override
	public BlockStart tryStart(ParserState state, MatchedBlockParser matchedBlockParser) {
		return null;
	}
}
