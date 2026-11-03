import java.time.Duration;
import java.time.Instant;
import java.util.*;"Design a parking lot system where different types of vehicles " +
        "can park, and the system manages spot assignment and calculates fees upon exit."



Requirement:
1. match available spot;
    3 different of vehicle
    reject entry if full
2. pricing system:
        hourly rate, round up to nearest hour,
        pay when leave;
3. ticket system:
    issue ticket;
    reject invalid ticket;

Entity:
    1. Parking System:
        ParkingLot;
        ParkingSpot;
        Vehicle;
    2. Pricing System:
    3. Ticketing:
        Ticket;

Entity:
    1. ParkingSystem:
        -List<ParkingSpot>
        -List<Ticket>
        boolean park(vehicle);
        parkingSpot findAvailable(vehicleSize);
        ticket issueTicket(vehicle, parkingSpot);
        boolean exit(Ticket)
        double calculateFee(Ticket);
    2. parkingSpot:
        -size
        -ID

    3. Ticket:
        -id
        -issuedTime
        -parkingSpotID
    4. Vehicle:
        -id
        -size





public class ParkingLot {
}

class ParkingSystem{
        private final List<ParkingSpot> parkingSpots;
        private final Set<String> occupiedSpotId = new HashSet<>();
        private final Map<String, Ticket> idToTicket = new HashMap<>();
        private long hourlyRateCents;

        public ParkingSystem(List<ParkingSpot> parkingSpots) {
            this.parkingSpots = parkingSpots;
        }
        boolean park(Vehicle vehicle){
            //find availability
            ParkingSpot parkingSpot = findAvailable(vehicle);
            if (parkingSpot == null) {
                throw new RuntimeException("there is no available parking spot");
            }
            //issue ticket
            String ticketId = UUID.randomUUID().toString();
            Ticket issuedTicket = issueTicket(ticketId, parkingSpot, vehicle);
            if (issuedTicket == null) {
                throw new RuntimeException("exception encountered when issuing ticket");
            }
            //update states->park the car
            occupiedSpotId.add(parkingSpot.getId());
            idToTicket.put(ticketId, issuedTicket);
            return true;

        }
        ParkingSpot findAvailable(Vehicle vehicle){
            for(ParkingSpot spot : parkingSpots){
                if (spot.getSize() == vehicle.size && !occupiedSpotId.contains(spot.getId())){
                    return spot;
                }
            }
            return null;
        }
        Ticket issueTicket(String ticketId, ParkingSpot parkingSpot, Vehicle vehicle){
            Instant now = Instant.now();
            return new Ticket(ticketId,parkingSpot.getId(), now, vehicle.getMake());
        }

        public boolean exit(String ticketId){
            //scan ticket
            //validate ticket
            if (!idToTicket.containsKey(ticketId)){
                throw new RuntimeException("no such ticket");
            }
            Ticket ticket = idToTicket.get(ticketId);
            //calculate fee
            //free the space
            //update source of truth
        }
        public long calculatefee(Ticket ticket){
            Duration duration = Duration.between(
                    Instant.now(),
                    ticket.issuedTime
            );

            long durationMillis = duration.toMillis();
            long hour = (durationMillis + 3_600_000 - 1)/ 3_600_000 ;
            return hour * hourlyRateCents;
    }
}

class ParkingSpot{
    private final VehicleSize size;
    private final String id;

    public ParkingSpot(VehicleSize size, String id) {
        this.size = size;
        this.id = id;
    }
    public VehicleSize getSize() {
        return size;
    }
    public String getId() {
        return id;
    }
}

class Ticket{
        private final String id;
        private final String spotId;
        private final Instant issuedTime;
        private final String make;

        public Ticket(String id, String spotId, Instant issuedTime, String make){
            this.id = id;
            this.spotId = spotId;
            this.issuedTime = issuedTime;
            this.make = make;
        }
        public String getSpotId() {
            return spotId;
        }
        public Instant getIssuedTime() {
            return issuedTime;
        }
        public String getMake() {
            return make;
        }

}

class Vehicle{
    private final VehicleSize size;
    private final String make;

    public Vehicle(VehicleSize size, String make){
        this.size = size;
        this.make = make;
    }
    public VehicleSize getSize() {
        return size;
    }
    public String getMake() {
        return make;
    }
}
enum VehicleSize{
    MOTORCYCLE,
    CAR,
    LARGE
}
