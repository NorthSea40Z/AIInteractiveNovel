#include <jni.h>
#include "llama.h"
#include <string>
#include <vector>
#include <cmath>
#include <cstdlib>
#include <ctime>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct LlamaState {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
};

static llama_token sample_token(const float *logits, int n_vocab) {
    // Find max
    float max_val = logits[0];
    for (int i = 1; i < n_vocab; i++) {
        if (logits[i] > max_val) max_val = logits[i];
    }
    // Apply temperature and compute softmax up to top-k
    float temp = 0.8f;
    int top_k = 40;
    std::vector<std::pair<float, int>> candidates;
    candidates.reserve(top_k);
    for (int i = 0; i < n_vocab; i++) {
        if (logits[i] > max_val - 3.0f) { // only consider reasonably likely tokens
            candidates.push_back({expf((logits[i] - max_val) / temp), i});
        }
    }
    // Sort by probability descending
    std::sort(candidates.begin(), candidates.end(),
        [](auto &a, auto &b) { return a.first > b.first; });
    // Keep top-k
    if ((int)candidates.size() > top_k) candidates.resize(top_k);
    // Normalize and sample
    float sum = 0;
    for (auto &c : candidates) sum += c.first;
    float r = (float)rand() / (float)RAND_MAX * sum;
    float acc = 0;
    for (auto &c : candidates) {
        acc += c.first;
        if (r <= acc) return (llama_token)c.second;
    }
    return candidates.empty() ? 0 : (llama_token)candidates[0].second;
}

