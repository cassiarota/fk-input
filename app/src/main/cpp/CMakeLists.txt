cmake_minimum_required(VERSION 3.22.1)
project(fk_rime LANGUAGES CXX)
set(CMAKE_CXX_STANDARD 17)

set(PREBUILT "${CMAKE_CURRENT_SOURCE_DIR}/prebuilt")
foreach(name IN ITEMS rime glog leveldb lua marisa opencc yaml)
    add_library("${name}_archive" STATIC IMPORTED)
endforeach()
set_target_properties(rime_archive PROPERTIES IMPORTED_LOCATION "${PREBUILT}/librime/lib/librime.a")
set_target_properties(glog_archive PROPERTIES IMPORTED_LOCATION "${PREBUILT}/glog/lib/libglog.a")
set_target_properties(leveldb_archive PROPERTIES IMPORTED_LOCATION "${PREBUILT}/leveldb/lib/libleveldb.a")
set_target_properties(lua_archive PROPERTIES IMPORTED_LOCATION "${PREBUILT}/lua/lib/liblua_static.a")
set_target_properties(marisa_archive PROPERTIES IMPORTED_LOCATION "${PREBUILT}/marisa/lib/libmarisa.a")
set_target_properties(opencc_archive PROPERTIES IMPORTED_LOCATION "${PREBUILT}/opencc/lib/libopencc.a")
set_target_properties(yaml_archive PROPERTIES IMPORTED_LOCATION "${PREBUILT}/yaml-cpp/lib/libyaml-cpp.a")

add_library(fk_rime SHARED fk_rime.cpp)
target_include_directories(fk_rime PRIVATE "${PREBUILT}/librime/include")
target_link_libraries(fk_rime PRIVATE
    "-Wl,--whole-archive" rime_archive "-Wl,--no-whole-archive"
    glog_archive leveldb_archive lua_archive marisa_archive opencc_archive yaml_archive
    log android)
