package net.globulus.mmap;

import java.io.Serializable;

public interface MergeInput<T extends MergeInput<T>> extends Serializable {
    T mergedUp(T other);
}
