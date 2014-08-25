import play.PlayImport._

name := """minecraft-rcon"""

organization := "io.rampant.minecraft"

version := "1.1-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

S3Resolver.defaults

scalaVersion := "2.11.1"

resolvers += "spray repo" at "http://repo.spray.io"

resolvers ++= Seq[Resolver](
	s3resolver.value("Releases resolver", s3("rampant.io.releases")),
	s3resolver.value("Snapshots resolver", s3("rampant.io.snapshots"))
)

libraryDependencies ++= Seq(
	cache,
	"io.rampant.minecraft" %% "scala-rcon" % "0.1-SNAPSHOT" changing() withSources(),
	"org.scala-tools.sbinary" %% "sbinary" % "0.4.3-SNAPSHOT" withSources(),
	"org.webjars" % "bootstrap" % "3.2.0",
	"com.decodified" %% "scala-ssh" % "0.6.4",
	"org.bouncycastle" % "bcprov-jdk16" % "1.46",
	"com.jcraft" % "jzlib" % "1.1.3"
)

