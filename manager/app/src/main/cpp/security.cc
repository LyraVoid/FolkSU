#include <android/log.h>
#include <jni.h>
#include <string>

#define LOG_TAG "FolkSUApi"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// Baked in at build time from manager/auth.properties (see app/build.gradle.kts). The values are
// passed unquoted so STR/XSTR turn them into string literals; an absent define expands to "".
#ifndef API_TOKEN
#define API_TOKEN
#endif
#ifndef APP_SIGNATURE_HASH
#define APP_SIGNATURE_HASH
#endif
#ifndef APP_PACKAGE_NAME
#define APP_PACKAGE_NAME
#endif

#define STR(x) #x
#define XSTR(x) STR(x)

namespace {

std::string bytesToHex(JNIEnv *env, jbyteArray bytes) {
    if (bytes == nullptr) return "";
    jsize length = env->GetArrayLength(bytes);
    jbyte *data = env->GetByteArrayElements(bytes, nullptr);
    if (data == nullptr) return "";
    static const char HEX[] = "0123456789abcdef";
    std::string result;
    result.reserve(static_cast<size_t>(length) * 2);
    for (jsize i = 0; i < length; i++) {
        auto value = static_cast<unsigned char>(data[i]);
        result.push_back(HEX[value >> 4]);
        result.push_back(HEX[value & 0x0F]);
    }
    env->ReleaseByteArrayElements(bytes, data, JNI_ABORT);
    return result;
}

std::string getSignatureHash(JNIEnv *env, jobject context) {
    jclass contextClass = env->GetObjectClass(context);
    jmethodID getPackageManager = env->GetMethodID(
            contextClass, "getPackageManager", "()Landroid/content/pm/PackageManager;");
    jmethodID getPackageName = env->GetMethodID(
            contextClass, "getPackageName", "()Ljava/lang/String;");
    jobject packageManager = env->CallObjectMethod(context, getPackageManager);
    auto packageName = (jstring) env->CallObjectMethod(context, getPackageName);

    jclass packageManagerClass = env->GetObjectClass(packageManager);
    jmethodID getPackageInfo = env->GetMethodID(
            packageManagerClass, "getPackageInfo",
            "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;");
    // 64 == PackageManager.GET_SIGNATURES.
    jobject packageInfo = env->CallObjectMethod(packageManager, getPackageInfo, packageName, 64);

    jclass packageInfoClass = env->GetObjectClass(packageInfo);
    jfieldID signaturesField = env->GetFieldID(
            packageInfoClass, "signatures", "[Landroid/content/pm/Signature;");
    auto signatures = (jobjectArray) env->GetObjectField(packageInfo, signaturesField);
    jobject signature = env->GetObjectArrayElement(signatures, 0);

    jclass signatureClass = env->GetObjectClass(signature);
    jmethodID toByteArray = env->GetMethodID(signatureClass, "toByteArray", "()[B");
    auto signatureBytes = (jbyteArray) env->CallObjectMethod(signature, toByteArray);

    jclass messageDigestClass = env->FindClass("java/security/MessageDigest");
    jmethodID getInstance = env->GetStaticMethodID(
            messageDigestClass, "getInstance",
            "(Ljava/lang/String;)Ljava/security/MessageDigest;");
    jstring algorithm = env->NewStringUTF("SHA-256");
    jobject messageDigest = env->CallStaticObjectMethod(messageDigestClass, getInstance, algorithm);
    jmethodID digest = env->GetMethodID(messageDigestClass, "digest", "([B)[B");
    auto hash = (jbyteArray) env->CallObjectMethod(messageDigest, digest, signatureBytes);

    return bytesToHex(env, hash);
}

std::string getPackageNameOf(JNIEnv *env, jobject context) {
    jclass contextClass = env->GetObjectClass(context);
    jmethodID getPackageName = env->GetMethodID(
            contextClass, "getPackageName", "()Ljava/lang/String;");
    auto packageName = (jstring) env->CallObjectMethod(context, getPackageName);
    const char *chars = env->GetStringUTFChars(packageName, nullptr);
    std::string result = chars != nullptr ? chars : "";
    if (chars != nullptr) env->ReleaseStringUTFChars(packageName, chars);
    return result;
}

}  // namespace

// Returns the online-store token only when the app's package name and signing-certificate hash match
// the values baked in at build time. A mismatch (repackaged APK, foreign signature) yields "".
extern "C" JNIEXPORT jstring JNICALL
Java_me_weishu_kernelsu_Natives_nativeGetApiToken(JNIEnv *env, jobject /*thiz*/, jobject context) {
    if (context == nullptr) {
        return env->NewStringUTF("");
    }

    const std::string currentPackage = getPackageNameOf(env, context);
    const std::string expectedPackage = XSTR(APP_PACKAGE_NAME);
    if (currentPackage != expectedPackage) {
        LOGE("Package name mismatch: %s != %s", currentPackage.c_str(), expectedPackage.c_str());
        return env->NewStringUTF("");
    }

    const std::string currentHash = getSignatureHash(env, context);
    const std::string expectedHash = XSTR(APP_SIGNATURE_HASH);
    if (expectedHash.empty()) {
        LOGI("Development mode: skipping signature verification");
    } else if (strcasecmp(currentHash.c_str(), expectedHash.c_str()) != 0) {
        LOGE("Signature hash mismatch");
        return env->NewStringUTF("");
    }

    return env->NewStringUTF(XSTR(API_TOKEN));
}
