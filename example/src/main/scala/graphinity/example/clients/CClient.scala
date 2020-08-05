package graphinity.example.clients

import graphinity.core.Vertex
import graphinity.core.InitVertexErr
import graphinity.core.OfVertex
import graphinity.core.InitError
import graphinity.core.VertexClass

import scala.concurrent.duration._

class CClient extends Vertex {
  private var count: Int = 1

  override val initAttemts: Int = 6
  override val initInterval: FiniteDuration = 1 seconds

  //the client is independent of other clients
  override val relatesWith: Set[VertexClass] = Set.empty

  /**
   *{{{
   * Simulate multiple initialization attempts e.g.:
   * - there is redeploying on 3rd party service side
   * - 3rd party service crashed for several seconds
   *   after that restart policy service of kubernetes gave new life him
   *}}}
   * @return error or instance
   */
  override def init: Either[InitVertexErr, OfVertex] =
    if (count >= 5) Right(this)
    else {
      count += 1
      Left(InitError("Achtung! Cannot be initialized! [CClient]", new RuntimeException("Aaaaaaa....")))
    }

  //specific code for the client
  def getClientNames: List[String] =
    List("Portos", "Malysh", "Murzik", "Sharik")
}
