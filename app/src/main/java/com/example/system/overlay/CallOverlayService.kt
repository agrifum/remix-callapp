package com.example.system.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.CallUppApplication
import com.example.R
import com.example.core.model.CallDirection
import com.example.core.phone.PhoneNumberNormalizer
import com.example.data.entity.CallDraftEntity
import com.example.data.repository.OverlayCommitRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/**
 * CallOverlayService:
 * Displays TYPE_APPLICATION_OVERLAY window with native Android View/XML (call_overlay.xml)
 * during an active phone call (both incoming and outgoing).
 * Commits to Room when saved or dismissed.
 */
class CallOverlayService : Service() {

    companion object {
        const val ACTION_SHOW_OVERLAY = "com.example.action.SHOW_OVERLAY"
        const val ACTION_HIDE_OVERLAY = "com.example.action.HIDE_OVERLAY"

        const val EXTRA_CALL_SESSION_ID = "call_session_id"
        const val EXTRA_PHONE_KEY = "phone_key"
        const val EXTRA_CALL_DIRECTION = "call_direction"
        const val EXTRA_CALL_TIMESTAMP = "call_timestamp"

        private const val CHANNEL_ID = "callupp_overlay_channel"
        private const val NOTIFICATION_ID = 9911

        @Volatile
        private var activeInstance: CallOverlayService? = null

        /**
         * Flushes the latest draft from the active overlay service synchronously,
         * cancels any pending debounced save, and stops the service.
         * Returns the latest in-memory draft if present and not already committed.
         */
        fun flushAndStop(context: Context, sessionId: String): CallDraftEntity? {
            val instance = activeInstance
            val draft = if (instance != null && instance.currentSessionId == sessionId) {
                instance.flushAndStopOverlay()
            } else {
                null
            }
            context.stopService(Intent(context, CallOverlayService::class.java))
            return draft
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var draftSaveJob: Job? = null
    private var currentDraftProvider: (() -> CallDraftEntity)? = null
    private var isCommitted: Boolean = false

    private var currentSessionId: String = ""
    private var currentPhoneKey: String = ""
    private var currentDirection: CallDirection = CallDirection.INCOMING
    private var currentCallTimestamp: Long = 0L

    fun flushAndStopOverlay(): CallDraftEntity? {
        draftSaveJob?.cancel()
        draftSaveJob = null

        if (isCommitted) {
            hideOverlayWindow()
            stopSelf()
            return null
        }

        val draft = currentDraftProvider?.invoke()
        currentDraftProvider = null
        hideOverlayWindow()
        stopSelf()
        return draft
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_OVERLAY -> {
                currentSessionId = intent.getStringExtra(EXTRA_CALL_SESSION_ID) ?: ""
                currentPhoneKey = intent.getStringExtra(EXTRA_PHONE_KEY) ?: ""
                val dirStr = intent.getStringExtra(EXTRA_CALL_DIRECTION)
                currentDirection = if (dirStr == CallDirection.OUTGOING.name) CallDirection.OUTGOING else CallDirection.INCOMING
                currentCallTimestamp = intent.getLongExtra(EXTRA_CALL_TIMESTAMP, System.currentTimeMillis())

                startForegroundSafely()
                showOverlayWindow()
            }
            ACTION_HIDE_OVERLAY -> {
                hideOverlayWindow()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundSafely() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CallUpp")
            .setContentText("Aktywna notatka podczas rozmowy")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun showOverlayWindow() {
        if (overlayView != null) return // Already showing

        val app = application as? CallUppApplication ?: return

        serviceScope.launch {
            val key = PhoneNumberNormalizer.normalizeKey(currentPhoneKey)
            val display = PhoneNumberNormalizer.formatDisplay(key)
            val client = app.container.clientRepository.getClientByPhoneKeySync(key)
            val pastNotes = app.container.noteRepository.getActiveNotesForPhoneSync(key)
            val services = app.container.serviceRepository.activeServices.first()
            val existingDraft = app.container.callDraftRepository.getDraftSync(currentSessionId)

            val maxAllowedHeight = (resources.displayMetrics.heightPixels * 0.70).toInt()
            val container = OverlayContainer(this@CallOverlayService, maxAllowedHeight)
            val inflater = LayoutInflater.from(this@CallOverlayService)
            val view = inflater.inflate(R.layout.call_overlay, container, false)
            container.addView(view)

            // Views lookup
            val overlayTitle = view.findViewById<TextView>(R.id.overlayTitle)
            val overlayClientBadge = view.findViewById<TextView>(R.id.overlayClientBadge)
            val overlayPhone = view.findViewById<TextView>(R.id.overlayPhone)
            val overlayClose = view.findViewById<ImageButton>(R.id.overlayClose)

            val pastNoteContainer = view.findViewById<View>(R.id.pastNoteContainer)
            val pastNoteText = view.findViewById<TextView>(R.id.pastNoteText)
            val pastNoteCount = view.findViewById<TextView>(R.id.pastNoteCount)

            val overlayNote = view.findViewById<EditText>(R.id.overlayNote)
            container.noteEditText = overlayNote

            val clientSection = view.findViewById<View>(R.id.clientSection)
            val clientCheckbox = view.findViewById<CheckBox>(R.id.clientCheckbox)

            val jobSection = view.findViewById<View>(R.id.jobSection)
            val jobCheckbox = view.findViewById<CheckBox>(R.id.jobCheckbox)
            val jobDetailsContainer = view.findViewById<View>(R.id.jobDetailsContainer)
            val serviceContainer = view.findViewById<LinearLayout>(R.id.serviceContainer)
            val dayContainer = view.findViewById<LinearLayout>(R.id.dayContainer)
            val timeButton = view.findViewById<Button>(R.id.timeButton)
            val timeClearButton = view.findViewById<ImageButton>(R.id.timeClearButton)

            val taskButton = view.findViewById<Button>(R.id.taskButton)
            val saveButton = view.findViewById<Button>(R.id.saveButton)

            var isRestoringDraft = true

            // Restore note text from existing draft if present
            if (existingDraft != null && existingDraft.noteText.isNotEmpty()) {
                overlayNote.setText(existingDraft.noteText)
                overlayNote.setSelection(existingDraft.noteText.length)
            }

            // Initial state: "Do Zadań" is disabled if note is empty or only whitespace
            taskButton.isEnabled = !overlayNote.text.isNullOrBlank()

            // 4. Header: Title & Phone & Client status
            if (client != null) {
                overlayTitle.text = client.displayName
                overlayClientBadge.visibility = View.VISIBLE
                overlayPhone.text = display
                overlayPhone.visibility = View.VISIBLE
            } else {
                overlayTitle.text = display
                overlayClientBadge.visibility = View.GONE
                overlayPhone.visibility = View.GONE
            }

            // 5. Past notes (preview of latest note + count of remaining active notes)
            if (pastNotes.isNotEmpty()) {
                pastNoteContainer.visibility = View.VISIBLE
                val newestNote = pastNotes.first().rawText.trim()
                pastNoteText.text = "Poprzednia notatka: \"$newestNote\""
                if (pastNotes.size > 1) {
                    val remainingCount = pastNotes.size - 1
                    pastNoteCount.text = "+ $remainingCount wcześniejszych"
                    pastNoteCount.visibility = View.VISIBLE
                } else {
                    pastNoteCount.visibility = View.GONE
                }
            } else {
                pastNoteContainer.visibility = View.GONE
                pastNoteCount.visibility = View.GONE
            }

            // 7 & 8. Client handling & Job gating
            var isMarkAsClient = client != null || (existingDraft?.markAsClient == true)
            var createJob = existingDraft?.createJob == true
            var selectedServiceId: String? = existingDraft?.serviceId
            var selectedPreliminaryDateEpochDay: Long? = existingDraft?.preliminaryDateEpochDay
            var selectedPreliminaryTimeMinute: Int? = existingDraft?.preliminaryTimeMinute

            val buildCurrentDraft = {
                val text = overlayNote.text?.toString() ?: ""
                CallDraftEntity(
                    callSessionId = currentSessionId,
                    phoneKey = key,
                    noteText = text,
                    markAsClient = isMarkAsClient,
                    createJob = createJob,
                    serviceId = selectedServiceId,
                    preliminaryDateEpochDay = selectedPreliminaryDateEpochDay,
                    preliminaryTimeMinute = selectedPreliminaryTimeMinute,
                    taskRequested = false,
                    updatedAt = System.currentTimeMillis()
                )
            }
            currentDraftProvider = buildCurrentDraft

            fun saveCurrentDraft() {
                if (isCommitted) return
                val draft = buildCurrentDraft()
                app.container.appScope.launch {
                    app.container.callDraftRepository.saveDraft(draft)
                }
            }

            fun scheduleDraftSave() {
                if (isRestoringDraft || isCommitted) return
                draftSaveJob?.cancel()
                draftSaveJob = app.container.appScope.launch {
                    delay(500)
                    saveCurrentDraft()
                }
            }

            // 6. Note input & debounced draft saving (500 ms)
            overlayNote.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val text = s?.toString() ?: ""
                    taskButton.isEnabled = text.isNotBlank()
                    scheduleDraftSave()
                }
                override fun afterTextChanged(s: Editable?) {}
            })

            // WindowManager LayoutParams: default FLAG_NOT_FOCUSABLE so dialer retains system focus
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 120
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
            }

            fun requestOverlayFocus() {
                if ((params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0) {
                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                    try {
                        windowManager?.updateViewLayout(container, params)
                    } catch (e: Exception) {
                        // WindowManager update failed
                    }
                }
                overlayNote.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(overlayNote, InputMethodManager.SHOW_IMPLICIT)
                overlayNote.post {
                    imm?.showSoftInput(overlayNote, InputMethodManager.SHOW_IMPLICIT)
                }
            }

            fun releaseOverlayFocus() {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(overlayNote.windowToken, 0)
                overlayNote.clearFocus()
                if ((params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0) {
                    params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    try {
                        windowManager?.updateViewLayout(container, params)
                    } catch (e: Exception) {
                        // WindowManager update failed
                    }
                }
            }

            container.onReleaseFocus = {
                releaseOverlayFocus()
            }

            overlayNote.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    requestOverlayFocus()
                }
                false
            }
            overlayNote.setOnClickListener {
                requestOverlayFocus()
            }

            container.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    releaseOverlayFocus()
                }
                false
            }

