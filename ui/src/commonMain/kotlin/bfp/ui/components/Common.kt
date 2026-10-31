package bfp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import bfp.contracts.BandDto
import bfp.ui.i18n.Strings
import bfp.ui.theme.LocalBandColors

fun Modifier.tag(id: String): Modifier = this.semantics { testTag = id }

@Composable
fun BandPill(band: BandDto, s: Strings) {
    val c = LocalBandColors.current
    val color = when (band) { BandDto.GREAT -> c.great; BandDto.GOOD -> c.good; BandDto.MIXED -> c.mixed; BandDto.AVOID -> c.avoid }
    Text(
        s.band(band),
        modifier = Modifier.background(color.copy(alpha = 0.16f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
        color = color, style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
fun Notice(text: String, modifier: Modifier = Modifier, tagId: String? = null, tone: Color = MaterialTheme.colorScheme.surfaceVariant) {
    Surface(modifier = modifier.fillMaxWidth().let { if (tagId != null) it.tag(tagId) else it }, color = tone, shape = RoundedCornerShape(10.dp)) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun Loading(text: String) {
    Row(Modifier.fillMaxWidth().tag("loading").padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SampleBanner(s: Strings) = Notice("${s.sampleBadge}. ${s.sampleNote}", tagId = "sample-banner", tone = MaterialTheme.colorScheme.errorContainer)
