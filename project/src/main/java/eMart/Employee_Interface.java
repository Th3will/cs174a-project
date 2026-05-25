package eMart;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

public class Employee_Interface {

    public static void main(String[] args) throws SQLException {
        Properties info = new Properties();
        info.put("user", Constants.DB_USER);
        info.put("password", Constants.DB_PASSWORD);

        oracle.jdbc.pool.OracleDataSource odsMart = new oracle.jdbc.pool.OracleDataSource();
        odsMart.setURL(Constants.Mart_DB_URL);
        odsMart.setConnectionProperties(info);

        oracle.jdbc.pool.OracleDataSource odsDepot = new oracle.jdbc.pool.OracleDataSource();
        odsDepot.setURL(Constants.Depot_DB_URL);
        odsDepot.setConnectionProperties(info);

        try (Connection martConn = odsMart.getConnection();
             Connection depotConn = odsDepot.getConnection()) {

            Screen screen = new ManagerPortalEntryScreen(martConn, depotConn, null);
            while (screen != null) {
                screen = screen.run();
            }
        }
        System.exit(0);
    }

    public static Screen getStartScreen(Manager manager, Connection martConn, Connection depotConn, Screen parent) {
        if (manager == null) {
            return new ManagerPortalEntryScreen(martConn, depotConn, parent);
        }
        return new ManagerMenuScreen(manager, martConn, depotConn, parent);
    }

    private static class ManagerPortalEntryScreen implements Screen {
        private final Connection martConn;
        private final Connection depotConn;
        private final Screen parent;

        public ManagerPortalEntryScreen(Connection martConn, Connection depotConn, Screen parent) {
            this.martConn = martConn;
            this.depotConn = depotConn;
            this.parent = parent;
        }

        @Override
        public Screen run() {
            Utility.clearConsole();
            System.out.println("=========================================");
            System.out.println("            Manager Portal               ");
            System.out.println("=========================================");
            System.out.println("1. Login");
            System.out.println("2. Create New Account");
            System.out.println("3. Exit/Cancel");
            System.out.println("=========================================");
            
            String choice = UIHelpers.promptString("Enter choice: ");
            if (choice.equals("1")) {
                return new UserInterface.BaseLoginScreen(martConn, "manager", this, user -> {
                    Manager manager = new Manager(user);
                    return new ManagerMenuScreen(manager, martConn, depotConn, this);
                });
            } else if (choice.equals("2")) {
                return new UserInterface.BaseRegisterScreen(martConn, "manager", this);
            } else if (choice.equals("3")) {
                return parent;
            } else {
                System.out.println("Invalid choice.");
                UIHelpers.sleep(1000);
                return this;
            }
        }
    }

    private static class ManagerMenuScreen implements Screen {
        private final Manager manager;
        private final Connection martConn;
        private final Connection depotConn;
        private final Screen parent;

        public ManagerMenuScreen(Manager manager, Connection martConn, Connection depotConn, Screen parent) {
            this.manager = manager;
            this.martConn = martConn;
            this.depotConn = depotConn;
            this.parent = parent;
        }

        @Override
        public Screen run() {
            Utility.clearConsole();
            System.out.println("=========================================");
            System.out.println("          Manager Menu - eMart           ");
            System.out.println("=========================================");
            System.out.println("Logged in as: " + manager.get_first_name() + " " + manager.get_last_name() + " (" + manager.get_eid() + ")");
            System.out.println("=========================================");
            System.out.println("1. Adjust Status Rules (Threshold/Fee/Discount)");
            System.out.println("2. Adjust Customer Status (Manual Override)");
            System.out.println("3. Monthly Sales Summary Reports");
            System.out.println("4. Adjust Product Price");
            System.out.println("5. Cleanup Old Sales Transactions");
            System.out.println("6. Logout");
            System.out.println("=========================================");
            
            String choice = UIHelpers.promptString("Enter choice: ");
            if (choice.equals("1")) {
                return new AdjustStatusValuesScreen(martConn, this);
            } else if (choice.equals("2")) {
                return new AdjustCustomerStatusScreen(martConn, this);
            } else if (choice.equals("3")) {
                return new MonthlySummaryScreen(martConn, this);
            } else if (choice.equals("4")) {
                return new AdjustPriceScreen(martConn, this);
            } else if (choice.equals("5")) {
                return new CleanupTransactionsScreen(martConn, this);
            } else if (choice.equals("6")) {
                System.out.println("Logging out...");
                UIHelpers.sleep(800);
                return parent;
            } else {
                System.out.println("Invalid choice. Please try again.");
                UIHelpers.sleep(1000);
                return this;
            }
        }
    }

