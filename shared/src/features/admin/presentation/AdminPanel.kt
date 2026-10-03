package features.admin.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import features.admin.domain.AdminState
import features.admin.domain.AdminTool
import features.map.presentation.MapState

private val Accent = Color(0xFFF59E0B)

@Composable
fun AdminToggleButton(admin: AdminState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable { admin.isOpen = !admin.isOpen },
        shape = RoundedCornerShape(12.dp),
        color = if (admin.isOpen) Accent else Color.White,
        shadowElevation = 6.dp
    ) {
        Text(
            "🛠 Admin",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminPanel(admin: AdminState, mapState: MapState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.width(330.dp).heightIn(max = 560.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Admin panel", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                if (admin.session != null) {
                    TextButton(onClick = { admin.logout() }) { Text("Sign out", fontSize = 12.sp) }
                }
            }
            admin.message?.let {
                Text(it, color = if (admin.messageIsError) Color(0xFFB91C1C) else Color(0xFF15803D), fontSize = 12.sp)
            }
            if (admin.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            when {
                admin.session == null -> LoginSection(admin)
                admin.isDrawingNewOutline -> NewOutlineSection(admin)
                admin.draft == null -> HomeSection(admin, mapState)
                else -> EditorSection(admin, mapState)
            }
        }
    }
}

@Composable
private fun LoginSection(admin: AdminState) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    Field("Username", user) { user = it }
    OutlinedTextField(
        value = pass,
        onValueChange = { pass = it },
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
    Button(onClick = { admin.login(user, pass) }, enabled = !admin.busy, modifier = Modifier.fillMaxWidth()) {
        Text("Sign in")
    }
}

@Composable
private fun HomeSection(admin: AdminState, mapState: MapState) {
    Text("New gallery", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Field("Name", admin.newMallName) { admin.newMallName = it }
    Button(
        onClick = { admin.startNewMall() },
        enabled = admin.newMallName.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Draw outline on the map") }

    HorizontalDivider()
    val focused = mapState.focusedMall
    Text("Existing gallery", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Text(focused?.name ?: "No gallery in focus", fontSize = 12.sp)
    OutlinedButton(
        onClick = { admin.startEditing() },
        enabled = focused != null,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Edit this gallery") }
}

@Composable
private fun NewOutlineSection(admin: AdminState) {
    Text("New gallery: ${admin.newMallName}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Text(
        "Click the corners of the building on the map (${admin.points.size} so far). " +
            "This shape is shown when the map is zoomed out.",
        fontSize = 12.sp
    )
    DrawButtons(admin, onCancel = { admin.cancelDrawing() })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorSection(admin: AdminState, mapState: MapState) {
    val draft = admin.draft ?: return
    Field("Gallery name", draft.name) { admin.renameMall(it) }

    Text("Floors", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (floor in draft.floors) {
            Chip("${floor.number}", mapState.currentFloorNumber == floor.number) { mapState.selectFloor(floor.number) }
        }
    }
    var newFloor by remember { mutableStateOf("") }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = newFloor,
            onValueChange = { newFloor = it.filter { c -> c.isDigit() || c == '-' }.take(3) },
            label = { Text("Floor no.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(
            onClick = { newFloor.toIntOrNull()?.let { admin.addFloor(it); newFloor = "" } },
            enabled = newFloor.toIntOrNull() != null
        ) { Text("Add") }
        OutlinedButton(onClick = { admin.deleteFloor(mapState.currentFloorNumber) }) { Text("Remove") }
    }

    HorizontalDivider()
    Text("Tools", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (tool in AdminTool.entries) {
            Chip(tool.title, admin.tool == tool) { admin.selectTool(tool) }
        }
    }
    admin.tool?.let { tool ->
        Text(tool.hint, fontSize = 12.sp, color = Color(0xFF475569))
        when (tool) {
            AdminTool.STORE -> {
                Field("Store name", admin.storeName) { admin.storeName = it }
                Field("Category", admin.storeCategory) { admin.storeCategory = it }
            }
            AdminTool.ENTRANCE -> Field("Entrance name", admin.entranceName) { admin.entranceName = it }
            else -> Unit
        }
        if (tool.kind == AdminTool.Kind.POLYGON) {
            DrawButtons(admin, onCancel = { admin.cancelDrawing() })
        }
    }

    HorizontalDivider()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = { admin.save() },
            enabled = admin.isDirty && !admin.busy,
            modifier = Modifier.weight(1f)
        ) { Text(if (admin.isNew) "Create" else "Save") }
        OutlinedButton(onClick = { admin.discard() }, modifier = Modifier.weight(1f)) {
            Text(if (admin.isDirty) "Discard" else "Close")
        }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { confirmDelete = false; admin.deleteMall() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C)),
                modifier = Modifier.weight(1f)
            ) { Text("Confirm delete", fontSize = 12.sp) }
            OutlinedButton(onClick = { confirmDelete = false }, modifier = Modifier.weight(1f)) { Text("Cancel") }
        }
    } else {
        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (admin.isNew) "Delete draft" else "Delete gallery", color = Color(0xFFB91C1C), fontSize = 12.sp)
        }
    }
}

@Composable
private fun DrawButtons(admin: AdminState, onCancel: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = { admin.finishDrawing() },
            enabled = admin.points.size >= 3,
            modifier = Modifier.weight(1f)
        ) { Text("Finish (${admin.points.size})", fontSize = 12.sp) }
        OutlinedButton(
            onClick = { admin.undoPoint() },
            enabled = admin.points.isNotEmpty(),
            modifier = Modifier.weight(1f)
        ) { Text("Undo", fontSize = 12.sp) }
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel", fontSize = 12.sp) }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Accent else Color(0xFFF1F5F9)
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 12.sp)
    }
}
