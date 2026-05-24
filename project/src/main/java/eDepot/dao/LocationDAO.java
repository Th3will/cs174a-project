package eDepot.dao;

import eDepot.models.Location;
import eDepot.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class LocationDAO {
    public boolean insertLocation(Location loc) {
        String sql = "INSERT INTO eDepot_Location (letter, num) VALUES (?, ?)";

        try (Connection conn = DatabaseConnection.testConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
        
            pstmt.setString(1, loc.getLetter());
            pstmt.setInt(2, loc.getNumber());

            // executeUpdate() returns # of rows affected; if 1 is returned, a row was successfully inserted
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Sucess: Location " + loc.getLetter() + loc.getNumber() + " inserted");
                return true;
            }
        }
        catch (SQLException e) {
            System.err.println("DB error while inserting new location: " + e.getMessage());
        }
        return false;
    }
}