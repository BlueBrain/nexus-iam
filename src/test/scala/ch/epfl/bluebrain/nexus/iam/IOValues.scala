package ch.epfl.bluebrain.nexus.iam

import cats.effect.IO
import org.scalactic.source
import org.scalatest.Matchers._
import org.scalatest.concurrent.ScalaFutures.{convertScalaFuture, PatienceConfig}

import scala.reflect.ClassTag

trait IOValues {
  implicit final def ioValues[A](io: IO[A]): IOValuesSyntax[A] =
    new IOValuesSyntax(io)

  protected class IOValuesSyntax[A](io: IO[A]) {
    def failed[Ex <: Throwable: ClassTag](implicit pos: source.Position): IO[Ex] = {
      val Ex = implicitly[ClassTag[Ex]]
      io.redeemWith({
        case Ex(ex) => IO.pure(ex)
        case other  =>
          IO(fail(s"Wrong throwable type caught, expected: '${Ex.runtimeClass.getName}', actual: '${other.getClass.getName}'"))
      }, a => IO(fail(s"The IO did not fail as expected, but computed the value '$a'")))
    }

    def ioValue(implicit config: PatienceConfig, pos: source.Position): A =
      io.unsafeToFuture().futureValue
  }
}

object IOValues extends IOValues