package graphinity.example.clients

import graphinity.core.Vertex
import graphinity.core.OfVertex
import graphinity.core.InitVertexErr
import graphinity.core.VertexClass
import scala.concurrent.duration._

class BClient(cClient: CClient) extends Vertex {
  override val initAttemts: Int = 3
  override val initInterval: FiniteDuration = 1 seconds

  override val relatesWith: Set[VertexClass] = Set(classOf[CClient])

  /**
   * {{{
   * Without simulate multiple initialization attempts
   * because there is no problem on 3rd party service side.
   * }}}
   *
   * @return error or instance
   */
  override def init: Either[InitVertexErr, OfVertex] =
    Right(this)

  //specific code for the client
  def reverseClientNames: List[String] =
    cClient.getClientNames.map(_.reverse)
}
