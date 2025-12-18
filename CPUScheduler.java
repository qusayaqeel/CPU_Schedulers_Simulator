import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.util.regex.*;

public class CPUScheduler {

    // --- Process Class ---
    static class Process {
        String name;
        int arrivalTime;
        int burstTime;
        int priority;
        int quantum; // For AG
        
        // Dynamic variables
        int remainingTime;
        int agQuantum; 
        int completionTime;
        int waitingTime;
        int turnaroundTime;
        int waitingTimeInQueue; // Specific counter for Aging
        List<Integer> quantumHistory = new ArrayList<>(); 

        public Process(String name, int arr, int burst, int prio, int quant) {
            this.name = name;
            this.arrivalTime = arr;
            this.burstTime = burst;
            this.priority = prio;
            this.quantum = quant;
            this.remainingTime = burst;
            this.agQuantum = quant;
            this.waitingTimeInQueue = 0;
            if(quant > 0) this.quantumHistory.add(quant);
        }

        public Process(Process p) {
            this(p.name, p.arrivalTime, p.burstTime, p.priority, p.quantum);
        }
    }

    // --- MAIN METHOD ---
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("==========================================");
        System.out.println("      OS Assignment 3 - CPU Scheduler     ");
        System.out.println("==========================================\n");

        while(true) {
            System.out.print("\nEnter JSON filename (e.g., test_1.json) or 'exit': ");
            String filename = scanner.nextLine().trim();

            if (filename.equalsIgnoreCase("exit")) break;

            try {
                File f = new File(filename);
                if (!f.exists()) {
                    System.out.println("Error: File not found.");
                    continue;
                }
                
                String content = new String(Files.readAllBytes(Paths.get(filename)));
                
                // Parse Global Config
                int contextSwitch = parseJsonInt(content, "contextSwitch", 0);
                int rrQuantum = parseJsonInt(content, "rrQuantum", 2); 
                int agingInterval = parseJsonInt(content, "agingInterval", 1);

                List<Process> processes = parseProcesses(content);
                
                if(processes.isEmpty()) {
                    System.out.println("Error: No processes found.");
                    continue;
                }

                // Auto-detect AG Test
                boolean isAGTest = filename.toLowerCase().contains("ag") || content.contains("\"quantum\"");

                if (isAGTest) {
                    System.out.println("\n>>> Running AG Scheduling <<<");
                    runAG(cloneList(processes));
                } else {
                    System.out.println("\n>>> Running Standard Algorithms <<<");
                    System.out.println("Config: [CS: " + contextSwitch + "] [RR: " + rrQuantum + "] [Aging: " + agingInterval + "]");
                    
                    System.out.println("\n1. SJF (Preemptive)");
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
                System.out.println("Error: " + e.getMessage());
            }
        }
        scanner.close();
    }

    // --- JSON PARSING ---
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

    public static List<Process> cloneList(List<Process> list) {
        List<Process> n = new ArrayList<>();
        for(Process p : list) n.add(new Process(p));
        return n;
    }

