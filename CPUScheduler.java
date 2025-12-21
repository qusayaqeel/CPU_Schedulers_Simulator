import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.util.regex.*;

public class CPUScheduler {

    // ==========================================
    // 1. Process Class
    // ==========================================
    static class Process {
        String name;
        int arrivalTime;
        int burstTime;
        int priority;
        int quantum; 
        
        // Dynamic variables
        int remainingTime;
        int agQuantum;
        int executedInCurrentCycle; 
        
        int completionTime;
        int waitingTime;
        int turnaroundTime;
        
        // Priority Logic Specific
        int lastUpdate; 
        
        List<Integer> quantumHistory = new ArrayList<>(); 

        public Process(String name, int arr, int burst, int prio, int quant) {
            this.name = name;
            this.arrivalTime = arr;
            this.burstTime = burst;
            this.priority = prio;
            this.quantum = quant;
            this.remainingTime = burst;
            
            this.agQuantum = quant;
            this.executedInCurrentCycle = 0;
            this.lastUpdate = 0; 
            this.quantumHistory.add(quant);
        }

        public Process(Process p) {
            this(p.name, p.arrivalTime, p.burstTime, p.priority, p.quantum);
        }
    }

    // ==========================================
    // 2. Main Method
    // ==========================================
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("==========================================");
        System.out.println("      OS Assignment 3 - CPU Scheduler     ");
        System.out.println("==========================================\n");

