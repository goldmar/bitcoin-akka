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

import com.markgoldenstein.bitcoin.messages.actor._
import com.markgoldenstein.bitcoin.messages.json._

case class TransactionNotification(txid: String, account: String, address: String, category: String, amount: BigDecimal, confirmations: BigDecimal, timereceived: BigDecimal)


case class RawTransaction(hex: String, txid: String, version: BigDecimal, locktime: BigDecimal, vin: Seq[VIn], vout: Seq[VOut])

case class VIn(txid: String, vout: Int, scriptSig: ScriptSig, sequence: BigDecimal)

case class VOut(value: BigDecimal, n: BigDecimal, scriptPubKey: ScriptPubKey)

case class ScriptSig(asm: String, hex: String)

case class ScriptPubKey(asm: String, hex: String, reqSigs: BigDecimal, `type`: String, addresses: Seq[String])


case class UnspentTransaction(txid: String, account: String, address: String, amount: BigDecimal, confirmations: BigDecimal)

case class SignedTransaction(hex: String, complete: Boolean)