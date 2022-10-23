module pawgen.main {
	exports net.pawet.pawgen;
    exports net.pawet.pawgen.component;
	exports net.pawet.pawgen.utils;

	requires static lombok;
	requires java.xml;
	requires com.github.mustachejava;
	requires java.desktop;
	requires java.logging;
	requires java.net.http;
	requires jakarta.json;
	requires org.slf4j;
}
