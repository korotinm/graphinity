package graphinity.core

sealed trait GraphinityError {
  def message: String
  def cause: Throwable
}

/**
 * Error for cases e.g.: there is happened smth. which fatally affects the further execution of the program.
 * For Graphinity system a fatal case is an instruction to complete the process
 */
final case class FatalError(message: String, cause: Throwable) extends GraphinityError

object FatalError {
  private val unknownState = "Unknown state"

  def apply(msg: String): FatalError = {
    val fullMsg = s"$unknownState: $msg"
    FatalError(fullMsg, new RuntimeException(fullMsg))
  }
}

/**
 * The error describes group of init errors
 */
sealed trait InitVertexErr extends GraphinityError

final case class InitError(message: String, cause: Throwable) extends InitVertexErr
