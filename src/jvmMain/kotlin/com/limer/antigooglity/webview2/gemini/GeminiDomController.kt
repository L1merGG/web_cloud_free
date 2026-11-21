package com.limer.antigooglity.webview2.gemini

import com.limer.antigooglity.webview2.ScriptExecutor
import com.limer.antigooglity.webview2.SimpleJsonValue
import com.limer.antigooglity.webview2.WebView2Command
import com.limer.antigooglity.webview2.WebView2HostClient
import kotlinx.coroutines.delay

data class GeminiPageState(
    val isScriptAvailable: Boolean,
    val isDocumentLoaded: Boolean = false,
    val isGeminiUrl: Boolean = false,
    val isChatReady: Boolean = false,
    val hasEditor: Boolean = false,
    val isEditorEmpty: Boolean = true,
    val isEditorAccessible: Boolean = false,
    val hasSendButton: Boolean = false,
    val isSendButtonDisabled: Boolean = true,
    val isGenerating: Boolean = false,
    val isInitialLoading: Boolean = false,
) {
    val isLoaded: Boolean
        get() = isScriptAvailable && isDocumentLoaded

    val isEditorReady: Boolean
        get() = isLoaded && isGeminiUrl && hasEditor && isEditorAccessible
}

data class GeminiEditorReadyResult(
    val ready: Boolean,
    val state: GeminiPageState? = null,
    val failureReason: String? = null,
)

data class GeminiMessageItem(
    val id: String,
    val text: String,
)

data class GeminiConversationSnapshot(
    val userQueryCount: Int = 0,
    val modelResponseCount: Int = 0,
    val lastUserQuery: String = "",
    val lastModelResponse: String = "",
    val modelMessageIds: List<String> = emptyList(),
    val userMessageIds: List<String> = emptyList(),
    val isGenerating: Boolean = false,
)

data class GeminiSendResult(
    val success: Boolean,
    val error: String? = null,
    val snapshot: GeminiConversationSnapshot? = null,
)

data class GeminiResponseResult(
    val found: Boolean,
    val text: String,
    val partialText: String? = null,
    val error: String? = null,
)

/**
 * Controller for Gemini Web UI interactions via ScriptExecutor / WebView2HostClient.
 * Provides resilient DOM access: strict host/state audit, popup dismissal, send verification,
 * and correlation of turns via stable container identities.
 */