        while(true) {
            System.out.print("\nEnter JSON filename (e.g., test.json) or 'exit': ");
            String filename = scanner.nextLine().trim();

            if (filename.equalsIgnoreCase("exit")) break;

            try {
                File f = new File(filename);
                if (!f.exists()) {
                    System.out.println("Error: File not found.");
                    continue;
                }
                
                String content = new String(Files.readAllBytes(Paths.get(filename)));
                
                int contextSwitch = parseJsonInt(content, "contextSwitch", 0);
                int rrQuantum = parseJsonInt(content, "rrQuantum", 2); 
                int agingInterval = parseJsonInt(content, "agingInterval", 1);
                
                List<Process> processes = parseProcesses(content);
                
                if(processes.isEmpty()) {
                    System.out.println("Error: No processes found.");
                    continue;
                }

                boolean isAGTest = filename.toLowerCase().contains("ag") || content.contains("\"quantum\"");

                if (isAGTest) {
                    runAG(cloneList(processes));
                } else {
                    System.out.println("\n>>> Running Standard Algorithms <<<");
                    System.out.println("Config: [CS: " + contextSwitch + "] [RR: " + rrQuantum + "] [Aging: " + agingInterval + "]");
                    
                    System.out.println("1. SJF (Preemptive)");
                    System.out.println("2. Round Robin");
                    System.out.println("3. Priority (Preemptive + Aging)");
                    System.out.println("4. Run ALL");
                    System.out.print("Choice: ");
                    String choice = scanner.nextLine();

                    switch (choice) {
                        case "1": runSJF(cloneList(processes), contextSwitch); break;
                        case "2": runRR(cloneList(processes), contextSwitch, rrQuantum); break;
                        case "3": runPriority(cloneList(processes), contextSwitch, agingInterval); break;
                        case "4": 
                            runSJF(cloneList(processes), contextSwitch);
                            runRR(cloneList(processes), contextSwitch, rrQuantum);
                            runPriority(cloneList(processes), contextSwitch, agingInterval);
                            break;
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Error: " + e.getMessage());
            }
        }
        scanner.close();
    }

    // ========================================================
    // ALGORITHM 3: Priority (Corrected Logic)
    // ========================================================
    public static void runPriority(List<Process> processes, int contextSwitch, int agingInterval) {
        System.out.println("\n========================================================");
        System.out.println("           Priority Scheduler (Aging=" + agingInterval + ")");
        System.out.println("========================================================");
        
        PriorityQueue<Process> readyQueue = new PriorityQueue<>(
            Comparator.comparingInt((Process p) -> p.priority)
                      .thenComparingInt(p -> p.arrivalTime)
                      .thenComparing(p -> p.name)
        );

        int time = 0;
        int completed = 0;
        Process current = null;
        Process lastExecutedProcess = null;
        List<String> executionOrder = new ArrayList<>();
        String lastAddedToOrder = "";
        Set<Process> arrivedSet = new HashSet<>();

        while (completed < processes.size()) {
            // 1. Add Arrivals
            for (Process p : processes) {
                if (p.arrivalTime == time && p.remainingTime == p.burstTime && !arrivedSet.contains(p)) {
                    p.lastUpdate = time;
                    readyQueue.add(p);
                    arrivedSet.add(p);
                }
            }

            // 2. Apply Aging
            if (agingInterval > 0) {
                boolean changed = false;
                List<Process> temp = new ArrayList<>(readyQueue);
                for (Process p : temp) {
                    int waited = time - p.lastUpdate;
                    if (waited > 0 && waited % agingInterval == 0) {
                        if (p.priority > 1) { 
                            p.priority--;
                            p.lastUpdate = time;
                            changed = true;
                        }
                    }
                }
                if (changed) {
                    readyQueue.clear();
                    readyQueue.addAll(temp);
                }
            }

            // 3. Preemption Check
            if (current != null && !readyQueue.isEmpty()) {
                Process best = readyQueue.peek();
                if (shouldPreempt(best, current)) {
                    current.lastUpdate = time;
                    readyQueue.add(current);
                    current = null;
                }
            }

            // 4. Selection
            if (current == null) {
                if (readyQueue.isEmpty()) {
                    time++;
                    continue;
                }

                while (true) {
                    Process candidate = readyQueue.poll();

                    // Context Switch Logic
                    if (lastExecutedProcess != null && candidate != lastExecutedProcess && contextSwitch > 0) {
                        for (int i = 0; i < contextSwitch; i++) {
                            time++;
                            // Check arrivals during CS
                            for (Process p : processes) {
                                if (p.arrivalTime == time && !arrivedSet.contains(p)) {
                                    p.lastUpdate = time;
                                    readyQueue.add(p);
                                    arrivedSet.add(p);
                                }
                            }
                            // Aging during CS
                            if (agingInterval > 0) {
                                boolean changedCS = false;
                                List<Process> tempCS = new ArrayList<>(readyQueue);
                                for (Process p : tempCS) {
                                    int waited = time - p.lastUpdate;
                                    if (waited > 0 && waited % agingInterval == 0) {
                                        if (p.priority > 1) {
                                            p.priority--;
                                            p.lastUpdate = time;
                                            changedCS = true;
                                        }
                                    }
                                }
                                if (changedCS) {
                                    readyQueue.clear();
                                    readyQueue.addAll(tempCS);
                                }
                            }
                        }
                    }

                    // Re-evaluate
                    if (!readyQueue.isEmpty() && shouldPreempt(readyQueue.peek(), candidate)) {
                        candidate.lastUpdate = time; 
                        readyQueue.add(candidate); 
                        continue; 
                    }

                    current = candidate;
                    break;
                }
            }

            // 5. Execution
            if (current != null) {
                if (!current.name.equals(lastAddedToOrder)) {
                    executionOrder.add(current.name);
                    lastAddedToOrder = current.name;
                }

                current.remainingTime--;
                lastExecutedProcess = current;
                time++;

                if (current.remainingTime == 0) {
                    completed++;
                    current.completionTime = time;
                    current.turnaroundTime = time - current.arrivalTime;
                    current.waitingTime = current.turnaroundTime - current.burstTime;
                    current = null;
                }
            }
        }
        
        printResults(processes, executionOrder, false);
    }

    private static boolean shouldPreempt(Process best, Process running) {
        if (best.priority < running.priority) return true;
        if (best.priority == running.priority) {
            if (best.arrivalTime < running.arrivalTime) return true;
            if (best.arrivalTime == running.arrivalTime) {
                return best.name.compareTo(running.name) < 0;
            }
        }
        return false;
    }

    // ========================================================
    // ALGORITHM 4: AG Scheduler (Official Logic)
    // ========================================================
    public static void runAG(List<Process> processes) {
        System.out.println("\n========================================================");
        System.out.println("               AG Scheduler Simulation");
        System.out.println("========================================================");

        LinkedList<Process> readyQueue = new LinkedList<>();
        List<Process> completedProcesses = new ArrayList<>();
        List<String> order = new ArrayList<>();
        
        processes.sort(Comparator.comparingInt(p -> p.arrivalTime));

        int time = 0;
        Process active = null;
        int processIndex = 0;

        while (completedProcesses.size() < processes.size()) {

            while (processIndex < processes.size() && processes.get(processIndex).arrivalTime <= time) {
                readyQueue.add(processes.get(processIndex));
                processIndex++;
            }

            if (active == null) {
                if (!readyQueue.isEmpty()) {
                    active = readyQueue.poll();
                    active.executedInCurrentCycle = 0; 
                    order.add(active.name);
                } else {
                    time++;
                    continue; 
                }
            }

            int q = active.agQuantum;
            int q25 = (int) Math.ceil(q * 0.25);
            int q50 = (int) Math.ceil(q * 0.50);
            
            active.remainingTime--;
            active.executedInCurrentCycle++;
            time++;

            if (active.remainingTime == 0) {
                active.completionTime = time;
                active.turnaroundTime = active.completionTime - active.arrivalTime;
                active.waitingTime = active.turnaroundTime - active.burstTime;
                active.agQuantum = 0;
                active.quantumHistory.add(0);
                completedProcesses.add(active);
                active = null;
                continue;
            }

            if (active.executedInCurrentCycle == q) {
                active.agQuantum += 2;
                active.quantumHistory.add(active.agQuantum);
                readyQueue.add(active);
                active = null;
                continue;
            }

            if (readyQueue.isEmpty()) continue;

            if (active.executedInCurrentCycle == q25) {
                Process bestPrio = null;
                int minPrio = active.priority;
                for(Process p : readyQueue) {
                    if(p.priority < minPrio) {
                        minPrio = p.priority;
                        bestPrio = p;
                    }
                }
                if (bestPrio != null) {
                    int remainingQ = q - active.executedInCurrentCycle;
                    active.agQuantum += (int) Math.ceil(remainingQ / 2.0);
                    active.quantumHistory.add(active.agQuantum);
                    readyQueue.add(active);
                    active = bestPrio;
                    readyQueue.remove(active);
                    active.executedInCurrentCycle = 0;
                    order.add(active.name);
                    continue;
                }
            }

            if (active.executedInCurrentCycle >= q50) {
                Process bestSJF = null;
                int minRem = active.remainingTime;
                for(Process p : readyQueue) {
                    if(p.remainingTime < minRem) {
                        minRem = p.remainingTime;
                        bestSJF = p;
                    }
                }
                if (bestSJF != null) {
                    int remainingQ = q - active.executedInCurrentCycle;
                    active.agQuantum += remainingQ;
                    active.quantumHistory.add(active.agQuantum);
                    readyQueue.add(active);
                    active = bestSJF;
                    readyQueue.remove(active);
                    active.executedInCurrentCycle = 0;
                    order.add(active.name);
                    continue;
                }
            }
        }
        printResults(completedProcesses, order, true);
    }

    // ==========================================
    // Helper Methods & Other Algos
    // ==========================================

    public static void runSJF(List<Process> processes, int contextSwitch) {
        System.out.println("\n========================================================");
        System.out.println("               SJF Scheduler (Preemptive)");
        System.out.println("========================================================");
        int time = 0;
        int completed = 0;
        Process active = null;
        List<String> order = new ArrayList<>();
        List<Process> ready = new ArrayList<>();
        processes.sort(Comparator.comparingInt(p -> p.arrivalTime));

        while(completed < processes.size()) {
            checkArrivals(processes, ready, time);
            
            Process shortest = null;
            int minRem = Integer.MAX_VALUE;
            
            for(Process p : ready) {
                if(p.remainingTime < minRem) {
                    minRem = p.remainingTime;
                    shortest = p;
                }
            }

            if(shortest != null && shortest != active) {
                if(active != null || time > 0) {
                     time = advanceTime(time, contextSwitch, processes, ready);
                }
                active = shortest;
            }

            if(active != null) {
                order.add(active.name);
                active.remainingTime--;
                time = advanceTime(time, 1, processes, ready);

                if(active.remainingTime == 0) {
                    active.completionTime = time;
                    active.turnaroundTime = active.completionTime - active.arrivalTime;
                    active.waitingTime = active.turnaroundTime - active.burstTime;
                    completed++;
                    ready.remove(active);
                    active = null;
                }
            } else {
                time = advanceTime(time, 1, processes, ready);
            }
        }
        printResults(processes, order, false);
    }

    public static void runRR(List<Process> processes, int contextSwitch, int quantum) {
        System.out.println("\n========================================================");
        System.out.println("               Round Robin (Quantum=" + quantum + ")");
        System.out.println("========================================================");
        int time = 0;
        int completed = 0;
        List<String> order = new ArrayList<>();
        Queue<Process> queue = new LinkedList<>();
        
        processes.sort(Comparator.comparingInt(p -> p.arrivalTime));
        int pIdx = 0;

        while(pIdx < processes.size() && processes.get(pIdx).arrivalTime <= time) {
            queue.add(processes.get(pIdx));
            pIdx++;
        }

        while(completed < processes.size()) {
            if(queue.isEmpty()) {
                if(pIdx < processes.size()) {
                    time = processes.get(pIdx).arrivalTime;
                    while(pIdx < processes.size() && processes.get(pIdx).arrivalTime <= time) {
                        queue.add(processes.get(pIdx));
                        pIdx++;
                    }
                } else {
                    break;
                }
            }

            Process current = queue.poll();
            order.add(current.name);

            int executeTime = Math.min(current.remainingTime, quantum);
            current.remainingTime -= executeTime;
            time += executeTime;
            
            while(pIdx < processes.size() && processes.get(pIdx).arrivalTime <= time) {
                queue.add(processes.get(pIdx));
                pIdx++;
            }

            if(current.remainingTime > 0) {
                queue.add(current);
            } else {
                current.completionTime = time;
                current.turnaroundTime = current.completionTime - current.arrivalTime;
                current.waitingTime = current.turnaroundTime - current.burstTime;
                completed++;
            }

            if(completed < processes.size()) {
                time += contextSwitch;
                while(pIdx < processes.size() && processes.get(pIdx).arrivalTime <= time) {
                    queue.add(processes.get(pIdx));
                    pIdx++;
                }
            }
        }
        printResults(processes, order, false);
    }

    // ==========================================
    // UPDATED TABLE PRINTING
    // ==========================================
    public static void printResults(List<Process> list, List<String> order, boolean isAG) {
        // 1. Print Execution Order
        System.out.println("\nExecution Order:");
        if(!order.isEmpty()) {
            System.out.print("[ " + order.get(0));
            for(int i=1; i<order.size(); i++) {
                if(!order.get(i).equals(order.get(i-1))) {
                    System.out.print(" -> " + order.get(i));
                }
            }
            System.out.println(" ]");
        }

        // 2. Prepare Table
        System.out.println("\nProcess Execution Details:");
        String format;
        if (isAG) {
            System.out.println("+------------+-------------+------------+-------------+------------------+-----------------+----------------------------------------+");
            System.out.printf("| %-10s | %-11s | %-10s | %-11s | %-16s | %-15s | %-38s |%n", "Process", "Arrival", "Burst", "Priority", "Waiting Time", "Turnaround", "Quantum History");
            System.out.println("+------------+-------------+------------+-------------+------------------+-----------------+----------------------------------------+");
            format = "| %-10s | %-11d | %-10d | %-11d | %-16d | %-15d | %-38s |%n";
        } else {
            System.out.println("+------------+-------------+------------+-------------+------------------+-----------------+");
            System.out.printf("| %-10s | %-11s | %-10s | %-11s | %-16s | %-15s |%n", "Process", "Arrival", "Burst", "Priority", "Waiting Time", "Turnaround");
            System.out.println("+------------+-------------+------------+-------------+------------------+-----------------+");
            format = "| %-10s | %-11d | %-10d | %-11d | %-16d | %-15d |%n";
        }

        list.sort(Comparator.comparing(p -> p.name));
        double totalWait = 0, totalTurn = 0;

        for (Process p : list) {
            totalWait += p.waitingTime;
            totalTurn += p.turnaroundTime;
            if (isAG) {
                System.out.printf(format, p.name, p.arrivalTime, p.burstTime, p.priority, p.waitingTime, p.turnaroundTime, p.quantumHistory.toString());
            } else {
                System.out.printf(format, p.name, p.arrivalTime, p.burstTime, p.priority, p.waitingTime, p.turnaroundTime);
            }
        }

        // 3. Footer & Averages
        if (isAG) {
            System.out.println("+------------+-------------+------------+-------------+------------------+-----------------+----------------------------------------+");
        } else {
            System.out.println("+------------+-------------+------------+-------------+------------------+-----------------+");
        }

        System.out.printf("\nAverage Waiting Time:    %.2f%n", totalWait / list.size());
        System.out.printf("Average Turnaround Time: %.2f%n", totalTurn / list.size());
        System.out.println("====================================================================================================\n");
    }

    // --- Utils ---
    public static List<Process> cloneList(List<Process> list) {
        List<Process> n = new ArrayList<>();
        for(Process p : list) n.add(new Process(p));
        return n;
    }
    
    public static int advanceTime(int current, int amount, List<Process> all, List<Process> queue) {
        for(int i=0; i<amount; i++) {
            current++;
            checkArrivals(all, queue, current);
        }
        return current;
    }

    public static void checkArrivals(List<Process> all, List<Process> queue, int time) {
        for(Process p : all) {
            if(p.arrivalTime <= time && p.remainingTime > 0) {
                if(!queue.contains(p)) queue.add(p);
            }
        }
    }

    private static int parseJsonInt(String content, String key, int defaultValue) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(content);
        if (m.find()) return Integer.parseInt(m.group(1));
        return defaultValue;
    }

    private static List<Process> parseProcesses(String content) {
        List<Process> list = new ArrayList<>();
        String minified = content.replaceAll("\\s+", "");
        int start = minified.indexOf("\"processes\":[");
        if(start == -1) return list;
        int end = minified.indexOf("]", start);
        String arrayInner = minified.substring(start + 13, end);
        String[] rawObjs = arrayInner.split("\\},\\{");
        
        for(String raw : rawObjs) {
            raw = raw.replace("{", "").replace("}", "");
            String name = extractStr(raw, "name");
            int arr = extractInt(raw, "arrival");
            int burst = extractInt(raw, "burst");
            int prio = extractInt(raw, "priority");
            int quant = extractInt(raw, "quantum"); 
            list.add(new Process(name, arr, burst, prio, quant));
        }
        return list;
    }

    private static String extractStr(String src, String key) {
        Pattern p = Pattern.compile("\"" + key + "\":\"([^\"]+)\"");
        Matcher m = p.matcher(src);
        return m.find() ? m.group(1) : "";
    }

    private static int extractInt(String src, String key) {
        Pattern p = Pattern.compile("\"" + key + "\":(\\d+)");
        Matcher m = p.matcher(src);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }
}