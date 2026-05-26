package eDepot.dao;

import eDepot.models.NoticeLine;
import eDepot.utils.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;

import java.util.List;
import java.util.ArrayList;

public class NoticeLineDAO {
    public boolean insertNoticeLine(NoticeLine line) {
        try (Connection conn = DatabaseConnection.testConnection()) {
            return insertNoticeLine(conn, line);
        }
        catch (SQLException e) {
            System.err.println("DB error while inserting notice line: " + e.getMessage());
            return false;
        }
    }

    public boolean insertNoticeLine(Connection conn, NoticeLine line) throws SQLException {
        String sql = "INSERT INTO eDepot_Notice_Line (snid, stock_num, notice_quantity) VALUES (?, ?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, line.getShippingNoticeId());
            pstmt.setString(2, line.getStockNumber());
            pstmt.setInt(3, line.getNoticeQuantity());
            return pstmt.executeUpdate() > 0;
        }
    }

    // return all rows in a shipping notice that correspond to a specific shipping notice ID
    public List<NoticeLine> getLinesForNotice(int snid) {
        List<NoticeLine> noticeLines = new ArrayList<>();
        String sql = "SELECT snid, stock_num, notice_quantity FROM eDepot_Notice_Line WHERE snid = ?";

        try (Connection conn = DatabaseConnection.testConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, snid);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int row_snid = rs.getInt("snid");
                    String row_stock_num = rs.getString("stock_num");
                    int row_notice_quantity = rs.getInt("notice_quantity");

                    NoticeLine line = new NoticeLine(row_snid, row_stock_num, row_notice_quantity);
                    noticeLines.add(line);
                }
            }
            return noticeLines;
        }
        catch (SQLException e) {
            System.err.println("DB error while fetching shipping notice line: " + e.getMessage());
        }
        return noticeLines;
    }
}
