package eDepot.dao;

import eDepot.models.WarehouseItem;
import eDepot.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

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

    // TODO: add methods for fill-order inventory updates and replenishment checks.
}
