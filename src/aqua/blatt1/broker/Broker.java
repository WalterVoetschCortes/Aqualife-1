package aqua.blatt1.broker;

import aqua.blatt1.client.AquaClient;
import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.Properties;
import aqua.blatt1.common.msgtypes.*;
import aqua.blatt1.endpoint.SecureEndpoint;
import messaging.Endpoint;
import messaging.Message;


import javax.crypto.NoSuchPaddingException;
import javax.swing.*;
import java.net.InetSocketAddress;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.rmi.*;

public class Broker implements AquaBroker{

    int NUMTHREADS = 5;
    int count;
    int leaseLength = 10;
    volatile boolean stopRequested = false;
    SecureEndpoint endpoint;
    ClientCollection client = new ClientCollection();
    ExecutorService executor = Executors.newFixedThreadPool(NUMTHREADS);
    ReadWriteLock lock = new ReentrantReadWriteLock();
    Timer timer = new Timer();

    public Broker() {
        endpoint = new SecureEndpoint(Properties.PORT);
        client = new ClientCollection();
        executor = Executors.newFixedThreadPool(NUMTHREADS);
    }


    public static void main(String[] args) throws RemoteException, AlreadyBoundException{
        Broker broker = new Broker();
        Registry registry = LocateRegistry.createRegistry(Registry.REGISTRY_PORT);
        AquaBroker stub = (AquaBroker) UnicastRemoteObject.exportObject(broker, 0);
        registry.bind(Properties.BROKER_NAME, stub);
    }


    public void register(AquaClient aquaClient) throws RemoteException, AlreadyBoundException{
        int ClientIndex = client.indexOf(aquaClient);
        Date timestamp = new Date();
        String id;

        if (ClientIndex == -1) {
            id = "tank" + (count++);

            Registry registry = LocateRegistry.getRegistry(Registry.REGISTRY_PORT);
            registry.bind(id, aquaClient);

            client.add(id, aquaClient, timestamp);

            int newTankAddressIndex = client.indexOf(aquaClient);
            AquaClient leftNeighbor = (AquaClient) client.getLeftNeighorOf(newTankAddressIndex);
            AquaClient rightNeighbor = (AquaClient) client.getRightNeighorOf(newTankAddressIndex);

            AquaClient leftOfLeftNeighbor = (AquaClient) client.getLeftNeighorOf(client.indexOf(leftNeighbor));
            AquaClient rightOfRightNeighbor = (AquaClient) client.getRightNeighorOf(client.indexOf(rightNeighbor));

            if (client.size() == 1) {
                aquaClient.updateNeighbors(aquaClient, aquaClient);
                aquaClient.receiveToken(new Token());
            } else {
                aquaClient.updateNeighbors(leftNeighbor,rightNeighbor);
                leftNeighbor.updateNeighbors(leftOfLeftNeighbor, aquaClient);
                rightNeighbor.updateNeighbors(aquaClient, rightOfRightNeighbor);
            }
        } else {
            System.out.println("prüfe ob bereits registriert");
            int index = client.indexOf(aquaClient);
            client.setTimestamp(index, timestamp);
            id = client.getId(index);
        }
        aquaClient.onRegistration(id, leaseLength);
    }

    public void deregister(String id) throws RemoteException, NotBoundException{
        Registry registry = LocateRegistry.getRegistry(Registry.REGISTRY_PORT);
        registry.unbind(id);

        AquaClient leftNeighborAddress = (AquaClient) client.getLeftNeighorOf(client.indexOf(id));
        AquaClient rightNeighborAddress = (AquaClient) client.getRightNeighorOf(client.indexOf(id));

        int leftNeighborIndex = client.indexOf(client.getLeftNeighorOf(client.indexOf(id)));
        int rightNeighborIndex = client.indexOf(client.getRightNeighorOf(client.indexOf(id)));

        AquaClient leftOfLeftNeighbor = (AquaClient) client.getLeftNeighorOf(leftNeighborIndex);
        AquaClient rightOfRightNeighbor = (AquaClient) client.getRightNeighorOf(rightNeighborIndex);

        if (client.size() == 2) {
            leftNeighborAddress.updateNeighbors(leftNeighborAddress, leftNeighborAddress);
        } else {
            leftNeighborAddress.updateNeighbors(leftOfLeftNeighbor, rightNeighborAddress);
            rightNeighborAddress.updateNeighbors(leftNeighborAddress, rightOfRightNeighbor);
        }
    }

    private void handOffFish(HandoffRequest handoffRequest, InetSocketAddress inetSocketAddress) {
        int index = client.indexOf(inetSocketAddress);
        FishModel fishModel = handoffRequest.getFish();
        Direction direction = fishModel.getDirection();

        InetSocketAddress neighborReceiver;
        if (direction == Direction.LEFT) {
            neighborReceiver = (InetSocketAddress) client.getLeftNeighorOf(index);
        } else {
            neighborReceiver = (InetSocketAddress) client.getRightNeighorOf(index);
        }

        endpoint.send(neighborReceiver, handoffRequest);
    }

    public void handleNameResolutionRequest(String tankId, String id, AquaClient aquaClient) throws RemoteException {
        int indexOf = client.indexOf(tankId);
        AquaClient tankAddress = (AquaClient) client.getClient(indexOf);
        aquaClient.handleNameResolutionResponse(tankAddress, id, aquaClient);
    }


}
