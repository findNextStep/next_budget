package ai.findnextstep.budget.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.findnextstep.budget.logic.model.Category
import ai.findnextstep.budget.ui.theme.categoryBackgroundAlpha
import ai.findnextstep.budget.ui.theme.categoryForeground

/**
 * 类型选择器。网格布局展示所有预定义类型。
 * 使用非 lazy 布局以免嵌套在可滚动容器中时崩溃。
 */
@Composable
fun CategorySelector(
    categories: List<Category>,
    selectedCategory: Category?,
    onCategorySelected: (Category) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 8.dp)) {
        Text(
            text = "选择类型",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )

        // 每行 4 列，手动分行避免 LazyVerticalGrid 嵌套滚动崩溃
        val columns = 4
        val rows = categories.chunked(columns)
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (category in row) {
                    CategoryChip(
                        category = category,
                        isSelected = category == selectedCategory,
                        onClick = { onCategorySelected(category) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // 填充不足 4 列的行
                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CategoryChip(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val catColor = categoryForeground(category.key, MaterialTheme.colorScheme.primary)
    val bgAlpha = categoryBackgroundAlpha()
    val bgColor = if (isSelected) {
        catColor
    } else {
        catColor.copy(alpha = bgAlpha)
    }
    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        catColor
    }

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        tonalElevation = if (isSelected) 4.dp else 0.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = category.displayName,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        }
    }
}
