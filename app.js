const canvas = document.getElementById("game");
const statusEl = document.getElementById("status");
const permissionButton = document.getElementById("permission");
const restartButton = document.getElementById("restart");
const ctx = canvas.getContext("2d");

const mazeLayout = [
  "111111111111111",
  "100000100000001",
  "101110101111101",
  "101000101000101",
  "101011101011101",
  "101000001000001",
  "101111111011101",
  "100000001000001",
  "111011101111101",
  "100010001000001",
  "101110111011101",
  "101000000010001",
  "101011111110101",
  "100000000000001",
  "111111111111111",
];

const maze = mazeLayout.map((row) => row.split("").map((c) => c === "1"));
const rows = maze.length;
const cols = maze[0].length;

const ball = {
  x: 1.5,
  y: 1.5,
  vx: 0,
  vy: 0,
  radius: 0.28,
};

const goal = { x: cols - 2.5, y: rows - 2.5, radius: 0.4 };

let input = { ax: 0, ay: 0 };
let lastTime = 0;
let won = false;

function resizeCanvas() {
  const rect = canvas.getBoundingClientRect();
  canvas.width = rect.width * window.devicePixelRatio;
  canvas.height = rect.height * window.devicePixelRatio;
}

window.addEventListener("resize", resizeCanvas);
resizeCanvas();

function resetGame() {
  ball.x = 1.5;
  ball.y = 1.5;
  ball.vx = 0;
  ball.vy = 0;
  won = false;
  statusEl.textContent = "傾けてボールを動かそう";
}

function setInputFromOrientation(event) {
  const gamma = event.gamma ?? 0;
  const beta = event.beta ?? 0;
  const maxTilt = 30;
  input.ax = Math.max(-maxTilt, Math.min(maxTilt, gamma)) / maxTilt;
  input.ay = Math.max(-maxTilt, Math.min(maxTilt, beta)) / maxTilt;
}

function requestPermissionIfNeeded() {
  if (
    typeof DeviceMotionEvent !== "undefined" &&
    typeof DeviceMotionEvent.requestPermission === "function"
  ) {
    return DeviceMotionEvent.requestPermission();
  }
  return Promise.resolve("granted");
}

permissionButton.addEventListener("click", () => {
  requestPermissionIfNeeded()
    .then((result) => {
      if (result === "granted") {
        window.addEventListener("deviceorientation", setInputFromOrientation, true);
        statusEl.textContent = "センサー使用中";
      } else {
        statusEl.textContent = "センサーが許可されませんでした";
      }
    })
    .catch(() => {
      statusEl.textContent = "センサーの有効化に失敗しました";
    });
});

restartButton.addEventListener("click", resetGame);

window.addEventListener("keydown", (event) => {
  const speed = 0.8;
  if (event.key === "ArrowLeft") input.ax = -speed;
  if (event.key === "ArrowRight") input.ax = speed;
  if (event.key === "ArrowUp") input.ay = -speed;
  if (event.key === "ArrowDown") input.ay = speed;
});

window.addEventListener("keyup", () => {
  input.ax = 0;
  input.ay = 0;
});

function update(dt) {
  if (won) return;
  const accel = 6;
  const friction = 3.5;

  ball.vx += input.ax * accel * dt;
  ball.vy += input.ay * accel * dt;

  ball.vx -= ball.vx * friction * dt;
  ball.vy -= ball.vy * friction * dt;

  moveBall(ball.vx * dt, 0);
  moveBall(0, ball.vy * dt);

  const dx = ball.x - goal.x;
  const dy = ball.y - goal.y;
  if (Math.hypot(dx, dy) < ball.radius + goal.radius) {
    won = true;
    statusEl.textContent = "ゴール！おめでとう";
  }
}

function moveBall(dx, dy) {
  ball.x += dx;
  ball.y += dy;

  const minX = 1 + ball.radius;
  const maxX = cols - 2 - ball.radius;
  const minY = 1 + ball.radius;
  const maxY = rows - 2 - ball.radius;
  ball.x = Math.max(minX, Math.min(maxX, ball.x));
  ball.y = Math.max(minY, Math.min(maxY, ball.y));

  const cellMinX = Math.floor(ball.x - ball.radius);
  const cellMaxX = Math.floor(ball.x + ball.radius);
  const cellMinY = Math.floor(ball.y - ball.radius);
  const cellMaxY = Math.floor(ball.y + ball.radius);

  for (let y = cellMinY; y <= cellMaxY; y += 1) {
    for (let x = cellMinX; x <= cellMaxX; x += 1) {
      if (!maze[y]?.[x]) continue;
      resolveCircleRectCollision(x, y);
    }
  }
}

function resolveCircleRectCollision(cellX, cellY) {
  const rectX = cellX;
  const rectY = cellY;
  const nearestX = Math.max(rectX, Math.min(ball.x, rectX + 1));
  const nearestY = Math.max(rectY, Math.min(ball.y, rectY + 1));
  const dx = ball.x - nearestX;
  const dy = ball.y - nearestY;
  const dist = Math.hypot(dx, dy);

  if (dist < ball.radius && dist > 0) {
    const overlap = ball.radius - dist;
    ball.x += (dx / dist) * overlap;
    ball.y += (dy / dist) * overlap;
    if (Math.abs(dx) > Math.abs(dy)) {
      ball.vx = 0;
    } else {
      ball.vy = 0;
    }
  }
}

function draw() {
  ctx.save();
  ctx.scale(window.devicePixelRatio, window.devicePixelRatio);
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  const cellSize = Math.min(
    canvas.width / window.devicePixelRatio / cols,
    canvas.height / window.devicePixelRatio / rows
  );
  const offsetX =
    (canvas.width / window.devicePixelRatio - cols * cellSize) / 2;
  const offsetY =
    (canvas.height / window.devicePixelRatio - rows * cellSize) / 2;

  ctx.translate(offsetX, offsetY);

  ctx.fillStyle = "#151515";
  ctx.fillRect(0, 0, cols * cellSize, rows * cellSize);

  for (let y = 0; y < rows; y += 1) {
    for (let x = 0; x < cols; x += 1) {
      if (maze[y][x]) {
        ctx.fillStyle = "#2a2a2a";
        ctx.fillRect(x * cellSize, y * cellSize, cellSize, cellSize);
      }
    }
  }

  ctx.fillStyle = "#3ee387";
  ctx.beginPath();
  ctx.arc(
    goal.x * cellSize,
    goal.y * cellSize,
    goal.radius * cellSize,
    0,
    Math.PI * 2
  );
  ctx.fill();

  ctx.fillStyle = won ? "#ffd166" : "#4cc3ff";
  ctx.beginPath();
  ctx.arc(ball.x * cellSize, ball.y * cellSize, ball.radius * cellSize, 0, Math.PI * 2);
  ctx.fill();

  ctx.restore();
}

function loop(timestamp) {
  const delta = (timestamp - lastTime) / 1000 || 0;
  lastTime = timestamp;
  update(Math.min(delta, 0.05));
  draw();
  requestAnimationFrame(loop);
}

resetGame();
requestAnimationFrame(loop);
