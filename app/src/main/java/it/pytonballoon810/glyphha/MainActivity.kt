package it.pytonballoon810.glyphha

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.nothing.ketchum.Common

class MainActivity : ComponentActivity() {
    private lateinit var baseUrlInput: TextInputEditText
    private lateinit var tokenInput: TextInputEditText
    private lateinit var progressEntityIdInput: TextInputEditText
    private lateinit var maxValueInput: TextInputEditText
    private lateinit var remainingTimeEntityIdInput: TextInputEditText
    private lateinit var interruptedEntityIdInput: TextInputEditText
    private lateinit var turnOffValueInput: TextInputEditText
    private lateinit var resetValueInput: TextInputEditText
    private lateinit var genericErrorEntityIdInput: TextInputEditText
    private lateinit var genericErrorTriggerValueInput: TextInputEditText
    private lateinit var useCaseSpinner: Spinner
    private lateinit var genericModeSpinner: Spinner
    private lateinit var genericModeTitle: TextView
    private lateinit var genericControlsTitle: TextView
    private lateinit var appTabLayout: TabLayout
    private lateinit var mainTabContent: LinearLayout
    private lateinit var debugTabContent: LinearLayout
    private lateinit var completionIconSpinner: Spinner
    private lateinit var errorIconSpinner: Spinner
    private lateinit var debugIconSpinner: Spinner
    private lateinit var customEditorTitle: TextView
    private lateinit var customIconEditor: PixelEditorView
    private lateinit var debugProgressValueInput: TextInputEditText
    private lateinit var debugProgressMaxInput: TextInputEditText
    private lateinit var debugRawTextInput: TextInputEditText
    private lateinit var debugCurrentRenderDataText: TextView
    private lateinit var mappingsContainer: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var glyphController: GlyphController

    private lateinit var store: SensorMappingStore
    private lateinit var iconOptions: List<IconOption>
    private lateinit var useCaseOptions: List<UseCaseOption>
    private lateinit var genericModeOptions: List<GenericModeOption>
    private var deviceMatrixSize: Int = 13

    private val mappings = mutableListOf<SensorMapping>()
    private val debugRenderHandler = Handler(Looper.getMainLooper())
    private val debugRenderUpdater = object : Runnable {
        override fun run() {
            updateCurrentRenderDataPanel()
            if (debugTabContent.visibility == View.VISIBLE) {
                debugRenderHandler.postDelayed(this, DEBUG_RENDER_REFRESH_MS)
            }
        }
    }

    private data class IconOption(
        val type: CompletionIconType,
        val label: String
    )

    private data class UseCaseOption(
        val useCase: UseCaseType,
        val label: String
    )

    private data class GenericModeOption(
        val mode: GenericDisplayMode,
        val label: String
    )

    private data class MappingConfig(
        val useCase: UseCaseType,
        val progressEntityId: String,
        val maxValue: Double,
        val remainingEntityId: String?,
        val interruptedEntityId: String?,
        val genericMode: GenericDisplayMode,
        val turnOffValue: String?,
        val resetValue: String?,
        val genericErrorEntityId: String?,
        val genericErrorTriggerValue: String?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = SensorMappingStore(this)
        glyphController = GlyphController(this)

        bindViews()
        setupUseCaseSpinner()
        setupGenericModeSpinner()
        setupCompletionIconUi()
        setupTabLayout()
        loadState()
        bindActions()
        updateUseCaseFieldVisibility(useCaseOptions[useCaseSpinner.selectedItemPosition].useCase)
        updateAutomaticSync()
    }

    override fun onResume() {
        super.onResume()
        updateAutomaticSync()
    }

    override fun onPause() {
        if (::debugTabContent.isInitialized && debugTabContent.visibility == View.VISIBLE) {
            clearDebugRenderDisplay()
        }
        super.onPause()
    }

    override fun onDestroy() {
        stopCurrentRenderPolling()
        glyphController.stop()
        super.onDestroy()
    }

