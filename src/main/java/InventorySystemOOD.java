
/**
 * Design an inventory management system that tracks products across multiple warehouses.
 * The system needs to handle adding and removing inventory,
 * transferring stock between locations, and alerting when inventory runs low
 **/

import java.util.*;

/**
 1. warehouse inventory transfer + tracking;
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
 **/


public class InventorySystemOOD {
    public static void main(String[] args) {

    }
}

class InventorySystem {
    private final Map<String, Warehouse> warehouses = new HashMap<>();

    public boolean transferProduct(String productId, int quantity, String source, String destination) {
        if (source == null || destination == null) {
            throw new IllegalArgumentException("source or destination cannot be null");
        }
        if (source.isEmpty() || destination.isEmpty()) {
            throw new IllegalArgumentException("source or destination cannot be empty");
        }
        if (source.equals(destination)) {
            throw new IllegalArgumentException("source and destination cannot be the same");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity cannot be zero or negative");
        }
        //check inventory at source
        //make the transfer, reduce one at source, add one at destination
        String lockAId = (source.compareTo(destination) < 0) ? source : destination;
        String lockBId = (lockAId.equals(source)) ? destination : source;
        Warehouse lockA = warehouses.get(lockAId);
        Warehouse lockB = warehouses.get(lockBId);
        synchronized (lockA) {
            synchronized (lockB) {
                if (warehouses.get(source).remove(productId, quantity)) {
                    warehouses.get(destination).add(productId, quantity);
                    return true;
                }
            }
            return false;
        }

    }


}

/**
 Extension:
 Reservation System:
 customer check avaiability, warehouse confirm it, deduct it from inventory temperoraly, upon confirmation ddeduct permanaly;
 has to be atomic operation. Reservation has a timeout
 **/

class Warehouse {
    private final String warehouseId;
    private final Map<String, Integer> inventory = new HashMap<>();
    private final Map<String, List<AlertConfig>> alertConfigs = new HashMap<>();
    private final Map<String, InventoryReservation> reservations = new HashMap<>();
    private final Map<String, Integer> reserved = new HashMap<>();

    public Warehouse(String warehouseId) {
        this.warehouseId = warehouseId;
    }

    public synchronized void setAlertConfigs(String productId, Alert alert, int inventoryThreshold) {
        if (productId == null || productId.isEmpty()) {
            throw new IllegalArgumentException("productId cannot be null or empty");
        }
        if (alert == null) {
            throw new IllegalArgumentException("alert cannot be null");
        }
        if (inventoryThreshold <= 0) {
            throw new IllegalArgumentException("inventoryThreshold cannot be zero or negative");
        }
        AlertConfig alertConfig = new AlertConfig(inventoryThreshold, alert);
        List<AlertConfig> alertConfigList = alertConfigs.computeIfAbsent(productId, k -> new ArrayList<>());
        alertConfigList.add(alertConfig);
    }

    synchronized public String reserve(String productId, int quantity, long expirationTime) {
        if (productId == null || productId.isEmpty()) {
            throw new IllegalArgumentException("productId cannot be null or empty");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity cannot be zero or negative");
        }
        if (!inventory.containsKey(productId)) {
            throw new IllegalArgumentException("productId not found");
        }
        if (inventory.get(productId) < quantity) {
            throw new RuntimeException("product " + productId + " does not have enough inventory");
        }
        if (checkAvailabilityWithReservation(productId, quantity)) {
            String reservationId = UUID.randomUUID().toString();
            InventoryReservation reservation = new InventoryReservation(reservationId, productId, quantity, System.currentTimeMillis() + expirationTime);
            reservations.put(reservationId, reservation);
            reserved.put(productId, reserved.getOrDefault(productId, 0) + quantity);
            return reservationId;
        }
        return null;


    }

    public boolean confirmReservation(String reservationId) {
        List<AlertToFire> alertsToFire = new ArrayList<>();
        synchronized (this) {
            if (reservationId == null || !reservations.containsKey(reservationId)) {
                throw new IllegalArgumentException("reservation is null or reservationId not found");
            }
            InventoryReservation reservation = reservations.get(reservationId);
            long now = System.currentTimeMillis();
            String productId = reservation.productId();
            int quantity = reservation.quantity();
            long expiredTime = reservation.expiredAt();
            ;
            if (now > expiredTime) {
                System.out.println("Reservation expired");
                evictReservation(reservation);
                return false;
            }
            evictReservation(reservation);
            int curInventory = inventory.get(productId);
            int newInventory = inventory.get(productId) - quantity;
            inventory.put(productId, newInventory);
            alertsToFire = getAlertsToFire(productId, curInventory, newInventory);

        }
        triggerAlert(alertsToFire);
        return true;
    }

