package eDepot.dao;

import eDepot.models.Manufacturer;
import eDepot.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ManufacturerDAO {
    public boolean insertManufacturer(Manufacturer manufacturer) {
        String sql = "INSERT INTO eDepot_Manufacturer (mname) VALUES (?)";

        try (Connection conn = DatabaseConnection.testConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, manufacturer.getName());

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Success: Manufacturer " + manufacturer.getName() + " inserted");
                return true;
            }
        }
        catch (SQLException e) {
            System.err.println("DB error while inserting manufacturer: " + e.getMessage());
        }
        return false;
    }

    // TODO: add read/update/delete DAO methods as features are implemented.
}
