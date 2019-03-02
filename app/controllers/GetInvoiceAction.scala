package controllers

import Conversions._
import akka.pattern.ask
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.{ Calendar, Date }
import play.api._
import play.api.Play.current
import play.api.data.Forms._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json._
import play.api.mvc._
import play.api.mvc.WebSocket.FrameFormatter
import scala.concurrent.Future
import scala.util.Random
import play.api.i18n.Lang

import com.coinport.bitway.data._
import com.github.tototoshi.play2.json4s.native.Json4s
import org.json4s.native.Serialization
import org.json4s.{ Extraction, NoTypeHints }
import Util._
import controllers.CreateInvoiceAction.FormMapping

object GetInvoiceAction extends Controller with Authenticated with BackendAccess with Json4s {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  implicit val formats = Serialization.formats(NoTypeHints)
  val standardThemes = List("default", "black", "white", "grey", "amber", "blue",
    "bluegrey", "brown", "cyan", "deeporange", "deeppurple", "green",
    "indigo", "lightblue", "lightgreen", "lime", "orange", "pink",
    "purple", "red", "teal", "yellow")

  val companyThemes = List("dhpay", "wish", "ctrip", "masapay",
    "iapppay", "halo", "ipaynow", "geoswift", "globebill", "wintopay",
    "meituan", "oceanpay", "shouxinyi", "gearbest", "yinyingtong",
    "aukey")

  val invoiceThemes = standardThemes ++ companyThemes

  val random = Random

  protected def dateFormatter(millSeconds: Long) = {
    val fmt = new SimpleDateFormat("yyyy-M-d")
    fmt.format(millSeconds)
  }

  def debugInvoice(id: String) = Action {
    implicit request =>
      val status = request.queryString.get("status").map(_(0)).get
      InvoiceStatus.valueOf(status) match {
        case Some(st) =>
          BackendAccess.routers.paymentActor ! InvoiceDebugRequest(id, st)
          Accepted
        case _ => NotFound(Json.obj("error" -> s"invalid status: ${status}"))
      }
  }

  def invoiceView(id: String, view: String = "") = Action.async {
    implicit request =>
      BackendAccess.routers.invoiceAccessActor ? QueryInvoiceById(id) map {
        case QueryInvoiceResult(invoices, count) if invoices.nonEmpty =>
          val format: String = request.queryString.get("f").flatMap(_.headOption).getOrElse("html")
          val customLogoUrl = request.queryString.get("customLogoUrl").flatMap(_.headOption)
          val customTitle = request.queryString.get("customTitle").flatMap(_.headOption)
          val theme = getTheme(request.queryString.get("theme").flatMap(_.headOption), invoices(0).data.theme)

          if (format == "json") {
            Ok(Extraction.decompose(invoice2summary(invoices(0))))
          } else {
            val lang = request.queryString.get("lang").flatMap(_.headOption)
            val paymentUri = URLEncoder.encode(s"${BackendAccess.httpPrefix}/api/${BackendAccess.apiVersion}/i/${invoices(0).id}", "UTF8")
            val res = views.html.invoice(invoices(0), dateFormatter, Conversions.priceFormatter(_), view == "iframe",
              BackendAccess.apiVersion, BackendAccess.httpPrefix, BackendAccess.wsPrefix, paymentUri, theme, customLogoUrl, customTitle)
            if (lang.isDefined) Ok(res).withLang(Lang(lang.get)) else Ok(res)
          }

        case _ => NotFound(Json.obj("error" -> s"invoice with id: ${id} not found"))
      }
  }

  def invoiceData(id: String) = authenticated { (merchant, req) =>
    implicit val request = req
    BackendAccess.routers.invoiceAccessActor ? QueryInvoiceById(id) map {
      case QueryInvoiceResult(invoices, count) if invoices.nonEmpty =>
        Ok(Extraction.decompose(invoice2summary(invoices(0))))

      case _ => NotFound(Json.obj("error" -> s"invoice with id: ${id} not found"))
    }
  }

