package controllers

import com.coinport.bitway.util.MHash
import play.api._
import play.api.mvc._
import play.api.libs.json._
import akka.pattern.ask
import services.AccountService
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.coinport.bitway.data._
import ControllerHelper._
import java.util.Date
import PageUtil.Const._
import models._
import Util._

trait AccountAction {
  self: Controller with AccessLogging with BackendAccess =>
  val logger = Logger(this.getClass)

  def register = Action.async {
    implicit request =>
      val data = request.body.asJson.get
      val password = data.\("password").as[String]
      val email = data.\("email").as[String]

      val id = MHash.sha256ThenMurmur3(email)
      val user = User(id, None, Some(email), password)
      val merchant = Merchant(id = 0L, tokenAndKeyPairs = Map.empty[String, String],
        email = Some(email))
      validateParamsAndThen(
        new PasswordFormetValidator(password),
        new EmailFormatValidator(email)
      ) {
          BackendAccess.routers.accountActor ? DoRegister(merchant, password)
        } map {
          case RegisterSucceeded(_merchant) =>
            Ok(Json.obj("rv" -> true, "code" -> "", "msg" -> ""))
          case RegisterFailed(error) =>
            Ok(Json.obj("rv" -> false, "code" -> error.getValue(), "msg" -> error.name))
          case ApiResult(_, code, message, _) =>
            Ok(Json.obj("rv" -> false, "code" -> code, "msg" -> message))
          case e =>
            Ok(Json.obj("rv" -> false, "code" -> 0, "msg" -> e.toString))
        }
  }

  def login = Action.async {
    implicit request =>
      val data = request.body.asFormUrlEncoded.getOrElse(Map.empty[String, Seq[String]])
      val password = getParam(data, "password").getOrElse("")
      val email = getParam(data, "email").getOrElse("")
      BackendAccess.routers.accountActor ? DoLogin(email, password) map {
        case LoginSucceeded(merchantId, _email) => Redirect(routes.Application.profilePage(merchantId)).withSession("id" -> merchantId.toString, "email" -> _email)
        case LoginFailed(error) => Redirect(routes.Application.loginPage()).flashing("error" -> s"ERROR${error.value}")
        case ApiResult(_, code, message, _) => Redirect(routes.Application.loginPage()).flashing("error" -> s"E${code}")
        case e => Redirect(routes.Application.loginPage()).flashing("error" -> s"${e.toString}")
      }
  }

  def signupPage(lang: String = "zh-CN") = Action {
    implicit request =>
      Ok(views.html.register()(langFromParam(lang), request.flash, request.session))
  }

  def validateRegister(token: String) = Action.async {
    BackendAccess.routers.accountActor ? DoValidateRegister(token) map {
      case ValidateRegisterSucceeded(_) =>
        Redirect(routes.Application.loginPage()).flashing("msg" -> "register.verify.ok")
      case e =>
        Redirect(routes.Application.prompt("register", "register.verify.failed")).flashing("error" -> s"${e.toString}")
    }
  }

  def loginPage(lang: String = "zh-CN") = Action {
    implicit request =>
      Ok(views.html.login()(langFromParam(lang), request.flash, request.session))
  }

  def profilePage(id: Long) = Action.async {
    implicit request =>
      BackendAccess.backend ? QueryMerchantById(id) map {
        case QueryMerchantResult(merchantOpt) if merchantOpt.isDefined =>
          val merchant = merchantOpt.get
          val profileMap = PageUtil.merchant2ProfileMap(merchant)
          Ok(views.html.accountProfile(profileMap)(request.session))
        case _ =>
          logger.warn(s"can not get merchant by id. id: $id")
          val profileMap = Map.empty[String, List[Map[String, String]]]
          Ok(views.html.accountProfile(profileMap)(request.session))
      }
  }

  def createApiToken = Action.async {
    implicit request =>
      val uid = request.session.get("id").getOrElse("-1").toLong
      //      println(s"add token uid: $uid")
      BackendAccess.backend ? CreateApiToken(uid) map {
        case _ => Redirect(routes.Application.profilePage(uid))
      }
  }

  def removeApiToken = Action.async {
    implicit request =>
      val uid = request.session.get("id").getOrElse("-1").toLong
      val data = request.body.asFormUrlEncoded.getOrElse(Map.empty[String, Seq[String]])
      val token = getParam(data, "apiToken").getOrElse("")
      BackendAccess.backend ? DestoryApiToken(token) map {
        case _ => Redirect(routes.Application.profilePage(uid))
      }
  }

