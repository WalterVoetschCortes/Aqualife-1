package aqua.blatt1.client;

import java.net.InetSocketAddress;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import aqua.blatt1.broker.AquaBroker;
import aqua.blatt1.common.Direction;
import aqua.blatt1.common.FishLocation;
import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.RecordsState;
import aqua.blatt1.common.msgtypes.*;
import messaging.Endpoint;
import messaging.Message;

public class TankModel extends Observable implements Iterable<FishModel>, AquaClient {
    public static final int WIDTH = 600;
    public static final int HEIGHT = 350;
    protected static final int MAX_FISHIES = 5;
    public static final int NUMTHREADS = 5;
    protected static final Random rand = new Random();
    protected volatile String id;
    protected final Set<FishModel> fishies;
    protected int fishCounter = 0;
    //protected final ClientCommunicator.ClientForwarder forwarder;
    public AquaClient rightNeighbor;
    public AquaClient leftNeighbor;
    protected boolean booltoken;
    protected RecordsState record = RecordsState.IDLE;
    protected int localFhishies;
    protected boolean initiatorReady = false;
    protected boolean waitForIDLE = false;
    protected int showGlobalSnapshot;
    protected boolean showDialog;
    protected int counter = 0;
    Timer timer = new Timer();
    ExecutorService executor = Executors.newFixedThreadPool(NUMTHREADS);
    Map<String, AquaClient> homeAgent = new HashMap<>();
    AquaBroker aquaBroker;
    AquaClient aquaClientServerStub;
    int globalValue;
    volatile boolean hasCollector;

    public TankModel(AquaBroker aquaBroker) throws RemoteException {
        this.fishies = Collections.newSetFromMap(new ConcurrentHashMap<FishModel, Boolean>());
        this.aquaBroker=aquaBroker;
        aquaClientServerStub = (AquaClient) UnicastRemoteObject.exportObject(this, 0);
    }


