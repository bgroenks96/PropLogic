package edu.osu.cse.groenkeb.logic.proof.engine

import scala.Left
import scala.Right
import scala.collection.immutable.Nil
import scala.collection.immutable.Seq
import scala.collection.immutable.Set
import scala.collection.immutable.Stream

import edu.osu.cse.groenkeb.logic._
import edu.osu.cse.groenkeb.logic.proof.rules._
import edu.osu.cse.groenkeb.logic.proof._

sealed abstract class SolverOpts
case object Trace extends SolverOpts

final class ProofSolver(implicit strategy: ProofStrategy = new NaiveProofStrategy(),
                        options: Seq[SolverOpts] = Nil) {
  
  def prove(context: ProofContext): Stream[ProofResult] = {
    implicit val cntxt = context
    ProofSearch(step { proof(strategy.premises) } )()
  }

  private def proof(premises: scala.Seq[Premise])(implicit context: ProofContext): ProofResult = {
    if (trace) println(context)
    premises match {
      case Nil => inferFrom(RuleSet(strategy.rules))
      case Seq(head, rem @ _*) => head match {
        case a: Assumption if context.hasGoal(a.sentence) => success(a.proof, proof(rem))
        case p @ Proof(s, rule, args, undischarged, _) if context.hasGoal(s) => success(p, { proof(rem) })
        case _ => proof(rem)
      }
    }
  }

  private def inferFrom(rules: RuleSet)(implicit context: ProofContext): ProofResult = {
    // inferFromResults matches the given sequence of Success results to a RuleArgs type and performs the final inference
    // step.
    def inferFromUnaryResult(res1: ProofResult)(implicit rule: Rule): Option[Proof] = res1 match {
      case Success(subproof, _, _) => rule.infer(UnaryArgs(subproof)) match {
        case Some(proof) => Some(proof)
        case None => None
      }
      case _ => None
    }

    def inferFromBinaryResults(res1: ProofResult, res2: ProofResult)(implicit rule: Rule): Option[Proof] = (res1, res2) match {
      case (Success(subproof1, _, _), Success(subproof2, _, _)) => rule.infer(BinaryArgs(subproof1, subproof2)) match {
        case Some(proof) => Some(proof)
        case None => None
      }
      case _ => None
    }

    def inferFromTernaryResults(res1: ProofResult, res2: ProofResult, res3: ProofResult)
                               (implicit rule: Rule): Option[Proof] = (res1, res2, res3) match {
      case (Success(subproof1, _, _), Success(subproof2, _, _), Success(subproof3, _, _)) =>
        rule.infer(TernaryArgs(subproof1, subproof2, subproof3)) match {
          case Some(proof) => Some(proof)
          case None => None
        }
      case _ => None
    }
    
    def inferFromNResults(resN: Seq[ProofResult])(implicit rule: Rule): Option[Proof] = resN match {
      case results if results.forall { r => r.isInstanceOf[Success] } =>
        rule.infer(NArgs(resN.collect({case Success(subproof, _, _) => subproof}))) match {
          case Some(proof) => Some(proof)
          case None => None
        }
      case _ => None
    }

    // advance discards the head of the first Stream in 'paramResults' that has more than a single result, leaving the remainder
    // of the Streams unchanged.
    def advance(paramResults: scala.collection.Seq[Stream[ProofResult]]): Seq[Stream[ProofResult]] = paramResults match {
      case Nil => Nil
      case Seq(Stream(head)) => Seq(Stream())
      case Seq(Stream(head, next, tail@_*), rem@_*) => Seq(Stream.cons(next, { tail.toStream })) ++ rem
      case Seq(Stream(head), rem@_*) => Seq(Stream(head)) ++ advance(rem)
    }

    // ------ Aggregator Functions ------- //

    def unaryResults(p0: RuleParam)(resultContext: ProofContext, paramResults: Seq[Stream[ProofResult]])(implicit rule: Rule): ProofResult =
      paramResults.map { s => s.filter { r => r.isInstanceOf[Success] } } match {
        case results@Seq(Stream(head, rem@_*)) => inferFromUnaryResult(head) match {
          case Some(proof) => success(proof, { unaryResults(p0)(resultContext, advance(results)) })
          case None => failure({ unaryResults(p0)(resultContext, advance(results)) })
        }
        case Seq(s1) => failure()
        // unaryResults only supports a single set of results for a single parameter
        case _ => throw new IllegalArgumentException("unexpected parameter count for unary results")
      }


    def binaryResults(p0: RuleParam, p1: RuleParam)(resultContext: ProofContext, paramResults: Seq[Stream[ProofResult]])
                     (implicit rule: Rule): ProofResult = {
      paramResults.map { s => s.filter { r => r.isInstanceOf[Success] } } match {
        case results@Seq(Stream(head1, rem1@_*), Stream(head2, rem2@_*)) => inferFromBinaryResults(head1, head2) match {
          case Some(proof) => success(proof, { binaryResults(p0, p1)(resultContext, advance(results)) })
          case None => failure({ binaryResults(p0, p1)(resultContext, advance(results)) })
        }
        case Seq(s1, s2) => failure()
        // binaryResults only supports two results for two parameters
        case _ => throw new IllegalArgumentException("unexpected parameter count for binary results")
      }
    }

    def ternaryResults(p0: RuleParam, p1: RuleParam, p2: RuleParam)
                      (resultContext: ProofContext, paramResults: Seq[Stream[ProofResult]])
                      (implicit rule: Rule): ProofResult = {
      paramResults.map { s => s.filter { r => r.isInstanceOf[Success] } } match {
        case results@Seq(Stream(head1, rem1@_*), Stream(head2, rem2@_*), Stream(head3, rem3@_*)) => 
          inferFromTernaryResults(head1, head2, head3) match {
            case Some(proof) => success(proof, { ternaryResults(p0, p1, p2)(resultContext, advance(results)) })
            case None => failure({ ternaryResults(p0, p1, p2)(resultContext, advance(results)) })
        }
        case Seq(s1, s2, s3) => failure()
        // ternaryResults only supports three results for two parameters
        case _ => throw new IllegalArgumentException("unexpected parameter count for binary results")
      }
    }
    
    def nResults(params: Seq[RuleParam])
                (resultContext: ProofContext, paramResults: Seq[Stream[ProofResult]])(implicit rule: Rule): ProofResult = {
      paramResults.map { s => s.filter { r => r.isInstanceOf[Success] } } match {
        case anyMissing if anyMissing.length < params.length => failure()
        case all if all.forall { res => !res.isEmpty } => inferFromNResults(all.map { r => r.head }) match {
          case Some(proof) => success(proof, { nResults(params)(resultContext, advance(all)) })
          case None => failure({ nResults(params)(resultContext, advance(all)) })
        }
        case _ => failure()
      }
    }

    def optionResults(resultContext: ProofContext, paramResults: Seq[Stream[ProofResult]]): ProofResult = paramResults match {
      case Seq(res) => res.head
      case _ => throw new IllegalArgumentException("unexpected parameter count for binary results")
    }

    // ------------------------- //

    // pendingParams creates a new 'Pending' instance for the given RuleParams and Rule
    def pendingParams(params: RuleParams)(implicit rule: Rule): Pending = params match {
      case UnaryParams(param0) => pending(unaryResults(param0), step { tryParam(param0) })
      case BinaryParams(param0, param1) => pending(binaryResults(param0, param1), step { tryParam(param0) }, step { tryParam(param1) })
      case TernaryParams(param0, param1, param2) =>
        pending(ternaryResults(param0, param1, param2),
                step { tryParam(param0) },
                step { tryParam(param1) },
                step { tryParam(param2) })
      case NParams(params) => pending(nResults(immutable(params)), params.map { p => step { tryParam(p) }}:_*)
      case OptionParams(opts @ _*) =>
        pending(optionResults, opts.map {
          params => step({ pendingParams(params) })
        }.reduce((acc, step) => acc.then(step)))
      case EmptyParams => rule.infer(EmptyArgs) match {
        case None => pending((c, r) => r.head.head, step { failure() } )
        case Some(proof) => pending((c, r) => r.head.head, step { success(proof) })
      }
    }

    rules match {
      case RuleSet(Nil) => failure()
      case RuleSet(Seq(head, rem @ _*)) => 
        def tryNext(rule: Rule, majors: Seq[Option[Sentence]]): ProofResult = majors match {
          case Nil => failure({ inferFrom(RuleSet(immutable(rem))) })
          case Seq(head, rem @ _*) => rule.params(head) match {
            case None => failure({ tryNext(rule, immutable(rem)) })
            case Some(params) => pendingParams(params)(rule).andThen(step({
              tryNext(rule, immutable(rem))
            }))
          }
        }
        
        tryNext(head, Seq(None) ++ (strategy.premises map { p => Some(p.sentence) }))
    }
  }

  private def tryParam(param: RuleParam)(implicit rule: Rule, context: ProofContext): ProofResult = param match {
    case EmptyProof(conc) => context.available.collect({ case a:Assumption => a }).find { a => a.matches(conc) } match {
      case Some(assumption) => success(assumption.proof)
      case None => failure()
    }
    case AnyProof(conc) => {
      val newContext = context.withGoal(conc, rule)
      proof(strategy.premises(newContext))(newContext)
    }
    case RelevantProof(conc, discharges, restrict@_*) => {
      val newContext = context.withGoal(conc, rule).withAssumptions(discharges.assumptions:_*).restrict(restrict:_*)
      proof(strategy.premises(newContext))(newContext)
    }
  }
  
  private def trace = this.options.contains(Trace)

  ///////// Utility Methods ///////////

  // convenience method that creates a new ProofStep instance from a call-by-name parameter
  private def step(stepFunc: => ProofResult)(implicit context: ProofContext) = ProofStep { stepFunc }

  private def success(proof: Proof, hint: SearchHint)(implicit context: ProofContext) = {
    strategy.decide(Success(proof, context, hint))
  }

  private def success(proof: Proof, continuation: => ProofResult)(implicit context: ProofContext) = {
    strategy.decide(Success(proof, context, Continue(step(continuation))))
  }

  private def success(proof: Proof)(implicit context: ProofContext) = strategy.decide(Success(proof, context, Cut()))

  private def failure(hint: SearchHint)(implicit context: ProofContext) = strategy.decide(Failure(context, hint))

  private def failure(continuation: => ProofResult)(implicit context: ProofContext) = {
    strategy.decide(Failure(context, Continue(step(continuation))))
  }

  private def failure()(implicit context: ProofContext) = strategy.decide(Failure(context, Cut()))

  private def pending(aggregator: (ProofContext, Seq[Stream[ProofResult]]) => ProofResult,
                      steps: ProofStep*)(implicit context: ProofContext) = {
    Pending(context, immutable(steps), aggregator)
  }

  private def immutable[T](seq: scala.Seq[T]) = scala.collection.immutable.Seq(seq:_*)
}
