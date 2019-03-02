package models

import scalikejdbc._

case class User(id: Long,
  name: Option[String] = None,
  email: Option[String] = None,
  password: String)
/*
struct Merchant {
1: i64 id
2: optional string name
3: optional Contact contact
4: map<string, string> tokenAndKeyPairs
5: optional string email
6: optional bool emailVerified
7: optional string passwordHash
8: optional string verificationToken
9: optional string passwordResetToken
10: optional set<BankAccount> bankAccounts
11: optional map<Currency, CashAccount> settleAccount
12: double feeRate = 0.0 // normal rate is 0.005
13: double constFee = 0.0
14: optional string btcWithdrawalAddress
}
*/ 