            // Helper chip styling functions
            val density = resources.displayMetrics.density
            val dpToPx = { dp: Int -> (dp * density).toInt() }

            fun createChipBackground(selected: Boolean): GradientDrawable {
                return GradientDrawable().apply {
                    cornerRadius = 16f * density
                    if (selected) {
                        setColor(Color.parseColor("#FF1976D2")) // Primary blue
                        setStroke(dpToPx(1), Color.parseColor("#FF1976D2"))
                    } else {
                        setColor(Color.parseColor("#FFF0F0F0")) // Light neutral
                        setStroke(dpToPx(1), Color.parseColor("#FFDDDDDD"))
                    }
                }
            }

            val serviceButtons = mutableListOf<Pair<String, TextView>>()
            val dayButtons = mutableListOf<Pair<Long, TextView>>()

            fun updateTimeDisplay() {
                if (selectedPreliminaryTimeMinute != null) {
                    val m = selectedPreliminaryTimeMinute!!
                    val hours = m / 60
                    val minutes = m % 60
                    timeButton.text = String.format(Locale.getDefault(), "%02d:%02d", hours, minutes)
                    timeButton.background = createChipBackground(selected = true)
                    timeButton.setTextColor(Color.WHITE)
                    timeClearButton.visibility = View.VISIBLE
                } else {
                    timeButton.text = "Wybierz godzinę"
                    timeButton.background = createChipBackground(selected = false)
                    timeButton.setTextColor(Color.parseColor("#FF333333"))
                    timeClearButton.visibility = View.GONE
                }
            }

