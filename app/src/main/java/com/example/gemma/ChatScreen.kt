import androidx.compose.runtime.Composable

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ChatScreen(vm: ChatViewModel, llama: LlamaBridge) {

    var input by remember { mutableStateOf("") }

    Column {

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(vm.messages) { msg ->
                Text("${msg.role}: ${msg.text}")
            }
        }

        Row {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f)
            )

            Button(onClick = {
                vm.messages.add(ChatMessage("user", input))

                Thread {
                    val response = llama.generate(input)
                    vm.messages.add(ChatMessage("bot", response))
                }.start()

                input = ""
            }) {
                Text("Send")
            }
        }
    }
}