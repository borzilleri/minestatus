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

	case class MCServer(id: String, name: String, running: Boolean, players: List[String])

	val rconDefaults = current.configuration.getConfig("server.defaults").get.underlying
	val configuredServers = ConfigFactory.load().getConfigList("servers").asScala.map({ c => Configuration(c.withFallback(rconDefaults))})

	def index = Action {
		val servers = configuredServers.map({ s =>
			val users = Try(new RCon(s.getString("host").get, s.getInt("rcon.port").get, s.getString("rcon.password").get))
				.flatMap({ c => Try(c.list().asScala.toList)})
			users match {
				case Success(v) => ;
				case Failure(e) => play.api.Logger.error("rcon error", e)
			}
			MCServer(s.getString("id").get, s.getString("name").get, users.isSuccess, users.getOrElse(List()))
		})
		Ok(views.html.index(servers))
	}

	def start(id: String) = Action {
		configuredServers.find(_.getString("id").get.equalsIgnoreCase(id)) match {
			case None => NotFound("Unknown server: " + id)
			case Some(s) =>
				SSH(s.getString("host").get) { client =>
					client.exec(s.getString("command").get).right.map { result =>
						play.api.Logger.debug(result.stdOutAsString());
					}
				}
				Redirect(routes.Application.index)
		}
	}
}
