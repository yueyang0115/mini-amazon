package edu.duke.ece568.erss.amazon;

import edu.duke.ece568.erss.amazon.proto.WorldAmazonProtocol.APack;

public class Package {
    public static final String PROCESSING = "processing";
    public static final String PROCESSED = "processed";
    public static final String PACKING = "packing";
    public static final String PACKED = "packed";
    public static final String LOADING = "loading";
    public static final String LOADED = "loaded";
    public static final String DELIVERING = "delivering";
    public static final String DELIVERED = "delivered";
    public static final String ERROR = "error";

    private long id;
    private int whID;
    private int truckID;
    private int destX;
    private int destY;
    private String status;
    private APack pack;

    public Package(long id, int whID, APack pack) {
        this.id = id;
        this.whID = whID;
        this.pack = pack;
        this.truckID = -1;
        // we will only create the package object after purchase successful, so the initial state should be PROCESSED
        setStatus(PROCESSED);
        // TODO: fetch the actual data from database
        this.destX = 10;
        this.destY = 10;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getWhID() {
        return whID;
    }

    public void setWhID(int whID) {
        this.whID = whID;
    }

    public int getTruckID() {
        return truckID;
    }

    public void setTruckID(int truckID) {
        this.truckID = truckID;
    }

    public int getDestX() {
        return destX;
    }

    public void setDestX(int destX) {
        this.destX = destX;
    }

    public int getDestY() {
        return destY;
    }

    public void setDestY(int destY) {
        this.destY = destY;
    }

    public String getStatus() {
        return status;
    }

    public APack getPack() {
        return pack;
    }

    public void setDest(int x, int y){
        this.destX = x;
        this.destY = y;
    }

    /**
     * This function will update the status of current package, and also write the statue into database.
     * @param status latest status
     */
    public void setStatus(String status){
        this.status = status;
        // write the result into DB
        new SQL().updateStatus(this.id, this.status);
    }
}
