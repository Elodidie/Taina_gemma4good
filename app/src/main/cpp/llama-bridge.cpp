#include <jni.h>
#include "llama.h"

static llama_model *model;
static llama_context *ctx;

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_gemma_LlamaBridge_initModel(
        JNIEnv *env, jobject, jstring path) {

    const char *model_path = env->GetStringUTFChars(path, 0);

    llama_model_params mparams = llama_model_default_params();
    model = llama_load_model_from_file(model_path, mparams);

    llama_context_params cparams = llama_context_default_params();
    ctx = llama_new_context_with_model(model, cparams);

    return model != nullptr;
}