    private static class AdjustStatusValuesScreen implements Screen {
        private final Connection conn;
        private final Screen parent;

        public AdjustStatusValuesScreen(Connection conn, Screen parent) {
            this.conn = conn;
            this.parent = parent;
        }

        private static class StatusRecord {
            String levelName;
            double threshold;
            double shippingFee;
            double discount;

            public StatusRecord(String levelName, double threshold, double shippingFee, double discount) {
                this.levelName = levelName;
                this.threshold = threshold;
                this.shippingFee = shippingFee;
                this.discount = discount;
            }
        }

        @Override
        public Screen run() {
            PageProvider<StatusRecord> provider = (pageNumber, pageSize) -> {
                List<StatusRecord> list = new ArrayList<>();
                String sql = "SELECT level_name, threshold, shipping_fee, discount FROM status ORDER BY level_name";
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        list.add(new StatusRecord(
                            rs.getString("level_name"),
                            rs.getDouble("threshold"),
                            rs.getDouble("shipping_fee"),
                            rs.getDouble("discount")
                        ));
                    }
                }
                return list;
            };

            Consumer<StatusRecord> displayer = s -> 
                System.out.println(s.levelName + " Status | Shipping Waiver Threshold: $" + s.threshold + " | Shipping Fee: " + s.shippingFee + "% | Discount: " + s.discount + "%");

