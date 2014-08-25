package controllers

import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout
import com.decodified.scalassh.SSH
import com.typesafe.config.ConfigFactory
import io.rampant.minecraft.ServerInfo
import io.rampant.minestatus._
import play.api.Configuration
import play.api.Play.current
import play.api.cache.Cache
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws._
import play.api.mvc._

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration._

object Application extends Controller {
	implicit val actorTimeout = Timeout(5 seconds)
	val infoActor = Akka.system.actorOf(Props[StatusWorker], name = "infoActor")

	val sshLOG = play.api.Logger("ssh")

	val serverInfoCacheTimeout = 1.days

	val pageTitle = current.configuration.getString("page.title").get
	val serverDefaults = current.configuration.getConfig("server.defaults").get.underlying

	val configuredServers = ConfigFactory.load().getConfigList("servers").asScala.map({ c => Configuration(c.withFallback(serverDefaults))})

	val servers = current.configuration.getString("server.config.url").map({ url =>
		WS.url(url).get().map({ r => Configuration(ConfigFactory.parseString(r.body)).getConfigList("servers")
			.map(_.asScala.toList.collect({ case s => Configuration(s.underlying.withFallback(serverDefaults))}))
		})
	}).getOrElse(Future.successful(None))
	
	case class MCServer(id: String, name: String, info: Option[ServerInfo], running: Boolean, cachedInfo: Boolean = false)

	def serverFromConfig(s: Configuration) = {
		val host = s.getString("host").get
		val port = s.getInt("query.port").get
		val cacheKey = s"mcserver:$host:$port"
		val serverId = s.getString("id").get
		(infoActor ? QueryRequest(host, port)).map(_.asInstanceOf[QueryResponse]).map({
			case OfflineResponse =>
				val info = Cache.getAs[ServerInfo](cacheKey)
				MCServer(serverId, s.getString("name").getOrElse(serverId), info, running = false, cachedInfo = info.isDefined);
			case info: InfoResponse =>
				Cache.set(cacheKey, info, serverInfoCacheTimeout)
				MCServer(serverId, info.info.name, Some(info.info), running = true);
		})
	}

	def findServerConfig(id: String) = configuredServers.find(_.getString("id").get.equalsIgnoreCase(id))

	def index = Action.async {
		servers.map(_.getOrElse(List[Configuration]())).flatMap({ list =>
			Future.sequence(list.map(serverFromConfig)).map({ s => Ok(views.html.index(pageTitle, s))})
		})
	}

	/*
	def index = Action.async {
		Future.sequence(configuredServers.map(serverFromConfig))
			.map({ s => Ok(views.html.index(pageTitle, s))})
	}
	*/

	def start(id: String) = Action {
		findServerConfig(id) match {
			case None => NotFound("Unknown server: " + id)
			case Some(s) =>
				SSH(s.getString("host").get) { client =>
					client.exec(s.getString("command").get) match {
						case Right(v) => sshLOG.debug(v.stdOutAsString())
						case Left(v) => sshLOG.error("SSH Error: " + v)
					}
				}
				Redirect(routes.Application.index)
		}
	}
}
