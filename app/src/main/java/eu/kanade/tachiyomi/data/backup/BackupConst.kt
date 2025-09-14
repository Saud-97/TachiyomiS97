package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.BuildConfig.APPLICATION_ID as ID

object BackupConst {
    private const val NAME = "BackupRestorer"
    const val EXTRA_URI = "$ID.$NAME.EXTRA_URI"

    // Filter options
    const val BACKUP_CATEGORY = 0x1
    const val BACKUP_CATEGORY_MASK = 0x1
    const val BACKUP_CHAPTER = 0x2
    const val BACKUP_CHAPTER_MASK = 0x2
    const val BACKUP_HISTORY = 0x4
    const val BACKUP_HISTORY_MASK = 0x4
    const val BACKUP_TRACK = 0x8
    const val BACKUP_TRACK_MASK = 0x8
    const val BACKUP_APP_PREFS = 0x10
    const val BACKUP_APP_PREFS_MASK = 0x10
    const val BACKUP_SOURCE_PREFS = 0x20
    const val BACKUP_SOURCE_PREFS_MASK = 0x20
    const val BACKUP_CUSTOM_INFO = 0x40
    const val BACKUP_CUSTOM_INFO_MASK = 0x40
    const val BACKUP_READ_MANGA = 0x80
    const val BACKUP_READ_MANGA_MASK = 0x80

    const val BACKUP_ALL = 0x7F
}
