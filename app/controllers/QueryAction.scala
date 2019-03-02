package controllers

import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.mvc.WebSocket.FrameFormatter
import com.coinport.bitway.data._
import play.api.data._
import play.api.data.Forms._
import play.api.libs.json._
import play.api.data.validation._
import akka.pattern.ask
import scala.concurrent.Future

trait QueryAction {
  self: Controller with BackendAccess with Authenticated =>

  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  def queryBTCAsset = authenticated { (merchant, req) =>
    implicit val request = req

    BackendAccess.routers.blockchainView ? QueryAsset(Currency.Btc) map {

      case result: QueryAssetResult =>

        Ok(Json.obj(
          "currency" -> result.currency.name.toUpperCase,
          "wallets" -> JsObject(result.amounts.map { kv =>
            (kv._1.name.toLowerCase -> JsNumber(kv._2))
          }.toSeq)
        ))

      case _ => InternalServerError("")
    }
  }
}
