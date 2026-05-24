package eDepot.dao;

import eDepot.models.ShippingNotice;
import eDepot.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class ShippingNoticeDAO {
    public boolean insertShippingNotice(ShippingNotice notice) {
        String sql = "INSERT INTO eDepot_Shipping_Notice (snid, shipping_company_name) VALUES (?, ?)";

        try (Connection conn = DatabaseConnection.testConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, notice.getShippingNoticeId());
            pstmt.setString(2, notice.getShippingCompanyName());

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Success: Shipping notice " + notice.getShippingNoticeId() + " inserted");
                return true;
            }
        }
        catch (SQLException e) {
            System.err.println("DB error while inserting shipping notice: " + e.getMessage());
        }
        return false;
    }

    // TODO: add methods for receiving shipment workflow and notice status processing.
}
