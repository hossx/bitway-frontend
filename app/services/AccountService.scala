package services

import akka.util.Timeout
import com.coinport.bitway.data._
import controllers.BackendAccess._
import scala.concurrent.ExecutionContext.Implicits.global
import akka.pattern.ask
import models._
import scalikejdbc._
import scalikejdbc.config.DBs
import scala.concurrent.duration._
import scala.concurrent.Await

object AccountService {
  implicit val timeout = Timeout(2 seconds)
  DBs.setupAll()

  def createUser(user: User) = {
    DB localTx { implicit session =>
      sql"insert into users (id, email, password) values (${user.id}, ${user.email}, ${user.password})"
        .update.apply()
    }
  }

  def getUser(email: String) = {
    DB readOnly { implicit session =>
      sql"select * from users where email=${email}"
        .map(mapper).single().apply()
    }
  }

  def listUser = {
    val result = DB readOnly { implicit session =>
      sql"select * from users"
        .map(mapper).list.apply()
    }

    result
  }

  def getSecretKey(id: Long) = {
    val future = backend ? QueryMerchantById(id) map {
      case QueryMerchantResult(merchantOpt) if merchantOpt.isDefined =>
        val merchant = merchantOpt.get
        // TODO: use SINGLE secret key instead
        merchant.secretKey.getOrElse("")
      case _ => ""
    }
    Await.result(future, 2 second)
  }

  private def mapper(rs: WrappedResultSet): User = {
    User(
      id = rs.int("id"),
      email = rs.stringOpt("email"),
      password = rs.string("password")
    )
  }
}
