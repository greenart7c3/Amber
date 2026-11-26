package com.greenart7c3.nostrsigner.database

/** One account's application and permission DAOs, all on the same [AppDatabase]. */
class ApplicationDaos(
    val applicationDao: ApplicationDao,
    val applicationListDao: ApplicationListDao,
    val applicationWriteDao: ApplicationWriteDao,
    val permissionLookupDao: PermissionLookupDao,
    val permissionWriteDao: PermissionWriteDao,
    val permissionMaintenanceDao: PermissionMaintenanceDao,
) {
    companion object {
        fun of(database: AppDatabase) = ApplicationDaos(
            applicationDao = database.dao(),
            applicationListDao = database.applicationListDao(),
            applicationWriteDao = database.applicationWriteDao(),
            permissionLookupDao = database.permissionLookupDao(),
            permissionWriteDao = database.permissionWriteDao(),
            permissionMaintenanceDao = database.permissionMaintenanceDao(),
        )

        fun cached(raw: ApplicationDaos): ApplicationDaos {
            val cache = ApplicationDaoCache()
            return ApplicationDaos(
                applicationDao = CachingApplicationDao(raw.applicationDao, cache),
                applicationListDao = CachingApplicationListDao(raw.applicationListDao),
                applicationWriteDao = CachingApplicationWriteDao(raw.applicationWriteDao, cache),
                permissionLookupDao = CachingPermissionLookupDao(raw.permissionLookupDao, cache),
                permissionWriteDao = CachingPermissionWriteDao(raw.permissionWriteDao, cache),
                permissionMaintenanceDao = CachingPermissionMaintenanceDao(raw.permissionMaintenanceDao, cache),
            )
        }
    }
}
