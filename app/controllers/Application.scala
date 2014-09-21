package controllers

import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout
import com.decodified.scalassh.SSH
import com.typesafe.config.ConfigFactory
import io.rampant.minecraft.ServerInfo
import io.rampant.minestatus._
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.ws._
import play.api.mvc._

import scala.concurrent.Future
import scala.concurrent.duration._

object Application extends Controller {
	implicit val actorTimeout = Timeout(5 seconds)
	val infoActor = Akka.system.actorOf(Props[StatusWorker], name = "infoActor")

	val sshLOG = play.api.Logger("ssh")

	val pageTitle = current.configuration.getString("page.title").get

	case class ServerConfig(id: String, name: String, host: String, startCmd: Option[String])

	case class RemoteInfo(info: Option[ServerInfo] = None, running: Boolean = false)

	val serverInfoCacheTimeout = 1.days

	val servers = WS.url(ConfigFactory.load().getString("server.config.url")).get()
		.map({ r => (r.json \ "servers").as[JsArray].value})
		.map({ l => l.map({ s => s.as[ServerConfig]})})

	def findServer(id: String) = servers.map({ l => l.find(_.id == id)})

	def queryServer(server: ServerConfig) = {
		lazy val port = ConfigFactory.load().getInt("server.defaults.query.port")
		lazy val cacheKey = s"mcserver:${server.host}:$port"
		(infoActor ? QueryRequest(server.host, port)).map(_.asInstanceOf[QueryResponse]).map({
			case OfflineResponse =>
				RemoteInfo(info = Cache.getAs[ServerInfo](cacheKey))
			case info: InfoResponse =>
				Cache.set(cacheKey, info, serverInfoCacheTimeout)
				RemoteInfo(Some(info.info), running = true)
		})
	}

	implicit val serverConfigReads: Reads[ServerConfig] = (
		(JsPath \ "id").read[String] and
			(JsPath \ "name").read[String] and
			(JsPath \ "host").read[String] and
			(JsPath \ "command").readNullable[String]
		)(ServerConfig.apply _)

	implicit val serverInfoWrites: Writes[ServerInfo] = (
		(JsPath \ "name").write[String] and
			(JsPath \ "version").write[String] and
			(JsPath \ "gameId").write[String] and
			(JsPath \ "gameType").write[String] and
			(JsPath \ "map").write[String] and
			(JsPath \ "numPlayers").write[Int] and
			(JsPath \ "maxPlayers").write[Int] and
			(JsPath \ "players").write[List[String]] and
			(JsPath \ "plugins").write[String]
		)(i => (i.name, i.version, i.gameId, i.gameType, i.map, i.numPlayers, i.maxPlayers, i.players, i.plugins))

	implicit val remoteInfoWrite: Writes[RemoteInfo] = (
		(JsPath \ "info").writeNullable[ServerInfo] and
			(JsPath \ "running").write[Boolean]
		)(i => (i.info, i.running))

	def index = Action.async {
		servers.map({ l => Ok(views.html.index(pageTitle, l))})
	}

	/*
	def index = Action.async {
		Future.sequence(configuredServers.map(serverFromConfig))
			.map({ s => Ok(views.html.index(pageTitle, s))})
	}
	*/

	def start(id: String) = Action.async {
		findServer(id).map({
			case None => NotFound("Unknown server: " + id)
			case Some(s) =>
				s.startCmd match {
					case None => Forbidden
					case Some(cmd) =>
						SSH(s.host) { client =>
							client.exec(cmd) match {
								case Right(v) => sshLOG.debug(v.stdOutAsString())
								case Left(v) => sshLOG.error("SSH Error: " + v)
							}
						}
						Ok
				}
		})
	}

	def status(id: String) = Action.async {
		findServer(id).flatMap({
			case None => Future.successful(NotFound("Unknown server: " + id))
			case Some(server) =>
				queryServer(server).map({ info =>
					Ok(Json.toJson(info))
				})
		})
	}
}
