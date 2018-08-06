import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import sbt.Keys.name
import sbt._
import sbtrelease.Version.Bump.Minor
import scalariform.formatter.preferences._

com.typesafe.sbt.SbtScalariform.scalariformSettings

version in ThisBuild := "0.1.0-SNAPSHOT"

organization := "io.niglo"

scalaVersion := "2.10.6"

scalacOptions ++= Seq("-unchecked", "-deprecation")

publishMavenStyle := true

releaseVersionBump := Minor

releaseTagComment := s"Releasing ${(version in ThisBuild).value}"

releaseCommitMessage := s"Setting version to ${(version in ThisBuild).value}"


ScalariformKeys.preferences := FormattingPreferences().setPreference(RewriteArrowSymbols, true)
    .setPreference(AlignParameters, true) //XXX gives horrible method declarations
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(DoubleIndentClassDeclaration, true)


credentials += Credentials(Path.userHome / ".sbt" / ".credentials")

publishTo := {
    val DevelopementFactoryUrl = sys.props.get("publish.repository").getOrElse("https://niglo-nexus:8080/")
    if (isSnapshot.value)
      Some("snapshots" at DevelopementFactoryUrl + "/content/repositories/snapshots")
    else
      Some("stables" at DevelopementFactoryUrl + "/content/repositories/stables")
  }




val CirceVersion = "0.9.3"

name := "avro-swagger-convertor"

parallelExecution in IntegrationTest := false
testForkedParallel in IntegrationTest := false
fork in IntegrationTest := true
fork in Test := true
concurrentRestrictions := Seq(Tags.limitAll(1))
testForkedParallel in Test := true
Defaults.itSettings

configs(IntegrationTest)


libraryDependencies ++= Seq(
      "io.swagger"           % "swagger-codegen" % "2.2.1",
      "org.scalatest"        %% "scalatest"    % "3.0.1" % "it,test",
      "org.scalatra.scalate" %% "scalate-core" % "1.8.0" exclude("org.slf4j","slf4j-api"),
      "commons-io"           %  "commons-io"                 %  "2.5",
      "io.circe"             %% "circe-yaml"                 % "0.8.0")
libraryDependencies ++= Seq(
      "io.circe"             %% "circe-parser",
      "io.circe"             %% "circe-generic",
      "io.circe"             %% "circe-core").map(_ % CirceVersion)

libraryDependencies ++=  Seq(
      "com.chuusai"          %% "shapeless"                  % "2.3.3",
      compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full))

dependencyOverrides ++= Set("org.spire-math" % "jawn-parser_2.10" % "0.11.1")




