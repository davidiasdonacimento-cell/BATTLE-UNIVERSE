cmake_minimum_required(VERSION 3.22.1)
project(battleuniverse LANGUAGES CXX)

add_library(battleuniverse SHARED game_engine.cpp)
target_compile_features(battleuniverse PRIVATE cxx_std_17)
target_compile_options(battleuniverse PRIVATE -O2 -Wall -Wextra -Wno-unused-parameter)
target_link_libraries(battleuniverse GLESv3 log android)
