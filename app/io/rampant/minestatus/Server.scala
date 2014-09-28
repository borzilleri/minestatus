package io.rampant.minestatus

import java.io.{BufferedReader, InputStreamReader}
import java.util.Properties

import com.jcraft.jsch.{ChannelExec, JSch}
import play.api.Play.current

case class Server(id: String, name: String, host: String, remoteUser: Option[String], remoteCommand: Option[String]) {
	val LOGGER = play.api.Logger("server")

	lazy val user = remoteUser.getOrElse(current.configuration.getString("server.defaults.remote.user").get)
	lazy val remotePort = current.configuration.getInt("server.defaults.remote.port").getOrElse(22)

	def canStart = remoteCommand.isDefined

	def startServer: Boolean = {
		// Break out early if we have nothing to do.
		if (!remoteCommand.isDefined) return false

		val session = Server.getSession(user, host, remotePort)
		session.connect()

		val channel = session.openChannel("exec").asInstanceOf[ChannelExec]
		val response = new BufferedReader(new InputStreamReader(channel.getInputStream))

		channel.setCommand(s"${remoteCommand.get};")
		channel.connect()

		var msg: String = null
		while ( {
			msg = response.readLine
			msg != null
		}) {
			LOGGER.debug(msg)
		}
		val exitStatus = channel.getExitStatus
		if (0 != exitStatus) {
			LOGGER.error(s"Start command for $host exited with failure.")
		}

		channel.disconnect()
		session.disconnect()
		0 == exitStatus
	}
}

object Server {
	val JSCH = new JSch()
	val SSH_CONFIG = new Properties()
	protected val privateKeyName = "SERVER_PRIVATE_KEY"
	protected lazy val privateKey = current.configuration.getString("server.privateKey").get.getBytes

	JSCH.addIdentity(privateKeyName, privateKey, null, null)
	// TODO: Maybe load known hosts from env -> inputstream?
	SSH_CONFIG.put("StrictHostKeyChecking", "no")

	def getSession(user: String, host: String, port: Int) = {
		val session = JSCH.getSession(user, host, port)
		session.setConfig(Server.SSH_CONFIG)
		session
	}
}
