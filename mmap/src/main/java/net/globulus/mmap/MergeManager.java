package net.globulus.mmap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;

import javax.annotation.processing.Filer;

public final class MergeManager<T extends MergeInput> {

    private static final int MAX_MILLIS_PASSED = 60_000;

    private final Filer filer;
    private final long timestamp;
    private final String packageName;
    private final String processorName;
    private final ShouldMergeResolver resolver;
    private ProcessorLog processorLog = new ProcessorLog.Stub();

    public MergeManager(Filer filer,
                        long timestamp,
                        String packageName,
                        String processorName,
                        ShouldMergeResolver resolver) {
        assert filer != null : "Filer must be set!";
        assert timestamp != 0 : "Please obtain a timestamp using System.currentTimeMillis()!";
        assert packageName != null : "Package name must be set!";
        assert processorName != null : "Processor name must be set!";
        assert resolver != null : "ShouldMergeResolver must be set!";
        this.filer = filer;
        this.timestamp = timestamp;
        this.packageName = packageName;
        this.processorName = processorName;
        this.resolver = resolver;
    }

    public MergeManager<T> setProcessorLog(ProcessorLog processorLog) {
        this.processorLog = (processorLog != null) ? processorLog : new ProcessorLog.Stub();
        return this;
    }

    @SuppressWarnings("unchecked")
    public T manageMerging(T input) {
        if (resolver.shouldMerge()) {
            ByteBuffer buffer = ByteBuffer.allocate(50_000);
            // Find first merge file
            processorLog.warn(null, "Finding first merge file");
            Long firstMergeClassIndex = null;
            for (int i = MAX_MILLIS_PASSED; i > 0; i--) {
                long index = timestamp - i;
                try {
                    Class.forName(packageName + "."
                            + MergeFileCodeGen.getClassName(processorName, index));
                    firstMergeClassIndex = index;
                    processorLog.warn(null, "Found " + firstMergeClassIndex);
                    break; // break if no exception was thrown
                } catch (ClassNotFoundException ignored) { }
            }
            if (firstMergeClassIndex != null) {
                try {
                    for (int i = 0; i < Integer.MAX_VALUE; i++) {
                        long index = firstMergeClassIndex + i;
                        Class mergeClass = Class.forName(packageName
                                + "." + MergeFileCodeGen.getClassName(processorName, index));
                        buffer.put((byte[]) mergeClass.getField(MergeFileCodeGen.MERGE_FIELD_NAME).get(null));
                        if (!mergeClass.getField(MergeFileCodeGen.NEXT_FIELD_NAME).getBoolean(null)) {
                            break;
                        }
                    }
                } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            try {
                T merge = fromBytes(buffer.array());
                input = (T) input.mergedUp(merge);
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        new MergeFileCodeGen(packageName, processorName, processorLog).generate(filer, timestamp, input);

        return input;
    }

    @SuppressWarnings("unchecked")
    private T fromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInput in = new ObjectInputStream(bis)) {
            return (T) in.readObject();
        }
    }
}
