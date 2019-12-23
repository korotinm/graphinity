package graphinity

import java.lang.reflect.Modifier
import java.lang.reflect.Field
import zio.duration.Duration
import scala.concurrent.duration.FiniteDuration
import zio.ZIO
import zio.clock.Clock
import zio.console.Console
import scala.reflect.ClassTag
import scala.reflect.classTag

package object core {
  type OfVertex = Vertex
  type VertexClass = Class[_ <: OfVertex]
  type VertexCTag = ClassTag[_ <: OfVertex]
  type VertexHK[F[_]] = F[OfVertex]

  type GraphinityEnv = Clock with Console with Graphinity with VertexModule

  /**
   * @return true if all of clients and their links are ready
   */
  final def allReady: ZIO[GraphinityEnv, Nothing, Boolean] =
    ZIO.accessM(_.vertexModule.allReady)

  final def _isReady(vertClass: VertexClass): ZIO[GraphinityEnv, Nothing, Boolean] =
    ZIO.accessM(_.vertexModule.isReady(vertClass))

  final def regProcess(vertexMeta: (VertexClass, VertexHK[Option])): ZIO[GraphinityEnv, GraphinityError, Unit] =
    ZIO.accessM(_.vertexModule.regProcess(vertexMeta))

  final def addVertexCl(vertex: Vertex): ZIO[GraphinityEnv, Nothing, Unit] =
    ZIO.accessM(_.vertexModule.addVertexCl(vertex.vertexCl))

  final def registerVertex(vertexMeta: Graphinity.VertexMeta): ZIO[GraphinityEnv, GraphinityError, Unit] =
    ZIO.accessM(_.graphinity.registerVertex(vertexMeta))

  final def makeAsReady(vertexCl: VertexClass): ZIO[GraphinityEnv, GraphinityError, Boolean] =
    ZIO.accessM(_.graphinity.makeAsReady(vertexCl))

  final def getIfReady(vertexCl: VertexClass): ZIO[GraphinityEnv, Nothing, VertexHK[Option]] =
    ZIO.accessM(_.graphinity.getIfReady(vertexCl))

  final def areAllReady(vertClasses: Set[VertexClass]): ZIO[GraphinityEnv, Nothing, Boolean] =
    ZIO.accessM(_.graphinity.areAllReady(vertClasses))

  final def cleanAllStates: ZIO[GraphinityEnv, Nothing, Unit] =
    ZIO.accessM(_.graphinity.cleanAllStates)

  //utils
  final def toCTag(vertexCl: VertexClass): VertexCTag = ClassTag(vertexCl)

  final implicit def scalaDuration2zioDuration(fd: FiniteDuration): Duration = Duration.fromScala(fd)

  final def setFinalStaticField(field: Field, newValue: Object): Unit =
    setFinalStaticField(null, field, newValue)

  final def setFinalStaticField(
      instance: Object,
      field: Field,
      newValue: Object
    ): Unit = {
    field.setAccessible(true)

    val modifiersField = classOf[Field].getDeclaredField("modifiers");
    modifiersField.setAccessible(true);
    modifiersField.set(field, field.getModifiers() & ~Modifier.FINAL);

    field.set(instance, newValue)
  }

  final def mkField[T: ClassTag](name: String): Field =
    classTag[T].runtimeClass.getDeclaredField(name)
}
