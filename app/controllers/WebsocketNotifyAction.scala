package controllers

import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.mvc.WebSocket.FrameFormatter
import com.coinport.bitway.data._
import akka.pattern.ask
import akka.actor._
import services.{ AccountService, NotificationService }
import scala.concurrent.Future
import com.coinport.bitway.LocalRouters
import com.coinport.bitway.data._
import scala.collection.mutable.{ Map, Set }

case class InvoiceWsEvent(status: String, updateTime: Long, btcShouldPay: Option[String] = None)

case class InvoicePayChanged(status: InvoiceStatus, updateTime: Long, btcShouldPay: Double)

trait WebsocketNotifyAction {
  self: Controller with BackendAccess =>

  import play.api.Play.current

  implicit val outEventFormat = Json.format[InvoiceWsEvent]
  implicit val outEventFrameFormatter = FrameFormatter.jsonFrame[InvoiceWsEvent]

  def invoicews(invoiceId: String) = WebSocket.acceptWithActor[String, InvoiceWsEvent] { request =>
    browser => Props(new InvoiceWebsocketActor(BackendAccess.delegate, browser, invoiceId))
  }
}

class InvoiceWebsocketActor(delegate: ActorRef, browser: ActorRef, invoiceId: String)
    extends Actor with ActorLogging {

  import scala.concurrent.duration._
  import context.dispatcher

  //Schedules to send the "foo"-message to the testActor after 50ms
  context.system.scheduler.scheduleOnce(15 minutes, self, PoisonPill)

  override def preStart() = {
    log.info("websocket started: " + invoiceId)
    delegate ! WatchInvoiceStatus(invoiceId)
  }

  override def postStop() = {
    log.info("websocket stopped : " + invoiceId)
  }

  def receive = {
    case InvoiceStatusChanged(status, time) => browser ! InvoiceWsEvent(status.name, time)
    case p: InvoicePayChanged =>
      val formatter = new java.text.DecimalFormat("#.####")
      browser ! InvoiceWsEvent(p.status.name, p.updateTime, Some(formatter.format(p.btcShouldPay)))
    case _ =>
  }
}

class WSNotificationDelegateActor extends Actor with ActorLogging {
  val interestMap = Map.empty[String, ActorRef]
  val actorMap = Map.empty[ActorRef, String]

  def receive = {
    case WatchInvoiceStatus(invoiceId) =>
      context.watch(sender)
      interestMap += invoiceId -> sender
      actorMap += sender -> invoiceId
      log.debug(s"${sender.path} interested in ${invoiceId}")

    case Terminated(watched) =>
      context.unwatch(watched)
      actorMap.get(watched) match {
        case Some(invoiceId) => interestMap -= invoiceId
        case None =>
      }
      actorMap -= watched

      log.debug(s"${watched.path} died")

    case NotifyMerchants(notifications) =>
      notifications foreach { n =>
        println("post notify: " + n)
        interestMap.get(n.invoice.id) foreach { actor =>
          if (n.invoice.status == InvoiceStatus.New)
            actor ! InvoicePayChanged(n.invoice.status, n.invoice.updateTime, n.invoice.btcPrice - n.invoice.btcPaid.getOrElse(0.0))
          else
            actor ! InvoiceStatusChanged(n.invoice.status, n.invoice.updateTime)
        }

        val invoice = n.invoice
        // POST notifications
        invoice.data.notificationURL match {
          case Some(url) =>
            val merchantId = invoice.merchantId
            val secret = AccountService.getSecretKey(merchantId)

            val response = NotificationService.post(invoice, url, secret)
            println("response from " + url + ":\n" + response)
          case None =>
        }
      }

    case "ping" => sender ! "pong"

    case _ =>
  }
}
