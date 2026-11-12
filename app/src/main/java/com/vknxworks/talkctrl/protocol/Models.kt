package com.vknxworks.talkctrl.protocol

/** A Cardo model and its folder on Cardo's update server (from the Cardo app's model list). */
data class CardoModel(val name: String, val folder: String)

object Models {
    val all = listOf(
        CardoModel("Packtalk Edge", "Packtalk_Edge"),
        CardoModel("Packtalk Edge (no FPGA)", "Packtalk_Edge_No_FPGA"),
        CardoModel("Packtalk Edge Ducati", "Packtalk_Edge_Ducati"),
        CardoModel("Packtalk Edge KTM", "Packtalk_Edge_KTM"),
        CardoModel("Packtalk Edge Honda", "Packtalk_Edge_Honda"),
        CardoModel("Packtalk Edge Simpson", "Packtalk_Edge_Simpson"),
        CardoModel("Packtalk Edge Harley-Davidson", "Packtalk_Edge_Harley"),
        CardoModel("Packtalk Neo", "Packtalk_Neo"),
        CardoModel("Packtalk Neo (no FPGA)", "Packtalk_Neo_No_FPGA"),
        CardoModel("Packtalk Special Edition", "Packtalk_Neo_SE"),
        CardoModel("Packtalk Neo Louis", "Packtalk_Neo_Louis"),
        CardoModel("Packtalk Neo Louis (no FPGA)", "Packtalk_Neo_Louis_No_FPGA"),
        CardoModel("Packtalk Pro", "Packtalk_Pro"),
        CardoModel("Packtalk Pro 20 Years", "Packtalk_Pro_RSD"),
        CardoModel("Packtalk Custom", "Packtalk_Custom"),
        CardoModel("Packtalk ORV", "Packtalk_ORV"),
        CardoModel("Packtalk Outdoor", "Packtalk_Outdoor_Consumer"),
        CardoModel("Packtalk Outdoor Instructor", "Packtalk_Outdoor_Instructor"),
        CardoModel("Outdoor Freejump", "Packtalk_Outdoor_Freejump"),
        CardoModel("Packtalk S (Shoei)", "Shoei_Ucs_Edge"),
        CardoModel("HJC Packtalk", "HJC_Ucs_Edge"),
        CardoModel("509 UCS Edge", "509_Mesh"),
        CardoModel("SC Edge", "SC_Edge"),
        CardoModel("SC Edge ANC", "SC_Edge_ANC"),
        CardoModel("Cardo Venture", "Cardo_Venture"),
        CardoModel("Shockwave Mesh", "Shockwave_Mesh"),
        CardoModel("RG2 Shockwave", "Ruroc_RG2_Edge"),
        CardoModel("Delta V", "Packtalk_Polaris_DV"),
        CardoModel("Lazer VeloVox", "Lazer_VeloVox"),
        CardoModel("Cardo Sync", "Cardo_Sync"),
        CardoModel("Cardo UCS Mesh", "UCS_Mesh"),
        CardoModel("UCS Lite", "UCS_Lite"),
        CardoModel("UCS 4X", "UCS_4X"),
        CardoModel("Beyond GT", "Beyond_GT"),
        CardoModel("Beyond GTS", "Beyond_GTS"),
        CardoModel("Flow Lite", "Flow_Lite"),
        CardoModel("Freecom 4X", "Freecom4X"),
        CardoModel("Freecom 2X", "Freecom2X"),
        CardoModel("Freecom 4X Harley-Davidson", "Freecom4X_Harley"),
        CardoModel("Freecom 2X Harley-Davidson", "Freecom2X_Harley"),
        CardoModel("FC4X (FC-Moto)", "Fc_Moto_Ucs"),
        CardoModel("HJC 4X", "HJC_Ucs_4X"),
        CardoModel("4X S (Shoei)", "Shoei_Ucs_4X"),
        CardoModel("LS2 4X", "LS2"),
        CardoModel("Spirit", "Spirit"),
        CardoModel("Spirit HD", "SpiritHD"),
        CardoModel("Givi Spirit", "Givi_Ucs"),
        CardoModel("509 UCS Spirit", "509_Ucs_Spirit"),
        CardoModel("UCS Spirit", "Ucs_Spirit"),
        CardoModel("HJC Spirit", "HJC_Ucs_Spirit"),
    )

    /** Serial prefixes the Cardo app maps to a model; only the part that could be read (see PROTOCOL.md). */
    private val byPrefix = mapOf(
        "PT" to "Packtalk_Edge",
        "PD" to "Packtalk_Edge_Ducati",
        "PH" to "Packtalk_Edge_Honda",
        "KP" to "Packtalk_Edge_KTM",
        "SI" to "Packtalk_Edge_Simpson",
        "OR" to "Packtalk_ORV",
        "LC" to "UCS_Lite",
        "DD" to "Flow_Lite",
        "PI" to "Packtalk_Neo_Louis",
        "PL" to "Packtalk_Neo_Louis",
    )

    fun guess(serial: String?): CardoModel? {
        val folder = byPrefix[serial?.take(2)?.uppercase() ?: return null] ?: return null
        return all.firstOrNull { it.folder == folder }
    }

    fun byFolder(folder: String?) = all.firstOrNull { it.folder == folder }

    /** The Cardo app updates these with a different, unrelated updater; TalkCtrl can't. */
    val notUpdatable = setOf("Lazer_VeloVox", "Cardo_Sync", "UCS_Lite")

    /** Only folders from the list above reach the update server. */
    fun isKnownFolder(folder: String) = all.any { it.folder == folder }
}

/** Voice prompt languages: code on the unit, name, key used in firmware URLs. */
data class PromptLanguage(val code: Int, val name: String, val urlKey: String)

object Languages {
    val all = listOf(
        PromptLanguage(0, "English (US)", "en_us"),
        PromptLanguage(1, "English (UK)", "en_uk"),
        PromptLanguage(2, "Español", "sp"),
        PromptLanguage(3, "Français", "fr"),
        PromptLanguage(4, "Deutsch", "de"),
        PromptLanguage(5, "Italiano", "it"),
        PromptLanguage(6, "Português", "pt"),
        PromptLanguage(7, "Nederlands", ""),
        PromptLanguage(8, "Русский", "ru"),
        PromptLanguage(9, "日本語", "ja"),
        PromptLanguage(10, "中文", "ch"),
        PromptLanguage(11, "עברית", "he"),
        PromptLanguage(12, "한국어", "ko"),
    )

    fun byCode(code: Int) = all.firstOrNull { it.code == code }
}