            fun resetJobSection() {
                createJob = false
                jobCheckbox.isChecked = false
                jobDetailsContainer.visibility = View.GONE

                // Reset service selection
                selectedServiceId = null
                serviceButtons.forEach { (_, btn) ->
                    btn.background = createChipBackground(selected = false)
                    btn.setTextColor(Color.parseColor("#FF333333"))
                }

                // Reset day selection
                selectedPreliminaryDateEpochDay = null
                dayButtons.forEach { (_, btn) ->
                    btn.background = createChipBackground(selected = false)
                    btn.setTextColor(Color.parseColor("#FF333333"))
                }

                // Reset time selection
                selectedPreliminaryTimeMinute = null
                updateTimeDisplay()
                scheduleDraftSave()
            }

            if (client != null) {
                clientSection.visibility = View.GONE
                jobSection.visibility = View.VISIBLE
                jobCheckbox.text = "+ Nowe zlecenie"
                jobCheckbox.isChecked = createJob
                jobDetailsContainer.visibility = if (createJob) View.VISIBLE else View.GONE
            } else {
                clientSection.visibility = View.VISIBLE
                clientCheckbox.isChecked = isMarkAsClient
                jobSection.visibility = if (isMarkAsClient) View.VISIBLE else View.GONE
                jobCheckbox.text = "Utwórz zlecenie"
                jobCheckbox.isChecked = createJob
                jobDetailsContainer.visibility = if (createJob) View.VISIBLE else View.GONE

                clientCheckbox.setOnCheckedChangeListener { _, isChecked ->
                    isMarkAsClient = isChecked
                    if (isChecked) {
                        jobSection.visibility = View.VISIBLE
                        // Do not auto-check jobCheckbox
                    } else {
                        jobSection.visibility = View.GONE
                        resetJobSection()
                    }
                    scheduleDraftSave()
                }
            }

