package graphinity.example.clients

import graphinity.core.InitVertexErr
import graphinity.core.OfVertex
import graphinity.core.Vertex
import graphinity.core.VertexClass

import scala.concurrent.duration._

class DClient extends Vertex {
  private var count: Int = 1

  override val initAttemts: Int = 1
  override val initInterval: FiniteDuration = 1 seconds

  //the client is independent of other clients
  override val relatesWith: Set[VertexClass] = Set.empty

  /**
   * {{{
   * Without simulate multiple initialization attempts
   * because there is no problem on 3rd party service side.
   * }}}
   *
   * @return error or instance
   */
  override def init: Either[InitVertexErr, OfVertex] = Right(this)
}
