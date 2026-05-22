package ucsb.cs174a.project;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class DbImporter {

    public static NameSplit splitName(String fullName) {
        String[] nameParts = fullName.trim().split("\\s+");
        String firstName = "";
        String middleName = null;
        String lastName = "";
        if (nameParts.length == 1) {
            firstName = nameParts[0];
        } else if (nameParts.length == 2) {
            firstName = nameParts[0];
            lastName = nameParts[1];
        } else if (nameParts.length == 3) {
            firstName = nameParts[0];
            middleName = nameParts[1];
            lastName = nameParts[2];
        } else if (nameParts.length > 3) {
            firstName = nameParts[0];
            lastName = nameParts[nameParts.length - 1];
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < nameParts.length - 1; i++) {
                if (i > 1)
                    sb.append(" ");
                sb.append(nameParts[i]);
            }
            middleName = sb.toString();
        }
        return new NameSplit(firstName, middleName, lastName);
    }

    public static boolean recordExists(Connection conn, String tableName, String idColumn, String idValue)
            throws SQLException {
        String query = "SELECT 1 FROM " + tableName + " WHERE " + idColumn + " = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, idValue);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static void ensureStatusExists(Connection conn, String levelName) throws SQLException {
        if (levelName == null || levelName.trim().isEmpty()) {
            return;
        }
        if (recordExists(conn, "status", "level_name", levelName)) {
            return;
        }
        String insertQuery = "INSERT INTO status (level_name, threshold, shipping_fee, discount) VALUES (?, 0.0, 0.0, 0.0)";
        try (PreparedStatement stmt = conn.prepareStatement(insertQuery)) {
            stmt.setString(1, levelName);
            stmt.executeUpdate();
        }
    }

    public static void createItem(Connection connection, String stock_num, String category, int price, int warranty,
            String model_num, String man_name) throws SQLException {
        
        if (!recordExists(connection, "MANUFACTURER", "MNAME", man_name)) {
            String addManufacturerQuery = "INSERT INTO MANUFACTURER (MNAME) VALUES (?)";
            try (PreparedStatement stmt = connection.prepareStatement(addManufacturerQuery)) {
                stmt.setString(1, man_name);
                stmt.executeUpdate();
            }
        }

        if (recordExists(connection, "Item", "stock_num", stock_num)) {
            String updateQuery = "UPDATE Item SET category = ?, price = ?, warranty = ?, model_num = ?, mname = ? WHERE stock_num = ?";
            try (PreparedStatement stmt = connection.prepareStatement(updateQuery)) {
                stmt.setString(1, category);
                stmt.setDouble(2, price);
                stmt.setInt(3, warranty);
                stmt.setString(4, model_num);
                stmt.setString(5, man_name);
                stmt.setString(6, stock_num);
                stmt.executeUpdate();
            } catch (Exception e) {
                Utility.log("ERROR: update failed.");
                if (Utility.verbose) {
                    e.printStackTrace();
                }
            }
        } else {
            String query = "INSERT INTO ITEM (stock_num, category, price, warranty, model_num, mname) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, stock_num);
                stmt.setString(2, category);
                stmt.setDouble(3, price);
                stmt.setInt(4, warranty);
                stmt.setString(5, model_num);
                stmt.setString(6, man_name);
                stmt.executeUpdate();
            } catch (Exception e) {
                Utility.log("ERROR: insertion failed.");
                if (Utility.verbose) {
                    e.printStackTrace();
                }
            }
        }
    }
            
    // TODO: Make prettier
    public static void importItems(String csvFilePath, Connection martConn, Connection depotConn) throws SQLException {
        List<ParsedItem> parsedItems = CSVParser.parseCSV(csvFilePath);

        // Pass 1: Upsert Items into eMart
        importEntity(csvFilePath, "items (pass 1)", (path) -> parsedItems, (item) -> {
            createItem(martConn, item.stockNum, item.category, (int) item.price, item.warranty, item.modelNum,
                    item.manufacturer);
        });

        // Pass 2: Upsert Item Attributes, Compatibilities and Depot Inventory
        importEntity(csvFilePath, "item details and inventory (pass 2)", (path) -> parsedItems, (item) -> {
            // 1. Attributes
            for (String attrStr : item.attributes) {
                AttributeParsed parsedAttr = CSVParser.parseAttribute(attrStr);
                if (parsedAttr != null) {
                    String checkAttr = "SELECT 1 FROM Item_Attribute WHERE stock_num = ? AND attr_name = ?";
                    boolean attrExists = false;
                    try (PreparedStatement checkStmt = martConn.prepareStatement(checkAttr)) {
                        checkStmt.setString(1, item.stockNum);
                        checkStmt.setString(2, parsedAttr.name);
                        try (ResultSet rs = checkStmt.executeQuery()) {
                            if (rs.next()) {
                                attrExists = true;
                            }
                        }
                    }

                    Utility.log("Stock: " + item.stockNum +
                            ", Name: " + (parsedAttr.name == null ? "Null" : parsedAttr.name) +
                            ", Value: " + (parsedAttr.value == null ? "Null" : parsedAttr.value) +
                            ", Unit: " + (parsedAttr.unit == null ? "Null" : parsedAttr.unit));

                    if (attrExists) {
                        String updateAttr = "UPDATE Item_Attribute SET attr_value = ?, attr_unit = ? WHERE stock_num = ? AND attr_name = ?";
                        try (PreparedStatement updateStmt = martConn.prepareStatement(updateAttr)) {
                            if (parsedAttr.value != null) {
                                updateStmt.setDouble(1, parsedAttr.value);
                            } else {
                                updateStmt.setNull(1, java.sql.Types.DOUBLE);
                            }
                            updateStmt.setString(2, parsedAttr.unit);
                            updateStmt.setString(3, item.stockNum);
                            updateStmt.setString(4, parsedAttr.name);
                            updateStmt.executeUpdate();
                        }
                    } else {
                        String insertAttr = "INSERT INTO Item_Attribute (stock_num, attr_name, attr_value, attr_unit) VALUES (?, ?, ?, ?)";
                        try (PreparedStatement insertStmt = martConn.prepareStatement(insertAttr)) {
                            insertStmt.setString(1, item.stockNum);
                            insertStmt.setString(2, parsedAttr.name);
                            if (parsedAttr.value != null) {
                                insertStmt.setDouble(3, parsedAttr.value);
                            } else {
                                insertStmt.setNull(3, java.sql.Types.DOUBLE);
                            }
                            insertStmt.setString(4, parsedAttr.unit);
                            insertStmt.executeUpdate();
                        }
                    }
                }
            }

            // 2. Compatibilities
            for (String replacementStockNum : item.compatibilities) {
                // Ensure target item exists first
                boolean targetExists = recordExists(martConn, "Item", "stock_num", replacementStockNum);

                if (targetExists) {
                    String checkComp = "SELECT 1 FROM Compatible_With WHERE orig_stock_num = ? AND replacement_stock_num = ?";
                    boolean compExists = false;
                    try (PreparedStatement stmt = martConn.prepareStatement(checkComp)) {
                        stmt.setString(1, item.stockNum);
                        stmt.setString(2, replacementStockNum);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                compExists = true;
                            }
                        }
                    }

                    if (!compExists) {
                        String insertComp = "INSERT INTO Compatible_With (orig_stock_num, replacement_stock_num) VALUES (?, ?)";
                        try (PreparedStatement stmt = martConn.prepareStatement(insertComp)) {
                            stmt.setString(1, item.stockNum);
                            stmt.setString(2, replacementStockNum);
                            stmt.executeUpdate();
                        }
                    }
                } else {
                    Utility.log("Warning: Compatibility target " + replacementStockNum + " not found in Item table.");
                }
            }

            // 3. Depot Inventory
            // Manufacturer
            if (!recordExists(depotConn, "Manufacturer", "mname", item.manufacturer)) {
                String addManu = "INSERT INTO Manufacturer (mname) VALUES (?)";
                try (PreparedStatement stmt = depotConn.prepareStatement(addManu)) {
                    stmt.setString(1, item.manufacturer);
                    stmt.executeUpdate();
                }
            }

            // Location
            if (item.location != null && !item.location.trim().isEmpty()) {
                String loc = item.location.trim().toUpperCase();
                char letter = loc.charAt(0);
                int num = Integer.parseInt(loc.substring(1));

                String checkLoc = "SELECT 1 FROM Location WHERE letter = ? AND num = ?";
                boolean locExists = false;
                try (PreparedStatement stmt = depotConn.prepareStatement(checkLoc)) {
                    stmt.setString(1, String.valueOf(letter));
                    stmt.setInt(2, num);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            locExists = true;
                        }
                    }
                }
                if (!locExists) {
                    String addLoc = "INSERT INTO Location (letter, num) VALUES (?, ?)";
                    try (PreparedStatement stmt = depotConn.prepareStatement(addLoc)) {
                        stmt.setString(1, String.valueOf(letter));
                        stmt.setInt(2, num);
                        stmt.executeUpdate();
                    }
                }

                // Warehouse_Item
                boolean wiExists = recordExists(depotConn, "Warehouse_Item", "stock_num", item.stockNum);

                if (wiExists) {
                    String updateWI = "UPDATE Warehouse_Item SET mname = ?, model_num = ?, quantity = ?, min_level = ?, max_level = ?, loc_letter = ?, loc_num = ? WHERE stock_num = ?";
                    try (PreparedStatement stmt = depotConn.prepareStatement(updateWI)) {
                        stmt.setString(1, item.manufacturer);
                        stmt.setString(2, item.modelNum);
                        stmt.setInt(3, item.quantity);
                        stmt.setInt(4, item.minLevel);
                        stmt.setInt(5, item.maxLevel);
                        stmt.setString(6, String.valueOf(letter));
                        stmt.setInt(7, num);
                        stmt.setString(8, item.stockNum);
                        stmt.executeUpdate();
                    }
                } else {
                    String insertWI = "INSERT INTO Warehouse_Item (stock_num, mname, model_num, quantity, min_level, max_level, loc_letter, loc_num) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement stmt = depotConn.prepareStatement(insertWI)) {
                        stmt.setString(1, item.stockNum);
                        stmt.setString(2, item.manufacturer);
                        stmt.setString(3, item.modelNum);
                        stmt.setInt(4, item.quantity);
                        stmt.setInt(5, item.minLevel);
                        stmt.setInt(6, item.maxLevel);
                        stmt.setString(7, String.valueOf(letter));
                        stmt.setInt(8, num);
                        stmt.executeUpdate();
                    }
                }
            }
        });
    }

    @FunctionalInterface
    public interface CsvParserFunction<T> {
        List<T> parse(String csvFilePath) throws Exception;
    }

    @FunctionalInterface
    public interface DbUpsertConsumer<T> {
        void accept(T item) throws SQLException;
    }

    public static <T> void importEntity(String csvFilePath, String entityName, CsvParserFunction<T> parser, DbUpsertConsumer<T> upsert) {
        Utility.log("Importing " + entityName + " from " + csvFilePath + "...");
            
        try {
            List<T> records = parser.parse(csvFilePath);
            Utility.log("Parsed " + records.size() + " " + entityName + " records.");
            for (T record : records) {
                upsert.accept(record);
            }
            Utility.log("Finished importing " + entityName + ".");
        } catch (Exception e) {
            Utility.log("Error importing " + entityName + ": " + e.getMessage());
            if (Utility.verbose) {
                e.printStackTrace();
            }
        }
    }

    public static void importCustomers(String csvFilePath, Connection conn) throws SQLException {
        importEntity(csvFilePath, "customers", CSVParser::parseCustomers, (row) -> {
            NameSplit ns = splitName(row.name);
            ensureStatusExists(conn, row.status);

            if (recordExists(conn, "customer", "cid", row.cid)) {
                String updateQuery = "UPDATE customer SET first_name = ?, middle_name = ?, last_name = ?, password = ?, email = ?, address = ?, level_name = ? WHERE cid = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateQuery)) {
                    stmt.setString(1, ns.firstName);
                    stmt.setString(2, ns.middleName);
                    stmt.setString(3, ns.lastName);
                    stmt.setString(4, row.password);
                    stmt.setString(5, row.email);
                    stmt.setString(6, row.address);
                    stmt.setString(7, row.status);
                    stmt.setString(8, row.cid);
                    stmt.executeUpdate();
                }
            } else {
                String insertQuery = "INSERT INTO customer (cid, first_name, middle_name, last_name, password, email, address, level_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(insertQuery)) {
                    stmt.setString(1, row.cid);
                    stmt.setString(2, ns.firstName);
                    stmt.setString(3, ns.middleName);
                    stmt.setString(4, ns.lastName);
                    stmt.setString(5, row.password);
                    stmt.setString(6, row.email);
                    stmt.setString(7, row.address);
                    stmt.setString(8, row.status);
                    stmt.executeUpdate();
                }
            }
        });
    }

    public static void importManagers(String csvFilePath, Connection conn) throws SQLException {
        importEntity(csvFilePath, "managers", CSVParser::parseManagers, (row) -> {
            NameSplit ns = splitName(row.name);

            if (recordExists(conn, "manager", "eid", row.eid)) {
                String updateQuery = "UPDATE manager SET first_name = ?, middle_name = ?, last_name = ?, password = ?, email = ?, address = ? WHERE eid = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateQuery)) {
                    stmt.setString(1, ns.firstName);
                    stmt.setString(2, ns.middleName);
                    stmt.setString(3, ns.lastName);
                    stmt.setString(4, row.password);
                    stmt.setString(5, row.email);
                    stmt.setString(6, row.address);
                    stmt.setString(7, row.eid);
                    stmt.executeUpdate();
                }
            } else {
                String insertQuery = "INSERT INTO manager (eid, first_name, middle_name, last_name, password, email, address) VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(insertQuery)) {
                    stmt.setString(1, row.eid);
                    stmt.setString(2, ns.firstName);
                    stmt.setString(3, ns.middleName);
                    stmt.setString(4, ns.lastName);
                    stmt.setString(5, row.password);
                    stmt.setString(6, row.email);
                    stmt.setString(7, row.address);
                    stmt.executeUpdate();
                }
            }
        });
    }
}
