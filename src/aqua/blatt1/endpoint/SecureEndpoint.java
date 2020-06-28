package aqua.blatt1.endpoint;

import aqua.blatt1.common.msgtypes.KeyExchangeMessage;
import aqua.blatt1.common.msgtypes.LocationUpdate;
import messaging.Endpoint;
import messaging.Message;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Cipher;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.HashMap;

public class SecureEndpoint extends Endpoint {
    final String keyMaterial = "CAFEBABECAFEBABE";
    private final DatagramSocket socket;
    SecretKeySpec key;
    Cipher cipherEncrypt = Cipher.getInstance("RSA");
    Cipher cipherDecrypt = Cipher.getInstance("RSA");
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    HashMap<InetSocketAddress, PublicKey> publicKeyHashMap = new HashMap<>();
    KeyPair pair;
    PrivateKey privKey;
    PublicKey publicKey;

    private byte[] keyBytes;

    public SecureEndpoint() throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
        keyPairGenerator.initialize(2048);

        //Generating the pair of keys
        pair = keyPairGenerator.generateKeyPair();

        //Getting the private key from the key pair
        privKey = pair.getPrivate();

        //Getting the public key from the key pair
        publicKey = pair.getPublic();

        this.cipherEncrypt.init(Cipher.ENCRYPT_MODE, publicKey);
        this.cipherDecrypt.init(Cipher.DECRYPT_MODE, privKey);
        try {
            this.socket = new DatagramSocket();
        } catch (SocketException var2) {
            throw new RuntimeException(var2);
        }
    }

    public SecureEndpoint(int port) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException{
        keyPairGenerator.initialize(2048);

        //Generating the pair of keys
        pair = keyPairGenerator.generateKeyPair();

        //Getting the private key from the key pair
        privKey = pair.getPrivate();

        //Getting the public key from the key pair
        publicKey = pair.getPublic();

        this.cipherEncrypt.init(Cipher.ENCRYPT_MODE, publicKey);
        this.cipherDecrypt.init(Cipher.DECRYPT_MODE, privKey);
        try {
            this.socket = new DatagramSocket(port);
        } catch (SocketException var3) {
            throw new RuntimeException(var3);
        }
    }

    public void send(InetSocketAddress receiver, Serializable payload) {
        try {
            if (publicKeyHashMap.containsKey(receiver)) {
                System.out.println(publicKeyHashMap.containsKey(receiver));
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(payload);
                byte[] bytes = baos.toByteArray();
                byte[] encryptData = cipherEncrypt.doFinal(bytes);
                DatagramPacket datagram = new DatagramPacket(encryptData, encryptData.length, receiver);
                this.socket.send(datagram);
            } else {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(new KeyExchangeMessage(publicKey));
                byte[] bytes = baos.toByteArray();
                DatagramPacket datagram = new DatagramPacket(bytes, bytes.length, receiver);
                System.out.println("Sende public Key");
                this.socket.send(datagram);
            }

        } catch (Exception var7) {
            throw new RuntimeException(var7);
        }
    }
    private Message readDatagram(DatagramPacket datagram) {
        try {
            ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(datagram.getData()));
            return new Message((Serializable)ois.readObject(), (InetSocketAddress)datagram.getSocketAddress());
        } catch (Exception var3) {
            throw new RuntimeException(var3);
        }
    }

    public Message blockingReceive() {
        DatagramPacket datagram = new DatagramPacket(new byte[1024], 1024);

        try {
            this.socket.receive(datagram);
        } catch (Exception var3) {
            throw new RuntimeException(var3);
        }
        //System.out.println(readDatagram(datagram).getPayload());
        if (publicKeyHashMap.containsKey(readDatagram(datagram).getSender())) {
            try {
                byte[] decryptData = cipherDecrypt.doFinal(datagram.getData());
                datagram.setData(decryptData);
            } catch (final BadPaddingException | IllegalBlockSizeException e) {
                e.printStackTrace();
            }
        } else if (readDatagram(datagram).getPayload() instanceof KeyExchangeMessage) {
            try {
                InetSocketAddress receiver = readDatagram(datagram).getSender();
                PublicKey publicKey = ((KeyExchangeMessage) readDatagram(datagram).getPayload()).getPublicKey();
                publicKeyHashMap.put(receiver, publicKey);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(new KeyExchangeMessage(publicKey));
                byte[] bytes = baos.toByteArray();
                DatagramPacket datagram1 = new DatagramPacket(bytes, bytes.length, receiver);
                System.out.println("Sende public Key zurück");
                this.socket.send(datagram1);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return this.readDatagram(datagram);
    }

    public Message nonBlockingReceive() {
        DatagramPacket datagram = new DatagramPacket(new byte[1024], 1024);

        try {
            this.socket.setSoTimeout(1);
        } catch (SocketException var7) {
            throw new RuntimeException(var7);
        }

        boolean timeoutExpired;
        try {
            this.socket.receive(datagram);
            timeoutExpired = false;
        } catch (SocketTimeoutException var5) {
            timeoutExpired = true;
        } catch (IOException var6) {
            throw new RuntimeException(var6);
        }

        try {
            this.socket.setSoTimeout(0);
        } catch (SocketException var4) {
            throw new RuntimeException(var4);
        }
        try {
            byte[] decryptData = cipherDecrypt.doFinal(datagram.getData());
            datagram.setData(decryptData);
        } catch (final BadPaddingException | IllegalBlockSizeException e) {
            e.printStackTrace();
        }


        return timeoutExpired ? null : this.readDatagram(datagram);
    }

    private SecretKeySpec generateKey() throws InvalidKeyException{
        SecretKeySpec key = new SecretKeySpec(keyMaterial.getBytes(), "AES");
        return key;
    }
}
