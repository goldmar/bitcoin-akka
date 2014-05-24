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
import akka.actor.{Actor, ActorLogging, ReceiveTimeout, Status}
import akka.pattern.pipe
import play.api.libs.json._
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.java_websocket.drafts.{Draft, Draft_17}
import com.markgoldenstein.bitcoin.messages.actor._
import com.markgoldenstein.bitcoin.messages.json._
import JsonImplicits._

class BtcWalletActor(websocketUri: String, rpcUser: String, rpcPass: String,
                     keyStoreFile: String, keyStorePass: String,
                     onConnect: () => Unit, handleNotification: Actor.Receive,
                     timeoutDuration: FiniteDuration) extends Actor with ActorLogging {

  implicit val executionContext = context.dispatcher

  // this HashMap maps JSON RPC request IDs to the corresponding response promises
  // and a function that converts the JSON RPC response to the final actor response
  val rpcRequests = mutable.HashMap.empty[String, (Promise[AnyRef], JsValue => AnyRef)]

  // start actor in connecting mode
  override def receive = connecting

  // try to connect to btcwallet
  def connecting: Actor.Receive = {
    case Connected =>
      context.become(active)
      onConnect()
    case ReceiveTimeout => tryToConnect()
    case _: RequestMessage =>
      val message = "Cannot process request: no connection to btcwallet."
      sender ! Status.Failure(new IllegalStateException(message))
      log.error(message)
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

    case m: RequestMessage =>
      m match {
        case m: WalletPassPhrase =>
          // do not log wallet pass
          log.debug("Actor Request\n{}", m.copy(walletPass = "hidden").treeString)
        case _ =>
          log.debug("Actor Request\n{}", m.treeString)
      }

      m match {
        case CreateRawTransaction(inputs, receivers) =>
          val resultFunc = (result: JsValue) => result.as[String]
          request(JsonMessage.createRawTransaction(inputs, receivers), resultFunc)
        case GetNewAddress =>
          val resultFunc = (result: JsValue) => result.as[String]
          request(JsonMessage.getNewAddress, resultFunc)
        case GetRawTransaction(transactionHash) =>
          val resultFunc = (result: JsValue) => Json.fromJson[RawTransaction](result).get
          request(JsonMessage.getRawTransaction(transactionHash), resultFunc)
        case ListUnspentTransactions(minConfirmations, maxConfirmations) =>
          val resultFunc = (result: JsValue) => Json.fromJson[Seq[UnspentTransaction]](result).get
          request(JsonMessage.listUnspentTransactions(minConfirmations, maxConfirmations), resultFunc)
        case SendRawTransaction(signedTransaction) =>
          val resultFunc = (result: JsValue) => result.as[String]
          request(JsonMessage.sendRawTransaction(signedTransaction), resultFunc)
        case SignRawTransaction(transaction) =>
          val resultFunc = (result: JsValue) => Json.fromJson[SignedTransaction](result).get
          request(JsonMessage.signRawTransaction(transaction), resultFunc)
        case WalletPassPhrase(walletPass, timeout) =>
          request(JsonMessage.walletPassPhrase(walletPass, timeout))
      }

    case RemoveRequest(id) => rpcRequests -= id
    case Disconnected =>
      log.info("Connection to btcwallet closed.")
      context.become(connecting)
      tryToConnect()

    case m: NotificationMessage =>
      log.debug("Actor Notification\n{}", m.treeString)
      handleNotification.applyOrElse(m, unhandled)
  }

  def handleJsonNotification: PartialFunction[JsonNotification, Unit] = {
    // handle a new transaction notification: call processTransaction
    case JsonNotification(_, "newtx", params) =>
      Json.fromJson[TransactionNotification](params(1)).map(txNtfn =>
        if (txNtfn.category == "receive")
          self ! ReceivedPayment(txNtfn.txid, txNtfn.address, txNtfn.amount, txNtfn.confirmations))
    case _ => // ignore
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
    if (request.method == "walletpassphrase") {
      // do not log wallet pass
      log.info("Json Request\n{}", Json.prettyPrint(Json.toJson(request.copy(params = Json.arr("hidden")))))
    } else {
      log.info("Json Request\n{}", Json.prettyPrint(Json.toJson(request)))
    }
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
      context.system.scheduler.scheduleOnce(timeoutDuration, self, ReceiveTimeout)
    }
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