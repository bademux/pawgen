module pawgen.main {
	exports net.pawet.pawgen;

	requires static lombok;
	requires java.xml;
	requires com.github.mustachejava;
	requires java.desktop;
	requires java.logging;
	requires java.net.http;
	requires jakarta.json;
	requires org.slf4j;
	requires org.slf4j.simple;
	requires jul.to.slf4j;
}
