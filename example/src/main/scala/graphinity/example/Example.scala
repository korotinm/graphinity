package graphinity.example

import zio.ZIO
import zio.Task
import zio.IO
import zio.console._
import zio.Schedule
import graphinity.core._
import graphinity.example.runtime.GraphinityRuntime
import graphinity.example.clients.CClient
import graphinity.example.clients.BClient
import graphinity.example.clients.AClient
import graphinity.example.clients.DClient

object Example extends GraphinityRuntime {
  import zio.duration._

  def run(args: List[String]): ZIO[GraphinityEnv, Nothing, Int] =
    (for {
      //instantiation of clients
      cClient <- IO.effectTotal(new CClient)
      bClient <- IO.effectTotal(new BClient(cClient))
      aClient <- IO.effectTotal(new AClient)
      dClient <- IO.effectTotal(new DClient)

      //[!!! Important steps:

      //1) registration all of instances for give ability to readiness check
      clients <- Task.succeed(List(dClient, cClient, bClient, aClient))
      _ <- ZIO.collectAll(clients.map(v => addVertexCl(v)))

      //2) the beginning of each instance initialization
      initFiber <- ZIO.collectAllPar(clients.map(_.startInit)).fork

      //!!!]

      //monitoring while one of clients is not ready
      isReadyMonitoring <- ZIO
        .collectAll(clients.map(v => v.isReady.map(b => (v, b))))
        .map { v =>
          val lines = v.map {
            case (inst, status) =>
              s"$status     $inst\n"
          }.mkString

          println(
            s"""STATUS    INSTANCE
            |$lines
            |""".stripMargin
          )

          v
        }
        .repeat(
          (Schedule.spaced(1300 milliseconds) <*
            Schedule.recurs(40)).untilInput(v => v.forall(_._2 == true))
        )
        .fork

      //monitoring until all of clients are ready
      allReadyMonitoring <- allReady
        .repeat(Schedule.spaced(50 milliseconds).untilInput(_ == true))
        .fork

      _ <- initFiber.join
      _ <- allReadyMonitoring.join
      _ <- isReadyMonitoring.join
    } yield ())
      .foldCause(_ => 1, _ => 0)

  def main(args: Array[String]): Unit =
    try sys.exit(
      unsafeRun(
        for {
          fiber <- run(args.toList).fork
          _ <- IO.effectTotal(java.lang.Runtime.getRuntime.addShutdownHook(new Thread {

            override def run() = {
              val _ = unsafeRunSync(fiber.interrupt)
            }
          }))
          result <- fiber.join
        } yield result
      )
    )
    catch { case _: SecurityException => }
}
