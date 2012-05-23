package at.bitcoin_austria.bitfluids;

import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.discovery.*;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

/**
 * User: Andreas
 */
public enum Environment {

    PROD(NetworkParameters.prodNet(), "bitfluids.wallet", "bitfluids.blocks") {
        @Override
        public ECKey getKey200() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public ECKey getKey150() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public List<PeerDiscovery> getPeerDiscoveries() {
            return Arrays.asList(
                    new IrcDiscovery("#bitcoin"),
                    new DnsDiscovery(getNetworkParams()),
                    new SeedPeers(getNetworkParams()));

        }
    }, TEST(makeTestNet(), "bitfluids.walletTEST", "bitfluids.blocksTEST") {
        @Override
        public ECKey getKey200() {
            return makePubKey(APETERSSON_2_EUR_PUBKEY, getNetworkParams());
        }

        @Override
        public ECKey getKey150() {
            return makePubKey(APETERSSON_1_50_PUBKEY, getNetworkParams());
        }

        @Override
        public List<PeerDiscovery> getPeerDiscoveries() {
            /*  PeerDiscovery peerDiscovery = new PeerDiscovery() {
                @Override
                public InetSocketAddress[] getPeers() throws PeerDiscoveryException {
                    InetSocketAddress[] ret = new InetSocketAddress[1];
                    ret[0] = new InetSocketAddress("192.168.0.199", getNetworkParams().port);
                    return ret;
                }

                @Override
                public void shutdown() {
                    //To change body of implemented methods use File | Settings | File Templates.
                }
            };
            return Arrays.asList(peerDiscovery);*/
             return Arrays.asList(
          new IrcDiscovery("#bitcoinTEST")
          , new DnsDiscovery(getNetworkParams())
          , new SeedPeers(getNetworkParams()));
        }
    };

    private static NetworkParameters makeTestNet() {
        NetworkParameters testNet = NetworkParameters.testNet();
        return testNet;
    }

    public static final String APETERSSON_2_EUR_PUBKEY = "n4d5cP2u1cmBrYEnr7iWVMccnsWyzkLn3T";
    public static final String APETERSSON_1_50_PUBKEY = "mngpA1D7a2xD9M3KP9VjjNrryNp9bWERGA";

    private final NetworkParameters networkParams;
    private final String walletFilename;
    private final String blockChainFilename;

    private static ECKey makePubKey(String pubKey, NetworkParameters params) {
        try {
            return Utils.watchKeyFromString(pubKey, params);
        } catch (AddressFormatException e) {
            throw new RuntimeException(e);
        }
    }

    private Environment(NetworkParameters networkParams, String walletFilename, String blockChainFilename) {
        this.networkParams = networkParams;
        this.walletFilename = walletFilename;
        this.blockChainFilename = blockChainFilename;
    }

    public NetworkParameters getNetworkParams() {
        return networkParams;
    }


    public abstract ECKey getKey200();

    public abstract ECKey getKey150();

    public String getWalletFilename() {
        return walletFilename;
    }

    public abstract List<PeerDiscovery> getPeerDiscoveries();

    public String getBlockChainFilename() {
        return blockChainFilename;
    }
}