  def updateSecret = Action.async {
    implicit request =>
      val uid = request.session.get("id").getOrElse("-1").toLong
      BackendAccess.backend ? UpdateSecretKey(uid) map {
        case _ => Redirect(routes.Application.profilePage(uid))
      }
  }

  def updateProfile = Action.async {
    implicit request =>
      val uid = request.session.get("id").getOrElse("-1").toLong
      val data = request.body.asFormUrlEncoded.getOrElse(Map.empty[String, Seq[String]])
      val merchantName = getParam(data, "name").getOrElse("")
      val merchantAddr = getParam(data, "address")
      val merchantEmail = getParam(data, "email")
      val merchantPhone = getParam(data, "phone")
      val contact = Contact(name = merchantName, address = merchantAddr, email = merchantEmail, phone = merchantPhone)

      val btcAddr = getParam(data, "btcAddr")

      //val bankCardsCount = getParam(data, "bankCardsCount").getOrElse("1").toInt
      val bankCardsCount = 1
      val bankAccounts = ((0 until bankCardsCount) map {
        i: Int =>
          val bankName = getParam(data, "bankName" + i).getOrElse("")
          val ownerName = getParam(data, "ownerName" + i).getOrElse("")
          val cardId = getParam(data, "cardId" + i).getOrElse("")
          val branch = getParam(data, "branch" + i)
          val country = getParam(data, "bankCountry" + i).map {
            case "0" => Country.China
            case "1" => Country.Usa
            case _ => null
          } flatMap (Option(_))

          val bankSwift = getParam(data, "bankSwift" + i)
          val bankCity = getParam(data, "bankCity" + i)
          val bankAddress = getParam(data, "bankAddr" + i)
          val bankPostal = getParam(data, "bankPostal" + i)
          val currency = getParam(data, "bankCurrency" + i) map {
            case "1" => Currency.Cny
            case "2" => Currency.Usd
            case _ => null
          } flatMap (Option(_))

          BankAccount(bankName = bankName,
            ownerName = ownerName,
            accountNumber = cardId,
            branchBankName = branch,
            country = country,
            achRoutingNumber = None,
            usTaxId = None,
            bankSwiftCode = bankSwift,
            bankTransitNumber = None,
            bankStreetAddress = bankAddress,
            bankCity = bankCity,
            bankPostalCode = bankPostal,
            bicBranchCode = None,
            perferredCurrency = currency)

        // if (cardId == null || cardId.trim.isEmpty) null
        // else BankAccount(bankName, ownerName, cardId, branch)
      }).toSet //.filter { _ != null }

      val merchant = Merchant(id = uid, name = Some(merchantName), tokenAndKeyPairs = Map.empty[String, String], contact = Some(contact), bankAccounts = Some(bankAccounts), btcWithdrawalAddress = btcAddr)
      BackendAccess.routers.accountActor ? DoUpdateMerchant(merchant) map {
        case UpdateMerchantSucceeded(_merchant) =>
          Redirect(routes.Application.profilePage(uid))
        // val profileMap = PageUtil.merchant2ProfileMap(_merchant)
        // Ok(views.html.accountProfile(profileMap)(request.session))
        case e =>
          logger.error(s"update merchant failed. error: ${e.toString}")
          Redirect(routes.Application.profilePage(uid))
      }
  }

  def requestPasswordResetPage = Action {
    implicit request =>
      Ok(views.html.requestpwdresetpage()(langFromParam("zh-CN"), request.session))
  }

  def requestPasswordReset = Action.async {
    implicit request =>
      val data = request.queryString
      val email = getParam(data, "email").getOrElse("")

      BackendAccess.routers.accountActor ? DoRequestPasswordReset(email, None) map {
        case RequestPasswordResetSucceeded(_) =>
          Redirect(routes.Application.prompt("pwdreset.request.succeeded"))
        case e =>
          Redirect(routes.Application.prompt("pwdreset.request.failed")).flashing("error" -> s"${e.toString}")
      }
  }

  def passwordResetPage(token: String) = Action {
    implicit request =>
      Ok(views.html.doPasswordReset(token)(request.session, langFromParam("zh-CN")))
  }

