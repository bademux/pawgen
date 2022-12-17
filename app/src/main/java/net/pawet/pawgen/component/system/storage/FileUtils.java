package net.pawet.pawgen.component.system.storage;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
class FileUtils {

	public static String parseFileExt(@NonNull String fileName) {
		int dotIndex = fileName.lastIndexOf('.');
		if (dotIndex == -1 || dotIndex == fileName.length() - 1) {
			return "";
		}
		return fileName.substring(dotIndex + 1);
	}

}
