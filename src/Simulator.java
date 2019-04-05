import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.*;

/**
 * Created by xufeng on 3/4/19.
 */
public class Simulator {
    public static void main(String[] args) throws FileNotFoundException {
        Queue<Process> processList = loadInput();

        SchedulingResult fcfsResult = fcfs(copy(processList));
        generateReport(fcfsResult, "FCFS.txt");

        for (int quantum = 1; quantum <= 20; quantum += 1) {
            SchedulingResult roundRobinResult = roundRobin(copy(processList), quantum);
            generateReport(roundRobinResult, "RR-quantum-" + quantum + ".txt");
        }

        SchedulingResult srtfResult  = srtf(copy(processList));
        generateReport(srtfResult, "SRTF.txt");

        DecimalFormat df = new DecimalFormat("0.0");

        for (double alpha = 0.1; alpha < 1.0 ; alpha += 0.1) {
            SchedulingResult sjfResult = sjf(copy(processList), alpha);
            generateReport(sjfResult, "SJF-alpha-" + df.format(alpha) + ".txt");
        }
    }

    private static Queue<Process> loadInput() throws FileNotFoundException {
        Scanner in = new Scanner(new File("input/input.txt"));
        Queue<Process> processList = new LinkedList<>();
        while(in.hasNextLine()) {
            String[] tokens = in.nextLine().trim().split(" ");
            int id = Integer.parseInt(tokens[0]);
            int arrivalTime = Integer.parseInt(tokens[1]);
            int burstTime = Integer.parseInt(tokens[2]);
            processList.add(new Process(id, arrivalTime, burstTime));
        }
        in.close();
        return processList;
    }


    public static void generateReport(SchedulingResult result, String reportName) throws FileNotFoundException {
        PrintWriter pw = new PrintWriter(new File("output/" + reportName));

        for (ContextSwitch cs : result.contextSwitches) {
            pw.println("(" + cs.time + ", " + cs.processID + ")");
        }

        double waitingTime = 0.0;
        Set<Integer> processIDs = new HashSet<>();
        for (Process process : result.completion) {
            waitingTime += process.getWaitingTime();
            processIDs.add(process.id);
        }
        double avgWaitingTime = waitingTime / result.completion.size();
        pw.println("average waiting time " + avgWaitingTime);
        System.out.println(reportName.replace(".txt", "") + "," + avgWaitingTime);

        pw.close();
    }

    public static Queue<Process> copy(Queue<Process> list) {
        Queue<Process> copyList = new LinkedList<>();
        for (Process process : list) {
            copyList.add(process.copy());
        }
        return copyList;
    }

    public static SchedulingResult fcfs(Queue<Process> processList) {
        int currentTime = 0;
        Queue<Process> completed = new LinkedList<>();
        List<ContextSwitch> contextSwitches = new ArrayList<>();

        while (!processList.isEmpty()) {
            Process serving = processList.poll();
            // handle the case where CPU is idle for sometime
            currentTime = Math.max(currentTime, serving.arrivalTime);

            contextSwitches.add(new ContextSwitch(currentTime, serving.id));

            currentTime += serving.remainingTime;
            serving.remainingTime = 0;
            serving.finishTime = currentTime;
            completed.add(serving);
        }

        return new SchedulingResult(completed, contextSwitches);
    }

    public static SchedulingResult sjf(Queue<Process> processList, double alpha) {
        Map<Integer, PredictionAndActualPair> map = new HashMap<>();

        int currentTime = 0;
        PriorityQueue<Process> queue = new PriorityQueue<>(Comparator.comparingDouble(o -> o.prediction));
        Queue<Process> completed = new LinkedList<>();
        List<ContextSwitch> contextSwitches = new ArrayList<>();

        queue.offer(processList.poll());

        while (!queue.isEmpty()) {
            Process serving = queue.poll();
            // handle the case where CPU is idle for sometime
            currentTime = Math.max(currentTime, serving.arrivalTime);
            contextSwitches.add(new ContextSwitch(currentTime, serving.id));

            currentTime += serving.remainingTime;
            serving.finishTime = currentTime;
            completed.add(serving);
            map.put(serving.id, serving.getPredictionAndActualPair());

            while (!processList.isEmpty() && (processList.peek().arrivalTime <= currentTime || queue.isEmpty())) {
                Process nextArrival = processList.poll();

                if (map.containsKey(nextArrival.id)) {
                    PredictionAndActualPair pair = map.get(nextArrival.id);
                    nextArrival.prediction = pair.getNextPrediction(alpha);
//                    System.out.println("next prediction is " + nextArrival.prediction);
                }

                queue.offer(nextArrival);
            }
        }

        return new SchedulingResult(completed, contextSwitches);
    }

