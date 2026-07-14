package com.inscopelabs.abx.server

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.inscopelabs.abx.server.boot.BootRoute

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BootRoute.redirectIfNeeded(this)) return
        setContentView(R.layout.activity_main)
    }
}
