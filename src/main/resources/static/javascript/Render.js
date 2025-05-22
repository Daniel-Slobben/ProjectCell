const canvas = document.getElementById("gridCanvas");
const ctx = canvas.getContext("2d");

const cellSize = 10; // Each cell is 10x10 pixels
let size = 12;
let x  = 1;
let y = 1;

canvas.width = size * cellSize;
canvas.height = size * cellSize;

function setView(x, y) {
    this.x = x;
    this.y = y;
    fetchGrid();
}

function fetchGrid() {
    fetch(`/state/${x}/${y}`)
        .then(response => response.json())
        .then(data => drawGrid(data))
        .catch(err => console.error("Error fetching grid:", err));
}

function drawGrid(gridData) {
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    for (let y = 0; y < size; y++) {
        for (let x = 0; x < size; x++) {
            const cell = gridData[x][y]; // Extract cell object

            if (cell === null || cell === undefined) ctx.fillStyle = "white";
            else ctx.fillStyle = "black";

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
document.getElementById("blockXAmount").value = x;
document.getElementById("blockYAmount").value = x;
fetchGrid();
