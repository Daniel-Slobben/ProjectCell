package slobben.cells.service.workers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import slobben.cells.dto.incoming.ClientUpdateRequest;
import slobben.cells.dto.internal.Coordinates;
import slobben.cells.dto.outgoing.BlockFrame;
import slobben.cells.dto.outgoing.EncodedBlock;
import slobben.cells.dto.outgoing.StatusResponse;
import slobben.cells.entities.Block;
import slobben.cells.entities.Client;
import slobben.cells.service.ExecutorService;
import slobben.cells.util.BlockUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static slobben.cells.dto.outgoing.StatusResponse.Type.NO_BLOCKS;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService extends Worker {
    private static final String DESTINATION = "/queue/blocks-user%s";
    private static final int SECTOR_BLOCKS_PER_MESSAGE = 250;
    private static final int MAX_VIEW_BLOCKS = 1_000_000;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final ExecutorService executorService;
    private final Map<String, Block> blocks;
    private final Map<String, Client> clients = new ConcurrentHashMap<>();
    @Value("${cells.targetspeed}")
    private int targetSpeed;
    @Value("${cells.sectors.period-ms}")
    private int sectorPeriodMs;
    @Value("${cells.sectors.tile}")
    private int tileSize;
    @Value("${cells.max-cell-blocks}")
    private int maxCellBlocks;
    private int ticks = 0;
    private final AtomicInteger connections = new AtomicInteger();
    private volatile Map<Coordinates, byte[]> tiles = Map.of();
    private int tilesTick = Integer.MIN_VALUE;

    @Override
    public String getName() {
        return "Client updates with %s amount of clients".formatted(clients.size());
    }

    public void execute() {
        int tick = ticks++;
        int period = Math.max(1, sectorPeriodMs / Math.max(1, targetSpeed));
        Predicate<Client> due = client -> !client.isSectors() || client.getSectorPhase() % period == tick % period;
        // at most a period old, and only written when a client that watches sectors is due
        if (tick - (long) tilesTick >= period && clients.values().stream().anyMatch(client -> client.isSectors() && due.test(client))) {
            tiles = writeTiles();
            tilesTick = tick;
        }
        Set<Runnable> tasks = clients.values().stream()
                .filter(due)
                .map(client -> (Runnable) () -> {
                    if (client.isSectors()) sendTiles(client, tiles);
                    else sendClientUpdate(client, client.getActiveBlocks(), true);
                })
                .collect(Collectors.toSet());

        executorService.executeTasksParallel(tasks, getName());
    }

    private Map<Coordinates, byte[]> writeTiles() {
        Map<Coordinates, List<EncodedBlock>> sectorsPerTile = new HashMap<>();
        for (Block block : blocks.values()) {
            EncodedBlock sectors = block.getEncodedBlockSectors();
            if (sectors != null) {
                Coordinates tile = new Coordinates(Math.floorDiv(block.getX(), tileSize), Math.floorDiv(block.getY(), tileSize));
                sectorsPerTile.computeIfAbsent(tile, key -> new ArrayList<>()).add(sectors);
            }
        }
        Map<Coordinates, byte[]> newTiles = new HashMap<>();
        sectorsPerTile.forEach((tile, sectors) -> newTiles.put(tile, BlockFrame.write(sectors)));
        return newTiles;
    }

    // every tile with a block in view, so also blocks around the view
    private void sendTiles(Client client, Map<Coordinates, byte[]> tiles) {
        ClientUpdateRequest view = client.getLastUpdateRequest();
        for (int tileX = Math.floorDiv(view.minX(), tileSize); tileX <= Math.floorDiv(view.maxX(), tileSize); tileX++) {
            for (int tileY = Math.floorDiv(view.minY(), tileSize); tileY <= Math.floorDiv(view.maxY(), tileSize); tileY++) {
                byte[] tile = tiles.get(new Coordinates(tileX, tileY));
                if (tile != null) send(client, tile);
            }
        }
    }

    private void sendSectors(Client client, List<Block> watched) {
        List<EncodedBlock> sectors = watched.stream().map(Block::getEncodedBlockSectors).filter(Objects::nonNull).toList();
        for (int i = 0; i < sectors.size(); i += SECTOR_BLOCKS_PER_MESSAGE) {
            send(client, BlockFrame.write(sectors.subList(i, Math.min(i + SECTOR_BLOCKS_PER_MESSAGE, sectors.size()))));
        }
    }

    // The blocks in view, but not in exclude if there is one. A view of more blocks than the world has looks at the world.
    private List<Block> blocksIn(ClientUpdateRequest view, ClientUpdateRequest exclude) {
        if (view.blockCount() > blocks.size()) {
            return blocks.values().stream()
                    .filter(block -> inView(view, block.getX(), block.getY()) && (exclude == null || !inView(exclude, block.getX(), block.getY())))
                    .toList();
        }
        return keysIn(view, exclude).stream().map(blocks::get).filter(Objects::nonNull).toList();
    }

    private static List<String> keysIn(ClientUpdateRequest view, ClientUpdateRequest exclude) {
        List<String> keys = new ArrayList<>();
        // long, so a view up to Integer.MAX_VALUE ends
        for (long x = view.minX(); x <= view.maxX(); x++) {
            for (long y = view.minY(); y <= view.maxY(); y++) {
                if (exclude == null || !inView(exclude, (int) x, (int) y)) keys.add(BlockUtils.getKey((int) x, (int) y));
            }
        }
        return keys;
    }

    private static boolean inView(ClientUpdateRequest view, int x, int y) {
        return x >= view.minX() && x <= view.maxX() && y >= view.minY() && y <= view.maxY();
    }

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        String sessionId = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        Client client = new Client(sessionId);
        client.setSectorPhase(connections.getAndIncrement());
        clients.put(sessionId, client);
    }

    // Also comes when the broker closes a session that stopped sending heartbeats.
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        clients.remove(event.getSessionId());
    }

    public void sendClientUpdate(Client client, Collection<String> blockKeys, boolean sendBorderBlocks) {
        List<Block> watched = blockKeys.stream().map(blocks::get).filter(Objects::nonNull).toList();
        if (sendBorderBlocks) {
            send(client, BlockFrame.write(watched.stream().map(Block::getEncodedBlockBorders).filter(Objects::nonNull).toList()));
        } else {
            try (var virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (Block block : watched) {
                    virtualExecutor.execute(() -> {
                        EncodedBlock encoded = block.getEncodedBlock();
                        if (encoded != null) send(client, BlockFrame.write(List.of(encoded)));
                    });
                }
            }
        }
    }

    private void send(Client client, Object payload) {
        simpMessagingTemplate.convertAndSend(DESTINATION.formatted(client.getSessionId()), payload);
    }

    public void updateClientBlocks(String sessionId, ClientUpdateRequest view) {
        Client client = clients.get(sessionId);
        if (client == null) {
            log.debug("Update for a closed session {}", sessionId);
            return;
        }
        long blockCount = view.blockCount();
        if (blockCount > (view.watchesSectors() ? MAX_VIEW_BLOCKS : maxCellBlocks)) {
            log.debug("Session {} asks for {} blocks, sectors {}", sessionId, blockCount, view.watchesSectors());
            return;
        }
        ClientUpdateRequest oldView = client.getLastUpdateRequest();
        // A client that comes back to a part of the world that was pruned in the meantime finds nothing there. Its view
        // stays unset, so the next update starts over.
        if (oldView == null && blocksIn(view, null).isEmpty()) {
            send(client, new StatusResponse(NO_BLOCKS));
            return;
        }
        boolean switched = client.isSectors() != view.watchesSectors();
        client.setLastUpdateRequest(view);
        client.setSectors(view.watchesSectors());

        // only the blocks that come into view or go out of it, not every block in view
        ClientUpdateRequest kept = switched ? null : oldView;
        if (client.isSectors()) {
            client.getActiveBlocks().clear();
            sendSectors(client, blocksIn(view, kept));
            return;
        }
        Set<String> clientBlocks = client.getActiveBlocks();
        if (kept != null) keysIn(kept, view).forEach(clientBlocks::remove);
        Set<String> added = new HashSet<>(keysIn(view, kept));
        clientBlocks.addAll(added);
        Arrays.stream(view.resendKeys()).filter(clientBlocks::contains).forEach(added::add);
        sendClientUpdate(client, added, false);
    }
}
