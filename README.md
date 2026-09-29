# إدارة توزيع المنتجات للصيدليات — نسخة أندرويد

نسخة أندرويد من تطبيق سطح المكتب [pharmacy-distribution](https://github.com/qut2006sib-hash/pharmacy-distribution): الصيدليات، المنتجات، سجل التواصل والمتابعات، الطلبيات، سجل التعديلات، والنسخ الاحتياطي — دون إنترنت، والبيانات على الهاتف فقط.

## التوافق مع نسخة الحاسوب

- قاعدة البيانات **مطابقة لمخطط الحاسوب** (SQLite، المراجعة `0001_initial`، المعرّف `application_id` نفسه).
- يمكن **استعادة نسخة احتياطية من الحاسوب (`.sqlite3`) على الهاتف**، واستعادة نسخة الهاتف على الحاسوب من «استعادة من ملف».
- لا توجد مزامنة تلقائية: الهاتف والحاسوب قاعدتان مستقلتان؛ ينقل المستخدم البيانات بنسخة احتياطية.

## حماية البيانات (كما في نسخة الحاسوب)

- كل حفظ داخل معاملة واحدة، مع سجل تعديلات (قبل/بعد) لكل عملية.
- بعد كل حفظ تُنشأ نسخة احتياطية مفحوصة (`integrity_check`، العلاقات، إجماليات الطلبيات).
- عند الفتح: فحص كامل ثم نسخة «startup»؛ إن فشل الفحص لا يُحذف شيء ولا تُنشأ قاعدة بديلة.
- الاستعادة تحتفظ بالبيانات الحالية أولًا (نسخة `before_restore`، أو نسخ جنائية إن كانت تالفة).
- خاص بأندرويد: تعطيل معالج أندرويد الافتراضي الذي **يحذف** القاعدة التالفة، وتعطيل وضع WAL التلقائي في أندرويد 9+.
- النسخ التلقائية (`after_save`, `startup`, `daily`) تُقلَّم إلى آخر 60 من كل نوع لتوفير مساحة الهاتف؛ النسخ اليدوية لا تُحذف أبدًا.
- **مهم:** النسخ داخل الهاتف تضيع إن حُذف التطبيق أو ضاع الهاتف. استخدم «حفظ نسخة إلى ملف» دوريًا.

## البنية

| الوحدة | المحتوى |
|---|---|
| `core/` | Kotlin خالص: المخطط، التحقق، قواعد العمل (منقولة من `services.py`)، النسخ والاستعادة، التصدير. مختبرة على JVM مع SQLite حقيقي. |
| `app/` | واجهة Jetpack Compose (عربية، من اليمين لليسار) + ربط SQLite الخاص بأندرويد + الطباعة. |

## البناء

```bash
./gradlew :core:test            # اختبارات القواعد والتوافق مع الحاسوب
./gradlew :app:assembleDebug    # APK في app/build/outputs/apk/debug/
```

كل دفع إلى GitHub يبني ملفات APK تلقائيًا (Actions → آخر تشغيل → Artifacts).

نسخة release موقعة حاليًا بمفتاح debug. قبل النشر على Play أنشئ keystore خاصًا وأضفه كأسرار في GitHub.

---

**English summary:** Android (Kotlin + Jetpack Compose) port of the offline desktop pharmacy-distribution manager. `core/` is a platform-free Kotlin port of the desktop business rules, schema, validation, backup/restore and export, tested on the JVM against real SQLite, including a backup produced by the desktop app. Backups are interchangeable with the desktop version (same schema revision and application id). CI builds debug and release APKs on every push.
