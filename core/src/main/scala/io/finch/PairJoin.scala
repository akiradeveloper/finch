package io.finch

import shapeless.{HNil, ::, DepFn2}
import shapeless.ops.adjoin.Adjoin

/**
 * We need a version of [[shapeless.ops.adjoin.Adjoin]] that provides slightly different behavior in
 * the case of singleton results (we simply return the value, not a singleton `HList`).
 */
trait PairAdjoin[A, B] extends DepFn2[A, B]

trait LowPriorityPairAdjoin {
  type Aux[A, B, Out0] = PairAdjoin[A, B] { type Out = Out0 }

  implicit def pairAdjoin[A, B, Out0](implicit
    adjoin: Adjoin.Aux[A :: B :: HNil, Out0]
  ): Aux[A, B, Out0] =
    new PairAdjoin[A, B] {
      type Out = Out0

      def apply(a: A, b: B): Out0 = adjoin(a :: b :: HNil)
    }
}

object PairAdjoin extends LowPriorityPairAdjoin {
  implicit def singletonPairAdjoin[A, B, C](implicit
    adjoin: Adjoin.Aux[A :: B :: HNil, C :: HNil]
  ): Aux[A, B, C] = new PairAdjoin[A, B] {
    type Out = C

    def apply(a: A, b: B): C = adjoin(a :: b :: HNil).head
  }
}
