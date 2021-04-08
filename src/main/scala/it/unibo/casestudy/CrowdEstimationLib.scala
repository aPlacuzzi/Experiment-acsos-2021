package it.unibo.casestudy

import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._
import it.unibo.scafi.space.{Point2D, Point3D}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}

/**
 * See papers:
 * - Building blocks for aggregate programming of self-organising applications (Beal, Viroli, 2014)
 * - Aggregate Programming for the Internet of Things (Beal et al., IEEE Computer, 2015)
 */
trait CrowdEstimationLib extends BuildingBlocks {
  self: AggregateProgram with StandardSensors with ScafiAlchemistSupport =>
  val (high, low, none) = (2, 1, 0) // crowd level

  def unionHoodPlus[A](expr: => A): List[A] =
    foldhoodPlus(List[A]())(_ ++ _) {
      List[A](expr)
    }

  /**
   * Density is estimated as ρ = |nbrs|/pπr2w, where |nbrs| counts neighbors
   * within range r, p estimates the proportion of people with a device running
   * the app (about 0.5 percent of marathon attendees), and w estimates the
   * fraction of walkable space in the local urban environment.
   */
  def countNearby(range: Double): Double =
    includingSelf.sumHood(mux(node.has("human") && nbrRange() < range) { 1 } { 0 })

  def densityEstimation(p: Double, range: Double, w: Double): Double = countNearby(range) / (p * Math.PI * Math.pow(range, 2) * w)

  def isRecentEvent(event: Boolean, timeout: Double): Boolean = {
    branch(event) {
      true
    } {
      timerLocalTime(FiniteDuration(timeout.toLong, TimeUnit.SECONDS)) > 0
    }
  }

  def timerLocalTime(dur: Duration): Long = T(initial = dur.toNanos, dt = deltaTime().toNanos)

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
    val partition = S(range, nbrRange)
    node.put("leader", partition)
    val localDensity = densityEstimation(p, range, w)
    val avg = summarize(partition, _ + _, localDensity, 0.0) / summarize(partition, _ + _, 1.0, 0.0)
    val count = summarize(partition, _ + _, 1.0 / p, 0.0)
    avg > dangerousDensity && count > groupSize
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
  def crowdTrackingFull(p: Double,
                        range: Double,
                        w: Double,
                        crowdedDensity: Double,
                        dangerousThreshold: Double,
                        groupSize: Double,
                        timeFrame: Double): Crowding = {
    val densityEst = densityEstimation(p, range, w)
    mux (isRecentEvent(densityEst > crowdedDensity, timeFrame)) {
      if (dangerousDensityFull(p, range, dangerousThreshold, groupSize, w)) {
        Overcrowded
      } else AtRisk
    } {
      Fine
    }
  }

  def computeWarning(radius: Double, crowding: Crowding): Boolean = distanceTo(crowding == AtRisk) < radius && crowding != Overcrowded

  sealed trait Crowding

  case object Overcrowded extends Crowding

  case object AtRisk extends Crowding

  case object Fine extends Crowding

  val noAdvice = Point2D(Double.NaN, Double.NaN)
}
