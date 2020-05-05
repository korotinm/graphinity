package graphinity.example.runtime

import graphinity.core.Graphinity
import graphinity.core.GraphinityEnv
import graphinity.core.GraphinityLive
import graphinity.core.GraphinityLive.GraphinityState
import graphinity.core.GraphinityLive.VertexState
import graphinity.core.VertexCTag
import graphinity.core.VertexClass
import zio.Has
import zio.Runtime
import zio.clock.Clock
import zio.console.Console
import zio.internal.Platform

trait GraphinityRuntime extends Runtime[GraphinityEnv] {
  self =>

  override val platform: Platform = Platform.default

  val graphinityImpl: Graphinity.Live =
    new Graphinity.Live {
      override def graphinity: Graphinity.Service =
        unsafeRun(
          GraphinityLive.make(zio.Ref.make(GraphinityState(meta = Map.empty[VertexCTag, VertexState])),
                              zio.Ref.make(Set.empty[VertexClass])))
    }

  val has: Has[Console.Service] with Has[Clock.Service] with Has[Graphinity.Service] =
    Has.allOf[Console.Service, Clock.Service, Graphinity.Service](
      Console.Service.live,
      Clock.Service.live,
      graphinityImpl.graphinity
    )

  override val environment: GraphinityEnv = has
}
