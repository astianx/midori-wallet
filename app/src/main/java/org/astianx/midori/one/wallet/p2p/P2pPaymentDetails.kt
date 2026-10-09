package org.astianx.midori.one.wallet.p2p

/** Typed payment IDs in the pinned SDK 1.3.7 field order. QR-based IDs remain unsupported here.
 * Native checks deliberately accept a subset of SDK inputs; the provider remains the authority.
 * Bank details stay local and are encrypted only when an accepted SELL order is reviewed.
 */
internal object P2pPaymentDetails {
    fun fields(country: String): List<String> = when(country) {
        "IN" -> listOf("upi"); "ID" -> listOf("phone"); "BR" -> listOf("pix"); "AR", "CO" -> listOf("alias")
        "VE" -> listOf("phone","rif","bank"); "BO" -> listOf("account")
        "NG" -> listOf("account","bank","name"); "CU" -> listOf("phone","card")
        "EC" -> listOf("bank","accountType","account","name","cedula")
        "PE" -> listOf("phone","cci"); "PH" -> listOf("phone","bank")
        "KE" -> listOf("phone","till","paybill","paybillAccount"); else -> emptyList()
    }
    fun optional(country: String) = country in setOf("PE","KE")
    private fun String.rx(pattern: String) = matches(Regex(pattern))
    fun pack(country: String, values: Map<String,String>): String = fields(country).joinToString("|") { values[it].orEmpty().trim() }
    fun valid(country: String, value: String): Boolean {
        if(value.isBlank() || value.length>512 || value.any { it.isISOControl() }) return false
        val parts=value.split('|');val fields=fields(country)
        if(fields.isEmpty() || parts.size!=fields.size || parts.any { it!=it.trim() }) return false
        val p=fields.zip(parts).toMap()
        fun f(name:String)=p[name].orEmpty()
        fun number(name:String, pattern:String)=f(name).rx(pattern)
        val email="[^\\s@]+@[^\\s@]+\\.[^\\s@]+"
        return when(country) {
            "IN" -> f("upi").rx("[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z0-9]{2,64}")
            "ID" -> number("phone","[0-9]{9,12}")
            "BR" -> f("pix").rx(email) || f("pix").rx("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") ||
                f("pix").rx("[0-9]{10}") || f("pix").rx("[1-9][1-9]9[0-9]{8}")
            "AR" -> f("alias").rx("[a-zA-Z0-9.\\-_]{6,20}") && !f("alias").rx("[0-9]{22}")
            "CO" -> f("alias").rx("3[0-9]{9}") || f("alias").rx(email) || f("alias").rx("@\\S+")
            "VE" -> number("phone","0?4[0-9]{9}") && f("rif").rx("[VEJGRP][0-9]+") && f("bank").isNotBlank()
            "BO" -> number("account","[0-9]{8,20}")
            "NG" -> number("account","[0-9]{10}") && f("bank").isNotBlank() && f("name").rx("[A-Za-z][A-Za-z .'-]{1,}")
            "CU" -> number("phone","(?:53)?[0-9]{8}") && number("card","[0-9]{16}")
            "EC" -> f("bank").isNotBlank() && f("accountType").isNotBlank() && number("account","[0-9]{4,20}") &&
                f("name").rx("[A-Za-zÀ-ÿ][A-Za-zÀ-ÿ .'-]{1,}") && cedula(f("cedula"))
            "PE" -> (f("phone").isEmpty() || number("phone","9[0-9]{8}")) && (f("cci").isEmpty() || number("cci","[0-9]{20}")) &&
                (f("phone").isNotBlank() || f("cci").isNotBlank())
            "PH" -> number("phone","(?:0|63)?9[0-9]{9}") && f("bank").isNotBlank()
            "KE" -> (f("phone").isEmpty() || number("phone","(?:254|0)?[17][0-9]{8}")) &&
                (f("till").isEmpty() || number("till","[0-9]{5,7}")) &&
                (f("paybill").isEmpty() || number("paybill","[0-9]{5,7}")) &&
                (f("paybillAccount").isEmpty() || f("paybillAccount").rx("[A-Za-z0-9]{1,20}")) &&
                (f("phone").isNotEmpty() || f("till").isNotEmpty() || f("paybill").isNotEmpty() && f("paybillAccount").isNotEmpty()) &&
                (f("paybill").isEmpty() == f("paybillAccount").isEmpty())
            else -> false
        }
    }
    private fun cedula(value: String): Boolean {
        if(!value.rx("[0-9]{10}|[0-9]{13}")) return false
        val province=value.take(2).toInt();if(province !in 1..24) return false
        if(value.length==13 && (value.endsWith("000") || value[2] !in "01234569")) return false
        if(value[2] in "69") return value.length==13
        if(value[2] !in "012345") return false
        val sum=(0..8).sumOf { i -> val n=value[i].digitToInt()*(if(i%2==0) 2 else 1);if(n>9) n-9 else n }
        return (10-sum%10)%10==value[9].digitToInt()
    }
}
