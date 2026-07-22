package com.inscopelabs.abx.server.workspace.widget

import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.inscopelabs.abx.server.R

class WorkspaceServerSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    enum class Phase {
        WORKSPACE,
        SERVER
    }

    private var currentPhase: Phase = Phase.WORKSPACE
    var onPhaseChanged: ((Phase) -> Unit)? = null

    private val switchThumb: View
    private val txtWorkspaceLabel: TextView
    private val txtServerLabel: TextView
    private val switchTrack: FrameLayout

    private var currentAnimator: ObjectAnimator? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_workspace_server_switch, this, true)

        switchTrack = findViewById(R.id.switchTrack)
        switchThumb = findViewById(R.id.switchThumb)
        txtWorkspaceLabel = findViewById(R.id.txtWorkspaceLabel)
        txtServerLabel = findViewById(R.id.txtServerLabel)

        val padding = resources.getDimensionPixelSize(R.dimen.spacing_xs)
        switchTrack.setPadding(padding, padding, padding, padding)

        txtWorkspaceLabel.setOnClickListener {
            if (currentPhase != Phase.WORKSPACE) {
                setPhase(Phase.WORKSPACE, animate = true)
            }
        }

        txtServerLabel.setOnClickListener {
            if (currentPhase != Phase.SERVER) {
                setPhase(Phase.SERVER, animate = true)
            }
        }

        applyState(animate = false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post {
            updateThumbDimensions()
            applyState(animate = false)
        }
    }

    private fun updateThumbDimensions() {
        val totalWidth = switchTrack.width
        val paddingLeft = switchTrack.paddingLeft
        val paddingRight = switchTrack.paddingRight
        val availableWidth = totalWidth - paddingLeft - paddingRight
        if (availableWidth > 0) {
            val thumbWidth = availableWidth / 2
            val lp = switchThumb.layoutParams
            if (lp.width != thumbWidth) {
                lp.width = thumbWidth
                switchThumb.layoutParams = lp
            }
        }
    }

    fun setInitialPhase(phase: Phase) {
        currentPhase = phase
        post {
            updateThumbDimensions()
            applyState(animate = false)
        }
    }

    fun setPhase(phase: Phase, animate: Boolean = true) {
        if (currentPhase == phase && !animate) return
        currentPhase = phase
        applyState(animate = animate)
        onPhaseChanged?.invoke(currentPhase)
    }

    fun togglePhase() {
        val newPhase = if (currentPhase == Phase.WORKSPACE) Phase.SERVER else Phase.WORKSPACE
        setPhase(newPhase, animate = true)
    }

    fun getPhase(): Phase = currentPhase

    private fun applyState(animate: Boolean) {
        val activeColor = ContextCompat.getColor(context, R.color.color_on_primary)
        val inactiveColor = ContextCompat.getColor(context, R.color.color_on_surface_variant)

        if (currentPhase == Phase.WORKSPACE) {
            txtWorkspaceLabel.setTextColor(activeColor)
            txtServerLabel.setTextColor(inactiveColor)
        } else {
            txtWorkspaceLabel.setTextColor(inactiveColor)
            txtServerLabel.setTextColor(activeColor)
        }

        val totalWidth = switchTrack.width
        val paddingLeft = switchTrack.paddingLeft
        val paddingRight = switchTrack.paddingRight
        val availableWidth = totalWidth - paddingLeft - paddingRight
        val thumbWidth = availableWidth / 2

        val targetX = if (currentPhase == Phase.WORKSPACE) {
            paddingLeft.toFloat()
        } else {
            (paddingLeft + thumbWidth).toFloat()
        }

        currentAnimator?.cancel()

        if (animate && totalWidth > 0) {
            val fromX = switchThumb.translationX
            currentAnimator = ObjectAnimator.ofFloat(switchThumb, View.TRANSLATION_X, fromX, targetX).apply {
                duration = 200L
                interpolator = DecelerateInterpolator()
                start()
            }
        } else {
            switchThumb.translationX = targetX
        }
    }
}
