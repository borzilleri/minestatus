package io.rampant.minestatus

import java.net.DatagramSocket

import akka.actor.Actor
import io.rampant.minecraft.Query

import scala.util.{Failure, Success}

class StatusWorker extends Actor {
	val socket = new DatagramSocket()

	override def receive: Receive = {
		case r: QueryRequest =>
			val query = Query(r.host, r.port, Some(socket))
			query.serverInfo match {
				case Success(info) => sender ! InfoResponse(info)
				case Failure(e) => sender ! OfflineResponse
			}
	}


	@scala.throws[Exception](classOf[Exception])
	override def postStop(): Unit = {
		super.postStop()
		if (null != socket && !socket.isClosed) {
			socket.close()
		}
	}
}
