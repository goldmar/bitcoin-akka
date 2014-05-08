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

object BtcWalletActorImpl {
  val config = ConfigFactory.load().getConfig("btcwallet")
  val websocketUri = config.getString("websocket-uri")
  val rpcUser: String = config.getString("rpc-user")
  val rpcPass: String = config.getString("rpc-pass")
  val walletPass: String = config.getString("wallet-pass")
  val keyStoreFile: String = config.getString("keystore-file")
  val keyStorePass: String = config.getString("keystore-pass")

  def props: Props = Props(new BtcWalletActorImpl(websocketUri, rpcUser, rpcPass, walletPass, keyStoreFile, keyStorePass))
}

class BtcWalletActorImpl(websocketUri: String, rpcUser: String, rpcPass: String, walletPass: String, keyStoreFile: String, keyStorePass: String)
  extends BtcWalletActor(websocketUri, rpcUser, rpcPass, walletPass, keyStoreFile, keyStorePass) {

  val watchedAddresses = Seq("n1KWwWXRPPvJbFa1Bpwc46WU27Ku4XT6M8", "mwftPxUGPhb94sM47LgXN8F5GR5Ujd2Xf3")

  override def handleNotification = {
    case ReceivedPaymentNotification(txId, vOuts, senderAddress, amount) =>
      val inputs = for (out <- vOuts) yield txId -> out
      self.ask(CreateRawTransactionRequest(inputs, Seq(senderAddress -> amount))).mapTo[String]
        .flatMap(tx => self.ask(SignRawTransactionRequest(tx)).mapTo[SignedTransaction]).foreach(tx =>
        if (tx.complete) self ! SendRawTransactionRequest(tx.hex))
    case _ => // ignore
  }

  override def handleRequest = {
    case _ => // ignore
  }
}