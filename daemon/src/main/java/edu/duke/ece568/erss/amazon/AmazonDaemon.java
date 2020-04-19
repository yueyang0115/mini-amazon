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
// TODO: consider parse response in a separate function(sometimes a response of one package will contains information of the other)
public class AmazonDaemon {
     private static final String WORLD_HOST = "vcm-13663.vm.duke.edu";
//    private static final String WORLD_HOST = "vcm-14299.vm.duke.edu";
    private static final int WORLD_PORT = 23456;
    // the default timeout for each request
    // i.e. resend request if don't receive ack within TIME_OUT
    private static final int TIME_OUT = 10000;
    private static final String UPS_HOST = "vcm-14299.vm.duke.edu";
    private static final int UPS_PORT = 54321;

    // TODO: debug info
    MockUPS ups;

    // NOTE!!! the world simulator use only one socket to communicate with us
    // i.e. the server expect to receive AConnect from each new connection
    private final InputStream in;
    private final OutputStream out;
    // global sequence number
    private long seqNum;
    // server communicate with UPS
    private Server upsServer;
    // daemon thread used to communicate with front-end
    private DaemonThread daemonThread;
    // a mpa of all unfinished package(key is the package id)
    private final Map<Long, Package> packageMap;
    private final ThreadPoolExecutor threadPool;
    private List<AInitWarehouse> warehouses;