    public synchronized void onRegistration(String id, int leasLength) {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    aquaBroker.register(aquaClientServerStub);
                } catch (RemoteException | AlreadyBoundException e) {
                    e.printStackTrace();
                }
            }
        }, leasLength*1000);

        if (fishCounter == 0) {
            newFish(WIDTH - FishModel.getXSize(), rand.nextInt(HEIGHT - FishModel.getYSize()));
        }
        this.id = id;
    }

    public synchronized void newFish(int x, int y) {
        if (fishies.size() < MAX_FISHIES) {
            x = x > WIDTH - FishModel.getXSize() - 1 ? WIDTH - FishModel.getXSize() - 1 : x;
            y = y > HEIGHT - FishModel.getYSize() ? HEIGHT - FishModel.getYSize() : y;

            FishModel fish = new FishModel("fish" + (++fishCounter) + "@" + getId(), x, y,
                    rand.nextBoolean() ? Direction.LEFT : Direction.RIGHT);

            fishies.add(fish);
            homeAgent.put(fish.getId(), null);
        }
    }

    public void receiveFish(FishModel fish) throws RemoteException{
        if (fish.getDirection() == Direction.LEFT && record == RecordsState.RIGHT || fish.getDirection() == Direction.RIGHT && record == RecordsState.LEFT) {
            localFhishies++;
        }
        fish.setToStart();
        fishies.add(fish);
        updatefishLocation(fish);
    }

    public String getId() {
        return id;
    }

    public synchronized int getFishCounter() {
        return fishCounter;
    }

    public synchronized Iterator<FishModel> iterator() {
        return fishies.iterator();
    }

    private synchronized void updateFishies() throws RemoteException{
        for (Iterator<FishModel> it = iterator(); it.hasNext(); ) {
            FishModel fish = it.next();

            fish.update();

            if (fish.hitsEdge())
                hasToken(fish);

            if (fish.disappears()) {
                it.remove();
            }
        }
    }

    private synchronized void update() throws RemoteException{
        updateFishies();
        setChanged();
        notifyObservers();
    }

    protected void run() throws RemoteException, AlreadyBoundException{
        aquaBroker.register(aquaClientServerStub);

        try {
            while (!Thread.currentThread().isInterrupted()) {
                update();
                TimeUnit.MILLISECONDS.sleep(10);
            }
            //executor.shutdown();
        } catch (InterruptedException consumed) {
            // allow method to terminate
        }
    }

    public synchronized void finish() throws RemoteException, NotBoundException{
        aquaBroker.deregister(id);
    }

    public void updateNeighbors(AquaClient addressLeft, AquaClient addressRight) {
        this.leftNeighbor = addressLeft;
        this.rightNeighbor = addressRight;
    }

    public synchronized void receiveToken(Token token) {
        this.booltoken = true;
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                booltoken = false;
                try {
                    leftNeighbor.receiveToken(token);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }, 2 * 1000);
    }

    public synchronized void hasToken(FishModel fish) throws RemoteException{
        if (booltoken) {
            Direction direction = fish.getDirection();
            AquaClient receiverAddress;
            if (direction == Direction.LEFT) {
                receiverAddress = leftNeighbor;
            } else {
                receiverAddress = rightNeighbor;
            }
            receiverAddress.receiveFish(fish);
        } else {
            fish.reverse();
        }
    }

    public void initiateSnapshot() throws RemoteException{
        if (record == RecordsState.IDLE) {
            localFhishies = fishies.size();
            record = RecordsState.BOTH;
            initiatorReady = true;
            leftNeighbor.receiveSnapshotMarker(aquaClientServerStub, new SnapshotMarker());
            rightNeighbor.receiveSnapshotMarker(aquaClientServerStub, new SnapshotMarker());
        }

    }

    public void onReceiveCollector(Collector collector) {
        waitForIDLE = true;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                while (waitForIDLE)
                    if (record == RecordsState.IDLE) {
                        int currentFishState = collector.getLocalfishies();
                        int newFishState = currentFishState + localFhishies;
                        try {
                            leftNeighbor.handleSnapshotCollector(new Collector(counter));
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        waitForIDLE = false;
                    }
            }
        });

        if (initiatorReady) {
            initiatorReady = false;
            showDialog=true;
            System.out.println(collector.getLocalfishies());
            showGlobalSnapshot = collector.getLocalfishies();
        }
    }

    public void receiveSnapshotMarker(AquaClient sender, SnapshotMarker snapshotMarker) throws RemoteException{
        if (record == RecordsState.IDLE) {
            localFhishies = fishies.size();
            if (!leftNeighbor.equals(rightNeighbor)) {
                if (sender.equals(leftNeighbor)) {
                    record = RecordsState.RIGHT;
                } else if (sender.equals(rightNeighbor)) {
                    record = RecordsState.LEFT;
                }
            } else {
                record = RecordsState.BOTH;
            }
            if (leftNeighbor.equals(rightNeighbor)) {
                leftNeighbor.receiveSnapshotMarker(aquaClientServerStub, snapshotMarker);
            } else {
                leftNeighbor.receiveSnapshotMarker(aquaClientServerStub, snapshotMarker);
                rightNeighbor.receiveSnapshotMarker(aquaClientServerStub, snapshotMarker);
            }

        } else {
            if (!leftNeighbor.equals(rightNeighbor)) {
                if (sender.equals(leftNeighbor)) {
                    if (record == RecordsState.BOTH) {
                        record = RecordsState.RIGHT;
                    }
                    if (record == RecordsState.LEFT) {
                        record = RecordsState.IDLE;
                    }
                } else {
                    if (record == RecordsState.BOTH) {
                        record = RecordsState.LEFT;
                    }
                    if (record == RecordsState.RIGHT) {
                        record = RecordsState.IDLE;
                    }
                }
            } else {
                record = RecordsState.IDLE;
            }
        }
        if (initiatorReady && record == RecordsState.IDLE) {
            leftNeighbor.handleSnapshotCollector(new Collector(localFhishies));
        }
    }

    public void locateFishGlobally(String fishID) throws RemoteException{
        AquaClient inetSocketAddress = homeAgent.get(fishID);
         if (inetSocketAddress == null) {
            locateFishLocally(fishID);
        } else {
             inetSocketAddress.locateFishLocally(fishID);
        }
    }

    public void handleSnapshotCollector(Collector snapshotCollector) {
        if (initiatorReady) {
            initiatorReady = false;
            globalValue = snapshotCollector.getLocalfishies();
            showDialog = true;
        } else {
            hasCollector = true;
            executor.execute(() -> {
                while (hasCollector) {
                    if (record == RecordsState.IDLE) {
                        int counter = snapshotCollector.getLocalfishies() + localFhishies;
                        try {
                            leftNeighbor.handleSnapshotCollector(new Collector(counter));
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        hasCollector = false;
                    }
                }
            });
        }
    }

    public void locateFishLocally(String fishID) {
        for(FishModel fish : this.fishies) {
            if (fish.getId().equals(fishID)) {
                fish.toggle();
            }
        }
    }

    public void updatefishLocation(FishModel fish) throws RemoteException{
        String fishID = fish.getId();
        if (homeAgent.containsKey(fishID)) {
            homeAgent.put(fishID, null);
        } else {
            aquaBroker.handleNameResolutionRequest(fish.getTankId(), fish.getId(), aquaClientServerStub);
        }
    }

    public void leasingRunOut() throws RemoteException, NotBoundException{
        aquaBroker.deregister(this.id);
        System.exit(0);
    }

    public void handleNameResolutionResponse(AquaClient homeAddress, String fishId, AquaClient sender) throws RemoteException {
        homeAddress.handleLocationUpdate(id, sender);
    }

    public void handleLocationUpdate(String fishId, AquaClient currentTank) {
        homeAgent.replace(fishId, currentTank);
    }

}