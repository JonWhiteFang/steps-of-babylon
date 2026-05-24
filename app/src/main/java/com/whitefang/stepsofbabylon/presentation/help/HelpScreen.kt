package com.whitefang.stepsofbabylon.presentation.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HelpScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Help", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)

        HelpSection("💰 Currencies") {
            "• Steps — earned only by walking. Spent on Workshop upgrades and Labs research.\n" +
            "• Cash — earned by killing enemies in battle. Resets each round. Spent on in-round upgrades.\n" +
            "• Gems — earned from milestones, daily login streaks, and card packs. Spent on card packs and Lab rush.\n" +
            "• Power Stones — earned from weekly challenges, wave milestones, and boss kills. Spent on Ultimate Weapon upgrades.\n" +
            "• Card Copies — earned from packs and supply drops. Collect enough copies to level up a card."
        }

        HelpSection("🔨 Workshop") {
            "Permanent upgrades purchased with Steps. Three categories:\n" +
            "• Attack — Damage, Attack Speed, Range, Critical Chance, Multishot, Bounce Shot, Rapid Fire, and more.\n" +
            "• Defense — Health, Health Regen, Defense, Knockback, Thorn Damage, Lifesteal, Death Defy.\n" +
            "• Utility — Cash Bonus, Interest, Free Upgrades, Step Multiplier, Recovery Packages.\n\n" +
            "Cost formula: baseCost × scaling^level. Use Quick Invest to buy the cheapest affordable upgrade."
        }

        HelpSection("⚔️ Battle Controls") {
            "• Speed buttons (1×/2×/4×) — control game speed.\n" +
            "• Pause — freezes the battle.\n" +
            "• Upgrade button — opens the in-round upgrade menu. Spend Cash on temporary upgrades that last one round.\n\n" +
            "Waves last 26 seconds (spawn phase) followed by a 9-second cooldown. Survive as many waves as you can!"
        }

        HelpSection("🏔️ Tiers & Biomes") {
            "10 difficulty tiers with escalating challenges:\n" +
            "• Higher tiers unlock by reaching wave milestones.\n" +
            "• Each tier has a cash multiplier (higher tiers = more Cash per kill).\n" +
            "• Tiers 6+ introduce battle conditions (faster enemies, reduced regen, etc.).\n\n" +
            "5 biomes tied to tier ranges:\n" +
            "• Hanging Gardens (T1–2) → Burning Sands (T3–4) → Frozen Ziggurats (T5–6) → Underworld of Kur (T7–8) → Celestial Gate (T9–10)."
        }

        HelpSection("🔬 Labs") {
            "Research projects that take real time to complete:\n" +
            "• Start research by spending Steps. Each project has a real-world timer.\n" +
            "• Rush with Gems to complete instantly.\n" +
            "• Unlock up to 4 research slots (200 Gems each).\n" +
            "• Research types boost damage, health, critical chance, regen, cash, step efficiency, UW cooldown, and wave skip."
        }

        HelpSection("🃏 Cards") {
            "Collectible cards that provide per-round bonuses:\n" +
            "• Open packs with Gems (Common/Rare/Epic tiers).\n" +
            "• Duplicate cards add copies. Collect enough copies to level up (3 Common / 4 Rare / 5 Epic per level).\n" +
            "• Max level 7. Equip up to 3 cards at once.\n" +
            "• 9 card types with unique effects (defense, crit, cash, lifesteal, bounce, health, damage, gems)."
        }

        HelpSection("⚡ Ultimate Weapons") {
            "Powerful abilities that auto-trigger when their cooldown reaches zero and enemies are present:\n" +
            "• 6 types: Chain Lightning, Death Wave, Black Hole, Chrono Field, Poison Swamp, Golden Ziggurat.\n" +
            "• Unlock with Power Stones. Equip up to 3 at once.\n" +
            "• Each UW has 3 upgrade paths (Damage, Secondary, Cooldown) — 10 levels each.\n" +
            "• Boss kills award Power Stones (tier × PS, up to 100/day)."
        }

        HelpSection("📦 Walking Encounters") {
            "Rewards delivered while you walk:\n" +
            "• Supply drops appear in your inbox based on step milestones and random chance.\n" +
            "• Claim drops for Steps, Gems, Power Stones, or Card Copies.\n" +
            "• Daily missions refresh at midnight — complete 3 per day for bonus rewards.\n" +
            "• Walking milestones award Gems, Power Stones, and cosmetics at major step thresholds."
        }

        HelpSection("🛡️ Fair Play") {
            "Steps of Babylon uses anti-cheat to keep the game fair:\n" +
            "• 200 steps/minute rate limit (prevents shaker exploits).\n" +
            "• 50,000 steps/day ceiling.\n" +
            "• Health Connect cross-validation verifies step counts against your device's health data.\n" +
            "• Activity Minute Parity converts exercise sessions to step-equivalents for indoor workouts."
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun HelpSection(title: String, content: () -> String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(content(), style = MaterialTheme.typography.bodyMedium)
}
