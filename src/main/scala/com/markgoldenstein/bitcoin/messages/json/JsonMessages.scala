/*
 * Copyright 2014 Mark Goldenstein
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.markgoldenstein.bitcoin.messages.json

import scala.language.implicitConversions
import play.api.libs.json._
import play.api.libs.functional.syntax._
import com.markgoldenstein.bitcoin.messages.Utils

object JsonMessage {
  def createRawTransaction(inputs: Seq[(String, BigDecimal)], receivers: Seq[(String, BigDecimal)]) =
    JsonRequest("1.0", Utils.getUUID, "createrawtransaction", Json.arr(
      inputs.map(i => Json.obj("txid" -> i._1, "vout" -> i._2)),
      JsObject(receivers.map(r => r._1 -> Json.toJson(r._2)))))

  def getNewAddress =
    JsonRequest("1.0", Utils.getUUID, "getnewaddress", Json.arr())

  def getRawTransaction(transactionHash: String) =
    JsonRequest("1.0", Utils.getUUID, "getrawtransaction", Json.arr(transactionHash, 1))

  def listUnspentTransactions(minConfirmations: BigDecimal, maxConfirmations: BigDecimal, addresses: Seq[String] = Seq.empty[String]) = {
    val params = if (addresses.isEmpty)
      Json.arr(minConfirmations, maxConfirmations)
    else
      Json.arr(minConfirmations, maxConfirmations, addresses)

    JsonRequest("1.0", Utils.getUUID, "listunspent", params)
  }

  def sendRawTransaction(signedTransaction: String) =
    JsonRequest("1.0", Utils.getUUID, "sendrawtransaction", Json.arr(signedTransaction))

  def signRawTransaction(transaction: String) =
    JsonRequest("1.0", Utils.getUUID, "signrawtransaction", Json.arr(transaction))

  def walletPassPhrase(walletPass: String, timeout: BigDecimal) =
    JsonRequest("1.0", Utils.getUUID, "walletpassphrase", Json.arr(walletPass, timeout))
}

sealed trait JsonMessage

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
  implicit val rawTransactionFormats = Json.format[RawTransaction]

  implicit val unspentTransactionFormats = Json.format[UnspentTransaction]

  implicit val signedTransactionFormats = Json.format[SignedTransaction]
}
