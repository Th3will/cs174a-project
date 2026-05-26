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

    /*
     * Notice lines joined with the corresponding warehouse item so the CLI
     * can preview an incoming shipment without making the operator retype
     * anything. Ordered by stock number for deterministic display.
     */
    public static class LineDetail {
        private final String stockNumber;
        private final String manufacturerName;
        private final String modelNumber;
        private final int noticeQuantity;

        public LineDetail(String stockNumber, String manufacturerName, String modelNumber, int noticeQuantity) {
            this.stockNumber = stockNumber;
            this.manufacturerName = manufacturerName;
            this.modelNumber = modelNumber;
            this.noticeQuantity = noticeQuantity;
        }

        public String getStockNumber() { return stockNumber; }
        public String getManufacturerName() { return manufacturerName; }
        public String getModelNumber() { return modelNumber; }
        public int getNoticeQuantity() { return noticeQuantity; }
    }

    public List<LineDetail> getLineDetailsForNotice(Connection conn, int snid) throws SQLException {
        String sql =
                "SELECT nl.stock_num, wi.mname, wi.model_num, nl.notice_quantity " +
                "FROM eDepot_Notice_Line nl " +
                "JOIN eDepot_Warehouse_Item wi ON nl.stock_num = wi.stock_num " +
                "WHERE nl.snid = ? " +
                "ORDER BY nl.stock_num";

        List<LineDetail> details = new ArrayList<>();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, snid);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    details.add(new LineDetail(
                            rs.getString("stock_num"),
                            rs.getString("mname"),
                            rs.getString("model_num"),
                            rs.getInt("notice_quantity")));
                }
            }
        }
        return details;
    }
}
