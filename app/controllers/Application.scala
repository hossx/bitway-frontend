package controllers

import org.json4s.native.Serialization
import org.json4s.{ NoTypeHints, Extraction }
import play.api.mvc._
import play.api.libs.json._

import com.coinport.bitway.data._
import akka.pattern.ask
import services.{ NotificationService, AccountService }
import scala.concurrent.ExecutionContext.Implicits.global
import com.github.tototoshi.play2.json4s.native.Json4s
import models._

object Application extends Controller
    with AccountAction
    with QueryAction
    with WebsocketNotifyAction
    with PaymentAction
    with Authenticated
    with AccessLogging
    with Json4s {
  implicit val formats = Serialization.formats(NoTypeHints)

  def index = Action { implicit request => Ok(views.html.index()(request.session)) }

  def api = Action { implicit request => Ok(views.html.api()(request.session)) }

  def develop = Action { implicit request => Ok(views.html.develop()(request.session)) }

  def faq = Action { implicit request => Ok(views.html.faq()(request.session)) }

  def account = Action { implicit request => Ok(views.html.account()(request.session)) }

  def accessFlow = Action { implicit request => Ok(views.html.accessFlow()(request.session)) }

  def amlPolicy = Action { implicit request => Ok(views.html.amlPolicy()(request.session)) }

  def privatePolicy = Action { implicit request => Ok(views.html.privatePolicy()(request.session)) }

  def termsOfUse = Action { implicit request => Ok(views.html.termsOfUse()(request.session)) }

  def team = Action { implicit request => Ok(views.html.team()(request.session)) }

  def jobs = Action { implicit request => Ok(views.html.jobs()(request.session)) }

  def refundPage(invoiceId: String, currency: String) = Action { implicit request => Ok(views.html.refund(invoiceId, currency)(request.session)) }

  def invoiceDetail(invoiceId: String) = Action { implicit request => Ok(views.html.invoiceDetail(invoiceId)(request.session)) }

  def changePassword = Action { implicit request => Ok(views.html.changePassword()(request.session)) }

  def ledger(currency: String) = Action { implicit request =>
    val cur = Currency.valueOf(currency.toLowerCase().capitalize).getOrElse(Currency.Btc)
    Ok(views.html.ledger(cur.name.toUpperCase)(request.session))
  }

  def withdraw(currency: String) = Action { implicit request =>
    val cur = Currency.valueOf(currency.toLowerCase().capitalize).getOrElse(Currency.Btc)
    Ok(views.html.withdraw(cur.name.toUpperCase)(request.session))
  }

  def logout = Action {
    Redirect(routes.Application.index).withNewSession
  }

  def rates(cur: String) = Action.async {
    BackendAccess.routers.paymentActor ? GetBestBidRates() map {
      case BestBidRates(bidRates) =>
        if (bidRates.isEmpty) {
          Ok(Json.obj("error" -> "rates unavailable"))
        } else {
          val currency = Currency.valueOf(cur).getOrElse(Currency.Cny)
          val rateMap: Map[String, String] = bidRates.get(currency).get.piecewisePrice.map(x => x._1.toString -> x._2.toString).toMap
          Ok(Json.toJson(rateMap))
        }
    }
  }

  def redirect(invoiceId: String) = Action.async {
    implicit request =>
      BackendAccess.routers.invoiceAccessActor ? QueryInvoiceById(invoiceId) map {
        case QueryInvoiceResult(invoices, count) if invoices.nonEmpty =>
          val invoice = invoices(0)
          invoice.data.redirectURL match {
            case Some(url) =>
              val secret = AccountService.getSecretKey(invoice.merchantId)
              // POST invoice data to URL
              val result = NotificationService.post(invoice, url, secret)
              Ok(result).withHeaders("Content-Type" -> "text/html")
            case None =>
              NotFound
          }
        case _ => NotFound
      }
  }

  def debug = Action { implicit request =>
    val sb = new StringBuilder
    sb.append("============== Coinport HTTP Debug Page ==============\r\n\r\n")
    sb.append("[method]\r\n")
    sb.append(request.method)
    sb.append("\r\n\r\n")
    sb.append("[address]\r\n")
    sb.append(request.remoteAddress)
    sb.append("\r\n\r\n")
    sb.append("[headers]\r\n")
    sb.append(request.headers.toSimpleMap.mkString("\r\n"))
    sb.append("\r\n\r\n")
    sb.append("[params]\r\n")
    sb.append(request.queryString.mkString("\r\n"))
    sb.append("\r\n\r\n")
    sb.append("[body as Form Url Encoded]\r\n")

    println("aaaaaaaaaaaaa" + request.body)

    request.body.asFormUrlEncoded match {
      case Some(map) =>
        val str = map.mkString("\r\n")
        sb.append(str)
      case None =>
    }
    sb.append("\r\n\r\n")
    sb.append("[body as Json]\r\n")
    request.body.asJson match {
      case Some(json) =>
        sb.append(json.toString())
      case None =>
    }
    sb.append("\r\n\r\n")
    sb.append("=======================================================\r\n")
    println(sb.toString)
    Ok(sb.toString)
  }

  def test = Action {
    val u1 = AccountService.getUser("chunming@coinport.com")
    val user = User(123L, None, Some("a@a.com"), "pass123")
    AccountService.createUser(user)
    val result = AccountService.listUser
    Ok(Extraction.decompose(result))
  }
}
