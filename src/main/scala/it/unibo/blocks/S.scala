package it.unibo.blocks

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._
import it.unibo.casestudy.CrowdEstimationLib

class S extends AggregateProgram  with StandardSensors with BlockG with CrowdEstimationLib with ScafiAlchemistSupport
  with CustomSpawn with TimeUtils {
  override def main(): Any = {
    val leader = myS2(300, nbrRange)
    node.put("leader", leader)
  }
/*
  val inf: (Double, ID) = (Double.PositiveInfinity, Int.MaxValue)

  def myS2(grain: Double, metric: Metric) = myBreakUsingUids(randomUid, grain, metric)

  def myBreakUsingUids(
                        uid: (Double, ID),
                        grain: Double,
                        metric: Metric): Boolean = {
    share(uid) { case (lead, nbrLead) =>
      myDistanceCompetition2(distanceTo(lead == uid, metric), nbrLead(), uid, grain)
    } == uid
  }

  def myDistanceCompetition2(distance: Double, nbrLead: (Double, ID), uid: (Double, ID), grain: Double): (Double, ID) = {
    mux (distance > grain) {
      uid
    } {
      val thr = 0.25 * grain;
      mux (distance >= thr) {
        inf
      } {
        mux (distance >= thr) {
          inf
        } {
          minHood(nbrLead)
        }
      }
    }
  }*/
}
