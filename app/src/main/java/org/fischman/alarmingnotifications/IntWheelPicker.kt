package org.fischman.alarmingnotifications

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun IntWheelPicker(
    value: Int,
    max: Int,
    label: String,
    onValueChange: (Int) -> Unit
) {
    val itemCount = max + 1
    // A large number to simulate infinity
    val pseudoInfiniteCount = 100_000

    // Calculate a starting index that is roughly in the middle,
    // but perfectly aligned to the current 'value'
    val middleOffset = (pseudoInfiniteCount / 2)
    val initialIndex = middleOffset - (middleOffset % itemCount) + value

    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snappingLayout = rememberSnapFlingBehavior(lazyListState = state)

    // Sync state back to the caller
    LaunchedEffect(state.isScrollInProgress) {
        if (!state.isScrollInProgress) {
            // Map the large index back to the 0..max range
            onValueChange(state.firstVisibleItemIndex % itemCount)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(100.dp)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Box(modifier = Modifier.height(150.dp).padding(top = 8.dp), contentAlignment = Alignment.Center) {
            // Highlighting "Selection Window"
            Surface(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            ) {}

            LazyColumn(
                state = state,
                flingBehavior = snappingLayout,
                contentPadding = PaddingValues(vertical = 55.dp), // Centers the center item
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(pseudoInfiniteCount) { index ->
                    val actualValue = index % itemCount
                    val isSelected = state.firstVisibleItemIndex == index

                    Box(
                        modifier = Modifier.height(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = actualValue.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.graphicsLayer {
                                val scale = if (isSelected) 1.2f else 1.0f
                                scaleX = scale
                                scaleY = scale
                            }
                        )
                    }
                }
            }
        }
    }
}