    public AmazonDaemon() throws IOException {
        ups = new MockUPS();
        this.seqNum = 0;
        // set up the TCP connection to the world(not connected yet)
        Socket socket = new Socket(WORLD_HOST, WORLD_PORT);
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
        while (true){
            Socket s = upsServer.accept();
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
        // TODO: debug info, mock a new purchase request after 3s and another after 2s
//        new Thread(() -> {
//            try {
//                Thread.sleep(3000);
//                // WARNING!!! only two package while debugging(mock UPS only have two trucks)
//                List<String> packages = new ArrayList<>(Arrays.asList("36\n", "37\n"));
//                for (String p : packages){
//                    System.out.println("try to connect to daemon server");
//                    Socket socket = new Socket("localhost", 8888);
//                    PrintWriter out = new PrintWriter(socket.getOutputStream());
//                    out.write(p);
//                    out.flush();
//                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//                    System.out.println("receive confirm from amazon: " + in.readLine());
//                    socket.close();
//                    Thread.sleep(1000);
//                }
//            }catch (Exception e){
//                System.err.println(e.toString());
//            }
//        }).start();

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
                System.out.println(String.format("Receive new buying request, id: %d", packageID));
                // 1. retrieve the package info from DB
                long seq = getSeqNum();
                APurchaseMore.Builder newPackage = new SQL().queryPackage(packageID);
                newPackage.setSeqnum(seq);
                // 2. tell the world to purchase more
                AResponses.Builder res = send(ACommands.newBuilder().addBuy(newPackage), seq);
                // 3. receive the purchase result
                while (true){
                    // sometimes, the successful data will come back with the ack, so we check first
                    if (res.getArrivedCount() > 0){
                        // purchase successful
                        break;
                    }
                    // if only receive ack, keep waiting
                    res = receive();
                }
                System.out.println("Successful purchased: " + packageID);
                // 4. create & store the package object
                // this APack object is mainly for the convenient of following operation
                APack.Builder builder = APack.newBuilder();
                builder.setWhnum(newPackage.getWhnum());
                builder.addAllThings(newPackage.getThingsList());
                builder.setShipid(packageID);
                builder.setSeqnum(-1);
                Package p = new Package(packageID, newPackage.getWhnum(), builder.build());
                p.setDestination(new SQL().queryPackageDest(packageID));
                // store this unfinished package to the map
                packageMap.put(packageID, p);
                // pick(to UPS) and pack(to world) can happen in parallel
                // i.e. use two different threads to send out the command
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
                        handleUPSRequest(socket.getInputStream(), socket.getOutputStream());
                    } catch (IOException e) {
                        System.err.println("runUPSServer: " + e.toString());
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
    void picked(long packageID, int truckID){
        checkPackageID(packageID);
        Package pk = packageMap.get(packageID);
        pk.setTruckID(truckID);
        // check whether the package is packed
        if (pk.getStatus().equals(Package.PACKED)){
            toLoad(packageID);
        }
    }

    /**
     * The package is delivered.
     * @param packageID corresponding package
     */
    void delivered(long packageID){
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
            throw new IllegalArgumentException("invalid package id: " + packageID);
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
//        Package p = packageMap.get(packageID);
        threadPool.execute(() -> {
            System.out.println("packing: " + packageID);
            pack(packageID);
            System.out.println("packed: " + packageID);
            // once finish packing, check whether the truck has already arrived
            // if yes, go loading; if no, do nothing and return
            if (packageMap.get(packageID).getTruckID() != -1){
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
                pick.setSeqnum(getSeqNum());
                pick.setWh(warehouses.get(p.getWhID()));
                pick.setX(p.getDestX());
                pick.setY(p.getDestY());

                AUcommand.Builder command = AUcommand.newBuilder();
                command.addPick(pick);

                sendToUPS(command.build());
            }else {
                // TODO: debug info
                ups.pick(p.getWhID(), packageID);
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
            System.out.println("loading: " + packageID);
            load(p.getId());
            System.out.println("loaded: " + packageID);
            // once finish loading, tell UPS to deliver
            toDelivery(packageID);
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
	    System.out.println("delivering: " + packageID);
        p.setStatus(Package.DELIVERING);
        threadPool.execute(() -> {
            if (false){
                AUdeliver.Builder deliver = AUdeliver.newBuilder();
                deliver.setPackage(p.getPack());
                deliver.setSeqnum(getSeqNum());

                AUcommand.Builder command = AUcommand.newBuilder();
                command.addDeliver(deliver);

                sendToUPS(command.build());
            }else {
                // TODO: debug info
                ups.delivery(p.getDestX(), p.getDestY(), packageID);
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

        long seq = getSeqNum();
        APack pack = p.getPack();
        command.addTopack(pack.toBuilder().setSeqnum(seq));

        AResponses.Builder responses = send(command, seq);
        System.out.println(String.format("package %d: %s", packageID, responses.toString()));

        if (responses.getReadyCount() == 0){
	        System.out.println(String.format("package %d: %s", packageID, "wait actual \"pack\" data"));
            // only receive ack, need another receive to receive ready list
            responses = receive();
            System.out.println(String.format("package %d: %s", packageID, responses.toString()));
        }

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

        long seq = getSeqNum();
        APutOnTruck.Builder load = APutOnTruck.newBuilder();
        load.setWhnum(p.getWhID());
        load.setTruckid(p.getTruckID());
        load.setShipid(packageID);
        load.setSeqnum(seq);
        command.addLoad(load);

        AResponses.Builder responses = send(command, seq);
        System.out.println(String.format("package %d: %s", packageID, responses.toString()));

        if (responses.getLoadedCount() == 0){
	        System.out.println(String.format("package %d: %s", packageID, "wait actual \"load\" data"));
            responses = receive();
            System.out.println(String.format("package %d: %s", packageID, responses.toString()));
        }
        p.setStatus(Package.LOADED);
    }

    /**
     * Query the status of a package, not so useful(we can keep track by ourselves)
     * @param packageID corresponding package
     */
    public void query(int packageID){
        ACommands.Builder command = ACommands.newBuilder();

        long seq = getSeqNum();
        AQuery.Builder query = AQuery.newBuilder();
        query.setPackageid(packageID);
        query.setSeqnum(seq);
        command.addQueries(query);

        AResponses.Builder responses = send(command, seq);
        System.out.println(String.format("package %d: %s", packageID, responses.toString()));

        if (responses.getPackagestatusCount() == 0){
            responses = receive();
            System.out.println(String.format("package %d: %s", packageID, responses.toString()));
        }
    }

    /**
     * Disconnect from the world.
     */
    public void disconnect(){
        ACommands.Builder command = ACommands.newBuilder();
        command.setDisconnect(true);

        AResponses.Builder res = send(command, 0);
        System.out.println(res.toString());

        if (res.hasFinished()){
            System.out.println("amazon disconnect finish");
        }
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
	    System.out.println("send ack back(to UPS): " + res.toString());
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
        if (seqs.size() > 0){
            ACommands.Builder commands = ACommands.newBuilder();
            for (long seq : seqs){
                commands.addAcks(seq);
            }
            System.out.println("send ack back(to World): " + commands.toString());
            synchronized (out){
                sendMsgTo(commands.build(), out);
            }
        }
    }

    /**
     * This function will send the AUcommand to UPS and receive an ack.
     * @param command AUcommand
     */
    void sendToUPS(AUcommand command){
        try {
            System.out.println("amazon sending(to UPS): " + command.toString());
            Socket socket = new Socket(UPS_HOST, UPS_PORT);
            InputStream inputStream = socket.getInputStream();
            OutputStream outputStream = socket.getOutputStream();

            Res.Builder r = Res.newBuilder();
            // note: we should use the new stream not the global one(which is for the world)
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    sendMsgTo(command, outputStream);
                }
            }, 0, TIME_OUT);

            while (true){
                recvMsgFrom(r, inputStream);
                // print out any error message
                if (r.getErrCount() > 0){
                    for (Err err : r.getErrList()){
                        System.out.println(err.toString());
                    }
                }

                if (r.getAckCount() > 0){
                    timer.cancel();
                    break;
                }

//                List<Long> acks = r.getAckList();
//                acks.sort(Long::compareTo);
//                if(acks.get(acks.size() - 1) == seqNum - 1){
//                    timer.cancel();
//                    break;
//                }
                r.clear();
            }
        }catch (Exception e){
            System.err.println("sendToUPS: " + e.toString());
        }
    }

    void handleUPSRequest(InputStream inputStream, OutputStream outputStream){
        try {
            UAcommand.Builder command = UAcommand.newBuilder();
            recvMsgFrom(command, inputStream);
            System.out.println("receive from ups:" + command);
            // send back ack, once receive
            sendAck(command.build(), outputStream);
            // check all trucks which arrive
            for (UApicked p : command.getPickList()){
                System.out.println("actually picked: package " + p.getShipid());
                // update package truck id and tell it to load
                picked(p.getShipid(), p.getTruckid());
            }
            // check all packages which is delivered
            for (UAdelivered d : command.getDeliverList()){
                System.out.println("actually delivered: package " + d.getShipid());
                // set the package delivered and remove it from the map(don't care anymore)
                delivered(d.getShipid());
            }
        }catch (Exception e){
            System.err.println("runUPSServer: " + e.toString());
        }
    }

    /**
     * A wrapper of the actual send function, this function will re-send automatically if timeout.
     * This function used to communicate with the world.
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
     * @param seqNum the sequence number of this command(used to check ack)
     * @return AResponse object, in case the response contains something other than ack
     */
    synchronized AResponses.Builder send(ACommands.Builder commands, long seqNum){
        // TODO: debug info
        commands.setSimspeed(30);
        System.out.println("amazon sending(to world): " + commands.toString());
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
            if (responses.getErrorCount() > 0){
                for (AErr err : responses.getErrorList()){
                    System.out.println(err.getErr());
                }
            }
            // TODO: maybe we should consider what if several ack arrive?
            if (responses.getAcksCount() > 0 && responses.getAcksList().contains(seqNum)){
                System.out.println("recv ack from world");
                System.out.println(responses.toString());
                // only after received the ack, we will stop the timer
                timer.cancel();
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
        synchronized (in){
            recvMsgFrom(responses, in);
        }
        // send ack back
        sendAck(responses.build());
        return responses;
    }

    /**
     * This function will handle the sequence number related stuff.
     * also, this function is thread safe
     * 1. return the latest sequence number
     * 2. make sequence number auto-increment
     * @return the latest sequence number
     */
    synchronized long getSeqNum(){
        long tmp = seqNum;
        seqNum++;
        return tmp;
    }

    public static void main(String[] args) throws Exception {
        AmazonDaemon amazonDaemon = new AmazonDaemon();
        amazonDaemon.config();
        amazonDaemon.runAll();
    }
}
