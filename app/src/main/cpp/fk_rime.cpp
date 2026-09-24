#include <jni.h>
#include <rime_api.h>

#include <mutex>
#include <string>
#include <vector>

namespace {
std::mutex engine_mutex;
RimeApi* api = nullptr;
bool initialized = false;

std::string utf8(JNIEnv* env, jstring value) {
  if (!value) return {};
  const char* chars = env->GetStringUTFChars(value, nullptr);
  if (!chars) return {};
  std::string result(chars);
  env->ReleaseStringUTFChars(value, chars);
  return result;
}
}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_uk_cassiangroup_fkinput_core_NativeRime_nativeInit(
    JNIEnv* env, jobject, jstring shared_dir, jstring user_dir) {
  std::lock_guard<std::mutex> lock(engine_mutex);
  if (initialized) return JNI_TRUE;
  static std::string shared;
  static std::string user;
  shared = utf8(env, shared_dir);
  user = utf8(env, user_dir);
  if (shared.empty() || user.empty()) return JNI_FALSE;

  api = rime_get_api();
  if (!api) return JNI_FALSE;
  RimeTraits traits = {};
  RIME_STRUCT_INIT(RimeTraits, traits);
  traits.shared_data_dir = shared.c_str();
  traits.user_data_dir = user.c_str();
  traits.distribution_name = "FK Input";
  traits.distribution_code_name = "fkinput";
  traits.distribution_version = "0.1.0";
  traits.app_name = "rime.fkinput";
  traits.min_log_level = 2;
  traits.log_dir = "";
  api->setup(&traits);
  api->initialize(&traits);
  if (api->start_maintenance(true)) api->join_maintenance_thread();
  initialized = true;
  return JNI_TRUE;
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_uk_cassiangroup_fkinput_core_NativeRime_nativeCandidates(
    JNIEnv* env, jobject, jstring input, jboolean nine_key) {
  std::lock_guard<std::mutex> lock(engine_mutex);
  jclass string_class = env->FindClass("java/lang/String");
  if (!initialized || !api || !string_class) {
    return env->NewObjectArray(0, string_class, nullptr);
  }
  const std::string keys = utf8(env, input);
  const RimeSessionId session = api->create_session();
  if (!session) return env->NewObjectArray(0, string_class, nullptr);
  api->select_schema(session, nine_key ? "fk_nine" : "fk_pinyin");
  for (unsigned char key : keys) api->process_key(session, key, 0);

  std::vector<std::string> words;
  RimeCandidateListIterator iterator = {};
  if (api->candidate_list_begin(session, &iterator)) {
    while (words.size() < 80 && api->candidate_list_next(&iterator)) {
      if (iterator.candidate.text) words.emplace_back(iterator.candidate.text);
    }
    api->candidate_list_end(&iterator);
  }
  api->destroy_session(session);

  jobjectArray result = env->NewObjectArray(words.size(), string_class, nullptr);
  for (size_t index = 0; index < words.size(); ++index) {
    jstring value = env->NewStringUTF(words[index].c_str());
    env->SetObjectArrayElement(result, index, value);
    env->DeleteLocalRef(value);
  }
  return result;
}

extern "C" JNIEXPORT void JNICALL
Java_uk_cassiangroup_fkinput_core_NativeRime_nativeClose(JNIEnv*, jobject) {
  std::lock_guard<std::mutex> lock(engine_mutex);
  if (initialized && api) api->finalize();
  initialized = false;
  api = nullptr;
}
