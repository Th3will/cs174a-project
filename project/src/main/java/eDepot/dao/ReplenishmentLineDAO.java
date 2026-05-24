package eDepot.dao;

import eDepot.models.ReplenishmentLine;
import eDepot.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;

import java.util.List;
import java.util.ArrayList;

public class ReplenishmentLineDAO {
    public boolean insertReplenishmentLine(ReplenishmentLine line) {
        String sql = "INSERT INTO eDepot_Replenishment_Line (oid, stock_num, replenishment_quantity) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConnection.testConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, line.getOrderId());
            pstmt.setString(2, line.getStockNumber());
            pstmt.setInt(3, line.getReplenishmentQuantity());

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Success: Replenishment line for order " + line.getOrderId() + " inserted");
                return true;
            }
        }
        catch (SQLException e) {
            System.err.println("DB error while inserting replenishment line: " + e.getMessage());
        }
        return false;
    }

    // return all lines in a replenishment order corresponding to a specific replenishment order ID
    public List<ReplenishmentLine> getLinesForReplenishmentOrder(int oid) {
        List<ReplenishmentLine> replenishmentLines = new ArrayList<>();
        String sql = "SELECT oid, stock_num, replenishment_quantity FROM eDepot_Replenishment_Line WHERE oid = ?";

        try (Connection conn = DatabaseConnection.testConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, oid);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int row_oid = rs.getInt("oid");
                    String row_stock_num = rs.getString("stock_num");
                    int row_replenishment_quantity = rs.getInt("replenishment_quantity");

                    ReplenishmentLine line = new ReplenishmentLine(row_oid, row_stock_num, row_replenishment_quantity);
                    replenishmentLines.add(line);
                }
            }
            return replenishmentLines;
        }
        catch (SQLException e) {
            System.err.println("DB error while fetching shipping notice line: " + e.getMessage());
        }
        return replenishmentLines;
    }
}
