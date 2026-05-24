package eDepot.dao;

import eDepot.models.ReplenishmentOrder;
import eDepot.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ReplenishmentOrderDAO {
    public boolean insertReplenishmentOrder(ReplenishmentOrder order) {
        String sql = "INSERT INTO eDepot_Replenishment_Order (oid, mname) VALUES (?, ?)";

        try (Connection conn = DatabaseConnection.testConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, order.getOrderId());
            pstmt.setString(2, order.getManufacturerName());

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Success: Replenishment order " + order.getOrderId() + " inserted");
                return true;
            }
        }
        catch (SQLException e) {
            System.err.println("DB error while inserting replenishment order: " + e.getMessage());
        }
        return false;
    }

    // TODO: add query methods for order history and generated reorder workflows.
}
