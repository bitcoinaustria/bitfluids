# Bitfluids

What you might need
---------------------------
* maven3
* android sdk 2.2 (because of the tablet we use)
* eclipse indigio 3.7.2
* ADT plugin
* mvn plugin
* git (obviously)

Steps to compile and run
----------------------------
* git clone bitfluids
* git clone https://code.google.com/p/bitcoinj/
* cd bitcoinj
* mvn install
* eclipse: import existing maven project
* eclipse: add android nature
* run?

Some common errors
-----------------------------
* gc overhead limit exceeded
    * echo “-Xms512m \n -Xmx1024m” >> eclipse.conf
* dx error: IllegalArgumentException: already added: slf4j-logger
    * the logger was included twice in the maven build 

Links
------
* http://mybitcoin.at/wiki/Bitfluids [german]
* [schildbach wallet](http://code.google.com/p/bitcoin-wallet/)
* [bitcoinj](https://code.google.com/p/bitcoinj)
* [bitcoin-austria](http://www.bitcoin-austria.at/)

