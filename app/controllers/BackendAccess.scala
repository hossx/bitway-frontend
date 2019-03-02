package controllers

import play.api._
import play.api.mvc._
import com.typesafe.config.ConfigFactory
import akka.actor._
import akka.cluster.Cluster
import com.coinport.bitway._
import com.coinport.bitway.Bitway
import akka.event.LoggingReceive
import play.api.Logger
import akka.util.Timeout
import scala.concurrent.duration._

import com.coinport.bitway.data.Exchange._

object BackendAccess {
  val defaultAkkaConfig = "akka.conf"
  val akkaConfigProp = System.getProperty("akka.config")
  val akkaConfigResource = if (akkaConfigProp != null) akkaConfigProp else defaultAkkaConfig

  Logger.info("=" * 20 + "  Akka config  " + "=" * 20)
  Logger.info("  conf/" + akkaConfigResource)
  Logger.info("=" * 55)

  val config = ConfigFactory.load(akkaConfigResource)
  val domainName = config.getString("domain-name")
  val apiVersion = config.getString("api-version")
  implicit val backendSystem = ActorSystem("bitway", config)
  implicit val cluster = Cluster(backendSystem)

  val exchanges = List(OkcoinCn, OkcoinEn, Btcchina, Huobi, Bitstamp)
  val routers = new LocalRouters(exchanges)
  val backend = backendSystem.actorOf(Props(new Bitway(routers)), name = "backend")
  val delegate = backendSystem.actorOf(Props[WSNotificationDelegateActor], common.Role.wsdelegate.toString)
  Logger.info(s"deployed WSNotificationDelegateActor at: ${delegate.path}")

  val httpPrefix = if (BackendAccess.domainName.isEmpty) "http://localhost:9000" else s"https://${BackendAccess.domainName}"
  val wsPrefix = if (BackendAccess.domainName.isEmpty) "ws://localhost:9000" else s"wss://${BackendAccess.domainName}"
}

trait BackendAccess {
  implicit val timeout = Timeout(2 seconds)
}
