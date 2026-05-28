package eDepot.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

import eDepot.dao.NoticeLineDAO.LineDetail;
import eDepot.models.ShippingNotice;
import eDepot.services.InventoryManager;
import eDepot.services.InventoryManager.AppliedShipmentLine;
import eDepot.services.InventoryManager.FillOrderResult;
import eDepot.services.InventoryManager.FilledOrderLine;
import eDepot.services.InventoryManager.GeneratedReplenishment;
import eDepot.services.InventoryManager.GeneratedReplenishmentLine;
import eDepot.services.InventoryManager.NoticeLineInput;
import eDepot.services.InventoryManager.OrderLineInput;
import eDepot.services.InventoryManager.ProcessedLine;
import eDepot.services.InventoryManager.ShipmentPreview;

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

    // TRANSACTION (1): Receive shipping notice from manufacturer
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

    // TRANSACTION (2): Receive a physical shipment from a prior shipping notice
    private void handleReceiveShipment() {
        System.out.println("\n[Transaction: Receive Shipment]");
        ShipmentPreview preview = null;
        while (true) {
            Integer snid = promptNonNegativeInt("Enter Shipping Notice ID: ", true);
            if (snid == null) {
                System.out.println("Cancelled.");
                return;
            }
            try {
                preview = inventoryManager.getShipmentPreview(snid);
            }
            catch (RuntimeException e) {
                System.out.println("Error loading shipping notice: " + e.getMessage());
                return;
            }

            if (preview == null) {
                System.out.println("  No shipping notice exists for ID " + snid + ". Enter a different ID.");
                continue;
            }
            if (preview.isAlreadyFulfilled()) {
                System.out.println("  Shipping notice " + snid
                        + " has already been fulfilled - cannot process the same shipment twice.");
                System.out.println("  Enter a different ID, or leave blank to cancel.");
                continue;
            }
            if (preview.getLines().isEmpty()) {
                System.out.println("  Shipping notice " + snid + " has no line items - nothing to receive.");
                return;
            }
            break;
        }

        System.out.println("\n--- Review Incoming Shipment ---");
        System.out.println("Notice ID: " + preview.getShippingNoticeId());
        System.out.println("Shipping Company: " + preview.getShippingCompanyName());
        System.out.println("Lines to receive:");
        for (LineDetail line : preview.getLines()) {
            System.out.println("  - " + line.getStockNumber()
                    + "  " + line.getManufacturerName() + "/" + line.getModelNumber()
                    + "  qty=" + line.getNoticeQuantity());
        }

        System.out.print("\nConfirm physical shipment matches notice and apply to inventory? (y/n): ");
        String confirm = scanner.nextLine().trim();
        if (!confirm.equalsIgnoreCase("y") && !confirm.equalsIgnoreCase("yes")) {
            System.out.println("Shipment receipt cancelled. No changes were made.");
            return;
        }

        try {
            List<AppliedShipmentLine> applied = inventoryManager.processShipmentArrival(preview.getShippingNoticeId());

            System.out.println("\nSuccess: Shipment for notice " + preview.getShippingNoticeId()
                    + " has been received and the notice has been marked fulfilled.");
            System.out.println("Inventory updates:");
            for (AppliedShipmentLine line : applied) {
                System.out.println("  - " + line.getStockNumber()
                        + "  " + line.getManufacturerName() + "/" + line.getModelNumber()
                        + "  +" + line.getQuantityReceived()
                        + "  (on-hand now " + line.getNewQuantityOnHand() + ")");
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

    // TRANSACTION (3): Search up an item by stock # and get its quantity
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

    // TRANSACTION (4): Input a fill order by hand; need order num and each item's (stockNum, orderQuantity)
    private void handleFillOrder() {
        System.out.println("\n[Transaction: Fill an Order]");
        System.out.println("Enter the order details from the eMart order sheet.");

        Integer orderNum = promptNonNegativeInt("Enter Order Number (or blank to cancel): ", true);
        if (orderNum == null) {
            System.out.println("Cancelled.");
            return;
        }

        List<OrderLineInput> lines = new ArrayList<>();
        Set<String> seenStockNums = new HashSet<>();
        System.out.println("\nEnter order line items. Type 'done' as the stock number to finish.");

        int lineNumber = 1;
        while (true) {
            System.out.println("\n-- Line " + lineNumber + " --");
            String stockNum = promptStockNumberOrDone("  Stock Number (XXnnnnn, or 'done' to finish): ");
            if (stockNum == null) {
                break;
            }
            if (!seenStockNums.add(stockNum)) {
                System.out.println("  Stock number " + stockNum
                        + " is already in this order. Combine the quantities into a single line.");
                continue;
            }
            Integer orderQuantity = promptPositiveInt("  Order Quantity (positive integer): ", false);
            lines.add(new OrderLineInput(stockNum, orderQuantity));
            lineNumber++;
        }

        if (lines.isEmpty()) {
            System.out.println("No line items entered. Order cancelled.");
            return;
        }

        System.out.println("\n--- Review Order ---");
        System.out.println("Order Number: " + orderNum);
        System.out.println("Lines:");
        for (OrderLineInput line : lines) {
            System.out.println("  - " + line.getStockNumber() + "  qty=" + line.getQuantity());
        }

        System.out.print("\nSubmit this order? (y/n): ");
        String confirm = scanner.nextLine().trim();
        if (!confirm.equalsIgnoreCase("y") && !confirm.equalsIgnoreCase("yes")) {
            System.out.println("Order cancelled. No changes were made.");
            return;
        }

        try {
            FillOrderResult result = inventoryManager.fillOrder(orderNum, lines);

            System.out.println("\nSuccess: Order " + orderNum + " filled.");
            System.out.println("Inventory updates:");
            for (FilledOrderLine line : result.getFilled()) {
                System.out.println("  - " + line.getStockNumber()
                        + "  " + line.getManufacturerName() + "/" + line.getModelNumber()
                        + "  -" + line.getQuantitySold()
                        + "  (on-hand now " + line.getNewQuantityOnHand() + ")");
            }

            if (result.getReplenishments().isEmpty()) {
                System.out.println("\nNo replenishment orders triggered.");
            } 
            else {
                System.out.println("\nGenerated replenishment orders:");
                for (GeneratedReplenishment rep : result.getReplenishments()) {
                    System.out.println("  Order " + rep.getOrderId() + " to " + rep.getManufacturerName() + ":");
                    for (GeneratedReplenishmentLine line : rep.getLines()) {
                        System.out.println("    - " + line.getStockNumber()
                                + " (" + line.getModelNumber() + ")"
                                + "  +" + line.getReplenishmentQuantity()
                                + "  (in-flight total " + line.getNewReplenishmentOnHand() + ")");
                    }
                }
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

    // -- HELPER FUNCTIONS ALL BELOW THIS LINE -- 
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

    private Integer promptNonNegativeInt(String prompt, boolean allowBlankCancel) {
        while (true) {
            System.out.print(prompt);
            String raw = scanner.nextLine().trim();
            if (allowBlankCancel && raw.isEmpty()) {
                return null;
            }
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

    /*
     * Prompt for a stock number in XXnnnnn form, or 'done' to end the loop.
     * Uppercases the input before validating so the operator can type either
     * case. Returns null iff the operator typed 'done' (case-insensitive).
     */
    private String promptStockNumberOrDone(String prompt) {
        while (true) {
            System.out.print(prompt);
            String raw = scanner.nextLine().trim();
            if (raw.equalsIgnoreCase("done")) {
                return null;
            }
            if (raw.matches("^[A-Z]{2}[0-9]{5}$")) {
                return raw;
            }
            System.out.println("  Not a valid stock number. Expected XXnnnnn (2 letters + 5 digits), "
                    + "or 'done' to finish.");
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
