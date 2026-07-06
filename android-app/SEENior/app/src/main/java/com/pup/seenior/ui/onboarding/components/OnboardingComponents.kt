package com.pup.seenior.ui.onboarding.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pup.seenior.ui.theme.SeniorColors
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun OnboardingTopBar(
    currentStep: Int,
    totalSteps: Int = 5,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = SeniorColors.TextPrimary
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            StepDots(
                currentStep = currentStep,
                totalSteps = totalSteps,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Box(modifier = Modifier.size(48.dp))
    }
}

@Composable
fun StepDots(currentStep: Int, totalSteps: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (step in 1..totalSteps) {
            val isActive = step == currentStep
            val isCompleted = step < currentStep
            val color = if (isActive || isCompleted) SeniorColors.Green else SeniorColors.DotFuture
            Box(
                modifier = Modifier
                    .height(10.dp)
                    .width(if (isActive) 28.dp else 10.dp)
                    .background(color, RoundedCornerShape(50))
            )
        }
    }
}

@Composable
fun OnboardingHeading(title: String, subtitle: String) {
    Text(
        text = title,
        color = SeniorColors.Green,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = subtitle,
        color = SeniorColors.TextSecondary,
        fontSize = 16.sp,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
fun QuestionLabel(text: String) {
    Text(
        text = text,
        color = SeniorColors.TextPrimary,
        fontSize = 16.sp,
        modifier = Modifier.padding(bottom = 8.dp, top = 20.dp)
    )
}

@Composable
fun FieldLabel(text: String) {
    Text(
        text = text,
        color = SeniorColors.TextPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 8.dp, top = 20.dp)
    )
}

private val fieldShape = RoundedCornerShape(12.dp)

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = SeniorColors.FieldBackground,
    focusedContainerColor = SeniorColors.FieldBackground,
    unfocusedBorderColor = SeniorColors.FieldBorder,
    focusedBorderColor = SeniorColors.Green,
    unfocusedTextColor = SeniorColors.TextPrimary,
    focusedTextColor = SeniorColors.TextPrimary
)

@Composable
fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    questionStyle: Boolean = false
) {
    Column(modifier = modifier) {
        if (questionStyle) QuestionLabel(label) else FieldLabel(label)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = SeniorColors.TextHint) },
            singleLine = true,
            shape = fieldShape,
            textStyle = TextStyle(fontSize = 18.sp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = fieldColors()
        )
    }
}

@Composable
fun <T> LabeledDropdownField(
    label: String,
    selected: T?,
    options: List<T>,
    onSelect: (T) -> Unit,
    optionLabel: (T) -> String,
    placeholder: String = "Select",
    modifier: Modifier = Modifier,
    questionStyle: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        if (questionStyle) QuestionLabel(label) else FieldLabel(label)
        Box {
            OutlinedTextField(
                value = selected?.let(optionLabel) ?: "",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true },
                enabled = false,
                placeholder = { Text(placeholder, color = SeniorColors.TextHint) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = SeniorColors.Green
                    )
                },
                shape = fieldShape,
                textStyle = TextStyle(fontSize = 18.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledContainerColor = SeniorColors.FieldBackground,
                    disabledBorderColor = SeniorColors.FieldBorder,
                    disabledTextColor = SeniorColors.TextPrimary,
                    disabledPlaceholderColor = SeniorColors.TextHint,
                    disabledTrailingIconColor = SeniorColors.Green
                )
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option), fontSize = 18.sp) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val bg = if (enabled) SeniorColors.Green else SeniorColors.DisabledButtonBg
    val fg = if (enabled) Color.White else SeniorColors.DisabledButtonText
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(bg, RoundedCornerShape(50))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun RoleCard(
    borderColor: Color,
    backgroundColor: Color,
    icon: @Composable () -> Unit,
    title: String,
    bullets: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(2.dp, borderColor, RoundedCornerShape(28.dp))
            .background(backgroundColor, RoundedCornerShape(28.dp))
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Text(
                text = title,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = SeniorColors.TextPrimary,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        Column(modifier = Modifier.padding(top = 16.dp)) {
            bullets.forEach { bullet ->
                Row(modifier = Modifier.padding(bottom = 6.dp)) {
                    Text("•  ", color = SeniorColors.TextSecondary, fontSize = 16.sp)
                    Text(bullet, color = SeniorColors.TextSecondary, fontSize = 16.sp)
                }
            }
        }
    }
}

private val displayTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LabeledTimeField(
    label: String,
    value: LocalTime?,
    onValueChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "HH:MM",
    questionStyle: Boolean = false
) {
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        if (questionStyle) QuestionLabel(label) else FieldLabel(label)
        OutlinedTextField(
            value = value?.format(displayTimeFormat) ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = false,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showPicker = true },
            placeholder = { Text(placeholder, color = SeniorColors.TextHint) },
            shape = fieldShape,
            textStyle = TextStyle(fontSize = 18.sp),
            colors = OutlinedTextFieldDefaults.colors(
                disabledContainerColor = SeniorColors.FieldBackground,
                disabledBorderColor = SeniorColors.FieldBorder,
                disabledTextColor = SeniorColors.TextPrimary,
                disabledPlaceholderColor = SeniorColors.TextHint
            )
        )
    }

    if (showPicker) {
        val initial = value ?: LocalTime.of(8, 0)
        val state = rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onValueChange(LocalTime.of(state.hour, state.minute))
                    showPicker = false
                }) { Text("OK", color = SeniorColors.Green) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Cancel", color = SeniorColors.TextSecondary)
                }
            },
            text = { TimePicker(state = state) }
        )
    }
}
