package com.rayan.gametimelimiter.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.rayan.gametimelimiter.data.ActionDashImport
import com.rayan.gametimelimiter.data.AppCatalog
import com.rayan.gametimelimiter.data.Rule
import com.rayan.gametimelimiter.data.Store
import com.rayan.gametimelimiter.ui.components.AppAvatar
import com.rayan.gametimelimiter.ui.components.Btn
import com.rayan.gametimelimiter.ui.components.BtnStyle
import com.rayan.gametimelimiter.ui.fmtMinutes
import com.rayan.gametimelimiter.ui.theme.C
import com.rayan.gametimelimiter.ui.theme.Ic
import java.time.format.DateTimeFormatter

/** Summary after an ActionDash import, with the option to recreate its daily limits. */
@Composable
fun ImportResultDialog(result: ActionDashImport.Result, alreadyLimited: Set<String>, onDone: (created: Int) -> Unit) {
    val context = LocalContext.current
    val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
    // Only apps that are still installed and not limited yet.
    val limits = remember(result) {
        result.limits.filterKeys { pkg ->
            pkg !in alreadyLimited && runCatching { context.packageManager.getApplicationInfo(pkg, 0) }.isSuccess
        }.toList().sortedBy { AppCatalog.label(context, it.first).lowercase() }
    }

    Dialog(onDismissRequest = { onDone(0) }) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(C.Surface)
                .border(1.dp, C.BorderStrong, RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Ic.Check, null, tint = C.Ok, modifier = Modifier.size(22.dp))
                Text("History imported", color = C.Text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "${"%,d".format(result.days)} days" +
                    (if (result.from != null && result.to != null) " (${result.from.format(fmt)} – ${result.to.format(fmt)})" else "") +
                    ", ${"%,d".format(result.hours.toLong())} hours across ${result.apps} apps. You'll find it in History.",
                color = C.Muted, fontSize = 14.sp,
            )

            if (limits.isNotEmpty()) {
                Text("Your ActionDash limits", color = C.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Column(
                    Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    limits.forEach { (pkg, minutes) ->
                        val label = AppCatalog.label(context, pkg)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AppAvatar(label, pkg, 28.dp)
                            Text(label, color = C.Text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(fmtMinutes(minutes), color = C.Muted, fontSize = 13.sp)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Btn("Not now", { onDone(0) }, Modifier.weight(1f))
                    Btn("Create ${limits.size} limit${if (limits.size > 1) "s" else ""}", {
                        val created = limits.count { (pkg, minutes) ->
                            Store.saveRule(Rule(name = AppCatalog.label(context, pkg), packages = listOf(pkg), dailyLimitMin = minutes)) == null
                        }
                        onDone(created)
                    }, Modifier.weight(1.4f), style = BtnStyle.Primary)
                }
            } else {
                Btn("Done", { onDone(0) }, Modifier.fillMaxWidth(), style = BtnStyle.Primary)
            }
        }
    }
}
