package it.unibo.alchemist.model.implementations.nodes

import it.unibo.alchemist.model.implementations.actions.{AbstractAction, ReproduceGPSTrace, RunProtelisProgram}
import it.unibo.alchemist.model.interfaces.{Environment, Molecule, Position, Time}

class ScafiNode[T, P<:Position[P]](env: Environment[T, P]) extends AbstractNode[T](env) {
  private var lastAccessedMolecule: Molecule = null

  override def createT = throw new IllegalStateException(s"The molecule $lastAccessedMolecule does not exist and cannot create empty concentration")

  override def getConcentration(mol: Molecule): T = {
    lastAccessedMolecule = mol
    super.getConcentration(mol)
  }

  override def cloneNode(currentTime: Time): AbstractNode[T] = {
    val clone = new ScafiNode(env)
    getContents.forEach { (mol, value) => clone.setConcentration(mol, value) }
    Console.println(getReactions.size())
    getReactions
      .stream()
      .filter {reaction => reaction.getActions.stream().map{ action => action.getClass.getSimpleName }.noneMatch {
        case "ReproduceGPSTrace" => {
          Console.println("pippo"); true
        }
        case name => {
          Console.println(name); false
        }
      }
      }
      .forEach { reaction => clone.addReaction(reaction.cloneOnNewNode(clone, currentTime))}
    clone
  }
}
