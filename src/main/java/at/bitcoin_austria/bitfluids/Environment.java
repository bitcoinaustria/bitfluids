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

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.discovery.DnsDiscovery;
import com.google.bitcoin.discovery.IrcDiscovery;
import com.google.bitcoin.discovery.PeerDiscovery;
import com.google.bitcoin.discovery.SeedPeers;

import java.util.Arrays;
import java.util.List;

/**
 * @author apetersson
 */
public enum Environment {

    PROD(NetworkParameters.prodNet(), "bitfluids.blocks") {
        @Override
        public Address getKey200() {
            return makePubKey(MTGOX_200_EUR);
        }

        @Override
        public Address getKey150() {
            return makePubKey(MTGOX_150_EUR);
        }

        @Override
        public List<PeerDiscovery> getPeerDiscoveries() {
            return Arrays.asList(
                    new DnsDiscovery(getNetworkParams()),
                    new IrcDiscovery("#bitcoin"),
                    new SeedPeers(getNetworkParams()));

        }
    }, TEST(makeTestNet(), "bitfluids.blocksTEST") {
        @Override
        public Address getKey200() {
            return makePubKey(APETERSSON_2_EUR_PUBKEY);
        }

        @Override
        public Address getKey150() {
            return makePubKey(APETERSSON_1_50_PUBKEY);
        }

        @Override
        public List<PeerDiscovery> getPeerDiscoveries() {
            //todo add support for testnet3 once its in bitcoinj
            return Arrays.asList(
                    new IrcDiscovery("#bitcoinTEST")
                    , new DnsDiscovery(getNetworkParams())
                    , new SeedPeers(getNetworkParams()));
        }
    };

    private static NetworkParameters makeTestNet() {
        return NetworkParameters.testNet();
    }

    private static final String MTGOX_200_EUR = "184bebdTa792ueyzQxUseXTpvAP5wXNTq1";
    private static final String MTGOX_150_EUR = "1JLMzJuRZGFm4hzNuWRREZFbE1LhJtvFk";

//    private static final String MTGOX_200_EUR = "1dice8EMZmqKvrGE4Qc9bUFf9PX3xaYDp";
//    private static final String MTGOX_150_EUR = "1dice97ECuByXAvqXpaYzSaQuPVvrtmz6";

    private static final String APETERSSON_2_EUR_PUBKEY = "n4d5cP2u1cmBrYEnr7iWVMccnsWyzkLn3T";
    private static final String APETERSSON_1_50_PUBKEY = "mngpA1D7a2xD9M3KP9VjjNrryNp9bWERGA";

    private final NetworkParameters networkParams;
    private final String blockChainFilename;

    Address makePubKey(String address) {
        try {
            return new Address(getNetworkParams(), address);
        } catch (AddressFormatException e) {
            throw new RuntimeException(e);
        }
    }

    private Environment(NetworkParameters networkParams, String blockChainFilename) {
        this.networkParams = networkParams;
        this.blockChainFilename = blockChainFilename;
    }

    public NetworkParameters getNetworkParams() {
        return networkParams;
    }


    public abstract Address getKey200();

    public abstract Address getKey150();

    public abstract List<PeerDiscovery> getPeerDiscoveries();

    public String getBlockChainFilename() {
        return blockChainFilename;
    }
}
