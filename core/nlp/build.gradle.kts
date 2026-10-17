plugins {
    alias(libs.plugins.medishelf.jvm.library)
}

// Pure Kotlin: on-device recognition of medical terms in OCR text. Lexicons ship as
// classpath resources (src/main/resources/medishelf/lexicon), so it works the same on the
// JVM in tests and inside the APK, with no network access.
