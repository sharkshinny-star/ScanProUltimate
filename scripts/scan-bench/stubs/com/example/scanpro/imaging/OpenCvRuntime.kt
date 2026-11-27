package com.example.scanpro.imaging
object OpenCvRuntime { private val loaded by lazy { nu.pattern.OpenCV.loadLocally(); true }; fun ensureLoaded(): Boolean = loaded }
