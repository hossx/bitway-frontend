package controllers

import play.api.mvc._
import com.coinport.bitway.data._
import play.api.data._
import play.api.data.Forms._
import play.api.libs.json._
import play.api.data.validation._
import akka.pattern.ask
import scala.concurrent.Future
import Conversions._
import com.github.tototoshi.play2.json4s.native.Json4s
import org.json4s.{ NoTypeHints, Extraction }
import org.json4s.native.Serialization
import Util._
import scala.Some
import play.api.data.validation.ValidationError

object CreateInvoiceAction extends Controller with Authenticated with BackendAccess with Json4s {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  implicit val formats = Serialization.formats(NoTypeHints)

  def createInvoice = authenticated {
    (merchant, req) =>
      val invoiceData = getInvoiceFromPostData(req.body, merchant)

      val updateInvoiceData =
        if (invoiceData.merchant.isEmpty) invoiceData
        else invoiceData.copy(merchant = merchant.contact)

      println("-" * 20)
      println("Create Invoice by merchant " + merchant.id + " , with name " + merchant.name)
      println(updateInvoiceData)
      println("-" * 20)

      BackendAccess.routers.blockchainActor ? CreateInvoice(updateInvoiceData, merchant.id, merchant.name, None) map {
        case InvoiceCreationFailed(errorCode, reason) => InternalServerError(Json.obj("error" -> reason))

        case InvoiceCreated(invoice: Invoice) =>
          // step 3: create a InvoiceSummary and return it to merchant as JSON.

          Ok(Extraction.decompose(invoice2summary(invoice)))

        case error: AllocateNewAddressResult =>
          InternalServerError(Json.obj("error" -> "Failed to generate bitcoin payment address. Please try again."))

        case x =>
          println("------bad request")
          BadRequest(Json.obj("error" -> x.toString))
      }
  }

  def getInvoiceFromPostData(data: AnyContent, merchant: Merchant) =
    try {
      val (speedEnum, speed): (TransactionSpeed, Int) = getSpeed(getParamFromAnyContent(data, "transactionSpeed"))
      var price = getParamFromAnyContent(data, "price").get.toDouble
      val currency = Currency.valueOf(getParamFromAnyContent(data, "currency").get).get

      if (currency == Currency.Btc)
        price = getParamFromAnyContent(data, "priceUnit") match {
          case Some("mbtc") => (BigDecimal(price) * BigDecimal(0.001)).toDouble
          case Some("bits") => (BigDecimal(price) * BigDecimal(0.000001)).toDouble
          case Some("satoshi") => (BigDecimal(price) * BigDecimal(0.00000001)).toDouble
          case _ => price
        }

      InvoiceData(
        price = price,
        currency = currency,
        posData = getParamFromAnyContent(data, "posData"),
        notificationURL = getParamFromAnyContent(data, "notificationURL"),
        transactionSpeed = speed,
        fullNotifications = getParamFromAnyContent(data, "fullNotifications").map(_.toBoolean).getOrElse(false),
        notificationEmail = getParamFromAnyContent(data, "notificationEmail"),
        redirectURL = getParamFromAnyContent(data, "redirectURL"),
        physical = getParamFromAnyContent(data, "physical").map(_.toBoolean).getOrElse(false),
        orderId = getParamFromAnyContent(data, "orderId"),
        itemDesc = getParamFromAnyContent(data, "itemDesc"),
        itemCode = getParamFromAnyContent(data, "itemCode"),
        buyer = getContactFromData(data, "buyer"),
        merchant = getContactFromData(data, "merchant"),
        displayPrice = getParamFromAnyContent(data, "displayPrice").map(_.toDouble).getOrElse(price),
        displayCurrency = getParamFromAnyContent(data, "displayCurrency").map(c => Currency.valueOf(c).get).getOrElse(currency),
        fee = BigDecimal(price * merchant.feeRate + merchant.constFee).setScale(3, BigDecimal.RoundingMode.HALF_UP).toDouble,
        tsSpeed = speedEnum,
        customTitle = getParamFromAnyContent(data, "customTitle"),
        customLogoUrl = getParamFromAnyContent(data, "customLogoUrl"),
        theme = getParamFromAnyContent(data, "theme"),
        redirectMethod = getParamFromAnyContent(data, "redirectMethod"))
    } catch {
      case e: Exception => throw new Exception("post data is invalid")
    }

