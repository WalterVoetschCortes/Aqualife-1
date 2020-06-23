package aqua.blatt1.client;

import java.net.InetSocketAddress;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import aqua.blatt1.common.Direction;
import aqua.blatt1.common.msgtypes.*;
import aqua.blatt1.endpoint.SecureEndpoint;
import messaging.Endpoint;
import messaging.Message;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.Properties;

import javax.crypto.NoSuchPaddingException;

public class ClientCommunicator {
	private final SecureEndpoint endpoint;

	public ClientCommunicator() throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
		endpoint = new SecureEndpoint();
	}

	public class ClientForwarder {
		private final InetSocketAddress broker;

		private ClientForwarder() {
			this.broker = new InetSocketAddress(Properties.HOST, Properties.PORT);
		}

		public void register() {
			endpoint.send(broker, new RegisterRequest());
		}

		public void deregister(String id) {
			endpoint.send(broker, new DeregisterRequest(id));
		}

		public void handOff(FishModel fish,InetSocketAddress rightneighbor, InetSocketAddress leftneighbor) {
			Direction direction = fish.getDirection();
			InetSocketAddress reveiverAdress;

			if (direction == Direction.LEFT) {
				reveiverAdress = leftneighbor;
			}
			else {
				reveiverAdress = rightneighbor;
			}

			endpoint.send(reveiverAdress, new HandoffRequest(fish));

			//endpoint.send(reveiverAdress, new HandoffRequest(fish));
			//endpoint.send(broker, new HandoffRequest(fish));

		}
		public void sendToken (InetSocketAddress receiver, Token token) {
			endpoint.send(receiver, token);
		}

		public void sendSnapshotMarker (InetSocketAddress receiver, SnapshotMarker snapshotMarker) {
			endpoint.send(receiver, snapshotMarker);
		}

		public void sendCollector (InetSocketAddress receiver, Collector collector) {
			endpoint.send(receiver, collector);
		}

		public void sendLocationRequest(InetSocketAddress receiver, LocationRequest locationRequest) {
			endpoint.send(receiver, locationRequest);
		}

		public void sendResoultionRequest(NameResolutionRequest nameResolutionRequest) {
			endpoint.send(broker, nameResolutionRequest);
		}

		public void sendCurrentFishLocation(InetSocketAddress homeLocation, String fishID) {
			endpoint.send(homeLocation, new LocationUpdate(fishID));
		}
	}

	public class ClientReceiver extends Thread {
		private final TankModel tankModel;

		private ClientReceiver(TankModel tankModel) {
			this.tankModel = tankModel;
		}

		@Override
		public void run() {
			while (!isInterrupted()) {
				Message msg = endpoint.blockingReceive();

				if (msg.getPayload() instanceof RegisterResponse)
					tankModel.onRegistration(((RegisterResponse) msg.getPayload()).getId(), ((RegisterResponse) msg.getPayload()).getLeaseLength());

				if (msg.getPayload() instanceof HandoffRequest)
					tankModel.receiveFish(((HandoffRequest) msg.getPayload()).getFish());

				if (msg.getPayload() instanceof NeighborUpdate) {
					//tankModel.leftNeighbor = ((NeighborUpdate) msg.getPayload()).getAddressLeft();
					//tankModel.rightNeighbor = ((NeighborUpdate) msg.getPayload()).getAddressRight();
					tankModel.updateNeighbors(((NeighborUpdate) msg.getPayload()).getAddressLeft(), ((NeighborUpdate) msg.getPayload()).getAddressRight());
				}

				if (msg.getPayload() instanceof Token) {
					tankModel.receiveToken((Token) msg.getPayload());
				}

				if (msg.getPayload() instanceof SnapshotMarker){
					tankModel.receiveSnapshotMarker(msg.getSender(), (SnapshotMarker) msg.getPayload());
				}

				if (msg.getPayload() instanceof Collector) {
					tankModel.onReceiveCollector((Collector) msg.getPayload());
				}

				if (msg.getPayload() instanceof LocationRequest) {
					tankModel.locateFishLocally(((LocationRequest) msg.getPayload()).getFishID());
				}

				if (msg.getPayload() instanceof NameResolutionResponse) {
					InetSocketAddress homeLocation = ((NameResolutionResponse) msg.getPayload()).getHomeLocation();
					String fishID = ((NameResolutionResponse) msg.getPayload()).getRequestID();
					tankModel.handleResponse(homeLocation, fishID);
				}

				if (msg.getPayload() instanceof LocationUpdate) {
					String fishID = ((LocationUpdate) msg.getPayload()).getFishID();
					InetSocketAddress currentLocation = msg.getSender();
					tankModel.updateCurrentLocation(fishID, currentLocation);
				}

				if (msg.getPayload() instanceof LeasingRunOut) {
					tankModel.leasingRunOut();
				}

			}
			System.out.println("Receiver stopped.");
		}
	}

	public ClientForwarder newClientForwarder() {
		return new ClientForwarder();
	}

	public ClientReceiver newClientReceiver(TankModel tankModel) {
		return new ClientReceiver(tankModel);
	}



}
