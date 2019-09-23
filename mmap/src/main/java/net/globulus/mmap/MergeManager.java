package net.globulus.mmap;

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

    final Filer filer;
    final long timestamp;
    final String packageName;
    final String processorName;
    final ShouldMergeResolver resolver;

    ProcessorLog processorLog = new ProcessorLog.Stub();
    int lookbackPeriod = DEFAULT_LOOKBACK_PERIOD;

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

    public MergeSession<T> newSession() {
        return new MergeSession<>(this);
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
        MergeSession<T> session = newSession();
        input = session.mergeInput(input);
        session.writeMergeFiles(input);
        return input;
    }
}
