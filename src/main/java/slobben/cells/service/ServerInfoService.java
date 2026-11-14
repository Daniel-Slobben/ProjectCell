package slobben.cells.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ServerInfoService {

    private static final int MAX_ENTRIES = 1000;
    private final List<Long> timeDeltas = new ArrayList<>(MAX_ENTRIES);
    private final List<Integer> blockCounts = new ArrayList<>(MAX_ENTRIES);
    private final Map<String, List<Long>> workTimes = new LinkedHashMap<>();

    public double getAverageTimeOffTarget() {
        return getAverage(timeDeltas);
    }

    public double getAverageBlockCount() {
        return getAverage(blockCounts);
    }

    public void updateBlockCounts(int blockCount) {
        addToList(blockCounts, blockCount);
    }

    public void updateTimeDeltaRecordings(long freeTime) {
        addToList(timeDeltas, freeTime);
    }

    public List<Long> getLastTimeDeltas(int amount) {
        return timeDeltas.subList(Math.max(0, timeDeltas.size() - amount), timeDeltas.size());
    }

    public synchronized void recordWorkTime(String worker, long nanos) {
        addToList(workTimes.computeIfAbsent(worker, _ -> new ArrayList<>(MAX_ENTRIES)), nanos);
    }

    public synchronized Map<String, Double> getAverageWorkTimes() {
        Map<String, Double> averages = new LinkedHashMap<>();
        workTimes.forEach((worker, times) -> averages.put(worker, getAverage(times) / 1_000_000));
        return averages;
    }

    private double getAverage(List<? extends Number> list) {
        return list.stream()
                .mapToDouble(Number::doubleValue)
                .average()
                .orElse(0.0);
    }

    private <T> void addToList(List<T> list, T toAdd) {
        list.add(toAdd);
        if (list.size() > MAX_ENTRIES) {
            list.removeFirst();
        }
    }
}
