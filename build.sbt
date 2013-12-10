import com.typesafe.sbt.SbtStartScript

seq(SbtStartScript.startScriptForClassesSettings: _*)

name := "shorty"

version := "0.1"

scalaVersion := "2.10.3"

resolvers += "spray repo" at "http://repo.spray.io"

libraryDependencies := Seq(
  "ch.qos.logback"         % "logback-classic"       % "1.0.13",
  "com.typesafe.akka"     %% "akka-actor"            % "2.2.3",
  "com.typesafe.akka"     %% "akka-slf4j"            % "2.2.3",
  "com.github.mauricio"   %% "postgresql-async"      % "0.2.9",
  "io.spray"               % "spray-can"             % "1.2.0",
  "io.spray"               % "spray-routing"         % "1.2.0",
  "io.spray"              %% "spray-json"            % "1.2.5",
  "org.scala-lang"         % "scala-reflect"         % "2.10.3",
  "com.datastax.cassandra" % "cassandra-driver-core" % "2.0.0-rc1",
  "org.scalatest"         %% "scalatest"             % "2.0"     % "test",
  "io.spray"               % "spray-testkit"         % "1.2.0"   % "test",
  "com.typesafe.akka"     %% "akka-testkit"          % "2.2.3"   % "test"
)

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-Xlint",
  "-Ywarn-dead-code",
  "-target:jvm-1.7",
  "-encoding", "UTF-8"
)
