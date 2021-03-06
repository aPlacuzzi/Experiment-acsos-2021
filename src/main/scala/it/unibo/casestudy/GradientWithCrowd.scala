/*
 * Copyright (C) 2016-2019, Roberto Casadei, Mirko Viroli, and contributors.
 * See the LICENSE file distributed with this work for additional information regarding copyright ownership.
*/

package it.unibo.casestudy

import it.unibo.alchemist.model.implementations.actions.TargetWalker
import it.unibo.alchemist.model.implementations.molecules.SimpleMolecule
import it.unibo.alchemist.model.implementations.positions.LatLongPosition
import it.unibo.alchemist.model.interfaces.GeoPosition
import it.unibo.alchemist.model.scafi.ScafiIncarnationForAlchemist._

/**
  * See papers:
  * - Building blocks for aggregate programming of self-organising applications (Beal, Viroli, 2014)
  * - Aggregate Programming for the Internet of Things (Beal et al., IEEE Computer, 2015)
  * In the latter, we read:
  *   - device communicate via once-per-second asynchronous local broadcasts with a range of 100 meters.13,16
  *   - Our example uses a simple conservative estimate of dangerous crowding via level of service (LoS) ratings,17
  *     with LoS D (>1.08 people/m2) indicating a crowd and LoS E (>2.17 people/
  *     m2) in a group of at least 300 people indicating potentially dangerous density. Density is estimated as ρ = |nbrs|/
  *     pπr2w, where |nbrs| counts neighbors within range r, p estimates the proportion of people with a device running
  *     the app (about 0.5 percent of marathon attendees), and w estimates the
  *     fraction of walkable space in the local
  *     urban environment.
  */
class GradientWithCrowd extends AggregateProgram  with StandardSensors with BlockG with CrowdEstimationLib with ScafiAlchemistSupport with CustomSpawn with TimeUtils {
  import SpawnInterface._
  override def main(): Any = {
    /* CROWD ESTIMATION
    *  * In FOCAS:
    * * p = 0.1; range = 15 // 30; wRange = 30 // 100; commRange = n.a.; avgThreshold = 2.17 people / m²;
    * sumThreshold = 300 people; maxDensity = 1.08 people / m²; timeFrame = 60; w = 0.25 (fraction of walkable space in the local urban environment)
    * */
    val isSource = node.get("isSource").asInstanceOf[Boolean]
    init(isSource)
    spawnDestination(isSource)
    val distToRiskZone = 30.0;
    val p = 0.005
    val crowdRange = 30
    val w = 0.25
    val crowdedDensity = 1.08
    val dangerousThreshold = 2.17
    val groupSize = 300
    val timeFrame = 60
    val crowding = crowdTrackingFull(p, crowdRange, w, crowdedDensity, dangerousThreshold, groupSize, timeFrame) // overcrowded(), atRisk(), or none()
    node.put("risk", crowding == AtRisk)
    val warning = computeWarning(100, crowding)
    node.put("warning", warning)
    node.put("channel1", false)
    node.put("channel2", false)
    node.put("channel3", false)
    var channelCount = 0
    sspawn[ID, Unit, Unit] (
      src => arg => {
        val k = rep(0)(_+1)
        val isChannelSource = isSource && mid() == src
        val isChannelDestination = node.get("isDestination").asInstanceOf[Boolean] && node.get("sourceId") == src
        branch(maxHood(k) > k  & isChannelSource) {
          POut((), External)
        } {
          val channel = channelToDestination(isChannelSource, isChannelDestination, 30, warning)
          node.put("_inChannel", channel._1)
          if (src == mid()) {
            node.put("_channelDistance", channel._2)
          }
          if (channel._1) {
            channelCount += 1
            src match {
              case 0 => node.put("channel1", true)
              case 1 => node.put("channel2", true)
              case 2 => node.put("channel3", true)
            }
          }
          navigateChannel(isChannelSource, channel)
          val state = if (removeDestination(isSource = isChannelSource)) Terminated else Bubble
          POut((), state)
        }
      },
      if (isSource && checkStartTime() && !isCloserToDestination()) Set(mid()) else Set.empty,
      ()
    )
    if (channelCount == 0 && node.has("frequency")) {
      node.put("frequency", 5)
    } else {
      node.put("frequency", 1)
    }
    exportData(isSource)
    warning
  }

  private def exportData(isSource: Boolean): Unit = {
    if (isSource && node.has("exportPrefix")) {
      val prefix = node.get("exportPrefix").asInstanceOf[String]
      node.put(prefix + "Distance", distanceToDestination())
      if (node.has("destinationId") && node.has("_channelDistance")) {
        node.put(prefix + "ChannelDistance", node.get("_channelDistance"))
      } else {
        node.put(prefix + "ChannelDistance", 0.0)
      }
      val myNode = alchemistEnvironment.getNodeByID(mid())
      val myPos = alchemistEnvironment.getPosition(myNode).asInstanceOf[GeoPosition]
      val distanceTraveled = node.getOption[GeoPosition]("previousPosition")
        .map(previousPos => previousPos.distanceTo(myPos))
        .getOrElse(0.0)
      node.put("previousPosition", myPos)
      node.put(prefix + "DistanceTraveled", node.getOrElse(prefix + "DistanceTraveled", 0.0) + distanceTraveled)
    }
    node.put("nodes", alchemistEnvironment.getNodes.size())
    node.put("rounds", node.getOrElse("rounds", 0) + 1)
  }

