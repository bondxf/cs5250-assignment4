import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Created by xufeng on 3/4/19.
 */
public class Simulator {
    public static void main(String[] args) throws FileNotFoundException {
        Scanner in = new Scanner(new File("input.txt"));
        List<Process> processList = new ArrayList<>();
        while(in.hasNextLine()) {
            String[] tokens = in.nextLine().trim().split(" ");
            int id = Integer.parseInt(tokens[0]);
            int arrivalTime = Integer.parseInt(tokens[1]);
            int burstTime = Integer.parseInt(tokens[2]);
            processList.add(new Process(id, arrivalTime, burstTime));
        }
        in.close();
    }
}

class Process {
    int id, arrivalTime, burstTime;

    public Process(int id, int arrivalTime, int burstTime) {
        this.id = id;
        this.arrivalTime = arrivalTime;
        this.burstTime = burstTime;
    }
}
