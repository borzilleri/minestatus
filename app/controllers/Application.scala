package controllers

import io.rampant.minecraft.rcon.RCon
import play.api.Play.current
import play.api.mvc._

import scala.collection.JavaConverters._
import scala.util.Try

object Application extends Controller {
	val rconHost = current.configuration.getString("minecraft.rcon.host").get
	val rconPort = current.configuration.getInt("minecraft.rcon.port").get
	val rconPass = current.configuration.getString("minecraft.rcon.password").get

	def index = Action {
		val users = Try(new RCon(rconHost, rconPort, rconPass))
			.flatMap({ c => Try(c.list().asScala.toList)})
		Ok(views.html.index(users.isSuccess, users.getOrElse(List())))
	}

	def start = Action {
		Redirect(routes.Application.index)
	}

}
