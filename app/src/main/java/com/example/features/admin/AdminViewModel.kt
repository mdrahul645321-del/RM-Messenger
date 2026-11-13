package com.example.features.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.firebase.AdminRepository
import com.example.core.firebase.FirestoreAnnouncementDocument
import com.example.core.firebase.FirestoreModerationReportDocument
import com.example.core.firebase.FirestorePlatformConfigDocument
import com.example.core.firebase.FirestoreUserDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AdminTab(val label: String) {
    OVERVIEW("Overview"),
    USERS("Users"),
    REPORTS("Reports"),
    BROADCAST("Broadcast"),
    PLATFORM("Platform"),
}

data class AdminPanelUiState(
    val isAuthorized: Boolean = false,
    val isSuperAdmin: Boolean = false,
    val currentUserId: String = "",
    val activeTab: AdminTab = AdminTab.OVERVIEW,
    val userSearchQuery: String = "",
    val users: List<FirestoreUserDocument> = emptyList(),
    val reports: List<FirestoreModerationReportDocument> = emptyList(),
    val announcements: List<FirestoreAnnouncementDocument> = emptyList(),
    val platformConfig: FirestorePlatformConfigDocument = FirestorePlatformConfigDocument(),
    val draftConfig: FirestorePlatformConfigDocument = FirestorePlatformConfigDocument(),
    val hasPendingConfigEdits: Boolean = false,
    val announcementTitleDraft: String = "",
    val announcementBodyDraft: String = "",
    val announcementPriorityDraft: String = "info",
    val isSubmitting: Boolean = false,
    val statusBanner: String? = null,
) {
    val openReportsCount: Int
        get() = reports.count { it.status == "open" }

    val suspendedUsersCount: Int
        get() = users.count { it.accountStatus == "suspended" }

    val activeAnnouncementsCount: Int
        get() = announcements.count { it.isActive }

    val filteredUsers: List<FirestoreUserDocument>
        get() {
            val q = userSearchQuery.trim().lowercase()
            if (q.isBlank()) return users
            return users.filter {
                it.displayName.lowercase().contains(q) ||
                    it.username.lowercase().contains(q) ||
                    (it.email?.lowercase()?.contains(q) == true) ||
                    (it.phoneNumber?.lowercase()?.contains(q) == true)
            }
        }
}

private data class LocalAdminTransientState(
    val activeTab: AdminTab = AdminTab.OVERVIEW,
    val userSearchQuery: String = "",
    val draftConfigOverride: FirestorePlatformConfigDocument? = null,
    val announcementTitleDraft: String = "",
    val announcementBodyDraft: String = "",
    val announcementPriorityDraft: String = "info",
    val isSubmitting: Boolean = false,
    val statusBanner: String? = null,
)

