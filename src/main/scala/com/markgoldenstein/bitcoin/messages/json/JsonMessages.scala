/*
 * This file is part of bitcoin-akka.  bitcoin-akka is free software: you can
 * redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, version 2.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Copyright 2014 Mark Goldenstein
 */

package com.markgoldenstein.bitcoin.messages.json

import play.api.libs.json._
import play.api.libs.functional.syntax._
import scala.language.implicitConversions
import com.markgoldenstein.bitcoin.messages.UUIDGenerator

object JsonMessage {
  def listUnspentTransactionsRequest(minConfirmations: BigDecimal = 1, maxConfirmations: BigDecimal = 999999, addresses: Seq[String] = Seq.empty[String]) = {
    val params =
      if (addresses.isEmpty) Json.arr(minConfirmations, maxConfirmations)
      else Json.arr(minConfirmations, maxConfirmations, addresses)
    JsonRequest("1.0", UUIDGenerator.get, "listunspent", params)
  }

  def createRawTransactionRequest(inputs: Seq[(String, BigDecimal)], receivers: Seq[(String, BigDecimal)]) =
    JsonRequest("1.0", UUIDGenerator.get, "createrawtransaction", Json.arr(
      inputs.map(i => Json.obj("txid" -> i._1, "vout" -> i._2)),
      JsObject(receivers.map(r => r._1 -> Json.toJson(r._2)))))

  def signRawTransactionRequest(transaction: String) =
    JsonRequest("1.0", UUIDGenerator.get, "signrawtransaction", Json.arr(transaction))

  def sendRawTransactionRequest(signedTransaction: String) =
    JsonRequest("1.0", UUIDGenerator.get, "sendrawtransaction", Json.arr(signedTransaction))

  def getRawTransactionRequest(transactionHash: String) =
    JsonRequest("1.0", UUIDGenerator.get, "getrawtransaction", Json.arr(transactionHash, 1))

  def newAddressRequest =
    JsonRequest("1.0", UUIDGenerator.get, "getnewaddress", Json.arr())

  def walletPassPhraseRequest(walletPass: String) =
    JsonRequest("1.0", UUIDGenerator.get, "walletpassphrase", Json.arr(walletPass, 5))
}

trait JsonMessage

case class JsonNotification(jsonrpc: String, method: String, params: JsArray) extends JsonMessage

case class JsonRequest(jsonrpc: String, id: String, method: String, params: JsArray) extends JsonMessage

case class JsonResponse(result: Option[JsValue], error: Option[JsValue], id: String) extends JsonMessage

object JsonImplicits {
  implicit val notificationFormats = Json.format[JsonNotification]
  implicit val requestFormats = Json.format[JsonRequest]
  implicit val responseFormats = Json.format[JsonResponse]

  implicit val jsonReads =
    __.read[JsonNotification].map(x => x: JsonMessage) |
      __.read[JsonRequest].map(x => x: JsonMessage) |
      __.read[JsonResponse].map(x => x: JsonMessage)

  implicit val transactionNotificationFormats = Json.format[TransactionNotification]

  implicit val rawTransactionScriptSigFormats = Json.format[ScriptSig]
  implicit val rawTransactionScriptPubKeyFormats = Json.format[ScriptPubKey]
  implicit val rawTransactionVInFormats = Json.format[VIn]
  implicit val rawTransactionVOutFormats = Json.format[VOut]
  implicit val rawTransactionFormats = Json.format[GetRawTransactionResponse]

  implicit val unspentTransactionFormats = Json.format[UnspentTransaction]

  implicit val signedTransactionFormats = Json.format[SignedTransaction]
}
