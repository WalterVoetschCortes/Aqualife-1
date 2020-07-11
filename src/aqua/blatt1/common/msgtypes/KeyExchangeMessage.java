package aqua.blatt1.common.msgtypes;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.security.PublicKey;

public class KeyExchangeMessage implements Serializable {
    private final PublicKey publicKey;
    public KeyExchangeMessage(PublicKey publicKey) {
        this.publicKey=publicKey;
    }

    public PublicKey getKey() {
        return publicKey;
    }
}
