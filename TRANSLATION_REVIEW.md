# Translation review of the draft strings (2026-11-08)

Scope: the 105 strings added since F64 (`values`, `values-hi`, `values-ta`, `values-te`, `values-kn`, `values-ml`),
read side by side with the English and with the words the rest of the app already uses. This is a model's pass, not a
native speaker's: it catches wrong terms, inconsistencies, ambiguity and length, not tone. The mechanical check
(every string present in every language, placeholders matching) was already clean.

## Fixed in this pass (objective: wrong term, or a different word from the rest of the app for the same thing)

| Where | Languages | What changed |
|---|---|---|
| `doc_share_pdf_will_shrink` | all five | The quality name "Small" was left in English inside the sentence; now the same word the quality picker shows (छोटा, சிறியது, చిన్నది, ಚಿಕ್ಕದು, ചെറുത്). |
| `settings_review_mode_subtitle` | all five | The button name "Done" was left in English; now the button's own label in quotes (हो गया, முடிந்தது, పూర్తయింది, ಮುಗಿದಿದೆ, പൂർത്തിയായി). |
| Tools single-page strings (6) | hi | पृष्ठ → पेज and दस्तावेज़ → डॉक्यूमेंट: the app uses पेज 206 times and पृष्ठ 9, डॉक्यूमेंट 78 and दस्तावेज़ 7. |
| `doc_remove_tag`, `ui_password_show/hide` | hi | हटाएँ/दिखाएँ/छिपाएँ → anusvara forms (हटाएं …): the app's convention (115 to 6). |
| `whats_new_pdf_tools_title` | hi, ta, te, kn | Verbs now match the tool cards (hi जोड़ें/बांटें, ta ஒழுங்குபடுத்து, te క్రమపరచండి/కలపండి, kn ಜೋಡಿಸಿ/ಸೇರಿಸಿ). Telugu had నిర్వహించండి ("manage") and the noun విలీనం in an imperative list. |
| Tools single-page hints, `whats_new_pdf_tools_body` | te, ml | పత్రం → డాక్యుమెంట్, രേഖ → ഡോക്യുമെന്റ്, the app's word for document (80 to 4, 35 to 5). |
| Compress size strings (4) | te | పరిమాణం → సైజ్ to match the other compress strings and the app (16 to 7). |
| `tools_result_compressed_label` | te, kn, ml | Native "shrunk" words replaced with the transliterated "compress" the Compress tool uses everywhere else; ml spelling കംപ്രസ്സ് → കംപ്രസ്. |
| "% smaller" strings | ta, kn, ml | One word per language for "smaller" (ta சிறியது, kn ಚಿಕ್ಕದು, ml കുറവ്); each had two. |
| `scan_guidance_same_page` | te, ml | te చివరిదే పేజీ ("the last page") → ముందు తీసిన పేజీయే ("the same page as the one taken before"); ml కഴിഞ്ഞ → മുമ്പത്തെ. |
| `scan_status_id_composing`, `scan_status_id_compose_failed` | ta | "both sides on one page" read as "both pages on one page" (பக்கம் means both); now ஒரே தாளில் (on one sheet). |
| `doc_share_pdf_keep_under` | ta | Incomplete infinitive இதற்குக் கீழ் வைக்க → இதற்குக் குறைவாக. |
| `scan_strip_delete_hint` | ml | ഇല്ലാതാക്കാൻ → നീക്കം ചെയ്യാൻ, the word the other remove strings use. |

Lint and the string XML were checked after the edits (see `BUILD_AND_TEST.md`).

## For a native speaker to decide (tone, register, idiom; not changed)

- **Greetings**: ml `greeting_afternoon` is ശുഭദിനം ("good day"), because Malayalam has no natural "good afternoon";
  kn ಶುಭ ಅಪರಾಹ್ನ is correct but Sanskritic, ಶುಭ ಮಧ್ಯಾಹ್ನ is more everyday. All five greetings are formal.
- **Exposure** (`scan_exposure`): ta uses the coined ஒளிவெளிப்பாடு, the other four transliterate "exposure". Camera apps in
  Tamil mostly transliterate too.
- **Import** (`widget_import`): te దిగుమతి, kn ಆಮದು, ta இறக்குமதி are the trade word for import, as Google's own Android
  strings use; the rest of this app transliterates (ఇంపోర్ట్, ಇಂಪೋರ್ಟ್). Either is defensible; the widget label is short, so
  the transliteration would match the Import PDF menu item.
- **What's new copy** (`whats_new_*`): literal in all five ("seamlessly", "invalid splits", "perspective corners"); reads
  like a translation. Worth a free rewrite by a speaker.
- **Hindi register**: the Tools strings say कृपया (please); the rest of the app does not. Harmless.
- **Length**: `settings_review_mode_subtitle` is long in every language (a settings subtitle, so it wraps); `doc_share_pdf_will_shrink`
  and `scan_quality_blurry` may wrap to three lines on a 360 dp phone at a large font.
- **Still unverified on a device**: whether document tab labels truncate in Hindi, Tamil and Telugu (R41 leftover).

## How to run the next pass

Strings added after this review are the new drafts: `git diff <this commit> -- app/src/main/res/values/strings*.xml`
lists their names; print them in the six languages and read them against the glossary above (page, document, Done,
Small, compress, import, remove).
