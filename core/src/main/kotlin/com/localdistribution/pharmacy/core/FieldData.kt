package com.localdistribution.pharmacy.core

/**
 * The user's own field data, imported once on first run (same marker as the
 * desktop's field_data_import_2026_09_26). Generated from the desktop
 * field_data.py. Set ENABLED = false to ship an empty app.
 */
object FieldData {
    const val ENABLED = true

    data class SeedPharmacy(val name: String, val aliases: List<String>, val phone: String, val address: String,
        val stage: String, val contractStatus: String, val notes: List<String>)
    data class SeedProduct(val sku: String, val name: String, val brand: String, val price: String)
    data class SeedOrder(val pharmacy: String, val orderDate: String, val currency: String, val status: String,
        val notes: String, val items: List<Pair<String, Long>>)

    val PHARMACIES = listOf(
        SeedPharmacy("صيدلية ميدورا – Medora Pharmacy", listOf("مادورا", "ميدورا", "صيدلية ميدورا", "Medora Pharmacy"), "0122222108", "PPM2+V9Q، حمص",
            "interested", "none", listOf("المصدر/المرجع: https://www.medora-sy.com/", "حالة التواصل: تم التواصل", "نتيجة التواصل: أبدوا تفاؤلاً كبيراً، وكان المقترح فتح رف/قسم كامل لمنتجاتنا.", "الخطوة التالية: سيتم التواصل قريباً لاستكمال الاتفاق.", "تقدير فرصة النجاح: عالية جدا", "الأولوية: عالية", "النشاط المخطط: زيارة ميدانية")),
        SeedPharmacy("صيدلية المنارة", listOf("المنارة", "منارة"), "0122136501", "PPG4+QV7، حمص",
            "new", "none", listOf("المصدر/المرجع: https://shofey.com/directory/%D8%B5%D9%8A%D8%AF%D9%84%D9%8A%D8%A9-%D8%A7%D9%84%D9%85%D9%86%D8%A7%D8%B1%D8%A9/")),
        SeedPharmacy("صيدلية ديما", listOf("ديمة", "ديما", "صيدلية ديمة"), "0122753610", "PP38+Q2P، ضاحية الوليد، مقابل الفرن الآلي",
            "new", "none", listOf("المصدر/المرجع: https://shofey.com/directory/%D8%B5%D9%8A%D8%AF%D9%84%D9%8A%D8%A9-%D8%AF%D9%8A%D9%85%D8%A7/")),
        SeedPharmacy("صيدلية البشائر", listOf(), "", "PMFX+M7R، شارع راغب الجمالي",
            "new", "none", listOf()),
        SeedPharmacy("Lama Kheder Pharmacy", listOf(), "", "PP59+9CJ، عكرمة الجديدة، شارع النادي العمالي، مقابل سايبا",
            "new", "none", listOf("Facebook: Lama Kheder Pharmacy", "المصدر/المرجع: https://www.findhealthclinics.org/SY/Homs/102916856135256/Lama-Kheder-Pharmacy")),
        SeedPharmacy("Auf Pharmaceuticals", listOf("Auf Pharmacy"), "0991586994 / 0312221957", "PPH3+CV6، دوار الدبلان",
            "new", "none", listOf("Facebook: Auf Pharmacy", "المصدر/المرجع: https://syria.worldplaces.me/review/99569811-auf-pharmacy.html")),
        SeedPharmacy("صيدلية الصوفي – Al Soufi Pharmacy", listOf("صيدلية الصوفي", "Al Soufi Pharmacy"), "", "PPV5+J4H، الدبلان / شارع حافظ إبراهيم",
            "new", "none", listOf("المصدر/المرجع: https://greenpages.sy/business/azhar-akasya-mnhkep6r")),
        SeedPharmacy("صيدلية الأندلس", listOf(), "0122111070", "PP87+439، دوار السيد الرئيس / كرم الشامي",
            "new", "none", listOf("المصدر/المرجع: https://shofey.com/directory/%D8%B5%D9%8A%D8%AF%D9%84%D9%8A%D8%A9-%D8%A7%D9%84%D8%A3%D9%86%D8%AF%D9%84%D8%B3/")),
        SeedPharmacy("صيدلية ساكورا", listOf("ساكورا"), "", "شارع الحمرا، بعد رسم البنين بحوالي 50 متر، حمص",
            "interested", "active", listOf("حالة التواصل: تم التواصل", "نتيجة التواصل: عرضت نشر المنتجات على صفحتها الشخصية، وبانتظار قرار الشريك/الشريكة في الصيدلية بشأن اعتماد المنتجات.", "الخطوة التالية: متابعة قرار الشريك/الشريكة في الصيدلية.", "تقدير فرصة النجاح: تم التعاقد", "الأولوية: عالية", "النشاط المخطط: زيارة ميدانية")),
        SeedPharmacy("صيدلية بريما – Prima Pharmacy", listOf("صيدلية بريما", "Prima Pharmacy"), "", "الدبلان، قرب حديقة الدبلان، حمص",
            "new", "none", listOf("النشاط المخطط: زيارة ميدانية")),
        SeedPharmacy("صيدلية الكلمة", listOf(), "", "مقابل مركز الثقافة، حمص",
            "declined", "none", listOf("حالة التواصل: تم التواصل", "نتيجة التواصل: لم يتم القبول.", "الخطوة التالية: لا توجد متابعة حالياً.", "تقدير فرصة النجاح: مقرونة بانتشار المنتج", "النشاط المخطط: زيارة ميدانية")),
        SeedPharmacy("B Pharma – بي فارما", listOf("B Pharma", "بي فارما"), "2455155", "",
            "new", "none", listOf("المصدر/المرجع: https://www.medicalsy.com/sub/77", "تقدير فرصة النجاح: مقرونة بانتشار المنتج")),
        SeedPharmacy("صيدلية نبض الحياة", listOf(), "", "بعد توشكا العز، حمص",
            "later", "none", listOf("المصدر/المرجع: تحديث ميداني – 17/09/2026", "حالة التواصل: تم التواصل", "نتيجة التواصل: سيقومون بالتفكير في العرض.", "الخطوة التالية: متابعة القرار.", "تقدير فرصة النجاح: مقرونة بانتشار المنتج")),
        SeedPharmacy("صيدلية أدورا – Adora Pharmacy", listOf("أدورا", "ادورا", "صيدلية أدورا", "Adora Pharmacy"), "", "",
            "declined", "none", listOf("المصدر/المرجع: تحديث ميداني – 17/09/2026", "حالة التواصل: تم التواصل", "نتيجة التواصل: لم يتم القبول.", "الخطوة التالية: لا توجد متابعة حالياً.", "تقدير فرصة النجاح: مقرونة بانتشار المنتج", "النشاط المخطط: زيارة ميدانية")),
    )

