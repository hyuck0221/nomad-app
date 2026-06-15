<p align="center">
  <img src="/icon.png" width="128" height="128" alt="NOMAD AI Logo" />
</p>

<h1 align="center">NOMAD AI</h1>

<p align="center">
  A general-purpose Android on-device LLM app.<br>
  Chat, write, summarize, translate, inspect image text, and use voice locally.
</p>

---

## Features

### Offline AI

Runs the language model directly on Android hardware. Once the model is installed, everyday chat and writing tasks can work without an internet connection.

### General Chat

Ask about concepts, draft messages, brainstorm ideas, summarize notes, translate text, or get coding help. The assistant is no longer travel-specific and is designed for broad daily use.

### Image Text Understanding

Take or attach a photo and NOMAD AI can use recognized text as context. It can summarize, translate, explain, or answer questions about visible text.

### Voice Conversation

Talk hands-free with speech input and text-to-speech output. You can use Android system TTS or install the local Supertonic 3 voice model.

### Translation and Interpretation

Use text translation or a face-to-face interpretation screen for multilingual conversations.

### Optional Utility Tags

The chat can still route practical requests such as currency conversion, simple expense logging, or choice chips when the model needs a clear user selection.

## Supported Languages

- Korean
- English
- Chinese
- Japanese

The AI responds in the language selected in the app. OCR supports Korean, English, Chinese, and Japanese text recognition.

## Privacy

- The AI model runs locally for conversations
- Chat history is stored locally on the device
- Camera OCR is processed on-device
- Network access is used for model downloads, updates, and live currency lookup when selected

## Requirements

- Android 8.0+
- 4 GB RAM minimum
- 6 GB+ RAM recommended
- Around 2.5 to 3.5 GB storage for AI models

## Tech Stack

- Kotlin
- Jetpack Compose
- LiteRT-LM / Gemma on-device models
- ML Kit Text Recognition
- Room
- WorkManager
- Android TextToSpeech
- sherpa-onnx / Supertonic 3 local TTS
