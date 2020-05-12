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
        InetSocketAddress inetSocketRight;
        InetSocketAddress inetSocketLeft;
        String id = "tank"+(count++);
        client.add( id, msg.getSender());

        inetSocketRight = (InetSocketAddress) client.getRightNeighorOf(client.indexOf(id));
        inetSocketLeft = (InetSocketAddress) client.getLeftNeighorOf(client.indexOf(id));

        endpoint.send(msg.getSender(), new RegisterResponse(id));
        endpoint.send(msg.getSender(), new NeighborUpdate(inetSocketRight, inetSocketLeft));
    }

    private void deregister(Message msg) {

        String removeID = ((DeregisterRequest) msg.getPayload()).getId();
        InetSocketAddress inetSocketRight;
        InetSocketAddress inetSocketRightRigth;

        InetSocketAddress inetSocketLeft;
        InetSocketAddress inetSocketLeftLeft;

        inetSocketRight = (InetSocketAddress) client.getRightNeighorOf(client.indexOf(removeID));
        inetSocketLeft = (InetSocketAddress) client.getLeftNeighorOf(client.indexOf(removeID));

        int indexRightNeighborOfRightNeighbor = client.indexOf(client.getRightNeighorOf(client.indexOf(removeID)));
        inetSocketRightRigth = (InetSocketAddress) client.getRightNeighorOf(indexRightNeighborOfRightNeighbor);

        int indexLeftNeighborOfLeftNeighbor = client.indexOf(client.getLeftNeighorOf(client.indexOf(removeID)));
        inetSocketLeftLeft = (InetSocketAddress) client.getLeftNeighorOf(indexLeftNeighborOfLeftNeighbor);

        System.out.println(inetSocketLeft);
        System.out.println(inetSocketRight);
        System.out.println(inetSocketLeftLeft);
        System.out.println(inetSocketRightRigth);

        endpoint.send(inetSocketRight, new NeighborUpdate(inetSocketRightRigth,inetSocketLeft));
        endpoint.send(inetSocketLeft, new NeighborUpdate(inetSocketRight, inetSocketLeftLeft));
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

}
