#include <jni.h>
#include "llama.h"
#include <string>
#include <vector>
#include <cmath>
#include <cstdlib>
#include <ctime>
#include <algorithm>
#include <cstring>
#include <errno.h>
#include <android/log.h>

#define LOG_TAG "LlamaJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct LlamaState {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
    std::vector<llama_token> recent_tokens;
    int kv_pos = 0;
    llama_token im_end_id = -1;
    std::vector<int32_t> pos_buf;
    std::vector<int32_t> n_seq_id_buf;
    std::vector<llama_seq_id*> seq_id_ptr_buf;
    std::vector<llama_seq_id> seq_id_flat;
    std::vector<int8_t> logits_buf;
};

static float rep_penalty = 1.15f;
static int rep_penalty_range = 64;

static llama_token sample_token(const float *logits, int n_vocab, const std::vector<llama_token> &recent) {
    // Find max
    float max_val = logits[0];
    for (int i = 1; i < n_vocab; i++) {
        if (logits[i] > max_val) max_val = logits[i];
    }
    // Apply temperature and repetition penalty
    float temp = 0.8f;
    int top_k = 40;
    std::vector<std::pair<float, int>> candidates;
    candidates.reserve(top_k);
    int start = std::max(0, (int)recent.size() - rep_penalty_range);
    for (int i = 0; i < n_vocab; i++) {
        if (logits[i] > max_val - 4.0f) { // wider window for more diversity
            float score = expf((logits[i] - max_val) / temp);
            // Apply repetition penalty
            for (int j = start; j < (int)recent.size(); j++) {
                if (recent[j] == i) {
                    score /= rep_penalty;
                    break;
                }
            }
            candidates.push_back({score, i});
        }
    }
    std::sort(candidates.begin(), candidates.end(),
        [](auto &a, auto &b) { return a.first > b.first; });
    if ((int)candidates.size() > top_k) candidates.resize(top_k);
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
    LOGI("llama_backend_init start");
    llama_backend_init();
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Loading model: %s", path);

    // Check file access
    FILE *f = fopen(path, "rb");
    if (!f) {
        LOGE("Cannot open model file: %s", strerror(errno));
        env->ReleaseStringUTFChars(model_path, path);
        return 0;
    }
    fseek(f, 0, SEEK_END);
    long fsize = ftell(f);
    fclose(f);
    LOGI("Model file size: %ld bytes (%.1f MB)", fsize, fsize / 1e6);

    auto mparams = llama_model_default_params();
    mparams.use_mmap = true;
    LOGI("Calling llama_model_load_from_file...");
    llama_model *model = llama_model_load_from_file(path, mparams);
    env->ReleaseStringUTFChars(model_path, path);

    if (!model) {
        LOGE("llama_model_load_from_file returned null");
        LOGI("Trying with use_mmap=false...");
        // Retry without mmap
        const char *path2 = env->GetStringUTFChars(model_path, nullptr);
        auto mparams2 = llama_model_default_params();
        mparams2.use_mmap = false;
        model = llama_model_load_from_file(path2, mparams2);
        env->ReleaseStringUTFChars(model_path, path2);
        if (!model) {
            LOGE("Both mmap and no-mmap failed. Model cannot be loaded.");
            return 0;
        }
        LOGI("Model loaded with use_mmap=false");
    }

    LOGI("Creating context with n_ctx=2048");
    auto cparams = llama_context_default_params();
    cparams.n_ctx = 16384;
    llama_context *ctx = llama_init_from_model(model, cparams);
    if (!ctx) { LOGE("llama_init_from_model failed"); llama_model_free(model); return 0; }

    const llama_vocab *vocab = llama_model_get_vocab(model);
    auto *state = new LlamaState{model, ctx, vocab};
    // Look up <|im_end|> token ID for early termination
    const char *end_marker = "<|im_end|>";
    std::vector<llama_token> end_tokens(4);
    int n_end = llama_tokenize(vocab, end_marker, strlen(end_marker), end_tokens.data(), (int)end_tokens.size(), false, false);
    if (n_end > 0) state->im_end_id = end_tokens[0];
    LOGI("Model loaded, im_end_id=%d, state=%p", state->im_end_id, state);
    return (jlong)state;
}

