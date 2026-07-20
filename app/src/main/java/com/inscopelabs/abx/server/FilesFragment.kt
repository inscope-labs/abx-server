package com.inscopelabs.abx.server

import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.fragment.app.Fragment

/**
 * One of the two main user views, and the default shown once the loading
 * gate completes. Renders at the exact same coordinates as ChatFragment
 * (both fill mainContentContainer) — toggled via the Chat/Files switch,
 * never shown simultaneously with ChatFragment.
 */
class FilesFragment : Fragment(R.layout.fragment_files) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x
                if (Math.abs(diffY) > Math.abs(diffX)) {
                    if (diffY > 150 && Math.abs(velocityY) > 150) {
                        (activity as? MainActivity)?.openToolbox()
                        return true
                    }
                }
                return false
            }
        })

        view.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }
}

