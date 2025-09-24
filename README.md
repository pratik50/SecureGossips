# 🔐 SecureGossips

SecureGossips is an Android app built with **Kotlin** that focuses on **secure and anonymous text communication**.  
The app was developed as a learning project to explore **end-to-end encryption**, **local persistence**, and **modern Android development practices**.

---

## 🚀 Tech Stack
- **Language:** Kotlin  
- **Architecture:** MVVM  
- **Local Storage:** Room (SQLite)  
- **Async:** Kotlin Coroutines + LiveData  
- **Networking:** Retrofit / OkHttp  
- **Encryption:** AES (basic message-level encryption)  
- **Backend:** Firebase (for message sync)  

---

## ✨ Key Features
- **Encrypted Messaging** — All gossips are encrypted before leaving the device  
- **Anonymous Posting** — No direct identity attached to messages  
- **Offline-first** — Drafts & message history stored locally with Room DB  
- **Sync Support** — Messages update automatically when internet is available  

---

## ⚙️ Setup & Installation
1. Clone the repo  
   ```bash
   git clone https://github.com/pratik50/SecureGossips.git
2.	Open in Android Studio
3.	Add required backend / Firebase configuration
4.	Build & run on emulator or device

---

## 🏗️ Architecture
The app follows the **MVVM pattern**:

- **View** → Activities/Fragments for UI  
- **ViewModel** → Exposes state using LiveData  
- **Repository** → Handles Room DB, network, and encryption    

---

💡 This project was built to learn secure messaging concepts and Android MVVM architecture.
  
  
   
