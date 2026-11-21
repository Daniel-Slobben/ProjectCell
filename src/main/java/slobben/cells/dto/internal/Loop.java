package slobben.cells.dto.internal;

import lombok.Getter;

import java.util.List;

public final class Loop {
    @Getter
    private final List<Phase> phases;
    @Getter
    private final Outline received;
    private int phase;

    public Loop(List<Phase> phases, int size) {
        this.phases = List.copyOf(phases);
        this.received = Outline.empty(size);
    }

    public Phase getCurrentPhase() {
        return phases.get(phase);
    }

    public void next() {
        phase = (phase + 1) % phases.size();
    }

    public record Phase(byte[] full, byte[] border, byte[] sectors, short[][] sectorCounts, Outline edges,
                        Outline borders) {
    }
}
