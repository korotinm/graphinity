import sbt._
import sbtcrossproject.CrossPlugin.autoImport.CrossType
import sbtcrossproject.CrossPlugin.autoImport.crossProject
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

val devZioV: String = "1.0.0-RC18-2"

lazy val buildSettings = Seq(
  organization := "org.me",
  scalaVersion := "2.13.2",
  crossScalaVersions := Seq("2.11.12", "2.12.10", scalaVersion.value)
)

lazy val commonJvmSettings = Seq(
  parallelExecution in Test := false
)

lazy val crossVersionSharedSources: Seq[Setting[_]] =
  Seq(Compile, Test).map { sc =>
    (unmanagedSourceDirectories in sc) ++= {
      (unmanagedSourceDirectories in sc).value.map { dir: File =>
        new File(dir.getPath + "_" + scalaBinaryVersion.value)
      }
    }
  }

lazy val publishSettings = Seq(
  homepage := None,
  licenses := Seq(),
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ =>
    false
  },
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshotVersion(version.value))
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  pomExtra :=
    <developers>
        <developer>
          <id>korotinm</id>
          <name>Mikhail Korotin</name>
          <url></url>
        </developer>
      </developers>
)

lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

def compilerOptions(scalaVersion: String, optimize: Boolean) = {
  val optimizeOptions =
    if (optimize)
      Seq(
        "-opt:l:inline",
        "-opt:l:method",
        "-opt-warnings",
        "-opt-inline-from:graphinity.**"
      )
    else Seq.empty

  val commonOptions =
    Seq(
      "-encoding",
      "utf8",
      "-Xfatal-warnings",
      "-feature",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-language:postfixOps",
      "-unchecked",
      "-deprecation"
    )

  val scalaVersionOptions =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, v)) if v <= 11 =>
        Seq("-Ypartial-unification")
      case Some((2, v)) if v == 12 =>
        Seq(
          "-Ypartial-unification",
          "-opt-warnings",
          "-Ywarn-extra-implicit",
          //"-Ywarn-unused",
          "-Yno-adapted-args",
          "-Ywarn-inaccessible",
          "-Ywarn-infer-any",
          "-Ywarn-nullary-override",
          "-Ywarn-nullary-unit"
        ) ++ optimizeOptions
      case Some((2, v)) if v == 13 =>
        Seq(
          "-opt-warnings",
          "-Ywarn-extra-implicit",
          //"-Ywarn-unused",
          "-Ymacro-annotations"
        ) ++ optimizeOptions
      case _ => Seq.empty
    }

  scalaVersionOptions ++ commonOptions
}

lazy val commonSettings = Seq(
  scalacOptions := compilerOptions(scalaVersion.value, !isSnapshotVersion(version.value)),
  resolvers ++= Seq(
    Resolver.sonatypeRepo("releases"),
    Resolver.sonatypeRepo("snapshots")
  ),
  scmInfo := None
) ++ crossVersionSharedSources

lazy val coreSettings = buildSettings ++ commonSettings ++ publishSettings

lazy val root = project
  .in(file("."))
  .aggregate(coreJVM, exampleJVM)
  .dependsOn(coreJVM)
  .dependsOn(exampleJVM)
  .settings(coreSettings: _*)
  .settings(noPublishSettings)

lazy val core =
  crossProject(JVMPlatform)
    .crossType(CrossType.Pure)
    .settings(moduleName := "graphinity")
    .settings(coreSettings: _*)
    .settings(
      libraryDependencies ++= Seq(
        "dev.zio" %% "zio" % devZioV
      )
    )
    .jvmSettings(commonJvmSettings: _*)

lazy val coreJVM = core.jvm

lazy val example =
  crossProject(JVMPlatform)
    .crossType(CrossType.Pure)
    .dependsOn(core)
    .settings(moduleName := "example")
    .settings(coreSettings: _*)
    .jvmSettings(commonJvmSettings: _*)

lazy val exampleJVM = example.jvm

/*[helpers */
def isSnapshotVersion(versionValue: String): Boolean = versionValue.trim.toUpperCase.endsWith("SNAPSHOT")
/*helpers]*/
