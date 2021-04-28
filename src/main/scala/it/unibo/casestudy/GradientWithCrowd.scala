/*
 * Copyright (C) 2016-2019, Roberto Casadei, Mirko Viroli, and contributors.
 * See the LICENSE file distributed with this work for additional information regarding copyright ownership.
*/

package it.unibo.casestudy

import it.unibo.alchemist.model.implementations.actions.{MoveToTarget, ReproduceGPSTrace, TargetWalker}
import it.unibo.alchemist.model.implementations.environments.OSMEnvironment
import it.unibo.alchemist.model.implementations.molecules.SimpleMolecule
import it.unibo.alchemist.model.implementations.positions.LatLongPosition
import it.unibo.alchemist.model.implementations.reactions.Event
import it.unibo.alchemist.model.implementations.timedistributions.DiracComb
import it.unibo.alchemist.model.interfaces.{Environment, GeoPosition, Position, Position2D}
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
  override def main(): Any = {
    /* CROWD ESTIMATION
    *  * In FOCAS:
    * * p = 0.1; range = 15 // 30; wRange = 30 // 100; commRange = n.a.; avgThreshold = 2.17 people / m²;
    * sumThreshold = 300 people; maxDensity = 1.08 people / m²; timeFrame = 60; w = 0.25 (fraction of walkable space in the local urban environment)
    * */
    // 48.210255,16.377142
    val source = node.get("isSource").asInstanceOf[Boolean]
    val destination = node.get("isDestination").asInstanceOf[Boolean]
    val destinationPosition = new LatLongPosition(48.21023, 16.377142)
    spawnDestination(source, destinationPosition)
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
    val channel = channelToDestination(source, destination, 30, warning)
    node.put("_inChannel", channel._1)
    node.put("distance", channel._2)
    navigateChannel(source, channel)
    removeDestination(isSource = source, destinationPosition)
    warning
  }

  private def removeDestination(isSource: Boolean, destination: Position[_]) = {
    val myNode = alchemistEnvironment.getNodeByID(mid())
    val myPos = alchemistEnvironment.getPosition(myNode)
    if (isSource && node.has("destinationId") &&
        myPos.asInstanceOf[GeoPosition].distanceTo(destination.asInstanceOf[GeoPosition]) <= 5) {
      alchemistEnvironment.removeNode(alchemistEnvironment.getNodeByID(node.get("destinationId")))
      node.remove("destinationId")
      myNode.getReactions
        .stream()
        .filter { reaction => reaction.getActions.stream().map{ action => action.getClass.getSimpleName }.anyMatch {
          name => name.equals(classOf[TargetWalker[_]].getSimpleName) }
        }
        .findFirst()
        .ifPresent(it => {
          myNode.removeReaction(it)
          node.remove("target")
        })
    }
  }

  private def navigateChannel(isSource: Boolean, channel: (Boolean, Double)) = {
    if (isSource) {
      val myNode = alchemistEnvironment.getNodeByID(mid())
      val optNewPos = alchemistEnvironment.getNeighborhood(myNode).getNeighbors
        .stream()
        .filter(node => node.contains(new SimpleMolecule("_inChannel")))
        .map(node => (node.getConcentration(new SimpleMolecule("_inChannel")).asInstanceOf[Boolean],
          node.getConcentration(new SimpleMolecule("distance")).asInstanceOf[Double],
          alchemistEnvironment.getPosition(node)))
        .filter(node => node._1 && node._2 < channel._2 && node._2 > 0)
        .max((n1, n2) => n1._2.compare(n2._2))
        .map(node => node._3)
      if (isSource && optNewPos.isPresent) {
        node.put("target", optNewPos.get);
      }
    }
  }

  private def navigateChannel2(isSource: Boolean, channel: (Boolean, Double)) = {
    branch(channel._1 || isSource) {
      val myNode = alchemistEnvironment.getNodeByID(mid())
      val myPos = alchemistEnvironment.getPosition(myNode)
      val optNewPos = includingSelf
        .mapNbrs(nbr((channel._2, myPos)))
        .filter(entry => entry._2._1 < channel._2 && entry._2._1 > 0)
        .maxByOption(entry => entry._2._1)
        .map(entry => entry._2._2)
      if (isSource && optNewPos.isDefined) {
        node.put("target", optNewPos.get);
      }
    } {}
  }

  private def spawnDestination(isSource: Boolean, destinationPosition: LatLongPosition): Unit = {
    val myNode = alchemistEnvironment.getNodeByID(mid())
    rep(init = true) {
      case true => {
        if (isSource) {
          val destination = myNode.cloneNode(alchemistTimestamp)
          destination.setConcentration(new SimpleMolecule("isSource"), false)
          destination.setConcentration(new SimpleMolecule("isDestination"), true)
          destination.setConcentration(new SimpleMolecule("human"), false)
          destination.setConcentration(new SimpleMolecule("accessPoint"), true)
          destination.removeConcentration(new SimpleMolecule("target"))
          alchemistEnvironment.addNode(destination, destinationPosition)
          node.put("destinationId", destination.getId)
        }
        false
      }
      case _ => false
    }
  }

  private def channelToDestination(source: Boolean, destination: Boolean, width: Double, warningZone: Boolean): (Boolean, Double) = {
    val ds = classicGradientWithShare(source, warningZone)
    val dd = classicGradientWithShare(destination, warningZone)
    val db = distanceBetween(source, destination, () => if(warningZone) Double.PositiveInfinity else nbrRange())
    val inChannel = !(ds + dd == Double.PositiveInfinity && db == Double.PositiveInfinity) && ds + dd <= db + width
    (inChannel, if (inChannel) dd else Double.PositiveInfinity)
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
