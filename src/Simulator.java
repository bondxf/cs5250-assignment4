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

        Queue<Process> completion = roundRobin(processList, 10);
        for (Process process : completion) {
            System.out.println(process.id + ": " + process.finishTime);
        }
    }

    public static Queue<Process> roundRobin(Queue<Process> processList, int quantum) {
        int currentTime = 0;

        Queue<Process> queue = new LinkedList<>();
        Queue<Process> completed = new LinkedList<>();

        queue.offer(processList.poll());

        while (!queue.isEmpty()) {
            Process nextToServe = queue.poll();
            // handle the case where CPU is idle for sometime
            currentTime = Math.max(currentTime, nextToServe.arrivalTime);

            // case 1: next to serve's remaining time larger than quantum
            // -> update remaining time and put it at the end of the queue
            // case 2: next to serve's remaining time smaller than or equal to quantum
            // -> clear remaining time, mark it as complete
            // both cases: update current time

            if (nextToServe.remainingTime > quantum) {
                currentTime += quantum;
                nextToServe.remainingTime -= quantum;

                // add other processes that arrived before the next quantum starts
                while (!processList.isEmpty() && processList.peek().arrivalTime <= currentTime) {
                    queue.add(processList.poll());
                }

                queue.add(nextToServe);
            } else {
                currentTime += nextToServe.remainingTime;
                nextToServe.remainingTime = 0;
                nextToServe.finishTime = currentTime;
                completed.add(nextToServe);

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

    public Process(int id, int arrivalTime, int burstTime) {
        this.id = id;
        this.arrivalTime = arrivalTime;
        this.burstTime = burstTime;
        this.remainingTime = burstTime;
        this.finishTime = -1;
    }

    int getWaitingTime() {
        return finishTime - (arrivalTime + burstTime);
    }
}
