const canvas = document.getElementById("gridCanvas");
const ctx = canvas.getContext("2d");

const cellSize = 10; // Each cell is 10x10 pixels
const rows = 1000;
const cols = 1000;

canvas.width = cols * cellSize;
canvas.height = rows * cellSize;

camera.size = rows / 2;
camera.x = 1000;
camera.y = 1000;

// Set the camera
function setCamera(int x, int y) {
    camera.x = x;
    camera.y = y;
}

// Fetch grid state from backend
function fetchGrid() {
    fetch("/state/" + camera.x "/" + camera.y + "/" + camera.size) // Expect JSON response
        .then(response => response.json())
        .then(data => drawGrid(data.state)) // Read "state" array
        .catch(err => console.error("Error fetching grid:", err));
}

// Draw the grid using the `CellState` enum
function drawGrid(gridData) {
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    for (let y = 0; y < rows; y++) {
        for (let x = 0; x < cols; x++) {
            const cell = gridData[x][y]; // Extract cell object
            const state = cell.cellState; // Get state as string

            if (state === "ALIVE") ctx.fillStyle = "black";
            else if (state === "DEAD") ctx.fillStyle = "white";
            else ctx.fillStyle = "gray"; // EMPTY

            ctx.fillRect(x * cellSize, y * cellSize, cellSize, cellSize);
            ctx.strokeStyle = "#ccc";
            ctx.strokeRect(x * cellSize, y * cellSize, cellSize, cellSize);
        }
    }
}

// Handle click event for updating a cell
canvas.addEventListener("click", function(event) {
    const x = Math.floor(event.offsetX / cellSize);
    const y = Math.floor(event.offsetY / cellSize);

    fetch(`/cell/${x}/${y}/toggle`, { method: "PUT" }) // Toggle state
        .then(() => fetchGrid()); // Refresh grid after change
});

setInterval(fetchGrid, 1000);
fetchGrid(); // Initial load
