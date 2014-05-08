import sbt._
import Keys._

name := "bitcoin-akka"

organization := "com.markgoldenstein"

version := "0.1-SNAPSHOT"

scalaVersion := "2.10.4"

scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8")

resolvers ++= Seq(
  "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
  "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository"
)

libraryDependencies ++= {
  val akkaV = "2.3.1"
  Seq(
    "com.typesafe.akka" %% "akka-remote" % akkaV,
    "com.typesafe.akka" %% "akka-testkit" % akkaV % "test",
    "com.typesafe.play" %% "play-json" % "2.2.2",
    "com.github.nikita-volkov" % "sext" % "0.2.3",
    "net.debasishg" % "redisclient_2.10" % "2.12",
    "org.java-websocket" % "Java-WebSocket" % "1.3.1-SNAPSHOT"
  )
}
