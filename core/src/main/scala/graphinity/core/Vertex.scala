package graphinity.core

import zio.IO
import zio.Schedule
import zio.URIO
import zio.ZIO
import zio.ZManaged

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

trait Vertex { self =>

  val vertexCl: VertexClass = self.getClass

  /**
   * number of attemts running Vertex initialisation
   */
  protected def initAttemts: Int

  /**
   * time interval for running Vertex initialisation
   */
  protected def initInterval: FiniteDuration

  /**
   * waiting time for Vertex initialising by default - initAttempts * initInterval
   */
  protected def initTimeout: FiniteDuration = FiniteDuration(initAttemts * initInterval.length, initInterval.unit)

  /**
   * the field used as interval for trying to make as ready
   */
  protected def tryMakeReadyInterval: FiniteDuration = FiniteDuration(1, SECONDS)

  /**
   * {{{
   * With using this method we can determine relationships between other clients/modules.
   * For example:
   * Module A has link with Module B.
   * Module B is not prepared(initialization in progress).
   * Then module A cannot be prepared because the link is not ready.
   * }}}
   * @return
   */
  def relatesWith: Set[VertexClass]

  /**
   * {{{
   * @return Either[InitVertexErr, OfVertex] -
   * returning either success result(instance of Vertex subtype)
   * or error if initialisation cannot been completed
   * }}}
   */
  protected def init: Either[InitVertexErr, OfVertex]

  def startInit: ZIO[GraphinityEnv, GraphinityError, Unit] = {
    val regStep = ZManaged
      .fromEffect(initialization)
      .use { mbInstance =>
        IO.succeed(vertexCl -> mbInstance)
      }
      .flatMap(regProcess)

    for {
      _ <- addVertex(self)
      _ <- regStep
      _ <- tryMakeReady(vertexCl)
    } yield ()
  }

  def isReady: URIO[GraphinityEnv, Boolean] = _isReady(vertexCl)

  private def tryMakeReady(vertCl: VertexClass) =
    makeAsReady(vertCl)
      .repeat(Schedule.spaced(tryMakeReadyInterval).untilInput(_ == true))

  private def initialization: ZIO[GraphinityEnv, InitVertexErr, VertexHK[Option]] =
    IO.fromEither(init)
      .retry(Schedule.recurs(initAttemts) <* Schedule.spaced(initInterval))
      .timeout(initTimeout)
}
