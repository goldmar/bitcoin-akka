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

package com.markgoldenstein.bitcoin.messages.actor

import com.markgoldenstein.bitcoin.messages.json.{UnspentTransaction, JsonResponse}


trait ActorMessage


trait NotificationMessage extends ActorMessage

case class ReceivedPayment(txId: String, address: String, amount: BigDecimal, confirmations: BigDecimal) extends NotificationMessage


trait RequestMessage extends ActorMessage

case class CreateRawTransaction(inputs: Seq[(String, BigDecimal)], receivers: Seq[(String, BigDecimal)]) extends RequestMessage

case object GetNewAddress extends RequestMessage

case class GetRawTransaction(transactionHash: String) extends RequestMessage

case class ListUnspentTransactions(minConfirmations: BigDecimal = 1, maxConfirmations: BigDecimal = 999999) extends RequestMessage

case class SendRawTransaction(signedTransaction: String) extends RequestMessage

case class SignRawTransaction(transaction: String) extends RequestMessage

case class WalletPassPhrase(walletPass: String, timeout: BigDecimal) extends RequestMessage


trait ResponseMessage extends ActorMessage


case class CompleteRequest(response: JsonResponse)

case class RemoveRequest(id: String)

case object Connected

case object Disconnected
