package me.seroperson.reload.live.webserver;

import java.io.File;

public class Source {
	private final File file;

	private final File original;

	public Source(File file, File original) {
		this.file = file;
		this.original = original;
	}

	public File getFile() {
		return file;
	}

	public File getOriginal() {
		return original;
	}
}
