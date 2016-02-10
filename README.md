# sbt-jnaerator

Downloads JNAerator from Maven and generates interfaces as part of compilation.

## Usage:

Add to `project/plugins.sbt`:

    addSbtPlugin("com.timcharper" % "sbt-jnaerator" % "0.3.1")

Configure:

    Jnaerator.settings

    jnaeratorTargets += Jnaerator.Target(
      headerFile = baseDirectory.value / "lib" / "libnative.h",
      packageName = "com.native",
      libraryName = "VeryNative")

## Settings

- `jnaeratorRuntime` - Which runtime to use. Automatically adds native interface library version known to work with JNAerator.
  - When `Jnaerator.Runtime.JNA`, adds dependency `"net.java.dev.jna" % "jna" % "4.2.1"`
  - When `Jnaerator.Runtime.BridJ`, adds dependency `"com.nativelibs4java" % "bridj" % "0.7.0"`
  - Override the version by `(version in jnaerator) := "my version"`
- jnaeratorTargets - A list of `Jnaerator.Target`, specifying one or more header files for which you'd like to generate interfaces.
