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

        PrintWriter pw = new PrintWriter(new File("averageWaitingTime.csv"));
        SchedulingResult fcfsResult = fcfs(copy(processList));
        generateReport(fcfsResult, "FCFS.txt");
        pw.println("FCFS,," + fcfsResult.getAverageWaitingTime());

        for (int quantum = 1; quantum <= 15; quantum += 1) {
            SchedulingResult roundRobinResult = roundRobin(copy(processList), quantum);
            generateReport(roundRobinResult, "RR-quantum-" + quantum + ".txt");
            pw.println("RR,quantum=" + quantum + "," + roundRobinResult.getAverageWaitingTime());
        }

        SchedulingResult srtfResult = srtf(copy(processList));
        generateReport(srtfResult, "SRTF.txt");
        pw.println("SRTF,," + srtfResult.getAverageWaitingTime());

        DecimalFormat df = new DecimalFormat("0.0");

        for (double alpha = 0.0; alpha < 1.0; alpha += 0.1) {
            SchedulingResult sjfResult = sjf(copy(processList), alpha);
            generateReport(sjfResult, "SJF-alpha-" + df.format(alpha) + ".txt");
            pw.println("SJF,alpha=" + df.format(alpha) + "," + sjfResult.getAverageWaitingTime());
        }

        pw.close();
    }

    static Queue<Process> loadInput() throws FileNotFoundException {
        Scanner in = new Scanner(new File("input/input.txt"));
        Queue<Process> processList = new LinkedList<>();
        while (in.hasNextLine()) {
            String[] tokens = in.nextLine().trim().split(" ");
            int id = Integer.parseInt(tokens[0]);
            int arrivalTime = Integer.parseInt(tokens[1]);
            int burstTime = Integer.parseInt(tokens[2]);
            processList.add(new Process(id, arrivalTime, burstTime));
        }
        in.close();
        return processList;
    }

    static void generateReport(SchedulingResult result, String reportName) throws FileNotFoundException {
        PrintWriter pw = new PrintWriter(new File("output/" + reportName));
        for (ContextSwitch cs : result.contextSwitches) {
            pw.println("(" + cs.time + ", " + cs.processID + ")");
        }
        pw.println("average waiting time " + result.getAverageWaitingTime());
        pw.close();
    }

    static Queue<Process> copy(Queue<Process> list) {
        Queue<Process> copyList = new LinkedList<>();
        for (Process process : list) {
            copyList.add(process.copy());
        }
        return copyList;
    }

    static SchedulingResult fcfs(Queue<Process> processList) {
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

    static SchedulingResult sjf(Queue<Process> processList, double alpha) {
        Map<Integer, PredictionAndActualPair> map = new HashMap<>();

        int currentTime = 0;
        PriorityQueue<Process> queue = new PriorityQueue<>((o1, o2) -> {
            if (Math.abs(o1.prediction - o2.prediction) > 0.000001) {
                return Double.compare(o1.prediction, o2.prediction);
            } else { // prediction is the same, then order by arrival time
                return o1.arrivalTime - o2.arrivalTime;
            }
        });
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

    static SchedulingResult srtf(Queue<Process> processList) {
        int currentTime = 0;

        PriorityQueue<Process> queue = new PriorityQueue<>(new Comparator<Process>() {
            @Override
            public int compare(Process o1, Process o2) {
                if (o1.remainingTime != o2.remainingTime) {
                    return o1.remainingTime - o2.remainingTime;
                } else { // if remaining time is the same, order by arrival time
                    return o1.arrivalTime - o2.arrivalTime;
                }
            }
        });
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

    static SchedulingResult roundRobin(Queue<Process> processList, int quantum) {
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

                if (queue.isEmpty() && !processList.isEmpty()) {
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

    SchedulingResult(Queue<Process> completion, List<ContextSwitch> contextSwitches) {
        this.completion = completion;
        this.contextSwitches = contextSwitches;
    }

    double getAverageWaitingTime() {
        double waitingTime = 0.0;
        for (Process process : completion) {
            waitingTime += process.getWaitingTime();
        }
        return waitingTime / completion.size();
    }
}

class ContextSwitch {
    int time, processID;

    ContextSwitch(int time, int processID) {
        this.time = time;
        this.processID = processID;
    }
}

class Process {
    int id, arrivalTime, burstTime, remainingTime, finishTime;
    double prediction;

    Process(int id, int arrivalTime, int burstTime) {
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

    Process copy() {
        return new Process(id, arrivalTime, burstTime);
    }
}

class PredictionAndActualPair {
    double prediction;
    double actual;

    PredictionAndActualPair(double prediction, double actual) {
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