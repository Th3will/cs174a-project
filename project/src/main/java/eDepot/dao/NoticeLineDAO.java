package eDepot.dao;

import eDepot.models.NoticeLine;
import eDepot.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class NoticeLineDAO {
    public boolean insertNoticeLine(NoticeLine line) {
        String sql = "INSERT INTO eDepot_Notice_Line (snid, stock_num, notice_quantity) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConnection.testConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, line.getShippingNoticeId());
            pstmt.setString(2, line.getStockNumber());
            pstmt.setInt(3, line.getNoticeQuantity());

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Success: Notice line for shipping notice " + line.getShippingNoticeId() + " inserted");
                return true;
            }
        }
        catch (SQLException e) {
            System.err.println("DB error while inserting notice line: " + e.getMessage());
        }
        return false;
    }

    // TODO: add methods to list all lines by shipping notice ID.
}
