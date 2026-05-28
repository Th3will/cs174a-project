package eDepot.dao;

import eDepot.models.WarehouseItem;
import eDepot.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WarehouseItemDAO {
    private static final String INSERT_SQL =
            "INSERT INTO eDepot_Warehouse_Item " +
            "(stock_num, mname, model_num, quantity, min_level, max_level, replenishment, loc_letter, loc_num) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    public boolean insertWarehouseItem(WarehouseItem item) {
        try (Connection conn = DatabaseConnection.testConnection()) {
            return insertWarehouseItem(conn, item);
        }
        catch (SQLException e) {
            System.err.println("DB error while inserting warehouse item: " + e.getMessage());
            return false;
        }
    }

    public boolean insertWarehouseItem(Connection conn, WarehouseItem item) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(INSERT_SQL)) {
            pstmt.setString(1, item.getStockNumber());
            pstmt.setString(2, item.getManufacturerName());
            pstmt.setString(3, item.getModelNumber());
            pstmt.setInt(4, item.getQuantity());
            pstmt.setInt(5, item.getMinLevel());
            pstmt.setInt(6, item.getMaxLevel());
            pstmt.setInt(7, item.getReplenishment());
            pstmt.setString(8, item.getLocationLetter());
            pstmt.setInt(9, item.getLocationNumber());
            return pstmt.executeUpdate() > 0;
        }
    }

    public Integer getQuantityByStockNumber(String stockNumber) {
        String sql = "SELECT quantity FROM eDepot_Warehouse_Item WHERE stock_num = ?";

        try (Connection conn = DatabaseConnection.testConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, stockNumber);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("quantity");
                }
            }
        }
        catch (SQLException e) {
            System.err.println("DB error while fetching item quantity: " + e.getMessage());
        }
        return null;
    }

    public Integer getQuantityByStockNumber(Connection conn, String stockNumber) throws SQLException {
        String sql = "SELECT quantity FROM eDepot_Warehouse_Item WHERE stock_num = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, stockNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt("quantity") : null;
            }
        }
    }

    public WarehouseItem findByManufacturerAndModel(Connection conn, String manufacturerName, String modelNumber)
            throws SQLException {
        String sql = "SELECT stock_num, mname, model_num, quantity, min_level, max_level, " +
                "replenishment, loc_letter, loc_num " +
                "FROM eDepot_Warehouse_Item WHERE mname = ? AND model_num = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, manufacturerName);
            pstmt.setString(2, modelNumber);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new WarehouseItem(
                        rs.getString("stock_num"),
                        rs.getString("mname"),
                        rs.getString("model_num"),
                        rs.getInt("quantity"),
                        rs.getInt("min_level"),
                        rs.getInt("max_level"),
                        rs.getInt("replenishment"),
                        rs.getString("loc_letter"),
                        rs.getInt("loc_num"));
            }
        }
    }

    public boolean addReplenishment(Connection conn, String stockNumber, int notice_quantity) throws SQLException {
        String sql = "UPDATE eDepot_Warehouse_Item SET replenishment = replenishment + ? WHERE stock_num = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, notice_quantity);
            pstmt.setString(2, stockNumber);
            return pstmt.executeUpdate() > 0;
        }
    }

    public WarehouseItem findByStockNumber(Connection conn, String stockNumber) throws SQLException {
        String sql = "SELECT stock_num, mname, model_num, quantity, min_level, max_level, " +
                "replenishment, loc_letter, loc_num " +
                "FROM eDepot_Warehouse_Item WHERE stock_num = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, stockNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new WarehouseItem(
                        rs.getString("stock_num"),
                        rs.getString("mname"),
                        rs.getString("model_num"),
                        rs.getInt("quantity"),
                        rs.getInt("min_level"),
                        rs.getInt("max_level"),
                        rs.getInt("replenishment"),
                        rs.getString("loc_letter"),
                        rs.getInt("loc_num"));
            }
        }
    }

    /*
     * Apply a received physical shipment in one SQL roundtrip: for every line
     * of the given shipping notice, move the notice_quantity out of
     * replenishment and into quantity on its warehouse item. Returns the
     * number of rows updated (one per distinct stock number on the notice).
     *
     * The chk_qty_limit and chk_wi_replenish CHECK constraints in the schema
     * will roll the MERGE back if the move would push quantity past max_level
     * or replenishment below 0, so the caller does not need to pre-validate.
     */
    public int applyShipmentForNotice(Connection conn, int snid) throws SQLException {
        String sql =
                "MERGE INTO eDepot_Warehouse_Item wi " +
                "USING (SELECT stock_num, notice_quantity FROM eDepot_Notice_Line WHERE snid = ?) nl " +
                "ON (wi.stock_num = nl.stock_num) " +
                "WHEN MATCHED THEN UPDATE SET " +
                "    wi.quantity = wi.quantity + nl.notice_quantity, " +
                "    wi.replenishment = wi.replenishment - nl.notice_quantity";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, snid);
            return pstmt.executeUpdate();
        }
    }

    /*
     * Generate the next available stock number in the XXnnnnn format.
     * Strategy: take the lexicographic MAX, increment the 5-digit suffix; if the
     * suffix would overflow 99999, advance the 2-letter prefix (AA -> AB -> ... -> ZZ).
     * Returns "AA00001" when no items exist yet.
     */
    public String generateNextStockNumber(Connection conn) throws SQLException {
        String sql = "SELECT MAX(stock_num) AS max_sn FROM eDepot_Warehouse_Item";

        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            if (!rs.next() || rs.getString("max_sn") == null) {
                return "AA00001";
            }
            String current = rs.getString("max_sn");
            return incrementStockNumber(current);
        }
    }

    static String incrementStockNumber(String stockNumber) {
        if (stockNumber == null || !stockNumber.matches("^[A-Z]{2}[0-9]{5}$")) {
            throw new IllegalArgumentException("Invalid stock number for increment: " + stockNumber);
        }
        String letters = stockNumber.substring(0, 2);
        int digits = Integer.parseInt(stockNumber.substring(2));

        if (digits < 99999) {
            return letters + String.format("%05d", digits + 1);
        }
        // Rolled over the numeric portion - advance the letter prefix.
        String nextLetters = incrementLetterPair(letters);
        return nextLetters + "00000";
    }

    private static String incrementLetterPair(String letters) {
        char hi = letters.charAt(0);
        char lo = letters.charAt(1);
        if (lo < 'Z') {
            return "" + hi + (char) (lo + 1);
        }
        if (hi < 'Z') {
            return "" + (char) (hi + 1) + 'A';
        }
        throw new IllegalStateException("Stock number space exhausted past ZZ99999");
    }

    /*
     * Decrement on-hand quantity for one stock number. The "quantity >= ?" guard
     * means the UPDATE silently returns 0 rows when the stock is insufficient,
     * which the caller can use to abort the whole fillOrder transaction with a
     * precise error. The chk_wi_qty CHECK in the schema is a backstop in case
     * a concurrent transaction beats us to the row between read and write.
     */
    public boolean decrementQuantity(Connection conn, String stockNumber, int orderQuantity) throws SQLException {
        String sql = "UPDATE eDepot_Warehouse_Item SET quantity = quantity - ? "
                + "WHERE stock_num = ? AND quantity >= ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, orderQuantity);
            pstmt.setString(2, stockNumber);
            pstmt.setInt(3, orderQuantity);
            return pstmt.executeUpdate() > 0;
        }
    }

    /*
     * Count of items belonging to the given manufacturer whose current on-hand
     * quantity sits strictly below their min_level. Used as the replenishment
     * trigger - per the spec, 3+ such items on the same manufacturer kicks off
     * a replenishment order for that manufacturer.
     */
    public int countItemsBelowMinForManufacturer(Connection conn, String manufacturerName) throws SQLException {
        String sql = "SELECT COUNT(*) AS cnt FROM eDepot_Warehouse_Item "
                + "WHERE mname = ? AND quantity < min_level";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, manufacturerName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt("cnt") : 0;
            }
        }
    }

    /*
     * All items belonging to the given manufacturer that still have headroom in
     * the warehouse - i.e. quantity + replenishment < max_level. The strict
     * inequality matches the inclusion rule we picked: anything at the
     * cap is left out, and a row with quantity + replenishment < max_level
     * needs (max_level - quantity - replenishment) more units to top up to max.
     * 
     * TLDR: return a list of item from a specific manufacturer, where each item's quantity + replenishment < max stock level
     */
    public List<WarehouseItem> findItemsBelowMaxForManufacturer(Connection conn, String manufacturerName)
            throws SQLException {
        String sql = "SELECT stock_num, mname, model_num, quantity, min_level, max_level, "
                + "replenishment, loc_letter, loc_num "
                + "FROM eDepot_Warehouse_Item "
                + "WHERE mname = ? AND quantity + replenishment < max_level "
                + "ORDER BY stock_num";

        List<WarehouseItem> items = new ArrayList<>();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, manufacturerName);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    items.add(new WarehouseItem(
                            rs.getString("stock_num"),
                            rs.getString("mname"),
                            rs.getString("model_num"),
                            rs.getInt("quantity"),
                            rs.getInt("min_level"),
                            rs.getInt("max_level"),
                            rs.getInt("replenishment"),
                            rs.getString("loc_letter"),
                            rs.getInt("loc_num")));
                }
            }
        }
        return items;
    }
}
