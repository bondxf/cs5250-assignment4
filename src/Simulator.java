import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

/**
 * Created by xufeng on 3/4/19.
 */
public class Simulator {
    public static void main(String[] args) throws FileNotFoundException {
        Scanner in = new Scanner(new File("test.txt"));
        Queue<Process> processList = new LinkedList<>();
        while(in.hasNextLine()) {
            String[] tokens = in.nextLine().trim().split(" ");
            int id = Integer.parseInt(tokens[0]);
            int arrivalTime = Integer.parseInt(tokens[1]);
            int burstTime = Integer.parseInt(tokens[2]);
            processList.add(new Process(id, arrivalTime, burstTime));
        }
        in.close();

        System.out.println("FCFS: ");
        Queue<Process> completion = fcfs(copy(processList));
        for (Process process : completion) {
            System.out.println(process.id + ": " + process.finishTime);
        }

        System.out.println("Round robin: ");
        completion = roundRobin(copy(processList), 10);
        for (Process process : completion) {
            System.out.println(process.id + ": " + process.finishTime);
        }

        System.out.println("Shortest remaining time first: ");
        completion = srtf(copy(processList));
        for (Process process : completion) {
            System.out.println(process.id + ": " + process.finishTime);
        }

        System.out.println("Shortest job first with prediction: ");
        completion = sjf(copy(processList), 0.5);
        for (Process process : completion) {
            System.out.println(process.id + ": " + process.finishTime);
        }
    }

    public static Queue<Process> copy(Queue<Process> list) {
        Queue<Process> copyList = new LinkedList<>();
        for (Process process : list) {
            copyList.add(process.copy());
        }
        return copyList;
    }

    public static Queue<Process> fcfs(Queue<Process> processList) {
        int currentTime = 0;
        Queue<Process> completed = new LinkedList<>();

        while (!processList.isEmpty()) {
            Process serving = processList.poll();
            currentTime += serving.remainingTime;
            serving.remainingTime = 0;
            serving.finishTime = currentTime;
            completed.add(serving);
        }

        return completed;
    }

    public static Queue<Process> sjf(Queue<Process> processList, double alpha) {
        Map<Integer, PredictionAndActualPair> map = new HashMap<>();

        int currentTime = 0;
        PriorityQueue<Process> queue = new PriorityQueue<>(Comparator.comparingDouble(o -> o.prediction));
        Queue<Process> completed = new LinkedList<>();

        queue.offer(processList.poll());

        while (!queue.isEmpty()) {
            Process serving = queue.poll();
            // handle the case where CPU is idle for sometime
            currentTime = Math.max(currentTime, serving.arrivalTime);
            currentTime += serving.remainingTime;
            serving.finishTime = currentTime;
            completed.add(serving);
            map.put(serving.id, serving.getPredictionAndActualPair());

            if (!processList.isEmpty()) {
                Process nextArrival = processList.poll();

                if (map.containsKey(nextArrival.id)) {
                    PredictionAndActualPair pair = map.get(nextArrival.id);
                    nextArrival.prediction = pair.getNextPrediction(alpha);
                }

                queue.offer(nextArrival);
            }
        }

        return completed;
    }

    public static Queue<Process> srtf(Queue<Process> processList) {
        int currentTime = 0;

        PriorityQueue<Process> queue = new PriorityQueue<>(Comparator.comparingDouble(o -> o.remainingTime));
        Queue<Process> completed = new LinkedList<>();

        queue.offer(processList.poll());

        while (!queue.isEmpty()) {
            Process serving = queue.poll();
            // handle the case where CPU is idle for sometime
            currentTime = Math.max(currentTime, serving.arrivalTime);

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

        return completed;
    }

    public static Queue<Process> roundRobin(Queue<Process> processList, int quantum) {
        int currentTime = 0;

        Queue<Process> queue = new LinkedList<>();
        Queue<Process> completed = new LinkedList<>();

        queue.offer(processList.poll());

        while (!queue.isEmpty()) {
            Process serving = queue.poll();
            // handle the case where CPU is idle for sometime
            currentTime = Math.max(currentTime, serving.arrivalTime);

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

        return completed;
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
    int actual;

    public PredictionAndActualPair(double prediction, int actual) {
        this.prediction = prediction;
        this.actual = actual;
    }

    double getNextPrediction(double alpha) {
        return prediction * (1 - alpha) + alpha * actual;
    }
}