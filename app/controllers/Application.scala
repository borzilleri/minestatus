package controllers

import com.decodified.scalassh.SSH
import io.rampant.minecraft.rcon.RCon
import play.api.Play.current
import play.api.mvc._

import scala.collection.JavaConverters._
import scala.util.Try

object Application extends Controller {
	val host = current.configuration.getString("minecraft.host").get
	val rconPort = current.configuration.getInt("minecraft.rcon.port").get
	val rconPass = current.configuration.getString("minecraft.rcon.password").get
	val startCmd = current.configuration.getString("minecraft.command").get

	def index = Action {
		val users = Try(new RCon(host, rconPort, rconPass))
			.flatMap({ c => Try(c.list().asScala.toList)})
		Ok(views.html.index(users.isSuccess, users.getOrElse(List())))
	}

	def start = Action {
		SSH(host) { client =>
			client.exec(startCmd).right.map { result =>
				play.api.Logger.debug(result.stdOutAsString());
			}
		}
		Redirect(routes.Application.index)
	}
}
