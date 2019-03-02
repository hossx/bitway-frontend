/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package controllers

import java.io.FileInputStream
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import scala.Array.canBuildFrom
import scala.concurrent.Future
import org.bitcoin.protocols.payments.Protos
import org.bitcoin.protocols.payments.Protos.Payment
import org.bitcoin.protocols.payments.Protos.PaymentRequest
import com.coinport.bitway.data.PaymentReceived
import com.coinport.bitway.data.QueryInvoiceById
import com.coinport.bitway.data.QueryInvoiceResult
import com.coinport.bitway.data.SendRawTransaction
import com.google.bitcoin.core.Address
import com.google.bitcoin.params.MainNetParams
import com.google.bitcoin.script.ScriptBuilder
import com.google.protobuf.ByteString
import akka.actor.actorRef2Scala
import akka.pattern.ask
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.Action
import play.api.mvc.Controller
import com.google.bitcoin.core.Transaction

trait PaymentAction extends Controller with BackendAccess {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  val pwd = scala.io.Source.fromFile("/var/bitway/private/cert_pwd").mkString.trim

  def paymentRequest(id: String) = Action.async { implicit request =>
    BackendAccess.routers.invoiceAccessActor ? QueryInvoiceById(id) map {
      case QueryInvoiceResult(invoices, count) if invoices.nonEmpty =>
        val invoice = invoices(0)

        val output = Protos.Output.newBuilder().setAmount((invoice.btcPrice * 10000000000L + 0.5).toLong / 100).setScript(ByteString.copyFrom(
          ScriptBuilder.createOutputScript(new Address(MainNetParams.get(),
            invoice.paymentAddress)).getProgram()))

        val paymentDetails = Protos.PaymentDetails.newBuilder()
          .setTime(invoice.invoiceTime).addOutputs(output)
          .setExpires(invoice.expirationTime)
          .setPaymentUrl(s"${BackendAccess.httpPrefix}/api/${BackendAccess.apiVersion}/p/${id}")
          .build()

        val paymentReq = Protos.PaymentRequest.newBuilder.setSerializedPaymentDetails(paymentDetails.toByteString)

        val keyStore = readCertFromFile("/var/bitway/private/coinport-cert")
        val certChain = keyStore.getCertificateChain("bitway")
        val privateKey: PrivateKey = keyStore.getKey("bitway", pwd.toCharArray()).asInstanceOf[PrivateKey]

        val certs = Protos.X509Certificates.newBuilder()
        certChain map {
          case m =>
            certs.addCertificate(ByteString.copyFrom(m.getEncoded()))
        }

        val signedPaymentReq = signPaymentRequest(paymentReq, certs.build, privateKey)

        Ok(signedPaymentReq.toByteArray).withHeaders(
          CONTENT_TYPE -> "application/bitcoin-paymentrequest",
          CONTENT_TRANSFER_ENCODING -> "binary")
      case _ => NotFound(Json.obj("error" -> s"invoice with id: ${id} not found"))
    }
  }

  def payment(id: String) = Action.async { implicit request =>

    val payment = Payment.parseFrom(request.body.asRaw.get.asBytes().get)

    // send tx
    payment.getTransactionsList().toArray() map {
      case tx =>
        BackendAccess.routers.blockchainActor ? SendRawTransaction(
          tx.asInstanceOf[ByteString].toByteArray().map("%02X" format _).mkString) map { case r => println(r) }
    }

    // update invoice(add payment into invoice)
    BackendAccess.routers.invoiceAccessActor ! PaymentReceived(id, ByteBuffer.wrap(payment.toByteArray))

    val paymentAck = Protos.PaymentACK.newBuilder().setPayment(payment).build()
    Future(Ok(paymentAck.toByteArray).withHeaders(
      CONTENT_TYPE -> "application/bitcoin-paymentack",
      CONTENT_TRANSFER_ENCODING -> "binary"))
  }

  private def signPaymentRequest(paymentReq: PaymentRequest.Builder, certs: Protos.X509Certificates, privateKey: PrivateKey): PaymentRequest = {

    var paymentReqToSign = paymentReq.setPkiType("x509+sha1").setPkiData(certs.toByteString).setSignature(ByteString.EMPTY)

    var algorithm: String = "SHA1withRSA"
    if (privateKey.getAlgorithm().equalsIgnoreCase("RSA"))
      algorithm = "SHA1withRSA"

    val signature: Signature = Signature.getInstance(algorithm);
    signature.initSign(privateKey);
    signature.update(paymentReqToSign.build().toByteArray());
    paymentReqToSign = paymentReqToSign.setSignature(ByteString.copyFrom(signature.sign()));
    paymentReqToSign.build()
  }

  private def readCertFromFile(path: String): KeyStore = {
    val keystore = KeyStore.getInstance("JKS")
    keystore.load(new FileInputStream(path), pwd.toCharArray)
    keystore
  }
}
