package it.unibo.alchemist.model.implementations.linkingrules

import it.unibo.alchemist.model.implementations.molecules.SimpleMolecule
import it.unibo.alchemist.model.implementations.neighborhoods.Neighborhoods
import it.unibo.alchemist.model.interfaces.*

open class CustomConnectWithinDistance<T, P : Position<P>>(val radius: Double, val virtualRadius: Double, virtualMoleculeKey: String)
    : LinkingRule<T,P> {

    val virtualMolecule: Molecule = SimpleMolecule(virtualMoleculeKey)

    override fun isLocallyConsistent(): Boolean = true

    private val Node<T>.isVirtualNode
        get() = getConcentration(virtualMolecule) as Boolean

    override fun computeNeighborhood(center: Node<T>, env: Environment<T, P>): Neighborhood<T> {
        val nbrs = env.getNodesWithinRange(center, radius)
        val nextNbrs = env.getNodesWithinRange(center, virtualRadius)
        if (center.isVirtualNode) {
            return Neighborhoods.make(env, center, nextNbrs)
        } else {
            nbrs.addAll(nextNbrs.filter { it.isVirtualNode })
            return Neighborhoods.make(env, center, nbrs)
        }
    }

}