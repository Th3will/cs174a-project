package eDepot.dao;

import eDepot.models.Location;
import eDepot.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class LocationDAO {
    public boolean insertLocation(Location loc) {
        try (Connection conn = DatabaseConnection.testConnection()) {
            return insertLocation(conn, loc);
        }
        catch (SQLException e) {
            System.err.println("DB error while inserting new location: " + e.getMessage());
            return false;
        }
    }

    public boolean insertLocation(Connection conn, Location loc) throws SQLException {
        String sql = "INSERT INTO eDepot_Location (letter, num) VALUES (?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, loc.getLetter());
            pstmt.setInt(2, loc.getNumber());
            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean exists(Connection conn, String letter, int number) throws SQLException {
        String sql = "SELECT 1 FROM eDepot_Location WHERE letter = ? AND num = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, letter);
            pstmt.setInt(2, number);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean isLocationOccupied(Connection conn, String letter, int number) throws SQLException {
        String sql = "SELECT 1 FROM eDepot_Warehouse_Item WHERE loc_letter = ? AND loc_num = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, letter);
            pstmt.setInt(2, number);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }
}