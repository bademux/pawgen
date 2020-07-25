package net.pawet.pawgen.component.netlify;

import java.io.InputStream;

public interface FileData {
	String getRootRelativePath();

	InputStream inputStream();
}
