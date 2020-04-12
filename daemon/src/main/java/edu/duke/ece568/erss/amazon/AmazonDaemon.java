package edu.duke.ece568.erss.amazon;

import edu.duke.ece568.erss.amazon.proto.AmazonUPSProtocol.*;
import edu.duke.ece568.erss.amazon.proto.WorldAmazonProtocol.*;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

import static edu.duke.ece568.erss.amazon.Utils.recvMsgFrom;
import static edu.duke.ece568.erss.amazon.Utils.sendMsgTo;

/**
 * 1. packageid is the same as shipid, and this is created when we send the APack
 * 2. all communication with the world will be blocking, e.g. load() will return only after finish loading
 * 3. all communication with the UPS will be unblocking, e.g. toPick() will return once send successfully(receive ack)
 * 4. a full process will contain "purchase(world) ---> pack(world) & pick(UPS) ---> load(world) ---> deliver(UPS)"
 */
public class AmazonDaemon {
    private static final String HOST = "vcm-13663.vm.duke.edu";
    private static final int PORT = 23456;
    // the default timeout for each request
    // i.e. resend request if don't receive ack within TIME_OUT
    private static final int TIME_OUT = 10000;

    // TODO: debug info
    MockUPS ups;

    // NOTE!!! the world simulator use only one socket to communicate with us
    // i.e. the server expect to receive AConnect from each new connection
    private InputStream in;
    private OutputStream out;
    // global sequence number
    private long seqNum;
    // server communicate with UPS
    private Server upsServer;
    // daemon thread used to communicate with front-end
    private DaemonThread daemonThread;
    // a mpa of all unfinished package(key is the package id)
    private Map<Long, Package> packageMap;
    private ThreadPoolExecutor threadPool;
    private List<AInitWarehouse> warehouses;

    public AmazonDaemon() throws IOException {
        ups = new MockUPS();
        this.seqNum = 0;
        // set up the TCP connection to the world(not connected yet)
        Socket socket = new Socket(HOST, PORT);
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
        this.daemonThread = null;
        this.packageMap = new ConcurrentHashMap<>();
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(30);
        this.threadPool = new ThreadPoolExecutor(50, 80, 5, TimeUnit.SECONDS, workQueue);
        initWareHouse();
    }

    /**
     * Initial a lost of warehouses, if you want more warehouse at different location, change here.
     */
    public void initWareHouse(){
        warehouses = new ArrayList<>();
        // the id is the same as the index
        warehouses.add(AInitWarehouse.newBuilder().setId(warehouses.size()).setX(2).setY(2).build());
        warehouses.add(AInitWarehouse.newBuilder().setId(warehouses.size()).setX(3).setY(3).build());
    }

    /**
     * This function will set up a (UPS) server, waiting for the connection from UPS.
     * After getting the world id, and successfully connected to the world, it will
     * open another thread handle the request coming from Django front-end. The main
     * thread is used to communicate with UPS and the world.
     */
    public void config() throws IOException {
        // TODO: debug info
        ups.init();

        System.out.println("Daemon is running...");
        System.out.println("Listening connection from UPS at 9999");
        // the server listening the request comes from UPS
        upsServer = new Server(9999);
        Socket s = null;
        while (s == null){
            s = upsServer.accept();
            if (s != null){
                UAstart.Builder builder = UAstart.newBuilder();
                recvMsgFrom(builder, s.getInputStream());
                // has a valid world id
                if (builder.hasWorldid() && connectToWorld(builder.getWorldid())){
                    System.out.println("Amazon connected to the world.");
                    // send back ack
                    sendMsgTo(Res.newBuilder().addAck(builder.getSeqnum()).build(), s.getOutputStream());
                    break;
                }
            }
        }
    }

    /**
     * Connect to the world.
     * @param worldID target world id
     * @return true if connect to the world successfully
     */
    boolean connectToWorld(long worldID) {
        AConnect.Builder connect =  AConnect.newBuilder();
        connect.setIsAmazon(true);
        connect.addAllInitwh(warehouses);
        if (worldID >= 0){
            connect.setWorldid(worldID);
        }

        AConnected.Builder connected = AConnected.newBuilder();

        sendMsgTo(connect.build(), out);
        recvMsgFrom(connected, in);

        System.out.println("world id: " + connected.getWorldid());
        System.out.println("result: " + connected.getResult());

        return connected.getResult().equals("connected!");
    }

