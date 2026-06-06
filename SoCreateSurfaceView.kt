package com.socreate.rendering

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import com.socreate.engine.BrushEngine

class SoCreateSurfaceView(
    context: Context,
    private val renderer: SoCreateRenderer,
    private val brushEngine: BrushEngine
) : GLSurfaceView(context) {

    init {
        setEGLContextClientVersion(3)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        isFocusableInTouchMode = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        brushEngine.processTouchEvent(event)
        requestRender()
        return true
    }
}
