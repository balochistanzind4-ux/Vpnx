package com.ajaz.tiktok.ui.screens

import androidx.compose.ui.graphics.Color
import com.ajaz.tiktok.core.parser.ProxyType
import com.ajaz.tiktok.ui.theme.EmeraldActive
import com.ajaz.tiktok.ui.theme.GoldAccent
import com.ajaz.tiktok.ui.theme.GoldGlow
import com.ajaz.tiktok.ui.theme.AmberWarning

object UiHelpers {

    /**
     * Extracts or guesses a country flag emoji based on server name or address.
     */
    fun getCountryFlag(name: String, address: String = ""): String {
        val lower = (name + " " + address).lowercase()
        return when {
            lower.contains("🇺🇸") || lower.contains("us") || lower.contains("united states") || lower.contains("america") || lower.contains("usa") -> "🇺🇸"
            lower.contains("🇩🇪") || lower.contains("de") || lower.contains("germany") || lower.contains("frankfurt") || lower.contains("berlin") -> "🇩🇪"
            lower.contains("🇸🇬") || lower.contains("sg") || lower.contains("singapore") -> "🇸🇬"
            lower.contains("🇨🇳") || lower.contains("cn") || lower.contains("china") || lower.contains("beijing") || lower.contains("shanghai") -> "🇨🇳"
            lower.contains("🇭🇰") || lower.contains("hk") || lower.contains("hong kong") -> "🇭🇰"
            lower.contains("🇯🇵") || lower.contains("jp") || lower.contains("japan") || lower.contains("tokyo") || lower.contains("osaka") -> "🇯🇵"
            lower.contains("🇬🇧") || lower.contains("uk") || lower.contains("united kingdom") || lower.contains("london") || lower.contains("gb") -> "🇬🇧"
            lower.contains("🇫🇷") || lower.contains("fr") || lower.contains("france") || lower.contains("paris") -> "🇫🇷"
            lower.contains("🇳🇱") || lower.contains("nl") || lower.contains("netherlands") || lower.contains("amsterdam") -> "🇳🇱"
            lower.contains("🇨🇦") || lower.contains("ca") || lower.contains("canada") || lower.contains("toronto") -> "🇨🇦"
            lower.contains("🇦🇺") || lower.contains("au") || lower.contains("australia") || lower.contains("sydney") -> "🇦🇺"
            lower.contains("🇰🇷") || lower.contains("kr") || lower.contains("korea") || lower.contains("seoul") -> "🇰🇷"
            lower.contains("🇮🇳") || lower.contains("in") || lower.contains("india") || lower.contains("mumbai") -> "🇮🇳"
            lower.contains("🇹🇼") || lower.contains("tw") || lower.contains("taiwan") -> "🇹🇼"
            lower.contains("🇹🇷") || lower.contains("tr") || lower.contains("turkey") || lower.contains("istanbul") -> "🇹🇷"
            lower.contains("🇷🇺") || lower.contains("ru") || lower.contains("russia") || lower.contains("moscow") -> "🇷🇺"
            lower.contains("🇮🇷") || lower.contains("ir") || lower.contains("iran") -> "🇮🇷"
            lower.contains("🇦🇪") || lower.contains("ae") || lower.contains("dubai") || lower.contains("uae") -> "🇦🇪"
            else -> "🌐"
        }
    }

    /**
     * Color theme corresponding to each proxy protocol badge.
     */
    fun getProtocolColor(type: ProxyType): Color {
        return when (type) {
            ProxyType.VLESS -> GoldAccent
            ProxyType.VMESS -> Color(0xFF38BDF8) // Sky blue
            ProxyType.TROJAN -> EmeraldActive
            ProxyType.SHADOWSOCKS -> Color(0xFFA78BFA) // Violet
            ProxyType.SOCKS5 -> AmberWarning
            ProxyType.HTTP -> Color(0xFF94A3B8)
            ProxyType.DIRECT -> Color(0xFF64748B)
        }
    }
}
