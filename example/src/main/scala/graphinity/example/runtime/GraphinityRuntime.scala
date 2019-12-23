package graphinity.example.runtime

import zio.Runtime
import zio.clock.Clock
import zio.console.Console
import graphinity.core.GraphinityEnv
import zio.internal.Platform
import zio.internal.PlatformLive
import graphinity.core.GraphinityLive.GraphinityState
import graphinity.core.GraphinityLive.VertexState
import graphinity.core.Graphinity
import graphinity.core.VertexModule
import graphinity.core.GraphinityLive
import graphinity.core.VertexModuleLive
import graphinity.core.VertexClass
import graphinity.core.VertexCTag

trait GraphinityRuntime extends Runtime[GraphinityEnv] { self =>

  override val environment: GraphinityEnv = new Clock.Live with Console.Live with Graphinity.Live
  with VertexModule.Live {

    override lazy val graphinity: Graphinity.Service =
      unsafeRun(GraphinityLive.make(zio.Ref.make(GraphinityState(meta = Map.empty[VertexCTag, VertexState]))))

    override lazy val vertexModule: VertexModule.Service =
      unsafeRun(VertexModuleLive.make(zio.Ref.make(Set.empty[VertexClass]), zio.Ref.make(false)))
  }
  override val platform: Platform = PlatformLive.Default
}
