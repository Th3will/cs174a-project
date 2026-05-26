package eDepot.dao;

import eDepot.models.Manufacturer;
import eDepot.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ManufacturerDAO {
    public boolean insertManufacturer(Manufacturer manufacturer) {
        try (Connection conn = DatabaseConnection.testConnection()) {
            return insertManufacturer(conn, manufacturer);
        }
        catch (SQLException e) {
            System.err.println("DB error while inserting manufacturer: " + e.getMessage());
            return false;
        }
    }

    public boolean insertManufacturer(Connection conn, Manufacturer manufacturer) throws SQLException {
        String sql = "INSERT INTO eDepot_Manufacturer (mname) VALUES (?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, manufacturer.getName());
            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean exists(Connection conn, String manufacturerName) throws SQLException {
        String sql = "SELECT 1 FROM eDepot_Manufacturer WHERE mname = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, manufacturerName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }
}
