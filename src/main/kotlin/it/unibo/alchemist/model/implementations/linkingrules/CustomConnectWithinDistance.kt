package it.unibo.alchemist.model.implementations.linkingrules

import it.unibo.alchemist.model.implementations.molecules.SimpleMolecule
import it.unibo.alchemist.model.implementations.neighborhoods.Neighborhoods
import it.unibo.alchemist.model.interfaces.*

open class CustomConnectWithinDistance<T, P : Position<P>>(val radius: Double, val accessPointRadius: Double, val accessPointMolecule: Molecule)
    : LinkingRule<T,P> {
    override fun isLocallyConsistent(): Boolean = false

    private val Node<T>.isAccessPoint
        get() = getConcentration(accessPointMolecule) as Boolean

    override fun computeNeighborhood(center: Node<T>, env: Environment<T, P>): Neighborhood<T> {
        val nbrs = env.getNodesWithinRange(center, radius)
        val nextNbrs = env.getNodesWithinRange(center, accessPointRadius)
        return Neighborhoods.make(env, center,
            if(center.isAccessPoint) {
                nbrs.addAll(nextNbrs.filter { it.isAccessPoint })
                nbrs
            } else nbrs
        )
    }
}