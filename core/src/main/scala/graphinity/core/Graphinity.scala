package graphinity.core

import graphinity.core.Graphinity.Graphinity
import graphinity.core.Graphinity.VertexMeta
import graphinity.core.GraphinityLive.GraphinityState
import graphinity.core.GraphinityLive.GraphinityState.VertexMetaState
import zio.Has
import zio.IO
import zio.Ref
import zio.Task
import zio.UIO
import zio.URLayer
import zio.ZIO
import zio.ZLayer

object Graphinity {

  type Graphinity = Has[Service]

  trait Service extends Serializable {
    def makeAsReady(vertexCl: VertexClass): IO[GraphinityError, Boolean]
    def isReady(vertClass: VertexClass): UIO[Boolean]
    def allReady: UIO[Boolean]
    def addVertexCl(vertCl: VertexClass): UIO[Unit]
    def regProcess(vertexMeta: (VertexClass, VertexHK[Option])): ZIO[Graphinity, GraphinityError, Unit]
  }

  final case class VertexMeta(vertexCl: VertexClass, mbVertex: VertexHK[Option])

  trait GraphinityDefault {
    def graphinity: Graphinity.Service
  }

  trait Live extends GraphinityDefault

  val live: URLayer[Has[(Ref[GraphinityState], Ref[Set[VertexClass]])], Has[Service]] =
    ZLayer.fromService[(Ref[GraphinityState], Ref[Set[VertexClass]]), Service] {
      case (stateRef, allVertexRef) =>
        new GraphinityLive(stateRef, allVertexRef)
    }
}

class GraphinityLive(val stateRef: Ref[GraphinityState], allVertexRef: Ref[Set[VertexClass]])
    extends Graphinity.Service {
  import GraphinityLive._

  override def makeAsReady(vertexCl: VertexClass): IO[GraphinityError, Boolean] = {
    @inline def processStatus(status: StateStatus): IO[GraphinityError, Boolean] = status match {
      case Modified(_)                 => IO.succeed(true)
      case NonModified(_)              => IO.succeed(false)
      case err @ InstanceNonDefined(_) => IO.fail(FatalError(err.message, new RuntimeException(err.message)))
      case unknown                     => IO.fail(FatalError(unknown.message))
    }

    for {
      vCt <- IO.effectTotal(toCTag(vertexCl))
      status <- stateRef
        .modify(_.makeReadiness(vCt) match {
          case StateResult(status, state) => (status, state)
        })
      res <- processStatus(status)
    } yield res
  }

  override def addVertexCl(vertCl: VertexClass): UIO[Unit] =
    allVertexRef.update(allVertex => allVertex + vertCl).unit

  override def isReady(vertClass: VertexClass): UIO[Boolean] =
    getIfReady(vertClass).map(_.isDefined)

  override def allReady: UIO[Boolean] =
    for {
      allVertex <- allVertexRef.get
      res <- areAllReady(allVertex)
    } yield res

  override def regProcess(vertexMeta: (VertexClass, VertexHK[Option])): ZIO[Graphinity, GraphinityError, Unit] =
    vertexMeta match {
      case (vertCl: VertexClass, mbVertex: VertexHK[Option]) =>
        registerVertex(VertexMeta(vertCl, mbVertex))
    }

  private def registerVertex(vertexMeta: Graphinity.VertexMeta): IO[GraphinityError, Unit] = {
    @inline def processStatus(status: StateStatus): IO[GraphinityError, Unit] = status match {
      case Modified(_) => IO.unit
      case err @ (InstanceExists(_) | InstanceNonDefined(_) | RefersToItself(_)) =>
        IO.fail(FatalError(err.message, new RuntimeException(err.message)))
      case unknown => IO.fail(FatalError(unknown.message))
    }

    for {
      metaState <- IO.succeed(VertexMetaState(vertexMeta))
      status <- stateRef
        .modify(_.register(metaState) match {
          case StateResult(status, state) => (status, state)
        })

      res <- processStatus(status)
    } yield res
  }

  private def getIfReady(vertexCl: VertexClass): UIO[VertexHK[Option]] =
    for {
      gstate <- stateRef.get
      vertexCTag <- Task.succeed[VertexCTag](toCTag(vertexCl))
      res = gstate
        .vertexState(vertexCTag)
        .filter(_.isReady)
        .map(_.instance)
    } yield res

  /**
   * If vertClasses is empty then false or checking each of the elements
   *
   * @param vertClasses check all of these classes if they are ready to use
   * @return zio.UIO[Boolean] all of elements in non empty input argument are ready to use
   */
  private def areAllReady(vertClasses: Set[VertexClass]): UIO[Boolean] =
    vertClasses.toList match {
      case Nil => IO.succeed(false)
      case _ =>
        for {
          gstate <- stateRef.get
          vertCTags = vertClasses.map(toCTag)
          foundVertCTags = gstate.meta.keySet.intersect(vertCTags)
          res = if (vertCTags.size != foundVertCTags.size) false
          else gstate.meta.filter { case (vertCTag, _) => vertCTags.contains(vertCTag) }.values.forall(_.isReady)
        } yield res
    }
}

