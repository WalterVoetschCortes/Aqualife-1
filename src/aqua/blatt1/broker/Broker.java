package aqua.blatt1.broker;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.*;
import aqua.blatt1.endpoint.SecureEndpoint;
import messaging.Endpoint;
import messaging.Message;


import javax.crypto.NoSuchPaddingException;
import javax.swing.*;
import java.net.InetSocketAddress;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Broker {

    int NUMTHREADS = 5;
    int count;
    int leaseLength = 10;
    volatile boolean stopRequested = false;
    SecureEndpoint endpoint;
    ClientCollection client = new ClientCollection();
    ExecutorService executor = Executors.newFixedThreadPool(NUMTHREADS);
    ReadWriteLock lock = new ReentrantReadWriteLock();
    Timer timer = new Timer();

    public Broker() throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
        endpoint = new SecureEndpoint(4711);
    }
    private class BrokerTask {
        public void brokerTask(Message msg) {
            if (msg.getPayload() instanceof RegisterRequest) {
                synchronized (client) {
                    register(msg);
                }
            }

            if (msg.getPayload() instanceof DeregisterRequest) {
                synchronized (client) {
                    deregister(msg);
                }
            }

            //lock.writeLock().lock();
            if (msg.getPayload() instanceof HandoffRequest) {
                lock.writeLock().lock();
                HandoffRequest handoffRequest = (HandoffRequest) msg.getPayload();
                InetSocketAddress inetSocketAddress = msg.getSender();
                handOffFish(handoffRequest, inetSocketAddress);
                lock.writeLock().unlock();
            }
            if (msg.getPayload() instanceof PoisonPill) {
                System.exit(0);
            }

            if (msg.getPayload() instanceof NameResolutionRequest) {
                String TankID = ((NameResolutionRequest) msg.getPayload()).getTankID();
                String RequestID = ((NameResolutionRequest) msg.getPayload()).getRequestID();
                InetSocketAddress sender = msg.getSender();
                sendInetSocketResponse(TankID, RequestID, sender);
            }
        }
    }

    public static void main(String[] args) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
        Broker broker = new Broker();
        broker.broker();
    }

    public void broker() {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                System.out.println("Ich gehe hier rein");
                System.out.println(client.size());
                if (client.size() > 0) {
                    for (int i = 0; i < client.size(); i++) {
                        Date timestamp = new Date();
                        Date tempTimestamp = client.getTimestamp(i);
                        System.out.println(tempTimestamp);
                        long leasingTime = timestamp.getTime() - tempTimestamp.getTime();
                        System.out.println(leasingTime);
                        if (leasingTime > 10 * 1000) {
                            endpoint.send((InetSocketAddress) client.getClient(i), new LeasingRunOut());
                        }
                    }
                }


            }
        }, 0, 3 * 1000);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                JOptionPane.showMessageDialog(null, "Press OK button to stop server");
                stopRequested = true;
            }
        });

        while (!stopRequested) {
            Message msg = endpoint.blockingReceive();
            BrokerTask brokerTask = new BrokerTask();
            executor.execute(() -> brokerTask.brokerTask(msg));
        }
        executor.shutdown();

    }

    private void register(Message msg) {
        Date timestamp = new Date();
        if (client.indexOf(msg.getSender()) == -1) {
            String id = "tank" + (count++);
            client.add(id, msg.getSender(), timestamp);
            Neighbor neighbor = new Neighbor(id);

            InetSocketAddress newClientAddress = (InetSocketAddress) client.getClient(client.indexOf(id));

            if (/*newClientAddress == inetSocketLeft && newClientAddress ==inetSocketRight*/ client.size() == 1) {
                endpoint.send(msg.getSender(), new NeighborUpdate(newClientAddress, newClientAddress));
                endpoint.send(msg.getSender(), new Token());
            } else {

                endpoint.send(neighbor.getRightNeighborSocket(), new NeighborUpdate(neighbor.getInitialRightNeighborSocket(), newClientAddress));
                endpoint.send(neighbor.getLeftNeighborSocket(), new NeighborUpdate(newClientAddress, neighbor.getInitialLeftNeighborSocket()));
                endpoint.send(newClientAddress, new NeighborUpdate(neighbor.getRightNeighborSocket(), neighbor.getLeftNeighborSocket()));
            }

            endpoint.send(msg.getSender(), new RegisterResponse(id, leaseLength));
        } else {
            System.out.println("prüfe ob bereits registriert");
            int index = client.indexOf(msg.getSender());
            client.setTimestamp(index, timestamp);
            endpoint.send(msg.getSender(), new RegisterResponse(client.getId(index), leaseLength));
        }
    }

    private void deregister(Message msg) {
        String removeID = ((DeregisterRequest) msg.getPayload()).getId();
        Neighbor neighbor = new Neighbor(removeID);

        if (client.size() == 2) {
            endpoint.send(neighbor.getRightNeighborSocket(), new NeighborUpdate(neighbor.getLeftNeighborSocket(),
                    neighbor.getLeftNeighborSocket()));
        } else {
            endpoint.send(neighbor.getRightNeighborSocket(), new NeighborUpdate(neighbor.getInitialRightNeighborSocket(), neighbor.getLeftNeighborSocket()));
            endpoint.send(neighbor.getLeftNeighborSocket(), new NeighborUpdate(neighbor.getRightNeighborSocket(), neighbor.getInitialLeftNeighborSocket()));
        }
        client.remove(client.indexOf(removeID));
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

    private void sendInetSocketResponse(String TankID, String RequestID, InetSocketAddress sender) {
        InetSocketAddress homeClient = (InetSocketAddress) client.getClient(client.indexOf(TankID));
        endpoint.send(sender, new NameResolutionResponse(homeClient, RequestID));
    }


}
