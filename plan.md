Here’s the **updated production-ready blueprint** with your new requirements: **near-human offline female TTS** + **smart content filtering** (knows what to read vs. skip). Everything stays 100% offline, synchronized with your existing auto-scroll/highlight engine.

---
## 🎙️ Updated Architecture (TTS + Smart Reading Layer)
```
Presentation (Compose UI, TTS Controls, Smart Filters Toggle)
   ↓
Domain (TtsEngine, ContentAnalyzer, ReadingController, SyncOrchestrator)
   ↓
Data (Room: LayoutCache, TtsSession, PositionTracker | DataStore: Voice/Filter prefs)
   ↓
System (Foreground Media Service, ONNX/TFLite Runtime, SAF Scanner)
```

---
## 🧠 1. Near-Human Female Voice (100% Offline)

### ✅ Recommended Stack
| Component | Choice | Why |
|-----------|--------|-----|
| **TTS Engine** | **Piper TTS** (VITS-based) | Open-source, optimized for mobile, ~60-90MB per voice, streams low-latency audio |
| **Voice Model** | `en_US-libritts_r-medium` or `en_US-amy-medium` (female, expressive) | High naturalness, clear articulation, trained on audiobook-quality data |
| **Runtime** | ONNX Runtime Mobile + `AudioTrack` streaming | Supports NNAPI delegate, handles chunked audio without UI thread blocking |
| **Sync Method** | Word-timestamp extraction → drives scroll & highlight | Precise mapping between spoken word and PDF position |

### 🔧 Implementation Flow
1. **Bundle/Download Model**: Ship base engine in APK; offer in-app download for premium female voice packs (keeps APK < 50MB).
2. **Audio Pipeline**:
   ```kotlin
   val tts = PiperTtsEngine(context)
   tts.loadVoice("female_neural_v2.onnx", vocabFile)
   tts.speak(text, onWordBoundary = { word, startMs, endMs ->
       highlightController.animateTo(word)
       scrollController.smoothScrollTo(word.yOffset)
   })
   ```
3. **Background Playback**: Run inside `ForegroundService` with `ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`. Android allows uninterrupted audio this way.
4. **Quality vs Performance Toggle**:
   - `Premium Neural` (VITS, ~40% CPU, near-human)
   - `Fast Standard` (eSpeak-NG fallback, ~5% CPU, robotic but instant)
   - Auto-switches based on device thermal/CPU budget.

---
## 🧹 2. Smart Reading Logic (What to Read vs Skip)

PDFs contain layout noise. Your app will use a **hybrid rule-based + lightweight ML filter** to isolate "narrative content".

### ✅ Content Classification Pipeline
```kotlin
data class TextBlock(
    val text: String,
    val bounds: Rect,
    val fontSize: Float,
    val fontName: String,
    val confidence: Float = 1f
)

sealed class ContentType {
    object Paragraph, Title, Footnote, Header, Footer, PageNumber, FigureCaption, Table, Ad, Unknown
}
```

### 📐 Filtering Rules (Offline, Zero Network)
| Rule | Logic | Skip? |
|------|-------|-------|
| **Margin Detection** | `Y < topMargin` or `Y > pageHeight - bottomMargin` | ✅ Skip (headers/footers) |
| **Repeated Text** | Same text + similar bounds across ≥3 pages | ✅ Skip (page numbers, running titles) |
| **Font Size Threshold** | `fontSize < bodyFontSize * 0.85` | ✅ Skip (footnotes, captions, references) |
| **Block Type (ML)** | Run lightweight LayoutParser ONNX model (12MB) | ✅ Skip `Figure`, `Table`, `Ad`, `Footer` |
| **Paragraph Structure** | Multi-sentence, centered Y, consistent indentation | 📖 Read |
| **User Override** | Toggle in settings: "Read footnotes?", "Read captions?" | Configurable |

### 🔍 Smart Analyzer Implementation
```kotlin
class ContentAnalyzer @Inject constructor(
    private val layoutModel: LayoutClassifier, // ONNX
    private val prefs: DataStore
) {
    fun analyzePage(blocks: List<TextBlock>): List<ReadableSegment> {
        val pageType = layoutModel.predictPageStructure(blocks)
        val filters = prefs.smartReadingFilters() // e.g., skipFooters=true
        
        return blocks
            .filter { !isMarginBlock(it, filters) }
            .filter { !isRepeatedAcrossPages(it) }
            .filter { filters.readFootnotes || it.type != ContentType.Footnote }
            .map { block -> ReadableSegment(block.text, block.bounds) }
            .mergeIntoParagraphs() // Combine adjacent blocks logically
    }
}
```

