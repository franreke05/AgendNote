package com.franciscor.agendnote.core.notifications

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.franciscor.agendnote.core.model.TaskItem
import kotlinx.datetime.LocalDate
import java.util.Calendar

actual object NotificationServiceProvider {
    actual fun getNotificationService(): NotificationService = AndroidNotificationService
}

/** Android notification scheduling backed by AlarmManager so it survives app process death. */
object AndroidNotificationService : NotificationService {
    private const val REQUEST_CODE = 4201
    private var activity: Activity? = null
    private var applicationContext: Context? = null

    fun initialize(activity: Activity) {
        this.activity = activity
        applicationContext = activity.applicationContext
        AndroidNotificationReceiver.createChannel(activity.applicationContext)
    }

    override suspend fun scheduleTaskNotification(task: TaskItem, taskDate: LocalDate) {
        val context = applicationContext ?: return
        val time = task.time ?: return
        val triggerAt = Calendar.getInstance().apply {
            clear()
            set(taskDate.year, taskDate.monthNumber - 1, taskDate.dayOfMonth, time.hour, time.minute, 0)
        }.timeInMillis
        if (triggerAt <= System.currentTimeMillis()) return

        cancelTaskNotification(task.id)
        val intent = Intent(context, AndroidNotificationReceiver::class.java).apply {
            action = ACTION_SHOW_TASK
            putExtra(EXTRA_TASK_ID, task.id)
            putExtra(EXTRA_TITLE, task.title)
            putExtra(EXTRA_DETAILS, task.details)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            !alarmManager.canScheduleExactAlarms()
        ) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    override suspend fun cancelTaskNotification(taskId: String) {
        val context = applicationContext ?: return
        val intent = Intent(context, AndroidNotificationReceiver::class.java).apply {
            action = ACTION_SHOW_TASK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        pendingIntent?.let {
            context.getSystemService(AlarmManager::class.java).cancel(it)
            it.cancel()
        }
    }

    override suspend fun requestPermissions() {
        val host = activity ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(host, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            host.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE)
        }
    }

    private const val ACTION_SHOW_TASK = "com.franciscor.agendnote.SHOW_TASK_NOTIFICATION"
    private const val EXTRA_TASK_ID = "task_id"
    private const val EXTRA_TITLE = "task_title"
    private const val EXTRA_DETAILS = "task_details"
}