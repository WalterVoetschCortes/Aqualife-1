package aqua.blatt1.endpoint;

import messaging.Endpoint;
import messaging.Message;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Cipher;
import java.io.*;
import java.net.*;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;

public class SecureEndpoint extends Endpoint {
    final String keyMaterial = "CAFEBABECAFEBABE";
    private final DatagramSocket socket;
    SecretKeySpec key;
    Cipher cipherEncrypt = Cipher.getInstance("AES");
    Cipher cipherDecrypt = Cipher.getInstance("AES/ECB/NoPadding");
    private byte[] keyBytes;

    public SecureEndpoint() throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
        this.key = generateKey();
        this.cipherEncrypt.init(Cipher.ENCRYPT_MODE, key);
        this.cipherDecrypt.init(Cipher.DECRYPT_MODE, key);
        try {
            this.socket = new DatagramSocket();
        } catch (SocketException var2) {
            throw new RuntimeException(var2);
        }
    }

    public SecureEndpoint(int port) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException{
        this.key = generateKey();
        this.cipherEncrypt.init(Cipher.ENCRYPT_MODE, key);
        this.cipherDecrypt.init(Cipher.DECRYPT_MODE, key);
        try {
            this.socket = new DatagramSocket(port);
        } catch (SocketException var3) {
            throw new RuntimeException(var3);
        }
    }

    public void send(InetSocketAddress receiver, Serializable payload) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(payload);
            byte[] bytes = baos.toByteArray();
            byte[] encryptData = cipherEncrypt.doFinal(bytes);
            DatagramPacket datagram = new DatagramPacket(encryptData, encryptData.length, receiver);
            this.socket.send(datagram);
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

        try {
            byte[] decryptData = cipherDecrypt.doFinal(datagram.getData());
            datagram.setData(decryptData);
        } catch (final BadPaddingException | IllegalBlockSizeException e) {
            e.printStackTrace();
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