  private def getTheme(urlTheme: Option[String], dataTheme: Option[String]): String = {
    urlTheme.map(_.toLowerCase) match {
      case Some("random") => standardThemes(random.nextInt(standardThemes.length))
      case Some(theme) => theme
      case None => dataTheme.map(_.toLowerCase) match {
        case Some(theme) if invoiceThemes.contains(theme) => theme
        case _ => "default"
      }
    }
  }

  def downloadBillReport(currency: String, invoiceOnly: Boolean = false) = Action.async {
    implicit request =>
      val merchantId = request.session.get("id").map(_.toLong).get
      val begin = string2Timestamp(request.queryString.get("begin").map(_(0)).get)
      var end = string2Timestamp(request.queryString.get("end").map(_(0)).get, false)
      val oneMonth: Long = 1000L * 3600 * 24 * 32
      if (end - begin > oneMonth || end < begin) {
        end = begin + oneMonth
      }
      val beginDate = timeStampToString(begin).substring(0, 10)
      val endDate = timeStampToString(end).substring(0, 10)
      val cur = Some(Currency.valueOf(currency).getOrElse(Currency.Cny))
      val (billType, filePrefix) = invoiceOnly match {
        case true => (Some(BillType.Invoice), "invoices_")
        case _ => (None, "bills_")
      }
      BackendAccess.backend ? QueryBill(merchantId, 0, 1000, cur, Some(begin), Some(end), billType) map {
        case QueryBillResult(items, count) =>
          try {
            val content: Enumerator[Array[Byte]] = Enumerator(billsToReport(items, cur.get, "", invoiceOnly).getBytes("UTF-8"))
            Result(
              header = ResponseHeader(200),
              body = content).withHeaders("Content-type" -> "application/force-download", "Content-Disposition" -> s"""attachment;filename=${filePrefix}${merchantId}_${cur.get.name}_${beginDate}-${endDate}.csv""")
          } catch {
            case e: Exception =>
              NotFound(Json.obj("error" -> s"something wrong with backend"))
          }
        case _ => NotFound(Json.obj("error" -> "something wrong with backend"))
      }
  }

  def downloadInvoices(currency: String) = downloadBillReport(currency, true)

  def billQuery(currency: String) = Action.async {
    implicit request =>
      val begin = string2Timestamp(request.queryString.get("begin").map(_(0)).get)
      val end = string2Timestamp(request.queryString.get("end").map(_(0)).get, false)

      val cur = Some(Currency.valueOf(currency).getOrElse(Currency.Btc))

      val limit = request.queryString.get("limit").map(_(0)).getOrElse("10")
      val skip = request.queryString.get("skip").map(_(0)).getOrElse("0")
      val merchantId = request.session.get("id").map(_.toLong).get

      BackendAccess.backend ? QueryBill(merchantId, skip.toInt, limit.toInt, cur, Some(begin), Some(end)) map {
        case QueryBillResult(bills, count) =>
          val jsArr = new JsArray(bills.map { i =>
            //val date = new Date(i.timestamp)
            val date = i.timestamp
            val flow = i.bType match {
              case BillType.Invoice => "+"
              case _ => "-"
            }

            val invoiceId = i.invoiceId.getOrElse("")
            val json = Json.obj(
              "bType" -> i.bType.value,
              "date" -> date,
              "invoiceId" -> invoiceId,
              "amount" -> i.amount,
              "flow" -> flow)
            if (i.invoice.isDefined) {
              val invoice: Invoice = i.invoice.get
              val buyer = invoice.data.buyer.map(_.name).getOrElse("")
              val itemDesc = invoice.data.itemDesc.getOrElse("")
              val rate = invoice.rate.getOrElse(0.0)
              val btcPrice = invoice.btcPrice
              val btcPaid = invoice.btcPaid

              json ++ Json.obj(
                "rate" -> rate,
                "merchant" -> invoice.merchantName,
                "buyer" -> buyer,
                "itemDesc" -> itemDesc,
                "btcPaid" -> btcPaid,
                "btcPrice" -> btcPrice)
            } else {
              json
            }
          })
          Ok(Json.obj("count" -> count, "items" -> jsArr))

        case _ => NotFound(Json.obj("error" -> "something wrong with backend"))
      }
  }

