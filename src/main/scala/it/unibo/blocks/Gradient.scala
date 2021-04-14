/*
 * Copyright (C) 2016-2019, Roberto Casadei, Mirko Viroli, and contributors.
 * See the LICENSE file distributed with this work for additional information regarding copyright ownership.
*/

package it.unibo.blocks

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._
import it.unibo.casestudy.CrowdEstimationLib

class Gradient extends AggregateProgram  with StandardSensors with BlockG with CrowdEstimationLib with ScafiAlchemistSupport {
  override def main(): Any = {
    val distanceFromDestination = classicGradient(mid() == 55)
    node.put("distance", distanceFromDestination)
  }
}
