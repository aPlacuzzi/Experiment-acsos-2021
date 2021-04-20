package it.unibo.casestudy

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._
import it.unibo.scafi.space.{Point2D, Point3D}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{DAYS, Duration, FiniteDuration}

/**
 * See papers:
 * - Building blocks for aggregate programming of self-organising applications (Beal, Viroli, 2014)
 * - Aggregate Programming for the Internet of Things (Beal et al., IEEE Computer, 2015)
 */
trait CrowdEstimationLib extends BuildingBlocks {
  self: AggregateProgram with StandardSensors with ScafiAlchemistSupport with CustomSpawn with TimeUtils =>
  val (high, low, none) = (2, 1, 0) // crowd level

  /**
   * Density is estimated as ρ = |nbrs|/pπr2w, where |nbrs| counts neighbors
   * within range r, p estimates the proportion of people with a device running
   * the app (about 0.5 percent of marathon attendees), and w estimates the
   * fraction of walkable space in the local urban environment.
   */
    // X ROBY, mi confermi che con questa foldhood conta i soli vicini umani (e non i virtuali) all'interno di range
  def countNearby(range: Double): Double = foldhood(0)(_ + _)(mux(nbr(node.get("human").asInstanceOf[Boolean]) & nbrRange() < range) { 1 } { 0 })

  def densityEstimation(p: Double, range: Double, w: Double): Double = countNearby(range) / (p * Math.PI * Math.pow(range, 2) * w)

  def isRecentEvent(event: Boolean, timeout: Double): Boolean = recentlyTrue(FiniteDuration(timeout.toLong, TimeUnit.SECONDS), event)

  /**
   * def dangerousDensity(p, range, dangerousDensity, groupSize, w) {
   * let partition = S(range, nbrRange);
   * let localDensity = densityEstimation(p, range, w);
   * let avg = summarize(partition, sum, localDensity, 0) / summarize(partition, sum, 1, 0);
   * let count = summarize(partition, sum, 1 / p, 0);
   * avg > dangerousDensity && count > groupSize
   * }
   */
  def dangerousDensityFull(p: Double, range: Double, dangerousDensity: Double, groupSize: Double, w: Double): Boolean = {
    val partition = myS2(range, nbrRange)
    val localDensity = densityEstimation(p, range, w)
    val avg = summarize(partition, _ + _, localDensity, 0.0) / summarize(partition, _ + _, 1.0, 0.0)
    val count = summarize(partition, _ + _, 1.0 / p, 0.0)
    broadcast(partition, avg > dangerousDensity && count > groupSize) // X ROBY: la broadcast qui serve?
  }

  /**
   * def crowdTracking(p, range, w, crowdedDensity, dangerousThreshold, groupSize, timeFrame) {
   * let densityEst = densityEstimation(p, range, w)
   * env.put("densityEst", densityEst)
   * if (isRecentEvent(densityEst > crowdedDensity, timeFrame)) {
   * if (dangerousDensity(p, range, dangerousThreshold, groupSize, w)) { overcrowded() } else { atRisk() }
   * } else { none() }
   * }
   *
   * @param p
   * @param range
   * @param w
   * @param crowdedDensity
   * @param dangerousThreshold
   * @param groupSize
   * @param timeFrame
   */
  def crowdTrackingFull(
    p: Double,
    range: Double,
    w: Double,
    crowdedDensity: Double,
    dangerousThreshold: Double,
    groupSize: Double,
    timeFrame: Double
  ): Crowding = {
    node.put("_near", countNearby(range))
    val densityEst = densityEstimation(p, range, w)
    branch (isRecentEvent(densityEst > crowdedDensity, timeFrame)) {
      /*branch (dangerousDensityFull(p, range, dangerousThreshold, groupSize, w)) { // Ignore difference between Overcrowded and atRisk
        Overcrowded.asInstanceOf[Crowding]
      } { AtRisk.asInstanceOf[Crowding] }*/
      AtRisk.asInstanceOf[Crowding]
    } {
      Fine.asInstanceOf[Crowding]
    }
  }

  def computeWarning(radius: Double, crowding: Crowding): Boolean = distanceTo(crowding == AtRisk) < radius && crowding != Overcrowded

  sealed trait Crowding

  case object Overcrowded extends Crowding

  case object AtRisk extends Crowding

  case object Fine extends Crowding

  val noAdvice = Point2D(Double.NaN, Double.NaN)

  val inf: (Double, ID) = (Double.PositiveInfinity, Int.MaxValue)

  def myS2(grain: Double, metric: Metric) = myBreakUsingUids(randomUid, grain, metric)

  def myBreakUsingUids(
    uid: (Double, ID),
    grain: Double,
    metric: Metric
  ): Boolean =
    share(uid) { case (lead, nbrLead) =>
      myDistanceCompetition2(distanceTo(lead == uid, metric), nbrLead(), uid, grain)
    } == uid

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
          minHood(nbr(nbrLead))
        }
      }
    }
  }
}
