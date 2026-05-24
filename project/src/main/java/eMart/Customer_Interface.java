package eMart;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

public class Customer_Interface {

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

            Screen screen = new CustomerPortalEntryScreen(martConn, depotConn, null);
            while (screen != null) {
                screen = screen.run();
            }
        }
    }

    public static Screen getStartScreen(Customer customer, Connection martConn, Connection depotConn, Screen parent) {
        if (customer == null) {
            return new CustomerPortalEntryScreen(martConn, depotConn, parent);
        }
        return new CustomerMenuScreen(customer, martConn, depotConn, parent);
    }

    private static class CustomerPortalEntryScreen implements Screen {
        private final Connection martConn;
        private final Connection depotConn;
        private final Screen parent;

        public CustomerPortalEntryScreen(Connection martConn, Connection depotConn, Screen parent) {
            this.martConn = martConn;
            this.depotConn = depotConn;
            this.parent = parent;
        }

        @Override
        public Screen run() {
            Utility.clearConsole();
            System.out.println("=========================================");
            System.out.println("            Customer Portal              ");
            System.out.println("=========================================");
            System.out.println("1. Login");
            System.out.println("2. Create New Account");
            System.out.println("3. Exit/Cancel");
            System.out.println("=========================================");
            
            String choice = UIHelpers.promptString("Enter choice: ");
            if (choice.equals("1")) {
                return new UserInterface.BaseLoginScreen(martConn, "customer", this, user -> {
                    String levelName = "New";
                    try (PreparedStatement pstmt = martConn.prepareStatement("SELECT level_name FROM customer WHERE cid = ?")) {
                        pstmt.setString(1, user.getId());
                        try (ResultSet rs = pstmt.executeQuery()) {
                            if (rs.next()) {
                                levelName = rs.getString("level_name");
                            }
                        }
                    } catch (SQLException e) {
                        // fallback to default
                    }
                    Customer customer = new Customer(user, levelName);
                    return new CustomerMenuScreen(customer, martConn, depotConn, this);
                });
            } else if (choice.equals("2")) {
                return new UserInterface.BaseRegisterScreen(martConn, "customer", this);
            } else if (choice.equals("3")) {
                return parent;
            } else {
                System.out.println("Invalid choice.");
                UIHelpers.sleep(1000);
                return this;
            }
        }
    }

    private static class CustomerMenuScreen implements Screen {
        private final Customer customer;
        private final Connection martConn;
        private final Connection depotConn;
        private final Screen parent;

        public CustomerMenuScreen(Customer customer, Connection martConn, Connection depotConn, Screen parent) {
            this.customer = customer;
            this.martConn = martConn;
            this.depotConn = depotConn;
            this.parent = parent;
        }

        private String getFreshLevelName() {
            String q = "SELECT level_name FROM customer WHERE cid = ?";
            try (PreparedStatement pstmt = martConn.prepareStatement(q)) {
                pstmt.setString(1, customer.get_cid());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("level_name");
                    }
                }
            } catch (SQLException e) {
                // fallback
            }
            return customer.get_level_name();
        }

        @Override
        public Screen run() {
            Utility.clearConsole();
            String level = getFreshLevelName();
            System.out.println("=========================================");
            System.out.println("          Customer Menu - eMart          ");
            System.out.println("=========================================");
            System.out.println("Logged in as: " + customer.get_first_name() + " " + customer.get_last_name() + " (" + customer.get_cid() + ")");
            System.out.println("Status level: " + level);
            System.out.println("Items in cart: " + customer.cart.getItems().size());
            System.out.println("=========================================");
            System.out.println("1. Browse Catalog");
            System.out.println("2. Filter Catalog");
            System.out.println("3. View Shopping Cart / Checkout");
            System.out.println("4. Order History");
            System.out.println("5. Logout");
            System.out.println("=========================================");
            
            String choice = UIHelpers.promptString("Enter choice: ");
            if (choice.equals("1")) {
                return new ItemListScreen(customer, martConn, depotConn, this, null, null);
            } else if (choice.equals("2")) {
                return new FilterSelectionScreen(customer, martConn, depotConn, this);
            } else if (choice.equals("3")) {
                return new CartScreen(customer, martConn, depotConn, this);
            } else if (choice.equals("4")) {
                return new OrderHistoryListScreen(customer, martConn, depotConn, this);
            } else if (choice.equals("5")) {
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

    private static class ItemListScreen implements Screen {
        private final Customer customer;
        private final Connection martConn;
        private final Connection depotConn;
        private final Screen parent;
        private final String filterWhereClause;
        private final List<Object> filterParams;

        public ItemListScreen(Customer customer, Connection martConn, Connection depotConn, Screen parent,
                              String filterWhereClause, List<Object> filterParams) {
            this.customer = customer;
            this.martConn = martConn;
            this.depotConn = depotConn;
            this.parent = parent;
            this.filterWhereClause = filterWhereClause;
            this.filterParams = filterParams;
        }

        @Override
        public Screen run() {
            PageProvider<Items> provider = (pageNumber, pageSize) -> 
                fetchItems(martConn, pageNumber, pageSize, filterWhereClause, filterParams);

            Consumer<Items> displayer = item -> {
                System.out.println(item.stock_num + " | " + item.category + " | " + item.man_name + " " + item.model_num + " | $" + item.price + " | Warranty: " + item.warranty + " months");
                if (!item.attributes.isEmpty()) {
                    System.out.print("   Attributes: ");
                    for (Items.Attribute a : item.attributes) {
                        System.out.print(a.key + "=" + a.value + "; ");
                    }
                    System.out.println();
                }
                if (!item.compatibilities.isEmpty()) {
                    System.out.println("   Compatible with: " + String.join(", ", item.compatibilities));
                }
            };

            EntityListing.EntitySelectionHandler<Items> selHandler = (item, currentListing) -> {
                Utility.clearConsole();
                System.out.println("=== Selected Item Details ===");
                System.out.println(item.toString());
                System.out.println("=============================");
                
                String choice = UIHelpers.promptString("Add this item to cart? (y/n): ");
                if (choice.equalsIgnoreCase("y")) {
                    int qty = UIHelpers.promptInt("Enter quantity: ");
                    if (qty <= 0) {
                        System.out.println("Quantity must be greater than 0.");
                        UIHelpers.waitForEnter();
                        return currentListing;
                    }
                    
                    int stockAvail = 0;
                    String stockSql = "SELECT quantity FROM eDepot_Warehouse_Item WHERE stock_num = ?";
                    try (PreparedStatement pstmt = depotConn.prepareStatement(stockSql)) {
                        pstmt.setString(1, item.stock_num);
                        try (ResultSet rs = pstmt.executeQuery()) {
                            if (rs.next()) {
                                stockAvail = rs.getInt("quantity");
                            }
                        }
                    } catch (SQLException e) {
                        System.out.println("Error checking warehouse stock: " + e.getMessage());
                        UIHelpers.waitForEnter();
                        return currentListing;
                    }
                    
                    if (stockAvail >= qty) {
                        customer.cart.add(item.stock_num, qty);
                        System.out.println("Added " + qty + " of " + item.stock_num + " to your cart.");
                    } else {
                        System.out.println("Insufficient quantity in eDepot warehouse.");
                        System.out.println("Available quantity: " + stockAvail);
                    }
                    UIHelpers.waitForEnter();
                }
                return currentListing;
            };

            List<TuiAction> actions = new ArrayList<>();
            EntityListing<Items> listing = new EntityListing<>(
                filterWhereClause == null ? "Product Catalog" : "Filtered Catalog Search",
                provider, displayer, selHandler, actions, parent
            );
            return listing.run();
        }
    }

    private static class FilterSelectionScreen implements Screen {
        private final Customer customer;
        private final Connection martConn;
        private final Connection depotConn;
        private final Screen parent;

        public FilterSelectionScreen(Customer customer, Connection martConn, Connection depotConn, Screen parent) {
            this.customer = customer;
            this.martConn = martConn;
            this.depotConn = depotConn;
            this.parent = parent;
        }

        @Override
        public Screen run() {
            Utility.clearConsole();
            System.out.println("=== Search Filters ===");
            System.out.println("1. Filter by Category");
            System.out.println("2. Filter by Manufacturer");
            System.out.println("3. Filter by Max Price");
            System.out.println("4. Filter by Attribute (Name and Value)");
            System.out.println("5. Filter by Compatibility (Items that can replace target stock#)");
            System.out.println("6. Cancel");
            System.out.println("======================");
            
            String choice = UIHelpers.promptString("Enter selection: ");
            if (choice.equals("6")) {
                return parent;
            }
            
            String where = null;
            List<Object> params = new ArrayList<>();
            
            if (choice.equals("1")) {
                String val = UIHelpers.promptString("Enter Category (leave empty to cancel): ");
                if (val.isEmpty()) return this;
                where = "category = ?";
                params.add(val);
            } else if (choice.equals("2")) {
                String val = UIHelpers.promptString("Enter Manufacturer name (leave empty to cancel): ");
                if (val.isEmpty()) return this;
                where = "mname = ?";
                params.add(val);
            } else if (choice.equals("3")) {
                String priceStr = UIHelpers.promptString("Enter Maximum Price (leave empty to cancel): ");
                if (priceStr.isEmpty()) return this;
                double maxVal = 0.0;
                try {
                    maxVal = Double.parseDouble(priceStr);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid price.");
                    UIHelpers.sleep(1000);
                    return this;
                }
                where = "price <= ?";
                params.add(maxVal);
            } else if (choice.equals("4")) {
                String name = UIHelpers.promptString("Enter Attribute Name (leave empty to cancel): ");
                if (name.isEmpty()) return this;
                String valStr = UIHelpers.promptString("Enter Attribute Value (leave empty to cancel): ");
                if (valStr.isEmpty()) return this;
                double val = 0.0;
                try {
                    val = Double.parseDouble(valStr);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid numeric value.");
                    UIHelpers.sleep(1000);
                    return this;
                }
                where = "stock_num IN (SELECT stock_num FROM item_attribute WHERE attr_name = ? AND attr_value = ?)";
                params.add(name);
                params.add(val);
            } else if (choice.equals("5")) {
                String stock = UIHelpers.promptString("Enter Target Stock Number (leave empty to cancel): ");
                if (stock.isEmpty()) return this;
                where = "stock_num IN (SELECT replacement_stock_num FROM compatible_with WHERE orig_stock_num = ?)";
                params.add(stock);
            } else {
                System.out.println("Invalid selection.");
                UIHelpers.sleep(1000);
                return this;
            }
            
            return new ItemListScreen(customer, martConn, depotConn, parent, where, params);
        }
    }

    private static class CartScreen implements Screen {
        private final Customer customer;
        private final Connection martConn;
        private final Connection depotConn;
        private final Screen parent;

        public CartScreen(Customer customer, Connection martConn, Connection depotConn, Screen parent) {
            this.customer = customer;
            this.martConn = martConn;
            this.depotConn = depotConn;
            this.parent = parent;
        }

        private static class StatusRules {
            double threshold;
            double shippingFeeRate;
            double discountRate;
        }

        private StatusRules loadRules(String level) throws SQLException {
            StatusRules rules = new StatusRules();
            String sql = "SELECT threshold, shipping_fee, discount FROM status WHERE level_name = ?";
            try (PreparedStatement pstmt = martConn.prepareStatement(sql)) {
                pstmt.setString(1, level);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        rules.threshold = rs.getDouble("threshold");
                        rules.shippingFeeRate = rs.getDouble("shipping_fee");
                        rules.discountRate = rs.getDouble("discount");
                    }
                }
            }
            return rules;
        }

        private String getFreshLevelName() {
            String q = "SELECT level_name FROM customer WHERE cid = ?";
            try (PreparedStatement pstmt = martConn.prepareStatement(q)) {
                pstmt.setString(1, customer.get_cid());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("level_name");
                    }
                }
            } catch (SQLException e) {
                // fallback
            }
            return customer.get_level_name();
        }

        @Override
        public Screen run() {
            Utility.clearConsole();
            System.out.println("=== Shopping Cart ===");
            Map<String, Integer> cartItems = customer.cart.getItems();
            if (cartItems.isEmpty()) {
                System.out.println("Your cart is empty.");
                UIHelpers.waitForEnter();
                return parent;
            }
            
            double subtotal = 0.0;
            System.out.println(String.format("%-10s | %-15s | %-12s | %-8s | %-10s", "Stock#", "Manufacturer", "Model", "Qty", "Subtotal"));
            System.out.println("-----------------------------------------------------------------");
            
            for (Map.Entry<String, Integer> entry : cartItems.entrySet()) {
                String stock = entry.getKey();
                int qty = entry.getValue();
                
                String detailsSql = "SELECT mname, model_num, price FROM item WHERE stock_num = ?";
                try (PreparedStatement pstmt = martConn.prepareStatement(detailsSql)) {
                    pstmt.setString(1, stock);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            String mname = rs.getString("mname");
                            String model = rs.getString("model_num");
                            double price = rs.getDouble("price");
                            double itemSub = price * qty;
                            subtotal += itemSub;
                            System.out.println(String.format("%-10s | %-15s | %-12s | %-8d | $%-10.2f", stock, mname, model, qty, itemSub));
                        }
                    }
                } catch (SQLException e) {
                    System.out.println("Error loading cart details: " + e.getMessage());
                }
            }
            
            System.out.println("-----------------------------------------------------------------");
            
            String level = getFreshLevelName();
            StatusRules rules;
            try {
                rules = loadRules(level);
            } catch (SQLException e) {
                System.out.println("Error loading discount/shipping rules: " + e.getMessage());
                UIHelpers.waitForEnter();
                return parent;
            }
            
            double discountVal = subtotal * (rules.discountRate / 100.0);
            double discountedSub = subtotal - discountVal;
            
            double shippingFee = 0.0;
            if (!level.equalsIgnoreCase("New") && discountedSub < rules.threshold) {
                shippingFee = discountedSub * (rules.shippingFeeRate / 100.0);
            }
            
            double total = discountedSub + shippingFee;
            
            System.out.println(String.format("Cart Subtotal:            $%8.2f", subtotal));
            System.out.println(String.format("Discount applied (%3.1f%%): -$%8.2f", rules.discountRate, discountVal));
            System.out.println(String.format("Discounted Subtotal:      $%8.2f", discountedSub));
            System.out.println(String.format("Shipping & Handling:      $%8.2f", shippingFee));
            System.out.println("-----------------------------------------------------------------");
            System.out.println(String.format("ORDER TOTAL:              $%8.2f", total));
            System.out.println("=================================================================");
            
            System.out.println("1. Confirm Checkout & Place Order");
            System.out.println("2. Edit Item Quantity");
            System.out.println("3. Remove Item from Cart");
            System.out.println("4. Clear Cart");
            System.out.println("5. Go Back");
            
            String choice = UIHelpers.promptString("Enter selection: ");
            if (choice.equals("1")) {
                performCheckout(discountVal, shippingFee, total);
                return parent;
            } else if (choice.equals("2")) {
                String stock = UIHelpers.promptString("Enter Stock# to edit quantity: ").toUpperCase();
                if (cartItems.containsKey(stock)) {
                    int newQty = UIHelpers.promptInt("Enter new quantity (0 to cancel): ");
                    if (newQty > 0) {
                        try {
                            int stockAvail = 0;
                            String stockSql = "SELECT quantity FROM eDepot_Warehouse_Item WHERE stock_num = ?";
                            try (PreparedStatement pstmt = depotConn.prepareStatement(stockSql)) {
                                pstmt.setString(1, stock);
                                try (ResultSet rs = pstmt.executeQuery()) {
                                    if (rs.next()) stockAvail = rs.getInt("quantity");
                                }
                            }
                            if (stockAvail >= newQty) {
                                customer.cart.update(stock, newQty);
                                System.out.println("Quantity updated.");
                            } else {
                                System.out.println("Insufficient quantity in eDepot. Available: " + stockAvail);
                            }
                            UIHelpers.waitForEnter();
                        } catch (SQLException e) {
                            System.out.println("Database error: " + e.getMessage());
                            UIHelpers.waitForEnter();
                        }
                    }
                } else {
                    System.out.println("Item not in cart.");
                    UIHelpers.sleep(1000);
                }
                return this;
            } else if (choice.equals("3")) {
                String stock = UIHelpers.promptString("Enter Stock# to remove: ").toUpperCase();
                if (cartItems.containsKey(stock)) {
                    customer.cart.remove(stock);
                    System.out.println("Removed from cart.");
                } else {
                    System.out.println("Item not in cart.");
                }
                UIHelpers.sleep(1000);
                return this;
            } else if (choice.equals("4")) {
                customer.cart.clear();
                System.out.println("Cart cleared.");
                UIHelpers.sleep(1000);
                return this;
            } else {
                return parent;
            }
        }

        private void performCheckout(double discountAmt, double shippingFee, double total) {
            System.out.println("\nProcessing checkout...");
            
            try {
                martConn.setAutoCommit(false);
                depotConn.setAutoCommit(false);
                
                for (Map.Entry<String, Integer> entry : customer.cart.getItems().entrySet()) {
                    String stock = entry.getKey();
                    int qty = entry.getValue();
                    int avail = 0;
                    
                    String checkSql = "SELECT quantity FROM eDepot_Warehouse_Item WHERE stock_num = ?";
                    try (PreparedStatement pstmt = depotConn.prepareStatement(checkSql)) {
                        pstmt.setString(1, stock);
                        try (ResultSet rs = pstmt.executeQuery()) {
                            if (rs.next()) {
                                avail = rs.getInt("quantity");
                            }
                        }
                    }
                    if (avail < qty) {
                        System.out.println("Checkout failed: Stock level changed for " + stock + ". Only " + avail + " available.");
                        martConn.rollback();
                        depotConn.rollback();
                        UIHelpers.waitForEnter();
                        return;
                    }
                }
                
                int orderNum = 1;
                String ordNumSql = "SELECT COALESCE(MAX(ord_num), 0) + 1 AS next_val FROM order_table";
                try (Statement stmt = martConn.createStatement();
                     ResultSet rs = stmt.executeQuery(ordNumSql)) {
                    if (rs.next()) {
                        orderNum = rs.getInt("next_val");
                    }
                }
                
                String insOrderSql = "INSERT INTO order_table (ord_num, order_date, total, shipping_fee, discount, cid) VALUES (?, sysdate, ?, ?, ?, ?)";
                try (PreparedStatement pstmt = martConn.prepareStatement(insOrderSql)) {
                    pstmt.setInt(1, orderNum);
                    pstmt.setDouble(2, total);
                    pstmt.setDouble(3, shippingFee);
                    pstmt.setDouble(4, discountAmt);
                    pstmt.setString(5, customer.get_cid());
                    pstmt.executeUpdate();
                }
                
                for (Map.Entry<String, Integer> entry : customer.cart.getItems().entrySet()) {
                    String stock = entry.getKey();
                    int qty = entry.getValue();
                    
                    double price = 0.0;
                    String priceSql = "SELECT price FROM item WHERE stock_num = ?";
                    try (PreparedStatement pstmt = martConn.prepareStatement(priceSql)) {
                        pstmt.setString(1, stock);
                        try (ResultSet rs = pstmt.executeQuery()) {
                            if (rs.next()) price = rs.getDouble("price");
                        }
                    }
                    
                    String insLineSql = "INSERT INTO order_line (stock_num, ord_num, order_price, order_quantity) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement pstmt = martConn.prepareStatement(insLineSql)) {
                        pstmt.setString(1, stock);
                        pstmt.setInt(2, orderNum);
                        pstmt.setDouble(3, price);
                        pstmt.setInt(4, qty);
                        pstmt.executeUpdate();
                    }
                    
                    String decStockSql = "UPDATE eDepot_Warehouse_Item SET quantity = quantity - ? WHERE stock_num = ?";
                    try (PreparedStatement pstmt = depotConn.prepareStatement(decStockSql)) {
                        pstmt.setInt(1, qty);
                        pstmt.setString(2, stock);
                        pstmt.executeUpdate();
                    }
                    
                    checkAndTriggerReplenishment(stock);
                }
                
                martConn.commit();
                depotConn.commit();
                
                System.out.println("Checkout successful!");
                System.out.println("Your Order Number: " + orderNum);
                
                recalculateCustomerStatus();
                
                customer.cart.clear();
                UIHelpers.waitForEnter();
                
            } catch (SQLException e) {
                System.out.println("Database error during checkout execution: " + e.getMessage());
                try {
                    martConn.rollback();
                    depotConn.rollback();
                } catch (SQLException ex) {
                    // ignore
                }
                UIHelpers.waitForEnter();
            } finally {
                try {
                    martConn.setAutoCommit(true);
                    depotConn.setAutoCommit(true);
                } catch (SQLException e) {
                    // ignore
                }
            }
        }

        private void checkAndTriggerReplenishment(String stockNum) throws SQLException {
            String mname = null;
            int quantity = 0;
            int minLevel = 0;
            String wiSql = "SELECT mname, quantity, min_level FROM eDepot_Warehouse_Item WHERE stock_num = ?";
            try (PreparedStatement pstmt = depotConn.prepareStatement(wiSql)) {
                pstmt.setString(1, stockNum);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        mname = rs.getString("mname");
                        quantity = rs.getInt("quantity");
                        minLevel = rs.getInt("min_level");
                    }
                }
            }
            
            if (mname == null) return;
            
            if (quantity < minLevel) {
                int belowMinCount = 0;
                String countSql = "SELECT COUNT(*) AS count_val FROM eDepot_Warehouse_Item WHERE mname = ? AND quantity < min_level";
                try (PreparedStatement pstmt = depotConn.prepareStatement(countSql)) {
                    pstmt.setString(1, mname);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            belowMinCount = rs.getInt("count_val");
                        }
                    }
                }
                
                if (belowMinCount >= 3) {
                    System.out.println("\n[DEPOT NOTICE] >= 3 items below min_level for manufacturer '" + mname + "'. Triggering replenishment order...");
                    
                    int nextOid = 1;
                    String oidSql = "SELECT COALESCE(MAX(oid), 0) + 1 AS next_val FROM eDepot_Replenishment_Order";
                    try (Statement stmt = depotConn.createStatement();
                         ResultSet rs = stmt.executeQuery(oidSql)) {
                        if (rs.next()) {
                            nextOid = rs.getInt("next_val");
                        }
                    }
                    
                    String insRepSql = "INSERT INTO eDepot_Replenishment_Order (oid, mname) VALUES (?, ?)";
                    try (PreparedStatement pstmt = depotConn.prepareStatement(insRepSql)) {
                        pstmt.setInt(1, nextOid);
                        pstmt.setString(2, mname);
                        pstmt.executeUpdate();
                    }
                    
                    String fetchWI = "SELECT stock_num, quantity, max_level FROM eDepot_Warehouse_Item WHERE mname = ? AND quantity < max_level";
                    try (PreparedStatement pstmt = depotConn.prepareStatement(fetchWI)) {
                        pstmt.setString(1, mname);
                        try (ResultSet rs = pstmt.executeQuery()) {
                            while (rs.next()) {
                                String repStock = rs.getString("stock_num");
                                int currentQty = rs.getInt("quantity");
                                int maxLvl = rs.getInt("max_level");
                                int repQty = maxLvl - currentQty;
                                
                                String insRepLineSql = "INSERT INTO eDepot_Replenishment_Line (oid, stock_num, replenishment_quantity) VALUES (?, ?, ?)";
                                try (PreparedStatement pstmt2 = depotConn.prepareStatement(insRepLineSql)) {
                                    pstmt2.setInt(1, nextOid);
                                    pstmt2.setString(2, repStock);
                                    pstmt2.setInt(3, repQty);
                                    pstmt2.executeUpdate();
                                }
                                
                                System.out.println(" -> Ordered replenishment quantity of " + repQty + " for stock number " + repStock);
                            }
                        }
                    }
                }
            }
        }

        private void recalculateCustomerStatus() throws SQLException {
            double sumTotal = 0.0;
            int purchasesCount = 0;
            String queryHistory = "SELECT total FROM (SELECT total FROM order_table WHERE cid = ? ORDER BY order_date DESC, ord_num DESC) WHERE ROWNUM <= 3";
            try (PreparedStatement pstmt = martConn.prepareStatement(queryHistory)) {
                pstmt.setString(1, customer.get_cid());
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        sumTotal += rs.getDouble("total");
                        purchasesCount++;
                    }
                }
            }
            
            String newLevel;
            if (purchasesCount == 0) {
                newLevel = "New";
            } else if (sumTotal > 500.0) {
                newLevel = "Gold";
            } else if (sumTotal > 100.0) {
                newLevel = "Silver";
            } else {
                newLevel = "Green";
            }
            
            String updateSql = "UPDATE customer SET level_name = ? WHERE cid = ?";
            try (PreparedStatement pstmt = martConn.prepareStatement(updateSql)) {
                pstmt.setString(1, newLevel);
                pstmt.setString(2, customer.get_cid());
                pstmt.executeUpdate();
            }
            
            System.out.println("Recalculating status... Last 3 orders total: $" + String.format("%.2f", sumTotal) + ". New status: " + newLevel);
        }
    }

    private static class OrderHistoryListScreen implements Screen {
        private final Customer customer;
        private final Connection martConn;
        private final Connection depotConn;
        private final Screen parent;

        public OrderHistoryListScreen(Customer customer, Connection martConn, Connection depotConn, Screen parent) {
            this.customer = customer;
            this.martConn = martConn;
            this.depotConn = depotConn;
            this.parent = parent;
        }

        @Override
        public Screen run() {
            PageProvider<OrderSummary> provider = (pageNumber, pageSize) -> {
                List<OrderSummary> summaries = new ArrayList<>();
                int offset = (pageNumber - 1) * pageSize;
                String sql = "SELECT ord_num, order_date, total, shipping_fee, discount FROM order_table WHERE cid = ? " +
                             "ORDER BY order_date DESC, ord_num DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
                try (PreparedStatement pstmt = martConn.prepareStatement(sql)) {
                    pstmt.setString(1, customer.get_cid());
                    pstmt.setInt(2, offset);
                    pstmt.setInt(3, pageSize);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            int ordNum = rs.getInt("ord_num");
                            Date date = rs.getDate("order_date");
                            double tot = rs.getDouble("total");
                            double ship = rs.getDouble("shipping_fee");
                            double disc = rs.getDouble("discount");
                            summaries.add(new OrderSummary(ordNum, date, tot, ship, disc));
                        }
                    }
                }
                return summaries;
            };

            Consumer<OrderSummary> displayer = summary -> 
                System.out.println("Order #" + summary.ordNum + " | Date: " + summary.date + " | Total: $" + summary.total + " (Discount: $" + summary.discount + ", Shipping: $" + summary.shippingFee + ")");

            EntityListing.EntitySelectionHandler<OrderSummary> selection = (summary, currentListing) -> {
                Utility.clearConsole();
                System.out.println("=== Order #" + summary.ordNum + " Details ===");
                
                String detailsSql = "SELECT ol.stock_num, i.mname, i.model_num, ol.order_price, ol.order_quantity " +
                                    "FROM order_line ol JOIN item i ON ol.stock_num = i.stock_num WHERE ol.ord_num = ?";
                List<OrderItem> items = new ArrayList<>();
                try (PreparedStatement pstmt = martConn.prepareStatement(detailsSql)) {
                    pstmt.setInt(1, summary.ordNum);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            String stock = rs.getString("stock_num");
                            String man = rs.getString("mname");
                            String model = rs.getString("model_num");
                            double price = rs.getDouble("order_price");
                            int qty = rs.getInt("order_quantity");
                            
                            OrderItem oi = new OrderItem(stock, man, model, price, qty);
                            items.add(oi);
                            System.out.println(String.format("   %s | %-15s | %-12s | Qty: %-3d | Price: $%.2f", stock, man, model, qty, price));
                        }
                    }
                } catch (SQLException e) {
                    System.out.println("Error reading order details: " + e.getMessage());
                }
                
                System.out.println("=================================");
                String choice = UIHelpers.promptString("Re-run this order? (Re-adds all items to cart) (y/n): ");
                if (choice.equalsIgnoreCase("y")) {
                    for (OrderItem oi : items) {
                        int stockAvail = 0;
                        try (PreparedStatement pstmt = depotConn.prepareStatement("SELECT quantity FROM eDepot_Warehouse_Item WHERE stock_num = ?")) {
                            pstmt.setString(1, oi.stockNum);
                            try (ResultSet rs = pstmt.executeQuery()) {
                                if (rs.next()) stockAvail = rs.getInt("quantity");
                            }
                        } catch (SQLException e) {
                            // continue
                        }
                        
                        if (stockAvail >= oi.quantity) {
                            customer.cart.add(oi.stockNum, oi.quantity);
                            System.out.println(" -> Re-added " + oi.quantity + " of " + oi.stockNum + " to cart.");
                        } else {
                            System.out.println(" -> Short stock for " + oi.stockNum + ". Available: " + stockAvail + " (Wanted " + oi.quantity + ")");
                        }
                    }
                    UIHelpers.waitForEnter();
                }
                return currentListing;
            };

            List<TuiAction> actions = new ArrayList<>();
            return new EntityListing<>("Order History", provider, displayer, selection, actions, parent).run();
        }

        private static class OrderSummary {
            int ordNum;
            Date date;
            double total;
            double shippingFee;
            double discount;

            public OrderSummary(int ordNum, Date date, double total, double shippingFee, double discount) {
                this.ordNum = ordNum;
                this.date = date;
                this.total = total;
                this.shippingFee = shippingFee;
                this.discount = discount;
            }
        }

        private static class OrderItem {
            String stockNum;
            String manufacturer;
            String model;
            double price;
            int quantity;

            public OrderItem(String stockNum, String manufacturer, String model, double price, int quantity) {
                this.stockNum = stockNum;
                this.manufacturer = manufacturer;
                this.model = model;
                this.price = price;
                this.quantity = quantity;
            }
        }
    }

    private static List<Items> fetchItems(Connection connection, int pageNumber, int pageSize, String whereClause, List<Object> params) throws SQLException {
        List<Items> list = new ArrayList<>();
        int offset = (pageNumber - 1) * pageSize;
        String query = "SELECT stock_num, category, price, warranty, model_num, mname FROM item";
        if (whereClause != null && !whereClause.trim().isEmpty()) {
            query += " WHERE " + whereClause;
        }
        query += " ORDER BY stock_num OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            int idx = 1;
            if (params != null) {
                for (Object p : params) {
                    pstmt.setObject(idx++, p);
                }
            }
            pstmt.setInt(idx++, offset);
            pstmt.setInt(idx++, pageSize);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String stockNum = rs.getString("stock_num");
                    String category = rs.getString("category");
                    int price = (int) rs.getDouble("price");
                    int warranty = rs.getInt("warranty");
                    String modelNum = rs.getString("model_num");
                    String mname = rs.getString("mname");
                    
                    Items item = new Items(stockNum, category, price, warranty, modelNum, mname);
                    
                    String attrQuery = "SELECT attr_name, attr_value, attr_unit FROM Item_Attribute WHERE stock_num = ?";
                    try (PreparedStatement attrStmt = connection.prepareStatement(attrQuery)) {
                        attrStmt.setString(1, stockNum);
                        try (ResultSet attrRs = attrStmt.executeQuery()) {
                            while (attrRs.next()) {
                                String name = attrRs.getString("attr_name");
                                double val = attrRs.getDouble("attr_value");
                                boolean valWasNull = attrRs.wasNull();
                                String unit = attrRs.getString("attr_unit");
                                String valStr = "";
                                if (!valWasNull) {
                                    valStr = (val == (long) val) ? String.valueOf((long) val) : String.valueOf(val);
                                }
                                if (unit != null && !unit.trim().isEmpty()) {
                                    if (!valStr.isEmpty()) {
                                        valStr += " " + unit.trim();
                                    } else {
                                        valStr = unit.trim();
                                    }
                                }
                                item.addAttribute(name, valStr);
                            }
                        }
                    }
                    
                    String compQuery = "SELECT replacement_stock_num FROM Compatible_With WHERE orig_stock_num = ?";
                    try (PreparedStatement compStmt = connection.prepareStatement(compQuery)) {
                        compStmt.setString(1, stockNum);
                        try (ResultSet compRs = compStmt.executeQuery()) {
                            while (compRs.next()) {
                                item.addCompatibility(compRs.getString("replacement_stock_num"));
                            }
                        }
                    }
                    
                    list.add(item);
                }
            }
        }
        return list;
    }
}