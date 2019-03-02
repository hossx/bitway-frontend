package controllers

import play.api._
import play.api.mvc._
import play.api.libs.json._

import com.coinport.bitway.data._
import akka.pattern.ask
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import play.api.Play.current
import play.api.libs.ws._
import scala.concurrent.Future
import play.api.libs.ws.WSAuthScheme

object DemoStore extends Controller {
  def pay = Action.async { implicit request =>
    val params = Map(
      "currency" -> Seq("cny"),
      "price" -> Seq("0.3"),
      "redirectURL" -> Seq(BackendAccess.httpPrefix + "/debug"),
      "orderId" -> Seq(System.currentTimeMillis.toString),
      "itemDesc" -> Seq("币丰支付Demo")
    )

    val url = BackendAccess.httpPrefix + "/api/v1/invoice"

    val theme = request.queryString.get("theme").flatMap(_.headOption).getOrElse("random")

    WS.url(url)
      .withHeaders("Content-Type" -> "application/x-www-form-urlencoded")
      .withRequestTimeout(5000)
      .withAuth("coinport1", "", WSAuthScheme.BASIC)
      .post(params)
      .map {
        response =>
          val targetUrl = (response.json \ "url").as[String] + "?theme=" + theme
          Redirect(targetUrl)
      }
  }

  def payWithDetails = Action.async {
    val params = Map(
      "currency" -> Seq("cny"),
      "price" -> Seq("0.35"),
      "redirectURL" -> Seq(BackendAccess.httpPrefix),
      "orderId" -> Seq("order00000001"),
      "itemDesc" -> Seq("夏季亮色系长裙"),
      "itemCode" -> Seq("77123"),
      "displayPrice" -> Seq("0.88"),
      "displayCurrency" -> Seq("USD"),
      "merchant.name" -> Seq("我的服装店"),
      "merchant.address" -> Seq("海淀区中关村"),
      "merchant.address2" -> Seq("欧美汇1122"),
      "merchant.city" -> Seq("北京"),
      "merchant.state" -> Seq("北京"),
      "merchant.zip" -> Seq("100123"),
      "merchant.country" -> Seq("中国"),
      "merchant.phone" -> Seq("18899881122"),
      "merchant.email" -> Seq("me@mystore.com"),
      "buyer.name" -> Seq("张三"),
      "buyer.address" -> Seq("闵行区金平路"),
      "buyer.address2" -> Seq("888弄88号"),
      "buyer.city" -> Seq("上海"),
      "buyer.state" -> Seq("上海"),
      "buyer.zip" -> Seq("111222"),
      "buyer.country" -> Seq("中国"),
      "buyer.phone" -> Seq("17744556677"),
      "buyer.email" -> Seq("buyer@gmail.com"))

    val url = BackendAccess.httpPrefix + "/api/v1/invoice"

    WS.url(url)
      .withHeaders("Content-Type" -> "application/x-www-form-urlencoded")
      .withRequestTimeout(5000)
      .withAuth("coinport1", "", WSAuthScheme.BASIC)
      .post(params)
      .map {
        response =>
          val targetUrl = (response.json \ "url").as[String] + "?theme=random"

          Redirect(targetUrl)
      }
  }
}
