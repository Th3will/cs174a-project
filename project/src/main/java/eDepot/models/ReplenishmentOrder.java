package eDepot.models;

public class ReplenishmentOrder {
    private int orderId;
    private String manufacturerName;

    public ReplenishmentOrder() {
    }

    public ReplenishmentOrder(int orderId, String manufacturerName) {
        this.orderId = orderId;
        this.manufacturerName = manufacturerName;
    }

    public int getOrderId() {
        return orderId;
    }

    public void setOrderId(int orderId) {
        this.orderId = orderId;
    }

    public String getManufacturerName() {
        return manufacturerName;
    }

    public void setManufacturerName(String manufacturerName) {
        this.manufacturerName = manufacturerName;
    }

    @Override
    public String toString() {
        return "ReplenishmentOrder{" +
                "orderId=" + orderId +
                ", manufacturerName='" + manufacturerName + '\'' +
                '}';
    }
}
