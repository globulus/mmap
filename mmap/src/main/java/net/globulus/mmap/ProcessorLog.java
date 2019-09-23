package net.globulus.mmap;

import javax.lang.model.element.Element;

/**
 * Defines an interface for outputting logs from the MMAP lib.
 */
public interface ProcessorLog {
	void note(Element element, String message, Object... args);
	void warn(Element element, String message, Object... args);
	void error(Element element, String message, Object... args);

	/**
	 * Empty ProcessorLog that implements all methods as empty.
	 */
	class Stub implements ProcessorLog {

		@Override
		public void note(Element element, String message, Object... args) { }

		@Override
		public void warn(Element element, String message, Object... args) { }

		@Override
		public void error(Element element, String message, Object... args) { }
	}
}
