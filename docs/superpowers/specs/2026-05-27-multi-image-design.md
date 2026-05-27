# Multi-Image Support Design

**Date**: 2026-05-27
**Status**: Approved

## Overview

Support multiple images per mistake entry (question images and answer images), displayed via horizontal pagers during review. Addressed primarily for subjective/essay questions where screenshots often span multiple pages.

## Storage: Delimiter Approach

Use `||` as delimiter to store multiple paths in existing `questionImagePath` and `referenceAnswer` fields. No database schema changes.

- Single image (backward compatible): `/data/.../img1.jpg`
- Multiple images: `/data/.../img1.jpg||/data/.../img2.jpg||/data/.../img3.jpg`

### Data Layer Changes

Add helper methods on `Mistake`:

```kotlin
fun Mistake.getQuestionImagePaths(): List<String> =
    questionImagePath?.split("||")?.filter { it.isNotBlank() } ?: emptyList()

fun Mistake.getAnswerImagePaths(): List<String> =
    referenceAnswer?.split("||")?.filter { it.isNotBlank() } ?: emptyList()
```

### Files Modified

| File | Change |
|------|--------|
| `domain/model/Mistake.kt` | Add `getQuestionImagePaths()` and `getAnswerImagePaths()` extension functions |
| `ui/screens/ImportScreen.kt` | Replace single image card with horizontal scrollable multi-image row for question images and answer images |
| `ui/screens/ImportViewModel.kt` | Change `imageUri: Uri?` to `imageUris: List<Uri>`, same for answer. Save joins with `||`. |
| `ui/screens/ReviewScreen.kt` | Essay questions: replace single image with `HorizontalPager` + page indicator for question images and answer images independently |

### Files NOT Modified

- `Entities.kt` — no schema change
- `Dao.kt` — queries unchanged
- `MistakeRepository.kt` — mapping unchanged
- `AppDatabase.kt` — version stays at 2

## Import UI

### Question Images (all question types)

Horizontal scrollable row of 120x160dp thumbnails:
- Each thumbnail shows the image with an × button to remove
- A dashed-border "+" placeholder at the end to add more
- Tap "+" → system image picker → appends to list
- Allows 0 images (no image required)

### Answer Images (essay only)

Same layout as question images, independent list.

### Save Logic

- Question images: `imageUris` list → copy each to local storage → `joinToString("||")` → `questionImagePath`
- Answer images: `answerImageUris` list → copy each → `joinToString("||")` → `referenceAnswer`
- Single image produces a string without `||`, backward compatible

### Edit Mode

- Load: `split("||")` existing paths → convert to `Uri` list → populate UI
- Existing images not re-uploaded if not replaced (preserve paths)

## Review UI (Essay Only)

### Question Image Area

- Replace `AsyncImage` with `HorizontalPager` from Compose Foundation
- Page indicator dots below (only shown when > 1 image)
- No images → hide the area entirely (current behavior)
- Single image → show image without pager/indicator

### Answer Image Area

- Same as question: `HorizontalPager` + page indicator
- Independent from question pager (no synchronized swiping)
- Only visible when user taps "View Answer"

### Non-Essay Questions

- No changes. Single image display as before.

## Edge Cases

- **Old data**: Single-path strings without `||` → `split("||")` returns 1-element list
- **Empty path**: `filter { it.isNotBlank() }` handles empty entries
- **0 images**: Pager/content hidden, same as current behavior
- **Editor deletes all images**: Saves as empty string or null
- **Image file deleted from disk**: Graceful degradation (Coil shows placeholder/error)
