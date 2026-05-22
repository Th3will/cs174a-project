package ucsb.cs174a.project;

import java.util.ArrayList;
import java.util.List;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Items {
    public String stock_num;
    public String category;
    public int price;
    public int warranty;
    public String model_num;
    public String man_name;
    public List<Attribute> attributes;
    public List<String> compatibilities;

    public static class Attribute {
        public String key;
        public String value;

        public Attribute(String key, String value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String toString() {
            return key + ": " + value;
        }
    }

    public static int getPriceByStockNum(String stock_num, Connection conn) {
        String query = "SELECT price FROM item WHERE stock_num = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, stock_num);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("price");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public Items() {
        this.attributes = new ArrayList<>();
        this.compatibilities = new ArrayList<>();
    }

    public Items(String stock_num, String category, int price, int warranty, String model_num, String man_name) {
        this.stock_num = stock_num;
        this.category = category;
        this.price = price;
        this.warranty = warranty;
        this.model_num = model_num;
        this.man_name = man_name;
        this.attributes = new ArrayList<>();
        this.compatibilities = new ArrayList<>();
    }

    public void addAttribute(String key, String value) {
        this.attributes.add(new Attribute(key, value));
    }

    public void addCompatibility(String compatibility) {
        this.compatibilities.add(compatibility);
    }

    public void print() {
        if (Utility.verbose) {
            System.out.println(this.toString());
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("==================================================\n");
        sb.append(String.format("Item Stock No: %s (%s)\n", stock_num, category));
        sb.append("--------------------------------------------------\n");
        sb.append(String.format("  Manufacturer: %s\n", man_name));
        sb.append(String.format("  Model Number: %s\n", model_num));
        sb.append(String.format("  Price:        $%d\n", price));
        sb.append(String.format("  Warranty:     %d months\n", warranty));
        sb.append("--------------------------------------------------\n");
        sb.append("  Attributes:\n");
        if (attributes == null || attributes.isEmpty()) {
            sb.append("    (None)\n");
        } else {
            for (Attribute attr : attributes) {
                sb.append(String.format("    - %s: %s\n", attr.key, attr.value));
            }
        }
        sb.append("  Compatibilities:\n");
        if (compatibilities == null || compatibilities.isEmpty()) {
            sb.append("    (None)\n");
        } else {
            for (String comp : compatibilities) {
                sb.append(String.format("    - %s\n", comp));
            }
        }
        sb.append("==================================================");
        return sb.toString();
    }
}

class ParsedItem {
    public String stockNum;
    public String category;
    public String manufacturer;
    public String modelNum;
    public int warranty;
    public double price;
    public List<String> attributes = new ArrayList<>();
    public List<String> compatibilities = new ArrayList<>();
    public int minLevel;
    public int quantity;
    public int maxLevel;
    public String location;
}

class AttributeParsed {
    public String name;
    public Double value;
    public String unit;
}
