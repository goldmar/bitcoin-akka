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

package example

import scala.language.postfixOps
import scala.language.implicitConversions
import com.typesafe.config.ConfigFactory
import akka.actor.Props
import akka.pattern.ask
import com.markgoldenstein.bitcoin.messages.actor._
import com.markgoldenstein.bitcoin.messages.json._
import com.markgoldenstein.bitcoin.BtcWalletActor

object SendBackBtcWalletActor {
  val config = ConfigFactory.load().getConfig("btcwallet")
  val websocketUri = config.getString("websocket-uri")
  val rpcUser: String = config.getString("rpc-user")
  val rpcPass: String = config.getString("rpc-pass")
  val walletPass: String = config.getString("wallet-pass")
  val keyStoreFile: String = config.getString("keystore-file")
  val keyStorePass: String = config.getString("keystore-pass")

  def props: Props = Props(new SendBackBtcWalletActor(websocketUri, rpcUser, rpcPass, walletPass, keyStoreFile, keyStorePass))
}

class SendBackBtcWalletActor(websocketUri: String, rpcUser: String, rpcPass: String, walletPass: String, keyStoreFile: String, keyStorePass: String)
  extends BtcWalletActor(websocketUri, rpcUser, rpcPass, walletPass, keyStoreFile, keyStorePass) {

  override def onConnect() {
    // ask about unspent transactions and process them
    self.ask(GetUnspentTransactionsRequest).mapTo[Seq[UnspentTransaction]]
      .foreach(_.map(tx => processTransaction(tx.txid, tx.address, tx.amount)))
  }

  override def handleMessage = {
    case ReceivedPaymentNotification(txId, address, amount, confirmations) =>
      processTransaction(txId, address, amount)
    case _ => // ignore
  }

  // process the transaction the bitcoins back
  def processTransaction(txId: String, address: String, amount: BigDecimal) {
    for {
    // request the relevant raw transaction
      tx <- self.ask(GetRawTransactionRequest(txId)).mapTo[GetRawTransactionResponse]
      // request the previous transaction for the first input (to get the sender address)
      prevTx <- self.ask(GetRawTransactionRequest(tx.vin(0).txid)).mapTo[GetRawTransactionResponse]
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
      self.ask(CreateRawTransactionRequest(inputs, Seq(senderAddress -> amount))).mapTo[String]
        .flatMap(tx => self.ask(SignRawTransactionRequest(tx)).mapTo[SignedTransaction]).foreach(tx =>
        if (tx.complete) self ! SendRawTransactionRequest(tx.hex))
    }
  }
}