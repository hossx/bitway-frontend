package utils

import play.api.libs.iteratee.{ Enumerator, Iteratee }
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

object PlayUtil {

  def enumeratorToBytes(chunks: Enumerator[Array[Byte]]) = {
    val consume = Iteratee.consume[Array[Byte]]()
    val newIteratee = Iteratee.flatten(chunks(consume))
    val future: Future[Array[Byte]] = newIteratee.run
    Await.result(future, 1 second)
  }
}
