package edu.duke.ece568.erss.amazon;
import edu.duke.ece568.erss.amazon.proto.WorldAmazonProtocol.*;

import java.sql.*;

public class SQL {

    // table name
    private static final String TABLE_ITEM = "amazon_item";
    private static final String TABLE_PRODUCT = "amazon_product";
    private static final String TABLE_PACKAGE = "amazon_package";
    // database configuration
    private static final String dbUrl = "jdbc:postgresql://localhost:5432/amazon";
    private static final String dbUser = "postgres";
    private static final String dbPassword = "postgres";

    public SQL() {

    }

    /**
     * This function will query the package info from database and construct a APurchaseMore object by this info.
     * NOTE: this APurchase doesn't contain the sequence number, so the return is a builder(which you can edit)
     * @param packageID newly create package ID
     * @return APurchaseMore.Builder
     */
    public APurchaseMore.Builder queryPackage(long packageID){
        try {
            Class.forName("org.postgresql.Driver");
            Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            conn.setAutoCommit(false);

            Statement statement = conn.createStatement();
            ResultSet result = statement.executeQuery(String.format(
                    "SELECT item.id, item.description, product.cnt " +
                            "FROM %s AS item, %s AS product " +
                            "WHERE item.id=product.item_id AND item.id " +
                            "IN (SELECT item_id FROM %s WHERE package_id = %d);",
                    TABLE_ITEM, TABLE_PRODUCT, TABLE_PRODUCT, packageID)
            );

            APurchaseMore.Builder purchase = APurchaseMore.newBuilder();
            purchase.setWhnum(queryWHNum(packageID));
            while (result.next()){
                int itemID = result.getInt("id");
                int cnt = result.getInt("cnt");
                String des = result.getString("description");
                purchase.addThings(AProduct.newBuilder().setId(itemID).setDescription(des).setCount(cnt));
            }

            statement.close();
            conn.close();
            return purchase;
        }catch (Exception e){
            System.err.println(e.toString());
        }
        return null;
    }

    /**
     * Query the corresponding ware house number of the package.
     * @param packageID package id
     * @return warehouse number(id)
     * @throws SQLException
     * @throws ClassNotFoundException
     */
    public int queryWHNum(long packageID) throws SQLException, ClassNotFoundException {
        Class.forName("org.postgresql.Driver");
        Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
        conn.setAutoCommit(false);

        Statement statement = conn.createStatement();
        ResultSet result = statement.executeQuery(String.format(
                "SELECT warehouse FROM %s WHERE id = %d;",
                TABLE_PACKAGE, packageID)
        );
        int whNum = -1;
        if (result.next()){
            whNum = result.getInt("warehouse");
        }

        statement.close();
        conn.close();
        return whNum;
    }

    public boolean updateStatus(long packageID, String status) {
        try {
            Class.forName("org.postgresql.Driver");
            Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            conn.setAutoCommit(false);

            Statement statement = conn.createStatement();
            statement.executeUpdate(String.format(
                    "UPDATE %s SET status='%s' WHERE id=%d;",
                    TABLE_PACKAGE, status, packageID)
            );
            conn.commit();

            statement.close();
            conn.close();
            return true;
        }catch (Exception e){
            System.err.println(e.toString());
        }
        return false;
    }

    public Destination queryPackageDest(long packageID) {
        Destination destination = null;
        try {
            Class.forName("org.postgresql.Driver");
            Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            conn.setAutoCommit(false);

            Statement statement = conn.createStatement();
            ResultSet result = statement.executeQuery(String.format(
                    "SELECT dest_x, dest_y FROM %s WHERE id = %d;",
                    TABLE_PACKAGE, packageID)
            );


            if (result.next()){
                destination = new Destination(result.getInt("dest_x"), result.getInt("dest_y"));
            }

            statement.close();
            conn.close();
        }catch (SQLException | ClassNotFoundException e){
            System.err.println(e.toString());
        }
        return destination;
    }

    public static void main(String[] args) {
        SQL sql = new SQL();
        System.out.println(sql.queryPackage(2));
    }
}

