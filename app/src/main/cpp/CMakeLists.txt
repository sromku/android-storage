cmake_minimum_required(VERSION 3.22.1)
project(rawdev)

# LibRaw built without external deps: no libjpeg, lcms, jasper, DNG SDK.
set(LIBRAW_DIR ${CMAKE_CURRENT_SOURCE_DIR}/libraw)
file(GLOB_RECURSE LIBRAW_SRC ${LIBRAW_DIR}/src/*.cpp)
# The *_ph.cpp files are include-only helpers (#included by their siblings), not TUs.
list(FILTER LIBRAW_SRC EXCLUDE REGEX "_ph\\.cpp$")

add_library(rawdev SHARED
    rawdev.cpp
    ${LIBRAW_SRC}
)

target_include_directories(rawdev PRIVATE ${LIBRAW_DIR})

target_compile_definitions(rawdev PRIVATE
    NO_JPEG
    NO_LCMS
    NO_JASPER
    LIBRAW_NODLL
)

target_compile_options(rawdev PRIVATE
    -O2
    -Wno-unused-parameter
    -Wno-unused-variable
    -Wno-deprecated-declarations
    -fexceptions
)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)
find_library(z-lib z)
target_link_libraries(rawdev ${log-lib} ${jnigraphics-lib} ${z-lib})