    val PRODUCTS = listOf(
        SeedProduct("MEDEE-VIT-C", "MEDEE SERUM (Vit C)", "MEDEE", "29.40"),
        SeedProduct("MEDEE-EXTRA-C", "MEDEE SERUM (EXTRA C)", "MEDEE", "30.75"),
        SeedProduct("MEDEE-HYDRA", "MEDEE HYDRA", "MEDEE", "10.40"),
        SeedProduct("MEDEE-HA", "MEDEE HA", "MEDEE", "24.55"),
        SeedProduct("MEDEE-RETINOL", "MEDEE RETINOL", "MEDEE", "24.55"),
        SeedProduct("MEDEE-ROLLER-025", "MEDEE DERMA ROLLER 0.25", "MEDEE", "23.50"),
        SeedProduct("MEDEE-ROLLER-05", "MEDEE DERMA ROLLER 0.5", "MEDEE", "23.50"),
        SeedProduct("JUVE-AQUAFLUID-SPF50", "JUVE LAB AQUAFLUID +SPF50", "JUVE LAB", "15.60"),
        SeedProduct("JUVE-SPF50-WHITENIZER", "JUVE LAB SPF50 + WHITENIZER", "JUVE LAB", "19.00"),
        SeedProduct("JUVE-TKN-CREAM", "JUVE LAB TKN CREAM", "JUVE LAB", "24.60"),
        SeedProduct("JUVE-CLEANSER-OILY", "JUVE LAB CLEANSER OILY COMBINED SKIN", "JUVE LAB", "9.85"),
        SeedProduct("JUVE-CLEANSER-DRY", "JUVE LAB CLEANSER DRY COMBINED SKIN", "JUVE LAB", "9.85"),
        SeedProduct("JUVE-CLEANSER-SOFT", "JUVE LAB CLEANSER SOFT COMBINED SKIN", "JUVE LAB", "9.85"),
        SeedProduct("JUVE-DEODORANT", "JUVE LAB DEODARANT", "JUVE LAB", "9.85"),
        SeedProduct("JUVE-DEODORANT-ANTI", "JUVE LAB DEODARANT ANTIPRESPIRANT", "JUVE LAB", "9.85"),
        SeedProduct("FEME-CAPSULES-60", "FEME HAIR CAPSULES 60", "FEME HAIR", "35.80"),
        SeedProduct("FEME-SHAMPOO", "FEME HAIR SHAMPOO", "FEME HAIR", "13.20"),
        SeedProduct("FEME-ROSEMARY-OIL", "FEME HAIR ROSEMARY OIL", "FEME HAIR", "18.30"),
    )

    val ORDER_1 = SeedOrder("صيدلية ساكورا", "2026-06-24", "USD", "confirmed",
        "مصدر البيانات: سجل الطلبيات المرفق. طريقة الدفع: كاش. حالة التسليم: غير مسلّمة. الكمية الإجمالية: 9. الإجمالي محسوب تلقائياً من البنود = 172.65؛ القيمة 345.30 الموجودة في ملخص الطلبية لا تتوافق مع أسعار وكميات البنود لذلك لم تُعتمد.",
        listOf("MEDEE-VIT-C" to 1L, "MEDEE-EXTRA-C" to 1L, "JUVE-DEODORANT" to 2L, "JUVE-SPF50-WHITENIZER" to 1L, "JUVE-CLEANSER-OILY" to 1L, "JUVE-CLEANSER-DRY" to 1L, "FEME-ROSEMARY-OIL" to 1L, "FEME-CAPSULES-60" to 1L))
}
