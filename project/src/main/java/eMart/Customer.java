package eMart;

import java.util.HashMap;
import java.util.Map;
import java.sql.Connection;

public class Customer {
    private final User user;
    private final String level_name;
    public final Cart cart;

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
            for (Map.Entry<String, Integer> entry : this.items.entrySet()) {
                int price = Items.getPriceByStockNum(entry.getKey(), emartConn);
                if (price == 0) {
                    System.out.println("Item not found in eMart");
                    return 0;
                }
                total_price += entry.getValue() * price;
            }
            return total_price;
        }    
    }

    public Customer(User user, String level_name) {
        this.user = user;
        this.level_name = level_name;
        this.cart = new Cart();
    }

    // Getters delegating to User
    public String get_cid() {
        return this.user.getId();
    }
    
    public String get_password() {
        return this.user.getPassword();
    }
    
    public String get_email() {
        return this.user.getEmail();
    }
    
    public String get_address() {
        return this.user.getAddress();
    }
    
    public String get_first_name() {
        return this.user.getFirstName();
    }
    
    public String get_middle_name() {
        return this.user.getMiddleName();
    }
    
    public String get_last_name() {
        return this.user.getLastName();
    }
    
    public String get_level_name() {
        return this.level_name;
    }
}
