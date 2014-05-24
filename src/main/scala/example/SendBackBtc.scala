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

package example

import scala.language.postfixOps
import scala.language.implicitConversions
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.markgoldenstein.bitcoin.messages.actor._
import com.markgoldenstein.bitcoin.messages.json._
import com.markgoldenstein.bitcoin.BtcWalletActor

object SendBackBtc extends App {
  implicit val timeout = Timeout(5 seconds)

  // we need an ActorSystem to host the BtcWallet actor
  val system = ActorSystem("BitcoinSystem")
  implicit val executionContext = system.dispatcher

  // configure our actor
  val config = ConfigFactory.load().getConfig("btcwallet")
  val websocketUri = config.getString("websocket-uri")
  val rpcUser: String = config.getString("rpc-user")
  val rpcPass: String = config.getString("rpc-pass")
  val walletPass: String = config.getString("wallet-pass")
  val keyStoreFile: String = config.getString("keystore-file")
  val keyStorePass: String = config.getString("keystore-pass")
  val props = Props(new BtcWalletActor(websocketUri, rpcUser, rpcPass, keyStoreFile, keyStorePass, onConnect, handleNotification, timeout.duration))

  // create and start our actor
  val btcWallet = system.actorOf(props, "btcwallet")

  def onConnect() {
    // ask about unspent transactions and process them
    btcWallet.ask(ListUnspentTransactions(minConfirmations = 0)).mapTo[Seq[UnspentTransaction]]
      .foreach(_.map(tx => processTransaction(tx.txid, tx.address, tx.amount)))
  }

  def handleNotification: Actor.Receive = {
    case ReceivedPayment(txId, address, amount, confirmations) =>
      processTransaction(txId, address, amount)
    case _ => // ignore
  }

  // process the transaction by sending the bitcoins back
  def processTransaction(txId: String, address: String, amount: BigDecimal) {
    for {
    // request the relevant raw transaction
      tx <- btcWallet.ask(GetRawTransaction(txId)).mapTo[RawTransaction]
      // request the previous transaction for the first input (to get the sender address)
      prevTx <- btcWallet.ask(GetRawTransaction(tx.vin(0).txid)).mapTo[RawTransaction]
    } yield {
      // get the sender address
      val senderAddress = prevTx.vout(tx.vin(0).vout).scriptPubKey.addresses(0)
      // get a list of outputs that send something to the given address
      val listOfTxOuts =
        for (txOut <- tx.vout
             if (txOut.scriptPubKey.`type` == "pubkeyhash"
               && address == txOut.scriptPubKey.addresses(0))
        ) yield txOut

      // send it back (using the correct outputs)
      val inputs = for (out <- listOfTxOuts.map(_.n)) yield txId -> out
      btcWallet.ask(CreateRawTransaction(inputs, Seq(senderAddress -> amount))).mapTo[String].flatMap(tx => {
        btcWallet ! WalletPassPhrase(walletPass, timeout.duration.toSeconds)
        btcWallet.ask(SignRawTransaction(tx)).mapTo[SignedTransaction]
      }).foreach(tx =>
        if (tx.complete) {
          btcWallet ! SendRawTransaction(tx.hex)
        })
    }
  }
}