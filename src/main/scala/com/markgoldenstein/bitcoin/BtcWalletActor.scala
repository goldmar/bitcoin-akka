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

package com.markgoldenstein.bitcoin

import scala.Exception
import scala.collection.mutable
import scala.collection.JavaConversions._
import scala.concurrent.{TimeoutException, Promise}
import scala.concurrent.duration._
import scala.Some
import scala.language.postfixOps
import scala.language.implicitConversions
import sext._
import java.net.URI
import java.security.KeyStore
import java.io.{FileInputStream, File}
import javax.net.ssl.{SSLContext, TrustManagerFactory}
import akka.actor.{Actor, ActorLogging, ReceiveTimeout}
import akka.pattern.{ask, pipe}
import play.api.libs.json._
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.java_websocket.drafts.{Draft, Draft_17}
import com.markgoldenstein.bitcoin.messages.actor._
import com.markgoldenstein.bitcoin.messages.json._
import JsonImplicits._
import akka.util.Timeout

abstract class BtcWalletActor(websocketUri: String, rpcUser: String, rpcPass: String, walletPass: String, keyStoreFile: String, keyStorePass: String) extends Actor with ActorLogging {
  // we put the actual business logic for notification handling here
  def handleNotification: PartialFunction[NotificationMessage, Unit]

  // we put the actual business logic for request handling here
  def handleRequest: PartialFunction[RequestMessage, Unit]

  implicit val executionContext = context.dispatcher
  implicit val timeout = Timeout(5 seconds)

  // this HashMap maps JSON RPC request IDs to the corresponding response promises
  // and a function that converts the JSON RPC response to the final actor response
  val rpcRequests = mutable.HashMap.empty[String, (Promise[AnyRef], JsValue => AnyRef)]

  // start actor in connecting mode
  override def receive = connecting

  // try to connect to btcwallet
  def connecting: Actor.Receive = {
    case Connected =>
      context.become(active)
      processMissedTransactions()
    case ReceiveTimeout => tryToConnect()
    case _: RequestMessage => log.info("Cannot process request: no connection to btcwallet.")
    case _ => // ignore
  }

  // connection established, handle requests
  def active: Actor.Receive = {
    case CompleteRequest(JsonResponse(resultOption, errorOption, id)) =>
      // only inspect expected messages
      rpcRequests.remove(id).foreach(req => {
        val (p: Promise[AnyRef], resultFunc) = req
        (resultOption, errorOption) match {
          case (Some(result), _) =>
            log.debug("Actor Response\n{}", result.treeString)
            p trySuccess resultFunc(result)
          case (_, Some(error)) =>
            val prettyError = Json.prettyPrint(error)
            log.error("Json request returned Error\n{}", prettyError)
            p tryFailure new RuntimeException(prettyError)
          case _ => // ignore
        }
      })

    case m: NotificationMessage =>
      log.debug("Actor Notification\n{}", m.treeString)
      handleNotification.applyOrElse(m, unhandled)

    case m @ CreateRawTransactionRequest(inputs, receivers) =>
      log.debug("Actor Request\n{}", m.treeString)
      val resultFunc = (result: JsValue) => result.as[String]
      request(JsonMessage.createRawTransactionRequest(inputs, receivers), resultFunc)
    case m @ SignRawTransactionRequest(transaction) =>
      log.debug("Actor Request\n{}", m.treeString)
      request(JsonMessage.walletPassPhraseRequest(walletPass))
      val resultFunc = (result: JsValue) => Json.fromJson[SignedTransaction](result).get
      request(JsonMessage.signRawTransactionRequest(transaction), resultFunc)
    case m @ SendRawTransactionRequest(signedTransaction) =>
      log.debug("Actor Request\n{}", m.treeString)
      val resultFunc = (result: JsValue) => result.as[String]
      request(JsonMessage.sendRawTransactionRequest(signedTransaction), resultFunc)
    case m @ GetRawTransactionRequest(transactionHash) =>
      log.debug("Actor Request\n{}", m.treeString)
      val resultFunc = (result: JsValue) => Json.fromJson[GetRawTransactionResponse](result).get
      request(JsonMessage.getRawTransactionRequest(transactionHash), resultFunc)
    case m @ ProcessMissedTransactionsRequest =>
      log.debug("Actor Request\n{}", m.treeString)
      val resultFunc = (result: JsValue) => Json.fromJson[Seq[UnspentTransaction]](result).get
      request(JsonMessage.listUnspentTransactionsRequest(minConfirmations = 0), resultFunc)
    case m @ NewAddressRequest =>
      log.debug("Actor Request\n{}", m.treeString)
      val resultFunc = (result: JsValue) => result.as[String]
      request(JsonMessage.newAddressRequest, resultFunc)

    case requestMessage: RequestMessage =>
      log.debug("Actor Request\n{}", requestMessage.treeString)
      handleRequest.applyOrElse(requestMessage, unhandled)

    case RemoveRequest(id) => rpcRequests -= id
    case Disconnected =>
      log.info("Connection to btcwallet closed.")
      context.become(connecting)
      tryToConnect()
    case _ => // ignore
  }

