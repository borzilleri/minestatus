import play.PlayImport._

name := """minestatus"""

organization := "io.rampant.minecraft"

version := "2.2-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala, SbtWeb)

scalaVersion := "2.11.1"

includeFilter in(Assets, LessKeys.less) := "main.less" | "test.less"

S3Resolver.defaults

resolvers ++= Seq[Resolver](
	"Spray Repo" at "http://repo.spray.io",
	"Rampant I/O Releases" at s3("rampant.io.releases").toHttp,
	"Rampant I/O Snapshots" at s3("rampant.io.snapshots").toHttp
)

libraryDependencies ++= Seq(
	cache, ws,
	"io.rampant.minecraft" %% "scala-rcon" % "0.1-SNAPSHOT" changing() withSources(),
	"com.jcraft" % "jsch" % "0.1.51",
	"org.webjars" % "jquery" % "2.1.1",
	"org.webjars" % "bootstrap" % "3.2.0",
	"org.webjars" % "underscorejs" % "1.6.0-3"
)