  def doPasswordReset = Action.async {
    implicit request =>
      val data = request.body.asJson.get
      val newPassword = data.\("password").as[String]
      val token = data.\("token").as[String]
      validateParamsAndThen(
        new PasswordFormetValidator(newPassword)
      ) {
          BackendAccess.routers.accountActor ? DoResetPassword(newPassword, token)
        } map {
          case ResetPasswordSucceeded(uid, email) =>
            Ok(Json.obj("rv" -> true, "code" -> "", "msg" -> ""))
          case ResetPasswordFailed(error) =>
            Ok(Json.obj("rv" -> false, "code" -> error.getValue(), "msg" -> error.name))
          case ApiResult(_, code, message, _) =>
            Ok(Json.obj("rv" -> false, "code" -> code, "msg" -> message))
          case e =>
            Ok(Json.obj("rv" -> false, "code" -> 0, "msg" -> e.toString))
        }
  }

  def prompt(titleKey: String, msgKey: String, lang: String = "zh-CN") = Action {
    implicit request =>
      Ok(views.html.prompt(titleKey, msgKey)(langFromParam(lang), request.flash, request.session))
  }

  def getBalance() = Action.async {
    implicit request =>
      val merchantId = Some(request.session.get("id").map(_.toLong).get).getOrElse(-1L)
      BackendAccess.routers.accountActor ? MerchantBalanceRequest(merchantId, Currency.list) map {
        case rv: MerchantBalanceResult =>
          val jarr = new JsArray(rv.balanceMap.map { balance =>
            val currency = balance._1
            Json.obj(
              "currency" -> currency.toString.toUpperCase(),
              "balance" -> priceFormatter2(balance._2),
              "freezing" -> priceFormatter2(rv.pendingMap.get.getOrElse(currency, 0.0))
            )
          }.toSeq)
          Ok(Json.obj("merchantId" -> merchantId, "balances" -> jarr))
      }
  }

  protected def priceFormatter2(price: Double) = {
    val formatter = new java.text.DecimalFormat("#.####")
    formatter.format(price)
  }

  protected def priceFormatter3(price: Double) = {
    val formatter = new java.text.DecimalFormat("#.########")
    formatter.format(price)
  }

  def refund = Action.async {
    implicit request =>
      val refundData = request.body.asJson.get \ "data"
      val amount = (refundData \ "amount").as[String]
      val emailvercode = (refundData \ "emailcode").as[String]
      val uuid = (refundData \ "emailuuid").as[String]
      val address = (refundData \ "address").as[String]
      val invoiceId = (refundData \ "invoiceId").as[String]

      val t = AccountTransfer(
        id = 0,
        merchantId = request.session.get("id").map(_.toLong).get,
        `type` = TransferType.Refund,
        currency = Currency.Cny,
        amount = 0,
        refundAmount = Some(amount.toDouble),
        address = Some(address),
        invoiceId = Some(invoiceId)
      )

      validateParamsAndThen(
        new CachedValueValidator(ErrorCode.InvalidEmailVerifyCode, true, uuid, emailvercode)
      ) {
          BackendAccess.routers.paymentActor ? MerchantRefund(transfer = t)
        } map {
          case rts: RequestTransferSucceeded =>
            Ok(Json.obj("rv" -> true))
          case rtf: RequestTransferFailed =>
            Ok(Json.obj("rv" -> false, "error" -> rtf.error.name))
          case ar: ApiResult =>
            Ok(Json.obj("rv" -> false, "error" -> ErrorCode.get(ar.code).get.name))
          case _ =>
            Ok(Json.obj())
        }
  }

  def queryRefund() = Action.async {
    implicit request =>
      val limit = request.queryString.get("limit").map(_(0)).getOrElse("10").toInt
      val skip = request.queryString.get("skip").map(_(0)).getOrElse("0").toInt

      BackendAccess.routers.paymentActor ? QueryTransfer(
        merchantId = Some(request.session.get("id").map(_.toLong).get),
        types = Seq(TransferType.Refund),
        cur = Cursor(limit = limit, skip = skip)) map {
          case rv: QueryTransferResult =>
            val jsArr = new JsArray(rv.transfers.map { t =>

              Json.obj(
                "id" -> t.id.toString,
                "date" -> t.created.get,
                "status" -> t.status.getValue,
                "refundAmount" -> priceFormatter2(t.refundAmount.get),
                "refundCurrency" -> t.refundCurrency.get.toString,
                "amount" -> priceFormatter3(t.externalAmount.get),
                "currency" -> t.currency.toString,
                "address" -> t.address.get)
            })
            Ok(Json.obj("count" -> rv.count, "items" -> jsArr))
        }
  }

