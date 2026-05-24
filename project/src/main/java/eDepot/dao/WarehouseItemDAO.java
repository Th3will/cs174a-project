package eDepot.dao;

import eDepot.models.WarehouseItem;
import eDepot.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class WarehouseItemDAO {
    public boolean insertWarehouseItem(WarehouseItem item) {
        String sql = "INSERT INTO eDepot_Warehouse_Item " +
                "(stock_num, mname, model_num, quantity, min_level, max_level, replenishment, loc_letter, loc_num) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.testConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, item.getStockNumber());
            pstmt.setString(2, item.getManufacturerName());
            pstmt.setString(3, item.getModelNumber());
            pstmt.setInt(4, item.getQuantity());
            pstmt.setInt(5, item.getMinLevel());
            pstmt.setInt(6, item.getMaxLevel());
            pstmt.setInt(7, item.getReplenishment());
            pstmt.setString(8, item.getLocationLetter());
            pstmt.setInt(9, item.getLocationNumber());

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Success: Warehouse item " + item.getStockNumber() + " inserted");
                return true;
            }
        }
        catch (SQLException e) {
            System.err.println("DB error while inserting warehouse item: " + e.getMessage());
        }
        return false;
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

    // TODO: add methods for fill-order inventory updates and replenishment checks.
}
