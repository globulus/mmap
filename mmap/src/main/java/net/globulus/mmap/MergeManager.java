package net.globulus.mmap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;

/**
 * Manages all aspects of multi-module hierarchical merging of processor inputs. Put an instance of
 * this class in your {@link javax.annotation.processing.AbstractProcessor#process(Set, RoundEnvironment)}
 * method, and invoke {@link #manageMerging(MergeInput)}.
 * @param <T> The {@link MergeInput} class for your processor.
 */
public final class MergeManager<T extends MergeInput> {

    private static final int DEFAULT_LOOKBACK_PERIOD = 30_000;

    private final Filer filer;
    private final long timestamp;
    private final String packageName;
    private final String processorName;
    private final ShouldMergeResolver resolver;

    private ProcessorLog processorLog = new ProcessorLog.Stub();
    private int lookbackPeriod = DEFAULT_LOOKBACK_PERIOD;

    /**
     * @param filer The {@link Filer} of your processor.
     * @param timestamp <b>Obtain this in your processor's constructor by calling {@link System#currentTimeMillis()}.</b>
     * @param packageName Name of the package where your processor is outputting files. Merge files will be written there.
     * @param processorName Unique name for your annotation processor.
     * @param resolver {@link ShouldMergeResolver}
     */
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

    /**
     * Sets the {@link ProcessorLog}
     * @return this for fluent syntax
     */
    public MergeManager<T> setProcessorLog(ProcessorLog processorLog) {
        this.processorLog = (processorLog != null) ? processorLog : new ProcessorLog.Stub();
        return this;
    }

    /**
     * Lookback period defines how many names does the merge manager scan before to find its previous
     * merge file. Increase this value (in ms) if your project is very large or your build machine slow.
     * @return this for fluent syntax
     */
    public MergeManager<T> setLookbackPeriod(int lookbackPeriod) {
        this.lookbackPeriod = (lookbackPeriod != 0) ? lookbackPeriod : DEFAULT_LOOKBACK_PERIOD;
        return this;
    }

    /**
     * If your {@link #resolver} returns true, look back to find the latest merge files. If such
     * files exist, {@link MergeInput#mergedUp(MergeInput) mergeUp} the provided input with the
     * decoded one, and return the new input.
     * @param input Current processor's input.
     * @return Merge of current input with the previous one.
     */
    @SuppressWarnings("unchecked")
    public T manageMerging(T input) {
        if (resolver.shouldMerge()) {
            ByteBuffer buffer = ByteBuffer.allocate(50_000);
            // Find first merge file
            processorLog.warn(null, "Finding first merge file");
            List<Class> mergeClasses = new ArrayList<>();
            for (int i = 0; i < lookbackPeriod; i++) {
                long index = timestamp - i;
                try {
                    Class lastMergeClass = Class.forName(getClassNameForIndex(index));
                    processorLog.warn(null, "Found merge class at " + index);
                    mergeClasses.add(lastMergeClass);

                    // If exception wasn't thrown, we've found the last written merge file
                    for (long j = index - 1; j > timestamp - lookbackPeriod; j--) {
                        try {
                            Class mergeClass = Class.forName(getClassNameForIndex(j));
                            mergeClasses.add(mergeClass);
                        } catch (ClassNotFoundException e) {
                            break; // Break as we don't have classes beyond this point
                        }
                    }

                    processorLog.warn(null, "Found a total of "
                            + mergeClasses.size() + " merge classes in this run.");
                    break; // break if something was found
                } catch (ClassNotFoundException ignored) { }
            }
            for (int i = mergeClasses.size() - 1; i >= 0; i--) {
                Class mergeClass = mergeClasses.get(i);
                try {
                    buffer.put((byte[]) mergeClass.getField(MergeFileCodeGen.MERGE_FIELD_NAME).get(null));
                    if (!mergeClass.getField(MergeFileCodeGen.NEXT_FIELD_NAME).getBoolean(null)) {
                        break;
                    }
                } catch (IllegalAccessException | NoSuchFieldException e) {
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

        new MergeFileCodeGen(packageName, processorName, processorLog)
                .generate(filer, timestamp, input);

        return input;
    }

    private String getClassNameForIndex(long index) {
        return packageName + "." + MergeFileCodeGen.getClassName(processorName, index);
    }

    @SuppressWarnings("unchecked")
    private T fromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInput in = new ObjectInputStream(bis)) {
            return (T) in.readObject();
        }
    }
}
