package it.unibo.blocks

import it.unibo.alchemist.model.implementations.positions.{GPSPointImpl, LatLongPosition}
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._
import it.unibo.casestudy.CrowdEstimationLib

class Clone extends AggregateProgram  with StandardSensors with BlockG with CrowdEstimationLib with ScafiAlchemistSupport
  with CustomSpawn {
  override def main(): Any = {
    rep(init = true) {
      case true => {
        val myNode = alchemistEnvironment.getNodeByID(mid())
        val clone = myNode.cloneNode(alchemistTimestamp)
        val myPos = alchemistEnvironment.getPosition(myNode)
        val gpsPoint = new LatLongPosition(myPos.getCoordinate(0) + 0.0001, myPos.getCoordinate(1) + 0.0001)
        alchemistEnvironment.addNode(clone, gpsPoint)
        false
      }
      case _ => false
    }

    true
  }
}