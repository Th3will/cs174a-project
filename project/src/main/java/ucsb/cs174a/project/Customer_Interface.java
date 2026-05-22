package ucsb.cs174a.project;

import java.util.Properties;
import java.util.Scanner;
import java.util.Map;

import oracle.jdbc.OracleConnection;
import oracle.jdbc.pool.OracleDataSource;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

// TUI login as a Customer or a Manager
// Create New Account
// Customers can browse, add items to cart and Submit orders, also look at order history
// Managers 
public class Customer_Interface {
    private static Scanner sc;

    public static void login(Connection emartConn, Connection depotConn) {
        Customer customer = null;
        while (true) {
            // assume 3 attempts allowed to login, before prompting again
            for (int i = 0; i < 3; i++) {
                System.out.println("Enter your email: ");
                String email = sc.nextLine();
                System.out.println("Enter your password: ");
                String password = sc.nextLine();
                // Validate user and pull account info
                // Check if email and password match to a customer account
                // if yes, pull all info and create customer object
                String query = "SELECT * FROM customer WHERE email = ? AND password = ?";
                try (PreparedStatement pstmt = emartConn.prepareStatement(query)) {
                    pstmt.setString(1, email);
                    pstmt.setString(2, password);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            String cid = rs.getString("cid");
                            String first_name = rs.getString("first_name");
                            String middle_name = rs.getString("middle_name");
                            String last_name = rs.getString("last_name");
                            String address = rs.getString("address");
                            String level_name = rs.getString("level_name");
                            customer = new Customer(cid, password, email, address, first_name, middle_name, last_name,
                                    level_name);
                            break;
                        } else {
                            System.out.println("Invalid email or password.");
                        }
                    }
                } catch (SQLException e) {
                    System.out.println("Database error during login: " + e.getMessage());
                }
            }
            if (customer == null) {
                System.out.println("Failed to log in. Would you like to try again? (y/n)");
                String choice = sc.nextLine();
                if (choice.equals("y")) {
                    continue;
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        if (customer != null) {
            // shop(customer, emartConn, depotConn);
            System.out.println("Hello, " + customer.get_first_name() + " " + customer.get_last_name());
        }
        return;
    }

    public static void create_new_account(Connection emartConn, Connection depotConn) {
        System.out.println("Enter your full name: ");
        String name = sc.nextLine();
        System.out.println("Enter your email: ");
        String email = sc.nextLine();
        System.out.println("Enter your password: ");
        String password = sc.nextLine();
        System.out.println("Enter your address: ");
        String address = sc.nextLine();

        return;
    }

    public static void shop(Customer customer, Connection emartConn, Connection depotConn) {
        while (true) {
            System.out.println("What would you like to do? (you're logged in!)\n" +
                    "1. Browse items\n" +
                    "2. Search for items\n" +
                    "3. Add to cart\n" +
                    "4. Checkout\n" +
                    "5. View previous orders\n" +
                    "6. Logout");
            String choice = sc.nextLine();
            if (choice.equals("1")) {
                list_items(customer, emartConn, depotConn);
            } else if (choice.equals("2")) {
                search_item(customer, emartConn, depotConn);
            } else if (choice.equals("3")) {
                add_to_cart(customer, emartConn, depotConn);
            } else if (choice.equals("4")) {
                checkout(customer, emartConn, depotConn);
            } else if (choice.equals("5")) {
                view_prev_order(customer, emartConn, depotConn);
            } else if (choice.equals("6")) {
                break;
            } else {
                System.out.println("Invalid choice. Please try again.");
            }
        }
        return;
    }

    // get all items and print them
    // maybe pagination? (not specified)
    public static void list_items(Customer customer, Connection emartConn, Connection depotConn) {

        return;
    }

    // search_item, this one's a lil complicated
    // add to cart, see above
    public static void search_item(Customer customer, Connection emartConn, Connection depotConn) {
        return;
    }

    // add to cart, helper function
    public static void add_to_cart(Customer customer, Connection emartConn, Connection depotConn) {
        System.out.println("Please enter the stock number of the item you want to add to your cart");
        String stockNum = sc.nextLine();
        // check if valid item, also get quantity 
        int quantityInStock = 0;
        String query = "SELECT * FROM item_inventory WHERE stock_num = ?";
        try (PreparedStatement pstmt = depotConn.prepareStatement(query)) {
            pstmt.setString(1, stockNum);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    quantityInStock = rs.getInt("quantity");
                } else {
                    System.out.println("Item not found.");
                    return;
                }
            }
        } catch (SQLException e) {
            System.out.println("Database error during add to cart: " + e.getMessage());
            return;
        }

        System.out.println("Please enter the quantity of the item you want to add to your cart");
        int quantity = sc.nextInt();
        // Update the cart with the new item quantity
        // check if there is enough stock first using depot conn
        