            // 9. Job details toggle
            jobCheckbox.setOnCheckedChangeListener { _, isChecked ->
                createJob = isChecked
                if (isChecked) {
                    jobDetailsContainer.visibility = View.VISIBLE
                } else {
                    resetJobSection()
                }
                scheduleDraftSave()
            }

            // Populate Services (all active services, no take(3) limit)
            serviceContainer.removeAllViews()
            serviceButtons.clear()
            services.forEach { s ->
                val isSelected = (s.id == selectedServiceId)
                val btn = TextView(this@CallOverlayService).apply {
                    text = s.name
                    textSize = 12f
                    setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
                    background = createChipBackground(selected = isSelected)
                    setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#FF333333"))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = dpToPx(8)
                    }
                }
                serviceButtons.add(s.id to btn)

                btn.setOnClickListener {
                    if (selectedServiceId == s.id) {
                        // Deselect if already selected
                        selectedServiceId = null
                    } else {
                        selectedServiceId = s.id
                    }
                    serviceButtons.forEach { (id, otherBtn) ->
                        val sel = (id == selectedServiceId)
                        otherBtn.background = createChipBackground(sel)
                        otherBtn.setTextColor(if (sel) Color.WHITE else Color.parseColor("#FF333333"))
                    }
                    scheduleDraftSave()
                }
                serviceContainer.addView(btn)
            }

            // Populate Days (Dziś, Jutro, and next upcoming days of week: Pon, Wt, Śr, Czw, Pt, Sob, Niedz)
            dayContainer.removeAllViews()
            dayButtons.clear()
            val today = LocalDate.now()
            for (i in 0 until 7) {
                val date = today.plusDays(i.toLong())
                val epochDay = date.toEpochDay()
                val label = when (i) {
                    0 -> "Dziś"
                    1 -> "Jutro"
                    else -> when (date.dayOfWeek) {
                        DayOfWeek.MONDAY -> "Pon"
                        DayOfWeek.TUESDAY -> "Wt"
                        DayOfWeek.WEDNESDAY -> "Śr"
                        DayOfWeek.THURSDAY -> "Czw"
                        DayOfWeek.FRIDAY -> "Pt"
                        DayOfWeek.SATURDAY -> "Sob"
                        DayOfWeek.SUNDAY -> "Niedz"
                        null -> date.dayOfWeek.name
                    }
                }

                val isSelected = (epochDay == selectedPreliminaryDateEpochDay)
                val btn = TextView(this@CallOverlayService).apply {
                    text = label
                    textSize = 12f
                    setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
                    background = createChipBackground(selected = isSelected)
                    setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#FF333333"))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = dpToPx(8)
                    }
                }
                dayButtons.add(epochDay to btn)

                btn.setOnClickListener {
                    if (selectedPreliminaryDateEpochDay == epochDay) {
                        // Deselect if already selected
                        selectedPreliminaryDateEpochDay = null
                    } else {
                        selectedPreliminaryDateEpochDay = epochDay
                    }
                    dayButtons.forEach { (d, otherBtn) ->
                        val sel = (d == selectedPreliminaryDateEpochDay)
                        otherBtn.background = createChipBackground(sel)
                        otherBtn.setTextColor(if (sel) Color.WHITE else Color.parseColor("#FF333333"))
                    }
                    scheduleDraftSave()
                }
                dayContainer.addView(btn)
            }

            // Time Picker setup
            updateTimeDisplay()
            timeButton.setOnClickListener {
                val defaultHour = if (selectedPreliminaryTimeMinute != null) {
                    selectedPreliminaryTimeMinute!! / 60
                } else {
                    LocalTime.now().hour
                }
                val defaultMinute = if (selectedPreliminaryTimeMinute != null) {
                    selectedPreliminaryTimeMinute!! % 60
                } else {
                    0
                }

                val timePickerDialog = TimePickerDialog(
                    this@CallOverlayService,
                    { _, hourOfDay, minute ->
                        selectedPreliminaryTimeMinute = hourOfDay * 60 + minute
                        updateTimeDisplay()
                        scheduleDraftSave()
                    },
                    defaultHour,
                    defaultMinute,
                    true // 24-hour format
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    timePickerDialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                }
                timePickerDialog.show()
            }

            timeClearButton.setOnClickListener {
                selectedPreliminaryTimeMinute = null
                updateTimeDisplay()
                scheduleDraftSave()
            }

            isRestoringDraft = false

            // Helper to commit and close
            fun commitAndClose(toTasks: Boolean) {
                isCommitted = true
                draftSaveJob?.cancel()
                draftSaveJob = null
                currentDraftProvider = null
                releaseOverlayFocus()
                val noteText = overlayNote.text?.toString() ?: ""

                app.container.callDraftRepository.tryClaimManualCommit(currentSessionId)

                app.container.appScope.launch {
                    val clientDisplayName = when {
                        client != null -> client.displayName
                        isMarkAsClient -> app.container.contactLookupRepository.resolveDisplayName(key)
                        else -> null
                    }

                    val req = OverlayCommitRequest(
                        callSessionId = currentSessionId,
                        phone = key,
                        noteText = noteText,
                        markAsClient = isMarkAsClient,
                        clientDisplayName = clientDisplayName,
                        createJob = createJob,
                        serviceId = selectedServiceId,
                        preliminaryDateEpochDay = selectedPreliminaryDateEpochDay,
                        preliminaryTimeMinute = selectedPreliminaryTimeMinute,
                        createOpenTask = toTasks,
                        callDirection = currentDirection,
                        callTimestamp = currentCallTimestamp
                    )
                    app.container.callDraftRepository.commitOverlaySession(req)
                }
                hideOverlayWindow()
                stopSelf()
            }

            // 10. "Do Zadań"
            taskButton.setOnClickListener {
                commitAndClose(toTasks = true)
            }

            // 11. "Zapisz"
            saveButton.setOnClickListener {
                commitAndClose(toTasks = false)
            }

            // 12. Close X
            overlayClose.setOnClickListener {
                draftSaveJob?.cancel()
                draftSaveJob = null
                releaseOverlayFocus()
                val draft = currentDraftProvider?.invoke()
                currentDraftProvider = null
                if (draft != null && draft.noteText.isNotBlank()) {
                    app.container.appScope.launch {
                        app.container.callDraftRepository.saveDraft(draft)
                    }
                }
                hideOverlayWindow()
                stopSelf()
            }

            try {
                windowManager?.addView(container, params)
                overlayView = container
            } catch (e: Exception) {
                // WindowManager add failed
            }
        }
    }

    private class OverlayContainer(
        context: Context,
        private val maxHeightPx: Int
    ) : FrameLayout(context) {

        var noteEditText: EditText? = null
        var onReleaseFocus: (() -> Unit)? = null

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val mode = MeasureSpec.getMode(heightMeasureSpec)
            val size = MeasureSpec.getSize(heightMeasureSpec)
            val newHeightSpec = if (mode == MeasureSpec.UNSPECIFIED || size > maxHeightPx) {
                MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST)
            } else {
                MeasureSpec.makeMeasureSpec(minOf(size, maxHeightPx), mode)
            }
            super.onMeasure(widthMeasureSpec, newHeightSpec)
        }

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (ev.action == MotionEvent.ACTION_DOWN) {
                val note = noteEditText
                if (note != null && note.hasFocus()) {
                    val rect = Rect()
                    note.getGlobalVisibleRect(rect)
                    if (!rect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                        onReleaseFocus?.invoke()
                    }
                }
            }
            return super.dispatchTouchEvent(ev)
        }
    }

    private fun hideOverlayWindow() {
        draftSaveJob?.cancel()
        draftSaveJob = null
        overlayView?.let {
            try {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(it.windowToken, 0)
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // Ignore
            }
            overlayView = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CallUpp Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Wyświetla podręczną notatkę podczas połączeń telefonicznych"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        draftSaveJob?.cancel()
        draftSaveJob = null
        currentDraftProvider = null
        hideOverlayWindow()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

