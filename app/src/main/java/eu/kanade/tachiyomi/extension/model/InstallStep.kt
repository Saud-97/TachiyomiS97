package eu.kanade.tachiyomi.extension.model

enum class InstallStep {
    Pending,
    Downloading,
    Loading,
    Installing,
    Installed,
    Error,
    Done,
    ;

    fun isCompleted(): Boolean = this == Installed || this == Error || this == Done
}
