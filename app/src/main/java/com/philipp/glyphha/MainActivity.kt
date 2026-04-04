package com.philipp.glyphha

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class MainActivity : ComponentActivity() {
    private lateinit var baseUrlInput: TextInputEditText
    private lateinit var tokenInput: TextInputEditText
    private lateinit var entityIdInput: TextInputEditText
    private lateinit var maxValueInput: TextInputEditText
    private lateinit var modeSpinner: Spinner
    private lateinit var mappingsContainer: LinearLayout
    private lateinit var statusText: TextView

    private lateinit var store: SensorMappingStore

    private val mappings = mutableListOf<SensorMapping>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = SensorMappingStore(this)

        bindViews()
        setupModeSpinner()
        loadState()
        bindActions()
        updateAutomaticSync()
    }

    override fun onResume() {
        super.onResume()
        updateAutomaticSync()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun bindViews() {
        val rootScroll = findViewById<android.view.View>(R.id.rootScroll)
        val initialLeft = rootScroll.paddingLeft
        val initialTop = rootScroll.paddingTop
        val initialRight = rootScroll.paddingRight
        val initialBottom = rootScroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(rootScroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                initialLeft,
                initialTop + bars.top,
                initialRight,
                initialBottom + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(rootScroll)

        baseUrlInput = findViewById(R.id.baseUrlInput)
        tokenInput = findViewById(R.id.tokenInput)
        entityIdInput = findViewById(R.id.entityIdInput)
        maxValueInput = findViewById(R.id.maxValueInput)
        modeSpinner = findViewById(R.id.modeSpinner)
        mappingsContainer = findViewById(R.id.mappingsContainer)
        statusText = findViewById(R.id.statusText)
    }

    private fun setupModeSpinner() {
        val labels = listOf(getString(R.string.mode_progress), getString(R.string.mode_raw))
        modeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
    }

    private fun loadState() {
        baseUrlInput.setText(store.loadBaseUrl())
        tokenInput.setText(store.loadToken())
        mappings.clear()
        mappings.addAll(store.loadMappings())
        renderMappings()
        updateAutomaticSync()
    }

    private fun bindActions() {
        findViewById<MaterialButton>(R.id.saveConnectionButton).setOnClickListener {
            val baseUrl = baseUrlInput.text?.toString().orEmpty().trim()
            val token = tokenInput.text?.toString().orEmpty().trim()
            if (baseUrl.isBlank() || token.isBlank()) {
                toast("Enter both URL and token")
                return@setOnClickListener
            }
            store.saveConnection(baseUrl, token)
            toast("Connection saved")
            updateAutomaticSync()
        }

        findViewById<MaterialButton>(R.id.addSensorButton).setOnClickListener {
            val entityId = entityIdInput.text?.toString().orEmpty().trim()
            if (entityId.isBlank()) {
                toast("Entity ID is required")
                return@setOnClickListener
            }

            val mode = if (modeSpinner.selectedItemPosition == 0) {
                DisplayMode.PROGRESS
            } else {
                DisplayMode.RAW_NUMBER
            }

            val maxValue = maxValueInput.text?.toString()?.toDoubleOrNull() ?: 100.0
            mappings += SensorMapping(entityId = entityId, mode = mode, maxValue = maxValue)
            store.saveMappings(mappings)
            renderMappings()
            entityIdInput.setText("")
            updateAutomaticSync()
        }
    }

    private fun startPolling() {
        val intent = Intent(this, GlyphSyncForegroundService::class.java).apply {
            action = GlyphSyncForegroundService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        statusText.text = "Background sync active"
    }

    private fun stopPolling(reason: String = "Stopped") {
        val intent = Intent(this, GlyphSyncForegroundService::class.java).apply {
            action = GlyphSyncForegroundService.ACTION_STOP
        }
        startService(intent)
        statusText.text = reason
    }

    private fun renderMappings() {
        mappingsContainer.removeAllViews()

        mappings.forEachIndexed { index, mapping ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 8)
            }

            val label = TextView(this).apply {
                text = "${mapping.entityId} (${mapping.mode.name}, max=${mapping.maxValue})"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val deleteButton = Button(this).apply {
                text = "Delete"
                setOnClickListener {
                    mappings.removeAt(index)
                    store.saveMappings(mappings)
                    renderMappings()
                    updateAutomaticSync()
                }
            }

            row.addView(label)
            row.addView(deleteButton)
            mappingsContainer.addView(row)
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun updateAutomaticSync() {
        val hasConfig = store.loadBaseUrl().isNotBlank() && store.loadToken().isNotBlank()
        val hasMappings = mappings.isNotEmpty()

        if (hasConfig && hasMappings) {
            startPolling()
        } else {
            stopPolling(
                when {
                    !hasConfig -> "Waiting for Home Assistant credentials"
                    else -> "Waiting for sensor mappings"
                }
            )
        }
    }
}
