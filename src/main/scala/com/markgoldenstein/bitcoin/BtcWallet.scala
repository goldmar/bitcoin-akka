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

import scala.concurrent.Future
import com.markgoldenstein.bitcoin.messages.json.{SignedTransaction, UnspentTransaction, RawTransaction}

trait BtcWallet {
  def createRawTransaction(inputs: Seq[(String, BigDecimal)], receivers: Seq[(String, BigDecimal)]): Future[String]

  def getNewAddress(): Future[String]

  def getRawTransaction(transactionHash: String): Future[RawTransaction]

  def listUnspentTransactions(minConfirmations: BigDecimal = 1, maxConfirmations: BigDecimal = 999999): Future[Seq[UnspentTransaction]]

  def sendRawTransaction(signedTransaction: String): Future[String]

  def signRawTransaction(transaction: String): Future[SignedTransaction]

  def walletPassPhrase(walletPass: String, timeout: BigDecimal): Unit
}
