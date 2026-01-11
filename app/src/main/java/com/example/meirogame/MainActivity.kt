package com.example.meirogame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var mazeView: MazeView
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mazeView = findViewById(R.id.mazeView)
        statusView = findViewById(R.id.status)
        val restartButton: Button = findViewById(R.id.restartButton)

        restartButton.setOnClickListener {
            mazeView.resetGame()
            statusView.text = "傾けてボールを動かそう"
        }

        mazeView.setOnGoalReachedListener {
            statusView.text = "ゴール！おめでとう"
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val ax = (-event.values[0] / 9.8f).coerceIn(-1f, 1f)
        val ay = (event.values[1] / 9.8f).coerceIn(-1f, 1f)
        mazeView.setTilt(ax, ay)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}

class MazeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), Choreographer.FrameCallback {
    private val mazeLayout = listOf(
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
    )

    private val maze = mazeLayout.map { row -> row.map { it == '1' } }
    private val rows = maze.size
    private val cols = maze[0].size

    private data class Ball(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var radius: Float
    )

    private val ball = Ball(1.5f, 1.5f, 0f, 0f, 0.28f)
    private val goal = Ball(cols - 2.5f, rows - 2.5f, 0f, 0f, 0.4f)

    private var inputAx = 0f
    private var inputAy = 0f
    private var lastFrameTimeNanos = 0L
    private var won = false
    private var onGoalReached: (() -> Unit)? = null

    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2A2A2A") }
    private val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#151515") }
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4CC3FF") }
    private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#3EE387") }

    fun setTilt(ax: Float, ay: Float) {
        inputAx = ax
        inputAy = ay
    }

    fun resetGame() {
        ball.x = 1.5f
        ball.y = 1.5f
        ball.vx = 0f
        ball.vy = 0f
        won = false
    }

    fun setOnGoalReachedListener(listener: () -> Unit) {
        onGoalReached = listener
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrameTimeNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (lastFrameTimeNanos == 0L) {
            lastFrameTimeNanos = frameTimeNanos
        }
        val dt = ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000.0f).coerceAtMost(0.05f)
        lastFrameTimeNanos = frameTimeNanos
        updatePhysics(dt)
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cellSize = min(width / cols.toFloat(), height / rows.toFloat())
        val offsetX = (width - cols * cellSize) / 2f
        val offsetY = (height - rows * cellSize) / 2f

        canvas.save()
        canvas.translate(offsetX, offsetY)

        canvas.drawRect(0f, 0f, cols * cellSize, rows * cellSize, floorPaint)

        for (y in 0 until rows) {
            for (x in 0 until cols) {
                if (maze[y][x]) {
                    canvas.drawRect(
                        x * cellSize,
                        y * cellSize,
                        (x + 1) * cellSize,
                        (y + 1) * cellSize,
                        wallPaint
                    )
                }
            }
        }

        canvas.drawCircle(
            goal.x * cellSize,
            goal.y * cellSize,
            goal.radius * cellSize,
            goalPaint
        )

        ballPaint.color = if (won) Color.parseColor("#FFD166") else Color.parseColor("#4CC3FF")
        canvas.drawCircle(
            ball.x * cellSize,
            ball.y * cellSize,
            ball.radius * cellSize,
            ballPaint
        )

        canvas.restore()
    }

    private fun updatePhysics(dt: Float) {
        if (won) return
        val accel = 6f
        val friction = 3.5f

        ball.vx += inputAx * accel * dt
        ball.vy += inputAy * accel * dt

        ball.vx -= ball.vx * friction * dt
        ball.vy -= ball.vy * friction * dt

        moveBall(ball.vx * dt, 0f)
        moveBall(0f, ball.vy * dt)

        val dx = ball.x - goal.x
        val dy = ball.y - goal.y
        if (hypot(dx, dy) < ball.radius + goal.radius) {
            won = true
            onGoalReached?.invoke()
        }
    }

    private fun moveBall(dx: Float, dy: Float) {
        ball.x += dx
        ball.y += dy

        val minX = 1 + ball.radius
        val maxX = cols - 2 - ball.radius
        val minY = 1 + ball.radius
        val maxY = rows - 2 - ball.radius
        ball.x = max(minX, min(maxX, ball.x))
        ball.y = max(minY, min(maxY, ball.y))

        val cellMinX = floor(ball.x - ball.radius).toInt()
        val cellMaxX = floor(ball.x + ball.radius).toInt()
        val cellMinY = floor(ball.y - ball.radius).toInt()
        val cellMaxY = floor(ball.y + ball.radius).toInt()

        for (y in cellMinY..cellMaxY) {
            for (x in cellMinX..cellMaxX) {
                if (maze.getOrNull(y)?.getOrNull(x) != true) continue
                resolveCircleRectCollision(x, y)
            }
        }
    }

    private fun resolveCircleRectCollision(cellX: Int, cellY: Int) {
        val nearestX = max(cellX.toFloat(), min(ball.x, cellX + 1f))
        val nearestY = max(cellY.toFloat(), min(ball.y, cellY + 1f))
        val dx = ball.x - nearestX
        val dy = ball.y - nearestY
        val dist = hypot(dx, dy)

        if (dist < ball.radius && dist > 0f) {
            val overlap = ball.radius - dist
            ball.x += (dx / dist) * overlap
            ball.y += (dy / dist) * overlap
            if (abs(dx) > abs(dy)) {
                ball.vx = 0f
            } else {
                ball.vy = 0f
            }
        }
    }
}
