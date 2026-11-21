package TaskScheduler;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/*
Requirements:
1. schedule task;
2. cancel task;
3. schedule recurring task
4. 3 differnt tasks:
    1. one time;
    2. fix_rate;
    3. fix_delay: after completion
 */

/*
flow:
1. basic flow; single watcher thread orchestration, notified and re-evaluate when task done and new task scheduled
2. Job running state;
3. cancel;
4. retry;
5. shutdown;
*/

public class TaskSchedulerOOD {
    public static void main(String[] args) throws InterruptedException {
        RunningScheduler scheduler = new RunningScheduler(4);
        long start = System.currentTimeMillis();
        Runnable task = () -> {
            System.out.println("Task started");

            try {
                Thread.sleep(2000); // 模拟执行 2 秒
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            System.out.println("Task finished");
        };

        scheduler.schedule(task, JobType.ONE_TIME, 0,2000);
        // Test 1: One-Time Task
        scheduler.schedule(() -> {
            log(start, "ONE_TIME executed");
        }, JobType.ONE_TIME, 0, 2000);

        // Test 2: Fixed-Rate Task
        String fixedRateId = scheduler.schedule(() -> {
            log(start, "FIX_RATE started");
            sleep(1500);
            log(start, "FIX_RATE finished");
        }, JobType.FIX_RATE, 3000, 0);

        // Test 3: Fixed-Delay Task
        String fixedDelayId = scheduler.schedule(() -> {
            log(start, "FIX_DELAY started");
            sleep(1500);
            log(start, "FIX_DELAY finished");
        }, JobType.FIX_DELAY, 3000, 0);

        // Let the tasks run
        Thread.sleep(11000);

        // Test 4: Cancellation
        System.out.println("\n--- Cancelling recurring tasks ---");
        scheduler.cancel(fixedRateId);
        scheduler.cancel(fixedDelayId);

        Thread.sleep(4000);

        // Test 5: Shutdown
        System.out.println("\n--- Shutdown ---");
        scheduler.shutdown();

        System.out.println("All tests completed");
    }

    private static void log(long start, String message) {
        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("[%5d ms] %s%n", elapsed, message);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

class RunningScheduler{
    private final Scheduler timeTable = new Scheduler();
    private final ExecutorService executor;
    private boolean running;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final Thread watcherThread = new Thread(this::orchestrate, "watcher thread");

    public RunningScheduler(int threadCount) {
        executor = Executors.newFixedThreadPool(threadCount);
        running = true;
        watcherThread.start();
    }
    public String schedule(Runnable task, JobType jobType, long interval, long delayMillis) {
        return changing(()->
            timeTable.schedule(task, jobType, interval, delayMillis)
        );
    }

     public void cancel(String jobId){
//        1. does watcher needs to signaled?;
//        2. new status for Job；
//        3. new map for scheduler for quick lookup;
//        4. during which step this need to be taken care of;
        changing(()->timeTable.cancelTask(jobId));
    }
    public void orchestrate(){
        lock.lock();
        try{
            while(running){
                for(Job job: timeTable.takeDue()){
                    executor.execute(()->{
                        run(job);
                    });
                }
                OptionalLong next = timeTable.nextRunAt();
                if (next.isEmpty()){
                    condition.await();
                } else {
                    long gap = next.getAsLong() - System.currentTimeMillis();
                    condition.await(gap, TimeUnit.MILLISECONDS);
                }
            }
        }catch(InterruptedException e){

        }finally{
            lock.unlock();
        }
    }
    private void run(Job job){
        //run the job
        //call the timetable finished
        //signal lock
        Throwable error = null;
        try{
            job.getTask().run();

        }catch(Exception e){
            error = e;
        }
        Throwable result = error;
        changing(()->{
            timeTable.finish(job, result);
            return null;
        });
    }

    private <T> T changing(Supplier<T> change){
        lock.lock();
        try{
            T result = change.get();
            condition.signal();
            return result;
        }finally{
            lock.unlock();
        }
    }

    public void shutdown() throws InterruptedException {
        changing(() -> {
            running = false;
            return true;
        });
        watcherThread.join();
        executor.shutdown();
        executor.awaitTermination(
                60, TimeUnit.SECONDS
        );

    }

}

class Scheduler{
    private final PriorityQueue<Job> timeTable = new PriorityQueue<>((a,b) -> Long.compare(a.getSchedule(), b.getSchedule()));
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final int maxRetryAttempts = 3;
    private final long backoff = 1000;


    public String schedule(Runnable task, JobType jobType, long interval, long delayMillis) {
//        Job(Runnable task, JobType jobType, long schedule, long interval)
        if (jobType != JobType.ONE_TIME && interval == 0){
            throw new IllegalArgumentException("delayMillis must be greater than 0 for fixRate and fixDelay");
        }
        Job job = new Job(task, jobType, System.currentTimeMillis() + delayMillis, interval);
        enqueue(job, System.currentTimeMillis() + delayMillis);
        jobs.put(job.getId(), job);
        return job.getId();

    }

    public boolean cancelTask(String jobId){
        if (jobId == null || jobId.isEmpty() || !jobs.containsKey(jobId)){
            throw new IllegalArgumentException("jobId is null or empty or doesnt exist");
        }
        Job job = jobs.get(jobId);
        JobStatus status = job.getStatus();
        if (status == JobStatus.SUCCEEDED || status == JobStatus.FAILED || status == JobStatus.CANCELED){
            System.out.println("job status is final, cannot be cancelled");
            return false;
        }
        job.setStatus(JobStatus.CANCELED);
        return true;
    }
    public List<Job> takeDue(){
        List<Job> res = new ArrayList<>();
        clean();
        while(!timeTable.isEmpty() && timeTable.peek().getSchedule() <= System.currentTimeMillis()){
            Job job = timeTable.poll();
            if (job.getJobType() == JobType.FIX_RATE){
                long now = System.currentTimeMillis();
                long interval = job.getInterval();
                long runAt = job.getSchedule() + interval;
                if (runAt < now){
                    runAt +=  (now - runAt)/interval * interval + interval;
                }
                job.setSchedule(runAt);
                enqueue(job, runAt);
                clean();
                if (job.getRunning()){
                    continue;
                }
            }
            job.incrementAttempts();
            job.setRunning(true);
            job.setStatus(JobStatus.RUNNING);
            res.add(job);
            clean();
        }
        return res;
    }
    public void clean(){
        while(!timeTable.isEmpty() &&(timeTable.peek().getStatus() == JobStatus.CANCELED
                || timeTable.peek().getStatus() == JobStatus.SUCCEEDED
                || timeTable.peek().getStatus() == JobStatus.FAILED) ){
            Job job = timeTable.poll();
            jobs.remove(job.getId());
        }
    }

    public void finish(Job job, Throwable error){
        if (error != null){
            System.out.println(job.getId()+": "+error.getMessage());
        }
        if(job.getStatus() == JobStatus.CANCELED){
            return;
        }
        job.setRunning(false);
        switch (job.getJobType()){
            case FIX_RATE -> {
                job.setStatus(JobStatus.SCHEDULED);
            }
            case FIX_DELAY -> {
                long next = job.getInterval() + System.currentTimeMillis();
                job.setSchedule(next);
                enqueue(job, next);
                job.setStatus(JobStatus.SCHEDULED);
            }
            case ONE_TIME -> {
                if (error != null){

                    if (job.getAttempts() < maxRetryAttempts){
                        long delay = backoff * (1L << (job.getAttempts() - 1));
                        long next = System.currentTimeMillis() + delay;
                        enqueue(job, next);
                        job.setStatus(JobStatus.SCHEDULED);
                    } else {
                        job.setStatus(JobStatus.FAILED);
                    }

                } else {
                    job.setStatus(JobStatus.SUCCEEDED);
                }
            }
        }
    }

    public OptionalLong nextRunAt(){
        return !timeTable.isEmpty() ?OptionalLong.of(timeTable.peek().getSchedule())  : OptionalLong.empty();
    }
    public void enqueue(Job job, long schedule){
        job.setSchedule(schedule);
        timeTable.add(job);
    }


}

class Job{
    private final String id = UUID.randomUUID().toString();
    private final Runnable task;
    private final JobType jobType;
    private long schedule;
    private final long interval;
    private boolean running;
    private JobStatus status;
    private int attempts;

    public Job(Runnable task, JobType jobType, long schedule, long interval){
        this.task = task;
        this.jobType = jobType;
        this.schedule = schedule;
        this.interval = interval;
        running = false;
        attempts = 0;
        status = JobStatus.SCHEDULED;
    }
    public int getAttempts(){
        return attempts;
    }
    public void incrementAttempts(){
        attempts++;
    }
    public JobStatus getStatus(){
        return status;
    }
    public void setStatus(JobStatus status){
        this.status = status;
    }
    public boolean getRunning(){
        return running;
    }
    public void setRunning(Boolean running){
        this.running = running;
    }
    public String getId(){
        return id;
    }
    public Runnable getTask(){
        return task;
    }
    public JobType getJobType(){
        return jobType;
    }
    public long getSchedule(){
        return schedule;
    }
    public void setSchedule(long schedule){
        this.schedule = schedule;
    }
    public long getInterval(){
        return interval;
    }


}

enum JobType{
    ONE_TIME,
    FIX_RATE,
    FIX_DELAY
}

enum JobStatus{
    SCHEDULED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELED
}
