package edu.osu.cse.groenkeb.logic

/**
 * Relational ordered pair (s1, s2)
 */
sealed abstract class Relation

sealed abstract class ObjectRelation(val sentences: Sentence*) extends Relation {
  def result: SentenceRelation

  def sentenceResult = result.sentence

  def count = sentences.length

  def head = sentences(0)

  def tail = sentences(count - 1)

  def decompose(): List[ObjectRelation]

  def contains(r: ObjectRelation): Boolean

  override def toString() = String.format("%s: (%s)", this.getClass.getSimpleName, sentences.mkString(", "))
}

/**
 * Base type for meta-linguistic relations between objects in the object-language.
 */
sealed abstract class MetaRelation(val relations: ObjectRelation*) extends Relation {
  def contains(r: ObjectRelation) = relations.contains(r)
}

/**
 * Base type for all connective relations: (s1, s2) in C(s1, s2)
 * where C is some logical connective operator
 */
sealed abstract class ConnectiveRelation(s1: Sentence, s2: Sentence) extends ObjectRelation(s1, s2) {
  def left = head

  def right = tail

  def decompose() = List(s1.toRelation, s2.toRelation)

  // first check if connective sentence matches, then left sentence, then right sentence iff not equal to left
  def contains(r: ObjectRelation) = sentenceResult.matches(r.sentenceResult) || s1.toRelation.contains(r) || (!s1.matches(s2) && s2.toRelation.contains(r))
}

/**
 * Identity relation for any singular sentence s: (s, s)
 */
case class SentenceRelation(val sentence: Sentence) extends ObjectRelation(sentence) {
  def result = this

  def decompose() = List(sentence.toRelation)

  def contains(r: ObjectRelation) = sentence.matches(r.sentenceResult) || (decompose() match {
    case SentenceRelation(s) :: Nil => s match {
      case AtomicSentence(a) => s.matches(r.sentenceResult)
      case complexSentence => s.toRelation.contains(r)
    }
    case complexRelation :: Nil => complexRelation.contains(r)
  })
}

case class AbsurdityRelation() extends ObjectRelation(Sentences.absurdity()) {
  def sentence = Sentences.absurdity()

  def result = SentenceRelation(sentence)

  def decompose() = List()

  def contains(r: ObjectRelation) = false
}

// ----- META RELATIONS ------ //

case class TurnstileRelation(prem: SentenceRelation, conc: SentenceRelation) extends MetaRelation(prem, conc)

// ----- CONNECTIVE RELATIONS ----- //

case class NotRelation(s: Sentence) extends ConnectiveRelation(s, s) {
  def result = SentenceRelation(UnarySentence(s, Not()))
}

case class AndRelation(s1: Sentence, s2: Sentence) extends ConnectiveRelation(s1, s2) {
  def result = SentenceRelation(BinarySentence(s1, s2, And()))
}

case class OrRelation(s1: Sentence, s2: Sentence) extends ConnectiveRelation(s1, s2) {
  def result = SentenceRelation(BinarySentence(s1, s2, Or()))
}

case class ImpliesRelation(s1: Sentence, s2: Sentence) extends ConnectiveRelation(s1, s2) {
  def result = SentenceRelation(BinarySentence(s1, s2, Implies()))
}

// -------------------------------- //

case class NullObjectRelation() extends ObjectRelation(NullSentence(), NullSentence()) {
  def result = SentenceRelation(Sentences.nil())

  def decompose() = List()

  def contains(r: ObjectRelation) = false
}
