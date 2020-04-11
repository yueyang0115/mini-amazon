package edu.duke.ece568.erss.amazon;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import edu.duke.ece568.erss.amazon.proto.AmazonUPSProtocol;
import edu.duke.ece568.erss.amazon.proto.WorldUPSProtocol.*;
import static edu.duke.ece568.erss.amazon.Utils.recvMsgFrom;
import static edu.duke.ece568.erss.amazon.Utils.sendMsgTo;

public class MockUPS {
    private static final String HOST = "vcm-13663.vm.duke.edu";
    private static final int PORT = 12345;

    public long worldID;
    public int truckID;

    private InputStream in;
    private OutputStream out;
    private long seqNum;

    public MockUPS() throws IOException {
        this.worldID = -1;
        seqNum = 0;
        truckID = 1;
        Socket socket = new Socket(HOST, PORT);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public void init(){
        new Thread(() -> {
            // 1. create a new world
            connectToWorld(-1);
            // 2. connect to amazon and tell it the result
            try {
                Socket socket = new Socket("localhost", 9999);
                sendMsgTo(AmazonUPSProtocol.UAstart.newBuilder().setWorldid((int) worldID).setSeqnum(seqNum).build(), socket.getOutputStream());
                AmazonUPSProtocol.Res.Builder builder = AmazonUPSProtocol.Res.newBuilder();
                recvMsgFrom(builder, socket.getInputStream());
                System.out.println("ups rec: " + builder.toString());
            }catch (Exception e){
                System.err.println(e.toString());
            }
        }).start();
    }

    public boolean connectToWorld(long worldID) {
        UInitTruck.Builder builder = UInitTruck.newBuilder();
        builder.setId(1);
        builder.setX(2);
        builder.setY(2);

        UConnect.Builder connect =  UConnect.newBuilder();
        connect.setIsAmazon(false);
        connect.addTrucks(builder);
        if (worldID >= 0){
            connect.setWorldid(worldID);
        }

        UConnected.Builder connected = UConnected.newBuilder();

        sendMsgTo(connect.build(), out);
        recvMsgFrom(connected, in);

        this.worldID = connected.getWorldid();
        System.out.println("world id: " + connected.getWorldid());
        System.out.println("result: " + connected.getResult());

        return connected.getResult().equals("connected!");
    }

    public void pick(int whID){
        new Thread(() -> {
            UCommands.Builder command = UCommands.newBuilder();

            UGoPickup.Builder pick = UGoPickup.newBuilder();
            pick.setWhid(whID);
            pick.setTruckid(truckID);
            pick.setSeqnum(seqNum);
            command.addPickups(pick);

            UResponses.Builder responses = UResponses.newBuilder();

            sendMsgTo(command.build(), out);
            recvMsgFrom(responses, in);
            System.out.println(responses.toString());

            if (responses.getCompletionsCount() == 0){
                responses.clear();
                recvMsgFrom(responses, in);
                System.out.println(responses.toString());
            }
            seqNum++;

            List<Long> seqs = new ArrayList<>();
            for (UFinished finished : responses.getCompletionsList()){
                seqs.add(finished.getSeqnum());
            }
            sendAck(seqs);

            // TODO: send back result
            try{
                Socket socket = new Socket("localhost", 9999);
                AmazonUPSProtocol.UApicked.Builder picked = AmazonUPSProtocol.UApicked.newBuilder();

                picked.setSeqnum(seqNum);
                picked.setShipid(2);
                picked.setTruckid(1);

                AmazonUPSProtocol.UAcommand.Builder c = AmazonUPSProtocol.UAcommand.newBuilder();
                c.addPick(picked);

                AmazonUPSProtocol.Res.Builder r = AmazonUPSProtocol.Res.newBuilder();

                System.out.println("ups send: " + c.toString());
                sendMsgTo(c.build(), socket.getOutputStream());
                recvMsgFrom(r, socket.getInputStream());
                // TODO: check ack
                if(r.getAck(0) == seqNum){
                    seqNum++;
                    System.out.println("ups pick receive correct ack");
                }
            }catch (IOException e){
                System.err.println(e.toString());
            }
        }).start();
    }

    public void delivery(int destX, int destY, long packageID){
        new Thread(() -> {
            UCommands.Builder command = UCommands.newBuilder();

            UGoDeliver.Builder delivery = UGoDeliver.newBuilder();
            delivery.setTruckid(truckID);
            delivery.addPackages(UDeliveryLocation.newBuilder().setPackageid(packageID).setX(destX).setY(destY));
            delivery.setSeqnum(seqNum);
            command.addDeliveries(delivery);

            UResponses.Builder responses = UResponses.newBuilder();

            sendMsgTo(command.build(), out);
            recvMsgFrom(responses, in);
            System.out.println(responses.toString());

            if (responses.getDeliveredCount() == 0){
                responses.clear();
                recvMsgFrom(responses, in);
                System.out.println(responses.toString());
            }
            seqNum++;

            List<Long> seqs = new ArrayList<>();
            for (UDeliveryMade d : responses.getDeliveredList()){
                seqs.add(d.getSeqnum());
            }
            sendAck(seqs);

            // TODO: send back result
            try{
                Socket socket = new Socket("localhost", 9999);
                AmazonUPSProtocol.UAdelivered.Builder delivered = AmazonUPSProtocol.UAdelivered.newBuilder();

                delivered.setSeqnum(seqNum);
                delivered.setShipid(2);

                AmazonUPSProtocol.UAcommand.Builder c = AmazonUPSProtocol.UAcommand.newBuilder();
                c.addDeliver(delivered);

                AmazonUPSProtocol.Res.Builder r = AmazonUPSProtocol.Res.newBuilder();

                System.out.println("ups send: " + c.toString());
                sendMsgTo(c.build(), socket.getOutputStream());
                recvMsgFrom(r, socket.getInputStream());
                // TODO: check ack
                if(r.getAck(0) == seqNum){
                    seqNum++;
                    System.out.println("ups delivery receive correct ack");
                }
            }catch (IOException e){
                System.err.println(e.toString());
            }
        }).start();
    }

    public void disconnect(){
        UCommands.Builder command = UCommands.newBuilder();
        command.setDisconnect(true);

        UResponses.Builder responses = UResponses.newBuilder();

        sendMsgTo(command.build(), out);
        recvMsgFrom(responses, in);
        System.out.println(responses.toString());

        if (responses.hasFinished()){
            System.out.println("ups disconnect finish");
        }
        seqNum++;
    }

    void sendAck(List<Long> seqs){
        UCommands.Builder commands = UCommands.newBuilder();
        for (long seq : seqs){
            commands.addAcks(seq);
        }
        sendMsgTo(commands.build(), out);
    }
}
