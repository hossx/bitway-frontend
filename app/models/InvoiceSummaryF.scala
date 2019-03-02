package models

import com.coinport.bitway.data.{ NotificationData, InvoiceStatus, Currency }

/**
 * Created by chenxi on 12/8/14.
 */
case class InvoiceSummaryF(
  id: String,
  price: Double,
  currency: String,
  btcPrice: String,
  status: String,
  invoiceTime: Long,
  expirationTime: Long,
  posData: Option[String] = None,
  currentTime: Option[Long] = None,
  orderId: Option[String] = None,
  url: Option[String] = None,
  redirectUrl: Option[String] = None,
  invalidTime: Long,
  updateTime: Long,
  paymentAddress: String,
  displayPrice: Double,
  displayCurrency: String,
  btcShouldPay: String)
