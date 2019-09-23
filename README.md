# Multi-module Annotation Processing

MMAP allows you to include multiple hierarchical module support in your JVM annotation processor! By default, JVM annotation processors [work on a single module](#why) and don't support hierarchical multi-module projects (i.e those that have several levels of libraries atop the release module). MMAP hacks that, allowing your annotation processor to [preserve its output between module runs](#how-does-it-work) and deliver its result based on data gathered from all the modules' processing.

Check out [the reasoning behind the lib](#why), [how does it work](#how-does-it-work) and [how to use it](#how-to-use). Additionally, all the Easy libs use it to allow for MM support, such as [EasyPrefs](https://github.com/globulus/EasyPrefs), and [EasyFlavor](https://github.com/globulus/easyflavor).

### Installation

MMAP is [hosted on JCenter](https://bintray.com/beta/#/gordan-glavas/mmap/net.globulus.mmap). To add it, simply include the dependency:

```gradle
 repositories {
    jcenter()
}
...
dependencies {
    compile 'net.globulus.mmap:mmap:1.0.1'
}
```

### Why

JVM annotation processors work in such a way that each module is processed in complete isolation - the processor doesn't know how many more modules are there out there, and the only output it can produce is a Java file.

This in itself isn't huge of a restriction and can be worked around, but other limitations exist: e.g, Android DEX requires all the generated file names to be unique, which becomes an issue since the AP can't know the names of previously generated files unless a convention is followed, which then makes it difficult to guarantee unique names for the next module. MMAP solves this issue using timestamps, allowing for unique names across all processed modules, with their inputs properly merged from the top down.

### How does it work

MMAP works by using **merge files**. A merge file is a simple Java class that contains a single byte array containing a serialized **merge input**.

A **merge input** is a class you define yourself, and should represent the input your annotation processor uses to generate code files. The interface itself is very simple, requiring a single method *mergeUp(T)* that merges input from a previous module with the current one - how does that work, what conflict resolution is used, it's all up to you.

As your annotation processor works, your **MergeManager** will decide if a merge is necessary, and then look for previous merge files, read them, and merge with current input. Then, the new input will be written to new merge files.

Merge files are **guaranteed to have unique names across all modules**, meaning that no name conflict will arise, and your processor's output will work well with Android DEX.

### How to use

The usage of the lib is extremely simple (code snippets taken from [EasyPrefs](https://github.com/globulus/EasyPrefs)):

1. Define your processor's generated file input as a class that implements **MergeInput**. Implement the *mergeUp* method to define how does the input merge with its top-level input.

```java
public static class Input implements MergeInput<Input> {
    
    final String masterMethod;
    final List<PrefType> classes;
    final List<ExposedMethod> methods;

...

    @Override
    public Input mergedUp(Input other) {
        String masterMethod = (other.masterMethod != null) ? other.masterMethod : this.masterMethod;
        List<PrefType> classes = new ArrayList<>(other.classes);
        classes.addAll(this.classes);
        List<ExposedMethod> methods = new ArrayList<>(other.methods);
        methods.addAll(this.methods);
        return new Input(masterMethod, classes, methods);
    }
}
```

2. Create a **MergeManager** instance in your Processor that has your MergeInput class as its type parameter, and supply it the following params:

    * *filer* from your annotation processor.
    * *timestamp* obtained at the begging of Processor run using *System.currentTimeMillis()*. **It is important that this value be obtained before outside of *Processor#process()* method, ideally in the processor's constructor.**
    * *packageName* that tells us where should the merge files live.
    * *processorName* that uniquely identifies your Processor.
    * *shoulMergeResolver* whose only method decides if your current module should be merged up or not.
    
```java
MergeManager mergeManager =  new MergeManager<Input>(mFiler, mTimestamp,
                FrameworkUtil.getEasyPrefsPackageName(), NAME,
                (ShouldMergeResolver) () -> shouldMergeResolution);
```
    
3. Use MergeManager's **manageMerging(T)** method to transform your input to a merged one, and write additional merge classes.

```java
input = mergeManager.managerMerging(input);
```

#### Config

If you wish to see MMPA's debug output, provide an implementation of a **ProcessorLog** using *MergeManager#setProcessorLog()*.

The default **lookback period** is 30 seconds - if your machine is slow and the build process for a module takes more than that, i.e subsequent calls to the annotation processor for the next module is more than 30 seconds after the previous one, use *MergeManager#setLookbackPeriod()* to increase this number.

#### Source and Sink

When designing your annotation processor, it might be necessary to know if a certain module is the topmost module (i.e, the one at the top of the module hierarchy), or if it is the bottom-most module (i.e, the one that is processed last).

To make this simpler, MMAP ships with two annotations, *@Source* and *@Sink*, which you may use to these ends. If you decide to employ these annotations, you'll most likely have these two lines in your Processor's *process* method:

 ```java
boolean shouldMerge = roundEnv.getElementsAnnotatedWith(Source.class).isEmpty();
boolean shouldWriteFinalFile = !roundEnv.getElementsAnnotatedWith(Sink.class).isEmpty();
```

Again, how you design your processor and if you use these annotations or not is entire up to the processor developer.

#### Advanced - MergeSession

