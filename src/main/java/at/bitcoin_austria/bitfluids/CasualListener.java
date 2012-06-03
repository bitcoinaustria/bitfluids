/*
 * Copyright 2012 Bitcoin Austria
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package at.bitcoin_austria.bitfluids;

import com.google.bitcoin.core.*;
import com.google.bitcoin.discovery.PeerDiscovery;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.BlockStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.math.BigInteger;
import java.util.*;

/**
 * @author schilly
 */
class CasualListener extends Thread {
    private final Environment env;
    private final File extFilesDir;
    private final TxNotifier txNotifier;
    private final List<Address> lookingFor;

//    private BlockChain chain;
    private PeerGroup peerGroup;

    final static Logger LOGGER = LoggerFactory.getLogger(CasualListener.class);
    //weakhashSet would be sufficient, but there is no such thing
    final WeakHashMap<Sha256Hash, Boolean> isInteresting = new WeakHashMap<Sha256Hash, Boolean>();

    //we keep strong refs to these to surely not double-check
    final Set<Sha256Hash> interestingHashes = new HashSet<Sha256Hash>();

    private final AbstractPeerEventListener txProcessListener;

    public CasualListener(Environment env, File extFilesDir, TxNotifier txNotifier) {
        this.env = env;
        this.extFilesDir = extFilesDir;
        this.txNotifier = txNotifier;
        lookingFor = Arrays.asList(env.getKey200(), env.getKey150());
        txProcessListener = new AbstractPeerEventListener() {
            @Override
            public void onTransaction(Peer peer, Transaction t) {
                processTransaction(t);
            }
        };

    }

    @Override
    public void run() {
        BlockStore blockStore;
        try {
            // we store this on the SD card, in
            // /Android/data/at.bitcoin_austria..../
            // there will be an exception wheren there is no
            // "getExternalFilesDir()". also, never store sensitive info
            // there!!!
//            blockStore = new BoundedOverheadBlockStore(env.getNetworkParams(), new File(extFilesDir, env.getBlockChainFilename()));
//            chain = new BlockChain(env.getNetworkParams(), blockStore);
            peerGroup = new PeerGroup(env.getNetworkParams(), new BlockChain(env.getNetworkParams(),new DummyBlockStore(env)));
            //connect to localhost for testing for fast block download
          /*  try {
                peerGroup.addAddress(new PeerAddress(InetAddress.getLocalHost()));
                peerGroup.addAddress(new PeerAddress(InetAddress.getByName("192.168.0.199")));
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }*/
            peerGroup.setMaxConnections(8);
            peerGroup.addEventListener(new AbstractPeerEventListener() {

                @Override
                public void onPeerConnected(Peer peer, int peerCount) {
                    peer.addEventListener(txProcessListener);
                }

                @Override
                public void onPeerDisconnected(Peer peer, int peerCount) {
                    peer.removeEventListener(txProcessListener);
                }
            });

            List<PeerDiscovery> discoveries = env.getPeerDiscoveries();
            for (PeerDiscovery discovery : discoveries) {
                peerGroup.addPeerDiscovery(discovery);
            }
            //in our case we are only interested in future transactions.
            peerGroup.setFastCatchupTimeSecs(new Date().getTime() / 1000);
            peerGroup.start();
//            peerGroup.downloadBlockChain();
        } catch (BlockStoreException e) {
            throw new RuntimeException(e);
        }
    }

    //synchronized to avoid double incoming TX
    private synchronized void processTransaction(Transaction t) {
        Sha256Hash transactionHash = t.getHash();
        //have seen it, but maybe already forgotten
        if (isInteresting.get(transactionHash) != null) {
            return;
        }
        //double-check maybe it was already collected and evicted from weakhashmap
        if (interestingHashes.contains(transactionHash)) {
            return;
        }
        boolean wasInteresting = analyzeTransaction(t);
        if (wasInteresting) {
            interestingHashes.add(transactionHash);
        }
        isInteresting.put(transactionHash, wasInteresting);
    }

    private boolean analyzeTransaction(Transaction t) {
        try {
            List<TransactionOutput> outputs = t.getOutputs();
            LOGGER.info("checking tx with output to:" + outputs);
            boolean wasInteresting = false;
            for (TransactionOutput output : outputs) {
                final Address candidateAddress = extractAddress(t, output);
                if (candidateAddress != null) {
                    byte[] candidate = candidateAddress.getHash160();
                    for (Address address : lookingFor) {
                        if (Arrays.equals(address.getHash160(), candidate)) {
                            BigInteger satoshis = output.getValue();
                            LOGGER.debug("detected relevant transaction!" + satoshis);
                            wasInteresting = true;
                            txNotifier.onValue(satoshis, address);
                        }
                    }
                }
            }
            return wasInteresting;
        } catch (RuntimeException e) {
            LOGGER.error("there was an error while looking at a transaction", e);
            return false;
        }
    }

    @Nullable
    private static Address extractAddress(Transaction t, TransactionOutput output) {
        Address candidateAddress = null;
        try {
            Script scriptPubKey = output.getScriptPubKey();
            candidateAddress = scriptPubKey.getToAddress();
        } catch (ScriptException e) {
            LOGGER.info("invalid script in TX id " + t.getHashAsString());
        }
        return candidateAddress;
    }

    public String getStatus() {
        if (peerGroup != null) {
            return " peers:" + peerGroup.getConnectedPeers().size();
        } else {
            return "not yet initialized";
        }
    }

    private class DummyBlockStore implements BlockStore {
        final Environment env;

        private DummyBlockStore(Environment env) {
            this.env = env;
        }

        @Override
        public void put(StoredBlock block) throws BlockStoreException {
            throw new UnsupportedOperationException("i'm too dumb!");
            //don't worry bud...
        }

        @Override
        public StoredBlock get(Sha256Hash hash) throws BlockStoreException {
          throw new UnsupportedOperationException("i'm too dumb!");
        }

        @Override
        public StoredBlock getChainHead() throws BlockStoreException {
            //always reporting genesisBlock as Chain Head...
            Block genesisBlock = env.getNetworkParams().genesisBlock;
            try {
                return new StoredBlock(genesisBlock, genesisBlock.getWork(), 0);
            } catch (VerificationException e) {
                throw new BlockStoreException(e);
            }
        }

        @Override
        public void setChainHead(StoredBlock chainHead) throws BlockStoreException {
            throw new UnsupportedOperationException("i'm too dumb!");
        }

        @Override
        public void close() throws BlockStoreException {
            throw new UnsupportedOperationException("i'm too dumb!");
        }
    }
}
