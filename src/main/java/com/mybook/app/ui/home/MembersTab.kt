package com.mybook.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import com.mybook.app.data.MemberEntity
import com.mybook.app.domain.MAX_NAME
import com.mybook.app.ui.common.SectionHeader

@Composable
fun MembersTab(
    members: List<MemberEntity>,
    onAdd: (String) -> Unit,
    onRemove: (MemberEntity) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var toRemove by remember { mutableStateOf<MemberEntity?>(null) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Add a member", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_NAME) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        onAdd(name)
                        name = ""
                    },
                    enabled = name.isNotBlank(),
                ) { Text("Add") }
            }
        }
        item { SectionHeader("Members (${members.size})") }
        items(members, key = { it.id }) { member ->
            ListItem(
                headlineContent = { Text(member.name) },
                trailingContent = {
                    IconButton(onClick = { toRemove = member }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove ${member.name}")
                    }
                },
            )
        }
    }

    toRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text("Remove ${member.name}?") },
            text = { Text("Members who already have expenses in this book can't be removed.") },
            confirmButton = { TextButton(onClick = { onRemove(member); toRemove = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text("Cancel") } },
        )
    }
}
