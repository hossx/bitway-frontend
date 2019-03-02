package controllers

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.UUID
import java.util.Random
import play.api.mvc._
import play.api.Logger
import play.api.i18n.Lang
import play.api.Play
import play.api.Play.current

import com.coinport.bitway.data.ErrorCode

trait AccessLogging {
  val accessLogger = Logger("access")

  object AccessLoggingAction extends ActionBuilder[Request] {
    def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]) = {
      accessLogger.info(s"method=${request.method} uri=${request.uri} remote-address=${request.remoteAddress}")
      block(request)
    }
  }
}

case class ApiResult(success: Boolean = true, code: Int = 0, message: String = "", data: Option[Any] = None)

trait Validator {
  def result: ApiResult
  def validate: Either[ApiResult, Boolean]
  def logger: Logger = Logger("validator")
}

abstract class GeneralValidator[T](params: T*) extends Validator {
  def isValid(t: T): Boolean
  def validate = validate(params)

  private def validate(params: Seq[T]): Either[ApiResult, Boolean] =
    if (params.isEmpty) Right(true)
    else {
      if (!isValid(params.head))
        Left(result)
      else
        validate(params.tail)
    }
}

class RepeatInputValidator(input: String, repeat: String) extends Validator {
  val result = ApiResult(false, ErrorCode.RepeatNotEqual.value, "repeat not identical with input", None)

  def validate = if (input != null && input.equals(repeat)) Right(true) else Left(result)
}

class CachedValueValidator(error: ErrorCode, check: Boolean, uuid: String, value: String) extends Validator {

  val cacheService = CacheService.getDefaultServiceImpl
  val result = ApiResult(false, error.value, error.toString)

  def validate = {
    if (!check) Right(true)
    else {
      if (uuid == null || uuid.trim.isEmpty || value == null || value.trim.isEmpty) Left(result) else {
        val cachedValue = cacheService.get(uuid)
        logger.info(s" validate cached value. uuid: $uuid, cachedValue: $cachedValue")
        if (cachedValue != null && cachedValue.equals(value)) {
          //cacheService.pop(uuid)
          Right(true)
        } else Left(result)
      }
    }
  }
}

object ControllerHelper {
  val emptyParamError = ApiResult(false, ErrorCode.ParamEmpty.value, "param can not emppty", None)
  val emailFormatError = ApiResult(false, ErrorCode.InvalidEmailFormat.value, "email format error", None)
  val passwordFormatError = ApiResult(false, ErrorCode.InvalidPasswordFormat.value, "password format error", None)

  class StringNonemptyValidator(stringParams: String*) extends GeneralValidator[String](stringParams: _*) {
    val result = emptyParamError
    def isValid(param: String) = param != null && param.trim.length > 0
  }

  class EmailFormatValidator(emails: String*) extends GeneralValidator[String](emails: _*) {
    val result = emailFormatError
    val emailRegex = """^[-0-9a-zA-Z.+_]+@[-0-9a-zA-Z.+_]+\.[a-zA-Z]{2,4}$"""
    def isValid(param: String) = param != null && param.matches(emailRegex)
  }

  class PasswordFormetValidator(passwords: String*) extends GeneralValidator[String](passwords: _*) {
    val result = passwordFormatError
    def isValid(param: String) = param != null && param.trim.length >= 8 && param.trim.length <= 32
  }

  def validateParamsAndThen(validators: Validator*)(f: => Future[_]): Future[_] =
    if (validators.isEmpty)
      f
    else {
      validators.head.validate match {
        case Left(r) => Future(r)
        case Right(b) => validateParamsAndThen(validators.tail: _*)(f)
      }
    }

  def langFromRequestCookie(request: Request[_]): Lang = {
    request.cookies.get(Play.langCookieName) match {
      case Some(langCookie) => Lang(langCookie.value)
      case None =>
        if (request.acceptLanguages.size > 0) request.acceptLanguages(0)
        else Lang("zh-CN")
    }
  }

  def langFromParam(lang: String): Lang =
    lang match {
      case "zh-CN" => Lang("zh-CN")
      case "zh-TW" => Lang("zh-TW")
      case "en-US" => Lang("en-US")
      case _ => Lang("en-US")
    }

  val rand = new Random()
  val randMax = 999999
  val randMin = 100000
  def generateVerifyCode: (String, String) = {
    val uuid = UUID.randomUUID().toString
    val verifyNum = rand.nextInt(randMax - randMin) + randMin
    val verifyCode = verifyNum.toString
    val cacheService = CacheService.getDefaultServiceImpl
    cacheService.putWithTimeout(uuid, verifyCode, 30 * 60)
    (uuid, verifyCode)
  }
}
