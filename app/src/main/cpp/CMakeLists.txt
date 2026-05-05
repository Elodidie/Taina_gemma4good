cmake_minimum_required(VERSION 3.22.1)

project("gemma")

add_library(llama-bridge SHARED llama-bridge.cpp)

add_subdirectory(llama.cpp)

target_link_libraries(llama-bridge llama)