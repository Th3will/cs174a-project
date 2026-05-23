

import eMart.Items;

public class Playground {
    public static void main(String[] args) {
        System.out.println("Testing Items print method:");
        Items item = new Items("AA00101", "Laptop", 1630, 12, "A6111", "HP");
        item.addAttribute("Processor", "Intel i7");
        item.addAttribute("RAM", "16GB");
        item.addCompatibility("HP Docking Station G5");
        item.addCompatibility("HP USB-C Charger");
        item.print();
    }
}
