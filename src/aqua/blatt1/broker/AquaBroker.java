package aqua.blatt1.broker;

import aqua.blatt1.client.AquaClient;

import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.AlreadyBoundException;

public interface AquaBroker extends Remote {
    void register(AquaClient aquaClient) throws RemoteException, AlreadyBoundException;

    void deregister(String id) throws RemoteException, NotBoundException;

    void handleNameResolutionRequest(String tankId, String id, AquaClient aquaClient) throws RemoteException;
}