  private def init(isSource: Boolean): Unit = {
    if (isSource && !node.has("destinationPosition")) {
      val lat = node.get("latDestination").asInstanceOf[Double]
      val long = node.get("longDestination").asInstanceOf[Double]
      val destination = new LatLongPosition(lat, long)
      node.put("destinationPosition", destination)
    }
  }

  //private def isCloserToDestination(): Boolean = distanceToDestination() <= 5

  private def isCloserToDestination(): Boolean = {
    val myNode = alchemistEnvironment.getNodeByID(mid())
    val myPos = alchemistEnvironment.getPosition(myNode)
    node.getOption[GeoPosition]("destinationPosition")
      .exists(pos => pos.equals(myPos.asInstanceOf[GeoPosition]))
  }

  private def checkStartTime(): Boolean =alchemistTimestamp.toDouble >= node.getOrElse("startTime", Double.PositiveInfinity)

  private def distanceToDestination(): Double = {
    val myNode = alchemistEnvironment.getNodeByID(mid())
    val myPos = alchemistEnvironment.getPosition(myNode)
    if (node.has("destinationPosition")) {
      return myPos.asInstanceOf[GeoPosition].distanceTo(node.get("destinationPosition").asInstanceOf[GeoPosition])
    }
    Double.PositiveInfinity
  }

  private def removeDestination(isSource: Boolean): Boolean = {
    val myNode = alchemistEnvironment.getNodeByID(mid())
    if (isSource && node.has("destinationId") && isCloserToDestination()) {
      alchemistEnvironment.removeNode(alchemistEnvironment.getNodeByID(node.get("destinationId")))
      node.remove("destinationId")
      val reactionToRemove = myNode.getReactions
        .stream()
        .filter { reaction => reaction.getActions.stream().map{ action => action.getClass.getSimpleName }.anyMatch {
          name => name.equals(classOf[TargetWalker[_]].getSimpleName) }
        }
        .findFirst()
      if (reactionToRemove.isPresent) {
        myNode.removeReaction(reactionToRemove.get())
//        node.remove("target")
        return true
      }
    }
    false
  }

  private def navigateChannel(isSource: Boolean, channel: (Boolean, Double)) = {
    branch(channel._1 || isSource) {
      val myNode = alchemistEnvironment.getNodeByID(mid())
      val myPos = alchemistEnvironment.getPosition(myNode)
      val previousTarget = node.getOption[GeoPosition]("previousTarget")
      val optNewPos = includingSelf
        .mapNbrs(nbr((channel._2, myPos)))
        .filter(entry => previousTarget.isEmpty || !previousTarget.get.equals(entry._2._2.asInstanceOf[GeoPosition]))
        .filter(entry => entry._2._1 < channel._2)
        .minByOption(entry => entry._2._1)
        .map(entry => entry._2._2)
      if (isSource && optNewPos.isDefined && !optNewPos.get.equals(node.get("target"))) {
        if (myPos.asInstanceOf[GeoPosition].equals(node.get("target"))) {
          node.put("previousTarget", node.get("target"))
        }
        node.put("target", optNewPos.get)
      }
    } {}
  }

  private def spawnDestination(isSource: Boolean): Unit = {
    val myNode = alchemistEnvironment.getNodeByID(mid())
    rep(init = true) {
      case true =>
        branch (isSource && checkStartTime()) {
          val destination = myNode.cloneNode(alchemistTimestamp)
          destination.setConcentration(new SimpleMolecule("isSource"), false)
          destination.setConcentration(new SimpleMolecule("isDestination"), true)
          destination.setConcentration(new SimpleMolecule("human"), false)
          destination.setConcentration(new SimpleMolecule("accessPoint"), true)
          destination.setConcentration(new SimpleMolecule("sourceId"), mid())
          destination.removeConcentration(new SimpleMolecule("target"))
          val prefix = node.get("exportPrefix").asInstanceOf[String]
          destination.setConcentration(new SimpleMolecule(prefix + "Distance"), Double.MinValue) // reset export data
          destination.setConcentration(new SimpleMolecule(prefix + "ChannelDistance"), Double.MinValue) // reset export data
          destination.setConcentration(new SimpleMolecule(prefix + "DistanceTraveled"), Double.MinValue) // reset export data
          alchemistEnvironment.addNode(destination, node.get("destinationPosition").asInstanceOf[LatLongPosition])
          node.put("destinationId", destination.getId)
          false
        }  {
          true
        }
      case _ => false
    }
  }

  private def channelToDestination(source: Boolean, destination: Boolean, width: Double, warningZone: Boolean): (Boolean, Double) = {
    val ds = classicGradientWithShare(source, warningZone)
    val dd = classicGradientWithShare(destination, warningZone)
    mux (ds == Double.PositiveInfinity || dd == Double.PositiveInfinity) {
      (false, Double.PositiveInfinity)
    } {
      val db = distanceBetween(source, destination, () => mux(warningZone) { Double.PositiveInfinity } { nbrRange() })
      val inChannel = !(ds + dd == Double.PositiveInfinity && db == Double.PositiveInfinity) && ds + dd <= db + width
      (inChannel, if (inChannel) dd else Double.PositiveInfinity)
    }
  }

  // Much faster than 'classicGradient'
  def classicGradientWithShare(source: Boolean, warningZone: Boolean, metric: () => Double = nbrRange): Double =
    share(Double.PositiveInfinity){ case (d,nbrf) =>
      mux(source){
        0.0
      } {
        mux (warningZone) {
          Double.PositiveInfinity
        } {
          minHoodPlus(nbrf() + metric())
        }
      }
    }
}
