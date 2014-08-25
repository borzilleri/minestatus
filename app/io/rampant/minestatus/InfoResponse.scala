package io.rampant.minestatus

import io.rampant.minecraft.ServerInfo

case class InfoResponse(info: ServerInfo) extends QueryResponse
