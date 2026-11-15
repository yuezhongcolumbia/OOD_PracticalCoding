

/*Design and implement a Splitwise System that allows users to
 split expenses among groups and individuals. The system should handle
 expense tracking, balance calculations, and settlement of debts between users.*/

/*Requirements:
1. core functionalities:
    a. expense tracking: who paid what, split between whom;
        split strategy:
            a.  (EQUAL, EXACT, PERCENTAGE)
    b. track expense history
2. settlements:
    a.who owes whom how much;
    b. track settlement status;

3. group management;
        1. add user
        2. remove user
        3. track group expense*/

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/*
Entity:
    1. SplitWise:
        -Map<String, Group>;
            Transaction addExpense(user, amount, group, List<User>, Stategy )
    2. Group:
        - Map<String, User>;
        - Map<User, >;
    3. User:
        -List<Group>;
    4. Expense:
        - User payer;
        - List<User> involved;
        - Double Amount;
        - Strategy;
    4. Transaction:

    5. Strategy;

    6. Split:
*/

/*
Race Condition:
1. User 的balancesheet update, Lost update;
2. addExpense(), settle(),operations需要atomic;
3. simplyfyDebt（）虽然是read operation, 也需要加锁；
*/

/*
遗留问题：
1. balancesheet没有group scope, 万一一个朋友同时出现在两个group, calculateBalance会merge
*/




public class SplitWiseOOD {
}

class SplitWise{
       private final  Map<String, Group> groups = new HashMap<>();
       private final Map<String, User> users = new HashMap<>();


    public User addUser(String id) {
        User user = new User(id);
        users.put(user.getId(), user);
        return user;
    }

    public Group addGroup(String id, List<User> members) {
        Group group = new Group(id, members);
        groups.put(group.getId(), group);
        return group;
    }

    public User getUser(String id) { return users.get(id); }
    public Group getGroup(String id) { return groups.get(id); }


       synchronized public  void addExpense(Expense.Builder builder){
            Expense expense = builder.build();
            User payer = expense.getPayer();
            List<Split> splits = expense.getSplits();
            for(Split split: splits){
                if (split.user() != payer){
                    payer.credit(split.user(), split.amount());
                    split.user().debt(payer, split.amount());
                }

            }
       }

       synchronized public void settle(String from, String to, BigDecimal amount){
           if (!users.containsKey(from)|| !users.containsKey(to)) {
               throw new IllegalArgumentException("user doesn't exist");
           }
           User userFrom = users.get(from);
           User userTo = users.get(to);
           userFrom.credit(userTo, amount);
           userTo.debt(userFrom, amount);
       }
       synchronized public List<Transaction> simplfyGroupDebt(String groupId){
           List<Map.Entry<User, BigDecimal>> creditors = new ArrayList<>();
           List<Map.Entry<User, BigDecimal>> debtors = new ArrayList<>();
           Group group = groups.get(groupId);
           for(User user: group.getUsers()){
               BigDecimal net = user.calculateNetBalance(group);
               if (net.compareTo(BigDecimal.ZERO) > 0){
                   creditors.add(new AbstractMap.SimpleEntry<>(user, net));
               }
               if (net.compareTo(BigDecimal.ZERO) < 0){
                   BigDecimal netAbs = net.abs();
                   debtors.add(new AbstractMap.SimpleEntry<>(user, netAbs));
               }
           }
           Collections.sort(debtors,Map.Entry.<User, BigDecimal>comparingByValue().reversed());
           Collections.sort(creditors,Map.Entry.<User, BigDecimal>comparingByValue().reversed());

           int debtorPointer = 0;
           int creditorPointer = 0;
           List<Transaction> result = new ArrayList<>();
           while(debtorPointer < debtors.size() && creditorPointer < creditors.size()){
               Map.Entry<User, BigDecimal> debtor = debtors.get(debtorPointer);
               Map.Entry<User, BigDecimal> creditor = creditors.get(creditorPointer);

               BigDecimal amount = debtor.getValue().min(creditor.getValue());
               Transaction transaction = new Transaction(debtor.getKey(), creditor.getKey(), amount);
               result.add(transaction);

               debtor.setValue(debtor.getValue().subtract(amount));
               creditor.setValue(creditor.getValue().subtract(amount));
               if (debtor.getValue().abs().compareTo(new BigDecimal("0.01")) < 0){
                   debtorPointer++;
               }
               if (creditor.getValue().compareTo(new BigDecimal(0.01)) < 0){
                   creditorPointer++;
               }

           }
           return result;
       }

}
class Expense{
        private final String id;
        private final User payer;
        private final List<User> participants;
        private final List<Split> splits;
        private final BigDecimal amount;
        private final SplitStrategy splitStrategy;
        private final List<BigDecimal> exactShare ;
        private final List<Double> percentageShare ;

        public Expense(Builder builder){
            id = builder.id;
            payer = builder.payer;
            amount = builder.amount;
            splitStrategy = builder.splitStrategy;
            participants = new ArrayList<>(builder.participants);
            exactShare = new ArrayList<>(builder.exactShare);
            percentageShare = new ArrayList<>(builder.percentageShare);
            splits = splitStrategy.calculateSplit(participants,amount, exactShare, percentageShare);

        }
        public User getPayer() {
            return payer;
        }
        public List<Split> getSplits() {
            return splits;
        }