extern "C" {

JNIEXPORT jlong JNICALL Java_com_ian_aigame_engine_NativeLLM_init(JNIEnv *env, jobject, jstring model_path) {
    srand(time(0));
    llama_backend_init();
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Loading model: %s", path);
    auto mparams = llama_model_default_params();
    mparams.use_mmap = true;
    llama_model *model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(model_path, path);
    if (!model) { LOGE("Model load failed"); return 0; }
    auto cparams = llama_context_default_params();
    cparams.n_ctx = 4096;
    llama_context *ctx = llama_init_from_model(model, cparams);
    if (!ctx) { llama_model_free(model); return 0; }
    const llama_vocab *vocab = llama_model_get_vocab(model);
    auto *state = new LlamaState{model, ctx, vocab};
    LOGI("Model loaded OK");
    return (jlong)state;
}

JNIEXPORT jstring JNICALL Java_com_ian_aigame_engine_NativeLLM_generate(
    JNIEnv *env, jobject, jlong ptr, jstring prompt, jint max_tokens)
{
    auto *state = reinterpret_cast<LlamaState *>(ptr);
    if (!state || !state->model || !state->ctx) return env->NewStringUTF("");

    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    std::string input(prompt_str);
    env->ReleaseStringUTFChars(prompt, prompt_str);
    input = "<|im_start|>system\n你是一個互動小說創作者，請用繁體中文創作故事。請嚴格按照格式回覆。<|im_end|>\n<|im_start|>user\n" + input + "<|im_end|>\n<|im_start|>assistant\n";

    std::vector<llama_token> tokens(4096);
    int n_tokens = llama_tokenize(state->vocab, input.c_str(), input.length(), tokens.data(), (int)tokens.size(), true, false);
    if (n_tokens <= 0) return env->NewStringUTF("");
    tokens.resize(n_tokens);
    LOGI("Input tokens: %d", n_tokens);

    int n_vocab = llama_vocab_n_tokens(state->vocab);
    llama_token eos = llama_vocab_eos(state->vocab);

    // Decode prompt using properly initialized batch
    llama_batch prompt_batch = llama_batch_init(n_tokens, 0, 1);
    prompt_batch.n_tokens = n_tokens;
    for (int i = 0; i < n_tokens; i++) {
        prompt_batch.token[i] = tokens[i];
        prompt_batch.pos[i] = i;
        prompt_batch.n_seq_id[i] = 1;
        prompt_batch.seq_id[i][0] = 0;
        prompt_batch.logits[i] = (i == n_tokens - 1);
    }
    if (llama_decode(state->ctx, prompt_batch)) { llama_batch_free(prompt_batch); return env->NewStringUTF(""); }
    LOGI("Prompt decoded, %d tokens", n_tokens);

    // Generate tokens
    std::vector<unsigned char> result_bytes;
    result_bytes.reserve(4096);
    llama_batch gen_batch = llama_batch_init(1, 0, 1);
    int max_gen = (max_tokens < 512) ? max_tokens : 512;
    int n_pos = n_tokens;

    // First sample: get logits from last prompt token
    const float *first_logits = llama_get_logits_ith(state->ctx, n_tokens - 1);
    if (!first_logits) { llama_batch_free(prompt_batch); llama_batch_free(gen_batch); return env->NewStringUTF(""); }
    llama_token next_token = sample_token(first_logits, n_vocab);

    for (int i = 0; i < max_gen; i++) {
        LOGI("Token %d: id=%d", i, next_token);
        if (next_token == eos) { LOGI("Break: EOS"); break; }

        char buf[64];
        int len = llama_token_to_piece(state->vocab, next_token, buf, sizeof(buf), 0, false);
        if (len > 0) {
            if (len == 10 && memcmp(buf, "<|im_end|>", 10) == 0) { LOGI("Break: im_end"); break; }
            for (int j = 0; j < len; j++) result_bytes.push_back((unsigned char)buf[j]);
        }

        gen_batch.n_tokens = 1;
        gen_batch.token[0] = next_token;
        gen_batch.pos[0] = n_pos++;
        gen_batch.n_seq_id[0] = 1;
        gen_batch.seq_id[0][0] = 0;
        gen_batch.logits[0] = true;

        if (llama_decode(state->ctx, gen_batch)) { LOGI("Break: decode fail"); break; }

        // Get logits for next iteration
        const float *logits = llama_get_logits_ith(state->ctx, 0);
        if (!logits) { LOGI("Break: no logits after decode"); break; }
        next_token = sample_token(logits, n_vocab);
    }

    llama_batch_free(prompt_batch);
    llama_batch_free(gen_batch);
    LOGI("Generated %d bytes", (int)result_bytes.size());

    LOGI("Generated %d bytes", (int)result_bytes.size());

    // Strip template tags
    std::vector<unsigned char> cleaned;
    cleaned.reserve(result_bytes.size());
    for (size_t j = 0; j < result_bytes.size(); ) {
        if (j + 10 <= result_bytes.size() && memcmp(&result_bytes[j], "<|im_end|>", 10) == 0) { j += 10; }
        else if (j + 11 <= result_bytes.size() && memcmp(&result_bytes[j], "<|im_start|>", 11) == 0) {
            while (j < result_bytes.size() && result_bytes[j] != '\n') j++;
            j++;
        } else { cleaned.push_back(result_bytes[j]); j++; }
    }

    // Return as Java string via byte array + UTF-8 charset
    jbyteArray jbytes = env->NewByteArray((jsize)cleaned.size());
    env->SetByteArrayRegion(jbytes, 0, (jsize)cleaned.size(), (const jbyte*)cleaned.data());
    jclass strCls = env->FindClass("java/lang/String");
    jmethodID ctor = env->GetMethodID(strCls, "<init>", "([BLjava/lang/String;)V");
    jstring cs = env->NewStringUTF("UTF-8");
    jstring result = (jstring)env->NewObject(strCls, ctor, jbytes, cs);
    env->DeleteLocalRef(jbytes); env->DeleteLocalRef(cs); env->DeleteLocalRef(strCls);
    return result;
}

JNIEXPORT void JNICALL Java_com_ian_aigame_engine_NativeLLM_close(JNIEnv *, jobject, jlong ptr) {
    auto *state = reinterpret_cast<LlamaState *>(ptr);
    if (!state) return;
    if (state->ctx) llama_free(state->ctx);
    if (state->model) llama_model_free(state->model);
    delete state;
    llama_backend_free();
}

} // extern "C"
// rebuild
