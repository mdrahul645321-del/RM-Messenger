package com.example.features.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.model.ContactSummary
import com.example.ui.components.AvatarCircle

@Composable
fun GroupInfoSheetContent(
    state: ChatUiState,
    currentUserId: String,
    onSaveGroupDetails: (String, String) -> Unit,
    onToggleMute: () -> Unit,
    onTogglePin: () -> Unit,
    onAddMember: (ContactSummary) -> Unit,
    onRemoveMember: (String) -> Unit,
    onToggleAdmin: (String, Boolean) -> Unit,
    onLeaveGroup: () -> Unit,
    onClose: () -> Unit,
) {
    var editingTitle by remember(state.conversationTitle) { mutableStateOf(state.conversationTitle) }
    var editingDesc by remember(state.groupDescription) { mutableStateOf(state.groupDescription) }
    var showAddMemberPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Groups,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(14.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.conversationTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${state.memberUids.size} members • ${if (state.isAdmin) "You are Admin" else "Member"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onToggleMute,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = if (state.isMuted) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (state.isMuted) "Unmute" else "Mute")
            }
            OutlinedButton(
                onClick = onTogglePin,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (state.isPinned) "Unpin" else "Pin")
            }
        }

        if (state.isAdmin) {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Admin Controls", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = editingTitle,
                        onValueChange = { editingTitle = it },
                        label = { Text("Group Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("group_edit_title_input"),
                    )
                    OutlinedTextField(
                        value = editingDesc,
                        onValueChange = { editingDesc = it },
                        label = { Text("Group Description") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { onSaveGroupDetails(editingTitle, editingDesc) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("group_save_details_button"),
                    ) {
                        Text("Save Group Info")
                    }
                }
            }
        } else if (state.groupDescription.isNotBlank()) {
            Text(
                text = state.groupDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Members (${state.memberUids.size})",
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.isAdmin) {
                TextButton(
                    onClick = { showAddMemberPicker = !showAddMemberPicker },
                    modifier = Modifier.testTag("group_add_member_toggle"),
                ) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (showAddMemberPicker) "Done" else "Add Member")
                }
            }
        }

        if (showAddMemberPicker && state.isAdmin) {
            if (state.contactsToAdd.isEmpty()) {
                Text(
                    text = "All your saved contacts are already in this group.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.contactsToAdd.forEach { contact ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    ) {
                        ListItem(
                            headlineContent = { Text(contact.displayName) },
                            supportingContent = { Text("@${contact.username}") },
                            trailingContent = {
                                Button(onClick = { onAddMember(contact) }) {
                                    Text("Add")
                                }
                            },
                        )
                    }
                }
            }
        }

        state.memberUids.forEach { memberUid ->
            val name = state.participantNames[memberUid] ?: "RM User"
            val handle = state.participantUsernames[memberUid] ?: ""
            val memberIsAdmin = state.adminUids.contains(memberUid)
            val isSelf = memberUid == currentUserId

            ListItem(
                leadingContent = {
                    AvatarCircle(initials = name.take(2), size = 38.dp)
                },
                headlineContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(if (isSelf) "$name (You)" else name)
                        if (memberIsAdmin) {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = "Admin",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                },
                supportingContent = if (handle.isNotBlank()) {
                    { Text("@$handle") }
                } else {
                    null
                },
                trailingContent = if (state.isAdmin && !isSelf) {
                    {
                        Row {
                            IconButton(
                                onClick = { onToggleAdmin(memberUid, !memberIsAdmin) },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AdminPanelSettings,
                                    contentDescription = if (memberIsAdmin) "Remove admin" else "Make admin",
                                    tint = if (memberIsAdmin) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = { onRemoveMember(memberUid) },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PersonRemove,
                                    contentDescription = "Remove member",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                } else {
                    null
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.memberUids.size > 1) {
                OutlinedButton(
                    onClick = onLeaveGroup,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("leave_group_button"),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Leave Group")
                }
            }
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.weight(1f),
            ) {
                Text("Close")
            }
        }
    }
}
