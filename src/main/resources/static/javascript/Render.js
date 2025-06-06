// Render.js

const canvas = document.getElementById("gridCanvas");
const ctx = canvas.getContext("2d");

const teleportButton = document.getElementById("teleportButton");
const xViewValue = document.getElementById("BlockXAmount");
const yViewValue = document.getElementById("BlockYAmount");

let blockSize = 0;
fetch("/blocksize")
    .then(response => response.json())
    .then(data => { blockSize = data; })
    .catch(err => console.error("Failed to load block state:", err));
const cellSize = 10;

const canvasWidth = 1600;
const canvasHeight = 800;

canvas.width = canvasWidth;
canvas.height = canvasHeight;

// Viewport offset in cell space
let cellOffsetX = 0;
let cellOffsetY = 0;

// Data structures
const blockData = new Map();           // Map<string "x,y", 2D array>
const subscriptions = new Map();       // Map<string "x,y", STOMP subscription>

let stompClient = null;

// === REDRAW SCHEDULER ===
const redrawQueue = new Set();
let redrawScheduled = false;

function scheduleRedraw(blockX, blockY) {
    const key = `${blockX},${blockY}`;
    redrawQueue.add(key);

    if (!redrawScheduled) {
        redrawScheduled = true;
        requestAnimationFrame(() => {
            for (const key of redrawQueue) {
                const [x, y] = key.split(',').map(Number);
                drawSingleBlock(x, y);
            }
            redrawQueue.clear();
            redrawScheduled = false;
        });
    }
}

// === DRAWING ===
function drawSingleBlock(blockX, blockY) {
    const key = `${blockX},${blockY}`;
    const data = blockData.get(key);
    if (!data) return;

    const baseX = blockX * blockSize;
    const baseY = blockY * blockSize;

    for (let y = 0; y < blockSize; y++) {
        for (let x = 0; x < blockSize; x++) {
            const cell = data[x]?.[y];
            const worldX = baseX + x;
            const worldY = baseY + y;

            const canvasX = (worldX - cellOffsetX) * cellSize;
            const canvasY = (worldY - cellOffsetY) * cellSize;

            if (canvasX >= 0 && canvasX < canvas.width && canvasY >= 0 && canvasY < canvas.height) {
                ctx.fillStyle = (cell === null || cell === undefined) ? "white" : "black";
                ctx.fillRect(canvasX, canvasY, cellSize, cellSize);
                ctx.strokeStyle = "#ccc";
                ctx.strokeRect(canvasX, canvasY, cellSize, cellSize);
            }
        }
    }

    // Draw block border
    const blockCanvasX = (baseX - cellOffsetX) * cellSize;
    const blockCanvasY = (baseY - cellOffsetY) * cellSize;
    const blockPixelSize = blockSize * cellSize;

    ctx.strokeStyle = "rgba(255, 0, 0, 0.6)";
    ctx.lineWidth = 2;
    ctx.strokeRect(blockCanvasX, blockCanvasY, blockPixelSize, blockPixelSize);
    ctx.lineWidth = 1;
}

// === WEBSOCKET CONNECT ===
function connectWebSocket() {
    const socket = new SockJS("/ws");
    stompClient = Stomp.over(socket);
    stompClient.debug = () => {}; // Disable debug logs

    stompClient.connect({}, () => {
        console.log("WebSocket connected");
        updateVisibleBlocks();
    }, (error) => {
        console.error("WebSocket connection error:", error);
    });
}

// === SUBSCRIBE & UNSUBSCRIBE ===
function subscribeToBlock(blockX, blockY) {
    const key = `${blockX},${blockY}`;
    if (!stompClient || subscriptions.has(key)) return;

    const topic = `/topic/block/${blockX}/${blockY}`;
    const subscription = stompClient.subscribe(topic, (message) => {
        const data = JSON.parse(message.body);
        blockData.set(key, data);
        scheduleRedraw(blockX, blockY);
    });

    subscriptions.set(key, subscription);
}

