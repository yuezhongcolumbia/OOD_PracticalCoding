import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class Elevator {

    public static void main(String[] args) {

        ElevatorSystem system = new ElevatorSystem();

        system.assignHallRequest(
                5,
                RequestType.HALLREQUEST_UP
        );

        for (int i = 0; i < 8; i++) {
            System.out.printf("Step %d%n", i);
            system.printStatus();
            system.step();
        }

        system.printStatus();
    }
}


class ElevatorSystem {
    private final List<ElevatorCar> elevatorCars;

    public ElevatorSystem() {
        elevatorCars = new ArrayList<ElevatorCar>();
        elevatorCars.add(new ElevatorCar());
        elevatorCars.add(new ElevatorCar());
        elevatorCars.add(new ElevatorCar());
    }

    public void step() {
        for (ElevatorCar elevatorCar : elevatorCars) {
            elevatorCar.step();
        }
    }

    public void printStatus() {
        for (int i = 0; i < elevatorCars.size(); i++) {
            System.out.printf("Elevator %d: ", i);
            elevatorCars.get(i).printStatus();
        }
    }

    public boolean assignHallRequest(int floor, RequestType type) {

        if (floor < 0 || floor > 9) {
            throw new RuntimeException("Invalid request");
        }
        if (type == RequestType.ELEVATOR_REQUEST) {
            throw new RuntimeException("cannot assign elevator request");
        }
        Request request = new Request(type, floor);
        ElevatorCar bestCar = findBestCar(request);

        if (bestCar == null) {
            return false;
        }
        return bestCar.addRequest(request);

    }

    public ElevatorCar findBestCar(Request request) {
        ElevatorCar bestCar = null;
        Direction direction = (request.type() == RequestType.HALLREQUEST_UP) ? Direction.UP : Direction.DOWN;
        int floor = request.floor();
        int minDist = Integer.MAX_VALUE;
        //pick up along the way
        for (ElevatorCar car : elevatorCars) {
            if (car.direction != direction) {
                continue;
            }
            if ((car.direction == Direction.UP && car.floor > floor)
                    || (car.direction == Direction.DOWN && car.floor < floor)) {
                continue;
            }
            if (!car.hasRequestAtOrBeyond(direction, floor)) {
                continue;
            }
            int distance = Math.abs(request.floor() - car.floor);
            if (distance < minDist) {
                bestCar = car;
                minDist = distance;
            }
        }
        if (bestCar != null) {
            return bestCar;
        }
        //pick up if idle
        for (ElevatorCar car : elevatorCars) {
            if (car.direction != Direction.IDLE) {
                continue;
            }
            int distance = Math.abs(request.floor() - car.floor);
            if (distance < minDist) {
                bestCar = car;
                minDist = distance;
            }
        }
        if (bestCar != null) {
            return bestCar;
        }

        //find the nearest one to pickup
        for (ElevatorCar car : elevatorCars) {
            int distance = Math.abs(request.floor() - car.floor);
            if (distance < minDist) {
                minDist = Math.abs(request.floor() - car.floor);
                bestCar = car;
            }
        }
        return bestCar;
    }

}

class ElevatorCar {
    int floor;
    private final Set<Request> requests = new HashSet<>();
    Direction direction;

    public ElevatorCar() {
        floor = 0;
        direction = Direction.IDLE;
    }

    public boolean addRequest(Request request) {
        if (request.floor() < 0 || request.floor() > 9) {
            return false;
        }
        if (request.floor() == floor) {
            return true;
        }
        if (requests.contains(request)) {
            return false;
        }
        return requests.add(request);
    }

    public boolean hasRequestAtOrBeyond(Direction direction, int destination) {
        for (Request request : this.requests) {
            if (direction == Direction.UP) {
                if (request.type() == RequestType.HALLREQUEST_UP && request.floor() >= destination) {
                    return true;
                } else if (request.type() == RequestType.ELEVATOR_REQUEST && request.floor() >= destination) {
                    return true;
                }
            }
            if (direction == Direction.DOWN) {
                if (request.type() == RequestType.HALLREQUEST_DOWN && request.floor() <= destination) {
                    return true;
                } else if (request.type() == RequestType.ELEVATOR_REQUEST && request.floor() <= destination) {
                    return true;
                }
            }
        }
        return false;
    }

    //elevator car simulation tick by tick
    public void step() {
        if (requests.isEmpty()) {
            direction = Direction.IDLE;
            return;
        }

        //find initial direction
        if (direction == Direction.IDLE) {
            int minDist = Integer.MAX_VALUE;
            Request nearestTask = null;
            for (Request request : requests) {
                int distance = Math.abs(request.floor() - floor);
                if (distance < minDist) {
                    minDist = distance;
                    nearestTask = request;
                }
            }
            direction = (nearestTask.floor() < floor) ? Direction.DOWN : Direction.UP;
        }
        //drop off
        RequestType pickupType = (direction == Direction.UP) ? RequestType.HALLREQUEST_UP : RequestType.HALLREQUEST_DOWN;
        Request pickupRequest = new Request(pickupType, floor);
        Request elevatorRequest = new Request(RequestType.ELEVATOR_REQUEST, floor);
        if (requests.contains(pickupRequest) || requests.contains(elevatorRequest)) {
            requests.remove(pickupRequest);
            requests.remove(elevatorRequest);
            if (requests.isEmpty()) {
                direction = Direction.IDLE;

            }
            return;
        }
        //turn around if needed
        boolean requestAhead = false;
        for (Request request : requests) {
            if (direction == Direction.UP && request.floor() > floor) {
                requestAhead = true;
                break;
            } else if (direction == Direction.DOWN && request.floor() < floor) {
                requestAhead = true;
                break;
            }
        }
        if (!requestAhead) {
            direction = (direction == Direction.UP) ? Direction.DOWN : Direction.UP;
            return;
        }
        //update current floor
        if (direction == Direction.UP) {
            floor++;
        }
        if (direction == Direction.DOWN) {
            floor--;
        }
    }

    public void printStatus() {
        System.out.printf(
                "Floor=%d, Direction=%s, Requests=%s%n",
                floor, direction, requests
        );
    }

}

record Request(RequestType type, int floor) {
}

enum RequestType {
    HALLREQUEST_UP,
    HALLREQUEST_DOWN,
    ELEVATOR_REQUEST,
}

enum Direction {
    UP,
    DOWN,
    IDLE,
}