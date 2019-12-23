package graphinity.core

import zio.UIO
import zio.IO
import zio.Task
import zio.Ref
import graphinity.core.GraphinityLive.GraphinityState.VertexMetaState
import graphinity.core.GraphinityLive.GraphinityState

trait Graphinity {
  def graphinity: Graphinity.Service
}

object Graphinity {

  trait Service {
    def registerVertex(vertexMeta: VertexMeta): IO[GraphinityError, Unit]

    def makeAsReady(vertexCl: VertexClass): IO[GraphinityError, Boolean]

    def getIfReady(vertexCl: VertexClass): UIO[VertexHK[Option]]

    /**
     * If vertClasses is empty then false or checking each of the elements
     *
     * @param vertClasses check all of these classes if they are ready to use
     * @return zio.UIO[Boolean] all of elements in non empty input argument are ready to use
     */
    def areAllReady(vertClasses: Set[VertexClass]): UIO[Boolean]

    def cleanAllStates: UIO[Unit]
  }

  final case class VertexMeta(vertexCl: VertexClass, mbVertex: VertexHK[Option])

  trait GraphinityDefault extends Graphinity {
    override def graphinity: Service
  }

  trait Live extends GraphinityDefault
}

class GraphinityLive(stateRef: Ref[GraphinityState]) extends Graphinity.Service {
  import GraphinityLive._

  override def registerVertex(vertexMeta: Graphinity.VertexMeta): IO[GraphinityError, Unit] = {
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

  override def getIfReady(vertexCl: VertexClass): UIO[VertexHK[Option]] =
    for {
      gstate <- stateRef.get
      vertexCTag <- Task.succeed[VertexCTag](toCTag(vertexCl))
      res = gstate
        .vertexState(vertexCTag)
        .filter(_.isReady)
        .map(_.instance)
    } yield res

  override def areAllReady(vertClasses: Set[VertexClass]): UIO[Boolean] =
    vertClasses.toList match {
      case Nil => IO.succeed(false)
      case _ =>
        for {
          gstate <- stateRef.get
          vertCTags = vertClasses.map(toCTag(_))
          foundVertCTags = gstate.meta.keySet.filter(vertCTags.contains(_))
          res = if (vertCTags.size != foundVertCTags.size) false
          else gstate.meta.filter { case (vertCTag, _) => vertCTags.contains(vertCTag) }.values.forall(_.isReady)
        } yield res
    }

  override def cleanAllStates: UIO[Unit] = IO.effectTotal {
    val f = mkField[GraphinityLive]("stateRef")
    setFinalStaticField(this, f, null)
  }
}

object GraphinityLive {
  import graphinity.core.Graphinity.VertexMeta

  def make(stateRef: UIO[Ref[GraphinityState]]): UIO[GraphinityLive] =
    for {
      sr <- stateRef
    } yield new GraphinityLive(sr)

  final case class VertexState(
      instance: OfVertex,
      relations: Set[VertexCTag],
      isReady: Boolean) { self =>

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

    @inline private final def add(vCt: VertexCTag, vInst: OfVertex): GraphinityState =
      self.copy(
        meta = self.meta + (vCt -> VertexState(instance = vInst,
                                               relations = vInst.relatesWith.map(cl => toCTag(cl)).toSet,
                                               isReady = false))
      )
  }

  object GraphinityState {

    final case class VertexMetaState(
        vertexCt: VertexCTag,
        vertexCl: VertexClass,
        mbVertex: VertexHK[Option])

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

    override def toString(): String =
      s"""
      |Warning! Instance already exists
      |Details about state:
      | - Info about exists vertex: "${existsVertex.toString()}";
      |""".stripMargin
  }

  final case class InstanceNonDefined(vertexCt: VertexCTag) extends StateErr {
    override lazy val message: String = toString()

    override def toString(): String =
      s"""
      |Warning! Instance of vertex not defined
      |Details about state:
      | - An attempt to register not defined vertex was detected: runtimeClass["${vertexCt.runtimeClass
           .getName()}"];
      | - Reasons for not defined vertex:
      |     timeout(increase "timeout" to avoid its), error happened during in initialization]
      |""".stripMargin
  }

  final case class RefersToItself(vertexCt: VertexCTag) extends StateErr {
    override lazy val message: String = toString()

    override def toString(): String =
      s"""
      |Warning! The vertex refers to iself
      |Details about state:
      | - Info about vertex: runtimeClass["${vertexCt.runtimeClass.getName()}"]
      |""".stripMargin
  }
  /* status]*/
}
