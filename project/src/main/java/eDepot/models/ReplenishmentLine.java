package eDepot.models;

public class ReplenishmentLine {
    private int orderId;
    private String stockNumber;
    private int replenishmentQuantity;

    public ReplenishmentLine() {
    }

    public ReplenishmentLine(int orderId, String stockNumber, int replenishmentQuantity) {
        this.orderId = orderId;
        this.stockNumber = stockNumber;
        this.replenishmentQuantity = replenishmentQuantity;
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public String getStockNumber() {
        return stockNumber;
    }

    public void setStockNumber(String stockNumber) {
        this.stockNumber = stockNumber;
    }

    public int getReplenishmentQuantity() {
        return replenishmentQuantity;
    }

    public void setReplenishmentQuantity(int replenishmentQuantity) {
        this.replenishmentQuantity = replenishmentQuantity;
    }

    @Override
    public String toString() {
        return "ReplenishmentLine{" +
                "orderId=" + orderId +
                ", stockNumber='" + stockNumber + '\'' +
                ", replenishmentQuantity=" + replenishmentQuantity +
                '}';
    }
}
