package controllers

import play.api.mvc.{ AnyContent, AnyContentAsFormUrlEncoded, AnyContentAsJson }
import play.api.libs.json.{ JsBoolean, JsNumber, JsString }

/**
 * Created by chenxi on 12/9/14.
 */
object Util {
  def getParamFromAnyContent(data: AnyContent, param: String): Option[String] = {
    data match {
      case AnyContentAsJson(body) =>
        (body \ param) match {
          case s: JsString => s.asOpt[String]
          case n: JsNumber => n.asOpt[Double].map(_.toString)
          case b: JsBoolean => b.asOpt[Boolean].map(_.toString)
          case _ => None
        }
      case AnyContentAsFormUrlEncoded(body) =>
        body.get(param).map(_(0))
      case _ => throw new Exception("Post data is neither json nor url encoded form!!")
    }
  }

  def getParam(queryString: Map[String, Seq[String]], param: String): Option[String] = {
    queryString.get(param).map(_(0))
  }

  def getParam(queryString: Map[String, Seq[String]], param: String, default: String): String = {
    queryString.get(param) match {
      case Some(seq) =>
        if (seq.isEmpty) default else seq(0)
      case None =>
        default
    }
  }
}
