package eMart;

import java.util.HashMap;
import java.util.Map;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Customer {
    // fields
    private String cid;
    private String password;
    private String email;
    private String address;
    private String first_name;
    private String middle_name;
    private String last_name;
    private String level_name;
    public Cart cart;

    // Cart
    public class Cart {
        private Map<String, Integer> items;
        public Cart() {
            this.items = new HashMap<>();
        }
        public void add(String stock_num, int quantity) {
            this.items.put(stock_num, quantity);
        }
        public void remove(String stock_num) {
            this.items.remove(stock_num);
        }
        public void update(String stock_num, int quantity) {
            this.items.put(stock_num, quantity);
        }
        public void clear() {
            this.items.clear();
        }

        public Map<String, Integer> getItems() {
            return this.items;
        }

        public void print() {
            for (Map.Entry<String, Integer> entry : this.items.entrySet()) {
                System.out.println(entry.getKey() + " " + entry.getValue());
            }
        }
       public double calc_price(Connection emartConn) {
        double total_price = 0;
        // for each item in cart, get price from emart and multiply by quantity
        for (Map.Entry<String, Integer> entry : this.items.entrySet()) {
            // query emart to get price
            int price = Items.getPriceByStockNum(entry.getKey(), emartConn);
            if (price == 0) {
                System.out.println("Item not found in emart");
                return 0;
            }
            total_price += entry.getValue() * price;
        }
        return total_price;
       }    
    }

    Customer(String cid, String password, String email, String address, String first_name, String middle_name, String last_name, String level_name) {
        this.cid = cid;
        this.password = password;
        this.email = email;
        this.address = address;
        this.first_name = first_name;
        this.middle_name = middle_name;
        this.last_name = last_name;
        this.level_name = level_name;
    }

    // Getters
    public String get_cid() {
        return this.cid;
    }
    public String get_password() {
        return this.password;
    }
    public String get_email() {
        return this.email;
    }
    public String get_address() {
        return this.address;
    }
    public String get_first_name() {
        return this.first_name;
    }
    public String get_middle_name() {
        return this.middle_name;
    }
    public String get_last_name() {
        return this.last_name;
    }
    public String get_level_name() {
        return this.level_name;
    }

    // Methods
    // list items
        // filter by attribute
    // add to cart
}
