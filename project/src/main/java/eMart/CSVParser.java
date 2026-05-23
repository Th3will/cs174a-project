package eMart;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class CSVParser {

    public static String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(currentField.toString().trim());
                currentField.setLength(0);
            } else {
                currentField.append(c);
            }
        }
        result.add(currentField.toString().trim());
        return result.toArray(new String[0]);
    }

    public static AttributeParsed parseAttribute(String attributeStr) {
        int colonIndex = attributeStr.indexOf(':');
        if (colonIndex == -1) {
            return null;
        }
        String name = attributeStr.substring(0, colonIndex).trim();
        String valPart = attributeStr.substring(colonIndex + 1).trim();

        AttributeParsed parsed = new AttributeParsed();
        if (name.length() > 20) {
            name = name.substring(0, 20);
        }
        parsed.name = name;

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^([+-]?(?:\\d+\\.\\d*|\\.\\d+|\\d+))(.*)$");
        java.util.regex.Matcher matcher = pattern.matcher(valPart);
        if (matcher.matches()) {
            String numStr = matcher.group(1);
            String unitStr = matcher.group(2).trim();
            try {
                parsed.value = Double.parseDouble(numStr);
            } catch (NumberFormatException e) {
                parsed.value = null;
            }
            parsed.unit = unitStr.isEmpty() ? null : unitStr;
        } else {
            parsed.value = null;
            parsed.unit = valPart.isEmpty() ? null : valPart;
        }

        if (parsed.unit != null && parsed.unit.length() > 10) {
            parsed.unit = parsed.unit.substring(0, 10);
        }

        return parsed;
    }

    public static List<ParsedItem> parseCSV(String filePath) {
        List<ParsedItem> items = new ArrayList<>();
        ParsedItem currentItem = null;

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean isHeader = true;
            while ((line = br.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }
                // Using parseCsvLine to remain consistent and robust
                String[] tokens = parseCsvLine(line);
                if (tokens.length < 12) {
                    continue;
                }

                String stockNum = tokens[0].trim();
                if (!stockNum.isEmpty()) {
                    currentItem = new ParsedItem();
                    currentItem.stockNum = stockNum;
                    currentItem.category = tokens[1].trim();
                    currentItem.manufacturer = tokens[2].trim();
                    currentItem.modelNum = tokens[3].trim();
                    
                    String desc = tokens[4].trim();
                    if (!desc.isEmpty()) {
                        currentItem.attributes.add(desc);
                    }
                    
                    currentItem.warranty = tokens[5].trim().isEmpty() ? 0 : Integer.parseInt(tokens[5].trim());
                    
                    String comp = tokens[6].trim();
                    if (!comp.isEmpty() && !comp.equalsIgnoreCase("None")) {
                        currentItem.compatibilities.add(comp);
                    }
                    
                    currentItem.price = tokens[7].trim().isEmpty() ? 0.0 : Double.parseDouble(tokens[7].trim());
                    currentItem.minLevel = tokens[8].trim().isEmpty() ? 0 : Integer.parseInt(tokens[8].trim());
                    currentItem.quantity = tokens[9].trim().isEmpty() ? 0 : Integer.parseInt(tokens[9].trim());
                    currentItem.maxLevel = tokens[10].trim().isEmpty() ? 0 : Integer.parseInt(tokens[10].trim());
                    currentItem.location = tokens[11].trim();
                    
                    items.add(currentItem);
                } else if (currentItem != null) {
                    String desc = tokens[4].trim();
                    if (!desc.isEmpty()) {
                        currentItem.attributes.add(desc);
                    }
                    String comp = tokens[6].trim();
                    if (!comp.isEmpty() && !comp.equalsIgnoreCase("None")) {
                        currentItem.compatibilities.add(comp);
                    }
                }
            }
        } catch (Exception e) {
            Utility.log("Error parsing CSV: " + e.getMessage());
            if (Utility.verbose) {
                e.printStackTrace();
            }
        }
        return items;
    }

    public static List<CustomerRow> parseCustomers(String filePath) throws Exception {
        List<CustomerRow> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean isHeader = true;
            while ((line = br.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }
                String[] tokens = parseCsvLine(line);
                if (tokens.length < 6) {
                    continue;
                }
                CustomerRow row = new CustomerRow();
                row.cid = tokens[0];
                row.password = tokens[1];
                row.name = tokens[2];
                row.email = tokens[3];
                row.address = tokens[4];
                row.status = tokens[5];
                list.add(row);
            }
        }
        return list;
    }

    public static List<ManagerRow> parseManagers(String filePath) throws Exception {
        List<ManagerRow> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean isHeader = true;
            while ((line = br.readLine()) != null) {
                if (isHeader) {
                    isHeader = false;
                    continue;
                }
                String[] tokens = parseCsvLine(line);
                if (tokens.length < 5) {
                    continue;
                }
                ManagerRow row = new ManagerRow();
                row.eid = tokens[0];
                row.password = tokens[1];
                row.name = tokens[2];
                row.email = tokens[3];
                row.address = tokens[4];
                list.add(row);
            }
        }
        return list;
    }
}

class CustomerRow {
    public String cid;
    public String password;
    public String name;
    public String email;
    public String address;
    public String status;
}

class ManagerRow {
    public String eid;
    public String password;
    public String name;
    public String email;
    public String address;
}
