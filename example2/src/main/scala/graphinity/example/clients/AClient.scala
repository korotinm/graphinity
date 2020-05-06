package graphinity.example.clients

import scala.concurrent.duration._
import graphinity.core.Vertex
import graphinity.core.InitVertexErr
import graphinity.core.OfVertex
import graphinity.core.VertexClass
import graphinity.core.InitError

class AClient extends Vertex {
  private var count: Int = 1

  override val initAttemts: Int = 30
  override val initInterval: FiniteDuration = 1 seconds

  override val relatesWith: Set[VertexClass] = Set(classOf[CClient], classOf[BClient])

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
    if (count >= 2) Right(this)
    else {
      count += 1
      Left(
        InitError("Achtung! Cannot be initialized! [AClient]", new RuntimeException("Smth. went wrong..."))
      )
    }
}
