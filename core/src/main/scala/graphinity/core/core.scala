package graphinity

import graphinity.core.Graphinity.Graphinity
import zio.ZIO
import zio.clock.Clock
import zio.console.Console
import zio.duration.Duration

import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag

package object core {
  type OfVertex = Vertex
  type VertexClass = Class[_ <: OfVertex]
  type VertexCTag = ClassTag[_ <: OfVertex]
  type VertexHK[F[_]] = F[OfVertex]

  type GraphinityEnv = Clock with Console with Graphinity /* with VertexModule*/

  /**
   * Add subtype of Vertex for further control
   *
   * @param vertex
   * @return
   */
  final def addVertex(vertex: Vertex): ZIO[Graphinity, Nothing, Unit] =
    ZIO.accessM(_.get.addVertexCl(vertex.vertexCl))

  /**
   * @return true if all of clients and their links are ready
   */
  final def allReady: ZIO[Graphinity, Nothing, Boolean] =
    ZIO.accessM(_.get.allReady)

  private[core] final def _isReady(vertClass: VertexClass): ZIO[Graphinity, Nothing, Boolean] =
    ZIO.accessM(_.get.isReady(vertClass))

  private[core] final def regProcess(
      vertexMeta: (VertexClass, VertexHK[Option])
  ): ZIO[Graphinity, GraphinityError, Unit] =
    ZIO.accessM(_.get.regProcess(vertexMeta))

  private[core] final def makeAsReady(vertexCl: VertexClass): ZIO[Graphinity, GraphinityError, Boolean] =
    ZIO.accessM(_.get.makeAsReady(vertexCl))

  //utils
  private[core] final def toCTag(vertexCl: VertexClass): VertexCTag = ClassTag(vertexCl)

  private[core] final implicit def scalaDuration2zioDuration(fd: FiniteDuration): Duration = Duration.fromScala(fd)
}
