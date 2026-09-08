package com.meridian.event.application.port.inbound;

import com.meridian.event.domain.model.Order;

public class OrderLineInput {
    private String sku;
    private int quantity;
    private double unitPrice;

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }
}
