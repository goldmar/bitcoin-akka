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

package com.markgoldenstein.bitcoin.messages.actor

import com.markgoldenstein.bitcoin.messages.json.JsonResponse


trait ActorMessage


trait NotificationMessage extends ActorMessage

case class ReceivedPaymentNotification(txId: String, vOuts: Seq[BigDecimal], senderAddress: String, amount: BigDecimal) extends NotificationMessage


trait RequestMessage extends ActorMessage

case class CreateRawTransactionRequest(inputs: Seq[(String, BigDecimal)], receivers: Seq[(String, BigDecimal)]) extends RequestMessage

case class SignRawTransactionRequest(transaction: String) extends RequestMessage

case class SendRawTransactionRequest(signedTransaction: String) extends RequestMessage

case class GetRawTransactionRequest(transactionHash: String) extends RequestMessage

case object ProcessMissedTransactionsRequest extends RequestMessage

case object NewAddressRequest extends RequestMessage


trait ResponseMessage extends ActorMessage


case class CompleteRequest(response: JsonResponse)

case class RemoveRequest(id: String)

case object Connected

case object Disconnected
