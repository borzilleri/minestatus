name := """minecraft-rcon"""

organization := "io.rampant.minecraft"

version := "1.1-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.1"

resolvers += "spray repo" at "http://repo.spray.io"

libraryDependencies ++= Seq(
	"org.webjars" % "bootstrap" % "3.2.0",
	"com.decodified" %% "scala-ssh" % "0.6.4",
	"org.bouncycastle" % "bcprov-jdk16" % "1.46",
	"com.jcraft" % "jzlib" % "1.1.3"
)

