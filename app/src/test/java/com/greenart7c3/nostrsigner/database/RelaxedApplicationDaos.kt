package com.greenart7c3.nostrsigner.database

import io.mockk.mockk

fun relaxedApplicationDaos(
    applicationDao: ApplicationDao = mockk(relaxed = true),
    applicationListDao: ApplicationListDao = mockk(relaxed = true),
    applicationWriteDao: ApplicationWriteDao = mockk(relaxed = true),
    permissionLookupDao: PermissionLookupDao = mockk(relaxed = true),
    permissionWriteDao: PermissionWriteDao = mockk(relaxed = true),
    permissionMaintenanceDao: PermissionMaintenanceDao = mockk(relaxed = true),
) = ApplicationDaos(applicationDao, applicationListDao, applicationWriteDao, permissionLookupDao, permissionWriteDao, permissionMaintenanceDao)
