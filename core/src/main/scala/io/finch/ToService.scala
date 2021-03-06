package io.finch

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.util.Future
import io.finch.response.EncodeResponse
import shapeless.ops.coproduct.Folder
import shapeless.{Coproduct, Poly1}

import scala.annotation.implicitNotFound

/**
 * Represents a conversion from an [[Endpoint]] returning a result type `A` to a Finagle service from a request-like
 * type `R` to a [[Response]].
 */
@implicitNotFound(
"""You can only convert a router into a Finagle service if the result type of the router is one of the following:

  * A value of a type with an EncodeResponse instance
  * A coproduct made up of some combination of the above

${A} does not satisfy the requirement. You may need to provide an EncodeResponse instance for ${A} (or for some
part of ${A}).
"""
)
trait ToService[A] {
  def apply(endpoint: Endpoint[A]): Service[Request, Response]
}

object ToService extends LowPriorityToServiceInstances {
  /**
   * An instance for coproducts with appropriately typed elements.
   */
  implicit def coproductRouterToService[C <: Coproduct](implicit
    folder: Folder.Aux[EncodeAll.type, C, Response]
  ): ToService[C] = new ToService[C] {
    def apply(router: Endpoint[C]): Service[Request, Response] =
      endpointToService(router.map(folder(_)))
  }
}

trait LowPriorityToServiceInstances {

  protected[finch] def encodeResponse[A](a: A)(implicit encode: EncodeResponse[A]): Response = {
    val rep = Response()
    rep.content = encode(a)
    rep.contentType = encode.contentType
    encode.charset.foreach { cs => rep.charset = cs }

    rep
  }

  /**
   * An instance for types that can be transformed into a Finagle service.
   */
  implicit def valueRouterToService[A](implicit
    polyCase: EncodeAll.Case.Aux[A, Response]
  ): ToService[A] = new ToService[A] {
    def apply(router: Endpoint[A]): Service[Request, Response] =
      endpointToService(router.map(polyCase(_)))
  }

  protected def endpointToService(
    e: Endpoint[Response]
  ): Service[Request, Response] = new Service[Request, Response] {

    def apply(req: Request): Future[Response] = e(Input(req)) match {
       case Some((remainder, output)) if remainder.isEmpty =>
         output().map(o => o.toResponse(req.version))
       case _ => Future.value(Response(req.version, Status.NotFound))
     }
  }

  /**
   * A polymorphic function value that accepts types that can be transformed into a Finagle service from a request-like
   * type to a [[Response]].
   */
  protected object EncodeAll extends Poly1 {
    /**
     * Transforms a [[Response]] directly into a constant service.
     */
    implicit def response: Case.Aux[Response, Response] =
      at(r => r)

    /**
     * Transforms an encodeable value into a constant service.
     */
    implicit def encodeable[A: EncodeResponse]: Case.Aux[A, Response] =
      at(a => encodeResponse(a))
  }
}
