//package util;
//
//import com.caucho.quercus.script.QuercusScriptEngineFactory;
//import lombok.SneakyThrows;
//import lombok.experimental.UtilityClass;
//import net.pawet.pawgen.component.ArticleHeader;
//
//import java.util.*;
//import java.util.stream.Stream;
//
//import static java.util.stream.Collectors.groupingBy;
//import static java.util.stream.Collectors.toList;
//
//@UtilityClass
//public class PhpAdapterUtil {
//
//	private final QuercusScriptEngineFactory FACTORY = new QuercusScriptEngineFactory();
//
//	public List<ArticleHeader> sort(Stream<ArticleHeader> input) {
//		return input.collect(groupingBy(ArticleHeader::getBaseCategory))
//			.values().stream()
//			.flatMap(PhpAdapterUtil::getSorted)
//			.collect(toList());
//	}
//
//	Stream<ArticleHeader> getSorted(List<ArticleHeader> articleHeaders) {
//		if (articleHeaders.size() < 2) {
//			return articleHeaders.stream();
//		}
//		ArticleHeader header = articleHeaders.get(0);
//		if (header.getCategory().isEmpty()) {
//			return articleHeaders.stream();
//		}
//		if (header.getLastCategory().isEmpty()) {
//			return articleHeaders.stream();
//		}
//		LinkedHashMap<String, List<ArticleHeader>> map = articleHeaders.stream()
//			.collect(groupingBy(ArticleHeader::getLastCategory, LinkedHashMap::new, toList()));
//		if(map.size() < 2){
//			return articleHeaders.stream();
//		}
//		var sorted = new ArrayList<>(map.keySet());
//		sortLikePhp(sorted);
//		return sorted.stream().map(map::get).flatMap(Collection::stream);
//	}
//
//	@SneakyThrows
//	void sortLikePhp(Collection<String> data) {
//		var engine = FACTORY.getScriptEngine();
//		engine.put("list", data);
//		engine.eval("<?php sort($list); ?>");
//	}
//
//
//	public int comparePhpLike(String categoryLeft, String categoryRight) {
//		int rightIndex = categoryRight.lastIndexOf('/');
//		if (categoryRight.regionMatches(true, 0, categoryLeft, 0, rightIndex)) {
//			int startIndexLeft = categoryLeft.lastIndexOf('/') + 1;
//			int startIndexRight = rightIndex + 1;
//			if (startIndexLeft != 0 && startIndexLeft != categoryLeft.length() &&
//				startIndexRight != 0 && startIndexRight != categoryRight.length()) {
//				try {
//					int left = Integer.parseInt(categoryLeft, startIndexLeft, categoryLeft.length(), 10);
//					int right = Integer.parseInt(categoryRight, startIndexRight, categoryRight.length(), 10);
//					return Integer.compare(left, right);
//				} catch (NumberFormatException ignored) {
//				}
//			}
//		}
//		return categoryLeft.compareTo(categoryRight);
//	}
//
//}
