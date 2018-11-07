package ch.epfl.bluebrain.nexus.iam

import cats.effect.IO
import ch.epfl.bluebrain.nexus.iam.IOValues.IOValuesSyntax
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.Future

trait IOValues extends ScalaFutures {
  implicit final def ioValues[A](io: IO[A]): IOValuesSyntax[A] =
    new IOValuesSyntax(io) {
      override def futureValue(f: Future[A]): A = f.futureValue
    }
}

object IOValues {
  abstract class IOValuesSyntax[A](io: IO[A]) {
    def futureValue(f: Future[A]): A
    def ioValue: A = futureValue(io.unsafeToFuture())
  }
}
