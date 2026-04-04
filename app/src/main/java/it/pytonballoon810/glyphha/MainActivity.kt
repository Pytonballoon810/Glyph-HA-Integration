package it.pytonballoon810.glyphha

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
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
import com.nothing.ketchum.Common
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText

class MainActivity : ComponentActivity() {
    private lateinit var baseUrlInput: TextInputEditText
    private lateinit var tokenInput: TextInputEditText
    private lateinit var entityIdInput: TextInputEditText
    private lateinit var maxValueInput: TextInputEditText
    private lateinit var secondaryTextEntityIdInput: TextInputEditText
    private lateinit var modeSpinner: Spinner
    private lateinit var appTabLayout: TabLayout
    private lateinit var mainTabContent: LinearLayout
    private lateinit var debugTabContent: LinearLayout
    private lateinit var completionIconSpinner: Spinner
    private lateinit var debugIconSpinner: Spinner
    private lateinit var customEditorTitle: TextView
    private lateinit var customIconEditor: PixelEditorView
    private lateinit var debugProgressValueInput: TextInputEditText
    private lateinit var debugProgressMaxInput: TextInputEditText
    private lateinit var debugRawTextInput: TextInputEditText
    private lateinit var mappingsContainer: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var glyphController: GlyphController

    private lateinit var store: SensorMappingStore
    private lateinit var iconOptions: List<IconOption>
    private var deviceMatrixSize: Int = 13

    private val mappings = mutableListOf<SensorMapping>()

    private data class IconOption(
        val type: CompletionIconType,
        val label: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = SensorMappingStore(this)
        glyphController = GlyphController(this)

        bindViews()
        setupModeSpinner()
        setupCompletionIconUi()
        setupTabLayout()
        loadState()
        bindActions()
        updateAutomaticSync()
    }

    override fun onResume() {
        super.onResume()
        updateAutomaticSync()
    }

    override fun onDestroy() {
        glyphController.stop()
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
        secondaryTextEntityIdInput = findViewById(R.id.secondaryTextEntityIdInput)
        modeSpinner = findViewById(R.id.modeSpinner)
        appTabLayout = findViewById(R.id.appTabLayout)
        mainTabContent = findViewById(R.id.mainTabContent)
        debugTabContent = findViewById(R.id.debugTabContent)
        completionIconSpinner = findViewById(R.id.completionIconSpinner)
        debugIconSpinner = findViewById(R.id.debugIconSpinner)
        customEditorTitle = findViewById(R.id.customEditorTitle)
        customIconEditor = findViewById(R.id.customIconEditor)
        debugProgressValueInput = findViewById(R.id.debugProgressValueInput)
        debugProgressMaxInput = findViewById(R.id.debugProgressMaxInput)
        debugRawTextInput = findViewById(R.id.debugRawTextInput)
        mappingsContainer = findViewById(R.id.mappingsContainer)
        statusText = findViewById(R.id.statusText)

        deviceMatrixSize = Common.getDeviceMatrixLength().coerceAtLeast(5)
        customIconEditor.setMatrixSize(deviceMatrixSize)
    }

    private fun setupModeSpinner() {
        val labels = listOf(getString(R.string.mode_progress), getString(R.string.mode_raw))
        modeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
    }