  def refundById(refundId: String) = Action.async {
    implicit request =>
      val transferId = refundId.toLong
      BackendAccess.routers.paymentActor ? QueryTransfer(
        transferId = Some(transferId),
        types = Seq(TransferType.Refund),
        cur = Cursor(limit = 1, skip = 0)) map {
          case rv: QueryTransferResult =>
            val t = rv.transfers(0)
            val json = if (t != null) {
              Json.obj(
                "id" -> t.id.toString,
                "date" -> t.created.get,
                "status" -> t.status.getValue,
                "refundAmount" -> priceFormatter2(t.refundAmount.get),
                "refundCurrency" -> t.refundCurrency.get.toString,
                "amount" -> priceFormatter3(t.externalAmount.get),
                "currency" -> t.currency.toString,
                "address" -> t.address.get)
            } else {
              Json.obj()
            }
            Ok(json)
        }
  }

  def confirmRefund = Action.async {
    implicit request =>
      val confirmData = request.body.asJson.get
      val transferId = (confirmData \ "refundId").as[String]
      val cancel = (confirmData \ "cancel").as[Boolean]

      val t = AccountTransfer(
        id = transferId.toLong,
        merchantId = request.session.get("id").map(_.toLong).get,
        `type` = TransferType.Refund,
        currency = Currency.Btc,
        amount = 0,
        updated = Some(System.currentTimeMillis())
      )

      if (cancel) BackendAccess.routers.paymentActor ! DoCancelTransfer(t)
      else BackendAccess.routers.paymentActor ! AdminConfirmTransferSuccess(t)

      Future(Ok(Json.obj()))
  }

  def currencyWithdraw = Action.async {
    implicit request =>
      val withdrawData = request.body.asJson.get \ "data"
      val amount = (withdrawData \ "amount").as[String]
      val emailvercode = (withdrawData \ "emailcode").as[String]
      val cur = (withdrawData \ "currency").as[String]
      val uuid = (withdrawData \ "emailuuid").as[String]
      val address = (withdrawData \ "address").as[String]
      val currency = Currency.valueOf(cur).getOrElse(Currency.Btc)

      val t = AccountTransfer(
        id = 0,
        merchantId = request.session.get("id").map(_.toLong).get,
        `type` = TransferType.Settle,
        currency = currency,
        amount = 0,
        created = Some(System.currentTimeMillis()),
        externalAmount = Some(amount.toDouble),
        address = Some(address)
      )

      validateParamsAndThen(
        new CachedValueValidator(ErrorCode.InvalidEmailVerifyCode, true, uuid, emailvercode)
      ) {
          BackendAccess.routers.accountActor ? MerchantSettle(transfer = t)
        } map {
          case rts: RequestTransferSucceeded =>
            Ok(Json.obj("success" -> true))
          case rtf: RequestTransferFailed =>
            Ok(Json.obj("success" -> false, "error" -> rtf.error.name))
          case ar: ApiResult =>
            Ok(Json.obj("success" -> false, "error" -> ErrorCode.get(ar.code).get.name))
        }
  }

  def queryWithdrawal(cur: String) = Action.async {
    implicit request =>
      val limit = request.queryString.get("limit").map(_(0)).getOrElse("10").toInt
      val skip = request.queryString.get("skip").map(_(0)).getOrElse("0").toInt
      val currency = Some(Currency.valueOf(cur).getOrElse(Currency.Btc))

      BackendAccess.routers.paymentActor ? QueryTransfer(
        merchantId = Some(request.session.get("id").map(_.toLong).get),
        currency = currency,
        types = Seq(TransferType.Settle),
        cur = Cursor(limit = limit, skip = skip)) map {
          case rv: QueryTransferResult =>
            val jsArr = new JsArray(rv.transfers.map { t =>
              val date = t.created.get

              Json.obj("id" -> t.id.toString, "date" -> date, "status" -> t.status.getValue, "amount" -> t.externalAmount.get.toString, "address" -> t.address.get)
            })
            Ok(Json.obj("count" -> rv.count, "items" -> jsArr))
        }
  }

  def cancelWithdrawal = Action.async {
    implicit request =>
      val withdrawData = request.body.asJson.get \ "data"
      val cur = (withdrawData \ "currency").as[String]
      val transferId = (withdrawData \ "id").as[String]
      val currency = Currency.valueOf(cur).getOrElse(Currency.Btc)

      val t = AccountTransfer(
        id = transferId.toLong,
        merchantId = request.session.get("id").map(_.toLong).get,
        `type` = TransferType.Settle,
        currency = currency,
        amount = 0,
        updated = Some(System.currentTimeMillis())
      )

      BackendAccess.routers.paymentActor ? DoCancelSettle(t) map {
        case _ => Ok(Json.obj())
      }
  }

