package com.socreate.rendering

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.socreate.engine.BrushEngine
import com.socreate.viewmodel.CanvasViewModel
import kotlin.math.abs

class SoCreateSurfaceView(
    context: Context,
    private val renderer: SoCreateRenderer,
    private val brushEngine: BrushEngine,
    private val viewModel: CanvasViewModel
) : GLSurfaceView(context) {

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            viewModel.zoomBy(detector.scaleFactor, detector.focusX, detector.focusY)
            return true
        }
    })

    private var lastPanX = 0f
    private var lastPanY = 0f
    private var isPanning = false

    init {
        setEGLContextClientVersion(3)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        isFocusableInTouchMode = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        val pointerCount = event.pointerCount

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (pointerCount == 1) {
                    // Single finger: drawing
                    isPanning = false
                    viewModel.handleCanvasTouch(event, width.toFloat(), height.toFloat())
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (pointerCount == 2) {
                    // Two finger: pan
                    isPanning = true
                    if (event.historySize > 0) {
                        val dx = event.getX(0) - event.getHistoricalX(0, 0)
                        val dy = event.getY(0) - event.getHistoricalY(0, 0)
                        viewModel.setPan(
                            viewModel.panX - dx / viewModel.zoom,
                            viewModel.panY - dy / viewModel.zoom
                        )
                    } else {
                        val dx = event.getX(0) - lastPanX
                        val dy = event.getY(0) - lastPanY
                        viewModel.setPan(
                            viewModel.panX - dx / viewModel.zoom,
                            viewModel.panY - dy / viewModel.zoom
                        )
                    }
                    lastPanX = event.getX(0)
                    lastPanY = event.getY(0)
                    requestRender()
                } else if (!isPanning) {
                    // Single finger drawing
                    viewModel.handleCanvasTouch(event, width.toFloat(), height.toFloat())
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (pointerCount == 2) {
                    isPanning = true
                    lastPanX = event.getX(0)
                    lastPanY = event.getY(0)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                isPanning = false
                if (pointerCount <= 1) {
                    viewModel.handleCanvasTouch(event, width.toFloat(), height.toFloat())
                }
            }
        }

        requestRender()
        return true
    }
}
