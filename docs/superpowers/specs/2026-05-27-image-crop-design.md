# Image Crop on Import Design

**Date**: 2026-05-27
**Status**: Approved

## Overview

Add image cropping step after selecting images from gallery during mistake import. Uses UCrop library. Applies to both question images and answer images.

## Flow

```
Click "+" in MultiImageRow → Gallery picker (GetContent) → UCrop crop screen → Crop result → addImageUri(croppedUri)
```

## Technical Approach

**Library**: `com.github.yalantis:ucrop:2.2.8`

## Files Modified

| File | Change |
|------|--------|
| `app/build.gradle.kts` | Add UCrop dependency |
| `ui/screens/ImportScreen.kt` | Replace `GetContent` launcher → `GetContent` + UCrop + crop result launcher |

## Implementation Details

### Gradle

Add to `dependencies`:
```kotlin
implementation("com.github.yalantis:ucrop:2.2.8")
```

### ImportScreen Changes

Replace the direct `GetContent` launcher with a two-step flow:

1. `GetContent` launcher: when image selected, launch UCrop activity
2. UCrop result launcher (`rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult())`): handle crop result, add cropped image to list

Both `imagePickerLauncher` and `answerImageLauncher` follow the same pattern.

### UCrop Configuration

- Free aspect ratio (no lock)
- JPEG output, 90% quality
- Max output size: 2048x2048
- Output to temp file in cache dir (UCrop result gets copied to `question_images/` by `copyImageToLocal` as before)

## Edge Cases

- User cancels crop: no image added (silent skip)
- User cancels gallery picker: no-op (existing behavior)
- Crop output file: UCrop creates a temp file; ViewModel's `copyImageToLocal` copies it to permanent storage at save time