    /**
     * Run both daemon thread and the UPS server.
     */
    public void runAll() {
        // TODO: debug info, mock a new purchase request after 3s
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                System.out.println("try to connect to daemon server");
                Socket socket = new Socket("localhost", 8888);
                PrintWriter out = new PrintWriter(socket.getOutputStream());
                out.write("2\n");
                out.flush();
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                System.out.println(in.readLine());
            }catch (Exception e){
                System.err.println(e.toString());
            }
        }).start();

        // prepare all core threads
        threadPool.prestartAllCoreThreads();
        // run two separate server
        runDaemonServer();
        runUPSServer();
    }

    /**
     * This function will create a daemon thread, which used to communicate with front-end.
     */
    void runDaemonServer(){
        daemonThread = new DaemonThread(packageID -> {
            // use thread pool communicate with the world
            threadPool.execute(() -> {
                System.out.println("Receive new buying request");
                // 1. retrieve the package info from DB
                APurchaseMore.Builder newPackage = new SQL().queryPackage(packageID);
                newPackage.setSeqnum(seqNum);
                // 2. tell the world to purchase more
                AResponses.Builder res = send(ACommands.newBuilder().addBuy(newPackage));
                // 3. receive the purchase result
                while (true){
                    // sometimes, the data will come back with the ack, so we check first
                    if (res.getArrivedCount() > 0){
                        // purchase successful
                        seqNum++;
                        break;
                    }
                    // if only receive ack, keep waiting
                    res = receive();
                }
                System.out.println("Successful purchased");
                // 4. create & store the package object
                // this APack object is mainly for the convenient of following operation
                APack.Builder builder = APack.newBuilder();
                builder.setWhnum(newPackage.getWhnum());
                builder.addAllThings(newPackage.getThingsList());
                builder.setShipid(packageID);
                builder.setSeqnum(-1);
                Package p = new Package(packageID, newPackage.getWhnum(), builder.build());
                // store this unfinished package to the map
                packageMap.put(packageID, p);
                // pick(to UPS) and pack(to world) can happen in parallel
                // i.e. use two different thread to send out the command
                toPack(packageID);
                toPick(packageID);
            });
        });
        daemonThread.start();
    }

    /**
     * This function will keep waiting for new connections from UPS side.
     */
    void runUPSServer(){
        while (!Thread.currentThread().isInterrupted()){
            Socket socket = upsServer.accept();
            if (socket != null){
                threadPool.execute(() -> {
                    try {
                        UAcommand.Builder command = UAcommand.newBuilder();
                        recvMsgFrom(command, socket.getInputStream());
                        System.out.println("receive from ups:");
                        System.out.println(command);
                        // check all trucks which arrive
                        for (UApicked p : command.getPickList()){
                            System.out.println("actually picked");
                            // update package truck id and tell it to load
                            picked(p.getShipid(), p.getTruckid());
                        }
                        // check all packages which is delivered
                        for (UAdelivered d : command.getDeliverList()){
                            System.out.println("actually delivered");
                            // set the package delivered and remove it from the map(don't care anymore)
                            delivered(d.getShipid());
                        }
                        // send back ack
                        sendAck(command.build(), socket.getOutputStream());
                    }catch (Exception e){
                        System.err.println(e.toString());
                    }
                });
            }
        }
    }

    /**
     * The truck has arrived at the warehouse, should load the package.
     * @param packageID corresponding package
     * @param truckID corresponding truck
     */
    synchronized void picked(long packageID, int truckID){
        checkPackageID(packageID);
        Package pk = packageMap.get(packageID);
        pk.setTruckID(truckID);
        pk.setStatus(Package.LOADING);
        // check whether the package is packed
        if (pk.getStatus().equals(Package.PACKED)){
            toLoad(packageID);
        }
    }

    /**
     * The package is delivered.
     * @param packageID corresponding package
     */
    synchronized void delivered(long packageID){
        checkPackageID(packageID);
        packageMap.get(packageID).setStatus(Package.DELIVERED);
        packageMap.remove(packageID);
    }

    /**
     * This function will check the validation of package ID.
     * It will throw an IllegalArgumentException if the package is not in the unfinished map.
     * @param packageID ID to be checked
     */
    void checkPackageID(long packageID){
        if (!packageMap.containsKey(packageID)){
            throw new IllegalArgumentException("invalid package id");
        }
    }

    /* ====== all functions start with to, e.g. toXXX() is asynchronous ====== */
    /**
     * Tell the world to pack the package.
     * Function is asynchronous, will use thread-pool and return immediately.
     * @param packageID corresponding package
     */
    void toPack(long packageID){
        checkPackageID(packageID);
        Package p = packageMap.get(packageID);
        threadPool.execute(() -> {
            System.out.println("packing");
            pack(p.getId());
            // once finish packing, check whether the truck has already arrived
            // if yes, go loading; if no, do nothing and return
            if (p.getTruckID() != -1){
                toLoad(packageID);
            }
        });
    }

    /**
     * Tell UPS go to a specific warehouse to pick up the package.
     * @param packageID corresponding package
     */
    void toPick(long packageID){
        checkPackageID(packageID);
        Package p = packageMap.get(packageID);
        threadPool.execute(() -> {
            if (false){
                AUpick.Builder pick = AUpick.newBuilder();
                pick.setPackage(p.getPack());
                pick.setSeqnum(seqNum);
                pick.setWh(warehouses.get(p.getWhID()));
                pick.setX(p.getDestX());
                pick.setY(p.getDestY());

                AUcommand.Builder command = AUcommand.newBuilder();
                command.addPick(pick);

                send(command.build());
            }else {
                // TODO: debug info
                ups.pick(p.getWhID());
                p.setTruckID(ups.truckID);
            }
        });
    }

    /**
     * Tell the world to load the package onto the truck.
     * NOTE: this function should only be called after the truck has arrived.
     * @param packageID corresponding package
     */
    void toLoad(long packageID){
        checkPackageID(packageID);
        Package p = packageMap.get(packageID);
        threadPool.execute(() -> {
            System.out.println("loading");
            load(p.getId());
            System.out.println("loaded");
            // once finish loading, tell UPS to deliver
            System.out.println("delivering");
            toDelivery(packageID);
            System.out.println("delivered");
        });
    }

    /**
     * Tell UPS to deliver the package.
     * NOTE: this function should only be called after the package is loaded.
     * @param packageID corresponding package
     */
    void toDelivery(long packageID){
        checkPackageID(packageID);
        Package p = packageMap.get(packageID);
        p.setStatus(Package.DELIVERING);
        threadPool.execute(() -> {
            if (false){
                AUdeliver.Builder deliver = AUdeliver.newBuilder();
                deliver.setPackage(p.getPack());
                deliver.setSeqnum(seqNum);

                AUcommand.Builder command = AUcommand.newBuilder();
                command.addDeliver(deliver);

                send(command.build());
            }else {
                // TODO: debug info
                ups.delivery(10, 10, packageID);
                p.setTruckID(ups.truckID);
            }
        });
    }

    /**
     * Pack the package, this function is blocking, so don't call it on the main thread.
     * @param packageID corresponding package
     */
    public void pack(long packageID){
        checkPackageID(packageID);
        Package p = packageMap.get(packageID);
        p.setStatus(Package.PACKING);

        ACommands.Builder command = ACommands.newBuilder();

        APack pack = p.getPack();
        command.addTopack(pack.toBuilder().setSeqnum(seqNum));

        AResponses.Builder responses = send(command);
        System.out.println(responses.toString());

        if (responses.getReadyCount() == 0){
            // only receive ack, need another receive to receive ready list
            responses = receive();
            System.out.println(responses.toString());
        }
        // receive the ready list
        seqNum++;

        p.setStatus(Package.PACKED);
    }

    /**
     * Load the packageID, this function is blocking, so don't call it on the main thread.
     * @param packageID corresponding package
     */
    public void load(long packageID){
        checkPackageID(packageID);
        Package p = packageMap.get(packageID);
        p.setStatus(Package.LOADING);

        ACommands.Builder command = ACommands.newBuilder();

        APutOnTruck.Builder load = APutOnTruck.newBuilder();
        load.setWhnum(p.getWhID());
        load.setTruckid(p.getTruckID());
        load.setShipid(packageID);
        load.setSeqnum(seqNum);
        command.addLoad(load);

        AResponses.Builder responses = send(command);
        System.out.println(responses.toString());

        if (responses.getLoadedCount() == 0){
            responses = receive();
            System.out.println(responses.toString());
        }
        seqNum++;
        p.setStatus(Package.LOADED);
    }

    /**
     * Query the status of a package, not so useful(we can keep track by ourselves)
     * @param packageID corresponding package
     */
    public void query(int packageID){
        ACommands.Builder command = ACommands.newBuilder();

        AQuery.Builder query = AQuery.newBuilder();
        query.setPackageid(packageID);
        query.setSeqnum(seqNum);
        command.addQueries(query);

        AResponses.Builder responses = send(command);
        System.out.println(responses.toString());

        if (responses.getPackagestatusCount() == 0){
            responses = receive();
            System.out.println(responses.toString());
        }
        seqNum++;
    }

    /**
     * Disconnect from the world.
     */
    public void disconnect(){
        ACommands.Builder command = ACommands.newBuilder();
        command.setDisconnect(true);

        AResponses.Builder responses = AResponses.newBuilder();

        sendMsgTo(command.build(), out);
        recvMsgFrom(responses, in);
        System.out.println(responses.toString());

        if (responses.hasFinished()){
            System.out.println("amazon disconnect finish");
        }
        seqNum++;
    }

    /**
     * Send back the ack to UPS.
     * This function will extract all seqnum in the UAcommand, and send back corresponding ack.
     * message UAcommand{
     *    repeated UApicked pick = 2;
     *    repeated UAdelivered deliver = 3;
     * }
     * @param command the incoming command you want to ack
     * @param outputStream output stream
     */
    void sendAck(UAcommand command, OutputStream outputStream){
        List<Long> seqs = new ArrayList<>();
        for (UApicked a : command.getPickList()){
            seqs.add(a.getSeqnum());
        }
        for (UAdelivered a : command.getDeliverList()){
            seqs.add(a.getSeqnum());
        }
        Res.Builder res = Res.newBuilder();
        for (long seq : seqs){
            res.addAck(seq);
        }
        sendMsgTo(res.build(), outputStream);
    }

    /**
     * This function will extract all seqnum in the AResponse and send back corresponding ack.
     * message AResponses {
     *   repeated APurchaseMore arrived = 1;
     *   repeated APacked ready = 2;
     *   repeated ALoaded loaded = 3;
     *   optional bool finished = 4;
     *   repeated AErr error = 5;
     *   repeated int64 acks = 6;
     *   repeated APackage packagestatus = 7;
     * }
     * @param responses the response you want to ack
     */
    void sendAck(AResponses responses){
        List<Long> seqs = new ArrayList<>();
        for (APurchaseMore a : responses.getArrivedList()){
            seqs.add(a.getSeqnum());
        }
        for (APacked a : responses.getReadyList()){
            seqs.add(a.getSeqnum());
        }
        for (ALoaded a : responses.getLoadedList()){
            seqs.add(a.getSeqnum());
        }
        for (AErr a : responses.getErrorList()){
            seqs.add(a.getSeqnum());
        }
        for (APackage a : responses.getPackagestatusList()){
            seqs.add(a.getSeqnum());
        }
        ACommands.Builder commands = ACommands.newBuilder();
        for (long seq : seqs){
            commands.addAcks(seq);
        }
        sendMsgTo(commands.build(), out);
    }

    /**
     * This function will send the AUcommand to UPS and receive an ack.
     * @param command AUcommand
     */
    void send(AUcommand command){
        try {
            Socket socket = new Socket("localhost", 1111);
            InputStream inputStream = socket.getInputStream();
            OutputStream outputStream = socket.getOutputStream();

            Res.Builder r = Res.newBuilder();
            // note: we should use the new stream not the global one(which is for the world)
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    sendMsgTo(command, outputStream);
                }
            }, 0, TIME_OUT);
            while (true){
                recvMsgFrom(r, inputStream);
                if(r.getAck(0) == seqNum){
                    seqNum++;
                    break;
                }
                r.clear();
            }
        }catch (Exception e){
            System.err.println(e.toString());
        }
    }

    /**
     * A wrapper of the actual send function, this function will re-send automatically if timeout.
     * message ACommands {
     *   repeated APurchaseMore buy = 1;
     *   repeated APack topack = 2;
     *   repeated APutOnTruck load = 3;
     *   repeated AQuery queries = 4;
     *   optional uint32 simspeed = 5;
     *   optional bool disconnect = 6;
     *   repeated int64 acks =7;
     * }
     * @param commands ACommand
     * @return AResponse object, in case the response contains something other than ack
     */
    AResponses.Builder send(ACommands.Builder commands){
        System.out.println("amazon sending: " + commands.toString());
        // if not receive the ack within 10s, it will resend the message
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                sendMsgTo(commands.build(), out);
            }
        }, 0, TIME_OUT);
        AResponses.Builder responses = AResponses.newBuilder();
        while (true){
            // keep receiving message until receive expected ack
            if (responses.getErrorCount() == 0 &&
                    responses.getAcksCount() > 0 &&
                    responses.getAcksList().get(responses.getAcksCount() - 1) == seqNum){
                timer.cancel();
                seqNum++;
                break;
            }else {
                responses = receive();
            }
        }
        return responses;
    }

    /**
     * This function is a wrapper of actual receive function, it will send back the ack automatically.
     */
    AResponses.Builder receive(){
        AResponses.Builder responses = AResponses.newBuilder();
        recvMsgFrom(responses, in);
        // send ack back
        sendAck(responses.build());
        return responses;
    }

    /* ====== these functions are workable but old and no use ====== */
    public void purchaseMoreOld(List<AProduct> products) {
        ACommands.Builder command = ACommands.newBuilder();

        APurchaseMore.Builder purchase = APurchaseMore.newBuilder();
        purchase.addAllThings(products);
        purchase.setSeqnum(seqNum);
        purchase.setWhnum(1);
        command.addBuy(purchase);

        AResponses.Builder responses = AResponses.newBuilder();

        sendMsgTo(command.build(), out);
        recvMsgFrom(responses, in);
        System.out.println(responses.toString());

        // TODO: maybe you want to check the error status
        if (responses.getErrorCount() == 0){
            seqNum++;
        }

        if (responses.getAcksCount() > 0){
            System.out.println("ack: " + responses.getAcks(0));
        }

        // send back ack
        List<Long> seqs = new ArrayList<>();
        for (APurchaseMore purchaseMore : responses.getArrivedList()){
            seqs.add(purchaseMore.getSeqnum());
        }
        sendAck(seqs);
    }
    public void packOld(List<AProduct> products){

        ACommands.Builder command = ACommands.newBuilder();

        APack.Builder pack = APack.newBuilder();
        pack.addAllThings(products);
        pack.setSeqnum(seqNum);
        pack.setWhnum(1);
        pack.setShipid(1);
        command.addTopack(pack);

        AResponses.Builder responses = AResponses.newBuilder();

        sendMsgTo(command.build(), out);
        recvMsgFrom(responses, in);
        System.out.println(responses.toString());

        if (responses.getReadyCount() == 0){
            // only receive ack, need another receive to receive ready list
            responses.clear();
            recvMsgFrom(responses, in);
            System.out.println(responses.toString());
        }
        // receive the ready list
        seqNum++;

        List<Long> seqs = new ArrayList<>();
        for (APacked packed : responses.getReadyList()){
            seqs.add(packed.getSeqnum());
        }
        sendAck(seqs);
    }
    public void loadOld(int truckID){
        ACommands.Builder command = ACommands.newBuilder();

        APutOnTruck.Builder load = APutOnTruck.newBuilder();
        load.setWhnum(1);
        load.setTruckid(truckID);
        load.setShipid(1);
        load.setSeqnum(seqNum);
        command.addLoad(load);

        AResponses.Builder responses = AResponses.newBuilder();

        sendMsgTo(command.build(), out);
        recvMsgFrom(responses, in);
        System.out.println(responses.toString());

        if (responses.getLoadedCount() == 0){
            responses.clear();
            recvMsgFrom(responses, in);
            System.out.println(responses.toString());
        }
        seqNum++;

        List<Long> seqs = new ArrayList<>();
        for (ALoaded loaded : responses.getLoadedList()){
            seqs.add(loaded.getSeqnum());
        }
        sendAck(seqs);
    }
    void sendAck(List<Long> seqs){
        ACommands.Builder commands = ACommands.newBuilder();
        for (long seq : seqs){
            commands.addAcks(seq);
        }
        sendMsgTo(commands.build(), out);
    }
    /* ====== end ====== */

    public static void main(String[] args) throws Exception {
        AmazonDaemon amazonDaemon = new AmazonDaemon();
        amazonDaemon.config();
        amazonDaemon.runAll();
    }
}
