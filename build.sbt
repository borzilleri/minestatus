import play.PlayImport._

name := """minecraft-rcon"""

organization := "io.rampant.minecraft"

version := "1.1-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

S3Resolver.defaults

scalaVersion := "2.11.1"

resolvers += "spray repo" at "http://repo.spray.io"

resolvers ++= Seq[Resolver](
	"Rampant I/O Releases" at s3("rampant.io.releases").toHttp,
	"Rampant I/O Snapshots" at s3("rampant.io.snapshots").toHttp
)

libraryDependencies ++= Seq(
	cache, ws,
	"io.rampant.minecraft" %% "scala-rcon" % "0.1-SNAPSHOT" changing() withSources(),
	"org.webjars" % "bootstrap" % "3.2.0",
	"com.decodified" %% "scala-ssh" % "0.6.4",
	"org.bouncycastle" % "bcprov-jdk16" % "1.46",
	"com.jcraft" % "jzlib" % "1.1.3"
)

