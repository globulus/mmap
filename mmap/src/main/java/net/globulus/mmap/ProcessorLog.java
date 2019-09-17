package net.globulus.mmap;

import javax.lang.model.element.Element;

/**
 * Created by gordanglavas on 30/09/16.
 */
public interface ProcessorLog {
	void note(Element element, String message, Object... args);
	void warn(Element element, String message, Object... args);
	void error(Element element, String message, Object... args);

	class Stub implements ProcessorLog {

		@Override
		public void note(Element element, String message, Object... args) { }

		@Override
		public void warn(Element element, String message, Object... args) { }

		@Override
		public void error(Element element, String message, Object... args) { }
	}
}
