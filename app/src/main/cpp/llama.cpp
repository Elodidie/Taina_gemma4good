extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_gemma_LlamaBridge_generate(JNIEnv *env, jobject, jstring prompt) {

    const char *input = env->GetStringUTFChars(prompt, 0);

    std::string result = run_inference(ctx, input); // llama.cpp logic

    return env->NewStringUTF(result.c_str());
}