class GeminiDomController(
    private val executor: ScriptExecutor,
) {
    constructor(client: WebView2HostClient) : this(client as ScriptExecutor)

    /**
     * Inspects the detailed state of the Gemini page and DOM elements.
     * Clearly separates:
     * 1. isEditorReady: editor exists, has contenteditable, not disabled.
     * 2. isInitialLoading: spinners in history/sidebar/chat container.
     * 3. isGenerating: active generation of a model response.
     */
    suspend fun getPageState(): GeminiPageState {
        val checkScript = """
            (function() {
                var isHttps = window.location.protocol === 'https:';
                var isGeminiHost = window.location.hostname === 'gemini.google.com';
                var docLoaded = document.readyState === 'complete' || document.readyState === 'interactive';
                var editor = document.querySelector('.ql-editor');
                var sendBtn = document.querySelector('button[aria-label*="Отправить"]') ||
                              document.querySelector('button[aria-label*="Send"]');

                // Active response generation indicators
                var stopBtn = document.querySelector('button[aria-label*="Остановить"], button[aria-label*="Stop"], [data-test-id*="stop-generation"], button.stop-button');
                var isStopBtnActive = !!(stopBtn && (stopBtn.offsetParent !== null || stopBtn.offsetWidth > 0 || stopBtn.offsetHeight > 0));
                var activeGenElem = document.querySelector('.generating, model-response.generating, message-content.generating');
                var isGen = isStopBtnActive || !!activeGenElem;

                // Initial page/history loading indicators
                var spinnerElem = document.querySelector('mat-progress-spinner, [data-test-id*="spinner"], [data-test-id*="loading"]');
                var isInitLoading = !!spinnerElem;

                var chatReady = !!(document.querySelector('rich-textarea') || document.querySelector('.conversation-container') || editor);
                var editorAccessible = editor ? (editor.getAttribute('contenteditable') !== 'false' && !editor.disabled) : false;
                var editorEmpty = editor ? (editor.innerText || '').trim() === '' : true;

                return {
                    isDocumentLoaded: docLoaded,
                    isGeminiUrl: isHttps && isGeminiHost,
                    isChatReady: chatReady,
                    hasEditor: !!editor,
                    isEditorEmpty: editorEmpty,
                    isEditorAccessible: editorAccessible,
                    hasSendButton: !!sendBtn,
                    isSendButtonDisabled: sendBtn ? (sendBtn.disabled || sendBtn.getAttribute('aria-disabled') === 'true') : true,
                    isGenerating: isGen,
                    isInitialLoading: isInitLoading
                };
            })()
        """.trimIndent()

        val resp = executor.executeScript(checkScript)
        if (!resp.success) {
            return GeminiPageState(isScriptAvailable = false)
        }

        val resultObj = resp.getResultJson() as? SimpleJsonValue.JsonObject
        if (resultObj == null) {
            return GeminiPageState(isScriptAvailable = true, isDocumentLoaded = false)
        }

        return GeminiPageState(
            isScriptAvailable = true,
            isDocumentLoaded = resultObj.getBoolean("isDocumentLoaded") ?: false,
            isGeminiUrl = resultObj.getBoolean("isGeminiUrl") ?: false,
            isChatReady = resultObj.getBoolean("isChatReady") ?: false,
            hasEditor = resultObj.getBoolean("hasEditor") ?: false,
            isEditorEmpty = resultObj.getBoolean("isEditorEmpty") ?: true,
            isEditorAccessible = resultObj.getBoolean("isEditorAccessible") ?: false,
            hasSendButton = resultObj.getBoolean("hasSendButton") ?: false,
            isSendButtonDisabled = resultObj.getBoolean("isSendButtonDisabled") ?: true,
            isGenerating = resultObj.getBoolean("isGenerating") ?: false,
            isInitialLoading = resultObj.getBoolean("isInitialLoading") ?: false,
        )
    }

    /**
     * Dismisses only recognized informational/consent dialogs (e.g. location permission banners).
     */
    suspend fun dismissPopups(): Int {
        val dismissScript = """
            (function() {
                var dialogs = Array.from(document.querySelectorAll('mat-dialog-container, [role="dialog"], .location-banner, .consent-dialog, [data-test-id*="banner"]'));
                var dismissed = 0;
                dialogs.forEach(function(d) {
                    var text = (d.innerText || '').toLowerCase();
                    if (text.indexOf('местоположени') !== -1 || text.indexOf('location') !== -1 || text.indexOf('cookie') !== -1) {
                        var closeBtn = d.querySelector('button[aria-label*="Закрыть"], button[aria-label*="Close"], button.close, [data-test-id*="close"]');
                        if (!closeBtn) {
                            var buttons = Array.from(d.querySelectorAll('button'));
                            for (var i = 0; i < buttons.length; i++) {
                                var bt = (buttons[i].innerText || '').trim().toLowerCase();
                                if (bt === 'закрыть' || bt === 'close' || bt === 'понятно' || bt === 'отклонить') {
                                    closeBtn = buttons[i];
                                    break;
                                }
                            }
                        }
                        if (closeBtn) {
                            closeBtn.click();
                            dismissed++;
                        }
                    }
                });
                return { dismissedCount: dismissed };
            })()
        """.trimIndent()

        val resp = executor.executeScript(dismissScript)
        val resultObj = resp.getResultJson() as? SimpleJsonValue.JsonObject
        return resultObj?.getInt("dismissedCount") ?: 0
    }

    /**
     * Waits for the editor to become ready and accessible for typing.
     * Does NOT block on background loading indicators (isInitialLoading) if the editor is accessible.
     */
    suspend fun waitForEditorReady(timeoutMs: Long = 20000L, pollIntervalMs: Long = 500L): GeminiEditorReadyResult {
        val startTime = System.currentTimeMillis()
        var lastState: GeminiPageState? = null

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            dismissPopups()
            val state = getPageState()
            lastState = state

            // Editor is ready when page is loaded, on Gemini URL, editor exists and accessible, and no active response is generating
            if (state.isLoaded && state.isGeminiUrl && state.hasEditor && state.isEditorAccessible && !state.isGenerating) {
                return GeminiEditorReadyResult(ready = true, state = state)
            }
            delay(pollIntervalMs)
        }

        val reason = when {
            lastState == null -> "NO_PAGE_STATE_RESPONSE"
            !lastState.isScriptAvailable -> "SCRIPT_NOT_AVAILABLE"
            !lastState.isDocumentLoaded -> "DOCUMENT_NOT_LOADED"
            !lastState.isGeminiUrl -> "NOT_GEMINI_URL"
            !lastState.hasEditor -> "EDITOR_NOT_FOUND"
            !lastState.isEditorAccessible -> "EDITOR_NOT_ACCESSIBLE"
            lastState.isGenerating -> "GENERATION_STILL_IN_PROGRESS"
            else -> "EDITOR_READY_TIMEOUT"
        }

        return GeminiEditorReadyResult(ready = false, state = lastState, failureReason = reason)
    }

    /**
     * Captures a snapshot of current conversation turns in the DOM using top-level message containers.
     */
    suspend fun getConversationSnapshot(): GeminiConversationSnapshot {
        val snapshotScript = """
            (function() {
                // Find top-level user message containers
                var userNodes = Array.from(document.querySelectorAll('user-query, [data-test-id="user-query"], .user-query-container'));
                if (userNodes.length === 0) {
                    // Fallback to query content if containers aren't custom elements
                    userNodes = Array.from(document.querySelectorAll('.query-text, .user-query'));
                }
                // Filter out nested nodes
                userNodes = userNodes.filter(function(node) {
                    var parent = node.parentElement;
                    while (parent) {
                        if (userNodes.indexOf(parent) !== -1) return false;
                        parent = parent.parentElement;
                    }
                    return true;
                });

                var userMessages = userNodes.map(function(n, idx) {
                    var id = n.getAttribute('id') || n.getAttribute('data-message-id') || ('user-' + idx);
                    var txt = (n.innerText || '').trim();
                    return { id: id, text: txt };
                }).filter(function(m) { return m.text.length > 0; });

                // Find top-level model response containers
                var modelNodes = Array.from(document.querySelectorAll('model-response, [data-test-id="model-response"]'));
                if (modelNodes.length === 0) {
                    modelNodes = Array.from(document.querySelectorAll('.model-response-text, message-content, .response-content'));
                }
                // Exclude any element that is an ancestor or descendant of another in modelNodes
                modelNodes = modelNodes.filter(function(node) {
                    var parent = node.parentElement;
                    while (parent) {
                        if (modelNodes.indexOf(parent) !== -1) return false;
                        parent = parent.parentElement;
                    }
                    return true;
                });

                var modelMessages = modelNodes.map(function(n, idx) {
                    var id = n.getAttribute('id') || n.getAttribute('data-message-id') || ('model-' + idx);
                    var txt = (n.innerText || '').trim();
                    return { id: id, text: txt };
                }).filter(function(m) {
                    return m.text.length > 0 &&
                           !m.text.startsWith("Ваш запрос") &&
                           !m.text.startsWith("Your prompt") &&
                           !m.text.startsWith("С чего начнем") &&
                           !m.text.includes("Данные о местоположении");
                });

                var stopBtn = document.querySelector('button[aria-label*="Остановить"], button[aria-label*="Stop"], [data-test-id*="stop-generation"], button.stop-button');
                var isStopBtnActive = !!(stopBtn && (stopBtn.offsetParent !== null || stopBtn.offsetWidth > 0 || stopBtn.offsetHeight > 0));
                var activeGenElem = document.querySelector('.generating, model-response.generating, message-content.generating');
                var isGen = isStopBtnActive || !!activeGenElem;

                return {
                    userQueryCount: userMessages.length,
                    modelResponseCount: modelMessages.length,
                    lastUserQuery: userMessages.length > 0 ? userMessages[userMessages.length - 1].text : "",
                    lastModelResponse: modelMessages.length > 0 ? modelMessages[modelMessages.length - 1].text : "",
                    userMessageIds: userMessages.map(function(m) { return m.id; }),
                    modelMessageIds: modelMessages.map(function(m) { return m.id; }),
                    isGenerating: isGen
                };
            })()
        """.trimIndent()

        val resp = executor.executeScript(snapshotScript)
        val resultObj = resp.getResultJson() as? SimpleJsonValue.JsonObject
            ?: return GeminiConversationSnapshot()

        val userIds = resultObj.getArray("userMessageIds")?.mapNotNull { (it as? SimpleJsonValue.JsonString)?.value } ?: emptyList()
        val modelIds = resultObj.getArray("modelMessageIds")?.mapNotNull { (it as? SimpleJsonValue.JsonString)?.value } ?: emptyList()

        return GeminiConversationSnapshot(
            userQueryCount = resultObj.getInt("userQueryCount") ?: 0,
            modelResponseCount = resultObj.getInt("modelResponseCount") ?: 0,
            lastUserQuery = resultObj.getString("lastUserQuery") ?: "",
            lastModelResponse = resultObj.getString("lastModelResponse") ?: "",
            userMessageIds = userIds,
            modelMessageIds = modelIds,
            isGenerating = resultObj.getBoolean("isGenerating") ?: false,
        )
    }

    /**
     * Sends a text prompt into the Gemini editor with strict pre-checks, text insertion verification,
     * single submission trigger, and user message appearance confirmation.
     */
    suspend fun sendPrompt(text: String): GeminiSendResult {
        // 1. Prompt text checks
        if (text.isBlank()) {
            return GeminiSendResult(success = false, error = "EMPTY_PROMPT")
        }

        dismissPopups()

        // 2. Pre-state verification
        val state = getPageState()
        if (!state.isScriptAvailable) {
            return GeminiSendResult(success = false, error = "SCRIPT_NOT_AVAILABLE")
        }
        if (!state.isGeminiUrl) {
            return GeminiSendResult(success = false, error = "NOT_GEMINI_PAGE")
        }
        if (!state.hasEditor) {
            return GeminiSendResult(success = false, error = "EDITOR_NOT_FOUND")
        }
        if (!state.isEditorAccessible) {
            return GeminiSendResult(success = false, error = "EDITOR_NOT_ACCESSIBLE")
        }
        if (state.isGenerating) {
            return GeminiSendResult(success = false, error = "GENERATION_IN_PROGRESS")
        }
        if (!state.isEditorEmpty) {
            return GeminiSendResult(success = false, error = "EDITOR_NOT_EMPTY")
        }

        // 3. Capture valid baseline snapshot prior to mutation
        val preSnapshot = getConversationSnapshot()

        // 4. Insert text into editor
        val escaped = WebView2Command.quote(text)
        val insertScript = """
            (function() {
                var editor = document.querySelector('.ql-editor');
                if (!editor) return { success: false, error: "EDITOR_NOT_FOUND" };
                editor.focus();
                document.execCommand('insertText', false, $escaped);
                editor.dispatchEvent(new Event('input', { bubbles: true }));
                var currentText = (editor.innerText || '').trim();
                return {
                    success: true,
                    editorText: currentText
                };
            })()
        """.trimIndent()

        val insertResp = executor.executeScript(insertScript)
        if (!insertResp.success) {
            return GeminiSendResult(success = false, error = insertResp.error ?: "TEXT_INSERTION_FAILED")
        }
        val insertObj = insertResp.getResultJson() as? SimpleJsonValue.JsonObject
        if (insertObj?.getBoolean("success") != true) {
            return GeminiSendResult(success = false, error = insertObj?.getString("error") ?: "TEXT_INSERTION_FAILED")
        }

        // Verify inserted text matches expected prompt (accounting for whitespace normalization)
        val actualInsertedText = insertObj.getString("editorText")?.trim() ?: ""
        val normalizedExpected = text.trim()
        if (actualInsertedText != normalizedExpected && !actualInsertedText.contains(normalizedExpected)) {
            return GeminiSendResult(success = false, error = "INSERTED_TEXT_MISMATCH")
        }

        // 5. Check send button state and trigger submission ONCE
        val triggerScript = """
            (function() {
                var sendBtn = document.querySelector('button[aria-label*="Отправить"]') ||
                              document.querySelector('button[aria-label*="Send"]');
                if (sendBtn) {
                    if (sendBtn.disabled || sendBtn.getAttribute('aria-disabled') === 'true') {
                        return { triggered: false, error: "SEND_BUTTON_DISABLED" };
                    }
                    sendBtn.click();
                    return { triggered: true, method: "button" };
                }

                // If button is absent, dispatch Enter on editor
                var editor = document.querySelector('.ql-editor');
                if (editor) {
                    editor.dispatchEvent(new KeyboardEvent('keydown', {
                        key: 'Enter',
                        code: 'Enter',
                        keyCode: 13,
                        which: 13,
                        bubbles: true,
                        cancelable: true
                    }));
                    return { triggered: true, method: "enter" };
                }

                return { triggered: false, error: "SEND_BUTTON_NOT_FOUND" };
            })()
        """.trimIndent()

        val triggerResp = executor.executeScript(triggerScript)
        if (!triggerResp.success) {
            return GeminiSendResult(success = false, error = triggerResp.error ?: "TRIGGER_FAILED")
        }
        val triggerObj = triggerResp.getResultJson() as? SimpleJsonValue.JsonObject
        if (triggerObj?.getBoolean("triggered") != true) {
            return GeminiSendResult(success = false, error = triggerObj?.getString("error") ?: "SEND_TRIGGER_FAILED")
        }

        // 6. Confirm send in DOM: must verify new user message appeared matching sent prompt text
        val startTime = System.currentTimeMillis()
        var confirmed = false
        while (System.currentTimeMillis() - startTime < 5000L) {
            delay(200L)
            val currentSnap = getConversationSnapshot()
            val userMessagesCount = currentSnap.userQueryCount
            val lastUserQuery = currentSnap.lastUserQuery.trim()

            // User message count grew OR lastUserQuery matches prompt text
            if ((userMessagesCount > preSnapshot.userQueryCount && lastUserQuery.contains(normalizedExpected)) ||
                (lastUserQuery == normalizedExpected && lastUserQuery != preSnapshot.lastUserQuery)
            ) {
                confirmed = true
                break
            }
        }

        if (!confirmed) {
            return GeminiSendResult(success = false, error = "SEND_NOT_CONFIRMED")
        }

        return GeminiSendResult(success = true, snapshot = preSnapshot)
    }

    /**
     * Retrieves all unique top-level model response items currently in the DOM.
     */
    suspend fun getAllModelResponses(): List<GeminiMessageItem> {
        val script = """
            (function() {
                var modelNodes = Array.from(document.querySelectorAll('model-response, [data-test-id="model-response"]'));
                if (modelNodes.length === 0) {
                    modelNodes = Array.from(document.querySelectorAll('.model-response-text, message-content, .response-content'));
                }
                // Filter out nested children to prevent double-counting
                modelNodes = modelNodes.filter(function(node) {
                    var parent = node.parentElement;
                    while (parent) {
                        if (modelNodes.indexOf(parent) !== -1) return false;
                        parent = parent.parentElement;
                    }
                    return true;
                });

                var filtered = [];
                modelNodes.forEach(function(el, idx) {
                    var id = el.getAttribute('id') || el.getAttribute('data-message-id') || ('model-' + idx);
                    var t = (el.innerText || '').trim();
                    if (t.length > 0 &&
                        !t.startsWith("Ваш запрос") &&
                        !t.startsWith("Your prompt") &&
                        !t.startsWith("С чего начнем") &&
                        !t.includes("Данные о местоположении")) {
                        filtered.push({ id: id, text: t });
                    }
                });
                return { responses: filtered };
            })()
        """.trimIndent()

        val resp = executor.executeScript(script)
        if (!resp.success) return emptyList()

        val resultObj = resp.getResultJson() as? SimpleJsonValue.JsonObject ?: return emptyList()
        val array = resultObj.getArray("responses") ?: return emptyList()
        return array.mapNotNull { item ->
            val obj = item as? SimpleJsonValue.JsonObject ?: return@mapNotNull null
            val id = obj.getString("id") ?: ""
            val text = obj.getString("text") ?: ""
            GeminiMessageItem(id = id, text = text)
        }
    }

    /**
     * Waits for the model response corresponding to the specified preSnapshot turn.
     * Prevents returning prior turns' responses by verifying appearance of the new response container.
     */
    suspend fun waitForResponse(
        preSnapshot: GeminiConversationSnapshot? = null,
        timeoutMs: Long = 45000L,
        pollIntervalMs: Long = 1000L,
    ): GeminiResponseResult {
        val startTime = System.currentTimeMillis()
        var lastObservedText = ""
        var stableCount = 0

        val targetIndex = preSnapshot?.modelResponseCount ?: 0
        val preModelIds = preSnapshot?.modelMessageIds?.toSet() ?: emptySet()

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            delay(pollIntervalMs)

            val responses = getAllModelResponses()
            val state = getPageState()

            // Find new response item by ID or by index
            val newResponse = when {
                // 1. By ID: an item whose ID was not present in preSnapshot
                responses.any { it.id.isNotBlank() && !preModelIds.contains(it.id) } ->
                    responses.first { it.id.isNotBlank() && !preModelIds.contains(it.id) }
                // 2. By position beyond the pre-send count
                responses.size > targetIndex ->
                    responses[targetIndex]
                // 3. Fallback: if list has elements and last item's text or ID differs from preSnapshot
                responses.isNotEmpty() && responses.last().id != preSnapshot?.modelMessageIds?.lastOrNull() ->
                    responses.last()
                else -> null
            }

            if (newResponse != null && newResponse.text.isNotBlank()) {
                val currentText = newResponse.text
                if (currentText == lastObservedText) {
                    stableCount++
                    // Check if generation completed and text was stable for at least 2 intervals
                    if (!state.isGenerating && stableCount >= 2) {
                        return GeminiResponseResult(found = true, text = currentText)
                    }
                } else {
                    lastObservedText = currentText
                    stableCount = 0
                }
            } else {
                stableCount = 0
            }
        }

        // On timeout: found MUST be false. We can return partialText if available.
        return GeminiResponseResult(
            found = false,
            text = "",
            partialText = if (lastObservedText.isNotBlank()) lastObservedText else null,
            error = "TIMEOUT_WAITING_FOR_RESPONSE",
        )
    }

    companion object {
        fun extractResponseText(json: String): String {
            return try {
                val parsed = SimpleJsonValue.parse(json) as? SimpleJsonValue.JsonObject
                parsed?.getString("text") ?: ""
            } catch (_: Exception) {
                ""
            }
        }
    }
}
