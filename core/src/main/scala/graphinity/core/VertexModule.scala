package graphinity.core

import zio.IO
import zio.URIO
import zio.ZIO
import zio.Ref
import graphinity.core.Graphinity.VertexMeta
import zio.UIO

trait VertexModule {
  def vertexModule: VertexModule.Service
}

object VertexModule {

  trait Service {
    def isReady(vertClass: VertexClass): URIO[GraphinityEnv, Boolean]
    def allReady: URIO[GraphinityEnv, Boolean]
    def addVertexCl(vertCl: VertexClass): UIO[Unit]
    def regProcess(vertexMeta: (VertexClass, VertexHK[Option])): ZIO[GraphinityEnv, GraphinityError, Unit]
  }

  trait VertexModuleDefault extends VertexModule {
    def vertexModule: VertexModule.Service
  }

  trait Live extends VertexModuleDefault
}

class VertexModuleLive(allVertexRef: Ref[Set[VertexClass]], allReadyRef: Ref[Boolean])
    extends VertexModule.Service {

  override def isReady(vertClass: VertexClass): URIO[GraphinityEnv, Boolean] =
    for {
      allReady <- allReadyRef.get
      res <- if (allReady) IO.succeed(true) else getIfReady(vertClass).map(_.isDefined)
    } yield res

  override def allReady: URIO[GraphinityEnv, Boolean] = {
    @inline def checkAll =
      for {
        allVertex <- allVertexRef.get
        areAllReady <- areAllReady(allVertex)
        res <- areAllReady match {
          case true  => allReadyRef.update(_ => true)
          case false => IO.succeed(false)
        }
      } yield res

    for {
      allReady <- allReadyRef.get
      res <- {
        if (allReady) IO.succeed(true)
        else checkAll
      }
    } yield res
  }

  override def regProcess(vertexMeta: (VertexClass, VertexHK[Option])): ZIO[GraphinityEnv, GraphinityError, Unit] =
    vertexMeta match {
      case (vertCl: VertexClass, mbVertex: VertexHK[Option]) =>
        registerVertex(VertexMeta(vertCl, mbVertex))
    }

  override def addVertexCl(vertCl: VertexClass): UIO[Unit] =
    allVertexRef.update(allVertex => allVertex + vertCl).map(_ => ())

  /* private def cleanAllVertexCl: Unit =
    setFinalStaticField(mkField[VertexModuleLive]("allVertexRef"), null) */
}

object VertexModuleLive {

  def make(allVertexRef: UIO[Ref[Set[VertexClass]]], allReadyRef: UIO[Ref[Boolean]]): UIO[VertexModuleLive] =
    for {
      avr <- allVertexRef
      arr <- allReadyRef
    } yield new VertexModuleLive(avr, arr)
}
