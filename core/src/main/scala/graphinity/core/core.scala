package graphinity

import graphinity.core.Graphinity.Graphinity
import graphinity.core.Graphinity.Service
import graphinity.core.GraphinityLive.GraphinityState
import graphinity.core.GraphinityLive.VertexState
import zio.Has
import zio.Ref
import zio.ZLayer
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

  type GraphinityEnv = Clock with Console with Graphinity

  /**
   * layers composition
   */
  val graphinityEnvLayer: ZLayer[Any, Nothing, GraphinityEnv] = {
    lazy val compositionLayer: ZLayer[Any, Nothing, Has[(Ref[GraphinityState], Ref[Set[VertexClass]])]] =
      ZLayer.fromEffect(
        for {
          stateRef <- Ref.make(GraphinityState(meta = Map.empty[VertexCTag, VertexState]))
          allVertexRef <- Ref.make(Set.empty[VertexClass])
        } yield (stateRef, allVertexRef)
      )

    //vertical composition: ROut of layer1 put into RIn of layer2 - allowing injection
    lazy val graphinityLive: ZLayer[Any, Nothing, Has[Service]] = compositionLayer >>> Graphinity.live

    //horizontal composition console ++ clock ++ graphinity
    graphinityLive.map(live => Has.allOf(Console.Service.live, Clock.Service.live) ++ live)
  }

  /**
   * @return true if all of clients and their links are ready
   */
  final def allReady: ZIO[Graphinity, Nothing, Boolean] =
    ZIO.accessM(_.get.allReady)

  /**
   * Add subtype of Vertex for further control
   *
   * @param vertex an instance of Vertex type
   * @return
   */
  private[core] final def addVertex(vertex: OfVertex): ZIO[Graphinity, Nothing, Unit] =
    ZIO.accessM(_.get.addVertexCl(vertex.vertexCl))

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

  private[core] final implicit def sDuration2zDuration(fd: FiniteDuration): Duration = Duration.fromScala(fd)
}
