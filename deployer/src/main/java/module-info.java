module deployer {
	requires static lombok;

	exports net.pawet.pawgen.deployer;
    exports net.pawet.pawgen.deployer.digest;
	exports net.pawet.pawgen.deployer.deployitem;

    requires java.net.http;
	requires transitive org.slf4j;
	requires transitive jakarta.json;
    requires org.bouncycastle.provider;

}