        customer.cart.add(stockNum, quantity);
        return;
    }

    // checkout
    public static void checkout(Customer customer, Connection emartConn, Connection depotConn) {

        // An order is made of all items currently in the customer's cart
        double total_price = customer.cart.calc_price(emartConn);


        double shipping_threshold;
        double shipping_fee;
        double discount;

        // get shipping_threshold and shipping_fee from database using customer's level
        String query = "SELECT * FROM customer_level WHERE level_name = ?";
        try (PreparedStatement pstmt = emartConn.prepareStatement(query)) {
            pstmt.setString(1, customer.get_level_name());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    shipping_threshold = rs.getDouble("shipping_threshold");
                    shipping_fee = rs.getDouble("shipping_fee");
                    discount = rs.getDouble("discount");
                } else {
                    System.out.println("Customer level not found.");
                    return;
                }
            }
        } catch (SQLException e) {
            System.out.println("Database error during checkout: " + e.getMessage());
            return;
        }

        total_price = total_price * (100 - discount)/100;

        if (total_price >= shipping_threshold) {
            shipping_fee = 0;
        }

        // add date somehow?
        java.util.Date today = new java.util.Date();

        // insert order
        // ord num, order_date, total, shipping_fee, discount, cid
        // ord num is generated by the database
        query = "INSERT INTO orders (order_date, total, shipping_fee, discount, cid) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = emartConn.prepareStatement(query)) {
            pstmt.setDate(1, new java.sql.Date(today.getTime()));
            pstmt.setDouble(2, total_price);
            pstmt.setDouble(3, shipping_fee);
            pstmt.setDouble(4, discount);
            pstmt.setString(5, customer.get_cid());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Database error during checkout: " + e.getMessage());
            return;
        }

        // get order num
        int order_num = 0;
        query = "SELECT order_num FROM orders WHERE order_date = ? AND total = ? AND shipping_fee = ? AND discount = ? AND cid = ?";
        try (PreparedStatement pstmt = emartConn.prepareStatement(query)) {
            pstmt.setDate(1, new java.sql.Date(today.getTime()));
            pstmt.setDouble(2, total_price);
            pstmt.setDouble(3, shipping_fee);
            pstmt.setDouble(4, discount);
            pstmt.setString(5, customer.get_cid());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    order_num = rs.getInt("order_num");
                } else {
                    System.out.println("Order not found.");
                    return;
                }
            }
        } catch (SQLException e) {
            System.out.println("Database error during checkout: " + e.getMessage());
            return;
        }

        // populate order_line with all items in cart
        // stock_num, order_num, order_price, order_quantity
        for (Map.Entry<String, Integer> entry : customer.cart.getItems().entrySet()) {
            String stock_num = entry.getKey();
            int order_quantity = entry.getValue();
            int order_price = Items.getPriceByStockNum(stock_num, emartConn);

            query = "INSERT INTO order_line (stock_num, order_num, order_price, order_quantity) VALUES (?, ?, ?, ?)";
            try (PreparedStatement pstmt = emartConn.prepareStatement(query)) {
                pstmt.setString(1, stock_num);
                pstmt.setInt(2, order_num);
                pstmt.setDouble(3, order_price);
                pstmt.setInt(4, order_quantity);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                System.out.println("Database error during checkout: " + e.getMessage());
                return;
            }
        }
        return;
    }

    // view_prev_order
    // also handles reordering?
    public static void view_prev_order(Customer customer, Connection emartConn, Connection depotConn) {
        return;
    }

    public static void main(String[] args) throws SQLException {
        // TUI loop for logging in
        // Connect to DB
        Properties info = new Properties();
        sc = new Scanner(System.in);

        Utility.log("Initializing connection properties...");
        info.put(OracleConnection.CONNECTION_PROPERTY_USER_NAME, Constants.DB_USER);
        info.put(OracleConnection.CONNECTION_PROPERTY_PASSWORD, Constants.DB_PASSWORD);
        info.put(OracleConnection.CONNECTION_PROPERTY_DEFAULT_ROW_PREFETCH, "20");

        Utility.log("Creating OracleDataSource for eMart...");
        OracleDataSource odsMart = new OracleDataSource();
        odsMart.setURL(Constants.Mart_DB_URL);
        odsMart.setConnectionProperties(info);

        Utility.log("Creating OracleDataSource for eDepot...");
        OracleDataSource odsDepot = new OracleDataSource();
        odsDepot.setURL(Constants.Depot_DB_URL);
        odsDepot.setConnectionProperties(info);

        try (OracleConnection martConn = (OracleConnection) odsMart.getConnection();
                OracleConnection depotConn = (OracleConnection) odsDepot.getConnection()) {
            while (true) {
                System.out.println("Welcome to the eMart TUI!\n" +
                        "1. Login\n" +
                        "2. Create New Account\n" +
                        "3. Exit");
                String choice = sc.nextLine();
                if (choice.equals("1")) {
                    login(martConn, depotConn);
                } else if (choice.equals("2")) {
                    create_new_account(martConn, depotConn);
                } else if (choice.equals("3")) {
                    break;
                } else {
                    System.out.println("Invalid choice. Please try again.");
                }
            }
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
        sc.close();
    }
}