    private fun setupCompletionIconUi() {
        iconOptions = listOf(
            IconOption(CompletionIconType.PRINTER, getString(R.string.icon_printer)),
            IconOption(CompletionIconType.CHECK, getString(R.string.icon_check)),
            IconOption(CompletionIconType.TROPHY, getString(R.string.icon_trophy)),
            IconOption(CompletionIconType.CUSTOM, getString(R.string.icon_custom))
        )

        completionIconSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            iconOptions.map { it.label }
        )
        debugIconSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            iconOptions.map { it.label }
        )

        completionIconSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateCustomEditorVisibility(iconOptions[position].type)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
            }
        }
    }

    private fun setupTabLayout() {
        appTabLayout.removeAllTabs()
        appTabLayout.addTab(appTabLayout.newTab().setText(getString(R.string.tab_main)), true)
        appTabLayout.addTab(appTabLayout.newTab().setText(getString(R.string.tab_debug)))

        appTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showTab(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
                showTab(tab.position)
            }
        })

        showTab(0)
    }

    private fun loadState() {
        baseUrlInput.setText(store.loadBaseUrl())
        tokenInput.setText(store.loadToken())

        val savedType = store.loadCompletionIconType()
        val savedPixels = store.loadCustomIconData()
        savedPixels?.let { customIconEditor.setActivePixels(it.activePixels) }

        val selectedIndex = iconOptions.indexOfFirst { it.type == savedType }.takeIf { it >= 0 } ?: 0
        completionIconSpinner.setSelection(selectedIndex, false)
        debugIconSpinner.setSelection(selectedIndex, false)
        updateCustomEditorVisibility(iconOptions[selectedIndex].type)

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
            val secondaryEntity = secondaryTextEntityIdInput.text?.toString().orEmpty().trim().ifBlank { null }
            mappings += SensorMapping(
                entityId = entityId,
                mode = mode,
                maxValue = maxValue,
                secondaryTextEntityId = secondaryEntity
            )
            store.saveMappings(mappings)
            renderMappings()
            entityIdInput.setText("")
            secondaryTextEntityIdInput.setText("")
            updateAutomaticSync()
        }

        findViewById<MaterialButton>(R.id.saveCompletionIconButton).setOnClickListener {
            val selectedType = iconOptions[completionIconSpinner.selectedItemPosition].type
            store.saveCompletionIconType(selectedType)

            if (selectedType == CompletionIconType.CUSTOM) {
                store.saveCustomIconData(customIconEditor.getCustomIconData())
            }

            toast("Completion icon settings saved")
        }

        findViewById<MaterialButton>(R.id.clearCustomIconButton).setOnClickListener {
            customIconEditor.clearPixels()
            store.saveCustomIconData(customIconEditor.getCustomIconData())
            toast("Custom icon cleared")
        }

        findViewById<MaterialButton>(R.id.debugRenderProgressButton).setOnClickListener {
            val value = debugProgressValueInput.text?.toString()?.toDoubleOrNull()
            if (value == null) {
                toast("Enter a valid progress value")
                return@setOnClickListener
            }
            val max = debugProgressMaxInput.text?.toString()?.toDoubleOrNull()?.coerceAtLeast(1.0) ?: 100.0

            glyphController.start()
            glyphController.renderProgressRatio((value / max).coerceIn(0.0, 1.0))
            toast("Progress rendered")
        }

        findViewById<MaterialButton>(R.id.debugRenderRawButton).setOnClickListener {
            val text = debugRawTextInput.text?.toString().orEmpty().trim()
            if (text.isBlank()) {
                toast("Enter text or a number")
                return@setOnClickListener
            }

            glyphController.start()
            glyphController.renderRawText(text)
            toast("Number text rendered")
        }

        findViewById<MaterialButton>(R.id.debugRenderIconButton).setOnClickListener {
            val selectedType = iconOptions[debugIconSpinner.selectedItemPosition].type
            val customData = if (selectedType == CompletionIconType.CUSTOM) {
                customIconEditor.getCustomIconData()
            } else {
                store.loadCustomIconData()
            }

            glyphController.start()
            glyphController.renderCompletionBlink(
                showIcon = true,
                iconType = selectedType,
                customIconData = customData
            )
            toast("Completion icon rendered")
        }

        findViewById<MaterialButton>(R.id.debugClearButton).setOnClickListener {
            glyphController.start()
            glyphController.clearAppDisplay()
            toast("Glyph display cleared")
        }
    }

    private fun updateCustomEditorVisibility(type: CompletionIconType) {
        val isCustom = type == CompletionIconType.CUSTOM
        customEditorTitle.visibility = if (isCustom) View.VISIBLE else View.GONE
        customIconEditor.visibility = if (isCustom) View.VISIBLE else View.GONE
        findViewById<View>(R.id.clearCustomIconButton).visibility = if (isCustom) View.VISIBLE else View.GONE
    }

    private fun showTab(position: Int) {
        val showMain = position == 0
        mainTabContent.visibility = if (showMain) View.VISIBLE else View.GONE
        debugTabContent.visibility = if (showMain) View.GONE else View.VISIBLE

        if (showMain) {
            updateAutomaticSync()
        } else {
            stopPolling("Debug mode active")
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
                val secondary = mapping.secondaryTextEntityId?.let { ", sub=$it" } ?: ""
                text = "${mapping.entityId} (${mapping.mode.name}, max=${mapping.maxValue}$secondary)"
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
