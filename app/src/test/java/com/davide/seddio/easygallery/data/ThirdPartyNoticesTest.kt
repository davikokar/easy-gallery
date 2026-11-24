package com.davide.seddio.easygallery.data

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

// Plain JVM. Gradle runs unit tests with the module directory as the working directory, so the files are read from disk.
class ThirdPartyNoticesTest {

    private val faceAssets = File("src/main/assets/face")

    // Trimmed, and split on either line ending, so the test does not depend on CR or LF.
    private fun noticeLines(): List<String> {
        val file = File(faceAssets, "THIRD_PARTY_NOTICES.txt")
        assertTrue("missing ${file.absolutePath}", file.isFile)
        val text = file.readText(Charsets.UTF_8)
        assertTrue("${file.name} is blank", text.isNotBlank())
        return text.split(Regex("\r?\n")).map { it.trim() }
    }

    private fun assertHasLine(lines: List<String>, expected: String) {
        assertTrue("THIRD_PARTY_NOTICES.txt has no line \"$expected\"", expected in lines)
    }

    @Test
    fun `every bundled component is listed with its version`() {
        val lines = noticeLines()

        listOf(
            "- libcpufeatures (AOSP, 2016)",
            "- libprotobuf 3.19.1",
            "- ADE 0.1.2e",
            "- oneTBB 2022.1.0",
            "- ittnotify 3.25.4",
            "- libjpeg-turbo 3.1.2-70",
            "- libwebp (decoder 0x0210)",
            "- libpng 1.6.53",
            "- libtiff 4.7.1",
            "- OpenJPEG 2.5.3",
            "- OpenEXR (IlmImf) 2.3.0",
            "- Carotene (tegra_hal) 0.0.1",
            "- KleidiCV 0.7.0",
            "- Flatbuffers 25.9.23",
            "- LLVM libc++ (libc++_shared.so, NDK 27.2.12479018)",
            "- zlib 1.3.0.1 - not shipped, the device's own library"
        ).forEach { assertHasLine(lines, it) }
    }

    @Test
    fun `every component has its own section in its licence group`() {
        val lines = noticeLines()

        listOf(
            "Apache License 2.0",
            "BSD 3-Clause License",
            "libjpeg-turbo (IJG and BSD 3-Clause)",
            "BSD 2-Clause License",
            "libtiff",
            "libpng-2.0 (PNG Reference Library License)",
            "LLVM",
            "zlib",
            "--- libcpufeatures (AOSP, 2016) ---",
            "--- ADE 0.1.2e ---",
            "--- oneTBB 2022.1.0 ---",
            "--- KleidiCV 0.7.0 ---",
            "--- Flatbuffers 25.9.23 ---",
            "--- libprotobuf 3.19.1 ---",
            "--- libwebp (decoder 0x0210) ---",
            "--- OpenEXR (IlmImf) 2.3.0 ---",
            "--- ittnotify 3.25.4 ---",
            "--- Carotene (tegra_hal) 0.0.1 ---",
            "--- libjpeg-turbo 3.1.2-70 ---",
            "--- OpenJPEG 2.5.3 ---",
            "--- libtiff 4.7.1 ---",
            "--- libpng 1.6.53 ---",
            "--- LLVM libc++ (libc++_shared.so, NDK 27.2.12479018) ---",
            "--- zlib 1.3.0.1 ---"
        ).forEach { assertHasLine(lines, it) }
    }

    @Test
    fun `the licence texts and copyright lines are present verbatim`() {
        val lines = noticeLines()

        listOf(
            "Apache License",
            "Version 2.0, January 2004",
            "Copyright (C) 2016 The Android Open Source Project",
            "Copyright (C) 2018 Intel Corporation",
            "Copyright 2008 Google Inc.  All rights reserved.",
            "Copyright (c) 2010, Google Inc. All rights reserved.",
            "Copyright (c) 2006, Industrial Light & Magic, a division of Lucasfilm",
            "Copyright (c) 2019 Intel Corporation. All rights reserved.",
            "ittnotify is licensed GPL-2.0-only OR BSD-3-Clause; Easy Gallery uses it under BSD-3-Clause.",
            "This software is based in part on the work of the Independent JPEG Group.",
            "libjpeg-turbo Licenses",
            "LEGAL ISSUES",
            "* The copyright in this software is being made available under the 2-clauses",
            "# LibTIFF license",
            "COPYRIGHT NOTICE, DISCLAIMER, and LICENSE",
            "The LLVM Project is under the Apache License v2.0 with LLVM Exceptions:"
        ).forEach { assertHasLine(lines, it) }
    }

    @Test
    fun `the Carotene header carries its licence name and holder`() {
        val lines = noticeLines()

        assertTrue("no line naming the 3-clause BSD License", lines.any { "(3-clause BSD License)" in it })
        assertTrue("no line naming NVIDIA Corporation", lines.any { "NVIDIA Corporation" in it })
    }

    @Test
    fun `NOTICE names the third-party notices file`() {
        val notice = File(faceAssets, "NOTICE.txt")
        assertTrue("missing ${notice.absolutePath}", notice.isFile)

        assertTrue("NOTICE.txt does not point to THIRD_PARTY_NOTICES.txt", "THIRD_PARTY_NOTICES.txt" in notice.readText(Charsets.UTF_8))
    }
}
