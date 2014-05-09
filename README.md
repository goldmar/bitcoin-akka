bitcoin-akka
============

bitcoin-akka demonstrates the processing of bitcoin transactions using the Actor model.  It is written in Scala, and is based on the [akka](http://akka.io) library, [btcd](https://github.com/conformal/btcd), and [btcwallet](https://github.com/conformal/btcwallet).

Usually, it's inconvenient to use websocket or json APIs directly.  bitcoin-akka is an abstraction to btcwallet's websocket API.  You can talk to bitcoin-akka using Actor messages and receive Futures in return.  Those messages are then forwarded to btcwallet using its websocket API.  When a response is available the Future is completed.

The project contains an example in which transactions are processed by sending the bitcoins back to the sender address.  The outputs of the incoming transactions are used as outputs for the corresponding outgoing transactions. Therefore, there is no need to wait for confirmations.

License
-------

This project is licensed under the terms of the GPL v2 license.

Usage
-----

* Install [btcd](https://github.com/conformal/btcd), [btcwallet](https://github.com/conformal/btcwallet), and [btcgui](https://github.com/conformal/btcgui).
* Configure btcd and btcwallet to use testnet and enable rpc support.
* Initialize a wallet and set the wallet passphrase using btcgui.
* Clone the Java-WebSocket repository: `git clone https://github.com/TooTallNate/Java-WebSocket.git`.  Install the development snapshot to your local maven repository: `mvn install`.
* Clone this repository: `git clone https://github.com/goldmar/bitcoin-akka.git`.
* Configure your passwords in src/main/resources/application.conf.
* Import your btcwallet certificate to keystore.jks: `keytool -keystore.jks -import -file ~/.btcwallet/rpc.cert -alias btcwallet.cert`.
* Change `watchedAddresses` in src/main/scala/example/BtcWalletActorImpl.scala to include the addresses in your btcwallet.
* Run the project: `sbt run`.
* Send some testnet coins to one of your addresses, and watch how they are automatically returned to the sender address.
