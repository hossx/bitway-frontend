package controllers

import play.api.mvc._
import services.AccountService
import sun.misc.BASE64Decoder
import com.coinport.bitway.data._
import utils.{ PlayUtil, SignUtil }
import scala.concurrent._
import akka.pattern.ask
import play.api.Logger

trait Authenticated {
  self: Controller with BackendAccess =>

  import scala.concurrent.ExecutionContext.Implicits.global

  private lazy val basicSt = "basic "

  def authenticated(f: (Merchant, Request[AnyContent]) => Future[Result]) =
    Action.async { request =>
      println(request.headers)
      request.headers.get("Authorization")
        .flatMap(decodeApiToken)
        .map { token =>
          BackendAccess.routers.accountActor ? ValidateToken(token) flatMap {
            case AccessDenied(reason) =>
              Logger.info(s"unauthenticated access from ip: ${getUserIPAddress(request)} with token: ${token}")
              Future.successful(Unauthorized)

            case AccessGranted(merchant) =>
              Logger.info(s"authenticated from ip: ${getUserIPAddress(request)} with token: ${token}")

              f(merchant, request).map {
                result =>
                  // get raw message
                  val buffer = PlayUtil.enumeratorToBytes(result.body)

                  // sign message
                  val secret = AccountService.getSecretKey(merchant.id)
                  val sign = SignUtil.sign(buffer, secret)

                  println("merchant: " + merchant.id)
                  println("secret:   " + secret)
                  println("sign:     " + sign)

                  // add header
                  result.withHeaders("Rest-Sign" -> sign)
              }
          }
        }.getOrElse {
          Logger.info("unauthorized!")
          Future.successful(Unauthorized)
        }
    }

  private def getUserIPAddress(request: RequestHeader): String = {
    return request.headers.get("x-forwarded-for").getOrElse(request.remoteAddress.toString)
  }

  private def decodeApiToken(auth: String): Option[String] = {
    if (auth.length() < basicSt.length()) {
      return None
    }
    val basicReqSt = auth.substring(0, basicSt.length())
    if (basicReqSt.toLowerCase() != basicSt) {
      return None
    }
    val basicAuthSt = auth.replaceFirst(basicReqSt, "")
    //BESE64Decoder is not thread safe, don't make it a field of this object
    val decoder = new BASE64Decoder()
    val decodedAuthSt = new String(decoder.decodeBuffer(basicAuthSt), "UTF-8")

    decodedAuthSt.split(":").headOption
  }
}
