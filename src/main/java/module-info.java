module pawgen.main {
	exports net.pawet.pawgen;

	requires static lombok;
	requires java.xml;
	requires com.github.mustachejava;
	requires JVips;

	requires java.desktop;
	requires java.logging;
}
