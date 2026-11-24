package app.meubolso.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/** Ícones disponíveis para categorias, por chave salva no banco. */
object CategoryIcons {
    val map: LinkedHashMap<String, ImageVector> = linkedMapOf(
        "cart" to Icons.Filled.ShoppingCart,
        "restaurant" to Icons.Filled.Restaurant,
        "fastfood" to Icons.Filled.Fastfood,
        "coffee" to Icons.Filled.LocalCafe,
        "car" to Icons.Filled.DirectionsCar,
        "bus" to Icons.Filled.DirectionsBus,
        "fuel" to Icons.Filled.LocalGasStation,
        "home" to Icons.Filled.Home,
        "bolt" to Icons.Filled.Bolt,
        "water" to Icons.Filled.WaterDrop,
        "wifi" to Icons.Filled.Wifi,
        "phone" to Icons.Filled.PhoneAndroid,
        "health" to Icons.Filled.LocalHospital,
        "pharmacy" to Icons.Filled.LocalPharmacy,
        "fitness" to Icons.Filled.FitnessCenter,
        "movie" to Icons.Filled.Movie,
        "game" to Icons.Filled.SportsEsports,
        "music" to Icons.Filled.MusicNote,
        "travel" to Icons.Filled.Flight,
        "subscriptions" to Icons.Filled.Subscriptions,
        "bag" to Icons.Filled.ShoppingBag,
        "clothes" to Icons.Filled.Checkroom,
        "gift" to Icons.Filled.CardGiftcard,
        "school" to Icons.Filled.School,
        "book" to Icons.Filled.MenuBook,
        "pet" to Icons.Filled.Pets,
        "baby" to Icons.Filled.ChildCare,
        "beauty" to Icons.Filled.Face,
        "tools" to Icons.Filled.Build,
        "card" to Icons.Filled.CreditCard,
        "bank" to Icons.Filled.AccountBalance,
        "work" to Icons.Filled.Work,
        "savings" to Icons.Filled.Savings,
        "money" to Icons.Filled.Payments,
        "undo" to Icons.AutoMirrored.Filled.Undo,
        "trending" to Icons.Filled.TrendingUp,
        "church" to Icons.Filled.Church,
        "more" to Icons.Filled.MoreHoriz,
    )

    fun get(key: String): ImageVector = map[key] ?: Icons.Filled.Category
}

/** Paleta de cores para categorias. */
val CategoryPalette: List<Long> = listOf(
    0xFF2E7D32, 0xFF43A047, 0xFF00897B, 0xFF00838F, 0xFF1565C0, 0xFF3949AB,
    0xFF5E35B1, 0xFF8E24AA, 0xFFC2185B, 0xFFD32F2F, 0xFFEF6C00, 0xFFF9A825,
    0xFF6D4C41, 0xFF546E7A, 0xFF757575, 0xFF000000,
)
