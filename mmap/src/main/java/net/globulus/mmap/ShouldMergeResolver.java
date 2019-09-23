package net.globulus.mmap;

/**
 * A simple lambda that tells the merge manager if it should merge input from this module with
 * inputs from modules higher up. Generally, it's only used to tell if the current module is the
 * topmost one, i.e if it has previous merge inputs or not.
 */
@FunctionalInterface
public interface ShouldMergeResolver {
    /**
     * @return true if {@link MergeManager} should merge this module's processor input.
     */
    boolean shouldMerge();
}
