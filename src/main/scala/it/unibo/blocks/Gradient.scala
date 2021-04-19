/*
 * Copyright (C) 2016-2019, Roberto Casadei, Mirko Viroli, and contributors.
 * See the LICENSE file distributed with this work for additional information regarding copyright ownership.
*/

package it.unibo.blocks

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

class Gradient extends AggregateProgram  with StandardSensors with BlockG with ScafiAlchemistSupport
  with CustomSpawn {
  override def main(): Any = {
    val distanceFromDestination = classicGradientWithShare(mid() == 55) // classicGradient(mid() == 55)
    node.put("distance", distanceFromDestination)
  }

  // Much faster than 'classicGradient'
  def classicGradientWithShare(source: Boolean, metric: () => Double = nbrRange): Double =
    share(Double.PositiveInfinity){ case (d,nbrf) =>
      mux(source){ 0.0 }{ minHoodPlus(nbrf() + metric()) }
    }
}
