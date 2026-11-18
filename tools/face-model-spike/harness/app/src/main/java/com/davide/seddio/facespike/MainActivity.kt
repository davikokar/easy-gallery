package com.davide.seddio.facespike

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView

const val TAG = "FACESPIKE"

/** Starts one measurement run from intent extras; results are files in the app's internal storage (adb pull). */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The run must stay in the foreground with the screen on: a background app gets 3 little cores.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(TextView(this).apply { text = "face-spike running" })
        val params = JobParams.from(intent)
        Thread { Job(applicationContext, params).run() }.start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
    }
}