  def updateLogistics = authenticated {
    (merchant, req) =>
      val logisticsData = getLogisticsFromData(req.body, merchant.id)
      logisticsData match {
        case Some(logistics) =>
          BackendAccess.backend ? OrderLogisticsUpdated(Seq(logistics)) map {
            case OrderLogisticsUpdatedResult(errorCode) if errorCode == ErrorCode.Ok =>
              Ok(Json.obj("Result" -> "Succeeded"))

            case OrderLogisticsUpdatedResult(errorCode) if errorCode != ErrorCode.Ok =>
              InternalServerError(Json.obj("error" -> errorCode.name))

            case x =>
              println("------bad request")
              BadRequest(Json.obj("error" -> x.toString))
          }
        case _ =>
          Future(BadRequest(Json.obj("error" -> "Invalid logistics code")))
      }
  }

  private def getContactFromData(data: AnyContent, prefix: String) = {
    val name = getParamFromAnyContent(data, prefix + ".name").getOrElse("")
    if (name.isEmpty) None
    else Some(Contact(
      name = name,
      address = getParamFromAnyContent(data, prefix + ".address"),
      address2 = getParamFromAnyContent(data, prefix + ".address2"),
      city = getParamFromAnyContent(data, prefix + ".city"),
      state = getParamFromAnyContent(data, prefix + ".state"),
      zip = getParamFromAnyContent(data, prefix + ".zip"),
      country = getParamFromAnyContent(data, prefix + ".country"),
      phone = getParamFromAnyContent(data, prefix + ".phone"),
      email = getParamFromAnyContent(data, prefix + ".email")))
  }

  private def getSpeed(speedTxt: Option[String]): (TransactionSpeed, Int) = {
    import TransactionSpeed._
    speedTxt match {
      case Some(txt) if txt.nonEmpty =>
        TransactionSpeed.valueOf(txt) match {
          case Some(High) => (High, 0)
          case Some(Medium) => (Medium, 1)
          case Some(Low) => (Low, 3)
          case _ => (High, 0)
        }
      case _ => (High, 0)
    }
  }

  private def getLogisticsFromData(data: AnyContent, merchantId: Long): Option[OrderUpdate] = {
    val orderId = getParamFromAnyContent(data, "orderId").getOrElse("")
    val logisticsCode = getParamFromAnyContent(data, "logisticsCode").getOrElse("")
    if (orderId.isEmpty || logisticsCode.isEmpty) None
    else Some(OrderUpdate(orderId, merchantId, Some(logisticsCode), getParamFromAnyContent(data, "logisticsOrgCode"), getParamFromAnyContent(data, "logisticsOrgName")))
  }

  // The following classes are used to parse POST data
  case class PlainContact(
    name: String,
    address: Option[String] = None,
    address2: Option[String] = None,
    city: Option[String] = None,
    state: Option[String] = None,
    zip: Option[String] = None,
    country: Option[String] = None,
    phone: Option[String] = None,
    email: Option[String] = None)

  case class PlainQueryInvoices(
    status: Option[String],
    begin: String,
    end: String,
    currency: Option[String],
    limit: Option[Int],
    skip: Option[Int])

  case class PlainQueryInvoiceByOrder(
    orderId: Option[String])

  object FormMapping {

    val currencyConstraint: Constraint[String] = Constraint("constraints.currencyCheck")({
      currency =>
        val errors = Currency.valueOf(currency) match {
          case None => Seq(ValidationError("invalid currency symbol: " + currency))
          case Some(_) => Nil
        }
        if (errors.isEmpty) Valid else Invalid(errors)
    })

    val priceConstraint: Constraint[BigDecimal] = Constraint("constraints.priceCheck")({
      price =>
        val errors = if (price <= 0) Seq(ValidationError("price must be greater than 0")) else Nil
        if (errors.isEmpty) Valid else Invalid(errors)
    })

    val notificationURLConstraint: Constraint[Option[String]] = Constraint("constraints.notificationURLCheck")({
      url =>
        val errors = url match {
          // case Some(url) if !url.startsWith("https://") => Seq(ValidationError("notificaitonURL must start with 'https://'"))
          case _ => Nil
        }
        if (errors.isEmpty) Valid else Invalid(errors)
    })

    val queryInvoiceByOrderForm = Form(
      mapping("orderId" -> optional(text))(PlainQueryInvoiceByOrder.apply)(PlainQueryInvoiceByOrder.unapply)
    )

    val queryInvoicesForm = Form(
      mapping(
        "status" -> optional(text(maxLength = 100)),
        "begin" -> text(maxLength = 100),
        "end" -> text(maxLength = 100),
        "currency" -> optional(text(maxLength = 10)),
        "limit" -> optional(number),
        "skip" -> optional(number))(PlainQueryInvoices.apply)(PlainQueryInvoices.unapply))
  }
}
