package aqua.blatt1.broker;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.*;
import messaging.Endpoint;
import messaging.Message;

import javax.swing.*;
import java.net.InetSocketAddress;
import java.sql.SQLOutput;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
public class Broker {

    int NUMTHREADS = 5;
    int count;
    volatile boolean stopRequested = false;
    Endpoint endpoint = new Endpoint(4711);
    ClientCollection client = new ClientCollection();
    ExecutorService executor = Executors.newFixedThreadPool(NUMTHREADS);
    ReadWriteLock lock = new ReentrantReadWriteLock();

    private class BrokerTask {
        public void brokerTask (Message msg) {
            if (msg.getPayload() instanceof RegisterRequest) {
               synchronized (client) {register(msg);}
            }

            if (msg.getPayload() instanceof DeregisterRequest) {
                synchronized (client) {deregister(msg);}
            }

            //lock.writeLock().lock();
            if (msg.getPayload() instanceof HandoffRequest) {
                lock.writeLock().lock();
                HandoffRequest handoffRequest = (HandoffRequest) msg.getPayload();
                InetSocketAddress inetSocketAddress = msg.getSender();
                handOffFish(handoffRequest,inetSocketAddress);
                lock.writeLock().unlock();
            }
            if (msg.getPayload() instanceof PoisonPill) {
                System.exit(0);
            }
        }
    }
    public static void main(String[] args) {
        Broker broker = new Broker();
        broker.broker();
    }

    public void broker(){

        executor.execute(new Runnable() {
            @Override
            public void run() {
                JOptionPane.showMessageDialog(null,"Press OK button to stop server");
                stopRequested=true;
            }
        });

        while( !stopRequested ) {
            Message msg = endpoint.blockingReceive();
            BrokerTask brokerTask = new BrokerTask();
            executor.execute(() -> brokerTask.brokerTask(msg));
        }
        executor.shutdown();
    }

    private void register(Message msg) {
        /*InetSocketAddress rightNeighborSocket;
        InetSocketAddress initalRightNeighborSocket;

        InetSocketAddress leftNeighborSocket;
        InetSocketAddress initialLeftNeighborSocket;*/

        String id = "tank"+(count++);
        client.add( id, msg.getSender());
        Neighbor neighbor = new Neighbor(id);


        InetSocketAddress newClientAddress = (InetSocketAddress) client.getClient(client.indexOf(id));

        /*rightNeighborSocket = (InetSocketAddress) client.getRightNeighorOf(client.indexOf(id));
        leftNeighborSocket = (InetSocketAddress) client.getLeftNeighorOf(client.indexOf(id));

        int indexRightNeighborOfRightNeighbor = client.indexOf(client.getRightNeighorOf(client.indexOf(id)));
        initalRightNeighborSocket = (InetSocketAddress) client.getRightNeighorOf(indexRightNeighborOfRightNeighbor);

        int indexLeftNeighborOfLeftNeighbor = client.indexOf(client.getLeftNeighorOf(client.indexOf(id)));
        initialLeftNeighborSocket = (InetSocketAddress) client.getLeftNeighorOf(indexLeftNeighborOfLeftNeighbor);*/



        if (/*newClientAddress == inetSocketLeft && newClientAddress ==inetSocketRight*/ client.size()==1) {
            endpoint.send(msg.getSender(), new NeighborUpdate(newClientAddress, newClientAddress));
            endpoint.send(msg.getSender(), new Token());
        }
        else {
            /*endpoint.send(rightNeighborSocket, new NeighborUpdate(initalRightNeighborSocket ,newClientAddress));
            endpoint.send(leftNeighborSocket, new NeighborUpdate(newClientAddress, initialLeftNeighborSocket));
            endpoint.send(newClientAddress, new NeighborUpdate(rightNeighborSocket, leftNeighborSocket));*/

            endpoint.send(neighbor.getRightNeighborSocket(), new NeighborUpdate(neighbor.getInitialRightNeighborSocket() ,newClientAddress));
            endpoint.send(neighbor.getLeftNeighborSocket(), new NeighborUpdate(newClientAddress, neighbor.getInitialLeftNeighborSocket()));
            endpoint.send(newClientAddress, new NeighborUpdate(neighbor.getRightNeighborSocket(), neighbor.getLeftNeighborSocket()));
        }

        endpoint.send(msg.getSender(), new RegisterResponse(id));
    }