JNIEXPORT jstring JNICALL Java_com_ian_aigame_engine_NativeLLM_generate(
    JNIEnv *env, jobject, jlong ptr, jstring prompt, jint max_tokens)
{
    auto *state = reinterpret_cast<LlamaState *>(ptr);
    if (!state || !state->model || !state->ctx) return env->NewStringUTF("");

    const char *prompt_str = env->GetStringUTFChars(prompt, nullptr);
    std::string input = "<|im_start|>system\n你是一個互動小說作家。只輸出繁體中文故事。不要輸出格式標記。<|im_end|>\n<|im_start|>user\n";
    input += prompt_str;
    input += "<|im_end|>\n<|im_start|>assistant\n";
    env->ReleaseStringUTFChars(prompt, prompt_str);
    LOGI("Generate, max=%d", max_tokens);
    std::vector<llama_token> tokens(4096);
    int n_tokens = llama_tokenize(state->vocab, input.c_str(), input.length(), tokens.data(), (int)tokens.size(), true, false);
    if (n_tokens <= 0) return env->NewStringUTF("");
    tokens.resize(n_tokens);
    LOGI("Input tokens: %d", n_tokens);

    int n_vocab = llama_vocab_n_tokens(state->vocab);
    llama_token eos = llama_vocab_eos(state->vocab);

    // Build prompt batch (llama_batch_get_one returns stack struct, no heap)
    int prompt_start = state->kv_pos;
    // Allocate state buffers for batch metadata
    state->pos_buf.resize(n_tokens);
    state->n_seq_id_buf.resize(n_tokens);
    state->logits_buf.resize(n_tokens);
    state->seq_id_flat.resize(n_tokens);
    state->seq_id_ptr_buf.resize(n_tokens);
    for (int i = 0; i < n_tokens; i++) {
        state->pos_buf[i]    = prompt_start + i;
        state->n_seq_id_buf[i] = 1;
        state->logits_buf[i] = (i == n_tokens - 1);
        state->seq_id_flat[i] = 0;
        state->seq_id_ptr_buf[i] = &state->seq_id_flat[i];
    }
    struct llama_batch prompt_batch = llama_batch_get_one(tokens.data(), n_tokens);
    prompt_batch.pos     = state->pos_buf.data();
    prompt_batch.n_seq_id = state->n_seq_id_buf.data();
    prompt_batch.seq_id  = state->seq_id_ptr_buf.data();
    prompt_batch.logits  = state->logits_buf.data();
    if (llama_decode(state->ctx, prompt_batch)) return env->NewStringUTF("");
    LOGI("Prompt decoded, %d tokens at pos %d", n_tokens, prompt_start);

    const float *first_logits = llama_get_logits_ith(state->ctx, n_tokens - 1);
    if (!first_logits) return env->NewStringUTF("");
    state->recent_tokens.clear();
    llama_token next_token = sample_token(first_logits, n_vocab, state->recent_tokens);
    int n_pos = prompt_start + n_tokens;
    int max_gen = (max_tokens < 512) ? max_tokens : 512;
    std::vector<unsigned char> result_bytes;
    result_bytes.reserve(4096);

    for (int i = 0; i < max_gen; i++) {
        LOGI("Token %d: id=%d", i, next_token);
        if (next_token == eos) { LOGI("Break: EOS"); break; }
        if (next_token == state->im_end_id) { LOGI("Break: im_end"); break; }

        char buf[64];
        int len = llama_token_to_piece(state->vocab, next_token, buf, sizeof(buf) - 1, 0, false);
        if (len > 0) {
            for (int j = 0; j < len; j++) result_bytes.push_back((unsigned char)buf[j]);
        }

        int32_t gen_pos = n_pos++;
        int32_t gen_n_seq = 1;
        int8_t gen_logit = 1;
        llama_seq_id gen_seq_val[1] = {0};
        llama_seq_id *gen_seq_ptr[1] = {gen_seq_val};
        struct llama_batch gen_batch = llama_batch_get_one(&next_token, 1);
        gen_batch.pos = &gen_pos;
        gen_batch.n_seq_id = &gen_n_seq;
        gen_batch.seq_id = gen_seq_ptr;
        gen_batch.logits = &gen_logit;
        if (llama_decode(state->ctx, gen_batch)) { LOGI("Break: decode fail"); break; }

        state->recent_tokens.push_back(next_token);
        if ((int)state->recent_tokens.size() > rep_penalty_range) {
            state->recent_tokens.erase(state->recent_tokens.begin());
        }

        // Get logits for next iteration
        const float *logits = llama_get_logits_ith(state->ctx, 0);
        if (!logits) { LOGI("Break: no logits after decode"); break; }
        next_token = sample_token(logits, n_vocab, state->recent_tokens);
    }

    state->kv_pos = n_pos;
    LOGI("Generated %d bytes, kv_pos=%d", (int)result_bytes.size(), state->kv_pos);

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

JNIEXPORT void JNICALL Java_com_ian_aigame_engine_NativeLLM_resetContext(JNIEnv *, jobject, jlong ptr) {
    auto *state = reinterpret_cast<LlamaState *>(ptr);
    if (state) state->kv_pos = 0;
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
