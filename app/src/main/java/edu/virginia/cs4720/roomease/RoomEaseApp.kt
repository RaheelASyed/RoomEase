package edu.virginia.cs4720.roomease

import android.app.Application
import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import edu.virginia.cs4720.roomease.data.AuthRepository
import edu.virginia.cs4720.roomease.data.ChatRepository
import edu.virginia.cs4720.roomease.data.ChoreRepository
import edu.virginia.cs4720.roomease.data.ExpenseRepository
import edu.virginia.cs4720.roomease.data.HouseholdRepository
import edu.virginia.cs4720.roomease.data.ReceiptImageStore
import edu.virginia.cs4720.roomease.data.ReceiptTextRecognizer
import edu.virginia.cs4720.roomease.data.SettingsRepository
import edu.virginia.cs4720.roomease.data.WriteErrorBus

/**
 * Lecture 9 "Wiring it up": build the data sources once when the app boots,
 * hand them to repositories, and hand repositories to ViewModels. Wired by hand, no Hilt.
 */
class AppContainer(context: Context) {
    private val auth = Firebase.auth
    private val db = Firebase.firestore

    init {
        // Debug builds can point at the local Firebase Emulator Suite: ./gradlew installDebug -Proomease.useEmulator=true
        // then forward the ports to the device: adb reverse tcp:9099 tcp:9099 && adb reverse tcp:8080 tcp:8080
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            auth.useEmulator(EMULATOR_HOST, 9099)
            db.useEmulator(EMULATOR_HOST, 8080)
        }
    }

    val writeErrors = WriteErrorBus()
    val authRepository = AuthRepository(auth, db)
    val householdRepository = HouseholdRepository(db)
    val choreRepository = ChoreRepository(db, writeErrors)
    val expenseRepository = ExpenseRepository(db, writeErrors)
    val chatRepository = ChatRepository(db, writeErrors)
    val settingsRepository = SettingsRepository(context)
    val receiptImageStore = ReceiptImageStore(context.filesDir)
    val receiptTextRecognizer = ReceiptTextRecognizer(context)

    private companion object {
        // Loopback + `adb reverse` works on emulators and USB phones, and avoids Android 17's
        // local-network permission that blocks apps from reaching 10.0.2.2 / LAN addresses.
        const val EMULATOR_HOST = "127.0.0.1"
    }
}

class RoomEaseApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
