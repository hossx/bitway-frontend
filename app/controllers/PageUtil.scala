package controllers

import com.coinport.bitway.data._
import scala.collection._
//import scala.collection.mutable.Map

object PageUtil {
  object Const {
    val KeyMerchantContact = "kmc"
    val KeyMerchantBankCards = "kmbc"
    val KeyMerchantApiTokens = "kmat"
    val KeyMerchantSecret = "ksecret"

    val MerchantBtcAddress = "mba"
    val MerchantSecret = "secret"

    val MerchantContactName = "mcn"
    val MerchantContactAddr = "mca"
    val MerchantContactEmail = "mce"
    val MerchantContactPhone = "mcp"

    val MerchantBankCardBankName = "mbcbn"
    val MerchantBankCardOwner = "mbco"
    val MerchantBankCardId = "mbci"
    val MerchantBankCardBranch = "mbcbr"
    val MerchantBankCardBankSwift = "mbcbs"
    val MerchantBankCardCountry = "mbcc"
    val MerchantBankCardAddress = "mbca"
    val MerchantBankCardCity = "mbcct"
    val MerchantBankCardPostal = "mbcp"
    val MerchantBankCardCurrency = "mbccu"

    val MerchantApiToken = "mat"
    val MerchantApiSecretKey = "mask"

  }

  import Const._

  def merchant2ProfileMap(merchant: Merchant): immutable.Map[String, List[immutable.Map[String, String]]] = {
    val resMap = mutable.Map.empty[String, List[immutable.Map[String, String]]]
    merchant.contact match {
      case Some(c) =>
        resMap += KeyMerchantContact -> List(immutable.Map(
          MerchantContactName -> merchant.name.getOrElse(""),
          MerchantContactAddr -> c.address.getOrElse(""),
          MerchantContactPhone -> c.phone.getOrElse(""),
          MerchantContactEmail -> c.email.getOrElse(""),
          MerchantBtcAddress -> merchant.btcWithdrawalAddress.getOrElse("")
        ))

      case None => resMap += KeyMerchantContact -> List(immutable.Map(
        MerchantBtcAddress -> merchant.btcWithdrawalAddress.getOrElse("")
      ))
    }

    merchant.tokenAndKeyPairs foreach {
      kv =>
        val (token, key) = (kv._1, kv._2)
        resMap += KeyMerchantApiTokens -> (resMap.get(KeyMerchantApiTokens).getOrElse(List.empty[immutable.Map[String, String]]) ++ List(immutable.Map(
          MerchantApiToken -> token,
          MerchantApiSecretKey -> key
        )))
    }

    resMap += KeyMerchantSecret -> List(immutable.Map(MerchantSecret -> merchant.secretKey.getOrElse("")))

    merchant.bankAccounts foreach {
      _ foreach {
        bankAccount =>
          resMap += KeyMerchantBankCards -> (resMap.get(KeyMerchantBankCards).getOrElse(List.empty[immutable.Map[String, String]]) ++ List(immutable.Map(
            MerchantBankCardBankName -> bankAccount.bankName,
            MerchantBankCardOwner -> bankAccount.ownerName,
            MerchantBankCardId -> bankAccount.accountNumber,
            MerchantBankCardBranch -> bankAccount.branchBankName.getOrElse(""),
            MerchantBankCardCountry -> bankAccount.country.map(_.value.toString).getOrElse(""),
            MerchantBankCardBankSwift -> bankAccount.bankSwiftCode.getOrElse(""),
            MerchantBankCardCity -> bankAccount.bankCity.getOrElse(""),
            MerchantBankCardAddress -> bankAccount.bankStreetAddress.getOrElse(""),
            MerchantBankCardPostal -> bankAccount.bankPostalCode.getOrElse(""),
            MerchantBankCardCurrency -> bankAccount.perferredCurrency.map(_.value.toString).getOrElse("")
          ))).filter(_.nonEmpty)
      }
    }

    if (!resMap.get(KeyMerchantBankCards).isDefined
      || resMap.get(KeyMerchantBankCards).get.isEmpty) {
      resMap += KeyMerchantBankCards -> List(immutable.Map.empty[String, String])
    }

    resMap.toMap
  }

}
