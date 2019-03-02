package utils

import play.api.libs.Codecs._

object SignUtil {
  def sign(message: String, secret: String): String = {
    sign(message.getBytes("UTF-8"), secret)
  }

  def sign(message: Array[Byte], secret: String): String = {
    val text = message ++ secret.getBytes("UTF-8")
    val sign = md5(text)
    sign
  }
}