  def invoiceDetailData(invoiceId: String) = Action.async {
    implicit request =>
      Future(Ok(Json.obj()))
  }

  def invoiceQuery2(currency: String) = Action.async {
    implicit request =>
      val begin = string2Timestamp(request.queryString.get("begin").map(_(0)).get)
      val end = string2Timestamp(request.queryString.get("end").map(_(0)).get, false)

      val status = request.queryString.get("status").map(_(0)).get
      val statusList =
        if (status.isEmpty) None
        else Some(status.split(";").map(x => InvoiceStatus.valueOf(x.toLowerCase.capitalize)).filter(_.isDefined).map(_.get).toSeq)

      val cur = Some(Currency.valueOf(currency).getOrElse(Currency.Btc))

      val limit = request.queryString.get("limit").map(_(0)).getOrElse("100")
      val skip = request.queryString.get("skip").map(_(0)).getOrElse("0")
      val orderId = request.queryString.get("orderId").map(_(0))
      val merchantId = request.session.get("id").map(_.toLong)

      BackendAccess.backend ? QueryInvoice(merchantId, skip.toInt, limit.toInt, statusList, Some(begin), Some(end), cur, orderId) map {
        case QueryInvoiceResult(invoices, count) =>
          val jsArr = new JsArray(invoices.map { i =>
            Json.obj("status" -> i.status.name, "date" -> timeStampToString(i.invoiceTime), "id" -> i.id, "price" -> i.data.price)
          })
          Ok(Json.obj("count" -> count, "items" -> jsArr))

        case _ => NotFound(Json.obj("error" -> "something wrong with backend"))
      }
  }

  def invoiceQueryByOrder = authenticated { (merchant, req) =>
    implicit val request = req

    FormMapping.queryInvoiceByOrderForm.bindFromRequest.fold(
      formWithErrors => Future {
        BadRequest(Json.obj("errors" -> formWithErrors.errors.flatMap(_.messages)))
      },

      qiof => {
        val orderId = qiof.orderId

        BackendAccess.routers.invoiceAccessActor ? QueryInvoice(Some(merchant.id), 0, 1, None, None, None, None, orderId) map {
          case QueryInvoiceResult(invoices, count) =>
            if (invoices.isEmpty) {
              NotFound(Json.obj("error" -> s"The invoice with orderId: $orderId does not exsit"))
            } else Ok(Extraction.decompose(invoice2summary(invoices(0))))
          case _ => NotFound(Json.obj("error" -> "something wrong with backend"))
        }
      })
  }

  def invoiceQuery = authenticated { (merchant, req) =>
    implicit val request = req

    FormMapping.queryInvoicesForm.bindFromRequest.fold(
      formWithErrors => Future {
        BadRequest(Json.obj("errors" -> formWithErrors.errors.flatMap(_.messages)))
      },

      pqi => {
        val statusList = pqi.status match {
          case Some(status) => status.split(";").map(x => InvoiceStatus.valueOf(x.toLowerCase.capitalize)).filter(_.isDefined).map(_.get).toSeq
          case None => Seq(InvoiceStatus.Paid, InvoiceStatus.Confirmed, InvoiceStatus.Complete)
        }

        val begin = string2Timestamp(pqi.begin)
        val end = string2Timestamp(pqi.end, false)

        val cur = pqi.currency match {
          case Some(c) => Some(Currency.valueOf(c).getOrElse(Currency.Btc))
          case None => None
        }

        val limit = pqi.limit.getOrElse(10)
        val skip = pqi.skip.getOrElse(0)

        BackendAccess.routers.invoiceAccessActor ? QueryInvoice(Some(merchant.id), skip, limit, Some(statusList), Some(begin), Some(end), cur) map {
          case QueryInvoiceResult(invoices, count) =>
            Ok(invoicesToJson(invoices, count))
          case _ => NotFound(Json.obj("error" -> "something wrong with backend"))
        }
      })
  }

