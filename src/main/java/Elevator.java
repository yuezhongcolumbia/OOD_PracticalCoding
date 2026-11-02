import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;"Design an elevator control system for a building. The system should handle multiple elevators, " +
        "floor requests, and move elevators efficiently to service requests


Requirements;
1. System manages 3 elevators serving 10 floors (0-9)
2. Users can request an elevator from any floor (hall call). System decides which elevator to dispatch;
3. Once inside, users can select one or more destination floors
4. Simulation runs in discrete time steps (e.g., a `step()` or `tick()` call advances time);
5. Elevator stops come in two types;
    - Hall calls: Request from a floor with direction (UP or DOWN)
    - Destination: Request from inside elevator (no direction specified)
6. System handles multiple concurrent pickup requests across floors;


Entity ;
core action: user send request to elevator, elevator assgign cars, user press button, button has floors;
on it, car stops
1. Elevator;
2. Elevator System;
3. HallRequest;
4. CarRequest;

Entity Design;
1. Elevator System;
    state;
        1. List<Elevator> Elevators;
    action;
        1. assignHallRequest
        2. how does system know a carRequest made inside a car? I think the logic is hallRequest is;
            passed to elevator car, it pickups when it can, if it is available(no carRequest), go there
            getBestCar()
        3.
2. Elevator:
    state;
        1. floor;
        2. List<CarRequest>, elevator really cares about carRequest, hallRequest are for system to assign;
        3. List<HallRequest>
        4. destination
    Action:
        1. initiateCarRequest;
        2. two type of stop, one is to stop somewhere to fulfill HallRequest, one is to fulfill carRequest;
            first one is assigned by the system, the other one is carried out inside elevator;
            but the system has to know, so lets say stop. basically, a carRequest made inside a car is;
            prioritized, it only picks up when it can;
            stop()
        3. isAvailable()；




public class Elevator {
}

class ElevatorSystem{
    private final List<ElevatorCar> elevatorCars;

    public ElevatorSystem(){
        elevatorCars = new ArrayList<ElevatorCar>();
        elevatorCars.add(new ElevatorCar());
        elevatorCars.add(new ElevatorCar());
        elevatorCars.add(new ElevatorCar());
    }

    public boolean assignHallRequest(int floor, RequestType type){

        if(floor < 0 || floor > 9){
            throw new RuntimeException("Invalid request");
        }
        if(type == RequestType.ELEVATOR_REQUEST){
            throw new RuntimeException("cannot assign elevator request");
        }
        Request request = new Request(type, floor);
        ElevatorCar bestCar = findBestCar(request);
        bestCar.requests.add(request);
        if(bestCar != null){
            return false;
        }
        return true;
    }

    public ElevatorCar findBestCar(Request request){
        ElevatorCar bestCar = null;
        Direction direction = (request.type == RequestType.HALLREQUEST_UP) ? Direction.UP : Direction.DOWN;
        int floor = request.floor;
        int minDist = Integer.MAX_VALUE;
        //pick up along the way
        for (ElevatorCar car : elevatorCars) {
            if (car.direction != direction) {
                continue;
            }
            if ((car.direction == Direction.UP && car.floor > floor)
            ||(car.direction == Direction.DOWN && car.floor < floor)) {
                continue;
            }
            if (!car.hasRequestAtOrBeyond(direction, floor)){
                continue;
            }
            int distance = Math.abs(request.floor - car.floor);
            if (distance < minDist) {
                bestCar = car;
                minDist = distance;
            }
        }
        if (bestCar != null){
            return bestCar;
        }
        //pick up if idle
        for(ElevatorCar car : elevatorCars){
            if (car.direction != Direction.IDLE){
                continue;
            }
            int distance = Math.abs(request.floor - car.floor);
            if (distance < minDist) {
                bestCar = car;
                minDist = distance;
            }
        }
        if (bestCar != null){
            return bestCar;
        }

        //find the nearest one to pickup
        for(ElevatorCar car : elevatorCars){
            int distance = Math.abs(request.floor - car.floor);
            if (distance < minDist) {
                minDist = Math.abs(request.floor - car.floor);
                bestCar = car;
            }
        }
        return bestCar;
    }

}

class ElevatorCar{
    private int floor;
    private final Set<Request> requests = new HashSet<>();
    private Direction direction;

    public ElevatorCar(){
        floor = 0;
        direction = Direction.IDLE;
    }

    public boolean hasRequestAtOrBeyond(Direction direction, int destination){
        for(Request request : this.requests){
            if (direction == Direction.UP){
                if (request.type == RequestType.HALLREQUEST_UP && request.floor >= destination){
                    return true;
                } else if (request.type == RequestType.ELEVATOR_REQUEST && request.floor >= destination){
                    return true;
                }
            }
            if (direction == Direction.DOWN){
                if (request.type == RequestType.HALLREQUEST_DOWN && request.floor <= destination){
                    return true;
                } else if (request.type == RequestType.ELEVATOR_REQUEST && request.floor <= destination){
                    return true;
                }
            }
        }
        return false;
    }

}

class Request{
    private final RequestType type;
    private int floor;

    public Request(RequestType type, int floor){
        this.type = type;
        this.floor = floor;
    }
}

enum RequestType{
   HALLREQUEST_UP,
   HALLREQUEST_DOWN,
   ELEVATOR_REQUEST,
}

enum Direction{
    UP,
    DOWN,
    IDLE,
}