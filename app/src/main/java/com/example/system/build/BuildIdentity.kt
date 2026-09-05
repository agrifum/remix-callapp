package com.example.system.build

data class BuildIdentity(
    val versionName: String,
    val versionCode: Int,
    val commitSha: String
) {
    fun displayLabel(): String {
        val shortCommit = if (commitSha == "unknown") commitSha else commitSha.take(7)
        return "Wersja $versionName · build $versionCode · commit $shortCommit"
    }
}