function unsubscribeFromBlock(blockX, blockY) {
    const key = `${blockX},${blockY}`;
    const subscription = subscriptions.get(key);
    if (subscription) {
        subscription.unsubscribe();
        subscriptions.delete(key);
        blockData.delete(key);
    }
}

// === UPDATE VISIBLE BLOCKS ===
function updateVisibleBlocks() {
    const startBlockX = Math.floor(cellOffsetX / blockSize);
    const startBlockY = Math.floor(cellOffsetY / blockSize);
    const endBlockX = Math.floor((cellOffsetX + canvas.width / cellSize) / blockSize);
    const endBlockY = Math.floor((cellOffsetY + canvas.height / cellSize) / blockSize);

    const visibleKeys = new Set();

    for (let blockY = startBlockY; blockY <= endBlockY; blockY++) {
        for (let blockX = startBlockX; blockX <= endBlockX; blockX++) {
            const key = `${blockX},${blockY}`;
            visibleKeys.add(key);

            if (!subscriptions.has(key)) {
                // Fetch initial block state via REST
                fetch(`/state/${blockX}/${blockY}`)
                    .then(response => response.json())
                    .then(data => {
                        blockData.set(key, data);
                        scheduleRedraw(blockX, blockY);
                    })
                    .catch(err => console.error("Failed to load block state:", err));

                // Subscribe for live updates
                subscribeToBlock(blockX, blockY);
            }
        }
    }

    // Unsubscribe from blocks no longer visible
    for (const key of subscriptions.keys()) {
        if (!visibleKeys.has(key)) {
            const [x, y] = key.split(',').map(Number);
            unsubscribeFromBlock(x, y);
        }
    }

    // Clear canvas and redraw all visible blocks
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    for (const key of visibleKeys) {
        const [x, y] = key.split(',').map(Number);
        scheduleRedraw(x, y);
    }
}

// === PAN & DRAG LOGIC ===
let isDragging = false;
let dragStartX = 0, dragStartY = 0;
let lastPanTime = 0;
const panThrottle = 50; // ms

canvas.addEventListener("mousedown", e => {
    isDragging = true;
    dragStartX = e.clientX;
    dragStartY = e.clientY;
});

canvas.addEventListener("mouseup", () => { isDragging = false; });
canvas.addEventListener("mouseleave", () => { isDragging = false; });

canvas.addEventListener("mousemove", e => {
    if (!isDragging) return;
    const now = Date.now();
    if (now - lastPanTime < panThrottle) return;

    const dx = e.clientX - dragStartX;
    const dy = e.clientY - dragStartY;

    dragStartX = e.clientX;
    dragStartY = e.clientY;

    const movedX = Math.round(dx / cellSize);
    const movedY = Math.round(dy / cellSize);

    if (movedX !== 0 || movedY !== 0) {
        cellOffsetX -= movedX;
        cellOffsetY -= movedY;
        updateVisibleBlocks();
        lastPanTime = now;
    }
});

// === CLICK TO TOGGLE CELL ===
canvas.addEventListener("click", e => {
    const rect = canvas.getBoundingClientRect();
    const canvasX = e.clientX - rect.left;
    const canvasY = e.clientY - rect.top;

    const cellX = cellOffsetX + Math.floor(canvasX / cellSize);
    const cellY = cellOffsetY + Math.floor(canvasY / cellSize);

    fetch(`/cell/${cellX}/${cellY}/toggle`, { method: "PUT" });
});

// === TELEPORT BUTTON ===
teleportButton.addEventListener("click", () => {
    const blockX = parseInt(xViewValue.value);
    const blockY = parseInt(yViewValue.value);

    if (isNaN(blockX) || isNaN(blockY)) {
        console.error("Invalid teleport input");
        return;
    }

    cellOffsetX = blockX * blockSize;
    cellOffsetY = blockY * blockSize;
    updateVisibleBlocks();
});

// Initialize view
xViewValue.value = 0;
yViewValue.value = 0;

// Kick off websocket connection and initial rendering
connectWebSocket();
