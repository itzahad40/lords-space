cmake_minimum_required(VERSION 3.10.2)
project(fightmode CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
add_library(fightmode SHARED fightmode_jni.cpp)
find_library(log-lib log)
target_link_libraries(fightmode ${log-lib})

# Android 15/16 devices can use 16 KB memory pages: align our library for them (harmless on 4 KB devices).
set_target_properties(fightmode PROPERTIES LINK_FLAGS "-Wl,-z,max-page-size=16384")
