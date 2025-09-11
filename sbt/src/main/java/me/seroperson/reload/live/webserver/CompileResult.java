package me.seroperson.reload.live.webserver;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface CompileResult {

	class CompileSuccess implements CompileResult {
		private final Map<String, Source> sources;
		private final List<File> classpath;

		public CompileSuccess(Map<String, Source> sources, List<File> classpath) {
			this.sources = requireNonNullElse(sources, Map.of());
			this.classpath = requireNonNullElse(classpath, List.of());
		}

		public Map<String, Source> getSources() {
			return sources;
		}

		public List<File> getClasspath() {
			return classpath;
		}
	}

	class CompileFailure implements CompileResult {
		private final Throwable exception;

		public CompileFailure(Throwable exception) {
			this.exception = requireNonNull(exception);
		}

		public Throwable getException() {
			return exception;
		}
	}
}
