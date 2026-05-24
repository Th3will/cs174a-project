package eDepot;

import java.sql.Connection;

import eDepot.dao.LocationDAO;
import eDepot.models.Location;
import eDepot.utils.DatabaseConnection;

public class Main {
    public static void main(String[] args) {
        System.out.println("Compilation with Maven works");

        try (Connection connection = DatabaseConnection.testConnection()) {
            System.out.println("Oracle DB connection successful!");
        } 
        catch (Exception exception) {
            System.out.println("Oracle DB connection failed: " + exception.getMessage());
        }

        LocationDAO locDAO = new LocationDAO();
        Location loc1 = new Location("A", 1);
        Location loc2 = new Location("A", 2);

        locDAO.insertLocation(loc1);
        locDAO.insertLocation(loc2);
    }
}
