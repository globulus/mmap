package net.globulus.mmap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class MergeSession<T extends MergeInput> {

    private final MergeManager<T> manager;

    MergeSession(MergeManager<T> m) {
        manager = m;
    }

    @SuppressWarnings("unchecked")
    public T mergeInput(T input) {
        if (manager.resolver.shouldMerge()) {
            ByteBuffer buffer = ByteBuffer.allocate(50_000);
            // Find first merge file
            manager.processorLog.warn(null, "Finding first merge file");
            List<Class> mergeClasses = new ArrayList<>();
            for (int i = 0; i < manager.lookbackPeriod; i++) {
                long index = manager.timestamp - i;
                try {
                    Class lastMergeClass = Class.forName(getClassNameForIndex(index));
                    manager.processorLog.warn(null, "Found merge class at " + index);
                    mergeClasses.add(lastMergeClass);

                    // If exception wasn't thrown, we've found the last written merge file
                    for (long j = index - 1; j > manager.timestamp - manager.lookbackPeriod; j--) {
                        try {
                            Class mergeClass = Class.forName(getClassNameForIndex(j));
                            mergeClasses.add(mergeClass);
                        } catch (ClassNotFoundException e) {
                            break; // Break as we don't have classes beyond this point
                        }
                    }

                    manager.processorLog.warn(null, "Found a total of "
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
        return input;
    }

    public void writeMergeFiles(T input) {
        new MergeFileCodeGen(manager.packageName, manager.processorName, manager.processorLog)
                .generate(manager.filer, manager.timestamp, input);
    }

    private String getClassNameForIndex(long index) {
        return manager.packageName + "." + MergeFileCodeGen.getClassName(manager.processorName, index);
    }

    @SuppressWarnings("unchecked")
    private T fromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInput in = new ObjectInputStream(bis)) {
            return (T) in.readObject();
        }
    }
}
