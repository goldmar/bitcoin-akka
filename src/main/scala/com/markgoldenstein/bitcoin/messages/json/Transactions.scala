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

package com.markgoldenstein.bitcoin.messages.json

import com.markgoldenstein.bitcoin.messages.actor._
import com.markgoldenstein.bitcoin.messages.json._

case class TransactionNotification(txid: String, account: String, address: String, category: String, amount: BigDecimal, confirmations: BigDecimal, timereceived: BigDecimal)


case class GetRawTransactionResponse(hex: String, txid: String, version: BigDecimal, locktime: BigDecimal, vin: Seq[VIn], vout: Seq[VOut]) extends ResponseMessage

case class VIn(txid: String, vout: Int, scriptSig: ScriptSig, sequence: BigDecimal)

case class VOut(value: BigDecimal, n: BigDecimal, scriptPubKey: ScriptPubKey)

case class ScriptSig(asm: String, hex: String)

case class ScriptPubKey(asm: String, hex: String, reqSigs: BigDecimal, `type`: String, addresses: Seq[String])


case class UnspentTransaction(txid: String, account: String, address: String, amount: BigDecimal, confirmations: BigDecimal)

case class SignedTransaction(hex: String, complete: Boolean)