        public static class Builder{
            private String id;
            private User payer;
            private BigDecimal amount;
            private SplitStrategy splitStrategy;
            private List<User> participants;
            private List<BigDecimal> exactShare = new ArrayList<>();
            private List<Double> percentageShare = new ArrayList<>();

            public Builder setId(String id){
                this.id = id;
                return this;
            }
            public Builder setPayer(User payer){
                this.payer = payer;
                return this;
            }
            public Builder setAmount(BigDecimal amount){
                this.amount = amount;
                return this;
            }
            public Builder setSplitStrategy(SplitStrategy splitStrategy){
                this.splitStrategy = splitStrategy;
                return this;
            }
            public Builder setParticipants(List<User> participants){
                this.participants = new ArrayList<>(participants);
                return this;
            }
            public Builder setExactShare(List<BigDecimal> exactShare){
                this.exactShare = exactShare;
                return this;
            }
            public Builder setPercentageShare(List<Double> percentageShare){
                this.percentageShare = percentageShare;
                return this;
            }
            public Expense build(){
                if (splitStrategy == null){
                    throw new IllegalArgumentException("splitStrategy cannot be null");
                }
                return new Expense(this);
            }

        }
}

class User{
      private final String id;
      private final Map<String, BigDecimal> balanceSheet = new HashMap<>();


      public User(String id){
          this.id = id;
      }
      public String getId() {
          return id;
      }

      synchronized public  void debt(User debtor, BigDecimal amount){
          balanceSheet.put(debtor.id, balanceSheet.getOrDefault(debtor.id, BigDecimal.ZERO).subtract(amount));
          if (balanceSheet.get(debtor.id).compareTo(BigDecimal.ZERO) == 0){
              balanceSheet.remove(debtor.id);
          }
      }
        synchronized public void credit(User user, BigDecimal amount){
          balanceSheet.put(user.id, balanceSheet.getOrDefault(user.id, BigDecimal.ZERO).add(amount));
          if (balanceSheet.get(user.id).compareTo(BigDecimal.ZERO) == 0){
              balanceSheet.remove(user.id);
          }
      }
      public BigDecimal calculateNetBalance(Group group){
          List<User> groupParticipants = group.getUsers();
          Set<String> participantIds = new HashSet<>();
          for(User user: groupParticipants){
              participantIds.add(user.id);
          }
          BigDecimal net = BigDecimal.ZERO;
          for(Map.Entry<String, BigDecimal> entry: balanceSheet.entrySet()){
              if (participantIds.contains(entry.getKey())){
                  net = net.add(entry.getValue());
              }

          }
          return net;
      }

}
class Group{
        private final String id;
        private final List<User> users ;

        public Group(String id, List<User> users){
            this.id = id;
            this.users = new ArrayList<>(users);
        }
        public String getId(){
            return id;
        }
        public List<User> getUsers() {
            return List.copyOf(users);
        }
}
record Split(
        User user,
        BigDecimal amount
){}

interface SplitStrategy{
    List<Split> calculateSplit(List<User> users, BigDecimal totalAmount, List<BigDecimal> exactShare, List<Double> percentageShare);
}
class EvenSplitStrategy implements SplitStrategy{
        @Override
        public List<Split> calculateSplit(List<User> users, BigDecimal totalAmount, List<BigDecimal> exactShare, List<Double> percentageShare){
            if (users == null || users.isEmpty()){
                throw new IllegalArgumentException("expense participants cannot be null or empty");
            }
            List<Split> splits = new ArrayList<>();
            int numberOfUsers = users.size();
            BigDecimal amount = totalAmount.divide(
                    BigDecimal.valueOf(users.size()),
                    2,
                    RoundingMode.HALF_UP
            );
            for(User user: users){
                splits.add(new Split(user, amount));
            }
            return splits;
        }
}

class ExactSplitStrategy implements SplitStrategy{
        @Override
        public List<Split> calculateSplit(List<User> users, BigDecimal totalAmount, List<BigDecimal> exactShare, List<Double> percentageShare){
            if (users == null || users.isEmpty()){
                throw new IllegalArgumentException("expense participants cannot be null or empty");
            }
            if (exactShare == null || users.size() != exactShare.size()){
                throw new IllegalArgumentException("number of shares must match number of users");
            }
            if (exactShare.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .subtract(totalAmount)
                    .abs()
                    .compareTo(new BigDecimal("0.01")) > 0){
                throw new RuntimeException("total shares not equal to total amount");
            }
            List<Split> splits = new ArrayList<>();
            for(int i = 0; i < exactShare.size(); i++){
                splits.add(new Split(users.get(i), exactShare.get(i)));
            }
            return splits;
        }
}

class PercentageSplitStrategy implements SplitStrategy{
    @Override
     public  List<Split> calculateSplit(List<User> users,
                                         BigDecimal totalAmount,
                                         List<BigDecimal> exactShare,
                                         List<Double> percentageShare){
        if (percentageShare == null || users.size() != percentageShare.size()){
            throw new IllegalArgumentException("number of shares must match number of users");
        }
        if (percentageShare.stream().reduce(0.00, Double::sum) != 1){
            throw new RuntimeException("total percentage not equal to 1");
        }
        List<Split> splits = new ArrayList<>();
        for(int i = 0; i < percentageShare.size(); i++){
            BigDecimal amount = totalAmount.multiply(BigDecimal.valueOf(percentageShare.get(i)));
            splits.add(new Split(users.get(i), amount));
        }
        return splits;
    }


}
record Transaction(
        User from,
        User to,
        BigDecimal amount
){}