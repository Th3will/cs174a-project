package eDepot.ui;

import java.util.Scanner;

public class eDepotCLI {
    private final Scanner scanner;

    public eDepotCLI() {
        this.scanner = new Scanner(System.in);
    }

    public void start() {
        System.out.println("---------------------------");
        System.out.println("    eDepot CLI Terminal    ");
        System.out.println("---------------------------");
        boolean running = true;
        
        while (running) {
            printMenu();
            System.out.print("\nEnter your choice (1-5): ");
            String input = scanner.nextLine().trim();

            switch (input) {
                case "1":
                    handleReceiveShippingNotice();
                    break;
                case "2":
                    handleReceiveShipment();
                    break;
                case "3":
                    handleCheckItemQuantity();
                    break;
                case "4":
                    handleFillOrder();
                    break;
                case "5":
                    System.out.println("Exiting eDepot CLI Terminal");
                    running = false;
                    break;
                default:
                    System.out.println("Not a valid selection, please try again.");
            }
        }
    }

    private void printMenu() {
        System.out.println("\n=== MAIN MENU ===");
        System.out.println("1. Receive Shipping Notice (from manufacturer)");
        System.out.println("2. Receive Shipment (from shipping company)");
        System.out.println("3. Check Item Quantity");
        System.out.println("4. Fill eMART Order");
        System.out.println("5. Exit");
    }

    private void handleReceiveShippingNotice() {
        System.out.println("\n[Transaction: Receive Shipping Notice]");
        // TODO: Prompt for shipping notice details
        // TODO: Pass data to inventoryManager.processShippingNotice(...)
    }

    private void handleReceiveShipment() {
        System.out.println("\n[Transaction: Receive Shipment]");
        // TODO: Prompt for the prior shipping notice ID
        // TODO: Pass data to inventoryManager.processShipmentArrival(...)
    }

    private void handleCheckItemQuantity() {
        System.out.println("\n[Transaction: Check Item Quantity]");
        System.out.print("Enter Stock Number (XXnnnnn): ");
        String stockNum = scanner.nextLine().trim();
        
        // TODO: int quantity = inventoryManager.checkQuantity(stockNum);
        // System.out.println("Current Quantity for " + stockNum + ": " + quantity);
    }

    private void handleFillOrder() {
        System.out.println("\n[Transaction: Fill an Order]");
        // TODO: Prompt for eMART Order Number
        // TODO: Pass data to inventoryManager.fillOrder(...)
        // Note: The inventory manager will handle the automated replenishment logic internally!
    }
}