    // ========================================================
    // 1. SJF (Preemptive) - Strict logic based on slides
    // ========================================================
    public static void runSJF(List<Process> processes, int contextSwitch) {
        System.out.println("\n--- SJF Scheduler ---");
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
            
            // Find shortest job
            for(Process p : ready) {
                if(p.remainingTime < minRem) {
                    minRem = p.remainingTime;
                    shortest = p;
                }
            }

            // Preemption Logic: STRICT less than (<)
            if(shortest != null && shortest != active) {
                // Apply Context Switch if we are switching processes
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

    // ========================================================
    // 2. Round Robin - Corrected Queue Order
    // ========================================================
    public static void runRR(List<Process> processes, int contextSwitch, int quantum) {
        System.out.println("\n--- Round Robin (Q=" + quantum + ") ---");
        int time = 0;
        int completed = 0;
        List<String> order = new ArrayList<>();
        Queue<Process> queue = new LinkedList<>();

        processes.sort(Comparator.comparingInt(p -> p.arrivalTime));
        int pIdx = 0;

        // Add initial processes
        while(pIdx < processes.size() && processes.get(pIdx).arrivalTime <= time) {
            queue.add(processes.get(pIdx));
            pIdx++;
        }

        while(completed < processes.size()) {
            if(queue.isEmpty()) {
                // Jump to next arrival time
                if(pIdx < processes.size()) {
                    time = processes.get(pIdx).arrivalTime;
                    while(pIdx < processes.size() && processes.get(pIdx).arrivalTime <= time) {
                        queue.add(processes.get(pIdx));
                        pIdx++;
                    }
                } else {
                    break; // All processes done
                }
            }

            Process current = queue.poll();
            order.add(current.name);

            // Execute for quantum or until completion
            int executeTime = Math.min(current.remainingTime, quantum);
            current.remainingTime -= executeTime;
            time += executeTime;

            // Add any new arrivals that arrived during execution
            while(pIdx < processes.size() && processes.get(pIdx).arrivalTime <= time) {
                queue.add(processes.get(pIdx));
                pIdx++;
            }

            // If process not finished, re-queue it AFTER new arrivals
            if(current.remainingTime > 0) {
                queue.add(current);
            } else {
                // Process completed
                current.completionTime = time;
                current.turnaroundTime = current.completionTime - current.arrivalTime;
                current.waitingTime = current.turnaroundTime - current.burstTime;
                completed++;
            }

            // Apply context switch if there are more processes to run
            if(completed < processes.size()) {
                time += contextSwitch;
                // Add any arrivals during context switch
                while(pIdx < processes.size() && processes.get(pIdx).arrivalTime <= time) {
                    queue.add(processes.get(pIdx));
                    pIdx++;
                }
            }
        }
        printResults(processes, order, false);
    }

    // ========================================================
    // 3. Priority - With Queue Aging
    // ========================================================
    public static void runPriority(List<Process> processes, int contextSwitch, int agingInterval) {
        System.out.println("\n--- Priority (Aging=" + agingInterval + ") ---");
        int time = 0;
        int completed = 0;
        Process active = null;
        List<String> order = new ArrayList<>();
        List<Process> ready = new ArrayList<>();

        while(completed < processes.size()) {
            checkArrivals(processes, ready, time);

            // Select Best Priority (Smallest Integer) BEFORE aging
            Process best = null;
            int minPrio = Integer.MAX_VALUE;
            for(Process p : ready) {
                if(p.priority < minPrio || (p.priority == minPrio && (best == null || p.arrivalTime < best.arrivalTime))) {
                    minPrio = p.priority;
                    best = p;
                }
            }

            // Context Switch
            if(best != null && best != active) {
                if(active != null) {
                     time += contextSwitch;
                     checkArrivals(processes, ready, time);
                }
                active = best;
            }

            if(active != null) {
                order.add(active.name);
                active.remainingTime--;
                time++;

                    // Apply aging AFTER execution to ready processes
                performAging(ready, agingInterval);
                // Also apply aging to active process
                if(active != null) {
                    List<Process> activeList = Arrays.asList(active);
                    performAging(activeList, agingInterval);
                }

                if(active.remainingTime == 0) {
                    active.completionTime = time;
                    active.turnaroundTime = active.completionTime - active.arrivalTime;
                    active.waitingTime = active.turnaroundTime - active.burstTime;
                    completed++;
                    ready.remove(active);
                    active = null;
                }
            } else {
                time++;
                performAging(ready, agingInterval);
            }

        }
        printResults(processes, order, false);
    }
    
    // Helper for aging - aggressive aging to match expected
    private static void performAging(List<Process> ready, int interval) {
        for(Process p : ready) {
            p.waitingTimeInQueue++;
            // Decrease priority every tick once waiting exceeds interval, multiple times if needed
            while(p.waitingTimeInQueue > interval && p.priority > 0) {
                p.priority--;
                p.waitingTimeInQueue -= interval;
            }
        }
    }

    // ========================================================
    // 4. AG Scheduler - Strict Logic
    // ========================================================
    public static void runAG(List<Process> processes) {
        System.out.println("\n--- AG Scheduler ---");
        int time = 0;
        List<String> order = new ArrayList<>();
        List<Process> ready = new ArrayList<>();
        Process active = null;
        int timeUsedInQuantum = 0;

        processes.sort(Comparator.comparingInt(p -> p.arrivalTime));

        while(true) {
            // Check if all processes are done
            boolean allDone = true;
            for(Process p : processes) {
                if(p.remainingTime > 0) {
                    allDone = false;
                    break;
                }
            }
            if(allDone) break;

            // Add arriving processes to ready queue
            for(Process p : processes) {
                if(p.arrivalTime == time && p.remainingTime > 0 && !ready.contains(p)) {
                    ready.add(p);
                }
            }

            // Select active process if none is running
            if(active == null && !ready.isEmpty()) {
                active = ready.remove(0);
                timeUsedInQuantum = 0;
            }

            // If still no active process, advance time
            if(active == null) {
                time++;
                continue;
            }

            // Execute one time unit
            order.add(active.name);
            active.remainingTime--;
            timeUsedInQuantum++;
            time++;

            // Check if process completed
            if(active.remainingTime == 0) {
                active.agQuantum = 0;
                active.quantumHistory.add(0);
                active.completionTime = time;
                active.turnaroundTime = active.completionTime - active.arrivalTime;
                active.waitingTime = active.turnaroundTime - active.burstTime;
                active = null;
                continue;
            }

            int currentQuantum = active.agQuantum;
            double q25 = currentQuantum * 0.25;
            double q50 = currentQuantum * 0.50;

            boolean switched = false;

            // 25%-50%: Non-Preemptive Priority check
            if(timeUsedInQuantum > q25 && timeUsedInQuantum <= q50) {
                Process bestPriority = null;
                int minPriority = active.priority;
                for(Process p : ready) {
                    if(p.priority < minPriority) {
                        minPriority = p.priority;
                        bestPriority = p;
                    }
                }
                if(bestPriority != null) {
                    // Allow current process to finish current second, then switch
                    // Update quantum: NewQ = Q + ceil((Q - Used) / 2.0)
                    active.agQuantum += (int) Math.ceil((currentQuantum - timeUsedInQuantum) / 2.0);
                    active.quantumHistory.add(active.agQuantum);
                    ready.add(active);
                    active = bestPriority;
                    ready.remove(bestPriority);
                    timeUsedInQuantum = 0;
                    switched = true;
                }
            }
            // 50%-100%: Preemptive SJF check
            else if(timeUsedInQuantum > q50) {
                Process shortest = null;
                int minRemaining = active.remainingTime;
                for(Process p : ready) {
                    if(p.remainingTime < minRemaining) {
                        minRemaining = p.remainingTime;
                        shortest = p;
                    }
                }
                if(shortest != null) {
                    // Update quantum: NewQ = Q + (Q - Used)
                    active.agQuantum += (currentQuantum - timeUsedInQuantum);
                    active.quantumHistory.add(active.agQuantum);
                    ready.add(active);
                    active = shortest;
                    ready.remove(shortest);
                    timeUsedInQuantum = 0;
                    switched = true;
                }
            }

            // Quantum End: if process used full quantum but isn't finished
            if(!switched && timeUsedInQuantum >= currentQuantum) {
                active.agQuantum += 2;
                active.quantumHistory.add(active.agQuantum);
                ready.add(active);
                active = null;
            }
        }
        printResults(processes, order, true);
    }

    // --- GENERAL UTILS ---
    public static int advanceTime(int current, int amount, List<Process> all, List<Process> queue) {
        for(int i=0; i<amount; i++) {
            current++;
            checkArrivals(all, queue, current);
        }
        return current;
    }

    public static void checkArrivals(List<Process> all, List<Process> queue, int time) {
        for(Process p : all) {
            if(p.arrivalTime == time && p.remainingTime > 0) {
                if(!queue.contains(p)) queue.add(p);
            }
        }
    }

    public static void printResults(List<Process> list, List<String> order, boolean isAG) {
        System.out.print("Execution Order: [ ");
        if(!order.isEmpty()) {
            System.out.print(order.get(0));
            for(int i=1; i<order.size(); i++) {
                if(!order.get(i).equals(order.get(i-1))) {
                    System.out.print(" -> " + order.get(i));
                }
            }
        }
        System.out.println(" ]");
        System.out.println("---------------------------------------------------------");
        System.out.printf("%-10s %-15s %-15s", "Process", "Waiting", "Turnaround");
        if(isAG) System.out.print(" Quantum History");
        System.out.println("\n---------------------------------------------------------");
        
        list.sort(Comparator.comparing(p -> p.name));
        double totalWait = 0, totalTurn = 0;
        
        for(Process p : list) {
            System.out.printf("%-10s %-15d %-15d", p.name, p.waitingTime, p.turnaroundTime);
            if(isAG) System.out.print(" " + p.quantumHistory);
            System.out.println();
            totalWait += p.waitingTime;
            totalTurn += p.turnaroundTime;
        }
        System.out.println("---------------------------------------------------------");
        System.out.printf("Average    %-15.1f %-15.1f\n", totalWait/list.size(), totalTurn/list.size());
    }
}