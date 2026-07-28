package com.pup.seenior.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LinkScreen(viewModel: FamilyPairingViewModel, onVerified: () -> Unit) {
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        BlueHeader(Icons.Filled.Link, "Link")

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                "Share With Family",
                color = FamilyColors.Blue,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp)
            )
            Text(
                "Enter the invite code from a senior's SEENior app to connect.",
                color = FamilyColors.TextSecondary,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 6.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .background(FamilyColors.BlueLightBg, RoundedCornerShape(20.dp))
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Invite code:", color = FamilyColors.TextPrimary, fontSize = 18.sp)

                LaunchedEffect(Unit) { focusRequester.requestFocus() }

                // A transparent full-width text field sits on top of the 6 display boxes:
                // tapping anywhere focuses it and pops the number keypad, while the boxes
                // render the digits. decorationBox draws the boxes so they share the tap target.
                BasicTextField(
                    value = viewModel.code,
                    onValueChange = { new -> if (new.length <= 6 && new.all { it.isDigit() }) viewModel.code = new },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    cursorBrush = SolidColor(Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .focusRequester(focusRequester),
                    decorationBox = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (i in 0 until 6) {
                                val ch = viewModel.code.getOrNull(i)?.toString() ?: ""
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .border(1.5.dp, if (ch.isNotEmpty()) FamilyColors.Blue else FamilyColors.FieldBorder, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(ch, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = FamilyColors.TextPrimary)
                                }
                            }
                        }
                    }
                )

                Spacer(Modifier.height(16.dp))

                if (viewModel.isVerifying) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = FamilyColors.Blue, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Looking up code…", color = FamilyColors.Blue, fontSize = 18.sp, modifier = Modifier.padding(start = 10.dp))
                    }
                } else {
                    BluePillButton(
                        text = "→  Verify code",
                        enabled = viewModel.code.length == 6,
                        onClick = { viewModel.verify(onVerified) }
                    )
                }
            }

            viewModel.error?.let {
                Text(it, color = FamilyColors.ErrorRed, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .background(FamilyColors.WarningBg, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Info, null, tint = FamilyColors.WarningText, modifier = Modifier.size(22.dp))
                Text(
                    if (viewModel.isVerifying)
                        "Connecting to SEENior's server to verify ${viewModel.code}. Make sure you have internet."
                    else "The senior must generate a code first from their SEENior app.",
                    color = FamilyColors.WarningText,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}