---
## 🔗 3. TTS ↔ Auto-Scroll ↔ Highlight Sync Engine

Instead of time-based scrolling, **TTS becomes the master clock**:

```kotlin
class ReadingController @Inject constructor(
    private val tts: PiperTtsEngine,
    private val highlighter: HighlightOverlay,
    private val scroller: AutoScrollEngine
) {
    private val wordQueue = MutableStateFlow<List<WordPosition>>(emptyList())

    fun startReading(segments: List<ReadableSegment>) {
        val flatWords = segments.flatMap { segment ->
            segment.text.splitToWords().map { WordPosition(it, segment.bounds) }
        }
        wordQueue.value = flatWords

        tts.speak(segments.joinToString(" "), 
            onWordStart = { wordIndex ->
                val pos = flatWords[wordIndex]
                highlighter.jumpTo(pos)
                scroller.smoothScrollTo(pos.yOffset)
            }
        )
    }
}
```
- **Speed Control**: TTS rate (0.5x–2.5x) directly adjusts scroll velocity.
- **Pause/Resume**: `tts.pause()` → scroll halts → highlight freezes. `tts.resume()` → sync continues.
- **Fallback**: If TTS lags >300ms, scroll uses interpolated time-based correction.

---
## 📦 Updated Tech Stack
```kotlin
// Neural TTS
implementation("com.github.piper-tts:piper-android:1.0.0")
implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.3")

// Layout Analysis (Offline ML)
implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")

// Audio Streaming & Foreground
implementation("androidx.media3:media3-exoplayer:1.2.1") // For audio track management
implementation("androidx.core:core-ktx:1.13.1")

// Keep existing: Room, DataStore, Pdfium, Compose, Hilt
```

---
## 📅 Updated Development Roadmap

| Phase | Deliverables | Time |
|-------|--------------|------|
| **1. Core PDF + Position** | Pdfium, Room/DAO, SAF scanner, start-position UI | 2 wks |
| **2. Smart Content Filter** | Layout rules, repeated-text detector, paragraph merger, settings toggle | 1.5 wks |
| **3. Offline Neural TTS** | Piper engine integration, female voice model, audio streaming, word-timestamp sync | 2.5 wks |
| **4. Sync Engine + Foreground Service** | TTS-driven scroll/highlight, media playback service, background resume logic | 2 wks |
| **5. Translation + Volume Keys** | Double-press handler, offline TFLite translator, cache, UI overlay | 1.5 wks |
| **6. Polish & Perf** | Memory profiling, thermal throttling, NNAPI delegate, animations, edge cases | 1.5 wks |
| **7. Testing & Release** | Offline validation, multi-device TTS/audio tests, Play Store packaging | 1 wk |

---
## ⚠️ Critical Android Realities & Workarounds

| Requirement | OS Limitation | Compliant Solution |
|-------------|---------------|-------------------|
| Near-human offline voice | Android's built-in TTS is robotic & device-dependent | Bundle Piper VITS model. Use NNAPI for hardware acceleration. Provide fallback. |
| Background reading + scroll | Android kills background UI & restricts audio | `ForegroundService` with `mediaPlayback` type. Audio continues; UI pauses but state persists. |
| Layout analysis on mobile | Heavy ML models drain battery | Use 12MB quantized LayoutParser ONNX. Run on `IO` dispatcher. Cache results per PDF. |
| Model size vs APK limit | Play Store APK limit is 150MB (without Play Asset Delivery) | Ship core engine in APK. Download voice/layout packs on first launch via in-app manager (still offline after). |

---
## ✅ Next Steps: Pick Your Starting Point
I can provide production-ready Kotlin code for any of these:
1. `PiperTtsEngine` wrapper with word-timestamp streaming & `AudioTrack` pipeline
2. `ContentAnalyzer` with margin/repetition/ML filtering logic
3. `ReadingController` sync engine (TTS → highlight → scroll)
4. Foreground Media Service setup for background playback + position tracking
5. Compose UI: TTS controls, smart filter toggles, voice quality switcher

**Which module do you want to implement first?** I’ll give you copy-paste ready, well-commented Kotlin with Compose integration.
