package slobben.cells.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ServerInfoService {

    private static final int MAX_ENTRIES = 1000;
    private final List<Long> timeDeltas = new ArrayList<>(MAX_ENTRIES);
    private final List<Integer> blockCounts = new ArrayList<>(MAX_ENTRIES);

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

    private double getAverage(List<? extends Number> list) {
        return list.stream()
                .mapToDouble(Number::intValue)
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
