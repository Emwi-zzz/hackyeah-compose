package features.admin.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.ui.AppBorder
import core.ui.AppColors
import core.ui.AppShapes
import features.admin.domain.AdminState
import features.admin.domain.AdminTool
import features.map.presentation.MapState

@Composable
fun AdminToggleButton(admin: AdminState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable { admin.isOpen = !admin.isOpen },
        shape = AppShapes.Pill,
        color = if (admin.isOpen) AppColors.AccentSoft else AppColors.Surface,
        contentColor = if (admin.isOpen) AppColors.Accent else AppColors.TextPrimary,
        border = AppBorder
    ) {
        Text(
            "🛠 Admin",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminPanel(admin: AdminState, mapState: MapState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.width(330.dp).heightIn(max = 560.dp),
        shape = AppShapes.Card,
        color = AppColors.Surface,
        contentColor = AppColors.TextPrimary,
        border = AppBorder,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Admin panel",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (admin.session != null) {
                    TextButton(onClick = { admin.logout() }) { Text("Sign out", fontSize = 12.sp) }
                }
            }
            admin.message?.let {
                Text(it, color = if (admin.messageIsError) AppColors.Danger else AppColors.Success, fontSize = 12.sp)
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
        shape = AppShapes.Control,
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = { admin.login(user, pass) },
        enabled = !admin.busy,
        shape = AppShapes.Control,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Sign in", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HomeSection(admin: AdminState, mapState: MapState) {
    SectionTitle("New gallery")
    Field("Name", admin.newMallName) { admin.newMallName = it }
    Button(
        onClick = { admin.startNewMall() },
        enabled = admin.newMallName.isNotBlank(),
        shape = AppShapes.Control,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Draw outline on the map") }

    HorizontalDivider(color = AppColors.Border)
    val focused = mapState.focusedMall
    SectionTitle("Existing gallery")
    Text(focused?.name ?: "No gallery in focus", fontSize = 12.sp, color = AppColors.TextSecondary)
    OutlinedButton(
        onClick = { admin.startEditing() },
        enabled = focused != null,
        shape = AppShapes.Control,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Edit this gallery") }

    HorizontalDivider()
    Text("Create administrator", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    Field("Username", admin.newAdminUsername) { admin.newAdminUsername = it }
    OutlinedTextField(
        value = admin.newAdminPassword,
        onValueChange = { admin.newAdminPassword = it },
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = { admin.createAdmin() },
        enabled = !admin.busy && admin.newAdminUsername.isNotBlank() && admin.newAdminPassword.isNotEmpty(),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Create admin account") }
}

@Composable
private fun NewOutlineSection(admin: AdminState) {
    SectionTitle("New gallery: ${admin.newMallName}")
    Text(
        "Click around the building footprint (${admin.points.size} points so far). " +
            "This shape is shown when the map is zoomed out.",
        fontSize = 12.sp,
        color = AppColors.TextSecondary
    )
    ShapeModeControls(admin)
    DrawButtons(admin, onCancel = { admin.cancelDrawing() })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorSection(admin: AdminState, mapState: MapState) {
    val draft = admin.draft ?: return
    Field("Gallery name", draft.name) { admin.renameMall(it) }

    SectionTitle("Floors")
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
            shape = AppShapes.Control,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(
            onClick = { newFloor.toIntOrNull()?.let { admin.addFloor(it); newFloor = "" } },
            enabled = newFloor.toIntOrNull() != null,
            shape = AppShapes.Control
        ) { Text("Add") }
        OutlinedButton(
            onClick = { admin.deleteFloor(mapState.currentFloorNumber) },
            shape = AppShapes.Control
        ) { Text("Remove") }
    }

    HorizontalDivider(color = AppColors.Border)
    SectionTitle("Tools")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (tool in AdminTool.entries) {
            Chip(tool.title, admin.tool == tool) { admin.selectTool(tool) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { admin.toggleVertexEditing() }, shape = AppShapes.Control) {
            Text(if (admin.isVertexEditing) "Done editing points" else "Drag shape points", fontSize = 12.sp)
        }
        if (admin.isVertexEditing) {
            Text("Drag blue anchors or purple Bézier controls", fontSize = 11.sp, color = AppColors.TextSecondary)
        }
    }
    if (admin.isVertexEditing) {
        Text(
            "Shift keeps the edge horizontal; Ctrl keeps it vertical. If both are held, the closer adjustment is used.",
            fontSize = 11.sp,
            color = AppColors.TextSecondary
        )
    }
    admin.tool?.let { tool ->
        Text(tool.hint, fontSize = 12.sp, color = AppColors.TextSecondary)
        when (tool) {
            AdminTool.STORE -> {
                Field("Store name", admin.storeName) { admin.storeName = it }
                Field("Category", admin.storeCategory) { admin.storeCategory = it }
            }
            AdminTool.ENTRANCE -> Field("Entrance name", admin.entranceName) { admin.entranceName = it }
            else -> Unit
        }
        if (tool.kind == AdminTool.Kind.POLYGON) {
            Text("${admin.points.size} points placed", fontSize = 12.sp, color = AppColors.TextSecondary)
            ShapeModeControls(admin)
            DrawButtons(admin, onCancel = { admin.cancelDrawing() })
        }
    }

    HorizontalDivider(color = AppColors.Border)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = { admin.save() },
            enabled = admin.isDirty && !admin.busy,
            shape = AppShapes.Control,
            modifier = Modifier.weight(1f)
        ) { Text(if (admin.isNew) "Create" else "Save", fontWeight = FontWeight.SemiBold) }
        OutlinedButton(onClick = { admin.discard() }, shape = AppShapes.Control, modifier = Modifier.weight(1f)) {
            Text(if (admin.isDirty) "Discard" else "Close")
        }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { confirmDelete = false; admin.deleteMall() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.DangerSoft,
                    contentColor = AppColors.Danger
                ),
                border = BorderStroke(1.dp, AppColors.Danger),
                shape = AppShapes.Control,
                modifier = Modifier.weight(1f)
            ) { Text("Confirm delete", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            OutlinedButton(
                onClick = { confirmDelete = false },
                shape = AppShapes.Control,
                modifier = Modifier.weight(1f)
            ) { Text("Cancel") }
        }
    } else {
        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (admin.isNew) "Delete draft" else "Delete gallery", color = AppColors.Danger, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ShapeModeControls(admin: AdminState) {
    Text(
        "Next edge (closing edge when finished)",
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        color = AppColors.TextPrimary
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip("Straight", !admin.isBezierDrawing) { admin.selectBezierMode(false) }
        Chip("Bézier", admin.isBezierDrawing) { admin.selectBezierMode(true) }
    }
    Text(
        "Choose the style before placing each point; it applies to the edge from the previous point. " +
            "The closing edge uses the selected style when you finish. Undo removes the last point and its edge.",
        fontSize = 11.sp,
        color = AppColors.TextMuted
    )
    Text(
        "While placing points, hold Shift for a horizontal edge or Ctrl for a vertical edge. " +
            "If both are held, the closer alignment is used.",
        fontSize = 11.sp,
        color = AppColors.TextMuted
    )
}

@Composable
private fun DrawButtons(admin: AdminState, onCancel: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = { admin.finishDrawing() },
            enabled = admin.points.size >= 3,
            shape = AppShapes.Control,
            modifier = Modifier.weight(1f)
        ) { Text("Finish (${admin.points.size})", fontSize = 12.sp) }
        OutlinedButton(
            onClick = { admin.undoPoint() },
            enabled = admin.points.isNotEmpty(),
            shape = AppShapes.Control,
            modifier = Modifier.weight(1f)
        ) { Text("Undo", fontSize = 12.sp) }
        OutlinedButton(onClick = onCancel, shape = AppShapes.Control, modifier = Modifier.weight(1f)) {
            Text("Cancel", fontSize = 12.sp)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = AppColors.TextPrimary)
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = AppShapes.Control,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = AppShapes.Control,
        color = if (selected) AppColors.Accent else AppColors.SurfaceRaised,
        contentColor = if (selected) AppColors.OnAccent else AppColors.TextSecondary,
        border = if (selected) null else AppBorder
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
