package it.unibo.blocks

import it.unibo.alchemist.model.implementations.positions.{GPSPointImpl, LatLongPosition}
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._
import it.unibo.casestudy.CrowdEstimationLib

class Clone extends AggregateProgram  with StandardSensors with BlockG with CrowdEstimationLib with ScafiAlchemistSupport
  with CustomSpawn {
  override def main(): Any = {
    val myNode = alchemistEnvironment.getNodeByID(mid())
    val myPos = alchemistEnvironment.getPosition(myNode)
    rep(init = true) {
      case true => {
        val clone = myNode.cloneNode(alchemistTimestamp)
        val gpsPoint = new LatLongPosition(myPos.getCoordinate(0) + 0.0001, myPos.getCoordinate(1) + 0.0001)
        alchemistEnvironment.addNode(clone, gpsPoint)
        false
      }
      case _ => false
    }
    assert(alchemistEnvironment.getNodes.stream().map { n => alchemistEnvironment.getPosition(n) }.filter { pos => pos.equals(myPos) }.count() == 1)
    true
  }
}