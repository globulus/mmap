package net.globulus.mmap;

import net.globulus.mmap.util.MmapJavaWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Writer;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;

/**
 * Generates the merge files, which are classes that contain byte array of serialized
 * {@link MergeInput}s.
 */
final class MergeFileCodeGen {

	private static final String CLASS_NAME_FORMAT = "%sMerge_%d";
	private static final int BYTE_STEP = 8_000;
	private static final Set<Modifier> PSF_MODIFIERS = EnumSet.of(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);

	static final String MERGE_FIELD_NAME = "MERGE";
	static final String NEXT_FIELD_NAME = "NEXT";

	private final String packageName;
	private final String processorName;
	private final ProcessorLog processorLog;

	MergeFileCodeGen(String packageName,
					 String processorName,
					 ProcessorLog processorLog) {
		this.packageName = packageName;
		this.processorName = processorName;
		if (processorLog == null) {
			this.processorLog = new ProcessorLog.Stub();
		} else {
			this.processorLog = processorLog;
		}
	}

	static String getClassName(String processorName, long index) {
		return String.format(CLASS_NAME_FORMAT, processorName, index);
	}

	void generate(Filer filer, long timestamp, MergeInput input) {
		try {
			byte[] bytes = convertToBytes(input);
			for (int i = 0, count = 0; i < bytes.length; i += BYTE_STEP, count++) {
				String className = getClassName(processorName, timestamp + count);
				processorLog.warn(null, "Creating file " + className);
				JavaFileObject jfo = filer.createSourceFile(packageName + "." + className);
				Writer writer = jfo.openWriter();
				try (MmapJavaWriter jw = new MmapJavaWriter(writer)) {
					jw.emitPackage(packageName);
					jw.emitEmptyLine();

					jw.emitJavadoc("Generated class by @%s. Do not modify this code!", processorName);
					jw.beginType(className, "class", EnumSet.of(Modifier.PUBLIC), null);
					jw.emitEmptyLine();

					jw.emitField("byte[]", MERGE_FIELD_NAME, PSF_MODIFIERS,
							fromBytes(Arrays.copyOfRange(bytes, i, Math.min(bytes.length, i + BYTE_STEP))));

					jw.emitField("boolean", NEXT_FIELD_NAME, PSF_MODIFIERS,
							Boolean.toString(i < bytes.length - BYTE_STEP));

					jw.endType();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String fromBytes(byte[] bytes) {
		return Arrays.toString(bytes).replace('[', '{').replace(']', '}');
	}

	private byte[] convertToBytes(Object object) throws IOException {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
			 ObjectOutput out = new ObjectOutputStream(bos)) {
			out.writeObject(object);
			return bos.toByteArray();
		}
	}
}
