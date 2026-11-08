
/**
Design an inventory management system that tracks products across multiple warehouses.
The system needs to handle adding and removing inventory,
transferring stock between locations, and alerting when inventory runs low
 **/

import java.net.IDN;1. warehouse inventory transfer + tracking;
    race condition.
2. inventory alert, per warehouse per product;

Entity:
 1.InventorySystem;
    - Map<String, Warehouse>;
    transferProduct(Product, quantity, source, destination);
    checkInventory(Product, warehouse);
    triggerAlert(warehouse)

 2. Warehouse;
    -Map<Product, Integer> inventory;
    -Map<Product, Integer> threshHold;


    boolean belowThreshold();
    void setLowStockAlert(){

    }


 3. Product;
    -ID;
    -name;
public class InventorySystemOOD {
}

class Warehouse{


    public void setLowStockAlert(String productId, ){
        //check which of
    }
}