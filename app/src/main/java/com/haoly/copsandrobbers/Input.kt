package com.haoly.copsandrobbers

import android.graphics.RectF
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.sqrt

class InputState {
    var mx = 0f
    var my = 0f
    var dash = false
    var useItem = false
}

/**
 * 触屏虚拟摇杆（左下浮动式）+ 两个动作键（右下），
 * 同时兼容键盘 / 手柄方向键（模拟器、电视上也能玩）。
 */
class InputController {

    val state = InputState()

    // 摇杆
    var joyActive = false
    var joyCx = 0f
    var joyCy = 0f
    var joyKx = 0f
    var joyKy = 0f
    var joyR = 70f
    private var joyId = -1

    // 按钮
    var dashCx = 0f
    var dashCy = 0f
    var itemCx = 0f
    var itemCy = 0f
    var btnR = 46f
    private var dashId = -1
    private var itemId = -1
    var dashHold = false
    var itemHold = false

    // 键盘方向
    private var keyX = 0f
    private var keyY = 0f

    private var vw = 0f
    private var vh = 0f

    fun layout(w: Float, h: Float) {
        vw = w
        vh = h
        val m = minOf(w, h)
        joyR = (m * 0.115f).coerceIn(52f, 108f)
        btnR = (joyR * 0.66f).coerceIn(34f, 68f)
        val pad = m * 0.05f
        dashCx = w - pad - btnR
        dashCy = h - pad - btnR * 1.15f
        itemCx = w - pad - btnR * 2.6f
        itemCy = h - pad - btnR * 0.8f
    }

    fun onTouch(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = ev.actionIndex
                val id = ev.getPointerId(i)
                val x = ev.getX(i)
                val y = ev.getY(i)
                when {
                    inCircle(x, y, dashCx, dashCy, btnR * 1.45f) -> {
                        dashId = id
                        dashHold = true
                        state.dash = true
                    }
                    inCircle(x, y, itemCx, itemCy, btnR * 1.45f) -> {
                        itemId = id
                        itemHold = true
                        state.useItem = true
                    }
                    joyId == -1 && x < vw * 0.55f && y > vh * 0.5f -> {
                        joyId = id
                        joyCx = x
                        joyCy = y
                        joyKx = x
                        joyKy = y
                        joyActive = true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until ev.pointerCount) {
                    val id = ev.getPointerId(i)
                    val x = ev.getX(i)
                    val y = ev.getY(i)
                    if (id == joyId) {
                        var dx = x - joyCx
                        var dy = y - joyCy
                        val d = sqrt(dx * dx + dy * dy)
                        if (d > joyR) {
                            dx = dx / d * joyR
                            dy = dy / d * joyR
                        }
                        joyKx = joyCx + dx
                        joyKy = joyCy + dy
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val i = ev.actionIndex.coerceIn(0, (ev.pointerCount - 1).coerceAtLeast(0))
                val id = ev.getPointerId(i)
                if (id == joyId) { joyId = -1; joyActive = false }
                if (id == dashId) { dashId = -1; dashHold = false }
                if (id == itemId) { itemId = -1; itemHold = false }
            }
        }
        return true
    }

    fun onKeyDown(code: Int): Boolean {
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> keyX = -1f
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> keyX = 1f
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> keyY = -1f
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> keyY = 1f
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_SHIFT_LEFT -> state.dash = true
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_BUTTON_B -> state.useItem = true
            else -> return false
        }
        return true
    }

    fun onKeyUp(code: Int): Boolean {
        when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> keyX = 0f
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W,
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> keyY = 0f
            else -> return false
        }
        return true
    }

    /** 取出本帧输入；dash / useItem 为边沿信号，取走后清零 */
    fun consume(): InputState {
        var mx = keyX
        var my = keyY
        if (joyActive) {
            val dx = (joyKx - joyCx) / joyR
            val dy = (joyKy - joyCy) / joyR
            val dead = 0.16f
            if (sqrt(dx * dx + dy * dy) > dead) {
                mx += dx
                my += dy
            }
        }
        state.mx = mx
        state.my = my
        return state
    }

    fun clearEdges() {
        state.dash = false
        state.useItem = false
    }

    private fun inCircle(x: Float, y: Float, cx: Float, cy: Float, r: Float): Boolean {
        val dx = x - cx
        val dy = y - cy
        return dx * dx + dy * dy <= r * r
    }

    fun hitButton(x: Float, y: Float): Boolean =
        inCircle(x, y, dashCx, dashCy, btnR * 1.45f) || inCircle(x, y, itemCx, itemCy, btnR * 1.45f)

    val joyRect: RectF get() = RectF(joyCx - joyR, joyCy - joyR, joyCx + joyR, joyCy + joyR)
}
