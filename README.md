# DocShift

DocShift is a lightweight Android utility app for working with images and PDFs completely offline.

## Features

### Image Compression
- Compress images to a target file size
- Common presets such as 20 KB, 50 KB, 100 KB, 200 KB and 500 KB
- Smart JPEG compression
- Batch image compression
- Original and final size reporting

### Image Resizing
- Resize by Pixels, Centimeters or Inches
- Optional Maintain Aspect Ratio
- Manual width and height
- Resize first, then compress to a target size

### PDF Size Reduction
- Reduce PDF file size to a selected target
- Common target presets from 100 KB to 2 MB
- Multi-page PDF processing
- Page-by-page progress
- Save or share the compressed PDF
- PDFs are re-rendered for compression, so interactive PDF features such as editable form fields and links are not preserved

### PDF Tools
- Convert images to PDF
- Extract PDF pages as images
- High-quality offline rendering

### Home Screen
The main tools are presented as tiles for faster access:
- Reduce Image Size
- Resize Image
- Reduce PDF Size
- Image → PDF
- PDF → Image

### Android Share Integration
DocShift works from Android's Share menu for supported images and PDFs.

All processing happens locally on the device. No account or cloud upload is required.

## Tech Stack

- Kotlin
- Jetpack Compose
- Android Storage Access Framework
- MediaStore
- FileProvider
- Coroutines

## Getting Started

1. Clone the repository.
2. Open it in Android Studio.
3. Let Gradle sync.
4. Run on a real device or emulator.

## License

MIT License

## Author

Developed by **Ayush Kumar**.