    private void deregister(Message msg) {
        String removeID = ((DeregisterRequest) msg.getPayload()).getId();
        Neighbor neighbor = new Neighbor(removeID);

        /*InetSocketAddress inetSocketRight;
        InetSocketAddress inetSocketRightRight;

        InetSocketAddress inetSocketLeft;
        InetSocketAddress inetSocketLeftLeft;*/

        /*inetSocketRight = (InetSocketAddress) client.getRightNeighorOf(client.indexOf(removeID));
        inetSocketLeft = (InetSocketAddress) client.getLeftNeighorOf(client.indexOf(removeID));

        int indexRightNeighborOfRightNeighbor = client.indexOf(client.getRightNeighorOf(client.indexOf(removeID)));
        inetSocketRightRight = (InetSocketAddress) client.getRightNeighorOf(indexRightNeighborOfRightNeighbor);

        int indexLeftNeighborOfLeftNeighbor = client.indexOf(client.getLeftNeighorOf(client.indexOf(removeID)));
        inetSocketLeftLeft = (InetSocketAddress) client.getLeftNeighorOf(indexLeftNeighborOfLeftNeighbor);

        System.out.println(inetSocketLeft);
        System.out.println(inetSocketRight);
        System.out.println(inetSocketLeftLeft);
        System.out.println(inetSocketRightRight);

        endpoint.send(inetSocketRight, new NeighborUpdate(inetSocketRightRight, inetSocketLeft));
        endpoint.send(inetSocketLeft, new NeighborUpdate(inetSocketRight, inetSocketLeftLeft));*/

        endpoint.send(neighbor.getRightNeighborSocket(), new NeighborUpdate(neighbor.getInitialRightNeighborSocket(),
                neighbor.getLeftNeighborSocket()));
        endpoint.send(neighbor.getLeftNeighborSocket(), new NeighborUpdate(neighbor.getRightNeighborSocket(), neighbor.getInitialLeftNeighborSocket()));
        client.remove(client.indexOf(removeID));
    }

    private void handOffFish(HandoffRequest handoffRequest, InetSocketAddress inetSocketAddress) {
        int index = client.indexOf(inetSocketAddress);
        FishModel fishModel = handoffRequest.getFish();
        Direction direction = fishModel.getDirection();

        InetSocketAddress neighborReceiver;
        if (direction == Direction.LEFT) {
            neighborReceiver = (InetSocketAddress) client.getLeftNeighorOf(index);
        }
        else {
            neighborReceiver = (InetSocketAddress) client.getRightNeighorOf(index);
        }

        endpoint.send(neighborReceiver, handoffRequest);
    }

    final class Neighbor {
        private String id;

        public Neighbor(String id) {
            this.id = id;
        }

        public InetSocketAddress getRightNeighborSocket() {
            InetSocketAddress rightNeighborSocket;
            rightNeighborSocket = (InetSocketAddress) client.getRightNeighorOf(client.indexOf(id));
            return rightNeighborSocket;
        }

        public InetSocketAddress getInitialRightNeighborSocket() {
            InetSocketAddress initialRightNeighborSocket;
            int indexInitalRightNeighborSocket = client.indexOf(client.getRightNeighorOf(client.indexOf(id)));
            initialRightNeighborSocket = (InetSocketAddress) client.getRightNeighorOf(indexInitalRightNeighborSocket);
            return initialRightNeighborSocket;
        }

        public InetSocketAddress getLeftNeighborSocket() {
            InetSocketAddress leftNeighborSocket;
            leftNeighborSocket = (InetSocketAddress) client.getLeftNeighorOf(client.indexOf(id));
            return leftNeighborSocket;
        }

        public InetSocketAddress getInitialLeftNeighborSocket() {
            InetSocketAddress initialLeftNeighborSocket;
            int indexInitialLeftNeighborSocket = client.indexOf(client.getLeftNeighorOf(client.indexOf(id)));
            initialLeftNeighborSocket = (InetSocketAddress) client.getLeftNeighorOf(indexInitialLeftNeighborSocket);
            return initialLeftNeighborSocket;
        }
    }


}
