cmake_minimum_required(VERSION 3.22.1)
project(dlibrec CXX)

# dlib is downloaded into raw/ (gitignored) by the spike, not vendored.
set(DLIB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../raw/dlib/dlib-20.0)

add_library(dlibrec SHARED dlibrec.cpp ${DLIB_DIR}/dlib/all/source.cpp)
target_include_directories(dlibrec PRIVATE ${DLIB_DIR})
target_compile_definitions(dlibrec PRIVATE DLIB_NO_GUI_SUPPORT NDEBUG)
target_compile_options(dlibrec PRIVATE -O3 -ffunction-sections -fdata-sections -fvisibility=hidden -Wno-everything)
target_link_options(dlibrec PRIVATE -Wl,--gc-sections)
target_link_libraries(dlibrec log)
