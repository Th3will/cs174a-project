package ucsb.cs174a.project;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.util.Properties;
import java.util.List;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.FileReader;

import oracle.jdbc.pool.OracleDataSource;
import oracle.jdbc.OracleConnection;
import java.sql.DatabaseMetaData;

public class Utility {

    // The recommended format of a connection URL is:
    // "jdbc:oracle:thin:@<DATABASE_NAME_LOWERCASE>_tp?TNS_ADMIN=<PATH_TO_WALLET>"
    // where
    // <DATABASE_NAME_LOWERCASE> is your database name in lowercase
    // and
    // <PATH_TO_WALLET> is the path to the connection wallet on your machine.
    // NOTE: on a Mac, there's no C: drive...


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

    // This method creates a database connection using
    // oracle.jdbc.pool.OracleDataSource.

    public static void test_import() {
        log("from another class");
    }

    // Todo: add stuff for tracking on the depot side too
    public static void createItem(Connection connection, String stock_num, String category, int price, int warranty,
            String model_num, String man_name) throws SQLException {
        // Check if manufacturer exists, if not add it
        String checkManufacturerQuery = "SELECT 1 FROM MANUFACTURER WHERE MNAME = ?";

        try (PreparedStatement statement = connection.prepareStatement(checkManufacturerQuery)) {
            statement.setString(1, man_name);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    String addManufacturerQuery = "INSERT INTO MANUFACTURER (MNAME) VALUES (?)";
                    try (PreparedStatement addManufacturerStatement = connection
                            .prepareStatement(addManufacturerQuery)) {
                        addManufacturerStatement.setString(1, man_name);
                        addManufacturerStatement.executeUpdate();
                    }
                }
            }
        }

        boolean exists = false;
        String checkItemQuery = "SELECT 1 FROM Item WHERE stock_num = ?";
        try (PreparedStatement checkStmt = connection.prepareStatement(checkItemQuery)) {
            checkStmt.setString(1, stock_num);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    exists = true;
                }
            }
        }

        if (exists) {
            String updateQuery = "UPDATE Item SET category = ?, price = ?, warranty = ?, model_num = ?, mname = ? WHERE stock_num = ?";
            try (PreparedStatement updateStmt = connection.prepareStatement(updateQuery)) {
                updateStmt.setString(1, category);
                updateStmt.setDouble(2, price);
                updateStmt.setInt(3, warranty);
                updateStmt.setString(4, model_num);
                updateStmt.setString(5, man_name);
                updateStmt.setString(6, stock_num);
                updateStmt.executeUpdate();
            } catch (Exception e) {
                log("ERROR: update failed.");
                log(e);
            }
        } else {
            String query = "INSERT INTO ITEM " +
                    "VALUES(?,?,?,?,?,?)";

            try (PreparedStatement insertionQuery = connection.prepareStatement(query)) {
                insertionQuery.setString(1, stock_num);
                insertionQuery.setString(2, category);
                insertionQuery.setDouble(3, price);
                insertionQuery.setInt(4, warranty);
                insertionQuery.setString(5, model_num);
                insertionQuery.setString(6, man_name);

                insertionQuery.executeUpdate();
            } catch (Exception e) {
                log("ERROR: insertion failed.");
                log(e);
            }
        }
    }

    public static void addAttribute(Connection connection, String stock_num, String Key, String Pair) {

    }

    public static class ParsedItem {
        public String stockNum;
        public String category;
        public String manufacturer;
        public String modelNum;
        public int warranty;
        public double price;
        public List<String> attributes = new ArrayList<>();
        public List<String> compatibilities = new ArrayList<>();
        public int minLevel;
        public int quantity;
        public int maxLevel;
        public String location;
    }

    public static class AttributeParsed {
        public String name;
        public Double value;
        public String unit;
    }

    public static AttributeParsed parseAttribute(String attributeStr) {
        int colonIndex = attributeStr.indexOf(':');
        if (colonIndex == -1) {
            return null;
        }
        String name = attributeStr.substring(0, colonIndex).trim();
        String valPart = attributeStr.substring(colonIndex + 1).trim();

        AttributeParsed parsed = new AttributeParsed();
        if (name.length() > 20) {
            name = name.substring(0, 20);
        }
        parsed.name = name;

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^([+-]?(?:\\d+\\.\\d*|\\.\\d+|\\d+))(.*)$");
        java.util.regex.Matcher matcher = pattern.matcher(valPart);
        if (matcher.matches()) {
            String numStr = matcher.group(1);
            String unitStr = matcher.group(2).trim();
            try {
                parsed.value = Double.parseDouble(numStr);
            } catch (NumberFormatException e) {
                parsed.value = null;
            }
            parsed.unit = unitStr.isEmpty() ? null : unitStr;
        } else {
            parsed.value = null;
            parsed.unit = valPart.isEmpty() ? null : valPart;
        }

        if (parsed.unit != null && parsed.unit.length() > 10) {
            parsed.unit = parsed.unit.substring(0, 10);
        }

        return parsed;
    }

    public static List<ParsedItem> parseCSV(String filePath) {
        List<ParsedItem> items = new ArrayList<>();
        ParsedItem currentItem = null;

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean isHeader = true;
            while ((line = br.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }
                String[] tokens = line.split(",", -1);
                if (tokens.length < 12) {
                    continue;
                }

                String stockNum = tokens[0].trim();
                if (!stockNum.isEmpty()) {
                    currentItem = new ParsedItem();
                    currentItem.stockNum = stockNum;
                    currentItem.category = tokens[1].trim();
                    currentItem.manufacturer = tokens[2].trim();
                    currentItem.modelNum = tokens[3].trim();

                    String desc = tokens[4].trim();
                    if (!desc.isEmpty()) {
                        currentItem.attributes.add(desc);
                    }

                    currentItem.warranty = tokens[5].trim().isEmpty() ? 0 : Integer.parseInt(tokens[5].trim());

                    String comp = tokens[6].trim();
                    if (!comp.isEmpty() && !comp.equalsIgnoreCase("None")) {
                        currentItem.compatibilities.add(comp);
                    }

                    currentItem.price = tokens[7].trim().isEmpty() ? 0.0 : Double.parseDouble(tokens[7].trim());
                    currentItem.minLevel = tokens[8].trim().isEmpty() ? 0 : Integer.parseInt(tokens[8].trim());
                    currentItem.quantity = tokens[9].trim().isEmpty() ? 0 : Integer.parseInt(tokens[9].trim());
                    currentItem.maxLevel = tokens[10].trim().isEmpty() ? 0 : Integer.parseInt(tokens[10].trim());
                    currentItem.location = tokens[11].trim();

                    items.add(currentItem);
                } else if (currentItem != null) {
                    String desc = tokens[4].trim();
                    if (!desc.isEmpty()) {
                        currentItem.attributes.add(desc);
                    }
                    String comp = tokens[6].trim();
                    if (!comp.isEmpty() && !comp.equalsIgnoreCase("None")) {
                        currentItem.compatibilities.add(comp);
                    }
                }
            }
        } catch (Exception e) {
            log("Error parsing CSV: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
        }
        return items;
    }

    public static void importItems(String csvFilePath, Connection martConn, Connection depotConn) throws SQLException {
        List<ParsedItem> parsedItems = parseCSV(csvFilePath);
        log("Parsed " + parsedItems.size() + " items from CSV.");

        // Pass 1: Upsert Items into eMart
        log("Importing items to eMart (pass 1)...");
        for (ParsedItem item : parsedItems) {
            createItem(martConn, item.stockNum, item.category, (int) item.price, item.warranty, item.modelNum,
                    item.manufacturer);
        }

        // Pass 1.5: Upsert Item Attributes into eMart
        log("Importing attributes to eMart (pass 1.5)...");
        for (ParsedItem item : parsedItems) {
            for (String attrStr : item.attributes) {
                AttributeParsed parsedAttr = parseAttribute(attrStr);
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

                    log("Stock: " + item.stockNum +
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
        }

        // Pass 2: Upsert Compatibilities into eMart
        log("Importing compatibilities to eMart (pass 2)...");
        for (ParsedItem item : parsedItems) {
            for (String replacementStockNum : item.compatibilities) {
                // Ensure target item exists first
                String checkTarget = "SELECT 1 FROM Item WHERE stock_num = ?";
                boolean targetExists = false;
                try (PreparedStatement stmt = martConn.prepareStatement(checkTarget)) {
                    stmt.setString(1, replacementStockNum);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            targetExists = true;
                        }
                    }
                }

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
                    log("Warning: Compatibility target " + replacementStockNum + " not found in Item table.");
                }
            }
        }

        // Pass 3: Upsert into eDepot
        log("Importing inventory to eDepot...");
        for (ParsedItem item : parsedItems) {
            // Manufacturer
            String checkManu = "SELECT 1 FROM Manufacturer WHERE mname = ?";
            boolean manuExists = false;
            try (PreparedStatement stmt = depotConn.prepareStatement(checkManu)) {
                stmt.setString(1, item.manufacturer);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        manuExists = true;
                    }
                }
            }
            if (!manuExists) {
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
                String checkWI = "SELECT 1 FROM Warehouse_Item WHERE stock_num = ?";
                boolean wiExists = false;
                try (PreparedStatement stmt = depotConn.prepareStatement(checkWI)) {
                    stmt.setString(1, item.stockNum);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            wiExists = true;
                        }
                    }
                }

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
        }
        log("Import completed successfully!");
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
            importItems(Constants.Items_CSV_Path, martConn, depotConn);

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