object GraphinityLive {
  import graphinity.core.Graphinity.VertexMeta

  def make(stateRef: UIO[Ref[GraphinityState]], allVertexRef: UIO[Ref[Set[VertexClass]]]): UIO[GraphinityLive] =
    for {
      sr <- stateRef
      avr <- allVertexRef
    } yield new GraphinityLive(sr, avr)

  final case class VertexState(instance: OfVertex, relations: Set[VertexCTag], isReady: Boolean) { self =>

    def makeReadiness: VertexState =
      self.copy(isReady = true)
  }

  final case class GraphinityState(meta: Map[VertexCTag, VertexState]) { self =>

    def vertexTags: Set[VertexCTag] = self.meta.keySet

    def vertexState(vertexCTag: VertexCTag): Option[VertexState] =
      self.meta.get(vertexCTag)

    def makeReadiness(vertexCt: VertexCTag): StateResult = {
      def relationsReadinessCheck(vState: VertexState): Boolean =
        vState.relations
          .map(self.vertexState)
          .forall(_.exists(_.isReady))

      self.meta.get(vertexCt) match {
        case Some(vState) if relationsReadinessCheck(vState) =>
          val newVState = vState.makeReadiness
          StateResult(status = Modified(s"Successfully made as ready to use [instance: ${vState.instance}]"),
                      state = self.copy(meta = self.meta + (vertexCt -> newVState)))
        case Some(vState) =>
          StateResult(
            status = NonModified(
              s"Cannot made as ready to use because not everyone is ready there in relations [instance: ${vState.instance}]"
            ),
            state = self
          )
        case None =>
          StateResult(status = InstanceNonDefined(vertexCt), state = self)
      }
    }

    def register(vertexMeta: VertexMetaState): StateResult =
      (self.meta.get(vertexMeta.vertexCt).map(_.instance), vertexMeta.mbVertex) match {
        case (Some(existsVertex), _) =>
          StateResult(
            status = InstanceExists(existsVertex),
            state = self
          )
        case (None, Some(newVertex)) =>
          //check if vertex refers to itself
          newVertex.relatesWith.contains(vertexMeta.vertexCl) match {
            case true =>
              StateResult(
                status = RefersToItself(vertexMeta.vertexCt),
                state = self
              )
            case false =>
              StateResult(
                status = Modified("Successfully updated"),
                state = self.add(vertexMeta.vertexCt, newVertex)
              )
          }
        case (None, None) =>
          StateResult(status = InstanceNonDefined(vertexMeta.vertexCt), state = self)
      }

    @inline private def add(vCt: VertexCTag, vInst: OfVertex): GraphinityState =
      self.copy(
        meta = self.meta + (vCt -> VertexState(instance = vInst,
                                               relations = vInst.relatesWith.map(cl => toCTag(cl)),
                                               isReady = false))
      )
  }

  object GraphinityState {

    final case class VertexMetaState(vertexCt: VertexCTag, vertexCl: VertexClass, mbVertex: VertexHK[Option])

    object VertexMetaState {

      def apply(vertexMeta: VertexMeta): VertexMetaState =
        VertexMetaState(toCTag(vertexMeta.vertexCl), vertexMeta.vertexCl, vertexMeta.mbVertex)
    }
  }

  final case class StateResult(status: StateStatus, state: GraphinityState)

  /*[status */
  sealed trait StateStatus {
    def message: String
  }

  sealed trait StateOk extends StateStatus

  sealed trait StateErr extends StateStatus

  final case class Modified(message: String) extends StateOk

  final case class NonModified(message: String) extends StateOk

  final case class InstanceExists(existsVertex: OfVertex) extends StateErr {
    override lazy val message: String = toString()

    override def toString: String =
      s"""
      |Warning! Instance already exists
      |Details about state:
      | - Info about exists vertex: "${existsVertex.toString}";
      |""".stripMargin
  }

  final case class InstanceNonDefined(vertexCt: VertexCTag) extends StateErr {
    override lazy val message: String = toString()

    override def toString: String =
      s"""
      |Warning! Instance of vertex not defined
      |Details about state:
      | - An attempt to register not defined vertex was detected: runtimeClass["${vertexCt.runtimeClass.getName}"];
      | - Reasons for not defined vertex:
      |     timeout(increase "timeout" to avoid its), error happened during in initialization]
      |""".stripMargin
  }

  final case class RefersToItself(vertexCt: VertexCTag) extends StateErr {
    override lazy val message: String = toString()

    override def toString: String =
      s"""
      |Warning! The vertex refers to iself
      |Details about state:
      | - Info about vertex: runtimeClass["${vertexCt.runtimeClass.getName}"]
      |""".stripMargin
  }
  /* status]*/
}
