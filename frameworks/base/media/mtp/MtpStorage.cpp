/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "MtpStorage"

#include "MtpDebug.h"
#include "MtpDatabase.h"
#include "MtpStorage.h"

#include <sys/types.h>
#include <sys/stat.h>
#include <sys/statfs.h>
#include <unistd.h>
#include <dirent.h>
#include <errno.h>
#include <string.h>
#include <stdio.h>
#include <limits.h>
//Added for USB Develpment debug, more log for more debuging help
#include "cutils/xlog.h"
//Added for USB Develpment debug, more log for more debuging help

namespace android {

MtpStorage::MtpStorage(MtpStorageID id, const char* filePath,
        const char* description, uint64_t reserveSpace,
        bool removable, uint64_t maxFileSize)
    :   mStorageID(id),
        mFilePath(filePath),
        mDescription(description),
        mMaxCapacity(0),
        mMaxFileSize(maxFileSize),
        mReserveSpace(reserveSpace),
        mRemovable(removable)
{
    LOGV("MtpStorage id: 0x%08x path: %s\n", id, filePath);
    //Added for USB Develpment debug, more log for more debuging help
    sxlog_printf(ANDROID_LOG_DEBUG, "MtpStorage",
                "MtpStorage id: 0x%08x path: %s\n", id, filePath);
    //Added for USB Develpment debug, more log for more debuging help
}

MtpStorage::~MtpStorage() {
}

int MtpStorage::getType() const {
    return (mRemovable ? MTP_STORAGE_REMOVABLE_RAM :  MTP_STORAGE_FIXED_RAM);
}

int MtpStorage::getFileSystemType() const {
    return MTP_STORAGE_FILESYSTEM_HIERARCHICAL;
}

int MtpStorage::getAccessCapability() const {
    return MTP_STORAGE_READ_WRITE;
}

uint64_t MtpStorage::getMaxCapacity() {
    if (mMaxCapacity == 0) {
        struct statfs   stat;
        if (statfs(getPath(), &stat))
            return -1;
        mMaxCapacity = (uint64_t)stat.f_blocks * (uint64_t)stat.f_bsize;
    }

    //ALPS00120037,Added for USB Develpment debug, more log for more debuging help
    sxlog_printf(ANDROID_LOG_DEBUG, "MtpStorage",
                "MtpStorage::getMaxCapacity: mMaxCapacity = %lld \n", mMaxCapacity);
    //ALPS00120037,Added for USB Develpment debug, more log for more debuging help
    
    return mMaxCapacity;
}

uint64_t MtpStorage::getFreeSpace() {
    struct statfs   stat;
    if (statfs(getPath(), &stat))
        return -1;
    uint64_t freeSpace = (uint64_t)stat.f_bavail * (uint64_t)stat.f_bsize;

    //ALPS00120037,Added for USB Develpment debug, more log for more debuging help
    sxlog_printf(ANDROID_LOG_DEBUG, "MtpStorage",
                "MtpStorage::getFreeSpace: freeSpace = %lld,  mReserveSpace = %lld \n", freeSpace, mReserveSpace);
    //ALPS00120037,Added for USB Develpment debug, more log for more debuging help

    return (freeSpace > mReserveSpace ? freeSpace - mReserveSpace : 0);
}

const char* MtpStorage::getDescription() const {
    return (const char *)mDescription;
}

//Added for Storage Update
bool MtpStorage::setDescription(const char* description) {
    mDescription.clear();
    mDescription.setTo(description);

    return true;
}
//Added for Storage Update

}  // namespace android
