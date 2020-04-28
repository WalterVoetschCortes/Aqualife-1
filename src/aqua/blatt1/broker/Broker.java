package aqua.blatt1.broker;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.DeregisterRequest;
import aqua.blatt1.common.msgtypes.HandoffRequest;
import aqua.blatt1.common.msgtypes.RegisterRequest;
import aqua.blatt1.common.msgtypes.RegisterResponse;
import messaging.Endpoint;
import messaging.Message;

import java.net.InetSocketAddress;

public class Broker {
    public static void main(String[] args) {
        Broker broker = new Broker();
        broker.broker();
    }

    Endpoint endpoint = new Endpoint(4711);
    ClientCollection client = new ClientCollection();

    public void broker(){
        boolean done = false;
        while( !done ) {
            Message msg = endpoint.blockingReceive();
            if (msg.getPayload() instanceof RegisterRequest) {
                register(msg);
            }

            if (msg.getPayload() instanceof DeregisterRequest) {
                deregister(msg);
            }
            if (msg.getPayload() instanceof HandoffRequest) {
                HandoffRequest handoffRequest = (HandoffRequest) msg.getPayload();
                InetSocketAddress inetSocketAddress = msg.getSender();
                handOffFish(handoffRequest,inetSocketAddress);
            }
        }
    }
    private void register(Message msg) {
        String id = "tank"+client.size();
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
