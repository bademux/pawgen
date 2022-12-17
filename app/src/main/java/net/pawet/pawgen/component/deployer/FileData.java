package net.pawet.pawgen.component.deployer;

import java.io.InputStream;

interface FileData {
	String getRootRelativePath();

	InputStream inputStream();
}
