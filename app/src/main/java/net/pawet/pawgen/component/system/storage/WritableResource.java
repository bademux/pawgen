package net.pawet.pawgen.component.system.storage;

import java.nio.channels.WritableByteChannel;

public interface WritableResource {

	WritableByteChannel writable();

}
