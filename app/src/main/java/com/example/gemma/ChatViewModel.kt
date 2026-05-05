import androidx.lifecycle.ViewModel
import androidx.compose.runtime.mutableStateListOf

class ChatViewModel : ViewModel() {
    val messages = mutableStateListOf<ChatMessage>()
}