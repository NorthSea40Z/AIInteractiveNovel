cmake_minimum_required(VERSION 3.22.1)
project("llama-jni" VERSION 1.0.0 LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

# Pre-built .so path
set(PREBUILT_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

# Import pre-built libraries
add_library(llama SHARED IMPORTED)
set_target_properties(llama PROPERTIES IMPORTED_LOCATION ${PREBUILT_DIR}/libllama.so)

add_library(llama-common SHARED IMPORTED)
set_target_properties(llama-common PROPERTIES IMPORTED_LOCATION ${PREBUILT_DIR}/libllama-common.so)

# Our JNI wrapper
add_library(llama-jni SHARED llama_jni.cpp)
target_include_directories(llama-jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/ggml/include
    ${CMAKE_CURRENT_SOURCE_DIR}/ggml/src)
target_link_libraries(llama-jni llama llama-common android log)
