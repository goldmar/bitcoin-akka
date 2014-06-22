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

package com.markgoldenstein.bitcoin

import scala.concurrent.duration.FiniteDuration
import akka.actor.{Actor, Props, TypedActor}
import akka.pattern.ask
import akka.util.Timeout
import com.markgoldenstein.bitcoin.messages.actor._
import com.markgoldenstein.bitcoin.messages.json._

class BtcWalletImpl(
    websocketUri: String,
    rpcUser: String,
    rpcPass: String,
    keyStoreFile: String,
    keyStorePass: String,
    onConnect: () => Unit,
    handleNotification: Actor.Receive,
    timeoutDuration: FiniteDuration)
  extends BtcWallet {

  implicit val timeout = Timeout(timeoutDuration)

  val actor = TypedActor.context.actorOf(Props(
    new BtcWalletActor(
      websocketUri,
      rpcUser,
      rpcPass,
      keyStoreFile,
      keyStorePass,
      onConnect,
      handleNotification,
      timeoutDuration)))

  override def createRawTransaction(inputs: Seq[(String, BigDecimal)], receivers: Seq[(String, BigDecimal)]) =
    (actor ? CreateRawTransaction(inputs, receivers)).mapTo[String]

  override def getNewAddress() =
    (actor ? GetNewAddress).mapTo[String]

  override def getRawTransaction(transactionHash: String) =
    (actor ? GetRawTransaction(transactionHash)).mapTo[RawTransaction]

  override def listUnspentTransactions(minConfirmations: BigDecimal, maxConfirmations: BigDecimal) =
    (actor ? ListUnspentTransactions(minConfirmations, maxConfirmations)).mapTo[Seq[UnspentTransaction]]

  override def sendRawTransaction(signedTransaction: String) =
    (actor ? SendRawTransaction(signedTransaction)).mapTo[String]

  override def signRawTransaction(transaction: String) =
    (actor ? SignRawTransaction(transaction)).mapTo[SignedTransaction]

  override def walletPassPhrase(walletPass: String, timeout: BigDecimal) =
    (actor ! WalletPassPhrase(walletPass, timeout))
}