  def sendVerificationEmail = Action.async {
    implicit request =>
      val userEmail = request.session.get("email").getOrElse("")
      val (uuid, verifyCode) = generateVerifyCode
      println(s"send verification email: email=$userEmail, uuid=$uuid, code=$verifyCode")
      BackendAccess.routers.accountActor ? SendVerificationMail(userEmail, verifyCode.toString) map {
        rv => Ok(Json.obj("uuid" -> uuid))
      }
  }

  def getAccountNumbers(currency: String) = Action.async {
    implicit request =>
      val uid = request.session.get("id").getOrElse("-1").toLong
      val cur = Currency.valueOf(currency.toLowerCase().capitalize).getOrElse(Currency.Btc)
      BackendAccess.routers.accountActor ? QueryMerchantById(uid) map {
        case QueryMerchantResult(m) if m.isDefined =>
          val accountNumbers = parseAccountNumbers(m.get, cur)
          Ok(Json.obj("withdrawAddress" -> Json.toJson(accountNumbers)))
        case _ => Ok(Json.obj())
      }
  }

  def isUserActive() = Action.async {
    implicit request =>
      val uid = request.session.get("id").getOrElse("-1").toLong
      BackendAccess.backend ? QueryMerchantById(uid) map {
        case QueryMerchantResult(merchantOpt) if merchantOpt.isDefined =>
          val merchant = merchantOpt.get
          Ok(merchant.status.name)
        case _ =>
          logger.warn(s"can not get merchant by id. id: $uid")
          Ok(MerchantStatus.Pending.name)
      }
  }

  private def merchant2Json(merchant: Merchant) = {
    val baSeq = merchant.bankAccounts.getOrElse(Seq()).toSeq
    val baJson = Json.toJson(baSeq.map { ba =>
      val baObj: Map[String, String] = Map(
        MerchantBankCardBankName -> ba.bankName,
        MerchantBankCardOwner -> ba.ownerName,
        MerchantBankCardId -> ba.accountNumber,
        MerchantBankCardBranch -> ba.branchBankName.getOrElse[String](""),
        MerchantBankCardCountry -> ba.country.map(_.value.toString).getOrElse[String](""),
        MerchantBankCardBankSwift -> ba.bankSwiftCode.getOrElse(""),
        MerchantBankCardCity -> ba.bankCity.getOrElse[String](""),
        MerchantBankCardAddress -> ba.bankStreetAddress.getOrElse(""),
        MerchantBankCardPostal -> ba.bankPostalCode.getOrElse(""),
        MerchantBankCardCurrency -> ba.perferredCurrency.map(_.value.toString).getOrElse("")
      )
      "aaa" -> Json.toJson(baObj)
    }.toMap[String, JsValue])

    val contactJson = Json.toJson(merchant.contact match {
      case Some(c) =>
        Map(MerchantContactName -> merchant.name.getOrElse(""),
          MerchantContactAddr -> c.address.getOrElse(""),
          MerchantContactPhone -> c.phone.getOrElse(""),
          MerchantContactEmail -> c.email.getOrElse(""),
          MerchantBtcAddress -> merchant.btcWithdrawalAddress.getOrElse(""))
      case None =>
        Map(MerchantBtcAddress -> merchant.btcWithdrawalAddress.getOrElse[String](""))
    })

    val tokenArray = new JsArray(merchant.tokenAndKeyPairs.map { i =>
      Json.obj("token" -> i._1, "secret" -> i._2)
    }.toSeq)

    Json.obj("bankaccount" -> baJson, "contact" -> contactJson, "token" -> tokenArray)
  }

  private def parseAccountNumbers(merchant: Merchant, currency: Currency): Set[String] = currency match {
    case Currency.Btc => Set(merchant.btcWithdrawalAddress.getOrElse(""))
    case Currency.Cny => if (merchant.bankAccounts.isDefined) {
      merchant.bankAccounts.get.filter(_.perferredCurrency == Some(currency)).map(x => x.bankName + " " + x.branchBankName.getOrElse("") + " " + x.accountNumber + " " + x.ownerName).toSet
    } else Set.empty[String]
    case Currency.Usd => if (merchant.bankAccounts.isDefined) {
      merchant.bankAccounts.get.filter(_.perferredCurrency == Some(currency)).map(x =>
        x.bankName
          + " " + x.branchBankName.getOrElse("")
          + " " + x.bankSwiftCode.getOrElse("")
          + " " + x.bankCity.getOrElse("")
          + " " + x.bankStreetAddress.getOrElse("")
          + " " + x.accountNumber
          + " " + x.ownerName
      ).toSet
    } else Set.empty[String]
    case _ => Set.empty[String]
  }
}
