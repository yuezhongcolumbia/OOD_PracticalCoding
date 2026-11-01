//import java.nio.file.attribute.UserDefinedFileAttributeView;"Design a locker system like Amazon Locker where delivery " +
//        "drivers can deposit packages and customers can pick them up using a code."
//
//Requirement:
//1. when you order, you should get a drop location
//2. when the item is deposited, you get a code, which you can use to pick up the itme
//3. itme size has to fit lockbox size, check availability, reject if non matches
//4.Assume the package is already at the locker location.
//5.code is generated
//6.1:1 storage and 1:1 code matching
//7. code has expiration
//
//Entity:
//1. lockbox(storage, return deposit location, retrival, generate code)
//2. compartment(sizing, occupied to not, item inside)
//3. item(size, id)
//
//Entity Design:
//lockbox:
//    state: compartment[], Map<String, AccessCode>
//    action:
//    String deposit(item);//scan item, find available compartment, generate accesscode, attach it to compartments
//    compartment findAvailableCompartment();
//    AccessCode generateAccessCode();
//    item pickup(String, compartment),//validate code(if TTL), remove it from map, empty the compartment
//
//
//compartment:
//    state: item, size,
//    action:
//        1. boolean deposit(item);
//        2. item retrieve()
//        3. boolean isAvailable()
//
//Accesscode:
//    state: code, compartment, TTL
//    action;
//        1. boolean isExpired()


import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class AmazonLocker {
    public static void main(String[] args) {
        Compartment[] compartments = new Compartment[9];
        int idx = 0;
        for(int i = 0; i < 3; i++){
            Compartment small = new Compartment(Size.SMALL);
            compartments[idx++] = small;
            Compartment medium = new Compartment(Size.MEDIUM);
            compartments[idx++] = medium;
            Compartment large = new Compartment(Size.LARGE);
            compartments[idx++] = large;
        }
        Lockbox lockbox = new Lockbox(compartments);
        Item smalla = new Item(Size.SMALL);
        Item medium = new Item(Size.MEDIUM);
        Item large = new Item(Size.LARGE);
        Item smallb = new Item(Size.SMALL);
        Item smallc = new Item(Size.SMALL);
        Item smalld = new Item(Size.SMALL);
        lockbox.deposit(smalla);
        lockbox.deposit(medium);
        lockbox.deposit(large);
        lockbox.deposit(smallb);
        String codeForSmallC = lockbox.deposit(smallc);
        Item retrivedSmallC = lockbox.pickup(codeForSmallC);
        lockbox.deposit(smalld);

    }

}

//lockbox:
//state: compartment[], Map<String, AccessCode>
//action:
//String deposit(item);//scan item, find available compartment, generate accesscode, attach it to compartments
//compartment findAvailableCompartment();
//AccessCode generateAccessCode();
//item pickup(String, compartment),//validate code(if TTL), remove it from map, empty the compartment
class Lockbox{ ;
            private final Compartment[] compartments;
            private final Map<String, AccessCode> strToAccessCode = new HashMap<>();
            private final Random random;

            public Lockbox( Compartment[] compartments) {

                this.compartments = compartments;
                random = new Random();
            }

            public String deposit(Item item){
                //get item size
                //find a matching available compartment
                Compartment emptyCompartment = findAvailableCompartment(item.getSize());
                if (emptyCompartment == null){
                    throw new RuntimeException("there is no available compartment of this size");
                }
                //generate accessCode, map the relationship
                AccessCode accessCode = generateAccessCode(emptyCompartment);

                //put in the item
                emptyCompartment.storeItem(item);
                strToAccessCode.put(accessCode.getCode(), accessCode);
                return accessCode.getCode();
            }

            public Item pickup(String code){
//                user type in code;
                AccessCode accessCode = strToAccessCode.get(code);
//                check code eligibility;
                if (accessCode == null){
                    throw new RuntimeException("the code cannot be null");
                }
                if (accessCode.isExpired()) {
                    System.out.println("Sorry, the code is expired");
                    return null;
                }
//                get item
                Compartment compartment = accessCode.getCompartment();
                Item retrived =  compartment.retrieveItem();
                strToAccessCode.remove(accessCode.getCode());
                return retrived;
            }

            public Compartment findAvailableCompartment(Size size){
                for(int i = 0; i < compartments.length; i++){
                    Compartment compartment = compartments[i];
                    if (compartment.isAvailable() && compartment.getSize() == size){
                        return compartment;
                    }
                }
                return null;
            }

            public AccessCode generateAccessCode(Compartment compartment){
                String code = String.valueOf(10000 + random.nextInt(10000));
                while(strToAccessCode.containsKey(code)){
                    code = String.valueOf(10000 + random.nextInt(10000));
                }
                Instant expiration = Instant.now().plus(Duration.ofDays(7));
                return new AccessCode(code, compartment, expiration);
            }
}


//compartment:
//    state: item, size,
//    action:
//        1. boolean deposit(item);
//        2. item retrieve()
//        3. boolean isAvailable()
class Compartment{
    private Item item;
    private final Size size;

    public Compartment(Size size){
        this.size = size;
        item = null;
    }
    public Size getSize(){
        return size;
    }

    public boolean isAvailable(){
        return item == null;
    }
    public void storeItem(Item item){
        if (item == null || item.getSize() != this.size || this.item != null){
            throw new RuntimeException("this Item cannot be stored");
        }
        this.item = item;
    }
    public Item retrieveItem(){
        if (this.item == null){
            throw new RuntimeException("there is no item in this compartment");
        }
        Item retrieved = this.item;
        this.item = null;
        return retrieved;
    }
}

//Accesscode:
//    state: code, compartment, TTL
//    action;
//        1. boolean isExpired()
class AccessCode{
    String code;
    Compartment compartment;
    Instant expirationDate;

    public AccessCode(String code, Compartment compartment, Instant expirationDate){
        this.code = code;
        this.compartment = compartment;
        this.expirationDate = expirationDate;
    }
    public Compartment getCompartment(){
        return compartment;
    }
    public String getCode(){
        return code;
    }
    public boolean isExpired(){
        return expirationDate.isBefore(Instant.now());
    }
}


class Item{
    Size size;

    public Item(Size size){
        this.size = size;
    }
    public Size getSize() {
        return size;
    }
}
enum Size{
    SMALL,
    MEDIUM,
    LARGE
        }


