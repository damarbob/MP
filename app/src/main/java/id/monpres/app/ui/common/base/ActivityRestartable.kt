package id.monpres.app.ui.common.base

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

interface ActivityRestartable {
    fun restartActivity() {

        val activity = this as AppCompatActivity
        val intent = Intent(activity, activity::class.java)

        activity.startActivity(intent)
        activity.finish()

    }
}
