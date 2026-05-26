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
        // Notices are inserted in the unfulfilled state ('N'); processShipmentArrival flips this to 'Y'.
        String sql = "INSERT INTO eDepot_Shipping_Notice (snid, shipping_company_name, fulfilled) VALUES (?, ?, 'N')";

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

    public ShippingNotice findById(Connection conn, int snid) throws SQLException {
        String sql = "SELECT snid, shipping_company_name FROM eDepot_Shipping_Notice WHERE snid = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, snid);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new ShippingNotice(rs.getInt("snid"), rs.getString("shipping_company_name"));
            }
        }
    }

    public boolean isFulfilled(Connection conn, int snid) throws SQLException {
        String sql = "SELECT fulfilled FROM eDepot_Shipping_Notice WHERE snid = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, snid);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                return "Y".equals(rs.getString("fulfilled"));
            }
        }
    }

    /*
     * Flip the notice from unfulfilled to fulfilled. Returns true only if a row
     * transitioned from 'N' to 'Y', so callers can detect double-fulfillment
     * attempts atomically (works as a guard inside the receive-shipment txn).
     */
    public boolean markFulfilled(Connection conn, int snid) throws SQLException {
        String sql = "UPDATE eDepot_Shipping_Notice SET fulfilled = 'Y' WHERE snid = ? AND fulfilled = 'N'";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, snid);
            return pstmt.executeUpdate() > 0;
        }
    }
}
