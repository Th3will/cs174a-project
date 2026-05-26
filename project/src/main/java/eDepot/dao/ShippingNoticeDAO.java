package eDepot.dao;

import eDepot.models.ShippingNotice;
import eDepot.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ShippingNoticeDAO {
    public boolean insertShippingNotice(ShippingNotice notice) {
        try (Connection conn = DatabaseConnection.testConnection()) {
            return insertShippingNotice(conn, notice);
        }
        catch (SQLException e) {
            System.err.println("DB error while inserting shipping notice: " + e.getMessage());
            return false;
        }
    }

    public boolean insertShippingNotice(Connection conn, ShippingNotice notice) throws SQLException {
        String sql = "INSERT INTO eDepot_Shipping_Notice (snid, shipping_company_name) VALUES (?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, notice.getShippingNoticeId());
            pstmt.setString(2, notice.getShippingCompanyName());
            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean exists(Connection conn, int snid) throws SQLException {
        String sql = "SELECT 1 FROM eDepot_Shipping_Notice WHERE snid = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, snid);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    // TODO: add methods for receiving shipment workflow and notice status processing.
}
