package controllers

import java.util.concurrent.TimeUnit

import akka.actor.Props
import akka.pattern.ask
import akka.routing.FromConfig
import akka.util.Timeout
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
	implicit val actorTimeout = Timeout(current.configuration.getLong("server.defaults.actor.timeout").getOrElse(10), TimeUnit.SECONDS)

	val serverInfoRouter = Akka.system.actorOf(Props[StatusWorker].withRouter(FromConfig()), "server-info-router")

	val pageTitle = current.configuration.getString("page.title").get

	case class RemoteInfo(info: Option[ServerInfo] = None, running: Boolean = false)

	val serverInfoCacheTimeout = 1.days

	val servers = WS.url(ConfigFactory.load().getString("server.config.url")).get()
		.map({ r => (r.json \ "servers").as[JsArray].value})
		.map({ l => l.map({ s => s.as[Server]})})

	def findServer(id: String) = servers.map({ l => l.find(_.id == id)})

	def queryServer(server: Server) = {
		lazy val port = ConfigFactory.load().getInt("server.defaults.query.port")
		lazy val cacheKey = s"mcserver:${server.host}:$port"
		(serverInfoRouter ? QueryRequest(server.host, port))
			.map(_.asInstanceOf[QueryResponse]).map({
			case OfflineResponse =>
				RemoteInfo(info = Cache.getAs[ServerInfo](cacheKey))
			case info: InfoResponse =>
				Cache.set(cacheKey, info, serverInfoCacheTimeout)
				RemoteInfo(Some(info.info), running = true)
		})
			.recover({ case throwable =>
			play.api.Logger.warn("Exception querying server", throwable)
			RemoteInfo(info = Cache.getAs[ServerInfo](cacheKey))
		})
	}

	implicit val serverConfigReads: Reads[Server] = (
		(JsPath \ "id").read[String] and
			(JsPath \ "name").read[String] and
			(JsPath \ "host").read[String] and
			(JsPath \ "user").readNullable[String] and
			(JsPath \ "command").readNullable[String]
		)(Server.apply _)

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

	def start(id: String) = Action.async {
		findServer(id).map({
			case None => NotFound("Unknown server: " + id)
			case Some(s) =>
				s.canStart match {
					case false => Forbidden
					case true => s.startServer
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
