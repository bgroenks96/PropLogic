package edu.osu.cse.groenkeb.logic.proof.engine.learn

import edu.osu.cse.groenkeb.logic._
import edu.osu.cse.groenkeb.logic.proof.Premise
import edu.osu.cse.groenkeb.logic.proof.ProofContext

import scala.collection.Seq

object SentenceGraph {  
  def from(sentence: Sentence) = build(sentence)
  
  private def build(sentence: Sentence, parent: Option[GraphNode] = None): (SentenceGraph, GraphNode) = sentence match {
    case s@AtomicSentence(atom) =>
      val node = AtomicNode(s)
      val children = atom.terms.map(t => VarNode(t))
      (SentenceGraph(Map[GraphNode, Adjacency](node -> createAdj(node, children, parent))), node)
    case s@BinarySentence(left, right, _) =>
      val node = BinaryNode(s)
      val (leftGraph, leftNode) = build(left, Some(node))
      val (rightGraph, rightNode) = build(right, Some(node))
      (SentenceGraph(Map[GraphNode, Adjacency](node -> createAdj(node, Seq(leftNode, rightNode), parent))) ++ leftGraph ++ rightGraph, node)
    case s@UnarySentence(operand, _) =>
      val node = UnaryNode(s)
      val (operandGraph, operandNode) = build(operand, Some(node))
      (SentenceGraph(Map[GraphNode, Adjacency](node -> createAdj(node, Seq(operandNode), parent))) ++ operandGraph, node)
    case s@QuantifiedSentence(sentence, quant) =>
      val node = QuantifierNode(s)
      val (exprGraph, exprNode) = build(sentence, Some(node))
      (SentenceGraph(Map[GraphNode, Adjacency](node -> createAdj(node, Seq(exprNode), parent))) ++ exprGraph, node)
    case _ => ???
  }
  
  private def createAdj(node: GraphNode, children: Seq[GraphNode], parent: Option[GraphNode]): Adjacency =
    Adjacency(parent.map(n => Seq(n)).getOrElse(Nil), children)
}

final case class SentenceGraph(adj: Map[GraphNode, Adjacency]) {
  def nodes = adj.keys.seq
  
  def size = nodes.size
  
  def has(node: GraphNode) = adj.contains(node)
  
  def adjIn(node: GraphNode) = adj.getOrElse(node, Adjacency()).in
  
  def adjOut(node: GraphNode) = adj.getOrElse(node, Adjacency()).out
  
  def ++(graph: SentenceGraph) =
    SentenceGraph(graph.adj ++ adj.map{ case (k,v) => k -> (v ++ graph.adj.getOrElse(k, Adjacency())) })
    
  override def toString =
    adj.map {case (n, s) => n.toString() + "->[" + s + "]"}.mkString("{", "; ", "}")
}

final case class Adjacency(in: Seq[GraphNode] = Nil, out: Seq[GraphNode] = Nil) {
  def ++(adj: Adjacency) = Adjacency((in ++ adj.in).distinct, (out ++ adj.out).distinct)
  
  override def toString = s"{in: [${in.mkString(",")} | out: ${out.mkString(",")}]}"
}

sealed abstract class GraphNode
final case class QuantifierNode(sentence: QuantifiedSentence) extends GraphNode {
  override def toString = sentence.toString()
}
final case class BinaryNode(sentence: BinarySentence) extends GraphNode {
  override def toString = sentence.toString()
}
final case class UnaryNode(sentence: UnarySentence) extends GraphNode {
  override def toString = sentence.toString()
}
final case class AtomicNode(sentence: AtomicSentence) extends GraphNode {
  override def toString = sentence.toString()
}
final case class VarNode(term: Term) extends GraphNode {
  override def toString = term.toString()
}
