class LlamaBridge {

    init {
        System.loadLibrary("llama-bridge")
    }

    external fun initModel(path: String): Boolean
    external fun generate(prompt: String): String
}