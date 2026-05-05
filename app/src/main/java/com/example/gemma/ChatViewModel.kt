package com.example.gemma

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val isLoading: Boolean = false
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _modelLoaded = MutableStateFlow(false)
    val modelLoaded: StateFlow<Boolean> = _modelLoaded.asStateFlow()

    private var model: GemmaInferenceModel? = null

    init {
        loadModel()
    }

    private fun loadModel() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                model = GemmaInferenceModel.getInstance(getApplication())
                _modelLoaded.value = true
            } catch (e: Exception) {
                _messages.value = listOf(
                    ChatMessage(
                        text = "❌ Failed to load model: ${e.message}\n\nMake sure you pushed the model file:\nadb push gemma-2b-it-cpu-int4.bin /data/local/tmp/llm/model.bin",
                        isUser = false
                    )
                )
            }
        }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank() || _isLoading.value) return

        val userMessage = ChatMessage(text = userText, isUser = true)
        val loadingMessage = ChatMessage(text = "", isUser = false, isLoading = true)
        _messages.value = _messages.value + userMessage + loadingMessage
        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            var accumulated = ""
            model?.generateResponseAsync(userText)
                ?.catch { e ->
                    // Replace loading bubble with error
                    updateLastBotMessage("Error: ${e.message}")
                    _isLoading.value = false
                }
                ?.onCompletion {
                    _isLoading.value = false
                }
                ?.collect { token ->
                    accumulated += token
                    updateLastBotMessage(accumulated)
                }
        }
    }

    private fun updateLastBotMessage(text: String) {
        val current = _messages.value.toMutableList()
        val lastIndex = current.indexOfLast { !it.isUser }
        if (lastIndex != -1) {
            current[lastIndex] = current[lastIndex].copy(text = text, isLoading = false)
            _messages.value = current
        }
    }
}