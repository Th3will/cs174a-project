package eDepot.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import eDepot.models.ShippingNotice;
import eDepot.services.InventoryManager;
import eDepot.services.InventoryManager.NoticeLineInput;
import eDepot.services.InventoryManager.ProcessedLine;

public class eDepotCLI {
    private final Scanner scanner;
    private final InventoryManager inventoryManager;

    public eDepotCLI() {
        this.scanner = new Scanner(System.in);
        this.inventoryManager = new InventoryManager();
    }

    public void start() {
        System.out.println("---------------------------");
        System.out.println("|   eDepot CLI Terminal   |");
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
                    System.out.println("\nNot a valid selection, please try again.");
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

        Integer snid;
        while (true) {
            snid = promptPositiveInt("Enter Shipping Notice ID (or blank to cancel): ", true);
            if (snid == null) {
                System.out.println("Cancelled.");
                return;
            }
            if (inventoryManager.shippingNoticeExists(snid)) {
                System.out.println("  Shipping notice ID " + snid + " already exists. Enter a different ID.");
                continue;
            }
            break;
        }

        String company = promptNonEmpty("Enter Shipping Company Name (or blank to cancel): ", true);
        if (company == null) {
            System.out.println("Cancelled.");
            return;
        }

        List<NoticeLineInput> lines = new ArrayList<>();
        System.out.println("\nEnter notice line items. Type 'done' as the manufacturer to finish.");

        int lineNumber = 1;
        while (true) {
            System.out.println("\n-- Line " + lineNumber + " --");
            String manufacturer = promptNonEmpty("  Manufacturer (or 'done' to finish): ", false);
            if (manufacturer.equalsIgnoreCase("done")) {
                break;
            }

            String modelNumber = promptNonEmpty("  Model Number: ", false);
            Integer noticeQuantity = promptPositiveInt("  Notice Quantity (positive integer): ", false);

            NoticeLineInput input;
            boolean isExisting;
            try {
                isExisting = inventoryManager.isKnownWarehouseProduct(manufacturer, modelNumber);
            }
            catch (RuntimeException e) {
                System.out.println("  Error checking product in database: " + e.getMessage());
                System.out.println("  Notice cancelled.");
                return;
            }

            if (isExisting) {
                System.out.println("  Existing product found - replenishment will be incremented.");
                input = new NoticeLineInput(manufacturer, modelNumber, noticeQuantity);
            } 
            else {
                System.out.println("  New product - additional warehouse setup info required:");
                Integer minLevel = promptPositiveInt("    Minimum stock level: ", false);
                Integer maxLevel = promptPositiveInt("    Maximum stock level: ", false);
                while (maxLevel < minLevel) {
                    System.out.println("    Max level must be >= min level.");
                    maxLevel = promptPositiveInt("    Maximum stock level: ", false);
                }
                String locLetter;
                Integer locNumber;
                while (true) {
                    locLetter = promptLocationLetter("    Location letter (A-Z): ");
                    locNumber = promptLocationNumber("    Location number: ");
                    if (inventoryManager.isLocationOccupied(locLetter, locNumber)) {
                        System.out.println("  Location " + locLetter + locNumber
                                + " is already occupied. Choose a different location.");
                        continue;
                    }
                    boolean duplicateInNotice = false;
                    for (NoticeLineInput line : lines) {
                        if (line.hasNewItemDetails()
                                && line.getLocationLetter().equals(locLetter)
                                && line.getLocationNumber().equals(locNumber)) {
                            duplicateInNotice = true;
                            break;
                        }
                    }
                    if (duplicateInNotice) {
                        System.out.println("  Location " + locLetter + locNumber
                                + " is already used by another line in this notice.");
                        continue;
                    }
                    break;
                }
                input = new NoticeLineInput(manufacturer, modelNumber, noticeQuantity,
                        minLevel, maxLevel, locLetter, locNumber);
            }
            lines.add(input);
            lineNumber++;
        }

        if (lines.isEmpty()) {
            System.out.println("No line items entered. Notice cancelled.");
            return;
        }

        System.out.println("\n--- Review Shipping Notice ---");
        System.out.println("Notice ID: " + snid);
        System.out.println("Shipping Company: " + company);
        System.out.println("Lines:");
        for (NoticeLineInput line : lines) {
            String tag = line.hasNewItemDetails() ? " [NEW PRODUCT]" : "";
            System.out.println("  - " + line.getManufacturerName() + " / "
                    + line.getModelNumber() + " / qty=" + line.getQuantity() + tag);
            if (line.hasNewItemDetails()) {
                System.out.println("      min=" + line.getMinLevel()
                        + ", max=" + line.getMaxLevel()
                        + ", location=" + line.getLocationLetter() + line.getLocationNumber());
            }
        }

        System.out.print("\nSubmit this notice? (y/n): ");
        String confirm = scanner.nextLine().trim();
        if (!confirm.equalsIgnoreCase("y") && !confirm.equalsIgnoreCase("yes")) {
            System.out.println("Notice cancelled. No changes were made.");
            return;
        }

        try {
            ShippingNotice notice = new ShippingNotice(snid, company);
            List<ProcessedLine> processed = inventoryManager.processShippingNotice(notice, lines);

            System.out.println("\nSuccess: Shipping notice " + snid + " recorded.");
            System.out.println("Replenishment quantities updated for:");
            for (ProcessedLine line : processed) {
                String suffix = line.isNewlyCreated()
                        ? " (new stock number assigned)"
                        : "";
                System.out.println("  - " + line.getStockNumber() + " <- "
                        + line.getManufacturerName() + "/" + line.getModelNumber()
                        + " +" + line.getQuantity() + suffix);
            }
        }
        catch (IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
            System.out.println("No changes were committed.");
        }
        catch (RuntimeException e) {
            System.out.println("Unexpected error: " + e.getMessage());
            System.out.println("No changes were committed.");
        }
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
        
        try {
            int quantity = inventoryManager.checkItemQuantity(stockNum);
            System.out.println("Current Quantity for " + stockNum + ": " + quantity);
        }
        catch (IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleFillOrder() {
        System.out.println("\n[Transaction: Fill an Order]");
        // TODO: Prompt for eMART Order Number
        // TODO: Pass data to inventoryManager.fillOrder(...)
        // Note: The inventory manager will handle the automated replenishment logic internally!
    }

    // Note: the difference between promptPositiveInt() and promptNonNegativeInt() is just one allows 0 and one disallows 0
    private Integer promptPositiveInt(String prompt, boolean allowBlankCancel) {
        while (true) {
            System.out.print(prompt);
            String raw = scanner.nextLine().trim();
            if (allowBlankCancel && raw.isEmpty()) {
                return null;
            }
            try {
                int value = Integer.parseInt(raw);
                if (value > 0) {
                    return value;
                }
                System.out.println("  Must be a positive integer.");
            }
            catch (NumberFormatException e) {
                System.out.println("  Not a valid integer.");
            }
        }
    }

    private Integer promptNonNegativeInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String raw = scanner.nextLine().trim();
            try {
                int value = Integer.parseInt(raw);
                if (value >= 0) {
                    return value;
                }
                System.out.println("  Must be a non-negative integer.");
            }
            catch (NumberFormatException e) {
                System.out.println("  Not a valid integer.");
            }
        }
    }

    private String promptNonEmpty(String prompt, boolean allowBlankCancel) {
        while (true) {
            System.out.print(prompt);
            String raw = scanner.nextLine().trim();
            if (raw.isEmpty()) {
                if (allowBlankCancel) {
                    return null;
                }
                System.out.println("  Cannot be empty.");
                continue;
            }
            return raw;
        }
    }

    private String promptLocationLetter(String prompt) {
        while (true) {
            System.out.print(prompt);
            String raw = scanner.nextLine().trim().toUpperCase();
            if (raw.matches("^[A-Z]$")) {
                return raw;
            }
            System.out.println("  Must be a single letter A-Z.");
        }
    }

    private Integer promptLocationNumber(String prompt) {
        while (true) {
            System.out.print(prompt);
            String raw = scanner.nextLine().trim();
            if (raw.matches("^0[0-9]+$")) {
                System.out.println("  Location number cannot have leading zeros");
                continue;
            }
            try {
                int value = Integer.parseInt(raw);
                if (value >= 0) {
                    return value;
                }
                System.out.println("  Must be a positive integer.");
            } catch (NumberFormatException e) {
                System.out.println("  Not a valid integer.");
            }
        }
    }
}