    public static SchedulingResult srtf(Queue<Process> processList) {
        int currentTime = 0;

        PriorityQueue<Process> queue = new PriorityQueue<>(Comparator.comparingDouble(o -> o.remainingTime));
        Queue<Process> completed = new LinkedList<>();
        List<ContextSwitch> contextSwitches = new ArrayList<>();

        queue.offer(processList.poll());

        while (!queue.isEmpty()) {
            Process serving = queue.poll();
            // handle the case where CPU is idle for sometime
            currentTime = Math.max(currentTime, serving.arrivalTime);
            contextSwitches.add(new ContextSwitch(currentTime, serving.id));

            // the current executing process finishes before the next one arrives
            if (!processList.isEmpty()) {
                Process nextArrival = processList.peek();
                int possibleRemainingTime = serving.remainingTime - (nextArrival.arrivalTime - currentTime);

                if (possibleRemainingTime > 0) {
                    currentTime = nextArrival.arrivalTime;
                    serving.remainingTime = possibleRemainingTime;
                    queue.offer(processList.poll());

                    queue.offer(serving); // put it back to the queue, pre-emption will be determined in the next iteration of while loop
                } else { // finish the current process
                    currentTime += serving.remainingTime;
                    serving.remainingTime = 0; // finish the executing task
                    serving.finishTime = currentTime;
                    completed.add(serving);

                    queue.offer(processList.poll()); // serve the next
                }
            } else {
                currentTime += serving.remainingTime;
                serving.remainingTime = 0; // finish the executing task
                serving.finishTime = currentTime;
                completed.add(serving);
            }
        }

        return new SchedulingResult(completed, contextSwitches);
    }

    public static SchedulingResult roundRobin(Queue<Process> processList, int quantum) {
        int currentTime = 0;

        Queue<Process> queue = new LinkedList<>();
        Queue<Process> completed = new LinkedList<>();
        List<ContextSwitch> contextSwitches = new ArrayList<>();

        queue.offer(processList.poll());

        while (!queue.isEmpty()) {
            Process serving = queue.poll();
            // handle the case where CPU is idle for sometime
            currentTime = Math.max(currentTime, serving.arrivalTime);
            contextSwitches.add(new ContextSwitch(currentTime, serving.id));

            // case 1: next to serve's remaining time larger than quantum
            // -> update remaining time and put it at the end of the queue
            // case 2: next to serve's remaining time smaller than or equal to quantum
            // -> clear remaining time, mark it as complete
            // both cases: update current time

            if (serving.remainingTime > quantum) {
                currentTime += quantum;
                serving.remainingTime -= quantum;

                // add other processes that arrived before the next quantum starts
                while (!processList.isEmpty() && processList.peek().arrivalTime <= currentTime) {
                    queue.add(processList.poll());
                }

                queue.add(serving);
            } else {
                currentTime += serving.remainingTime;
                serving.remainingTime = 0;
                serving.finishTime = currentTime;
                completed.add(serving);

                if (!processList.isEmpty()) {
                    queue.add(processList.poll());
                }
            }
        }

        return new SchedulingResult(completed, contextSwitches);
    }
}

class SchedulingResult {
    Queue<Process> completion;
    List<ContextSwitch> contextSwitches;

    public SchedulingResult(Queue<Process> completion, List<ContextSwitch> contextSwitches) {
        this.completion = completion;
        this.contextSwitches = contextSwitches;
    }
}

class ContextSwitch {
    int time, processID;

    public ContextSwitch(int time, int processID) {
        this.time = time;
        this.processID = processID;
    }
}

class Process {
    int id, arrivalTime, burstTime, remainingTime, finishTime;
    double prediction;

    public Process(int id, int arrivalTime, int burstTime) {
        this.id = id;
        this.arrivalTime = arrivalTime;
        this.burstTime = burstTime;
        this.remainingTime = burstTime;
        this.finishTime = -1;
        this.prediction = 5.0;
    }

    int getWaitingTime() {
        return finishTime - (arrivalTime + burstTime);
    }

    PredictionAndActualPair getPredictionAndActualPair() {
        return new PredictionAndActualPair(prediction, burstTime);
    }

    public Process copy() {
        return new Process(id, arrivalTime, burstTime);
    }
}

class PredictionAndActualPair {
    double prediction;
    double actual;

    public PredictionAndActualPair(double prediction, double actual) {
        this.prediction = prediction;
        this.actual = actual;
    }

    double getNextPrediction(double alpha) {
        double nextPrediction = alpha * actual + prediction * (1 - alpha);
        return nextPrediction;
    }

    @Override
    public String toString() {
        return "{" +
                "prediction=" + prediction +
                ", actual=" + actual +
                '}';
    }
}