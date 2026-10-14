package com.aurum.expense.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aurum.expense.data.local.MemberEntity
import com.aurum.expense.ui.common.SectionHeader

@Composable
fun MembersTab(
    members: List<MemberEntity>,
    me: String,
    isOwner: Boolean,
    onInvite: (String) -> Unit,
    onRemove: (MemberEntity) -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var toRemove by remember { mutableStateOf<MemberEntity?>(null) }

    LazyColumn(Modifier.fillMaxSize()) {
        if (isOwner) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Invite a member", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it.trim().take(254) },
                        label = { Text("Google account email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = { onInvite(email) }, enabled = email.isNotBlank()) { Text("Send invite") }
                    Text(
                        "They install Aurum, sign in with this Google account and join from \"Shared with you\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { SectionHeader("Members (${members.size})") }
        items(members, key = { it.email }) { member ->
            ListItem(
                headlineContent = { Text(member.displayName + if (member.email == me) " (you)" else "") },
                supportingContent = { Text("${member.email} · ${if (member.role == "owner") "Owner" else "Member"}") },
                trailingContent = {
                    if (isOwner && member.role != "owner") {
                        IconButton(onClick = { toRemove = member }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove ${member.displayName}")
                        }
                    }
                },
            )
        }
    }

    toRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text("Remove ${member.displayName}?") },
            text = { Text("They lose access to the group's Drive folder. Expenses they added stay in the group.") },
            confirmButton = { TextButton(onClick = { onRemove(member); toRemove = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text("Cancel") } },
        )
    }
}
