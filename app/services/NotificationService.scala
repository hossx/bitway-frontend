package services

import com.coinport.bitway.data._
import com.github.tototoshi.play2.json4s.native.Json4s
import controllers.Conversions
import org.json4s._
import org.json4s.native.Serialization._
import play.api.libs.ws._
import utils.SignUtil
import scala.concurrent.duration._
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await

object NotificationService extends Json4s {
  implicit val defaultFormats = formats(NoTypeHints)

  def post(invoice: Invoice, url: String, secret: String) = {
    val summary = Conversions.invoice2summary(invoice)
    val data = writePretty(Extraction.decompose(summary))
    val sign = SignUtil.sign(data, secret)

    println("post to " + url + "\nwith data: " + data)
    println("secret is " + secret)
    println("sign is " + sign)
    // POST invoice data to URL
    val future = WS.url(url)
      .withHeaders(
        "Content-Type" -> "application/json",
        "Rest-Sign" -> sign
      )
      .withRequestTimeout(5000)
      .post(data)
      .map {
        response: WSResponse =>
          println("response is\n" + response.body)
          response.body
      }

    Await.result(future, 5 second)
  }
}
