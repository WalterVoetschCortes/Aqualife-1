package aqua.blatt1.broker;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.DeregisterRequest;
import aqua.blatt1.common.msgtypes.HandoffRequest;
import aqua.blatt1.common.msgtypes.RegisterRequest;
import aqua.blatt1.common.msgtypes.RegisterResponse;
import messaging.Endpoint;
import messaging.Message;

import javax.swing.*;
import java.net.InetSocketAddress;
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
        String id = "tank"+(count++);
        client.add( id, msg.getSender());
        endpoint.send(msg.getSender(), new RegisterResponse(id));
    }

    private void deregister(Message msg) {
        client.remove(client.indexOf(((DeregisterRequest) msg.getPayload()).getId()));
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