  private def invoicesToJson(invoices: Seq[Invoice], count: Int): JsObject = {
    val jsArr = new JsArray(invoices.map { i =>
      Json.obj("status" -> i.status.name, "date" -> timeStampToString(i.invoiceTime), "id" -> i.id, "price" -> i.data.price)
    })
    Json.obj("count" -> count, "items" -> jsArr)
  }

  private def billsToReport(items: Seq[BillItem], currency: Currency, zoneStr: String = "", invoiceOnly: Boolean): String = {
    if (invoiceOnly) {
      "Date,Time,Time Zone,Name,Type,Status,Currency,Gross,Fee,Net,Reserve," +
        "From Email Address,To Email Address,Transaction ID,Counterparty Status,Item Title,Item ID,Invoice Number," +
        "Address Line 1,Address Line 2/District/Neighborhood,Town/City,State/Province/Region/County/Territory/Prefecture/Republic," +
        "Zip/Postal Code,Country,Contact Phone Number\n" +
        items.filter(it => it.bType == BillType.Invoice && it.invoice.isDefined).map {
          item =>
            val dateTime = timeStampToDateTime(item.timestamp)
            val i = item.invoice.get
            val buyer = i.data.buyer.getOrElse(Contact(""))
            val merchant = i.data.merchant.getOrElse(Contact(""))
            s"${dateTime(0)},${dateTime(1)},GMT+08:00,${buyer.name},,Completed,${currency.name.toUpperCase()},${item.amount},${i.data.fee},${item.amount - i.data.fee},," +
              s"${buyer.email.getOrElse("")},${merchant.email.getOrElse("")},${i.data.orderId.getOrElse("")},Unregistered,${i.data.itemDesc.getOrElse("")},${i.data.itemCode.getOrElse("")},${i.id}," +
              s"${buyer.address.getOrElse("")},${buyer.address2.getOrElse("")},${buyer.city.getOrElse("")},${buyer.state.getOrElse("")}," +
              s"${buyer.zip.getOrElse("")},${buyer.country.getOrElse("")},${buyer.phone.getOrElse("")}"
        }.toList.mkString("\n")
    } else {
      s"时间,订单摘要,付款人,应收BTC,已付BTC,汇率,类别,金额(${currency.name.toUpperCase()})\n" +
        items.map {
          i =>
            var buyer = ""
            var itemDesc = ""
            var rate = ""
            var btcPrice = ""
            var btcPaid = ""
            if (i.invoice.isDefined) {
              val invoice: Invoice = i.invoice.get
              buyer = invoice.data.buyer.map(_.name).getOrElse("")
              itemDesc = invoice.data.itemDesc.getOrElse("")
              rate = String.valueOf(invoice.rate.getOrElse(0.0))
              btcPrice = String.valueOf(invoice.btcPrice)
              btcPaid = String.valueOf(invoice.btcPaid.getOrElse(0.0))
            }
            val dateStr = timeStampToString(i.timestamp)
            s"$dateStr,$itemDesc,$buyer,$btcPrice,$btcPaid,$rate,${renderType(i.bType)}${i.amount}"
        }.toList.mkString("\n")
    }
  }

  private def renderType(billType: BillType): String = {
    import BillType._
    billType match {
      case Invoice => "收入,+"
      case Fee => "费用,-"
      case Refund => "退款,-"
      case Settle => "提现,-"
    }
  }
}
