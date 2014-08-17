package controllers

import com.decodified.scalassh.SSH
import com.typesafe.config.ConfigFactory
import io.rampant.minecraft.rcon.RCon
import play.api.Configuration
import play.api.Play.current
import play.api.mvc._

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object Application extends Controller {
	val rconLOG = play.api.Logger("rcon")
	val sshLOG = play.api.Logger("ssh")

	val pageTitle = current.configuration.getString("page.title").get
	val rconDefaults = current.configuration.getConfig("server.defaults").get.underlying
	val configuredServers = ConfigFactory.load().getConfigList("servers").asScala.map({ c => Configuration(c.withFallback(rconDefaults))})

	case class MCServer(id: String, name: String, running: Boolean, players: List[String])

	def index = Action {
		val servers = configuredServers.map({ s =>
			val users = Try(new RCon(s.getString("host").get, s.getInt("rcon.port").get, s.getString("rcon.password").get))
				.flatMap({ c => Try(c.list().asScala.toList)})
			users match {
				case Failure(e) => rconLOG.warn("RCon Error", e)
				case Success(v) => ;
			}
			val id = s.getString("id").get
			MCServer(id, s.getString("name").getOrElse(id), users.isSuccess, users.getOrElse(List()))
		})
		Ok(views.html.index(pageTitle, servers))
	}

	def start(id: String) = Action {
		configuredServers.find(_.getString("id").get.equalsIgnoreCase(id)) match {
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
