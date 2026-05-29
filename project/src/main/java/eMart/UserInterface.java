package eMart;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;
import java.util.function.Consumer;

public class UserInterface {

    @FunctionalInterface
    public interface AuthCallback {
        Screen onSuccess(User user);
    }

    // Generic Login Screen
    public static class BaseLoginScreen implements Screen {
        private final Connection conn;
        private final String userType; // "customer" or "manager"
        private final Screen parent;
        private final AuthCallback callback;

        public BaseLoginScreen(Connection conn, String userType, Screen parent, AuthCallback callback) {
            this.conn = conn;
            this.userType = userType;
            this.parent = parent;
            this.callback = callback;
        }

        @Override
        public Screen run() {
            Utility.clearConsole();
            System.out.println("=== " + userType.substring(0, 1).toUpperCase() + userType.substring(1) + " Login ===");
            System.out.println("(Press Enter on all fields to cancel)");
            String usernameOrEmail = UIHelpers.promptString("Username/Email: ");
            String password = UIHelpers.promptString("Password: ");

            if (usernameOrEmail.isEmpty() && password.isEmpty()) {
                return parent;
            }
            
            if (usernameOrEmail.isEmpty() || password.isEmpty()) {
                System.out.println("Error: Username/Email and password cannot be empty.");
                UIHelpers.waitForEnter();
                return this;
            }

            String table = userType.equals("customer") ? "customer" : "manager";
            String idCol = userType.equals("customer") ? "cid" : "eid";

            String query = "SELECT * FROM " + table + " WHERE (" + idCol + " = ? OR email = ?) AND password = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, usernameOrEmail);
                pstmt.setString(2, usernameOrEmail);
                pstmt.setString(3, password);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        String id = rs.getString(idCol);
                        String first_name = rs.getString("first_name");
                        String middle_name = rs.getString("middle_name");
                        String last_name = rs.getString("last_name");
                        String address = rs.getString("address");
                        String email = rs.getString("email");
                        
                        User user = new User(id, password, email, address, first_name, middle_name, last_name);
                        System.out.println("\nWelcome, " + first_name + "!");
                        UIHelpers.sleep(1000);
                        return callback.onSuccess(user);
                    }
                }
            } catch (SQLException e) {
                System.out.println("Database error during verification: " + e.getMessage());
                UIHelpers.waitForEnter();
                return parent;
            }

            System.out.println("Invalid username/email or password.");
            UIHelpers.waitForEnter();
            return this;
        }
    }

    // Generic Registration Screen
    public static class BaseRegisterScreen implements Screen {
        private final Connection conn;
        private final String userType; // "customer" or "manager"
        private final Screen parent;

        public BaseRegisterScreen(Connection conn, String userType, Screen parent) {
            this.conn = conn;
            this.userType = userType;
            this.parent = parent;
        }

        private boolean userExists(String id, String userType) throws SQLException {
            if (userType.equals("customer")) {
                return DbImporter.recordExists(conn, "customer", "cid", id);
            } else {
                return DbImporter.recordExists(conn, "manager", "eid", id);
            }
        }

        @Override
        public Screen run() {
            Utility.clearConsole();
            System.out.println("=== Create New " + userType.substring(0, 1).toUpperCase() + userType.substring(1) + " Account ===");
            System.out.println("(Press Enter on all fields to cancel)");
            
            String idPrompt = userType.equals("customer") ? "Customer ID (cid): " : "Manager ID (eid): ";
            String id = UIHelpers.promptString(idPrompt);
            String firstName = UIHelpers.promptString("First Name: ");
            String middleName = UIHelpers.promptString("Middle Name (Optional): ");
            String lastName = UIHelpers.promptString("Last Name: ");
            String password = UIHelpers.promptString("Password: ");
            String email = UIHelpers.promptString("Email: ");
            String address = UIHelpers.promptString("Address: ");

            if (id.isEmpty() && firstName.isEmpty() && middleName.isEmpty() && lastName.isEmpty() 
                    && password.isEmpty() && email.isEmpty() && address.isEmpty()) {
                return parent;
            }

            if (id.isEmpty() || firstName.isEmpty() || lastName.isEmpty() || password.isEmpty() || email.isEmpty() || address.isEmpty()) {
                System.out.println("Error: Required fields cannot be empty.");
                UIHelpers.waitForEnter();
                return this;
            }

            try {
                if (userExists(id, userType)) {
                    System.out.println("An account with ID '" + id + "' already exists.");
                    UIHelpers.waitForEnter();
                    return this;
                }
            } catch (SQLException e) {
                System.out.println("Database error: " + e.getMessage());
                UIHelpers.waitForEnter();
                return parent;
            }

            try {
                if (userType.equals("customer")) {
                    DbImporter.ensureStatusExists(conn, "New");
                    String sql = "INSERT INTO customer (cid, first_name, middle_name, last_name, password, email, address, level_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setString(1, id);
                        pstmt.setString(2, firstName);
                        pstmt.setString(3, middleName.isEmpty() ? null : middleName);
                        pstmt.setString(4, lastName);
                        pstmt.setString(5, password);
                        pstmt.setString(6, email);
                        pstmt.setString(7, address);
                        pstmt.setString(8, "New");
                        pstmt.executeUpdate();
                    }
                    System.out.println("Customer account successfully created!");
                } else {
                    String sql = "INSERT INTO manager (eid, first_name, middle_name, last_name, password, email, address) VALUES (?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setString(1, id);
                        pstmt.setString(2, firstName);
                        pstmt.setString(3, middleName.isEmpty() ? null : middleName);
                        pstmt.setString(4, lastName);
                        pstmt.setString(5, password);
                        pstmt.setString(6, email);
                        pstmt.setString(7, address);
                        pstmt.executeUpdate();
                    }
                    System.out.println("Manager account successfully created!");
                }
                UIHelpers.waitForEnter();
                return parent;
            } catch (SQLException e) {
                System.out.println("Database error during registration: " + e.getMessage());
                UIHelpers.waitForEnter();
                return this;
            }
        }
    }
}