    synchronized public void evictReservation(InventoryReservation reservation) {
        if (reservation == null) {
            throw new IllegalArgumentException("reservation cannot be null");
        }
        String reservationId = reservation.ReservationId();
        String productId = reservation.productId();
        int quantity = reservation.quantity();
        if (!reservations.containsKey(reservation.ReservationId())) {
            throw new IllegalArgumentException("reservationId not found");
        }

        reserved.put(productId, reserved.get(productId) - quantity);
        reservations.remove(reservationId);

    }

    boolean checkAvailabilityWithReservation(String productId, int quantity) {
        if (productId == null || productId.isEmpty()) {
            throw new IllegalArgumentException("productId cannot be null or empty");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity cannot be zero or negative");
        }
        if (!inventory.containsKey(productId)) {
            throw new IllegalArgumentException("productId not found");
        }
        int totalQuantity = inventory.get(productId);
        int reservedQuantity = reserved.getOrDefault(productId, 0);
        int availableQuantity = totalQuantity - reservedQuantity;
        if (availableQuantity < quantity) {
            System.out.println("there isn't enough inventory for product " + productId);
            return false;
        }
        return true;


    }

    public synchronized void cleanupExpiredReservations() {
        long now = System.currentTimeMillis();

        List<InventoryReservation> expired = new ArrayList<>();

        for (InventoryReservation reservation : reservations.values()) {
            if (now > reservation.expiredAt()) {
                expired.add(reservation);
            }
        }

        for (InventoryReservation reservation : expired) {
            evictReservation(reservation);
        }
    }

    public boolean remove(String productId, int quantity) {
        List<AlertToFire> alertsToFire = new ArrayList<>();
        synchronized (this) {
            if (productId == null || productId.isEmpty()) {
                throw new IllegalArgumentException("product cannot be null or empty");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be greater than 0");
            }
            if (!inventory.containsKey(productId)) {
                throw new RuntimeException("product " + productId + " does not exist");
            }
            int available = inventory.get(productId) - reserved.getOrDefault(productId, 0);

            if (available < quantity) {
                return false;
            }
            int curInventory = inventory.get(productId);
            int newInventory = inventory.get(productId) - quantity;
            inventory.put(productId, newInventory);
            alertsToFire = getAlertsToFire(productId, curInventory, newInventory);
        }

        triggerAlert(alertsToFire);
        return true;
    }

    synchronized public boolean add(String productId, int quantity) {
        if (productId == null || productId.isEmpty()) {
            throw new IllegalArgumentException("product cannot be null or empty");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than 0");
        }
        inventory.put(productId, inventory.get(productId) + quantity);
        return true;
    }

    public void triggerAlert(List<AlertToFire> alertToFire) {
        for (AlertToFire alertFiring : alertToFire) {
            int alertThreshold = alertFiring.getInventoryThreshold();
            Alert alert = alertFiring.getAlert();
            int inventoryNow = alertFiring.getInventory();
            String productId = alertFiring.getProductId();
            alert.onLowStock(this, productId, inventoryNow, alertThreshold);

        }
    }

    public List<AlertToFire> getAlertsToFire(String productId, int prevInv, int newInv) {
        if (productId == null || productId.isEmpty()) {
            throw new IllegalArgumentException("product cannot be null or empty");
        }
        if (!inventory.containsKey(productId)) {
            throw new RuntimeException("product " + productId + " does not exist");
        }
        List<AlertToFire> result = new ArrayList<>();
        for (AlertConfig alertConfig : alertConfigs.get(productId)) {
            int alertThreshold = alertConfig.getInventoryThreshold();
            Alert alert = alertConfig.getAlert();
            if (prevInv >= alertThreshold && newInv < alertThreshold) {
                result.add(new AlertToFire(alert, alertThreshold, newInv, productId));
            }
        }
        return result;
    }

}

class AlertConfig {
    private final int inventoryThreshold;
    private final Alert alert;

    public AlertConfig(int inventoryThreshold, Alert alert) {
        this.inventoryThreshold = inventoryThreshold;
        this.alert = alert;
    }

    public Alert getAlert() {
        return alert;
    }

    public int getInventoryThreshold() {
        return inventoryThreshold;
    }
}

class AlertToFire {
    private final Alert alert;
    private final int inventoryThreshold;
    private final int inventory;
    private final String productId;

    public AlertToFire(Alert alert, int inventoryThreshold, int inventory, String productId) {
        this.alert = alert;
        this.inventoryThreshold = inventoryThreshold;
        this.inventory = inventory;
        this.productId = productId;
    }

    public Alert getAlert() {
        return alert;
    }

    public int getInventoryThreshold() {
        return inventoryThreshold;
    }

    public int getInventory() {
        return inventory;
    }

    public String getProductId() {
        return productId;
    }
}

interface Alert {
    public void onLowStock(Warehouse warehouse, String productId, int quantity, int threshold);
}

record InventoryReservation(
        String ReservationId,
        String productId,
        int quantity,
        long expiredAt
) {
}



