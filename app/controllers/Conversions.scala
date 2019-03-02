package controllers

import play.api.libs.json.Writes
import com.coinport.bitway.data.{ Invoice, InvoiceSummary }
import play.api.libs.json._
import java.util.Calendar
import models.InvoiceSummaryF

/**
 * Created by chenxi on 7/23/14.
 */
object Conversions extends BackendAccess {
  def invoice2summary(invoice: Invoice) =
    InvoiceSummaryF(
      id = invoice.id,
      price = invoice.data.price,
      currency = invoice.data.currency.toString.toLowerCase,
      btcPrice = priceFormatter(invoice.btcPrice),
      status = invoice.status.toString.toUpperCase,
      invoiceTime = invoice.invoiceTime,
      expirationTime = invoice.expirationTime,
      posData = invoice.data.posData,
      currentTime = invoice.currentTime,
      orderId = invoice.data.orderId,
      url = Some(BackendAccess.httpPrefix + routes.GetInvoiceAction.invoiceView(invoice.id, "").toString),
      redirectUrl = invoice.data.redirectURL,
      invalidTime = invoice.invalidTime,
      updateTime = invoice.updateTime,
      paymentAddress = invoice.paymentAddress,
      displayPrice = invoice.data.displayPrice,
      displayCurrency = invoice.data.displayCurrency.toString.toLowerCase,
      btcShouldPay = priceFormatter(invoice.btcPrice - invoice.btcPaid.getOrElse(0.0))
    )

  def priceFormatter(price: Double, minAmount: Option[Double] = Some(0.0001)) = {
    val formatter = new java.text.DecimalFormat("#.####")
    formatter.format(Math.max(price, minAmount.getOrElse(0.0)))
  }

  def string2Timestamp(str: String, isBegin: Boolean = true, zoneStr: String = ""): Long = {
    val strList = str.split("-").map(_.toInt)
    val timeZone = java.util.TimeZone.getTimeZone(if (zoneStr.isEmpty) "Asia/Shanghai" else zoneStr)
    val calendar = Calendar.getInstance(timeZone)
    calendar.clear()
    if (isBegin)
      calendar.set(strList(0), strList(1) - 1, strList(2), 0, 0, 0)
    else
      calendar.set(strList(0), strList(1) - 1, strList(2), 23, 59, 59)
    calendar.getTimeInMillis
  }

  def timeStampToString(timestamp: Long, zoneStr: String = ""): String = {
    val timeZone = java.util.TimeZone.getTimeZone(if (zoneStr.isEmpty) "Asia/Shanghai" else zoneStr)
    val format = new java.text.SimpleDateFormat("YYYY-MM-dd HH:mm:ss")
    format.setTimeZone(timeZone)
    format.format(new java.util.Date(timestamp))
  }

  def timeStampToDateTime(timestamp: Long): Array[String] = {
    val timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
    val format = new java.text.SimpleDateFormat("MM/dd/YYYY-hh:mm:ss aaa")
    format.setTimeZone(timeZone)
    format.format(new java.util.Date(timestamp)).split("-")
  }
}