// Consolidating TUI Infrastructure to package-private definitions:

@FunctionalInterface
interface Screen {
    Screen run();
}

interface TuiAction {
    boolean matches(String input);
    Screen execute(String input);
    default String getCommandLabel() { return ""; }
}

@FunctionalInterface
interface PageProvider<T> {
    List<T> fetchPage(int pageNumber, int pageSize) throws SQLException;
}

class UIHelpers {
    private static final Scanner sc = new Scanner(System.in);

    public static String nextLine() {
        if (sc.hasNextLine()) {
            return sc.nextLine();
        }
        return "";
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void waitForEnter() {
        System.out.println("\nPress [Enter] to continue...");
        nextLine();
    }

    public static String promptString(String prompt) {
        System.out.print(prompt);
        return nextLine().trim();
    }

    public static int promptInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = nextLine().trim();
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid number. Please try again.");
            }
        }
    }

    public static double promptDouble(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = nextLine().trim();
            try {
                return Double.parseDouble(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid numeric value. Please try again.");
            }
        }
    }
}

class EntityListing<T> implements Screen {
    private final String title;
    private final PageProvider<T> pageProvider;
    private final Consumer<T> displayer;
    private final EntitySelectionHandler<T> selectionHandler;
    private final List<TuiAction> customActions;
    private final Screen parentScreen;
    
    private int currentPage = 1;
    private final int pageSize = 10;

    @FunctionalInterface
    public interface EntitySelectionHandler<T> {
        Screen handle(T entity, Screen currentListingScreen);
    }
    
    public EntityListing(String title, PageProvider<T> pageProvider, Consumer<T> displayer, 
                         EntitySelectionHandler<T> selectionHandler, List<TuiAction> customActions, Screen parentScreen) {
        this.title = title;
        this.pageProvider = pageProvider;
        this.displayer = displayer;
        this.selectionHandler = selectionHandler;
        this.customActions = customActions;
        this.parentScreen = parentScreen;
    }
    
    @Override
    public Screen run() {
        while (true) {
            Utility.clearConsole();
            System.out.println("=== " + title + " (Page " + currentPage + ") ===");
            List<T> items;
            try {
                items = pageProvider.fetchPage(currentPage, pageSize);
            } catch (SQLException e) {
                System.out.println("Error fetching page data: " + e.getMessage());
                UIHelpers.waitForEnter();
                return parentScreen;
            }
            
            if (items.isEmpty() && currentPage > 1) {
                System.out.println("No more items. Returning to page " + (currentPage - 1));
                currentPage--;
                UIHelpers.sleep(1500);
                continue;
            }
            
            if (items.isEmpty()) {
                System.out.println("(No entities found)");
            } else {
                for (int i = 0; i < items.size(); i++) {
                    System.out.print("[" + (i + 1) + "] ");
                    displayer.accept(items.get(i));
                    System.out.println();
                }
            }
            
            System.out.println("\n--- Actions ---");
            System.out.print("Navigation: ");
            if (currentPage > 1) System.out.print("[<] Prev Page  ");
            if (items.size() == pageSize) System.out.print("[>] Next Page  ");
            System.out.println("[e] Go Back");
            
            if (customActions != null && !customActions.isEmpty()) {
                System.out.print("Commands: ");
                for (TuiAction act : customActions) {
                    System.out.print(act.getCommandLabel() + "  ");
                }
                System.out.println();
            }
            
            System.out.print("Enter selection (number/command): ");
            String input = UIHelpers.nextLine().trim();
            
            if (input.equalsIgnoreCase("e")) {
                return parentScreen;
            } else if (input.equals("<")) {
                if (currentPage > 1) {
                    currentPage--;
                } else {
                    System.out.println("Already on the first page.");
                    UIHelpers.sleep(1000);
                }
            } else if (input.equals(">")) {
                if (items.size() == pageSize) {
                    currentPage++;
                } else {
                    System.out.println("Already on the last page.");
                    UIHelpers.sleep(1000);
                }
            } else {
                try {
                    int selectionIdx = Integer.parseInt(input);
                    if (selectionIdx >= 1 && selectionIdx <= items.size()) {
                        if (selectionHandler != null) {
                            Screen next = selectionHandler.handle(items.get(selectionIdx - 1), this);
                            if (next != null) return next;
                        }
                    } else {
                        System.out.println("Invalid selection index.");
                        UIHelpers.sleep(1000);
                    }
                } catch (NumberFormatException e) {
                    boolean matched = false;
                    if (customActions != null) {
                        for (TuiAction action : customActions) {
                            if (action.matches(input)) {
                                Screen next = action.execute(input);
                                if (next != null) return next;
                                matched = true;
                                break;
                            }
                        }
                    }
                    if (!matched) {
                        System.out.println("Unknown command: " + input);
                        UIHelpers.sleep(1000);
                    }
                }
            }
        }
    }
}
