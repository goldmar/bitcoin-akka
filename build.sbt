import sbt._
import Keys._

name := "bitcoin-akka"

organization := "com.markgoldenstein"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.2"

scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8")

resolvers ++= Seq(
  "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
  "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository"
)

libraryDependencies ++= {
  Seq(
    "com.typesafe.akka" %% "akka-remote" % "2.3.5",
    "com.typesafe.play" %% "play-json" % "2.3.4",
    "com.github.nikita-volkov" % "sext" % "0.2.3",
    "org.java-websocket" % "Java-WebSocket" % "1.3.1-SNAPSHOT"
  )
}
