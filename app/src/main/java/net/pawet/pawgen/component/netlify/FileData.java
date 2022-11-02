package net.pawet.pawgen.component.netlify;

import java.io.InputStream;

interface FileData {
	String getRootRelativePath();

	InputStream inputStream();
}
