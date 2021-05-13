package it.unibo.casestudy

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

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
  def countNearby(range: Double): Double = foldhood(0)(_ + _)(mux(nbr(node.get("human").asInstanceOf[Boolean]) & nbrRange() < range) { 1 } { 0 })

  def densityEstimation(p: Double, range: Double, w: Double): Double = countNearby(range) / (p * Math.PI * Math.pow(range, 2) * w)

  def isRecentEvent(event: Boolean, timeout: Double): Boolean = recentlyTrue(FiniteDuration(timeout.toLong, TimeUnit.SECONDS), event)

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
}
