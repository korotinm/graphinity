package graphinity.example.clients

import graphinity.core.Vertex
import scala.concurrent.duration._
import graphinity.core.InitVertexErr
import graphinity.core.InitError
import graphinity.core.OfVertex

class DClient extends Vertex {
  private var count: Int = 1

  override val initAttemts: Int = 1
  override val initInterval: FiniteDuration = 1 seconds

  //the client is independent of other clients
  override val relatesWith = Set.empty

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
