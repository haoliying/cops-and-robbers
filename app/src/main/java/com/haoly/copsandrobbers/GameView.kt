package com.haoly.copsandrobbers

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * 游戏主视图：固定步进更新 + 每帧重绘的游戏循环。
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    val game = Game()
    private val input = InputController()
    private val renderer = Renderer()

    @Volatile
    private var running = false
    private var thread: Thread? = null
    private var lastTime = 0L

    private val vibrator: android.os.Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) context.getSystemService(android.os.Vibrator::class.java) else null

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        keepScreenOn = true
        game.onEvent = { ev ->
            when (ev) {
                Game.EV_CATCH -> buzz(70)
                Game.EV_ESCAPE -> buzz(180)
                Game.EV_ITEM -> buzz(28)
                Game.EV_LEVEL -> buzz(120)
                Game.EV_OVER -> buzz(320)
                Game.EV_LOOT -> buzz(45)
            }
        }
    }

    // ------------------------------------------------------------ Surface 回调

    override fun surfaceCreated(holder: SurfaceHolder) {
        renderer.resize(width, height)
        input.layout(width.toFloat(), height.toFloat())
        lastTime = System.nanoTime()
        startLoop()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        renderer.resize(width, height)
        input.layout(width.toFloat(), height.toFloat())
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopLoop()
    }

    fun resumeGame() {
        if (width > 0 && height > 0) {
            renderer.resize(width, height)
            input.layout(width.toFloat(), height.toFloat())
        }
        startLoop()
    }

    fun pauseGame() {
        stopLoop()
        game.pause()
    }

    private fun startLoop() {
        if (running) return
        running = true
        lastTime = System.nanoTime()
        thread = Thread(this, "game-loop").apply { start() }
    }

    private fun stopLoop() {
        running = false
        val t = thread
        thread = null
        if (t != null) {
            try { t.join(400) } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
        }
    }

    override fun run() {
        while (running) {
            val now = System.nanoTime()
            var dt = (now - lastTime) / 1_000_000_000f
            lastTime = now
            if (dt > 0.08f) dt = 0.08f
            if (dt < 0.0005f) dt = 0.0005f

            step(dt)

            val spent = (System.nanoTime() - now) / 1_000_000L
            val sleep = 16L - spent
            if (sleep > 1L) {
                try { Thread.sleep(sleep) } catch (e: InterruptedException) { return }
            }
        }
    }

    private fun step(dt: Float) {
        val st = input.consume()
        game.update(dt, st)
        input.clearEdges()

        val canvas = holder.lockCanvas()
        if (canvas == null) return
        try {
            synchronized(holder) {
                renderer.draw(canvas, game, input)
            }
        } finally {
            try { holder.unlockCanvasAndPost(canvas) } catch (e: IllegalStateException) { /* ignore */ }
        }
    }

    // ------------------------------------------------------------------ 输入

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (game.state != Game.State.PLAY) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) game.tap()
            return true
        }
        return input.onTouch(event)
    }

    override fun onKeyDown(code: Int, event: KeyEvent): Boolean {
        if (code == KeyEvent.KEYCODE_BACK) return false
        if (game.state != Game.State.PLAY) {
            game.tap()
            return true
        }
        return input.onKeyDown(code)
    }

    override fun onKeyUp(code: Int, event: KeyEvent): Boolean = input.onKeyUp(code)

    /** 返回键：先暂停，再退出 */
    fun onBack(): Boolean {
        if (game.state == Game.State.PLAY) {
            game.pause()
            return true
        }
        return false
    }

    private fun buzz(ms: Long) {
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        } catch (e: Exception) {
            // 没有震动权限或硬件时忽略
        }
    }
}
