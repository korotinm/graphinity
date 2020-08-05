package graphinity.example

import graphinity.core._
import graphinity.example.clients.AClient
import graphinity.example.clients.BClient
import graphinity.example.clients.CClient
import graphinity.example.clients.DClient
import zio.ExitCode
import zio.IO
import zio.Schedule
import zio.Task
import zio.URIO
import zio.ZIO
import zio.console._
import zio.duration._

object Example extends zio.App {

  def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val program = for {
      //instantiation of clients
      cClient <- IO.effectTotal(new CClient)
      bClient <- IO.effectTotal(new BClient(cClient))
      aClient <- IO.effectTotal(new AClient)
      dClient <- IO.effectTotal(new DClient)

      //[!!! Important steps:
      //the beginning of each instance initialization
      clients <- Task.succeed(List(dClient, cClient, bClient, aClient))
      initFiber <- ZIO.foreachPar(clients)(_.startInit).fork
      //!!!]

      //monitoring #1
      isReadyMonitoring <- whileOneOfClientsNotReady(clients).fork

      //monitoring #2
      allReadyMonitoring <- untilAllOfClientsAreReady.fork

      _ <- initFiber.join
      _ <- allReadyMonitoring.join
      _ <- isReadyMonitoring.join
    } yield ()

    program
      .provideCustomLayer(graphinityEnvLayer)
      .foldM(
        err => putStrLn(s"Something went wrong: ${err.message}") *> ZIO.succeed(ExitCode.failure),
        _ => ZIO.succeed(ExitCode.success)
      )
  }

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
