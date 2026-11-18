import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/*
business flow:
payment -> user pick payment method -> gateway pick paymentProcessor -> authorize -> capture -> return

Entity:
1. PaymentGatewayService:
    - Map<merchentID + paymentMethod, paymentProcessor>;
    create(merchantID, paymentMethod, amount);

2.payment:
    -merchantID;
    -status;
    -amount;
    - paymentMethod;
3.  PaymentProcessor:
    Stripe
    Paypal
    authorize()
     capture()
     refund()
4. Status:
    INITIATED,
    AUTHORIZED,
    CAPTURED,
    FAILED,
    CAPTURE_FAILED,
    REFUNDED
5. PaymentMethod:
        - CARD,
        -WALLET,
        -UPI

 */



public class PaymentGatewayOOD {
}

class PaymentGatewayService{
    private final int retryAllowed;
    private final Map<String, PaymentProcessor> routing = new HashMap<>();
    private final Map<String, PaymentProcessor> idToProcessor = new HashMap<>();
    private final Map<IdempotentKey, Payment> idemKeyToPayment = new ConcurrentHashMap<>();
    private final Map<IdempotentKey, Object> locks = new ConcurrentHashMap<>();

    public PaymentGatewayService(int retryAllowed){
        this.retryAllowed = retryAllowed;
    }

    public Payment processPayment(String merchantId, PaymentMethod paymentMethod, BigDecimal amount, IdempotentKey idempotentKey) {
        //validate idempotency Key
        if (!merchantId.equals(idempotentKey.merchantId())
                || idempotentKey.requestId() == null
                || idempotentKey.requestId().isBlank()){
            throw new IllegalArgumentException("invalid idempotent key");
        }

        Object lock  = locks.computeIfAbsent(idempotentKey, k -> new Object());
        synchronized (lock){
            //idempotency check
            if (idemKeyToPayment.containsKey(idempotentKey)) {
                return idemKeyToPayment.get(idempotentKey);
            }

            String key = merchantId + paymentMethod.getName();
            if (!routing .containsKey(key) ) {
                throw new RuntimeException("the merchant doesnt support this payment method");
            }
            PaymentProcessor processor = routing.get(key);
            Payment payment = new Payment(merchantId, paymentMethod, processor.getId(), amount);

            idemKeyToPayment.put(idempotentKey, payment);
            ProcessorResponse authorizeResponse = executeWithRetry(() -> processor.authorize(payment));
            if (authorizeResponse.result() != ProcessorResult.SUCCESS) {
                payment.transitionTo(PaymentStatus.AUTHORIZED_FAILED);
                return payment;
            }
            payment.transitionTo(PaymentStatus.AUTHORIZED);

            ProcessorResponse capturedResponse = executeWithRetry(() -> processor.capture(payment));
            if (capturedResponse.result() != ProcessorResult.SUCCESS) {
                payment.transitionTo(PaymentStatus.CAPTURE_FAILED);
                return payment;
            }
            payment.transitionTo(PaymentStatus.CAPTURED);

            return payment;
        }

    }

    public ProcessorResponse executeWithRetry(Supplier<ProcessorResponse> operation) {
        int maxRetryAttempt = retryAllowed;
        ProcessorResponse response = null;
        for(int i = 0; i < maxRetryAttempt; i++) {
            response = operation.get();
            if (response.result() != ProcessorResult.RETRIABLE_FAILURE){
                return response;
            }
        }
        return response;
    }

    public Payment refund(IdempotentKey idempotentKey) {
        Object lock  = locks.computeIfAbsent(idempotentKey, k -> new Object());
        synchronized (lock){
            Payment payment = idemKeyToPayment.get(idempotentKey);
            if (payment == null) {
                throw new RuntimeException("this payment record doesnt exist");
            }
            if (payment.getStatus() == PaymentStatus.REFUNDED){
                return payment;
            }
            if (payment.getStatus() != PaymentStatus.CAPTURED){
                throw new RuntimeException("the payment isn't fulfilled");
            }

            PaymentProcessor processor = idToProcessor.get(payment.getProcessorID());

            ProcessorResponse refundResponse = executeWithRetry(() -> processor.refund(payment));
            if (refundResponse.result() == ProcessorResult.SUCCESS) {
                payment.transitionTo(PaymentStatus.REFUNDED);
                return payment;
            }
            return payment;
        }

    }
}

interface PaymentProcessor{
    public String getId();
    public ProcessorResponse authorize(Payment payment);
    public ProcessorResponse capture(Payment payment);
    public ProcessorResponse refund(Payment payment);
    public void mockProcessorResponse(ProcessorResult result);
}

class StripePaymentProcessor implements PaymentProcessor{
    private final String processorID;
    private ProcessorResult mockResult = ProcessorResult.SUCCESS;

    public StripePaymentProcessor(String processorID){
        this.processorID = processorID;
    }
    @Override
    public String getId() {
        return processorID;
    }

    @Override
    public ProcessorResponse authorize(Payment payment) {
        return new ProcessorResponse(mockResult, Operation.AUTHORIZE);
    }
    @Override
   public ProcessorResponse capture(Payment payment) {
        return new ProcessorResponse(mockResult, Operation.CAPTURE);
    }
    @Override
    public ProcessorResponse refund(Payment payment) {
        return new ProcessorResponse(mockResult, Operation.REFUND);
    }

    @Override
    public void mockProcessorResponse(ProcessorResult result) {
        mockResult = result;
    }
}


class Payment{
    private static final Map<PaymentStatus, Set<PaymentStatus>>
            VALID_TRANSITIONS = Map.of(

            PaymentStatus.INITIATED,
            Set.of(
                    PaymentStatus.AUTHORIZED,
                    PaymentStatus.AUTHORIZED_FAILED
            ),

            PaymentStatus.AUTHORIZED,
            Set.of(
                    PaymentStatus.CAPTURED,
                    PaymentStatus.CAPTURE_FAILED
            ),

            PaymentStatus.CAPTURED,
            Set.of(PaymentStatus.REFUNDED)
    );

    private final String merchantId;
    private final PaymentMethod paymentMethod;
    private final String processorID;
    private final BigDecimal amount;
    private PaymentStatus status;

    public Payment(String merchantId, PaymentMethod paymentMethod, String processorID, BigDecimal amount) {
        this.merchantId = merchantId;
        this.paymentMethod = paymentMethod;
        this.processorID = processorID;
        status = PaymentStatus.INITIATED;
        this.amount = amount;

    }
    public String getProcessorID(){
        return processorID;
    }

    public PaymentStatus getStatus() {
        return status;
    }
    public String getMerchantId() {
        return merchantId;
    }
    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

   public void transitionTo(PaymentStatus next) {
        if (!VALID_TRANSITIONS.get(this.status).contains(next)){
            throw new IllegalArgumentException("cannot transition to said Payment Status");
        }
        this.status = next;
    }
}

enum PaymentMethod{
    CARD("CARD"),
    WALLET("WALLET"),
    UPI("UPI");

    private final String name;
    private PaymentMethod(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }

}
enum PaymentStatus{
    INITIATED,
    AUTHORIZED,
    AUTHORIZED_FAILED,
    CAPTURED,
    FAILED,
    CAPTURE_FAILED,
    REFUNDED
}

record ProcessorResponse(
        ProcessorResult result,
        Operation operation
){


}
record IdempotentKey(
        String merchantId,
        String requestId
){}
enum ProcessorResult{
    SUCCESS,
    PERMINANT_FAILURE,
    RETRIABLE_FAILURE,
}
enum Operation{
    AUTHORIZE,
    CAPTURE,
    REFUND
}




