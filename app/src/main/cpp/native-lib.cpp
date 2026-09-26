#include <jni.h>
#include <cstdlib>
#include <cstring>
#include <vector>
#include <string>
#include "node.h"

extern "C"
JNIEXPORT jint JNICALL
Java_id_kua_batibati_botsurat_NodeBridge_startNodeWithArguments(
        JNIEnv *env,
        jclass,
        jobjectArray arguments) {

    const jsize argc = env->GetArrayLength(arguments);
    if (argc <= 0) return -1;

    std::vector<std::string> javaArgs;
    javaArgs.reserve(argc);
    size_t total = 0;

    for (jsize i = 0; i < argc; ++i) {
        auto jarg = (jstring) env->GetObjectArrayElement(arguments, i);
        const char *utf = env->GetStringUTFChars(jarg, nullptr);
        javaArgs.emplace_back(utf ? utf : "");
        total += javaArgs.back().size() + 1;
        if (utf) env->ReleaseStringUTFChars(jarg, utf);
        env->DeleteLocalRef(jarg);
    }

    std::vector<char> buffer(total, '\0');
    std::vector<char *> argv(argc);
    char *cursor = buffer.data();

    for (jsize i = 0; i < argc; ++i) {
        const auto &s = javaArgs[i];
        std::memcpy(cursor, s.c_str(), s.size());
        argv[i] = cursor;
        cursor += s.size() + 1;
    }

    return static_cast<jint>(node::Start(argc, argv.data()));
}
