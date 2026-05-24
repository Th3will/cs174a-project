package eDepot.utils;

import io.github.cdimascio.dotenv.Dotenv;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    private static final Dotenv DOTENV = Dotenv.load();

    public static Connection testConnection() throws SQLException {
        String url = DOTENV.get("ORACLE_JDBC_EDEPOT_URL");
        String user = DOTENV.get("ORACLE_DB_USER");
        String password = DOTENV.get("ORACLE_DB_PASSWORD");

        if (url == null || user == null || password == null) {
            throw new IllegalStateException(
                    "Missing Oracle DB environment variables. Set ORACLE_JDBC_EDEPOT_URL, ORACLE_DB_USER, and ORACLE_DB_PASSWORD.");
        }

        return DriverManager.getConnection(url, user, password);
    }
}