  def handleJsonNotification: PartialFunction[JsonNotification, Unit] = {
    // handle a new transaction notification: call processTransaction
    case JsonNotification(_, "newtx", params) =>
      Json.fromJson[TransactionNotification](params(1)).map(txNtfn =>
        if (txNtfn.category == "receive")
          processTransaction(txNtfn.txid, txNtfn.address, txNtfn.amount))
    case _ => // ignore
  }

  // initialize SSL stuff - we need this to open a btcwallet connection
  var btcWalletClient: WebSocketBtcWalletClient = null
  val uri = new URI(websocketUri)
  val headers = Map(("Authorization", "Basic " + new sun.misc.BASE64Encoder().encode((rpcUser + ":" + rpcPass).getBytes)) :: Nil: _*)
  val ks = KeyStore.getInstance("JKS")
  val kf = new File(keyStoreFile)
  ks.load(new FileInputStream(kf), keyStorePass.toCharArray)
  val tmf = TrustManagerFactory.getInstance("SunX509")
  tmf.init(ks)
  val sslContext = SSLContext.getInstance("TLS")
  sslContext.init(null, tmf.getTrustManagers, null)
  val factory = sslContext.getSocketFactory
  tryToConnect()

  def tryToConnect() {
    rpcRequests.clear()
    btcWalletClient = new WebSocketBtcWalletClient(uri, new Draft_17, headers, 0)
    btcWalletClient.setSocket(factory.createSocket())
    val connected = btcWalletClient.connectBlocking()

    if (connected) {
      log.info("Connection to btcwallet established.")
      self ! Connected
    } else {
      log.info(s"Btcwallet not available: $websocketUri")
      context.system.scheduler.scheduleOnce(5 seconds, self, ReceiveTimeout)
    }
  }

  // checks watched addresses for unspent transactions and creates unspent transaction notifications accordingly
  def processMissedTransactions() {
    self.ask(ProcessMissedTransactionsRequest).mapTo[Seq[UnspentTransaction]].foreach(unspentTransactions =>
      unspentTransactions.foreach(tx =>
        processTransaction(tx.txid, tx.address, tx.amount)
      ))
  }

  // create a ReceivedPaymentNotification
  def processTransaction(txid: String, address: String, amount: BigDecimal) {
    for {
      // request the relevant raw transaction
      tx <- self.ask(GetRawTransactionRequest(txid)).mapTo[GetRawTransactionResponse]
      // request the previous transaction for the first input
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
      val calculatedAmount = listOfTxOuts.map(_.value).sum
      if (amount != calculatedAmount) {
        // should not happen
        log.error("Error while processing transaction [{}]. Calculated sum of outputs from previous transaction [{}] does not match given amount [{}].",
          txid, calculatedAmount, amount)
      } else {
        self ! ReceivedPaymentNotification(tx.txid, listOfTxOuts.map(_.n), senderAddress, calculatedAmount)
      }
    }
  }

  // helper method for request handling, to be called from handleRequest
  // this variant is for commands without a response
  def request(request: JsonRequest) {
    log.info("Json Request\n{}", Json.prettyPrint(Json.toJson(request)))
    btcWalletClient.send(Json.toJson(request).toString())
  }

  // helper method for request handling, to be called from handleRequest
  // this variant is for commands with a response
  def request(request: JsonRequest, resultFunc: JsValue => AnyRef) {
    log.info("Json Request\n{}", Json.prettyPrint(Json.toJson(request)))
    val p = Promise[AnyRef]()
    val f = p.future
    rpcRequests += request.id ->(p, resultFunc)
    btcWalletClient.send(Json.toJson(request).toString())

    context.system.scheduler.scheduleOnce(5 seconds) {
      p tryFailure new TimeoutException("Timeout: btcwallet did not respond in time.")
      self ! RemoveRequest(request.id)
    }

    pipe(f) to sender
  }

  class WebSocketBtcWalletClient(serverUri: URI, protocolDraft: Draft, httpHeaders: Map[String, String], connectTimeout: Int)
    extends WebSocketClient(serverUri, protocolDraft, httpHeaders, connectTimeout) {
    override def onMessage(jsonMessage: String): Unit = {
      Json.fromJson[JsonMessage](Json.parse(jsonMessage)) foreach {
        case notification: JsonNotification =>
          log.info("Json Notification\n{}", Json.prettyPrint(Json.parse(jsonMessage)))
          handleJsonNotification.applyOrElse(notification, unhandled)
        case response: JsonResponse =>
          log.info("Json Response\n{}", Json.prettyPrint(Json.parse(jsonMessage)))
          self ! CompleteRequest(response)
        case _ => // ignore
      }
    }

    override def onOpen(handshakeData: ServerHandshake) {}

    override def onClose(code: Int, reason: String, remote: Boolean) {
      self ! Disconnected
    }

    override def onError(ex: Exception) {}
  }
}