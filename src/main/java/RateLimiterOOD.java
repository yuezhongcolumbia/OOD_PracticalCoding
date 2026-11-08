import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

//{
//        "endpoint":"/search",
//        "algorithm":"TokenBucket",
//        "algoConfig":{
//        "capacity":1000,
//        "refillRatePerSecond":10
//        }
//        }
//
//
//        1.factory design;
//2.
//rate liming
//algo;TokenBucketLimiter,SlidingWindowLogLimiter ..;
//        3.
//handle concurrency;


public class RateLimiterOOD {

}

class RateLimiter {
    private final Map<String, Limiter> endPointToLimiter;
    private Limiter defaultLimiter;

    public RateLimiter(List<Map<String, Object>> configs, Map<String, Object> defaultConfig) {
        endPointToLimiter = new HashMap<>();
        LimiterFactory limiterFactory = new LimiterFactory();
        for (Map<String, Object> config : configs) {
            String endPoint = (String) config.get("endpoint");
            Limiter limiter = limiterFactory.createLimiter(config);
            endPointToLimiter.put(endPoint, limiter);
        }
        defaultLimiter = limiterFactory.createLimiter(defaultConfig);
    }

    public RateLimitResult allow(String userId, String endPoint) {
        Limiter limiter = defaultLimiter;
        if (endPointToLimiter.containsKey(endPoint)) {
            limiter = endPointToLimiter.get(endPoint);
        }
        return limiter.allow(userId);
    }

    public void updateEndPointConfig(List<Map<String, Object>> configs, Map<String, Object> defaultConfig){
        for(Map<String, Object> config : configs){
            String endPoint = (String) config.get("endpoint");
            Map<String, Object> newConfig = (Map<String, Object>) config.get("algoConfig");
            if (endPointToLimiter.containsKey(endPoint)) {
                Limiter limiter = endPointToLimiter.get(endPoint);
                limiter.updateConfig(newConfig);
            } else {
                LimiterFactory limiterFactory = new LimiterFactory();
                endPointToLimiter.put(endPoint, limiterFactory.createLimiter(config));
            }
        }
    }

}

interface Limiter {
    public RateLimitResult allow(String userId);
    public void updateConfig(Map<String, Object> config);
}

class TokenBucketLimiter implements Limiter {
    public static class TokenBucket {
        double tokens;
        long lastRefilledTime;

        public TokenBucket(double tokens, long lastRefilledTime) {
            this.tokens = tokens;
            this.lastRefilledTime = lastRefilledTime;
        }
    }

    private  int capacity;
    private  int refillRatePerSecond;
    private final ConcurrentHashMap<String, TokenBucket> userBuckets = new ConcurrentHashMap<>();

    public TokenBucketLimiter(int capacity, int refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
    }
    @Override
    public RateLimitResult allow(String userId) {
        //create or get from map
        long now = System.currentTimeMillis();
        TokenBucket tokenBucket = userBuckets.computeIfAbsent(userId, k -> new TokenBucket(capacity, now));

        synchronized (tokenBucket) {
            //refill logic if needed
            refill(tokenBucket, now);
            // consume token
            if (tokenBucket.tokens >= 1) {
                tokenBucket.tokens--;
                long remainingToken = (long) Math.floor(tokenBucket.tokens);
                return new RateLimitResult(true, remainingToken, null);
            }
            //if failed figure out retry
            double tokenNeeded = 1 - tokenBucket.tokens;
            long timeToRefill = (long) Math.ceil(tokenNeeded / refillRatePerSecond * 1000);
            return new RateLimitResult(false, 0L, timeToRefill);
        }



    }

    public void refill(TokenBucket tokenBucket, long now) {
        if (tokenBucket.lastRefilledTime < now) {
            double elapsedTimeinSeconds = (now - tokenBucket.lastRefilledTime) / 1000.0;
            double refill = elapsedTimeinSeconds * refillRatePerSecond;
            tokenBucket.tokens = Math.min(capacity, tokenBucket.tokens + refill);
            tokenBucket.lastRefilledTime = now;
        }
    }
    @Override
    public void updateConfig(Map<String, Object> config) {
        int newMaxCapacity = (Integer) config.get("capacity");
        int newRefillRatePerSecond = (Integer) config.get("refillRatePerSecond");

        this.refillRatePerSecond = newRefillRatePerSecond;
        this.capacity = newMaxCapacity;
        for(TokenBucket tokenBucket : userBuckets.values()) {
            if (tokenBucket.tokens > newMaxCapacity) {
                tokenBucket.tokens = newMaxCapacity;
            }
        }
    }
}

class SlidingWindowLogLimiter implements Limiter {
    static class RequestLog{
        private final Queue<Long> log = new LinkedList<>();

    }

    private final int maxRequests;
    private final long windowMs;
    private final ConcurrentHashMap<String, RequestLog> requestLogs = new ConcurrentHashMap<>();

    public SlidingWindowLogLimiter(int maxRequests, long windowMs) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
    }
    @Override
    public RateLimitResult allow(String userId) {
//        10:01 10:02 10:03
//                window = 30ms
//                request = 2
//        how to calculate retry time?
        //create or get
        RequestLog requestLog = requestLogs.computeIfAbsent(userId, k -> new RequestLog());
        synchronized (requestLog) {
            //evict stale logs
            long now = System.currentTimeMillis();
            long cutoff = now - windowMs;
            Queue<Long> log = requestLog.log;
            while (!log.isEmpty() && log.peek() <= cutoff){
                requestLog.log.poll();
            }
            //enqueue
            if (log.size() < maxRequests) {
                log.offer(now);
                long remaining = (long)(maxRequests - log.size());
                return new RateLimitResult(true, remaining, null);
            }
            //if not calculate retry time
            long retryInMs = log.peek() + windowMs - now;
            return new RateLimitResult(false, 0L, retryInMs);
        }


    }

    @Override
    public void updateConfig(Map<String, Object> config) {

    }
}

class LimiterFactory {
    public Limiter createLimiter(Map<String, Object> config) {
        if (config == null) {
            throw new IllegalArgumentException("config is null");
        }
        String algo = (String) config.get("algorithm");
        Map<String, Object> algoConfig = (Map<String, Object>) config.get("algoConfig");

        if (algo.equals("TokenBucket")) {
            int capacity = (Integer) algoConfig.getOrDefault("capacity", 0);
            int ratePerSecond = (Integer) algoConfig.getOrDefault("refillRatePerSecond", 0);
            return new TokenBucketLimiter(capacity, ratePerSecond);
        }
        if (algo.equals("SlidingWindowLog")) {
            long windowMs = (Long) algoConfig.getOrDefault("windowMs", 0);
            int maxRequests = (Integer) algoConfig.getOrDefault("maxRequests", 0);
            return new SlidingWindowLogLimiter(maxRequests, windowMs);
        }
        throw new IllegalArgumentException("%s is an unknown algo: ".formatted(algo));
    }
}

class RateLimitResult {
    Boolean allowed;
    Long remainingTokens;
    Long nextRetryTime;

    public RateLimitResult(Boolean allowed, Long remainingTokens, Long nextRetryTime) {
        this.allowed = allowed;
        this.remainingTokens = remainingTokens;
        this.nextRetryTime = nextRetryTime;
    }

}

