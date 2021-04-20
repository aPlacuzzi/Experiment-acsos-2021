/*
 * Copyright (C) 2010-2019, Danilo Pianini and contributors listed in the main project's alchemist/build.gradle file.
 *
 * This file is part of Alchemist, and is distributed under the terms of the
 * GNU General Public License, with a linking exception,
 * as described in the file LICENSE in the Alchemist distribution's top directory.
 */

package it.unibo.alchemist.model.implementations.linkingrules

import it.unibo.alchemist.model.implementations.molecules.SimpleMolecule
import it.unibo.alchemist.model.implementations.neighborhoods.Neighborhoods
import it.unibo.alchemist.model.interfaces.Environment
import it.unibo.alchemist.model.interfaces.Molecule
import it.unibo.alchemist.model.interfaces.Neighborhood
import it.unibo.alchemist.model.interfaces.Node
import it.unibo.alchemist.model.interfaces.Position
import org.danilopianini.util.ArrayListSet
import org.danilopianini.util.ListSet

class CustomConnectViaAccessPoint<T, P : Position<P>>(
    val nodeToNodeRange: Double,
    accesspointToAccesspointRange: Double,
    accessPointMolecule: String
) : CustomConnectWithinDistance<T, P>(nodeToNodeRange, accesspointToAccesspointRange, SimpleMolecule(accessPointMolecule)) {

    private val Node<T>.isAccessPoint
        get() = getConcentration(accessPointMolecule) as Boolean

    /*
    private fun Neighborhood<T>.closestAccessPoint(env: Environment<T, P>): Node<T>? {
        val closestAP = asSequence().filter { it.isAccessPoint }.minBy { env.getDistanceBetweenNodes(center, it) }
        if(closestAP!=null && env.getDistanceBetweenNodes(center, closestAP) <= nodeToNodeRange) return closestAP
        else return null
    }
     */

    private fun Neighborhood<T>.nearbyAccessPoints(env: Environment<T, P>): List<Node<T>> {
        return neighbors.filter { it.isAccessPoint }.toList()
    }

    override fun computeNeighborhood(center: Node<T>, env: Environment<T, P>): Neighborhood<T> =
        super.computeNeighborhood(center, env).run {
            // print("[${center.id}]")
            var nbrhood  = this.neighbors.toMutableSet()

            if (!center.isAccessPoint) {
                val newNbrs = this.nearbyAccessPoints(env).flatMap {
                    env.getNodesWithinRange(it, nodeToNodeRange).filter { it.id!=center.id && !it.isAccessPoint }
                }
                nbrhood.addAll(newNbrs)
            }

            Neighborhoods.make(env, center, nbrhood)
        }

    // override fun isLocallyConsistent() = false
}