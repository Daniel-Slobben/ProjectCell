// Render.js
const canvas = document.getElementById("gridCanvas");
const ctx = canvas.getContext("2d");

// UI elements
const teleportButton = document.getElementById("teleportButton");
const xViewValue = document.getElementById("BlockXAmount");
const yViewValue = document.getElementById("BlockYAmount");

// Constants
const blockSize = 100; // Number of cells per block
const cellSize = 10;   // Size of each cell in pixels

const canvasWidth = 1600;
const canvasHeight = 800;

canvas.width = canvasWidth;
canvas.height = canvasHeight;

// Viewport offset in cell-space
let cellOffsetX = 0;
let cellOffsetY = 0;

// Fetch and draw all visible blocks
function fetchGrid() {
    const startBlockX = Math.floor(cellOffsetX / blockSize);
    const startBlockY = Math.floor(cellOffsetY / blockSize);
    const endBlockX = Math.floor((cellOffsetX + canvas.width / cellSize) / blockSize);
    const endBlockY = Math.floor((cellOffsetY + canvas.height / cellSize) / blockSize);

    const promises = [];

    for (let blockY = startBlockY; blockY <= endBlockY; blockY++) {
        for (let blockX = startBlockX; blockX <= endBlockX; blockX++) {
            const promise = fetch(`/state/${blockX}/${blockY}`)
                .then(res => res.json())
                .then(data => ({ blockX, blockY, data }));

            promises.push(promise);
        }
    }

    Promise.all(promises)
        .then(blocks => drawVisibleGrid(blocks))
        .catch(err => console.error("Error fetching blocks:", err));
}

// Render all visible blocks
function drawVisibleGrid(blocks) {
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    for (const { blockX, blockY, data } of blocks) {
        const baseX = blockX * blockSize;
        const baseY = blockY * blockSize;

        for (let y = 0; y < blockSize; y++) {
            for (let x = 0; x < blockSize; x++) {
                const cell = data[x]?.[y];

                const worldCellX = baseX + x;
                const worldCellY = baseY + y;

                const canvasX = (worldCellX - cellOffsetX) * cellSize;
                const canvasY = (worldCellY - cellOffsetY) * cellSize;

                if (
                    canvasX >= 0 && canvasX < canvas.width &&
                    canvasY >= 0 && canvasY < canvas.height
                ) {
                    ctx.fillStyle = (cell === null || cell === undefined) ? "white" : "black";
                    ctx.fillRect(canvasX, canvasY, cellSize, cellSize);
                    ctx.strokeStyle = "#ccc";
                    ctx.strokeRect(canvasX, canvasY, cellSize, cellSize);
                }
            }
        }

        // Draw block borders
        const blockCanvasX = (baseX - cellOffsetX) * cellSize;
        const blockCanvasY = (baseY - cellOffsetY) * cellSize;
        const blockPixelSize = blockSize * cellSize;

        ctx.strokeStyle = "rgba(255, 0, 0, 0.6)";
        ctx.lineWidth = 2;
        ctx.strokeRect(blockCanvasX, blockCanvasY, blockPixelSize, blockPixelSize);
    }

    ctx.lineWidth = 1; // reset for next render
}


// Mouse dragging (panning)
let isDragging = false;
let dragStartX, dragStartY;

canvas.addEventListener("mousedown", function (e) {
    isDragging = true;
    dragStartX = e.clientX;
    dragStartY = e.clientY;
});

canvas.addEventListener("mouseup", () => isDragging = false);
canvas.addEventListener("mouseleave", () => isDragging = false);

canvas.addEventListener("mousemove", function (e) {
    if (!isDragging) return;

    const dx = e.clientX - dragStartX;
    const dy = e.clientY - dragStartY;

    dragStartX = e.clientX;
    dragStartY = e.clientY;

    const movedX = Math.round(dx / cellSize);
    const movedY = Math.round(dy / cellSize);

    if (movedX !== 0 || movedY !== 0) {
        cellOffsetX -= movedX;
        cellOffsetY -= movedY;
        fetchGrid();
    }
});

// Handle click to toggle a cell
canvas.addEventListener("click", function (e) {
    const rect = canvas.getBoundingClientRect();
    const canvasX = e.clientX - rect.left;
    const canvasY = e.clientY - rect.top;

    const cellX = cellOffsetX + Math.floor(canvasX / cellSize);
    const cellY = cellOffsetY + Math.floor(canvasY / cellSize);

    fetch(`/cell/${cellX}/${cellY}/toggle`, { method: "PUT" })
        .then(() => fetchGrid());
});

// Teleport to a specific block
teleportButton.addEventListener("click", function () {
    const blockX = parseInt(xViewValue.value);
    const blockY = parseInt(yViewValue.value);

    if (isNaN(blockX) || isNaN(blockY)) {
        console.error("Invalid teleport input");
        return;
    }

    cellOffsetX = blockX * blockSize;
    cellOffsetY = blockY * blockSize;
    fetchGrid();
});

// Auto-refresh every second
setInterval(fetchGrid, 1000);

// Set initial values
xViewValue.value = 0;
yViewValue.value = 0;

// Initial fetch
fetchGrid();
