package eMart;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.util.Properties;
import java.util.List;
import java.util.ArrayList;

import oracle.jdbc.pool.OracleDataSource;
import oracle.jdbc.OracleConnection;

public class Utility {

    public static boolean verbose = false;

    public static void log(String message) {
        if (verbose) {
            System.out.println(message);
        }
    }

    public static void log(Object obj) {
        if (verbose) {
            System.out.println(obj);
        }
    }

    public static void log() {
        if (verbose) {
            System.out.println();
        }
    }

    public static void test_import() {
        log("from another class");
    }

    public static Items[] getItems(Connection connection) throws SQLException {
        List<Items> itemsList = new ArrayList<>();
        String selectQuery = "SELECT stock_num, category, price, warranty, model_num, mname FROM Item";
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(selectQuery)) {

            while (resultSet.next()) {
                String stockNum = resultSet.getString("stock_num");
                String category = resultSet.getString("category");
                int price = (int) resultSet.getDouble("price");
                int warranty = resultSet.getInt("warranty");
                String modelNum = resultSet.getString("model_num");
                String mname = resultSet.getString("mname");

                Items item = new Items(stockNum, category, price, warranty, modelNum, mname);

                // Fetch attributes for this item
                String attrQuery = "SELECT attr_name, attr_value, attr_unit FROM Item_Attribute WHERE stock_num = ?";
                try (PreparedStatement attrStmt = connection.prepareStatement(attrQuery)) {
                    attrStmt.setString(1, stockNum);
                    try (ResultSet attrRs = attrStmt.executeQuery()) {
                        while (attrRs.next()) {
                            String name = attrRs.getString("attr_name");
                            double val = attrRs.getDouble("attr_value");
                            boolean valWasNull = attrRs.wasNull();
                            String unit = attrRs.getString("attr_unit");
                            String valStr = "";
                            if (!valWasNull) {
                                valStr = (val == (long) val) ? String.valueOf((long) val) : String.valueOf(val);
                            }
                            if (unit != null && !unit.trim().isEmpty()) {
                                if (!valStr.isEmpty()) {
                                    valStr += " " + unit.trim();
                                } else {
                                    valStr = unit.trim();
                                }
                            }
                            item.addAttribute(name, valStr);
                        }
                    }
                }

                // Fetch compatibilities for this item
                String compQuery = "SELECT replacement_stock_num FROM Compatible_With WHERE orig_stock_num = ?";
                try (PreparedStatement compStmt = connection.prepareStatement(compQuery)) {
                    compStmt.setString(1, stockNum);
                    try (ResultSet compRs = compStmt.executeQuery()) {
                        while (compRs.next()) {
                            item.addCompatibility(compRs.getString("replacement_stock_num"));
                        }
                    }
                }

                itemsList.add(item);
            }
        }
        return itemsList.toArray(new Items[0]);
    }

    public static void clearConsole() {
        // \033[H moves the cursor to the top-left corner
        // \033[2J clears the entire screen
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    public static void main(String args[]) throws SQLException {
        Properties info = new Properties();

        log("Initializing connection properties...");
        info.put(OracleConnection.CONNECTION_PROPERTY_USER_NAME, Constants.DB_USER);
        info.put(OracleConnection.CONNECTION_PROPERTY_PASSWORD, Constants.DB_PASSWORD);
        info.put(OracleConnection.CONNECTION_PROPERTY_DEFAULT_ROW_PREFETCH, "20");

        log("Creating OracleDataSource for eMart...");
        OracleDataSource odsMart = new OracleDataSource();
        odsMart.setURL(Constants.Mart_DB_URL);
        odsMart.setConnectionProperties(info);

        log("Creating OracleDataSource for eDepot...");
        OracleDataSource odsDepot = new OracleDataSource();
        odsDepot.setURL(Constants.Depot_DB_URL);
        odsDepot.setConnectionProperties(info);

        try (OracleConnection martConn = (OracleConnection) odsMart.getConnection();
                OracleConnection depotConn = (OracleConnection) odsDepot.getConnection()) {
            log("Connections established!");

            // Perform database import from CSV
            DbImporter.importItems(Constants.Items_CSV_Path, martConn, depotConn);
            DbImporter.importCustomers(Constants.Customers_CSV_Path, martConn);
            DbImporter.importManagers(Constants.Managers_CSV_Path, martConn);

            log("\nRetrieving items from the eMart database to verify...");
            Items[] dbItems = getItems(martConn);
            for (Items item : dbItems) {
                item.print();
                log();
            }
        } catch (Exception e) {
            log("CONNECTION OR SQL ERROR:");
            log(e);
        }

    }

}
