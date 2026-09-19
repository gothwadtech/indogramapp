package com.gothwad.grixchat.data

import kotlinx.coroutines.flow.Flow

class GrixRepository(private val grixDao: GrixDao) {

    val allOfflineDrafts: Flow<List<OfflineDraft>> = grixDao.getAllOfflineDrafts()
    val allNotifications: Flow<List<NotificationItem>> = grixDao.getAllNotifications()

    suspend fun saveOfflineDraft(content: String, recipient: String = "General") {
        grixDao.insertOfflineDraft(OfflineDraft(content = content, recipient = recipient))
    }

    suspend fun deleteOfflineDraft(draft: OfflineDraft) {
        grixDao.deleteOfflineDraft(draft)
    }

    suspend fun clearAllDrafts() {
        grixDao.clearAllDrafts()
    }

    suspend fun saveNotification(title: String, message: String) {
        grixDao.insertNotification(NotificationItem(title = title, message = message))
    }

    suspend fun markNotificationAsRead(id: Int) {
        grixDao.markNotificationAsRead(id)
    }

    suspend fun deleteNotification(id: Int) {
        grixDao.deleteNotification(id)
    }

    suspend fun clearAllNotifications() {
        grixDao.clearAllNotifications()
    }
}