class AdminViewModel(
    private val currentUserId: String,
    private val currentUserName: String,
    private val currentUserRole: String,
    private val adminRepository: AdminRepository,
) : ViewModel() {

    private val isAuthorized = currentUserRole == "admin" || currentUserRole == "super_admin"
    private val isSuperAdmin = currentUserRole == "super_admin"
    private val transient = MutableStateFlow(LocalAdminTransientState())

    private val usersFlow = if (isAuthorized) {
        adminRepository.observeAllUsersForAdmin().catch { e ->
            if (e is CancellationException) throw e
            emit(emptyList())
        }
    } else {
        flowOf(emptyList())
    }

    private val reportsFlow = if (isAuthorized) {
        adminRepository.observeAllReportsForAdmin().catch { e ->
            if (e is CancellationException) throw e
            emit(emptyList())
        }
    } else {
        flowOf(emptyList())
    }

    private val announcementsFlow = if (isAuthorized) {
        adminRepository.observeAnnouncements(activeOnly = false).catch { e ->
            if (e is CancellationException) throw e
            emit(emptyList())
        }
    } else {
        flowOf(emptyList())
    }

    private val configFlow = if (isAuthorized) {
        adminRepository.observePlatformConfig().catch { e ->
            if (e is CancellationException) throw e
            emit(FirestorePlatformConfigDocument())
        }
    } else {
        flowOf(FirestorePlatformConfigDocument())
    }

    val uiState: StateFlow<AdminPanelUiState> = combine(
        usersFlow,
        reportsFlow,
        announcementsFlow,
        configFlow,
        transient,
    ) { users, reports, announcements, liveConfig, local ->
        val effectiveDraft = local.draftConfigOverride ?: liveConfig
        AdminPanelUiState(
            isAuthorized = isAuthorized,
            isSuperAdmin = isSuperAdmin,
            currentUserId = currentUserId,
            activeTab = local.activeTab,
            userSearchQuery = local.userSearchQuery,
            users = users,
            reports = reports,
            announcements = announcements,
            platformConfig = liveConfig,
            draftConfig = effectiveDraft,
            hasPendingConfigEdits = local.draftConfigOverride != null && local.draftConfigOverride != liveConfig,
            announcementTitleDraft = local.announcementTitleDraft,
            announcementBodyDraft = local.announcementBodyDraft,
            announcementPriorityDraft = local.announcementPriorityDraft,
            isSubmitting = local.isSubmitting,
            statusBanner = local.statusBanner,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AdminPanelUiState(
            isAuthorized = isAuthorized,
            isSuperAdmin = isSuperAdmin,
            currentUserId = currentUserId,
        ),
    )

    fun selectTab(tab: AdminTab) {
        transient.update { it.copy(activeTab = tab) }
    }

    fun updateUserSearchQuery(query: String) {
        transient.update { it.copy(userSearchQuery = query) }
    }

    fun toggleUserSuspension(targetUser: FirestoreUserDocument) {
        if (!isSuperAdmin || targetUser.uid == currentUserId) return
        val nextStatus = if (targetUser.accountStatus == "suspended") "active" else "suspended"
        viewModelScope.launch {
            transient.update { it.copy(isSubmitting = true) }
            val result = adminRepository.setUserAccountStatus(targetUser.uid, nextStatus)
            transient.update {
                it.copy(
                    isSubmitting = false,
                    statusBanner = result.fold(
                        onSuccess = {
                            if (nextStatus == "suspended") {
                                "${targetUser.displayName} suspended."
                            } else {
                                "${targetUser.displayName} reactivated."
                            }
                        },
                        onFailure = { err -> err.localizedMessage ?: "Failed to update account status." },
                    ),
                )
            }
        }
    }

    fun toggleUserAdminRole(targetUser: FirestoreUserDocument) {
        if (!isSuperAdmin || targetUser.uid == currentUserId || targetUser.role == "super_admin") return
        val nextRole = if (targetUser.role == "admin") "user" else "admin"
        viewModelScope.launch {
            transient.update { it.copy(isSubmitting = true) }
            val result = adminRepository.setUserRole(targetUser.uid, nextRole)
            transient.update {
                it.copy(
                    isSubmitting = false,
                    statusBanner = result.fold(
                        onSuccess = {
                            "${targetUser.displayName} role updated to ${nextRole.uppercase()}."
                        },
                        onFailure = { err -> err.localizedMessage ?: "Failed to update role." },
                    ),
                )
            }
        }
    }

    fun resolveReport(reportId: String, newStatus: String, resolutionNote: String) {
        if (!isAuthorized) return
        viewModelScope.launch {
            transient.update { it.copy(isSubmitting = true) }
            val result = adminRepository.resolveReport(reportId, newStatus, resolutionNote)
            transient.update {
                it.copy(
                    isSubmitting = false,
                    statusBanner = result.fold(
                        onSuccess = { "Report marked as $newStatus." },
                        onFailure = { err -> err.localizedMessage ?: "Failed to update report." },
                    ),
                )
            }
        }
    }

    fun updateAnnouncementDraft(
        title: String? = null,
        body: String? = null,
        priority: String? = null,
    ) {
        transient.update {
            it.copy(
                announcementTitleDraft = title ?: it.announcementTitleDraft,
                announcementBodyDraft = body ?: it.announcementBodyDraft,
                announcementPriorityDraft = priority ?: it.announcementPriorityDraft,
            )
        }
    }

    fun publishAnnouncement() {
        if (!isAuthorized) return
        val local = transient.value
        viewModelScope.launch {
            transient.update { it.copy(isSubmitting = true) }
            val result = adminRepository.publishAnnouncement(
                authorName = currentUserName,
                title = local.announcementTitleDraft,
                body = local.announcementBodyDraft,
                priority = local.announcementPriorityDraft,
            )
            transient.update { state ->
                result.fold(
                    onSuccess = {
                        state.copy(
                            isSubmitting = false,
                            announcementTitleDraft = "",
                            announcementBodyDraft = "",
                            statusBanner = "Global announcement published.",
                        )
                    },
                    onFailure = { err ->
                        state.copy(
                            isSubmitting = false,
                            statusBanner = err.localizedMessage ?: "Failed to publish announcement.",
                        )
                    },
                )
            }
        }
    }

    fun toggleAnnouncementActive(announcement: FirestoreAnnouncementDocument) {
        if (!isAuthorized) return
        viewModelScope.launch {
            val result = adminRepository.toggleAnnouncementActive(announcement.id, announcement.isActive)
            if (result.isFailure) {
                transient.update {
                    it.copy(statusBanner = result.exceptionOrNull()?.localizedMessage ?: "Update failed.")
                }
            }
        }
    }

    fun deleteAnnouncement(announcementId: String) {
        if (!isAuthorized) return
        viewModelScope.launch {
            val result = adminRepository.deleteAnnouncement(announcementId)
            transient.update {
                it.copy(
                    statusBanner = result.fold(
                        onSuccess = { "Announcement deleted." },
                        onFailure = { err -> err.localizedMessage ?: "Delete failed." },
                    ),
                )
            }
        }
    }

    fun updatePlatformDraft(transform: (FirestorePlatformConfigDocument) -> FirestorePlatformConfigDocument) {
        val current = transient.value.draftConfigOverride ?: uiState.value.platformConfig
        transient.update { it.copy(draftConfigOverride = transform(current)) }
    }

    fun savePlatformConfig() {
        if (!isSuperAdmin) return
        val toSave = transient.value.draftConfigOverride ?: uiState.value.platformConfig
        viewModelScope.launch {
            transient.update { it.copy(isSubmitting = true) }
            val result = adminRepository.updatePlatformConfig(toSave)
            transient.update { state ->
                result.fold(
                    onSuccess = {
                        state.copy(
                            isSubmitting = false,
                            draftConfigOverride = null,
                            statusBanner = "Platform controls updated live.",
                        )
                    },
                    onFailure = { err ->
                        state.copy(
                            isSubmitting = false,
                            statusBanner = err.localizedMessage ?: "Failed to update platform controls.",
                        )
                    },
                )
            }
        }
    }

    fun clearBanner() {
        transient.update { it.copy(statusBanner = null) }
    }

    class Factory(
        private val currentUserId: String,
        private val currentUserName: String,
        private val currentUserRole: String,
        private val adminRepository: AdminRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AdminViewModel(
                currentUserId = currentUserId,
                currentUserName = currentUserName,
                currentUserRole = currentUserRole,
                adminRepository = adminRepository,
            ) as T
        }
    }
}