            EntityListing.EntitySelectionHandler<StatusRecord> selection = (record, currentListing) -> {
                Utility.clearConsole();
                System.out.println("=== Adjust Rules for: " + record.levelName + " ===");
                System.out.println("(Press Enter on all fields to cancel)");
                String thresholdStr = UIHelpers.promptString("Enter new shipping waiver threshold ($): ");
                String shippingFeeStr = UIHelpers.promptString("Enter new shipping fee rate (%): ");
                String discountStr = UIHelpers.promptString("Enter new discount rate (%): ");
                
                if (thresholdStr.isEmpty() && shippingFeeStr.isEmpty() && discountStr.isEmpty()) {
                    return currentListing;
                }
                
                double threshold = record.threshold;
                double shippingFee = record.shippingFee;
                double discount = record.discount;
                try {
                    if (!thresholdStr.isEmpty()) threshold = Double.parseDouble(thresholdStr);
                    if (!shippingFeeStr.isEmpty()) shippingFee = Double.parseDouble(shippingFeeStr);
                    if (!discountStr.isEmpty()) discount = Double.parseDouble(discountStr);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid numeric values entered. Update cancelled.");
                    UIHelpers.waitForEnter();
                    return currentListing;
                }
                
                String sql = "UPDATE status SET threshold = ?, shipping_fee = ?, discount = ? WHERE level_name = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setDouble(1, threshold);
                    pstmt.setDouble(2, shippingFee);
                    pstmt.setDouble(3, discount);
                    pstmt.setString(4, record.levelName);
                    pstmt.executeUpdate();
                    System.out.println("Status level '" + record.levelName + "' updated successfully.");
                } catch (SQLException e) {
                    System.out.println("Error updating status rules: " + e.getMessage());
                }
                UIHelpers.waitForEnter();
                return currentListing;
            };

            return new EntityListing<>("Status Value Rules", provider, displayer, selection, null, parent).run();
        }
    }

    private static class AdjustCustomerStatusScreen implements Screen {
        private final Connection conn;
        private final Screen parent;

        public AdjustCustomerStatusScreen(Connection conn, Screen parent) {
            this.conn = conn;
            this.parent = parent;
        }

        private static class CustomerSummary {
            String cid;
            String firstName;
            String lastName;
            String levelName;

            public CustomerSummary(String cid, String firstName, String lastName, String levelName) {
                this.cid = cid;
                this.firstName = firstName;
                this.lastName = lastName;
                this.levelName = levelName;
            }
        }

        @Override
        public Screen run() {
            PageProvider<CustomerSummary> provider = (pageNumber, pageSize) -> {
                List<CustomerSummary> list = new ArrayList<>();
                int offset = (pageNumber - 1) * pageSize;
                String sql = "SELECT cid, first_name, last_name, level_name FROM customer ORDER BY cid OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setInt(1, offset);
                    pstmt.setInt(2, pageSize);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            list.add(new CustomerSummary(
                                rs.getString("cid"),
                                rs.getString("first_name"),
                                rs.getString("last_name"),
                                rs.getString("level_name")
                            ));
                        }
                    }
                }
                return list;
            };

            Consumer<CustomerSummary> displayer = c -> 
                System.out.println("ID: " + c.cid + " | Name: " + c.firstName + " " + c.lastName + " | Status Level: " + c.levelName);

            EntityListing.EntitySelectionHandler<CustomerSummary> selection = (customer, currentListing) -> {
                Utility.clearConsole();
                System.out.println("=== Customer Manual Override ===");
                System.out.println("Customer: " + customer.firstName + " " + customer.lastName + " (ID: " + customer.cid + ")");
                System.out.println("Current Level: " + customer.levelName);
                String newLevel = UIHelpers.promptString("Enter new status level (Gold, Silver, Green, New) (leave empty to cancel): ");
                if (newLevel.isEmpty()) {
                    return currentListing;
                }
                
                boolean exists = false;
                try (PreparedStatement pstmt = conn.prepareStatement("SELECT 1 FROM status WHERE level_name = ?")) {
                    pstmt.setString(1, newLevel);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) exists = true;
                    }
                } catch (SQLException e) {
                    // ignore
                }

                if (!exists) {
                    System.out.println("Status level '" + newLevel + "' does not exist in status rules. Please edit status rules first or enter a valid level.");
                    UIHelpers.waitForEnter();
                    return currentListing;
                }

                String sql = "UPDATE customer SET level_name = ? WHERE cid = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, newLevel);
                    pstmt.setString(2, customer.cid);
                    pstmt.executeUpdate();
                    System.out.println("Customer status updated successfully.");
                } catch (SQLException e) {
                    System.out.println("Error updating customer: " + e.getMessage());
                }
                UIHelpers.waitForEnter();
                return currentListing;
            };

            return new EntityListing<>("Customer Status Management", provider, displayer, selection, null, parent).run();
        }
    }

    private static class MonthlySummaryScreen implements Screen {
        private final Connection conn;
        private final Screen parent;

        public MonthlySummaryScreen(Connection conn, Screen parent) {
            this.conn = conn;
            this.parent = parent;
        }

        @Override
        public Screen run() {
            Utility.clearConsole();
            System.out.println("=== Monthly Sales Summary ===");
            System.out.println("(Press Enter on both fields to cancel)");
            String monthStr = UIHelpers.promptString("Enter Month (MM, 1-12): ");
            String yearStr = UIHelpers.promptString("Enter Year (YYYY): ");
            
            if (monthStr.isEmpty() && yearStr.isEmpty()) {
                return parent;
            }
            
            int month = 0;
            int year = 0;
            try {
                month = Integer.parseInt(monthStr);
                year = Integer.parseInt(yearStr);
            } catch (NumberFormatException e) {
                System.out.println("Invalid numeric values.");
                UIHelpers.sleep(1000);
                return this;
            }
            
            if (month < 1 || month > 12) {
                System.out.println("Invalid month.");
                UIHelpers.sleep(1000);
                return this;
            }
            if (year < 1900 || year > 2100) {
                System.out.println("Invalid year.");
                UIHelpers.sleep(1000);
                return this;
            }

            System.out.println("\nSelect Summary Report Type:");
            System.out.println("1. Sales Per Product");
            System.out.println("2. Sales Per Category");
            System.out.println("3. Top Customers Spent");
            System.out.println("4. Cancel");
            String choice = UIHelpers.promptString("Enter selection: ");
            
            if (choice.equals("1")) {
                return new ProductSummaryScreen(conn, month, year, this);
            } else if (choice.equals("2")) {
                return new CategorySummaryScreen(conn, month, year, this);
            } else if (choice.equals("3")) {
                return new TopCustomerSummaryScreen(conn, month, year, this);
            } else {
                return parent;
            }
        }
    }

    private static class ProductSummaryScreen implements Screen {
        private final Connection conn;
        private final int month;
        private final int year;
        private final Screen parent;

        public ProductSummaryScreen(Connection conn, int month, int year, Screen parent) {
            this.conn = conn;
            this.month = month;
            this.year = year;
            this.parent = parent;
        }

        private static class ProductSummaryRow {
            String stockNum;
            String manufacturer;
            String model;
            int totalQty;
            double totalRev;

            public ProductSummaryRow(String stockNum, String manufacturer, String model, int totalQty, double totalRev) {
                this.stockNum = stockNum;
                this.manufacturer = manufacturer;
                this.model = model;
                this.totalQty = totalQty;
                this.totalRev = totalRev;
            }
        }

        @Override
        public Screen run() {
            PageProvider<ProductSummaryRow> provider = (pageNumber, pageSize) -> {
                List<ProductSummaryRow> list = new ArrayList<>();
                int offset = (pageNumber - 1) * pageSize;
                String sql = "SELECT ol.stock_num, i.mname, i.model_num, SUM(ol.order_quantity) AS total_qty, SUM(ol.order_quantity * ol.order_price) AS total_revenue " +
                             "FROM order_line ol JOIN order_table o ON ol.ord_num = o.ord_num JOIN item i ON ol.stock_num = i.stock_num " +
                             "WHERE EXTRACT(MONTH FROM o.order_date) = ? AND EXTRACT(YEAR FROM o.order_date) = ? " +
                             "GROUP BY ol.stock_num, i.mname, i.model_num " +
                             "ORDER BY total_revenue DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setInt(1, month);
                    pstmt.setInt(2, year);
                    pstmt.setInt(3, offset);
                    pstmt.setInt(4, pageSize);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            list.add(new ProductSummaryRow(
                                rs.getString("stock_num"),
                                rs.getString("mname"),
                                rs.getString("model_num"),
                                rs.getInt("total_qty"),
                                rs.getDouble("total_revenue")
                            ));
                        }
                    }
                }
                return list;
            };

            Consumer<ProductSummaryRow> displayer = r -> 
                System.out.println(r.stockNum + " | " + r.manufacturer + " " + r.model + " | Total Quantity Sold: " + r.totalQty + " | Revenue: $" + String.format("%.2f", r.totalRev));

            return new EntityListing<>("Product Sales Summary (" + month + "/" + year + ")", provider, displayer, null, null, parent).run();
        }
    }

    private static class CategorySummaryScreen implements Screen {
        private final Connection conn;
        private final int month;
        private final int year;
        private final Screen parent;

        public CategorySummaryScreen(Connection conn, int month, int year, Screen parent) {
            this.conn = conn;
            this.month = month;
            this.year = year;
            this.parent = parent;
        }

        private static class CategorySummaryRow {
            String category;
            int totalQty;
            double totalRev;

            public CategorySummaryRow(String category, int totalQty, double totalRev) {
                this.category = category;
                this.totalQty = totalQty;
                this.totalRev = totalRev;
            }
        }

        @Override
        public Screen run() {
            PageProvider<CategorySummaryRow> provider = (pageNumber, pageSize) -> {
                List<CategorySummaryRow> list = new ArrayList<>();
                int offset = (pageNumber - 1) * pageSize;
                String sql = "SELECT i.category, SUM(ol.order_quantity) AS total_qty, SUM(ol.order_quantity * ol.order_price) AS total_revenue " +
                             "FROM order_line ol JOIN order_table o ON ol.ord_num = o.ord_num JOIN item i ON ol.stock_num = i.stock_num " +
                             "WHERE EXTRACT(MONTH FROM o.order_date) = ? AND EXTRACT(YEAR FROM o.order_date) = ? " +
                             "GROUP BY i.category " +
                             "ORDER BY total_revenue DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setInt(1, month);
                    pstmt.setInt(2, year);
                    pstmt.setInt(3, offset);
                    pstmt.setInt(4, pageSize);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            list.add(new CategorySummaryRow(
                                rs.getString("category"),
                                rs.getInt("total_qty"),
                                rs.getDouble("total_revenue")
                            ));
                        }
                    }
                }
                return list;
            };

            Consumer<CategorySummaryRow> displayer = r -> 
                System.out.println(r.category + " | Total Quantity Sold: " + r.totalQty + " | Total Revenue: $" + String.format("%.2f", r.totalRev));

            return new EntityListing<>("Category Sales Summary (" + month + "/" + year + ")", provider, displayer, null, null, parent).run();
        }
    }

    private static class TopCustomerSummaryScreen implements Screen {
        private final Connection conn;
        private final int month;
        private final int year;
        private final Screen parent;

        public TopCustomerSummaryScreen(Connection conn, int month, int year, Screen parent) {
            this.conn = conn;
            this.month = month;
            this.year = year;
            this.parent = parent;
        }

        private static class CustomerSummaryRow {
            String cid;
            String firstName;
            String lastName;
            double totalSpent;

            public CustomerSummaryRow(String cid, String firstName, String lastName, double totalSpent) {
                this.cid = cid;
                this.firstName = firstName;
                this.lastName = lastName;
                this.totalSpent = totalSpent;
            }
        }

        @Override
        public Screen run() {
            PageProvider<CustomerSummaryRow> provider = (pageNumber, pageSize) -> {
                List<CustomerSummaryRow> list = new ArrayList<>();
                int offset = (pageNumber - 1) * pageSize;
                String sql = "SELECT o.cid, c.first_name, c.last_name, SUM(o.total) AS total_spent " +
                             "FROM order_table o JOIN customer c ON o.cid = c.cid " +
                             "WHERE EXTRACT(MONTH FROM o.order_date) = ? AND EXTRACT(YEAR FROM o.order_date) = ? " +
                             "GROUP BY o.cid, c.first_name, c.last_name " +
                             "ORDER BY total_spent DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setInt(1, month);
                    pstmt.setInt(2, year);
                    pstmt.setInt(3, offset);
                    pstmt.setInt(4, pageSize);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            list.add(new CustomerSummaryRow(
                                rs.getString("cid"),
                                rs.getString("first_name"),
                                rs.getString("last_name"),
                                rs.getDouble("total_spent")
                            ));
                        }
                    }
                }
                return list;
            };

            Consumer<CustomerSummaryRow> displayer = r -> 
                System.out.println("ID: " + r.cid + " | Name: " + r.firstName + " " + r.lastName + " | Total Purchased: $" + String.format("%.2f", r.totalSpent));

            return new EntityListing<>("Top Customers (" + month + "/" + year + ")", provider, displayer, null, null, parent).run();
        }
    }

    private static class AdjustPriceScreen implements Screen {
        private final Connection conn;
        private final Screen parent;

        public AdjustPriceScreen(Connection conn, Screen parent) {
            this.conn = conn;
            this.parent = parent;
        }

        @Override
        public Screen run() {
            Utility.clearConsole();
            System.out.println("=== Adjust Product Price ===");
            String stock = UIHelpers.promptString("Enter Stock Number (e.g. AA00101) (leave empty to cancel): ").toUpperCase();
            if (stock.isEmpty()) {
                return parent;
            }
            
            double currentPrice = 0.0;
            String mname = "";
            String model = "";
            boolean exists = false;
            
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT price, mname, model_num FROM item WHERE stock_num = ?")) {
                pstmt.setString(1, stock);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        exists = true;
                        currentPrice = rs.getDouble("price");
                        mname = rs.getString("mname");
                        model = rs.getString("model_num");
                    }
                }
            } catch (SQLException e) {
                System.out.println("Database error: " + e.getMessage());
                UIHelpers.waitForEnter();
                return parent;
            }
            
            if (!exists) {
                System.out.println("Item not found in catalog.");
                UIHelpers.waitForEnter();
                return this;
            }
            
            System.out.println("Item Found: " + mname + " " + model + " (" + stock + ")");
            System.out.println("Current Price: $" + currentPrice);
            String priceStr = UIHelpers.promptString("Enter new price (leave empty to cancel): ");
            if (priceStr.isEmpty()) {
                return parent;
            }
            double newPrice = 0.0;
            try {
                newPrice = Double.parseDouble(priceStr);
            } catch (NumberFormatException e) {
                System.out.println("Invalid numeric value.");
                UIHelpers.waitForEnter();
                return this;
            }
            if (newPrice < 0) {
                System.out.println("Price cannot be negative.");
                UIHelpers.waitForEnter();
                return this;
            }
            
            try (PreparedStatement pstmt = conn.prepareStatement("UPDATE item SET price = ? WHERE stock_num = ?")) {
                pstmt.setDouble(1, newPrice);
                pstmt.setString(2, stock);
                pstmt.executeUpdate();
                System.out.println("Price updated successfully to $" + newPrice);
            } catch (SQLException e) {
                System.out.println("Database error while updating price: " + e.getMessage());
            }
            UIHelpers.waitForEnter();
            return parent;
        }
    }

    private static class CleanupTransactionsScreen implements Screen {
        private final Connection conn;
        private final Screen parent;

        public CleanupTransactionsScreen(Connection conn, Screen parent) {
            this.conn = conn;
            this.parent = parent;
        }

        @Override
        public Screen run() {
            Utility.clearConsole();
            System.out.println("=== Cleanup Old Transactions ===");
            System.out.println("This action will delete all orders that are no longer needed");
            System.out.println("to calculate customer status levels. Only the 3 most recent");
            System.out.println("orders for each customer will be retained.");
            System.out.println("=================================");
            
            String confirm = UIHelpers.promptString("Do you want to proceed? (y/n): ");
            if (confirm.equalsIgnoreCase("y")) {
                System.out.println("\nAnalyzing and cleaning up transactions...");
                
                try {
                    conn.setAutoCommit(false);
                    
                    List<Integer> ordersToDelete = new ArrayList<>();
                    String selectSql = "SELECT ord_num FROM order_table o WHERE o.ord_num NOT IN ( " +
                                       "  SELECT ord_num FROM ( " +
                                       "    SELECT ord_num, ROW_NUMBER() OVER (PARTITION BY cid ORDER BY order_date DESC, ord_num DESC) as rn " +
                                       "    FROM order_table " +
                                       "  ) WHERE rn <= 3 " +
                                       ")";
                    try (Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery(selectSql)) {
                        while (rs.next()) {
                            ordersToDelete.add(rs.getInt("ord_num"));
                        }
                    }
                    
                    if (ordersToDelete.isEmpty()) {
                        System.out.println("No transactions eligible for deletion. All records are currently active.");
                        conn.commit();
                        UIHelpers.waitForEnter();
                        return parent;
                    }
                    
                    int linesDeleted = 0;
                    int ordersDeleted = 0;
                    
                    try (PreparedStatement delLine = conn.prepareStatement("DELETE FROM order_line WHERE ord_num = ?");
                         PreparedStatement delOrd = conn.prepareStatement("DELETE FROM order_table WHERE ord_num = ?")) {
                        
                        for (int ordNum : ordersToDelete) {
                            delLine.setInt(1, ordNum);
                            linesDeleted += delLine.executeUpdate();
                            
                            delOrd.setInt(1, ordNum);
                            ordersDeleted += delOrd.executeUpdate();
                        }
                    }
                    
                    conn.commit();
                    System.out.println("Cleanup completed successfully.");
                    System.out.println("Deleted " + linesDeleted + " order line item(s) from " + ordersDeleted + " order(s).");
                    
                } catch (SQLException e) {
                    System.out.println("Error cleaning up transactions: " + e.getMessage());
                    try {
                        conn.rollback();
                    } catch (SQLException ex) {
                        // ignore
                    }
                } finally {
                    try {
                        conn.setAutoCommit(true);
                    } catch (SQLException e) {
                        // ignore
                    }
                }
                UIHelpers.waitForEnter();
            }
            return parent;
        }
    }
}
