package edu.osu.cse.groenkeb.logic

case class Atom(val predicate: Predicate, val terms: Term*) {
  def matches(atom: Atom) = atom != null && predicate.matches(atom.predicate) && terms.equals(atom.terms)
  
  def toRelation = ObjectRelation(predicate, terms:_*)
  
  override def toString = terms match {
    case Nil => predicate.toString()
    case terms => String.format("%s[%s]", predicate.toString(), terms.mkString("."))
  }
}

object Atom {
  final val absurdity = Atom(NamedPredicate("!"))
  
  def parse(str: String) = {
    val propPattern = "([A-Za-z]+)".r
    val objPattern = "([A-Za-z]+)\\[([A-Za-z0-9\\.]+)\\]".r
    str match {
      case objPattern(pred, argstr) => Atom(NamedPredicate(pred), argstr.split("\\.").map { s => Term(s) }:_*)
      case propPattern(pred) => Atom(NamedPredicate(pred))
      case _ => throw new Exception("invalid format for atom string: " + str)
    }
  }
}