    private fun bindViews() {
        val rootScroll = findViewById<View>(R.id.rootScroll)
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
        progressEntityIdInput = findViewById(R.id.entityIdInput)
        maxValueInput = findViewById(R.id.maxValueInput)
        remainingTimeEntityIdInput = findViewById(R.id.secondaryTextEntityIdInput)
        interruptedEntityIdInput = findViewById(R.id.interruptedEntityIdInput)
        turnOffValueInput = findViewById(R.id.turnOffValueInput)
        resetValueInput = findViewById(R.id.resetValueInput)
        genericErrorEntityIdInput = findViewById(R.id.genericErrorEntityIdInput)
        genericErrorTriggerValueInput = findViewById(R.id.genericErrorTriggerValueInput)
        useCaseSpinner = findViewById(R.id.useCaseSpinner)
        genericModeSpinner = findViewById(R.id.genericModeSpinner)
        genericModeTitle = findViewById(R.id.genericModeTitle)
        genericControlsTitle = findViewById(R.id.genericControlsTitle)
        appTabLayout = findViewById(R.id.appTabLayout)
        mainTabContent = findViewById(R.id.mainTabContent)
        debugTabContent = findViewById(R.id.debugTabContent)
        completionIconSpinner = findViewById(R.id.completionIconSpinner)
        errorIconSpinner = findViewById(R.id.errorIconSpinner)
        debugIconSpinner = findViewById(R.id.debugIconSpinner)
        customEditorTitle = findViewById(R.id.customEditorTitle)
        customIconEditor = findViewById(R.id.customIconEditor)
        debugProgressValueInput = findViewById(R.id.debugProgressValueInput)
        debugProgressMaxInput = findViewById(R.id.debugProgressMaxInput)
        debugRawTextInput = findViewById(R.id.debugRawTextInput)
        debugCurrentRenderDataText = findViewById(R.id.debugCurrentRenderDataText)
        mappingsContainer = findViewById(R.id.mappingsContainer)
        statusText = findViewById(R.id.statusText)

        deviceMatrixSize = Common.getDeviceMatrixLength().coerceAtLeast(5)
        customIconEditor.setMatrixSize(deviceMatrixSize)
    }

    private fun setupUseCaseSpinner() {
        useCaseOptions = listOf(
            UseCaseOption(
                useCase = UseCaseType.TRACK_3D_PRINTER_PROGRESS,
                label = getString(R.string.usecase_printer_tracking)
            ),
            UseCaseOption(
                useCase = UseCaseType.TRACK_GENERIC_SENSOR,
                label = getString(R.string.usecase_generic_tracking)
            )
        )

        useCaseSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            useCaseOptions.map { it.label }
        )

        useCaseSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateUseCaseFieldVisibility(useCaseOptions[position].useCase)
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
            }
        }
    }

    private fun setupGenericModeSpinner() {
        genericModeOptions = listOf(
            GenericModeOption(GenericDisplayMode.PROGRESS, getString(R.string.generic_mode_progress)),
            GenericModeOption(GenericDisplayMode.NUMBER, getString(R.string.generic_mode_number))
        )

        genericModeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            genericModeOptions.map { it.label }
        )
    }

    private fun setupCompletionIconUi() {
        iconOptions = listOf(
            IconOption(CompletionIconType.PRINTER, getString(R.string.icon_printer)),
            IconOption(CompletionIconType.CHECK, getString(R.string.icon_check)),
            IconOption(CompletionIconType.CROSS, getString(R.string.icon_cross)),
            IconOption(CompletionIconType.TROPHY, getString(R.string.icon_trophy)),
            IconOption(CompletionIconType.CUSTOM, getString(R.string.icon_custom))
        )

        completionIconSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            iconOptions.map { it.label }
        )
        errorIconSpinner.adapter = ArrayAdapter(
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
                updateCustomEditorVisibility()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
            }
        }

        errorIconSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateCustomEditorVisibility()
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
        val savedErrorType = store.loadErrorIconType()
        val savedPixels = store.loadCustomIconData()
        savedPixels?.let { customIconEditor.setActivePixels(it.activePixels) }

        val selectedIndex = iconOptions.indexOfFirst { it.type == savedType }.takeIf { it >= 0 } ?: 0
        val selectedErrorIndex = iconOptions.indexOfFirst { it.type == savedErrorType }.takeIf { it >= 0 } ?: 0
        completionIconSpinner.setSelection(selectedIndex, false)
        errorIconSpinner.setSelection(selectedErrorIndex, false)
        debugIconSpinner.setSelection(selectedIndex, false)
        updateCustomEditorVisibility()

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
                toast(getString(R.string.toast_enter_url_token))
                return@setOnClickListener
            }
            store.saveConnection(baseUrl, token)
            toast(getString(R.string.toast_connection_saved))
            updateAutomaticSync()
        }

        findViewById<MaterialButton>(R.id.addSensorButton).setOnClickListener {
            val primaryEntityId = progressEntityIdInput.text?.toString().orEmpty().trim()
            if (primaryEntityId.isBlank()) {
                toast(getString(R.string.toast_entity_required))
                return@setOnClickListener
            }

            val config = MappingConfig(
                useCase = useCaseOptions[useCaseSpinner.selectedItemPosition].useCase,
                progressEntityId = primaryEntityId,
                maxValue = maxValueInput.text?.toString()?.toDoubleOrNull() ?: 100.0,
                remainingEntityId = remainingTimeEntityIdInput.text?.toString().orEmpty().trim().ifBlank { null },
                interruptedEntityId = interruptedEntityIdInput.text?.toString().orEmpty().trim().ifBlank { null },
                genericMode = genericModeOptions[genericModeSpinner.selectedItemPosition].mode,
                turnOffValue = turnOffValueInput.text?.toString().orEmpty().trim().ifBlank { null },
                resetValue = resetValueInput.text?.toString().orEmpty().trim().ifBlank { null },
                genericErrorEntityId = genericErrorEntityIdInput.text?.toString().orEmpty().trim().ifBlank { null },
                genericErrorTriggerValue = genericErrorTriggerValueInput.text?.toString().orEmpty().trim().ifBlank { null }
            )

            mappings += toSensorMapping(config)

            store.saveMappings(mappings)
            renderMappings()
            resetMappingFormInputs()
            notifyMappingsUpdated()
            updateAutomaticSync()
        }

        findViewById<MaterialButton>(R.id.saveCompletionIconButton).setOnClickListener {
            val selectedType = iconOptions[completionIconSpinner.selectedItemPosition].type
            val selectedErrorType = iconOptions[errorIconSpinner.selectedItemPosition].type
            store.saveCompletionIconType(selectedType)
            store.saveErrorIconType(selectedErrorType)

            if (selectedType == CompletionIconType.CUSTOM || selectedErrorType == CompletionIconType.CUSTOM) {
                store.saveCustomIconData(customIconEditor.getCustomIconData())
            }

            toast(getString(R.string.toast_completion_icon_saved))
        }

        findViewById<MaterialButton>(R.id.clearCustomIconButton).setOnClickListener {
            customIconEditor.clearPixels()
            store.saveCustomIconData(customIconEditor.getCustomIconData())
            toast(getString(R.string.toast_custom_icon_cleared))
        }

        findViewById<MaterialButton>(R.id.debugRenderProgressButton).setOnClickListener {
            val value = debugProgressValueInput.text?.toString()?.toDoubleOrNull()
            if (value == null) {
                toast(getString(R.string.toast_enter_valid_progress))
                return@setOnClickListener
            }
            val max = debugProgressMaxInput.text?.toString()?.toDoubleOrNull()?.coerceAtLeast(1.0) ?: 100.0

            glyphController.start()
            glyphController.renderProgressRatio((value / max).coerceIn(0.0, 1.0))
            updateCurrentRenderDataPanel()
            toast(getString(R.string.toast_progress_rendered))
        }

        findViewById<MaterialButton>(R.id.debugRenderRawButton).setOnClickListener {
            val text = debugRawTextInput.text?.toString().orEmpty().trim()
            if (text.isBlank()) {
                toast(getString(R.string.toast_enter_text_or_number))
                return@setOnClickListener
            }

            glyphController.start()
            glyphController.renderRawText(text)
            updateCurrentRenderDataPanel()
            toast(getString(R.string.toast_number_rendered))
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
            updateCurrentRenderDataPanel()
            toast(getString(R.string.toast_icon_rendered))
        }

        findViewById<MaterialButton>(R.id.debugClearButton).setOnClickListener {
            glyphController.start()
            glyphController.clearAppDisplay()
            updateCurrentRenderDataPanel()
            toast(getString(R.string.toast_display_cleared))
        }
    }

    private fun updateUseCaseFieldVisibility(useCase: UseCaseType) {
        val printerVisible = useCase == UseCaseType.TRACK_3D_PRINTER_PROGRESS
        val genericVisible = useCase == UseCaseType.TRACK_GENERIC_SENSOR

        setViewAndParentVisibility(remainingTimeEntityIdInput, printerVisible)
        setViewAndParentVisibility(interruptedEntityIdInput, printerVisible)

        genericModeTitle.visibility = if (genericVisible) View.VISIBLE else View.GONE
        genericModeSpinner.visibility = if (genericVisible) View.VISIBLE else View.GONE
        genericControlsTitle.visibility = if (genericVisible) View.VISIBLE else View.GONE
        setViewAndParentVisibility(turnOffValueInput, genericVisible)
        setViewAndParentVisibility(resetValueInput, genericVisible)
        setViewAndParentVisibility(genericErrorEntityIdInput, genericVisible)
        setViewAndParentVisibility(genericErrorTriggerValueInput, genericVisible)
    }

    private fun setViewAndParentVisibility(view: View, visible: Boolean) {
        val target = if (view.parent is View) view.parent as View else view
        target.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun updateCustomEditorVisibility() {
        val completionType = iconOptions.getOrNull(completionIconSpinner.selectedItemPosition)?.type
        val errorType = iconOptions.getOrNull(errorIconSpinner.selectedItemPosition)?.type
        val isCustom = completionType == CompletionIconType.CUSTOM || errorType == CompletionIconType.CUSTOM
        customEditorTitle.visibility = if (isCustom) View.VISIBLE else View.GONE
        customIconEditor.visibility = if (isCustom) View.VISIBLE else View.GONE
        findViewById<View>(R.id.clearCustomIconButton).visibility = if (isCustom) View.VISIBLE else View.GONE
    }

    private fun showTab(position: Int) {
        val wasDebugVisible = debugTabContent.visibility == View.VISIBLE
        val showMain = position == 0
        mainTabContent.visibility = if (showMain) View.VISIBLE else View.GONE
        debugTabContent.visibility = if (showMain) View.GONE else View.VISIBLE

        if (showMain) {
            stopCurrentRenderPolling()
            if (wasDebugVisible) {
                clearDebugRenderDisplay()
            }
            updateAutomaticSync()
        } else {
            stopPolling(getString(R.string.status_debug_mode_active))
            startCurrentRenderPolling()
        }
    }

    private fun clearDebugRenderDisplay() {
        glyphController.start()
        glyphController.clearAppDisplay()
    }

    private fun startCurrentRenderPolling() {
        debugRenderHandler.removeCallbacks(debugRenderUpdater)
        debugRenderUpdater.run()
    }

    private fun stopCurrentRenderPolling() {
        debugRenderHandler.removeCallbacks(debugRenderUpdater)
    }

    private fun updateCurrentRenderDataPanel() {
        debugCurrentRenderDataText.text = glyphController.getCurrentRenderData()
    }

    private fun startPolling() {
        val intent = Intent(this, GlyphSyncForegroundService::class.java).apply {
            action = GlyphSyncForegroundService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
        statusText.text = getString(R.string.status_background_sync_active)
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
                text = mapping.toDisplayLabel()
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val editButton = Button(this).apply {
                text = getString(R.string.edit_mapping)
                setOnClickListener {
                    showEditMappingDialog(index, mapping)
                }
            }

            val deleteButton = Button(this).apply {
                text = getString(R.string.delete_mapping)
                setOnClickListener {
                    mappings.removeAt(index)
                    store.saveMappings(mappings)
                    renderMappings()
                    notifyMappingsUpdated()
                    updateAutomaticSync()
                }
            }

            row.addView(label)
            row.addView(editButton)
            row.addView(deleteButton)
            mappingsContainer.addView(row)
        }
    }

    private fun showEditMappingDialog(index: Int, mapping: SensorMapping) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 20, 32, 0)
        }

        val progressInput = EditText(this).apply {
            hint = getString(R.string.hint_entity_id)
            setText(mapping.progressEntityId)
        }

        val useCaseInput = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                useCaseOptions.map { it.label }
            )
            val selectedIndex = useCaseOptions.indexOfFirst { it.useCase == mapping.useCase }
                .takeIf { it >= 0 }
                ?: 0
            setSelection(selectedIndex)
        }

        val genericModeInput = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                genericModeOptions.map { it.label }
            )
            val selectedIndex = genericModeOptions.indexOfFirst { it.mode == mapping.genericDisplayMode }
                .takeIf { it >= 0 }
                ?: 0
            setSelection(selectedIndex)
        }

        val maxInput = EditText(this).apply {
            hint = getString(R.string.hint_max_value)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(mapping.maxValue.toString())
        }

        val remainingInput = EditText(this).apply {
            hint = getString(R.string.hint_secondary_text_entity_id)
            setText(mapping.remainingTimeEntityId.orEmpty())
        }

        val interruptedInput = EditText(this).apply {
            hint = getString(R.string.hint_interrupted_entity_id)
            setText(mapping.interruptedEntityId.orEmpty())
        }

        val turnOffInput = EditText(this).apply {
            hint = getString(R.string.hint_turn_off_value)
            setText(mapping.turnOffValue.orEmpty())
        }

        val resetInput = EditText(this).apply {
            hint = getString(R.string.hint_reset_value)
            setText(mapping.resetValue.orEmpty())
        }

        val genericErrorEntityInput = EditText(this).apply {
            hint = getString(R.string.hint_generic_error_entity_id)
            setText(mapping.genericErrorEntityId.orEmpty())
        }

        val genericErrorTriggerInput = EditText(this).apply {
            hint = getString(R.string.hint_generic_error_trigger_value)
            setText(mapping.genericErrorTriggerValue.orEmpty())
        }

        container.addView(progressInput)
        container.addView(useCaseInput)
        container.addView(genericModeInput)
        container.addView(maxInput)
        container.addView(remainingInput)
        container.addView(interruptedInput)
        container.addView(turnOffInput)
        container.addView(resetInput)
        container.addView(genericErrorEntityInput)
        container.addView(genericErrorTriggerInput)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.edit_mapping))
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.save_mapping_changes)) { _, _ ->
                val progressEntityId = progressInput.text?.toString().orEmpty().trim()
                if (progressEntityId.isBlank()) {
                    toast(getString(R.string.toast_entity_required))
                    return@setPositiveButton
                }

                val config = MappingConfig(
                    useCase = useCaseOptions[useCaseInput.selectedItemPosition].useCase,
                    progressEntityId = progressEntityId,
                    maxValue = maxInput.text?.toString()?.toDoubleOrNull() ?: 100.0,
                    remainingEntityId = remainingInput.text?.toString().orEmpty().trim().ifBlank { null },
                    interruptedEntityId = interruptedInput.text?.toString().orEmpty().trim().ifBlank { null },
                    genericMode = genericModeOptions[genericModeInput.selectedItemPosition].mode,
                    turnOffValue = turnOffInput.text?.toString().orEmpty().trim().ifBlank { null },
                    resetValue = resetInput.text?.toString().orEmpty().trim().ifBlank { null },
                    genericErrorEntityId = genericErrorEntityInput.text?.toString().orEmpty().trim().ifBlank { null },
                    genericErrorTriggerValue = genericErrorTriggerInput.text?.toString().orEmpty().trim().ifBlank { null }
                )

                mappings[index] = toSensorMapping(config)

                store.saveMappings(mappings)
                renderMappings()
                notifyMappingsUpdated()
                updateAutomaticSync()
                toast(getString(R.string.toast_mapping_updated))
            }
            .show()
    }

    private fun toSensorMapping(config: MappingConfig): SensorMapping {
        return SensorMapping(
            useCase = config.useCase,
            progressEntityId = config.progressEntityId,
            maxValue = config.maxValue,
            remainingTimeEntityId = if (config.useCase == UseCaseType.TRACK_3D_PRINTER_PROGRESS) config.remainingEntityId else null,
            interruptedEntityId = if (config.useCase == UseCaseType.TRACK_3D_PRINTER_PROGRESS) config.interruptedEntityId else null,
            genericDisplayMode = if (config.useCase == UseCaseType.TRACK_GENERIC_SENSOR) config.genericMode else GenericDisplayMode.NUMBER,
            turnOffValue = if (config.useCase == UseCaseType.TRACK_GENERIC_SENSOR) config.turnOffValue else null,
            resetValue = if (config.useCase == UseCaseType.TRACK_GENERIC_SENSOR) config.resetValue else null,
            genericErrorEntityId = if (config.useCase == UseCaseType.TRACK_GENERIC_SENSOR) config.genericErrorEntityId else null,
            genericErrorTriggerValue = if (config.useCase == UseCaseType.TRACK_GENERIC_SENSOR) config.genericErrorTriggerValue else null
        )
    }

    private fun resetMappingFormInputs() {
        progressEntityIdInput.setText("")
        remainingTimeEntityIdInput.setText("")
        interruptedEntityIdInput.setText("")
        turnOffValueInput.setText("")
        resetValueInput.setText("")
        genericErrorEntityIdInput.setText("")
        genericErrorTriggerValueInput.setText("")
    }

    private fun SensorMapping.toDisplayLabel(): String {
        return when (useCase) {
            UseCaseType.TRACK_3D_PRINTER_PROGRESS -> {
                val remaining = remainingTimeEntityId?.let { ", time=$it" } ?: ""
                val interrupted = interruptedEntityId?.let { ", interrupted=$it" } ?: ""
                "Printer: $progressEntityId (max=$maxValue$remaining$interrupted)"
            }

            UseCaseType.TRACK_GENERIC_SENSOR -> {
                val turnOff = turnOffValue?.let { ", off=$it" } ?: ""
                val reset = resetValue?.let { ", reset=$it" } ?: ""
                val errEntity = genericErrorEntityId?.let { ", err=$it" } ?: ""
                val errValue = genericErrorTriggerValue?.let { ", errValue=$it" } ?: ""
                "Generic: $progressEntityId (${genericDisplayMode.name}, max=$maxValue$turnOff$reset$errEntity$errValue)"
            }
        }
    }

    private fun notifyMappingsUpdated() {
        val intent = Intent(this, GlyphSyncForegroundService::class.java).apply {
            action = GlyphSyncForegroundService.ACTION_MAPPINGS_UPDATED
        }
        ContextCompat.startForegroundService(this, intent)
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
                    !hasConfig -> getString(R.string.status_waiting_credentials)
                    else -> getString(R.string.status_waiting_mappings)
                }
            )
        }
    }

    companion object {
        private const val DEBUG_RENDER_REFRESH_MS = 800L
    }
}
