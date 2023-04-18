ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

lazy val root = (project in file("."))
  .settings(
    name := "Anymind-CQRS-assignment"
  )
lazy val akkaHttpVersion = "10.5.0"
lazy val akkaVersion     = "2.8.0"
lazy val slickVersion = "3.4.1"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http"                  % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-actor-typed"           % akkaVersion,
  "com.typesafe.akka" %% "akka-stream"                % akkaVersion,
  "com.typesafe.akka" %% "akka-persistence-typed"     % akkaVersion,
  "com.lightbend.akka" %% "akka-persistence-jdbc" % "5.2.1",
  "com.typesafe.akka" %% "akka-persistence-query" % akkaVersion,
  "com.typesafe.slick" %% "slick" % slickVersion,
  "com.typesafe.slick" %% "slick-hikaricp" % slickVersion,
  "ch.qos.logback"    % "logback-classic"             % "1.4.6",
  "mysql" % "mysql-connector-java" % "8.0.32",
  "com.typesafe.akka" %% "akka-stream-kafka" % "4.0.0"
)
