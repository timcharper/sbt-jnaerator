package com.timcharper.sbt

import java.io.File
import sbt.{config => sbtConfig, _}
import sbt.Keys.{cleanFiles, libraryDependencies, managedSourceDirectories,
  sourceDirectories, sourceDirectory, sourceGenerators, sourceManaged, streams,
  version, watchSources}

object JnaeratorPlugin extends AutoPlugin {
  object autoImport {

    val jnaerator = sbtConfig("jnaerator")

    val jnaeratorTargets = TaskKey[Seq[Jnaerator.Target]]("jnaerator-targets",
      "List of header-files and corresponding configuration for java interface generation")
    val jnaeratorGenerate = TaskKey[Seq[File]]("jnaerator-generate",
      "Run jnaerate and generate interfaces")
    val jnaeratorRuntime = SettingKey[Jnaerator.Runtime]("which runtime to use")

    object Jnaerator {
      sealed trait Runtime
      object Runtime {
        case object JNA extends Runtime
        case object BridJ extends Runtime
      }
      case class Target(
        headerFile: File,
        packageName: String,
        libraryName: String,
        extraArgs: Seq[String] = Nil)

      lazy val settings = inConfig(jnaerator)(Seq[Setting[_]](
        sourceDirectory := ((sourceDirectory in Compile) { _ / "native" }).value,
        sourceDirectories := ((sourceDirectory in Compile) { _ :: Nil }).value,
        sourceManaged := ((sourceManaged in Compile) { _ / "jnaerator_interfaces" }).value,
        jnaeratorGenerate <<= runJnaerator
      )) ++ Seq[Setting[_]](
        jnaeratorTargets := Nil,
        jnaeratorRuntime := Runtime.BridJ,
        version := ((jnaeratorRuntime in jnaerator) {
          /* Latest versions against which the targetted version of JNAerator is
           * known to be compatible */
          case Runtime.JNA => "4.2.1"
          case Runtime.BridJ => "0.7.0"
        }).value,
        cleanFiles += (sourceManaged in jnaerator).value,

        // watchSources ++= (jnaeratorTargets in jnaerator).flatMap(_.join).map { _.map(_.headerFile) }.value,
        watchSources ++= (jnaeratorTargets in jnaerator).map { _.map(_.headerFile) }.value,
        watchSources += file("."),

        sourceGenerators in Compile += (jnaeratorGenerate in jnaerator).taskValue,
        managedSourceDirectories in Compile += (sourceManaged in jnaerator).value,
        libraryDependencies += (jnaeratorRuntime in jnaerator, version in jnaerator).apply {
          case (Jnaerator.Runtime.JNA, v) =>
            "net.java.dev.jna" % "jna" % v
          case (Jnaerator.Runtime.BridJ, v) =>
            "com.nativelibs4java" % "bridj" % v
        }.value
      )
    }

    private def runJnaerator: Def.Initialize[Task[Seq[File]]] = Def.task {

      val targets = (jnaeratorTargets in jnaerator).value
      val s = (streams.value)
      val runtime = (jnaeratorRuntime in jnaerator).value
      val outputPath = (sourceManaged in jnaerator).value

      targets.flatMap { target =>
        val targetId = s"${target.headerFile.getName}-${(target, runtime, outputPath).hashCode}"
        val cachedCompile = FileFunction.cached(s.cacheDirectory / "jnaerator" / targetId, inStyle = FilesInfo.lastModified, outStyle = FilesInfo.exists) { (_: Set[File]) =>
          IO.delete(outputPath)
          outputPath.mkdirs()

	        // java -jar bin/jnaerator.jar -package com.package.name -library libName lib/libName.h -o src/main/java -mode Directory -f -scalaStructSetters
          val args = List(
            "-package", target.packageName,
            "-library", target.libraryName,
            target.headerFile.getCanonicalPath,
            "-o", outputPath.getCanonicalPath,
            "-mode", "Directory",
            "-f", "-scalaStructSetters") ++ target.extraArgs

          s.log.info(s"(${target.headerFile.getName}) Running JNAerator with args ${args.mkString(" ")}")
          try {
            com.ochafik.lang.jnaerator.JNAerator.main(args.toArray)
          } catch { case e: Exception =>
              throw new RuntimeException(s"error occured while running jnaerator: ${e.getMessage}", e)
          }

          (outputPath ** "*.java").get.toSet
        }
        cachedCompile(Set(target.headerFile)).toSeq
      }
    }
  }


}