// Customer facing
class Items {
    public String stock_num;
    public String category;
    public int price;
    public int warranty;
    public String model_num;
    public String man_name;
    public List<Attribute> attributes;
    public List<String> compatibilities;

    public static class Attribute {
        public String key;
        public String value;

        public Attribute(String key, String value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String toString() {
            return key + ": " + value;
        }
    }

    public Items() {
        this.attributes = new ArrayList<>();
        this.compatibilities = new ArrayList<>();
    }

    public Items(String stock_num, String category, int price, int warranty, String model_num, String man_name) {
        this.stock_num = stock_num;
        this.category = category;
        this.price = price;
        this.warranty = warranty;
        this.model_num = model_num;
        this.man_name = man_name;
        this.attributes = new ArrayList<>();
        this.compatibilities = new ArrayList<>();
    }

    public void addAttribute(String key, String value) {
        this.attributes.add(new Attribute(key, value));
    }

    public void addCompatibility(String compatibility) {
        this.compatibilities.add(compatibility);
    }

    public void print() {
        if (Utility.verbose) {
            System.out.println(this.toString());
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("==================================================\n");
        sb.append(String.format("Item Stock No: %s (%s)\n", stock_num, category));
        sb.append("--------------------------------------------------\n");
        sb.append(String.format("  Manufacturer: %s\n", man_name));
        sb.append(String.format("  Model Number: %s\n", model_num));
        sb.append(String.format("  Price:        $%d\n", price));
        sb.append(String.format("  Warranty:     %d months\n", warranty));
        sb.append("--------------------------------------------------\n");
        sb.append("  Attributes:\n");
        if (attributes == null || attributes.isEmpty()) {
            sb.append("    (None)\n");
        } else {
            for (Attribute attr : attributes) {
                sb.append(String.format("    - %s: %s\n", attr.key, attr.value));
            }
        }
        sb.append("  Compatibilities:\n");
        if (compatibilities == null || compatibilities.isEmpty()) {
            sb.append("    (None)\n");
        } else {
            for (String comp : compatibilities) {
                sb.append(String.format("    - %s\n", comp));
            }
        }
        sb.append("==================================================");
        return sb.toString();
    }
}
