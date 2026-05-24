package eDepot.dao;

import eDepot.models.ReplenishmentLine;
import eDepot.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

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

    // TODO: add methods to list all lines by replenishment order ID.
}
