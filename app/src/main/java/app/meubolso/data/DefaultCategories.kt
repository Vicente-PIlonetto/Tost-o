package app.meubolso.data

import app.meubolso.data.db.Category
import app.meubolso.data.db.TxType

object DefaultCategories {
    val all: List<Category> = listOf(
        Category(name = "Mercado", type = TxType.EXPENSE, icon = "cart", color = 0xFF2E7D32, sortOrder = 0),
        Category(name = "Alimentação fora", type = TxType.EXPENSE, icon = "restaurant", color = 0xFFEF6C00, sortOrder = 1),
        Category(name = "Transporte", type = TxType.EXPENSE, icon = "car", color = 0xFF1565C0, sortOrder = 2),
        Category(name = "Moradia", type = TxType.EXPENSE, icon = "home", color = 0xFF6D4C41, sortOrder = 3),
        Category(name = "Contas da casa", type = TxType.EXPENSE, icon = "bolt", color = 0xFFF9A825, sortOrder = 4),
        Category(name = "Saúde", type = TxType.EXPENSE, icon = "health", color = 0xFFD32F2F, sortOrder = 5),
        Category(name = "Lazer", type = TxType.EXPENSE, icon = "movie", color = 0xFF8E24AA, sortOrder = 6),
        Category(name = "Assinaturas", type = TxType.EXPENSE, icon = "subscriptions", color = 0xFFC2185B, sortOrder = 7),
        Category(name = "Compras", type = TxType.EXPENSE, icon = "bag", color = 0xFF00897B, sortOrder = 8),
        Category(name = "Educação", type = TxType.EXPENSE, icon = "school", color = 0xFF3949AB, sortOrder = 9),
        Category(name = "Outros", type = TxType.EXPENSE, icon = "more", color = 0xFF757575, sortOrder = 10),
        Category(name = "Salário", type = TxType.INCOME, icon = "work", color = 0xFF2E7D32, sortOrder = 0),
        Category(name = "Renda extra", type = TxType.INCOME, icon = "savings", color = 0xFF00838F, sortOrder = 1),
        Category(name = "Reembolso", type = TxType.INCOME, icon = "undo", color = 0xFF5E35B1, sortOrder = 2),
        Category(name = "Outros", type = TxType.INCOME, icon = "more", color = 0xFF757575, sortOrder = 3),
    )
}
