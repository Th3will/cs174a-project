package eDepot.dao;

import eDepot.models.ReplenishmentOrder;
import eDepot.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ReplenishmentOrderDAO {
    public boolean insertReplenishmentOrder(ReplenishmentOrder order) {
        try (Connection conn = DatabaseConnection.testConnection()) {
            return insertReplenishmentOrder(conn, order);
        }
        catch (SQLException e) {
            System.err.println("DB error while inserting replenishment order: " + e.getMessage());
            return false;
        }
    }

    public boolean insertReplenishmentOrder(Connection conn, ReplenishmentOrder order) throws SQLException {
        String sql = "INSERT INTO eDepot_Replenishment_Order (oid, mname) VALUES (?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, order.getOrderId());
            pstmt.setString(2, order.getManufacturerName());
            return pstmt.executeUpdate() > 0;
        }
    }

    /*
     * Pick the next replenishment order ID for fillOrder to use. NVL(MAX,0)+1
     * keeps the first-ever order at 1 and gives a stable monotonic sequence
     * without needing an Oracle SEQUENCE object.
     */
    public int generateNextOrderId(Connection conn) throws SQLException {
        String sql = "SELECT NVL(MAX(oid), 0) + 1 AS next_oid FROM eDepot_Replenishment_Order";

        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (!rs.next()) {
                return 1;
            }
            return rs.getInt("next_oid");
        }
    }
}
