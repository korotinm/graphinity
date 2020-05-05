package graphinity.example

import graphinity.core._
import graphinity.example.clients.AClient
import graphinity.example.clients.BClient
import graphinity.example.clients.CClient
import graphinity.example.clients.DClient
import graphinity.example.runtime.GraphinityRuntime
import zio.IO
import zio.Schedule
import zio.Task
import zio.ZIO
import zio.console._
import zio.duration._

object Example extends GraphinityRuntime {

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
      _ <- ZIO.foreach(clients)(v => addVertex(v))

      //2) the beginning of each instance initialization
      initFiber <- ZIO.foreachPar(clients)(_.startInit).fork

      //!!!]

      //monitoring #1
      isReadyMonitoring <- whileOneOfClientsNotReady(clients).fork

      //monitoring #2
      allReadyMonitoring <- untilAllOfClientsAreReady.fork

      _ <- initFiber.join
      _ <- allReadyMonitoring.join
      _ <- isReadyMonitoring.join
    } yield ())
      .provide(environment)
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

  //monitoring while one of clients is not ready
  private def whileOneOfClientsNotReady(clients: List[Vertex]) =
    ZIO
      .foreach(clients)(v => v.isReady.map(b => (v, b)))
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

  //monitoring until all of clients are ready
  private def untilAllOfClientsAreReady =
    allReady
      .repeat(Schedule.spaced(10 milliseconds).untilInput(_ == true))
      .flatMap(
        attempts =>
          /*your action*/
          putStrLn(s"\nAll of clients are ready! [NUMBER OF CHECK: $attempts]\n")
      )
}
