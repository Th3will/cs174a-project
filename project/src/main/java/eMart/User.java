package eMart;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class User {
    // Login
    public User Login(String email, String password) {
        return null;
    }
    // List Items
    public void ListItems() {
        // get a Cursor to the Mart and print items.
            // For each item:
                // get stock_num, category, price, warranty, model_num, man_name
                // get attributes using the stock_num 
                // get model_num's details using model_num
                // Print it
    }

    private void printItem(String stock_num, Connection connection) throws SQLException {

    }
    
}
